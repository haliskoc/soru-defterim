package com.sorudefteri.domain.kamera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * KirpmaHesaplayici saf matematiği testleri:
 * - FILL_CENTER ölçekleme + merkezleme,
 * - ön kamera yansıtma (ayna),
 * - oran↔px dönüşümlerinin simetrisi,
 * - statik kılavuz çerçevesi.
 */
class KirpmaHesaplayiciTest {

    @Test
    fun `gorunum tumu ozel goruntu sinirlarina eslesir`() {
        // Görüntü 1000x1000, görünüm 500x550 → s=2, dH=500, vOffY=25
        val oran = KirpmaHesaplayici.gorunumPxiGorselOranina(
            kutu = Kutu(0, 25, 500, 500),
            gorselGen = 1000, gorselYuk = 1000,
            gorunumGen = 500, gorunumYuk = 550,
            aynaliMi = false
        )
        assertTrue(oran.sol < 1e-6 && oran.ust < 1e-6)
        assertEquals(1.0, oran.sag, 1e-6)
        assertEquals(1.0, oran.alt, 1e-6)
    }

    @Test
    fun `gorunumden gorsel orani dogru hesap vurur`() {
        val oran = KirpmaHesaplayici.gorunumPxiGorselOranina(
            kutu = Kutu(100, 125, 100, 200),
            gorselGen = 1000, gorselYuk = 1000,
            gorunumGen = 500, gorunumYuk = 550,
            aynaliMi = false
        )
        assertEquals(0.2, oran.sol, 1e-6)
        assertEquals(0.2, oran.ust, 1e-6)
        assertEquals(0.2, oran.genislik, 1e-6)
        assertEquals(0.4, oran.yukseklik, 1e-6)
    }

    @Test
    fun `goruntu oranindan gorunum pxi uyumlu simetrik doner`() {
        val oran = KutuOrani(0.2, 0.2, 0.2, 0.4)
        val kutu = KirpmaHesaplayici.gorselOraniGorunumPxne(
            oran = oran,
            gorselGen = 1000, gorselYuk = 1000,
            gorunumGen = 500, gorunumYuk = 550,
            aynaliMi = false
        )
        assertEquals(100, kutu.sol)
        assertEquals(125, kutu.ust)
        assertEquals(100, kutu.genislik)
        assertEquals(200, kutu.yukseklik)
    }

    @Test
    fun `on kamera aynali x koordinatini yesillik cevirir`() {
        val oran = KirpmaHesaplayici.gorunumPxiGorselOranina(
            kutu = Kutu(100, 125, 100, 200),
            gorselGen = 1000, gorselYuk = 1000,
            gorunumGen = 500, gorunumYuk = 550,
            aynaliMi = true
        )
        // Yansıtma: solda sol ucu görüntünün sağından gelir
        assertEquals(0.6, oran.sol, 1e-6)
        assertEquals(0.8, oran.sag, 1e-6)
        assertEquals(0.2, oran.ust, 1e-6)
        assertEquals(0.2, oran.genislik, 1e-6)

        val geri = KirpmaHesaplayici.gorselOraniGorunumPxne(
            oran = oran,
            gorselGen = 1000, gorselYuk = 1000,
            gorunumGen = 500, gorunumYuk = 550,
            aynaliMi = true
        )
        assertEquals(100, geri.sol)
        assertEquals(125, geri.ust)
        assertEquals(100, geri.genislik)
        assertEquals(200, geri.yukseklik)
    }

    @Test
    fun `oraniKutuya piksel araligina kisin presum`() {
        val kutu = KirpmaHesaplayici.oraniKutuya(KutuOrani(0.2, 0.2, 0.2, 0.4), 1000, 1000)
        assertEquals(200, kutu.sol)
        assertEquals(200, kutu.ust)
        assertEquals(200, kutu.genislik)
        assertEquals(400, kutu.yukseklik)
    }

    @Test
    fun `minimumBoyutOrani merkezi kor secret doger`() {
        val buyutulen = KirpmaHesaplayici.minimumBoyutOrani(KutuOrani(0.45, 0.5, 0.05, 0.06), 0.15)
        assertTrue(abs(buyutulen.genislik - 0.15) < 1e-9)
        assertTrue(abs(buyutulen.yukseklik - 0.15) < 1e-9)
        assertTrue(buyutulen.sol >= 0.0 && buyutulen.ust >= 0.0 && buyutulen.sag <= 1.0 && buyutulen.alt <= 1.0)
    }

    @Test
    fun `statik cerceve eski compose yukseklik kurallarini aynen uretir`() {
        val cerceve = KirpmaHesaplayici.statikCerceve(
            gorunumGen = 1080,
            gorunumYuk = 1200,
            ustAlanPx = 0,
            altAlanPx = 0,
            yanalAlanPx = 48
        )
        assertEquals(675, cerceve.genislik)  // 900*3/4
        assertEquals(900, cerceve.yukseklik) // 1200*0.75
        assertEquals(202, cerceve.sol)       // (1080-675)/2
        assertEquals(150, cerceve.ust)       // (1200-900)/2
    }

    @Test
    fun `statik cerceve bant genisligini asarsa daraltir`() {
        val cerceve = KirpmaHesaplayici.statikCerceve(
            gorunumGen = 300,
            gorunumYuk = 400,
            ustAlanPx = 0,
            altAlanPx = 0,
            yanalAlanPx = 20
        )
        // h=400*0.75=300, w=300*3/4=225 (maxWidth 260'ı aşmıyor)
        assertEquals(225, cerceve.genislik)
        assertEquals(300, cerceve.yukseklik)
        assertEquals(37, cerceve.sol)  // (300-225)/2
        assertEquals(50, cerceve.ust)  // (400-300)/2
    }

    @Test
    fun `statik cerceve cok dar ekranda bant sinirina iner`() {
        val cerceve = KirpmaHesaplayici.statikCerceve(
            gorunumGen = 200,
            gorunumYuk = 1200,
            ustAlanPx = 0,
            altAlanPx = 0,
            yanalAlanPx = 20
        )
        // maxWidth=160; h(=900) istenen w=675 > 160 → w=160, h=160*4/3=~213
        assertEquals(160, cerceve.genislik)
        assertEquals(213, cerceve.yukseklik)
        assertEquals(20, cerceve.sol)
    }
}
