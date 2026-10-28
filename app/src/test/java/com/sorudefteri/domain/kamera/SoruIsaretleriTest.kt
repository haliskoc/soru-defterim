package com.sorudefteri.domain.kamera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * İçerik-farkındalıklı tespit testleri: SoruIsaretleri + SoruAlgilayici.secMetinli.
 * Eski sec() davranışı korunur (SoruAlgilayiciTest yeşil kalmalı).
 */
class SoruIsaretleriTest {

    @Test
    fun `soru basi desenleri`() {
        assertTrue(SoruIsaretleri.soruBasi("12. Aşağıdakilerden hangisi?"))
        assertTrue(SoruIsaretleri.soruBasi("5) İşlemin sonucu kaçtır?"))
        assertTrue(SoruIsaretleri.soruBasi("Soru 7: Hangisi doğrudur?"))
        assertFalse(SoruIsaretleri.soruBasi("A) Ankara"))
        assertFalse(SoruIsaretleri.soruBasi("ÇÖZÜM: x = 5"))
    }

    @Test
    fun `sik desenleri`() {
        assertTrue(SoruIsaretleri.sik("A) Ankara"))
        assertTrue(SoruIsaretleri.sik("C. 42"))
        assertTrue(SoruIsaretleri.sik("(B) İstanbul"))
        assertFalse(SoruIsaretleri.sik("12. Hangisi?"))
    }

    @Test
    fun `bolum sonu desenleri`() {
        assertTrue(SoruIsaretleri.bolumSonu("ÇÖZÜM: x = 5"))
        assertTrue(SoruIsaretleri.bolumSonu("Cevap: C"))
        assertFalse(SoruIsaretleri.bolumSonu("A) Ankara"))
    }

    private fun satir(sol: Int, ust: Int, genislik: Int, metin: String, yukseklik: Int = 60) =
        TarananSatir(Kutu(sol, ust, genislik, yukseklik), metin)

    @Test
    fun `soru basi bonusu komsu soruyu ayirir`() {
        val alanGen = 1000
        val alanYuk = 2000
        // İki soru bitişik (geometride tek küme olurdu), metinle bölünmeli
        val satirlar = listOf(
            satir(100, 100, 800, "3. Hangisi doğrudur?"),
            satir(100, 170, 800, "A) Ankara"),
            satir(100, 240, 800, "B) İzmir"),
            satir(100, 310, 800, "4. Hangisi yanlıştır?"),
            satir(100, 380, 800, "A) Elma"),
            satir(100, 450, 800, "B) Armut"),
            satir(100, 520, 800, "C) Kiraz"),
            satir(100, 590, 800, "D) Erik")
        )
        val secilen = SoruAlgilayici.secMetinli(satirlar, alanGen, alanYuk)
        assertNotNull(secilen)
        // 4. soru (daha çok şık) kazanmalı; üst sınırı 310 civarı olmalı
        assertTrue(secilen!!.ust in 150..310)
    }

    @Test
    fun `bolum sonu iceren kume elenir`() {
        val alanGen = 1000
        val alanYuk = 2000
        val soru = listOf(
            satir(100, 100, 800, "12. Soru metni üst"),
            satir(100, 170, 800, "soru devam satırı"),
            satir(100, 240, 800, "A) Şık"),
            satir(100, 310, 800, "B) Şık")
        )
        val cozum = listOf(
            satir(100, 900, 800, "ÇÖZÜM adım bir"),
            satir(100, 970, 800, "çözüm devam"),
            satir(100, 1040, 800, "çözüm devam"),
            satir(100, 1110, 800, "Cevap: C")
        )
        val secilen = SoruAlgilayici.secMetinli(soru + cozum, alanGen, alanYuk)
        assertNotNull(secilen)
        assertTrue(secilen!!.alt < 900)
    }

    @Test
    fun `roi disi satirlar elenir`() {
        val alanGen = 1000
        val alanYuk = 2000
        val yan = (0 until 6).map { satir(820, 100 + it * 70, 170, "7. Yan soru $it") }
        val merkez = listOf(
            satir(100, 900, 500, "8. Merkez soru metni"),
            satir(100, 970, 500, "devam satırı bir"),
            satir(100, 1040, 500, "devam satırı iki"),
            satir(100, 1110, 500, "A) Şık")
        )
        val roi = Kutu(50, 800, 600, 600)
        val secilen = SoruAlgilayici.secMetinli(yan + merkez, alanGen, alanYuk, roi = roi)
        assertNotNull(secilen)
        assertTrue(secilen!!.merkezY in 850..1300)
    }

    @Test
    fun `gevsek esik formulu koparmaz`() {
        val alanGen = 1000
        val alanYuk = 2000
        // 2.0× yükseklik boşluk (şekil yeri): eski 1.6 koparırdı, 2.2 tutar
        val satirlar = listOf(
            satir(100, 100, 800, "9. Şekilli soru üst"),
            satir(100, 280, 800, "A) Şık"),
            satir(100, 350, 800, "B) Şık")
        )
        val secilen = SoruAlgilayici.secMetinli(satirlar, alanGen, alanYuk)
        assertNotNull(secilen)
        assertEquals(100 - 30, secilen!!.sol)
        assertTrue(secilen.alt >= 350)
    }

    @Test
    fun `bos girdi null doner`() {
        assertNull(SoruAlgilayici.secMetinli(emptyList(), 1000, 2000))
        assertNull(
            SoruAlgilayici.secMetinli(
                listOf(TarananSatir(Kutu(0, 0, 100, 60), "x")), 0, 0
            )
        )
    }
}
