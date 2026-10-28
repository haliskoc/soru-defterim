package com.sorudefteri.domain.kamera

/**
 * Soru sınır sinyalleri (SAF, YKS/LGS dizgisi).
 *
 * ML Kit'in okuduğu satır metninden soru başı / şık / bölüm-sonu ayırt eder.
 * Geometrik kümelemenin "kör" kaldığı yerde (formül boşluğu, komşu soru)
 * sınır kararını içeriğe dayandırır.
 */
object SoruIsaretleri {

    private val SORU_BASI = listOf(
        Regex("^\\s*\\d+\\s*[-.)]\\s*\\S"),
        Regex("^\\s*soru\\s*\\d+", RegexOption.IGNORE_CASE),
        Regex("^\\s*\\d+\\s*\\.\\s*soru", RegexOption.IGNORE_CASE)
    )

    private val SIK = listOf(
        Regex("^\\s*[A-Ea-e]\\s*[).]\\s*\\S"),
        Regex("^\\s*\\(\\s*[A-Ea-e]\\s*\\)")
    )

    private val BOLUM_SONU = listOf(
        Regex("çözüm", RegexOption.IGNORE_CASE),
        Regex("cevap\\s*:", RegexOption.IGNORE_CASE),
        Regex("^\\s*cevap\\s*[A-Ea-e]?\\s*$", RegexOption.IGNORE_CASE)
    )

    fun soruBasi(metin: String): Boolean = SORU_BASI.any { it.containsMatchIn(metin) }

    fun sik(metin: String): Boolean = SIK.any { it.containsMatchIn(metin) }

    fun bolumSonu(metin: String): Boolean = BOLUM_SONU.any { it.containsMatchIn(metin) }
}
