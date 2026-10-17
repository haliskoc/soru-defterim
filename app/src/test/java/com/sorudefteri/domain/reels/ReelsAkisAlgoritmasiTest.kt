package com.sorudefteri.domain.reels

import com.sorudefteri.domain.model.HataTipi
import com.sorudefteri.domain.model.Soru
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ReelsAkisAlgoritmasi] birim testleri:
 * yenilik (yarı-ömür) etkisi, ağırlıklı karıştırma determinizmi,
 * tekrar bloğu sırası ve eski soru taban ağırlığı (unutma sigortası).
 */
class ReelsAkisAlgoritmasiTest {

    private fun soru(
        id: Long,
        eklenmeGunOnce: Double = 365.0,
        isSolved: Boolean = true,
        nextReviewDate: Long? = SABIT_ZAMAN + 730L * 86_400_000L, // 2 yıl sonrası (uzak gelecek)
        mistakeType: HataTipi? = null,
        isFavorite: Boolean = false
    ): Soru {
        val simdi = SABIT_ZAMAN
        return Soru(
            id = id,
            imagePath = "soru.jpg",
            examType = "TYT",
            subjectId = "matematik",
            topic = "Konu$id",
            isSolved = isSolved,
            nextReviewDate = nextReviewDate,
            mistakeType = mistakeType,
            isFavorite = isFavorite,
            createdAt = (simdi - (eklenmeGunOnce * 86_400_000L)).toLong()
        )
    }

    @Test
    fun `yeni eklenen sorular eski sorulardan belirgin sekilde onde yer alir`() {
        val eskiListe = (0 until 100).map { soru(id = 100L + it) }
        val yeni1 = soru(id = 1L, eklenmeGunOnce = 0.5, isSolved = false, nextReviewDate = null)
        val yeni2 = soru(id = 2L, eklenmeGunOnce = 1.0, isSolved = false, nextReviewDate = null)
        val tumu = eskiListe + listOf(yeni1, yeni2)

        var yeniToplamPozisyon = 0.0
        var eskiToplamPozisyon = 0.0
        for (tohum in 1..200L) {
            val akis = ReelsAkisAlgoritmasi.akisKuras(tumu, SABIT_ZAMAN, tohum)
            yeniToplamPozisyon += akis.indexOfFirst { it.id == 1L }
            yeniToplamPozisyon += akis.indexOfFirst { it.id == 2L }
            val eskiPozisyonlar = akis.mapIndexedNotNull { index, item ->
                if (item.id >= 100L) index else null
            }
            eskiToplamPozisyon += eskiPozisyonlar.average()
        }

        val yeniOrtalama = yeniToplamPozisyon / (200.0 * 2)
        val eskiOrtalama = eskiToplamPozisyon / 200.0

        val ornekAkis = ReelsAkisAlgoritmasi.akisKuras(tumu, SABIT_ZAMAN, 1L)
        assertTrue(
            "Yeni sorular önde bekleniyor (yeni≈$yeniOrtalama, eski≈$eskiOrtalama, " +
                "tohum1 başta=${ornekAkis.take(3).map { it.id }}, sonda=${ornekAkis.takeLast(3).map { it.id }}, " +
                "yeniSkor=${ReelsAkisAlgoritmasi.skorHesapla(yeni1, SABIT_ZAMAN).toplam}, " +
                "eskiSkor=${ReelsAkisAlgoritmasi.skorHesapla(eskiListe.first(), SABIT_ZAMAN).toplam})",
            yeniOrtalama < eskiOrtalama / 3.0
        )
    }

    @Test
    fun `tekrari gelen sorular akisin en basinda blok olusturur`() {
        val gecikmis1 = soru(
            id = 10L,
            nextReviewDate = SABIT_ZAMAN - 20L * 86_400_000L // 20 gün gecikmiş
        )
        val gecikmis2 = soru(
            id = 11L,
            nextReviewDate = SABIT_ZAMAN - 3L * 86_400_000L // 3 gün gecikmiş
        )
        val yapilacak = soru(id = 12L, isSolved = false, nextReviewDate = null) // backlog
        val gelecekTekrar = soru(
            id = 13L,
            nextReviewDate = SABIT_ZAMAN + 5L * 86_400_000L
        )
        val akis = ReelsAkisAlgoritmasi.akisKuras(
            listOf(gelecekTekrar, gecikmis1, yapilacak, gecikmis2),
            SABIT_ZAMAN,
            tohum = 7L
        )

        // En gecikmiş tekrar tam olarak ilk sırada.
        assertEquals(10L, akis[0].id)
        // Acil blok: gecikmişler + backlog akışın başında; gelecekTekrar ağırlıklı bölüme düşer.
        val ilkUc = akis.take(3).map { it.id }.toSet()
        assertTrue(ilkUc.contains(10L))
        assertTrue(ilkUc.contains(11L))
        assertTrue(ilkUc.contains(12L))
        assertTrue(
            "Gecikmiş tekrar (20 gün) 3 gün gecikmişten önce olmalı",
            akis.indexOfFirst { it.id == 10L } < akis.indexOfFirst { it.id == 11L }
        )
    }

    @Test
    fun `akisKuras hicbir soruyu dusurmez ve listeyi tam dondurur`() {
        val karisik = listOf(
            soru(id = 1L, eklenmeGunOnce = 0.0, isSolved = false, nextReviewDate = null),
            soru(id = 2L, eklenmeGunOnce = 3.0),
            soru(id = 3L, nextReviewDate = SABIT_ZAMAN - 86_400_000L), // due
            soru(id = 4L, eklenmeGunOnce = 400.0), // çok eski, taban ağırlık
            soru(id = 5L, mistakeType = HataTipi.DIKKAT_HATASI),
            soru(id = 6L, isFavorite = true)
        )
        val akis = ReelsAkisAlgoritmasi.akisKuras(karisik, SABIT_ZAMAN, 42L)

        assertEquals(karisik.size, akis.size)
        assertEquals(karisik.map { it.id }.toSet(), akis.map { it.id }.toSet())
        // Akışta tekrar yok:
        assertEquals(akis.size, akis.map { it.id }.distinct().size)
    }

    @Test
    fun `cok eski sorular taban agirlikla akista kalmaya devam eder`() {
        val yeniSoru = soru(id = 1L, eklenmeGunOnce = 0.0, isSolved = false, nextReviewDate = null)
        val cokEski = (0 until 20).map { soru(id = 100L + it, eklenmeGunOnce = 300.0) }
        val akis = ReelsAkisAlgoritmasi.akisKuras(
            cokEski + listOf(yeniSoru),
            SABIT_ZAMAN,
            tohum = 5L
        )

        // Eski soruların tümü listede (düşürülmeden) ve son üçlüde en fazla kalıyor:
        assertEquals(cokEski.size + 1, akis.size)
        assertTrue(akis.map { it.id }.containsAll(cokEski.map { it.id }))
    }

    @Test
    fun `ayni girdi ve tohumla akis tam olarak ayni sirayi uretir`() {
        val liste = (1L..30L).map { soru(id = it, eklenmeGunOnce = it.toDouble()) }
        val birinci = ReelsAkisAlgoritmasi.akisKuras(liste, SABIT_ZAMAN, tohum = 99L)
        val ikinci = ReelsAkisAlgoritmasi.akisKuras(liste, SABIT_ZAMAN, tohum = 99L)
        assertEquals(birinci.map { it.id }, ikinci.map { it.id })
    }

    @Test
    fun `farkli tohum farkli dogal dagilim uretir`() {
        val liste = (1L..40L).map { soru(id = it, eklenmeGunOnce = it.toDouble()) }
        val tohumA = ReelsAkisAlgoritmasi.akisKuras(liste, SABIT_ZAMAN, tohum = 1L).map { it.id }
        var degisikBulundu = false
        for (tohum in 2L..50L) {
            if (ReelsAkisAlgoritmasi.akisKuras(liste, SABIT_ZAMAN, tohum).map { it.id } != tohumA) {
                degisikBulundu = true
                break
            }
        }
        assertTrue(
            "Ağırlıklı karıştırma tohuma duyarlı olmalı (tohum1=${tohumA.take(5)}, " +
                "örnek tohum2=${ReelsAkisAlgoritmasi.akisKuras(liste, SABIT_ZAMAN, 2L).map { it.id }.take(5)})",
            degisikBulundu
        )
    }

    @Test
    fun `skorHesapla yeni cozulmemis hatali skorun eski tabaninin cok uzerinde`() {
        val yeni = soru(
            id = 1L,
            eklenmeGunOnce = 0.0,
            isSolved = false,
            nextReviewDate = null,
            mistakeType = HataTipi.BILGI_EKSIKLIGI
        )
        val eski = soru(id = 2L, eklenmeGunOnce = 200.0)

        val yeniSkor = ReelsAkisAlgoritmasi.skorHesapla(yeni, SABIT_ZAMAN)
        val eskiSkor = ReelsAkisAlgoritmasi.skorHesapla(eski, SABIT_ZAMAN)

        assertTrue(
            "yeni(=${yeniSkor.toplam}, yenilik=${yeniSkor.yenilik}, tekrar=${yeniSkor.tekrarVurgusu}, " +
                "icerik=${yeniSkor.icerikVurgusu}) vs eski*2(=${eskiSkor.toplam * 2.0}, " +
                "eskiTekrar=${eskiSkor.tekrarVurgusu}, eskiYenilik=${eskiSkor.yenilik})",
            yeniSkor.toplam > eskiSkor.toplam * 2.0
        )
        // Yaş eşiği geçmiş her soru en az taban ağırlıkla görünür (unutma sigortası):
        assertTrue(eskiSkor.toplam >= ReelsAkisAlgoritmasi.Ayarlar().eskiSoruTabanAgirligi)
        // Yeni soru 0 gün yaşta yenilik bileşeni tam (1.0) olmalı:
        assertEquals(1.0, yeniSkor.yenilik, 1e-9)
    }

    @Test
    fun `gunTohumu gun icinde kararli gunler arasinda degisir`() {
        val sabah = ReelsAkisAlgoritmasi.gunTohumu(SABIT_ZAMAN)
        val aksam = ReelsAkisAlgoritmasi.gunTohumu(SABIT_ZAMAN + 5L * 3600 * 1000) // 5 saat sonrası
        val yarin = ReelsAkisAlgoritmasi.gunTohumu(SABIT_ZAMAN + 86_400_000L)

        assertEquals(sabah, aksam)
        assertTrue(sabah != yarin)
    }

    companion object {
        private const val SABIT_ZAMAN = 1_760_000_000_000L
    }
}
