package com.sorudefteri.domain.kamera

/**
 * Kamera saf mantığı için paylaşılan veri modelleri.
 * Android bağımlılığı YOKTUR — birim testlerde aynen kullanılır.
 */

/** 🫧 Piksel cinsinden dikdörtgen (sol-üst köşe + boyutlar). */
data class Kutu(
    val sol: Int,
    val ust: Int,
    val genislik: Int,
    val yukseklik: Int
) {
    val sag: Int get() = sol + genislik
    val alt: Int get() = ust + yukseklik
    val merkezX: Int get() = sol + genislik / 2
    val merkezY: Int get() = ust + yukseklik / 2
    val alan: Long get() = genislik.toLong() * yukseklik

    fun birlestir(diger: Kutu): Kutu {
        val sol2 = minOf(sol, diger.sol)
        val ust2 = minOf(ust, diger.ust)
        val sag2 = maxOf(sag, diger.sag)
        val alt2 = maxOf(alt, diger.alt)
        return Kutu(sol2, ust2, sag2 - sol2, alt2 - ust2)
    }
}

/** 🫧 0..1 arası normalize edilmiş kutu (normalda görüntü genişliğine/yüksekliğine oranla). */
data class KutuOrani(
    val sol: Double,
    val ust: Double,
    val genislik: Double,
    val yukseklik: Double
) {
    val sag: Double get() = sol + genislik
    val alt: Double get() = ust + yukseklik
    val merkezX: Double get() = sol + genislik / 2.0
    val merkezY: Double get() = ust + yukseklik / 2.0

    fun kisnala(): KutuOrani = KutuOrani(
        sol = sol.coerceIn(0.0, 1.0),
        ust = ust.coerceIn(0.0, 1.0),
        genislik = genislik.coerceIn(0.0, 1.0 - sol.coerceIn(0.0, 1.0)),
        yukseklik = yukseklik.coerceIn(0.0, 1.0 - ust.coerceIn(0.0, 1.0))
    )
}

/** Kamera çekim modu: tekli soru (kırpma + tarama) / çoklu soru (geniş kare). */
enum class KameraModu { TEKLI, COKLU }

/** Tekli mod taramasının durum makinesi durumu. */
enum class TaramaDurumu { ARANIYOR, BULUNDU, KILITLI }

/** ML Kit satırı: kutu + tanınan metin (içerik-farkındalıklı sınır için). */
data class TarananSatir(
    val kutu: Kutu,
    val metin: String = ""
)
