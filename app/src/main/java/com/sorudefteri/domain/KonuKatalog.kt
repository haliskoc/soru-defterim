package com.sorudefteri.domain

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hazır YKS Konu Kataloğu Yöneticisi
 * `assets/konular.json` dosyasını okuyup TYT ve AYT ders/ünite/konu hiyerarşisini sunar.
 */
@Singleton
class KonuKatalog @Inject constructor(
    @ApplicationContext private val context: Context
) {

    // Cache: key = "EXAMTYPE_SUBJECTID" (örn: "TYT_MATEMATIK"), value = Map<ÜniteAdı, List<KonuAdı>>
    private val unitelerCache: MutableMap<String, Map<String, List<String>>> by lazy {
        loadKatalog()
    }

    private fun loadKatalog(): MutableMap<String, Map<String, List<String>>> {
        val cache = mutableMapOf<String, Map<String, List<String>>>()
        try {
            val jsonString = context.assets.open("konular.json").use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            }

            val rootJson = JSONObject(jsonString)

            // Parse TYT
            if (rootJson.has("tyt")) {
                val tytObj = rootJson.getJSONObject("tyt")
                tytObj.keys().forEach { subjectKey ->
                    val subjectObj = tytObj.getJSONObject(subjectKey)
                    val unitMap = parseUnits(subjectObj)
                    cache["TYT_${subjectKey.uppercase()}"] = unitMap
                }
            }

            // Parse AYT
            if (rootJson.has("ayt")) {
                val aytObj = rootJson.getJSONObject("ayt")
                aytObj.keys().forEach { subjectKey ->
                    val subjectObj = aytObj.getJSONObject(subjectKey)
                    val unitMap = parseUnits(subjectObj)
                    cache["AYT_${subjectKey.uppercase()}"] = unitMap
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return cache
    }

    private fun parseUnits(subjectObj: JSONObject): Map<String, List<String>> {
        val unitMap = LinkedHashMap<String, List<String>>()
        if (subjectObj.has("uniteler")) {
            val unitsArray = subjectObj.getJSONArray("uniteler")
            for (i in 0 until unitsArray.length()) {
                val unitObj = unitsArray.getJSONObject(i)
                val unitName = unitObj.optString("ad", "Genel")
                val topicList = mutableListOf<String>()

                if (unitObj.has("konular")) {
                    val topicsArray = unitObj.getJSONArray("konular")
                    for (j in 0 until topicsArray.length()) {
                        val topic = topicsArray.optString(j)
                        if (topic.isNotBlank()) {
                            topicList.add(topic)
                        }
                    }
                }
                unitMap[unitName] = topicList
            }
        }
        return unitMap
    }

    private fun normalizeSubjectId(subjectId: String): String {
        return subjectId.lowercase().trim()
            .removePrefix("ayt_")
            .removePrefix("tyt_")
            .uppercase()
    }

    private fun buildCacheKey(examType: String, subjectId: String): String {
        val cleanExam = if (examType.equals("AYT", ignoreCase = true)) "AYT" else "TYT"
        val cleanSubject = normalizeSubjectId(subjectId)
        return "${cleanExam}_$cleanSubject"
    }

    /**
     * Belirtilen sınav türü ve derse ait tüm konuların düz listesini döner.
     */
    fun getKonular(examType: String, subjectId: String): List<String> {
        val unitMap = getUniteler(examType, subjectId)
        return unitMap.values.flatten()
    }

    fun getTopics(examType: String, subjectId: String): List<String> = getKonular(examType, subjectId)

    /**
     * Belirtilen sınav türü ve derse ait ünite -> konu listesi haritasını döner.
     */
    fun getUniteler(examType: String, subjectId: String): Map<String, List<String>> {
        val key = buildCacheKey(examType, subjectId)
        return unitelerCache[key] ?: emptyMap()
    }

    fun getUnits(examType: String, subjectId: String): Map<String, List<String>> = getUniteler(examType, subjectId)

    /**
     * Tüm katalogda arama yaparak eşleşen konuları döner
     */
    fun searchKonular(examType: String, subjectId: String, query: String): List<String> {
        val allTopics = getKonular(examType, subjectId)
        if (query.isBlank()) return allTopics
        val cleanQuery = query.lowercase().trim()
        return allTopics.filter { it.lowercase().contains(cleanQuery) }
    }
}
