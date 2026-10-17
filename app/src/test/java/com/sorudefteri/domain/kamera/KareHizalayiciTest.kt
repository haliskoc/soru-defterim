package com.sorudefteri.domain.kamera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * KareHizalayici piramit çapraz korelasyon testleri.
 *
 * Desen: doğal görüntü SARIMI (yumuşak bantlar + keskin kenarlı özellikler).
 * Saf pseudo-noise kullanılmıyor — kaba (adım 4) aşaması yalnız 4px-böle hizalı
 * içeriği bulabildiğinden sahte-gürültü sahte-tepki (aliasing) yaratır.
 */
class KareHizalayiciTest {

    /** Aperiodik damlalı-nokta alanı: yumuşak damlar + kum gürültü (bambu gibi davranır). */
    private fun hashDeger(x: Int, y: Int): Int {
        var h = x * 374761393 + y * 668265263
        h = h xor (h ushr 13)
        h *= 1274126177
        return (h ushr 5) % 256
    }

    private fun damlaliDesen(gen: Int, yuk: Int): IntArray {
        val dizi = IntArray(gen * yuk)
        for (y in 0 until yuk) {
            for (x in 0 until gen) {
                val nx = x / 6
                val ny = y / 6
                val fx = (x % 6) / 6.0
                val fy = (y % 6) / 6.0
                val duzgun = hashDeger(nx, ny) * (1 - fx) * (1 - fy) +
                    hashDeger(nx + 1, ny) * fx * (1 - fy) +
                    hashDeger(nx, ny + 1) * (1 - fx) * fy +
                    hashDeger(nx + 1, ny + 1) * fx * fy
                val v = (18 + duzgun * 0.75 + hashDeger(x, y) * 0.35).roundToInt()
                dizi[y * gen + x] = v.coerceIn(8, 255)
            }
        }
        return dizi
    }

    private fun yumusakDesen(gen: Int, yuk: Int): IntArray {
        val dizi = IntArray(gen * yuk)
        for (y in 0 until yuk) {
            for (x in 0 until gen) {
                dizi[y * gen + x] = x * 3
            }
        }
        return dizi
    }

    private fun kaydirmali(dizi: IntArray, gen: Int, yuk: Int, dx: Int, dy: Int): IntArray {
        val cikti = IntArray(gen * yuk)
        for (y in 0 until yuk) {
            for (x in 0 until gen) {
                val kx = (x - dx).coerceIn(0, gen - 1)
                val ky = (y - dy).coerceIn(0, yuk - 1)
                cikti[y * gen + x] = dizi[ky * gen + kx]
            }
        }
        return cikti
    }

    @Test
    fun `bilinen kayma kurtarilir`() {
        val gen = 64
        val yuk = 64
        val temel = damlaliDesen(gen, yuk)
        val diger = kaydirmali(temel, gen, yuk, dx = 6, dy = 8)

        val ofset = KareHizalayici.hizala(temel, diger, gen, yuk)

        assertNotNull(ofset)
        assertTrue(abs(ofset!!.dx - 6) <= 2)
        assertTrue(abs(ofset.dy - 8) <= 2)
        assertTrue(ofset.skor >= 0.8)
    }

    @Test
    fun `kaymasiz kareler sifir ofset verir`() {
        val gen = 48
        val yuk = 48
        val temel = damlaliDesen(gen, yuk)

        val ofset = KareHizalayici.hizala(temel, temel.clone(), gen, yuk)

        assertNotNull(ofset)
        assertEquals("beklenmedik xo: ${ofset!!.dx}", 0, ofset.dx)
        assertEquals("beklenmedik yo: ${ofset.dy}", 0, ofset.dy)
    }

    @Test
    fun `duz goruntude hizalama null doner`() {
        val gen = 48
        val yuk = 48
        val duz = IntArray(gen * yuk) { 120 }

        assertNull(KareHizalayici.hizala(duz, duz.clone(), gen, yuk))
    }

    @Test
    fun `tutarsiz boyutta null doner`() {
        val temel = damlaliDesen(64, 64)
        val diger = IntArray(32 * 32) { 100 } // düz → son aşamada korunur
        assertNull(KareHizalayici.hizala(temel, diger, 32, 32))
    }

    @Test
    fun `keskinlik olcu desende pozitif duzde sifir`() {
        val gen = 64
        val yuk = 64
        val desen = damlaliDesen(gen, yuk)
        val duz = IntArray(gen * yuk) { 100 }

        val duzSkor = KareHizalayici.keskinlikOlcu(duz, gen, yuk)
        val desenSkor = KareHizalayici.keskinlikOlcu(desen, gen, yuk)

        assertEquals(0.0, duzSkor, 1e-9)
        assertTrue(desenSkor > 10.0)
    }

    @Test
    fun `keskinlik olcu yumusak gecisten daha buyuk`() {
        val gen = 64
        val yuk = 64
        val yumusak = yumusakDesen(gen, yuk)
        val desen = damlaliDesen(gen, yuk)

        assertTrue(KareHizalayici.keskinlikOlcu(desen, gen, yuk) > KareHizalayici.keskinlikOlcu(yumusak, gen, yuk))
    }
}
