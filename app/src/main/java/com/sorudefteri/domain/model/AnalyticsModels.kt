package com.sorudefteri.domain.model

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Konu Yetkinlik Seviyeleri (4 Renk Kademeli Isı Haritası)
 * - 🟥 %0 - %40: Kritik Eksik
 * - 🟨 %41 - %70: Pekiştirilmeli
 * - 🟦 %71 - %89: Güçlü
 * - 🟩 %90 - %100: Tam Ustalık
 */
enum class MasteryLevel(
    val displayName: String,
    val minPercent: Int,
    val maxPercent: Int,
    val colorHex: Long,
    val emoji: String,
    val description: String
) {
    KRITIK_EKSIK(
        displayName = "Kritik Eksik",
        minPercent = 0,
        maxPercent = 40,
        colorHex = 0xFFEF5350, // Soft Red
        emoji = "🟥",
        description = "Temel kavram eksikliği var, acil konu tekrarı ve soru çözümü gerekli."
    ),
    PEKISTIRILMELI(
        displayName = "Pekiştirilmeli",
        minPercent = 41,
        maxPercent = 70,
        colorHex = 0xFFFFB74D, // Amber / Warm Orange
        emoji = "🟨",
        description = "Konu anlaşılmış ancak işlem/dikkat hataları yapılıyor. Pratik yapılmalı."
    ),
    GUCLU(
        displayName = "Güçlü",
        minPercent = 71,
        maxPercent = 89,
        colorHex = 0xFF42A5F5, // Vibrant Blue
        emoji = "🟦",
        description = "Konu kavranmış ve yüksek başarı oranı yakalanmış. Periyodik tekrar yeterli."
    ),
    TAM_USTALIK(
        displayName = "Tam Ustalık",
        minPercent = 90,
        maxPercent = 100,
        colorHex = 0xFF66BB6A, // Green
        emoji = "🟩",
        description = "Konuya tam hakimiyet! Sınavda soru kaçırma riski minimize edildi."
    );

    companion object {
        fun fromPercentage(percentage: Int): MasteryLevel {
            val clamped = percentage.coerceIn(0, 100)
            return when {
                clamped <= 40 -> KRITIK_EKSIK
                clamped <= 70 -> PEKISTIRILMELI
                clamped <= 89 -> GUCLU
                else -> TAM_USTALIK
            }
        }
    }
}

/**
 * Başarı / Yetkinlik yüzdesini hesaplar (0 - 100 arası tamsayı)
 */
fun calculateMasteryPercentage(solvedCount: Int, totalCount: Int): Int {
    if (totalCount <= 0) return 0
    val pct = (solvedCount.toFloat() / totalCount.toFloat() * 100f).roundToInt()
    return pct.coerceIn(0, 100)
}

/**
 * Belirli bir konudaki öğrenci yetkinlik özeti
 */
data class KonuYetkinlikItem(
    val examType: String,
    val subjectId: String,
    val unitName: String,
    val topicName: String,
    val totalCount: Int,
    val solvedCount: Int,
    val unsolvedCount: Int,
    val masteryPercentage: Int,
    val level: MasteryLevel
) {
    val isAttempted: Boolean get() = totalCount > 0
}

/**
 * 2x2 Acil Eylem Matrisi (Priority Quadrant) Bölgeleri
 */
enum class ActionQuadrant(
    val id: String,
    val title: String,
    val subtitle: String,
    val emoji: String,
    val badgeColorHex: Long
) {
    ACIL_MUDAHALE(
        id = "acil_mudahale",
        title = "Acil Müdahale",
        subtitle = "Sınavı kazandıracak / kaybettirecek acil konular!",
        emoji = "🚨",
        badgeColorHex = 0xFFEF5350
    ),
    GUCLU_KALE(
        id = "guclu_kale",
        title = "Güçlü Kale",
        subtitle = "Netlerini garantileyen konular",
        emoji = "🛡️",
        badgeColorHex = 0xFF43A047
    ),
    PEKISTIR(
        id = "pekistir",
        title = "Pekiştir",
        subtitle = "Fırsat buldukça bak",
        emoji = "⚠️",
        badgeColorHex = 0xFFFF9800
    ),
    GUVENLI(
        id = "guvenli",
        title = "Güvenli",
        subtitle = "Sorun yok",
        emoji = "☕",
        badgeColorHex = 0xFF78909C
    );

    companion object {
        fun resolve(isHighFrequency: Boolean, isHighError: Boolean): ActionQuadrant {
            return when {
                isHighFrequency && isHighError -> ACIL_MUDAHALE
                isHighFrequency && !isHighError -> GUCLU_KALE
                !isHighFrequency && isHighError -> PEKISTIR
                else -> GUVENLI
            }
        }
    }
}

/**
 * ÖSYM Soru Frekansı Sınıflandırması
 */
object OsymKonuAnalizi {

    /**
     * Son 5 yılın ÖSYM YKS sınavlarında (TYT & AYT) düzenli ve yüksek soru ağırlığına sahip anahtar kelimeler ve konular.
     */
    private val YUKSEK_FREKANS_ANAHTARLAR = listOf(
        // TYT Türkçe
        "paragraf", "anlam", "cümlede anlam", "sözcükte anlam", "noktalama", "yazım kuralları",
        // TYT & AYT Matematik
        "problem", "temel kavram", "rasyonel", "üslü", "köklü", "fonksiyon", "çarpanlara ayırma",
        "türev", "integral", "limit", "trigonometri", "logaritma", "dizi", "polinom", "olasılık",
        // Geometri
        "üçgen", "özel üçgen", "benzerlik", "analitik", "çember", "daire", "katı cisim",
        // Fizik
        "optik", "kuvvet", "hareket", "newton", "manyetizma", "indüksiyon", "dalga", "elektrik",
        "harmonik", "çembersel", "iş, güç", "enerji",
        // Kimya
        "mol", "kimyasal hesaplama", "asit", "baz", "tuz", "denge", "elektrokimya", "organik",
        "çözelti", "gazlar", "periyodik",
        // Biyoloji
        "hücre", "kalıtım", "ekoloji", "sistemler", "dolaşım", "sinir", "fotosentez", "solunum",
        "bitki biyolojisi", "protein sentezi",
        // Edebiyat & Tarih
        "divan", "cumhuriyet", "şiir bilgisi", "tanzimat"
    )

    fun isHighFrequency(examType: String, subjectId: String, topic: String): Boolean {
        val cleanTopic = topic.lowercase(Locale("tr", "TR")).trim()
        val cleanSubject = subjectId.lowercase(Locale("tr", "TR")).trim()

        if (cleanTopic.isBlank()) return false

        return YUKSEK_FREKANS_ANAHTARLAR.any { keyword ->
            cleanTopic.contains(keyword) || (keyword.length > 4 && cleanSubject.contains(keyword))
        }
    }
}

/**
 * Acil Eylem Matrisinde her bir konunun özet kartı
 */
data class QuadrantTopicItem(
    val examType: String,
    val subjectId: String,
    val topicName: String,
    val errorCount: Int,
    val solvedCount: Int,
    val totalCount: Int,
    val isHighFrequency: Boolean,
    val quadrant: ActionQuadrant,
    val urgencyScore: Int = errorCount * if (isHighFrequency) 3 else 1
)

/**
 * Öğrencinin sorularını ÖSYM frekansı ve hata sayısına göre 2x2 matriste gruplar
 */
fun partitionTopicsIntoQuadrants(
    questions: List<Soru>,
    errorThreshold: Int = 2
): Map<ActionQuadrant, List<QuadrantTopicItem>> {
    if (questions.isEmpty()) {
        return ActionQuadrant.entries.associateWith { emptyList() }
    }

    val grouped = questions.groupBy {
        Triple(it.examType.uppercase(Locale.ROOT), it.subjectId.lowercase(Locale.ROOT), it.topic.trim())
    }

    val items = grouped.mapNotNull { (key, topicQuestions) ->
        val (examType, subjectId, topic) = key
        if (topic.isBlank()) return@mapNotNull null

        val totalCount = topicQuestions.size
        val solvedCount = topicQuestions.count { it.isSolved }
        val unsolvedCount = totalCount - solvedCount
        val errorCount = topicQuestions.count { !it.isSolved || it.mistakeType != null }

        val isHighFreq = OsymKonuAnalizi.isHighFrequency(examType, subjectId, topic)
        // Çok Hata: Eğer hiç çözülememişse veya hata sayısı eşik değere eşit/büyükse
        val isHighError = (solvedCount == 0 && errorCount > 0) || (errorCount >= errorThreshold)
        val quadrant = ActionQuadrant.resolve(isHighFreq, isHighError)

        QuadrantTopicItem(
            examType = examType,
            subjectId = subjectId,
            topicName = topic,
            errorCount = errorCount,
            solvedCount = solvedCount,
            totalCount = totalCount,
            isHighFrequency = isHighFreq,
            quadrant = quadrant
        )
    }

    return ActionQuadrant.entries.associateWith { quad ->
        items.filter { it.quadrant == quad }
            .sortedWith(
                compareByDescending<QuadrantTopicItem> { it.urgencyScore }
                    .thenByDescending { it.errorCount }
                    .thenBy { it.topicName }
            )
    }
}

/**
 * Öğrenciye gösterilecek en acil 3 konuyu seçer (Öncelikle Acil Müdahale, ardından en çok hata yapılanlar)
 */
fun extractUrgentTopics(
    quadrantMap: Map<ActionQuadrant, List<QuadrantTopicItem>>,
    limit: Int = 3
): List<QuadrantTopicItem> {
    val acilMudahale = quadrantMap[ActionQuadrant.ACIL_MUDAHALE].orEmpty()
    if (acilMudahale.size >= limit) {
        return acilMudahale.take(limit)
    }

    // Eğer Acil Müdahale'de 3'ten az konu varsa, Pekiştir veya diğer hatalı konulardan tamamla
    val remainderNeeded = limit - acilMudahale.size
    val pekistir = quadrantMap[ActionQuadrant.PEKISTIR].orEmpty()
        .filter { it.errorCount > 0 }
        .take(remainderNeeded)

    return (acilMudahale + pekistir).take(limit)
}
