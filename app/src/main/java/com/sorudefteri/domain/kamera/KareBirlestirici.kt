package com.sorudefteri.domain.kamera

import kotlin.math.max
import kotlin.math.min

/**
 * Çoklu kare BİRLEŞTİRME motoru — SAF.
 *
 * Hizalı karelerin piksel-piksel MEDYANINI alır:
 *  - Gürültüyü bastırır (√n kazanç),
 *  - Hizalı-olmayan (hareketli) pikselleri "aykırı" olarak eler → ghosting azalır,
 *  - Ref (kare 0) köşelerinde hizalanamayan kenarlar REF karesinin orijinal
 *    pikseliyle doldurulur.
 *
 * `ofsetler[i]`: `KareHizalayici.hizala(kare0, kare_i)` sonucu; null ise o kare
 * çıkarılamaz, yeniden kare 0'dan örneklenmez — sadece medyan listesinden çıkar.
 *
 * Pixel düzeni: ARGB int (AARRGGBB) — android Bitmap.getPixels formatı.
 */
object KareBirlestirici {

    fun birlestir(kareler: List<IntArray>, gen: Int, yuk: Int, ofsetler: List<Ofset?>): IntArray {
        require(kareler.isNotEmpty()) { "kareler boş olamaz" }
        require(ofsetler.size == kareler.size) { "ofsetler kare sayısıyla aynı olmalı" }
        if (gen <= 0 || yuk <= 0) return IntArray(0)
        if (kareler.size == 1 || ofsetler.all { it == null }) return kareler[0].clone()

        val dxler = IntArray(kareler.size) { ofsetler[it]?.dx ?: 0 }
        val dyler = IntArray(kareler.size) { ofsetler[it]?.dy ?: 0 }

        // Tüm karelerin geçerli kesişim bölgesi
        var xLo = 0
        var xHi = gen - 1
        var yLo = 0
        var yHi = yuk - 1
        for (i in kareler.indices) {
            xLo = max(xLo, -dxler[i])
            xHi = min(xHi, gen - 1 - dxler[i])
            yLo = max(yLo, -dyler[i])
            yHi = min(yHi, yuk - 1 - dyler[i])
        }
        val kesisimGecerli = xLo <= xHi && yLo <= yHi
        if (!kesisimGecerli) {
            xLo = 0; xHi = gen - 1; yLo = 0; yHi = yuk - 1
        }

        val cikti = IntArray(gen * yuk)
        val kirmizilar = IntArray(kareler.size)
        val yesiller = IntArray(kareler.size)
        val maviler = IntArray(kareler.size)

        for (y in 0 until yuk) {
            val gecerliSatir = kesisimGecerli && y in yLo..yHi
            for (x in 0 until gen) {
                if (!gecerliSatir || x !in xLo..xHi) {
                    cikti[y * gen + x] = kareler[0][y * gen + x]  // kenar: REF karesi
                    continue
                }
                var say = 0
                for (i in kareler.indices) {
                    val xi = x + dxler[i]
                    val yi = y + dyler[i]
                    if (xi < 0 || xi >= gen || yi < 0 || yi >= yuk) continue
                    val p = kareler[i][yi * gen + xi]
                    kirmizilar[say] = (p shr 16) and 0xFF
                    yesiller[say] = (p shr 8) and 0xFF
                    maviler[say] = p and 0xFF
                    say++
                }
                if (say == 0) {
                    cikti[y * gen + x] = kareler[0][y * gen + x]
                    continue
                }
                val r = medyan(kirmizilar, say)
                val g = medyan(yesiller, say)
                val b = medyan(maviler, say)
                cikti[y * gen + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return cikti
    }

    private fun medyan(dizi: IntArray, say: Int): Int {
        if (say == 1) return dizi[0]
        // küçük n (≤5): seçerek standart sıralama
        val kopya = IntArray(say) { dizi[it] }
        java.util.Arrays.sort(kopya)
        return if (say % 2 == 1) kopya[say / 2] else (kopya[say / 2 - 1] + kopya[say / 2]) / 2
    }
}
