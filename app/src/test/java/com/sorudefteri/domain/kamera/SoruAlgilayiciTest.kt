package com.sorudefteri.domain.kamera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SoruAlgilayici tekli soru seçici testleri:
 * - merkez bandındaki en büyük küme seçilir,
 * - merkez dışı kümeler filtrelenir,
 * - satır hizası korumak küme kurarken işe yarar,
 * - boş/alıcız konumlarda null.
 */
class SoruAlgilayiciTest {

    private fun satir(sol: Int, ust: Int, genislik: Int, yukseklik: Int = 60) =
        Kutu(sol, ust, genislik, yukseklik)

    @Test
    fun `iki ayri soru blogunda merkezdeki buyuk blogu secer`() {
        val alanGen = 1000
        val alanYuk = 2000
        // Üst soru bloğu (y 100..460)
        val ustBlogunSatirlari = (0 until 5).map { satir(100, 100 + it * 70, 800) }
        // Orta soru bloğu (y 600..1010)
        val ortaBlogunSatirlari = (0 until 6).map { satir(100, 600 + it * 70, 800) }

        val secilen = SoruAlgilayici.sec(ustBlogunSatirlari + ortaBlogunSatirlari, alanGen, alanYuk)

        // orta blok: (600..1010) ± dolgu (padX=30, padY=100)
        assertNotNull(secilen)
        assertEquals(70, secilen!!.sol)
        assertEquals(500, secilen.ust)
        assertEquals(860, secilen.genislik)
        assertEquals(610, secilen.yukseklik)
    }

    @Test
    fun `merkez banki disindaki buyuk kumeler elenir`() {
        val alanGen = 1000
        val alanYuk = 2000
        val yanKume = (0 until 8).map { satir(1000, 100 + it * 70, 300) } // cx ~1.15 → merkez bandı dışı
        val merkezKume = (0 until 4).map { satir(100, 900 + it * 70, 800) }

        val secilen = SoruAlgilayici.sec(yanKume + merkezKume, alanGen, alanYuk)

        assertNotNull(secilen)
        assertTrue(secilen!!.merkezY in 950..1200) // merkez kümeye ait
    }

    @Test
    fun `cok kucuk satirlar elenir`() {
        val alanGen = 1000
        val alanYuk = 2000
        val mikro = (0 until 5).map { satir(200, 500 + it * 20, 400, yukseklik = 2) }

        val secilen = SoruAlgilayici.sec(mikro, alanGen, alanYuk)

        assertNull(secilen)
    }

    @Test
    fun `tek satirli kumeyi destekler`() {
        val alanGen = 1000
        val alanYuk = 2000
        val tek = listOf(satir(100, 900, 800))

        val secilen = SoruAlgilayici.sec(tek, alanGen, alanYuk)

        assertNotNull(secilen)
        assertEquals(70, secilen!!.sol)
        assertEquals(860, secilen.genislik) // 800 + 2*30 padX
    }

    @Test
    fun `bos girdi null doner`() {
        assertNull(SoruAlgilayici.sec(emptyList(), 1000, 2000))
        assertNull(SoruAlgilayici.sec(listOf(satir(0, 0, 100, 60)), 0, 0))
    }
}
