package com.sorudefteri.domain.model

import androidx.compose.runtime.Immutable

/**
 * Soru Reels akışında gösterilen ögeler.
 * Hem öğrencinin sorularını hem de aralara serpiştirilen hap bilgi & formül kartlarını destekler.
 */
@Immutable
sealed class ReelsItem {
    abstract val id: String

    @Immutable
    data class SoruItem(
        val soru: Soru
    ) : ReelsItem() {
        override val id: String get() = "soru_${soru.id}"
    }

    @Immutable
    data class FlashcardItem(
        override val id: String,
        val ders: String,
        val konu: String,
        val title: String,
        val summary: String,
        val formula: String? = null,
        val tip: String? = null,
        val categoryEmoji: String = "⚡",
        val gradientColors: List<Long> = listOf(0xFF0F172A, 0xFF1E293B)
    ) : ReelsItem()
}

/**
 * YKS / TYT / AYT için kritik sınav hap bilgileri ve formül veritabanı.
 */
object YksFlashcardsRepository {
    val curatedCards: List<ReelsItem.FlashcardItem> = listOf(
        ReelsItem.FlashcardItem(
            id = "fc_mat_1",
            ders = "Matematik",
            konu = "Türev",
            title = "L'Hôpital Belirsizlik Kuralı",
            summary = "Limit sıfır bölü sıfır veya sonsuz bölü sonsuz belirsizliği verdiğinde pay ve paydanın ayrı ayrı türevini al.",
            formula = "\\lim_{x \\to a} \\frac{f(x)}{g(x)} = \\lim_{x \\to a} \\frac{f'(x)}{g'(x)}",
            tip = "Sakın bölümün türevini alma! Payın türevini üste, paydanın türevini alta yaz.",
            categoryEmoji = "📐",
            gradientColors = listOf(0xFF1E1B4B, 0xFF312E81)
        ),
        ReelsItem.FlashcardItem(
            id = "fc_mat_2",
            ders = "Matematik",
            konu = "Parabol",
            title = "Tepe Noktası & Simetri Ekseni",
            summary = "${'$'}f(x) = ax^2 + bx + c${'$'} parabolünün simetri ekseni ${'$'}r${'$'} doğrusudur. Köklerin aritmetik ortalamasıdır.",
            formula = "r = -\\frac{b}{2a}, \\quad k = f(r) = \\frac{4ac - b^2}{4a}",
            tip = "Parabol simetri eksenine eşit uzaklıktaki noktalarda daima aynı y değerini alır: ${'$'}f(r - x) = f(r + x)${'$'}.",
            categoryEmoji = "📈",
            gradientColors = listOf(0xFF14532D, 0xFF166534)
        ),
        ReelsItem.FlashcardItem(
            id = "fc_mat_3",
            ders = "Matematik",
            konu = "Diziler",
            title = "Geometrik Dizi Ortanca Terim",
            summary = "Sonlu bir geometrik dizide baştan ve sondan eşit uzaklıktaki terimlerin çarpımı birbirine eşittir.",
            formula = "a_n^2 = a_{n-k} \\cdot a_{n+k} \\implies a_1 \\cdot a_3 = a_2^2",
            tip = "${'$'}c_1, c_2, c_3, c_4, c_5${'$'} geometrik dizisinde ${'$'}c_1 \\cdot c_5 = c_2 \\cdot c_4 = c_3^2${'$'} kuralını doğrudan kullan.",
            categoryEmoji = "🔢",
            gradientColors = listOf(0xFF4C1D95, 0xFF581C87)
        ),
        ReelsItem.FlashcardItem(
            id = "fc_fizik_1",
            ders = "Fizik",
            konu = "Kuvvet ve Hareket",
            title = "Newton'un Temel Dinamik Yasası",
            summary = "Bir cisme etki eden net kuvvet sıfırdan farklıysa cisim ivmeli hareket yapar.",
            formula = "\\vec{F}_{net} = m \\cdot \\vec{a}",
            tip = "Sürtünmeli yüzeylerde sürtünme kuvvetini daima hareket yönünün tersine çiz: ${'$'}F_s = k \\cdot N${'$'}.",
            categoryEmoji = "🚀",
            gradientColors = listOf(0xFF701A75, 0xFF4A044E)
        ),
        ReelsItem.FlashcardItem(
            id = "fc_fizik_2",
            ders = "Fizik",
            konu = "Modern Fizik",
            title = "Einstein Fotoelektrik Denklemi",
            summary = "Gelen foton enerjisi, elektronu sökme (bağlanma) işi ve fırlayan fotoelektronun kinetik enerjisine harcanır.",
            formula = "E_{foton} = h \\cdot \\nu = E_b + E_{kinetik}",
            tip = "Işığın şiddetini (parlaklığını) artırmak sökülen elektron sayısını artırır, kinetik enerjiyi DEĞİŞTİRMEZ!",
            categoryEmoji = "⚛️",
            gradientColors = listOf(0xFF0C4A6E, 0xFF075985)
        ),
        ReelsItem.FlashcardItem(
            id = "fc_kimya_1",
            ders = "Kimya",
            konu = "Gazlar",
            title = "İdeal Gaz Denklemi (Paran Varsa Ne Rahat)",
            summary = "Gazların basınç, hacim, mol sayısı ve sıcaklık arasındaki temel bağıntısıdır.",
            formula = "P \\cdot V = n \\cdot R \\cdot T",
            tip = "Sıcaklığı daima Kelvin (${'$'}T = t + 273${'$'}) olarak almayı unutma, °C cinsinden yazmak en yaygın hatadır!",
            categoryEmoji = "🧪",
            gradientColors = listOf(0xFF831843, 0xFF9D174D)
        ),
        ReelsItem.FlashcardItem(
            id = "fc_biyo_1",
            ders = "Biyoloji",
            konu = "Hücre Bölünmeleri",
            title = "Mitoz vs Mayoz Altın Farkı",
            summary = "Mitoz vücut hücrelerinde görülür ve çeşitlilik sağlamaz. Mayoz eşey ana hücrelerinde görülür ve crossing-over ile çeşitlilik yaratır.",
            formula = "\\text{Mitoz: } 2n \\to 2n \\quad | \\quad \\text{Mayoz: } 2n \\to n",
            tip = "Kardeş kromatit ayrılması hem Mitoz Anafazında hem Mayoz II Anafazında gerçekleşir.",
            categoryEmoji = "🧬",
            gradientColors = listOf(0xFF064E3B, 0xFF065F46)
        ),
        ReelsItem.FlashcardItem(
            id = "fc_geo_1",
            ders = "Geometri",
            konu = "Üçgenler",
            title = "Özel Dik Üçgenler ve Benzerlik Oranı",
            summary = "3-4-5, 5-12-13, 8-15-17, 7-24-25 katları ve benzerlik oranı ${'$'}k${'$'} olan iki üçgenin alan oranı ${'$'}k^2${'$'} dir.",
            formula = "\\text{Benzerlik Oranı } = k \\implies \\frac{\\text{Alan}_1}{\\text{Alan}_2} = k^2",
            tip = "Hacim oranı sorulursa benzerlik oranının küpünü (${'$'}k^3${'$'}) almayı unutma.",
            categoryEmoji = "📏",
            gradientColors = listOf(0xFF1E3A5F, 0xFF14273E)
        )
    )
}
