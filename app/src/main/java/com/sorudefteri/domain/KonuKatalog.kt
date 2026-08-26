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

            // Supported exam sections: tyt, ayt, lgs, kpss
            val sections = listOf("tyt", "ayt", "lgs", "kpss")
            for (section in sections) {
                if (rootJson.has(section)) {
                    val sectionObj = rootJson.getJSONObject(section)
                    val examPrefix = section.uppercase(Locale.ROOT)
                    sectionObj.keys().forEach { subjectKey ->
                        val subjectObj = sectionObj.getJSONObject(subjectKey)
                        val unitMap = parseUnits(subjectObj)
                        val normalizedKey = normalizeKey(subjectKey)
                        cache["${examPrefix}_$normalizedKey"] = unitMap

                        // Display name alias
                        val displayName = subjectObj.optString("ad", "")
                        if (displayName.isNotBlank()) {
                            cache["${examPrefix}_${normalizeKey(displayName)}"] = unitMap
                        }
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
            .removePrefix("lgs_")
            .removePrefix("kpss_")
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
        val cleanExam = when (examType.uppercase(Locale.ROOT)) {
            "LGS" -> "LGS"
            "KPSS" -> "KPSS"
            "AYT" -> "AYT"
            else -> "TYT"
        }
        val normalized = normalizeKey(subjectId)
        val cache = getCache()

        // 1. Doğrudan tam eşleşme (örn: TYT_MATEMATIK, LGS_FEN, KPSS_TARIH)
        cache["${cleanExam}_$normalized"]?.let { return it }

        // 2. Çapraz sınav türü kontrolü
        val otherExams = listOf("TYT", "AYT", "LGS", "KPSS").filter { it != cleanExam }
        for (other in otherExams) {
            cache["${other}_$normalized"]?.let { return it }
        }

        // 3. İçeren anahtar kontrolü
        for ((key, map) in cache) {
            if (key.startsWith("${cleanExam}_") && (key.contains(normalized) || normalized.contains(key.removePrefix("${cleanExam}_")))) {
                return map
            }
        }

        // 4. Genel arama
        for ((key, map) in cache) {
            if (key.contains(normalized) || (normalized.length >= 3 && key.endsWith(normalized))) {
                return map
            }
        }

        return emptyMap()
    }

    /**
     * Kataloğun önbelleğe yüklenip yüklenmediğini döner.
     */
    fun isKatalogLoaded(): Boolean = unitelerCache != null

    /**
     * Kataloğun boş olup olmadığını döner.
     */
    fun isKatalogEmpty(): Boolean = getCache().isEmpty()

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
     * Güvenli ünite listesi: Eğer konu bulunamazsa "Katalog hazırlanıyor..." EmptyState desteği sağlar.
     */
    fun getUniteListWithFallback(examType: String, subjectId: String): List<KonuUnite> {
        val list = getUniteList(examType, subjectId)
        if (list.isEmpty()) {
            return listOf(
                KonuUnite(
                    uniteAdi = "Genel Konular",
                    konular = listOf("Katalog hazırlanıyor...")
                )
            )
        }
        return list
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
