package com.sorudefteri.domain

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ünite ve altındaki konuları temsil eden veri modeli
 */
data class KonuUnite(
    val uniteAdi: String,
    val konular: List<String>
)

/**
 * Hazır YKS Konu Kataloğu Yöneticisi
 * `assets/konular.json` dosyasını okuyup TYT ve AYT ders/ünite/konu hiyerarşisini sunar.
 */
@Singleton
class KonuKatalog @Inject constructor(
    @ApplicationContext private val context: Context
) {

    // Thread-safe önbellek: key = "EXAMTYPE_SUBJECTKEY", value = Map<ÜniteAdı, List<KonuAdı>>
    @Volatile
    private var unitelerCache: Map<String, Map<String, List<String>>>? = null
    private val lock = Any()

    private fun getCache(): Map<String, Map<String, List<String>>> {
        return unitelerCache ?: synchronized(lock) {
            unitelerCache ?: loadKatalog().also { unitelerCache = it }
        }
    }

    /**
     * Kataloğu arka planda (IO thread) önceden yükler.
     */
    suspend fun preload() = withContext(Dispatchers.IO) {
        getCache()
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
                    val normalizedKey = normalizeKey(subjectKey)
                    cache["TYT_$normalizedKey"] = unitMap

                    // Ayrıca dersin 'ad' değerini de alias olarak ekle
                    val displayName = subjectObj.optString("ad", "")
                    if (displayName.isNotBlank()) {
                        cache["TYT_${normalizeKey(displayName)}"] = unitMap
                    }
                }
            }

            // Parse AYT
            if (rootJson.has("ayt")) {
                val aytObj = rootJson.getJSONObject("ayt")
                aytObj.keys().forEach { subjectKey ->
                    val subjectObj = aytObj.getJSONObject(subjectKey)
                    val unitMap = parseUnits(subjectObj)
                    val normalizedKey = normalizeKey(subjectKey)
                    cache["AYT_$normalizedKey"] = unitMap

                    // Ayrıca dersin 'ad' değerini de alias olarak ekle
                    val displayName = subjectObj.optString("ad", "")
                    if (displayName.isNotBlank()) {
                        cache["AYT_${normalizeKey(displayName)}"] = unitMap
                    }
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
                val unitName = unitObj.optString("ad", "Genel").trim()
                val topicList = mutableListOf<String>()

                if (unitObj.has("konular")) {
                    val topicsArray = unitObj.getJSONArray("konular")
                    for (j in 0 until topicsArray.length()) {
                        val topic = topicsArray.optString(j).trim()
                        if (topic.isNotBlank()) {
                            topicList.add(topic)
                        }
                    }
                }
                if (unitName.isNotBlank() && topicList.isNotEmpty()) {
                    unitMap[unitName] = topicList
                }
            }
        }
        return unitMap
    }

    private fun normalizeKey(raw: String): String {
        return raw.lowercase(Locale.ROOT)
            .trim()
            .removePrefix("ayt_")
            .removePrefix("tyt_")
            .removePrefix("ydt_")
            .replace("ç", "c")
            .replace("ğ", "g")
            .replace("ı", "i")
            .replace("i̇", "i")
            .replace("ö", "o")
            .replace("ş", "s")
            .replace("ü", "u")
            .replace("-", "")
            .replace("_", "")
            .replace(" ", "")
            .uppercase(Locale.ROOT)
    }

    private fun findMatchingUnitMap(examType: String, subjectId: String): Map<String, List<String>> {
        val cleanExam = if (examType.equals("AYT", ignoreCase = true)) "AYT" else "TYT"
        val normalized = normalizeKey(subjectId)
        val cache = getCache()

        // 1. Doğrudan tam eşleşme
        cache["${cleanExam}_$normalized"]?.let { return it }

        // 2. TYT / AYT çapraz kontrolü
        val otherExam = if (cleanExam == "AYT") "TYT" else "AYT"
        cache["${otherExam}_$normalized"]?.let { return it }

        // 3. İçeren anahtar kontrolü
        for ((key, map) in cache) {
            if (key.startsWith("${cleanExam}_") && (key.contains(normalized) || normalized.contains(key.removePrefix("${cleanExam}_")))) {
                return map
            }
        }

        return emptyMap()
    }

    /**
     * Belirtilen sınav türü ve derse ait ünite -> konu listesi haritasını döner.
     */
    fun getUniteler(examType: String, subjectId: String): Map<String, List<String>> {
        return findMatchingUnitMap(examType, subjectId)
    }

    fun getUnits(examType: String, subjectId: String): Map<String, List<String>> = getUniteler(examType, subjectId)

    /**
     * Belirtilen sınav türü ve derse ait tüm üniteleri ve konularını liste formatında döner.
     */
    fun getUniteList(examType: String, subjectId: String): List<KonuUnite> {
        val unitMap = getUniteler(examType, subjectId)
        return unitMap.map { (uniteAdi, konular) ->
            KonuUnite(uniteAdi = uniteAdi, konular = konular)
        }
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
     * Belirtilen sınav türü ve derse ait en popüler / ilk birkaç öneri konusunu döner.
     */
    fun getFrequentSuggestions(examType: String, subjectId: String, limit: Int = 8): List<String> {
        return getKonular(examType, subjectId).take(limit)
    }

    /**
     * Tüm katalogda filtreleme/arama yaparak eşleşen üniteleri ve altındaki konuları döner.
     */
    fun searchUniteler(examType: String, subjectId: String, query: String): List<KonuUnite> {
        val allUnits = getUniteList(examType, subjectId)
        if (query.isBlank()) return allUnits

        val cleanQuery = query.lowercase(Locale("tr", "TR")).trim()
        return allUnits.mapNotNull { unite ->
            val unitMatches = unite.uniteAdi.lowercase(Locale("tr", "TR")).contains(cleanQuery)
            val filteredKonular = unite.konular.filter { konu ->
                konu.lowercase(Locale("tr", "TR")).contains(cleanQuery)
            }

            if (unitMatches) {
                unite
            } else if (filteredKonular.isNotEmpty()) {
                unite.copy(konular = filteredKonular)
            } else {
                null
            }
        }
    }

    /**
     * Düz arama yaparak eşleşen konuları döner.
     */
    fun searchKonular(examType: String, subjectId: String, query: String): List<String> {
        val allTopics = getKonular(examType, subjectId)
        if (query.isBlank()) return allTopics
        val cleanQuery = query.lowercase(Locale("tr", "TR")).trim()
        return allTopics.filter { it.lowercase(Locale("tr", "TR")).contains(cleanQuery) }
    }
}
