package com.sorudefteri.domain.kamera

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Hizalama sonucu: `temel(x, y) == diger(x + dx, y + dy)` eşleşmesini sağlayan öteleme.
 * `skor` 0..1 arası normalize çapraz korelasyondur.
 */
data class Ofset(val dx: Int, val dy: Int, val skor: Double)

/**
 * Çoklu kare ("uzun pozlama") HİZALAMA motoru — SAF.
 *
 * Kaba'dan inceye üç basamaklı piramit çapraz korelasyon:
 *  - Adım 4 (incelt): ±16 kaba-px hareket yakalanır (görüntüde ±64 px kapsam),
 *  - Adım 2: ±3 ince düzelme (merkez yarıda),
 *  - Adım 1: ±1 son rötuş.
 *
 * Girdi: gri (luma) değerli IntArray diziler. Saf Kotlin'dir, test edilebilir.
 */
object KareHizalayici {

    /** Bundan düşük korelasyonlu hizalamaya güvenilmez (null döner). */
    const val MIN_SKOR = 0.20

    fun hizala(temel: IntArray, diger: IntArray, gen: Int, yuk: Int): Ofset? {
        if (gen <= 4 || yuk <= 4) return null
        if (temel.size < gen * yuk || diger.size < gen * yuk) return null

        val kabaAd = 4
        if (gen / kabaAd >= 8 && yuk / kabaAd >= 8) {
            val kaba = tara(
                temel = incelt(temel, gen, yuk, kabaAd),
                diger = incelt(diger, gen, yuk, kabaAd),
                gen = gen / kabaAd,
                yuk = yuk / kabaAd,
                yaricap = 16,
                merkezDx = 0,
                merkezDy = 0,
                ornekAtla = 1,
                merkezBolge = false
            )
            if (kaba == null || kaba.skor < MIN_SKOR) {
                return kaba?.takeIf { it.skor >= MIN_SKOR }?.let { Ofset(it.dx * kabaAd, it.dy * kabaAd, it.skor) }
            }
            val orta = tara(
                temel, diger, gen, yuk,
                yaricap = 3,
                merkezDx = kaba.dx * kabaAd,
                merkezDy = kaba.dy * kabaAd,
                ornekAtla = 2,
                merkezBolge = true
            )
            if (orta == null || orta.skor < MIN_SKOR) {
                return Ofset(kaba.dx * kabaAd, kaba.dy * kabaAd, kaba.skor)
            }
            val ince = tara(
                temel, diger, gen, yuk,
                yaricap = 1,
                merkezDx = orta.dx,
                merkezDy = orta.dy,
                ornekAtla = 2,
                merkezBolge = true
            )
            return ince ?: orta
        }

        val dogrusal = tara(temel, diger, gen, yuk, 4, 0, 0, ornekAtla = 1, merkezBolge = false)
        return dogrusal?.takeIf { it.skor >= MIN_SKOR }
    }

    /**
     * Keskinlik ölçümü: Laplacian varyansı (2 adımda örnekleme).
     * Hizalama başarısızsa EN KESKİN kare seçilir.
     */
    fun keskinlikOlcu(gri: IntArray, gen: Int, yuk: Int): Double {
        if (gen < 3 || yuk < 3) return 0.0
        var toplam = 0.0
        var kareToplam = 0.0
        var say = 0
        for (y in 1 until yuk - 1 step 2) {
            for (x in 1 until gen - 1 step 2) {
                val i = y * gen + x
                val merkez = gri[i].toDouble()
                val lap = 4 * merkez - gri[i - 1] - gri[i + 1] - gri[i - gen] - gri[i + gen]
                toplam += lap
                kareToplam += lap * lap
                say++
            }
        }
        if (say == 0) return 0.0
        val ort = toplam / say
        return kareToplam / say - ort * ort
    }

    private fun incelt(a: IntArray, gen: Int, yuk: Int, adim: Int): IntArray {
        val ng = gen / adim
        val ny = yuk / adim
        val cikti = IntArray(ng * ny)
        for (y in 0 until ny) {
            val oku = (y * adim) * gen
            val yaz = y * ng
            for (x in 0 until ng) cikti[yaz + x] = a[oku + x * adim]
        }
        return cikti
    }

    /**
     * Öteleme taraması: (dx, dy) ∈ [merkez ± yaricap]; her kaydırma için
     * geçerli kesişim bölgesinde normalize çapraz korelasyon hesaplar.
     */
    private fun tara(
        temel: IntArray,
        diger: IntArray,
        gen: Int,
        yuk: Int,
        yaricap: Int,
        merkezDx: Int,
        merkezDy: Int,
        ornekAtla: Int,
        merkezBolge: Boolean
    ): Ofset? {
        val istA = istatistik(temel, gen, yuk, ornekAtla, merkezBolge)
        val istB = istatistik(diger, gen, yuk, ornekAtla, merkezBolge)
        val ortA = istA.first
        val ortB = istB.first
        val degA = istA.second
        val degB = istB.second
        if (degA < 1e-6 || degB < 1e-6) return null

        // Kaydırma ufkunda küçülen örtüşmede tekil/az örnekle bölge +1 skoru yaratır;
        // en az bu kadar örnek GIRIMET asgari tutulur.
        val enAzOrnek = (gen * yuk / 16).coerceAtLeast(8)
        val xDikkat = gen / 4
        val yDikkat = yuk / 4

        var enIyi: Ofset? = null
        var enIyiSkor = -1.0

        for (dy in merkezDy - yaricap..merkezDy + yaricap) {
            val yLo = max(0, -dy)
            val yHi = min(yuk - 1, yuk - 1 - dy)
            if (yLo > yHi) continue
            for (dx in merkezDx - yaricap..merkezDx + yaricap) {
                val xLo = max(0, -dx)
                val xHi = min(gen - 1, gen - 1 - dx)
                if (xLo > xHi) continue

                val yBas = if (merkezBolge) max(yLo, yDikkat) else yLo
                val ySon = if (merkezBolge) min(yHi, yuk - 1 - yDikkat) else yHi
                val xBas = if (merkezBolge) max(xLo, xDikkat) else xLo
                val xSon = if (merkezBolge) min(xHi, gen - 1 - xDikkat) else xHi
                if (yBas > ySon || xBas > xSon) continue

                // Normlar EŞLEŞTİRİLEN piksellerin aynısı üzerinden toplanır:
                // kaydırmada bölge farkı sapması kalkar, skor ≤ 1 otomatik sağlanır.
                var sAA = 0.0
                var sBB = 0.0
                var sAB = 0.0
                var say = 0
                var y = yBas
                while (y <= ySon) {
                    val satirA = y * gen
                    val satirB = (y + dy) * gen
                    var x = xBas
                    while (x <= xSon) {
                        val av = temel[satirA + x] - ortA
                        val bv = diger[satirB + x + dx] - ortB
                        sAA += av * av
                        sBB += bv * bv
                        sAB += av * bv
                        say++
                        x += ornekAtla
                    }
                    y += ornekAtla
                }
                if (say < enAzOrnek) continue
                val payda = sqrt(sAA * sBB)
                if (payda < 1e-6) continue
                val skor = sAB / payda
                if (skor > enIyiSkor) {
                    enIyiSkor = skor
                    enIyi = Ofset(dx, dy, skor)
                }
            }
        }
        return enIyi
    }

    /** ortalama + toplam karesel sapma (örnekleme adımı ve bölge seçimli). */
    private fun istatistik(
        dizi: IntArray,
        gen: Int,
        yuk: Int,
        ornekAtla: Int,
        merkezBolge: Boolean
    ): Pair<Double, Double> {
        val xBas = if (merkezBolge) gen / 4 else 0
        val xSon = if (merkezBolge) gen - 1 - gen / 4 else gen - 1
        val yBas = if (merkezBolge) yuk / 4 else 0
        val ySon = if (merkezBolge) yuk - 1 - yuk / 4 else yuk - 1
        var toplam = 0.0
        var say = 0
        var y = yBas
        while (y <= ySon) {
            val satir = y * gen
            var x = xBas
            while (x <= xSon) {
                toplam += dizi[satir + x]
                say++
                x += ornekAtla
            }
            y += ornekAtla
        }
        if (say == 0) return Pair(0.0, 0.0)
        val ort = toplam / say
        var degToplam = 0.0
        y = yBas
        while (y <= ySon) {
            val satir = y * gen
            var x = xBas
            while (x <= xSon) {
                val fark = dizi[satir + x] - ort
                degToplam += fark * fark
                x += ornekAtla
            }
            y += ornekAtla
        }
        return Pair(ort, degToplam)
    }
}
