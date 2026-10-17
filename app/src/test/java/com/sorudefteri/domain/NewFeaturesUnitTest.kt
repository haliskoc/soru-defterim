package com.sorudefteri.domain

import androidx.compose.ui.geometry.Rect
import com.sorudefteri.data.local.entity.DenemeSinaviEntity
import com.sorudefteri.data.local.entity.SoruChatMessageEntity
import com.sorudefteri.data.local.entity.toEntity
import com.sorudefteri.domain.model.DenemeDersNeti
import com.sorudefteri.domain.model.DenemeSinavi
import com.sorudefteri.ui.soruekle.components.BatchSplitMode
import com.sorudefteri.ui.soruekle.components.calculateCropRects
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Faz 1, 2 ve 3 kapsamında eklenen yeni özellikler için birim testler:
 * - Deneme Sınavı Takip Veri Modeli ve JSON Dönüşümleri
 * - Sokratik AI Chat Mesaj Modeli
 * - Batch Crop (Toplu Kırpma / A4 Bölücü) Koordinat Hesaplamaları
 */
class NewFeaturesUnitTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun denemeSinavi_toEntityAndToDomain_tamVeriTutarliligi() {
        val dersler = listOf(
            DenemeDersNeti(dersAdi = "Türkçe", dogruSayisi = 35, yanlisSayisi = 5, bosSayisi = 0, net = 33.75),
            DenemeDersNeti(dersAdi = "Temel Matematik", dogruSayisi = 30, yanlisSayisi = 2, bosSayisi = 8, net = 29.5),
            DenemeDersNeti(dersAdi = "Fen Bilimleri", dogruSayisi = 18, yanlisSayisi = 2, bosSayisi = 0, net = 17.5),
            DenemeDersNeti(dersAdi = "Sosyal Bilimler", dogruSayisi = 16, yanlisSayisi = 4, bosSayisi = 0, net = 15.0)
        )
        val toplamNet = dersler.sumOf { it.net }
        val deneme = DenemeSinavi(
            id = 42L,
            baslik = "Özdebir Türkiye Geneli TYT-1",
            sinavTuru = "TYT",
            tarih = 1720000000000L,
            toplamNet = toplamNet,
            dersNetleri = dersler,
            notlar = "Matematikte geometri soruları zaman aldı."
        )

        // Domain -> Entity
        val entity = deneme.toEntity(json)
        assertEquals(42L, entity.id)
        assertEquals("Özdebir Türkiye Geneli TYT-1", entity.baslik)
        assertEquals("TYT", entity.sinavTuru)
        assertEquals(95.75, entity.toplamNet, 0.001)
        assertTrue(entity.dersNetleriJson.contains("Temel Matematik"))

        // Entity -> Domain
        val restored = entity.toDomain(json)
        assertEquals(deneme.id, restored.id)
        assertEquals(deneme.baslik, restored.baslik)
        assertEquals(deneme.toplamNet, restored.toplamNet, 0.001)
        assertEquals(4, restored.dersNetleri.size)
        assertEquals("Türkçe", restored.dersNetleri[0].dersAdi)
        assertEquals(33.75, restored.dersNetleri[0].net, 0.001)
        assertEquals(deneme.notlar, restored.notlar)
    }

    @Test
    fun denemeSinavi_bozukJsonDurumundaCokmedenBosListeDoner() {
        val corruptedEntity = DenemeSinaviEntity(
            id = 1L,
            baslik = "Hasarlı Kayıt",
            sinavTuru = "AYT",
            tarih = 1000L,
            toplamNet = 50.0,
            dersNetleriJson = "{ invalid json content }"
        )

        val domain = corruptedEntity.toDomain(json)
        assertEquals(1L, domain.id)
        assertTrue("Bozuk JSON güvenle boş liste üretmeli", domain.dersNetleri.isEmpty())
    }

    @Test
    fun soruChatMessageEntity_olusturmaVeSenderKontrolu() {
        val userMsg = SoruChatMessageEntity(
            id = 1L,
            soruId = 101L,
            sender = "USER",
            messageText = "Bu soruda Pisagor teoremini nasıl kullanabilirim?"
        )
        val aiMsg = SoruChatMessageEntity(
            id = 2L,
            soruId = 101L,
            sender = "AI",
            messageText = "Öncelikle dik üçgenin hipotenüsünü belirleyelim. Hangi kenar dik açının karşısında?"
        )

        assertEquals(101L, userMsg.soruId)
        assertEquals("USER", userMsg.sender)
        assertEquals("AI", aiMsg.sender)
        assertTrue(userMsg.timestamp > 0L)
    }

    @Test
    fun calculateCropRects_twoVertical_dikeyIkiyeBolur() {
        val rects = calculateCropRects(1000, 1600, BatchSplitMode.TWO_VERTICAL, 0.5f)
        assertEquals(2, rects.size)

        // Sol sütun
        assertEquals(Rect(0f, 0f, 500f, 1600f), rects[0])
        // Sağ sütun
        assertEquals(Rect(500f, 0f, 1000f, 1600f), rects[1])
    }

    @Test
    fun calculateCropRects_twoHorizontal_yatayIkiyeBolur() {
        val rects = calculateCropRects(1000, 1600, BatchSplitMode.TWO_HORIZONTAL, 0.5f)
        assertEquals(2, rects.size)

        // Üst yarı
        assertEquals(Rect(0f, 0f, 1000f, 800f), rects[0])
        // Alt yarı
        assertEquals(Rect(0f, 800f, 1000f, 1600f), rects[1])
    }

    @Test
    fun calculateCropRects_threeHorizontal_yatayUceBolur() {
        val rects = calculateCropRects(900, 1500, BatchSplitMode.THREE_HORIZONTAL, 0.5f)
        assertEquals(3, rects.size)

        assertEquals(0f, rects[0].top, 0.1f)
        assertEquals(1500f * 0.333f, rects[0].bottom, 0.1f)

        assertEquals(1500f * 0.333f, rects[1].top, 0.1f)
        assertEquals(1500f * 0.666f, rects[1].bottom, 0.1f)

        assertEquals(1500f * 0.666f, rects[2].top, 0.1f)
        assertEquals(1500f, rects[2].bottom, 0.1f)
    }

    @Test
    fun calculateCropRects_fourGrid_dortEsitIzgarayaBolur() {
        val rects = calculateCropRects(1000, 1000, BatchSplitMode.FOUR_GRID, 0.5f)
        assertEquals(4, rects.size)

        // Sol Üst
        assertEquals(Rect(0f, 0f, 500f, 500f), rects[0])
        // Sağ Üst
        assertEquals(Rect(500f, 0f, 1000f, 500f), rects[1])
        // Sol Alt
        assertEquals(Rect(0f, 500f, 500f, 1000f), rects[2])
        // Sağ Alt
        assertEquals(Rect(500f, 500f, 1000f, 1000f), rects[3])
    }

    @Test
    fun calculateCropRects_customHorizontal_belirtilenOranaGoreBolur() {
        val rects = calculateCropRects(1000, 2000, BatchSplitMode.CUSTOM_HORIZONTAL, 0.7f)
        assertEquals(2, rects.size)

        // Üst %70
        assertEquals(Rect(0f, 0f, 1000f, 1400f), rects[0])
        // Alt %30
        assertEquals(Rect(0f, 1400f, 1000f, 2000f), rects[1])
    }
}
