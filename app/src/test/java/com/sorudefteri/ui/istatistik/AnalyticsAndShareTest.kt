package com.sorudefteri.ui.istatistik

import android.content.Intent
import android.net.Uri
import com.sorudefteri.domain.model.ActionQuadrant
import com.sorudefteri.domain.model.HataTipi
import com.sorudefteri.domain.model.MasteryLevel
import com.sorudefteri.domain.model.OsymKonuAnalizi
import com.sorudefteri.domain.model.Soru
import com.sorudefteri.domain.model.calculateMasteryPercentage
import com.sorudefteri.domain.model.extractUrgentTopics
import com.sorudefteri.domain.model.partitionTopicsIntoQuadrants
import com.sorudefteri.ui.ortak.TeacherShareHelper
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Faz 4.4, 4.5 ve 4.6 Birim Testleri:
 * 1. Konu Yetkinlik Isı Haritası Yüzde ve Kademe Sınıflandırması
 * 2. ÖSYM Ağırlıklı 2x2 Acil Eylem Matrisi Bölümleme & Sıralama
 * 3. Hocama Sor WhatsApp Paylaşım Metni ve Intent Hazırlığı
 */
class AnalyticsAndShareTest {

    // =========================================================================
    // 1. Konu Yetkinlik Isı Haritası Kategori Testleri (%0-%40, %41-%70, %71-%89, %90-%100)
    // =========================================================================

    @Test
    fun masteryLevel_yuzdeKademeleri_dogruSiniflandirilir() {
        // 🟥 %0 - %40: Kritik Eksik
        assertEquals(MasteryLevel.KRITIK_EKSIK, MasteryLevel.fromPercentage(0))
        assertEquals(MasteryLevel.KRITIK_EKSIK, MasteryLevel.fromPercentage(20))
        assertEquals(MasteryLevel.KRITIK_EKSIK, MasteryLevel.fromPercentage(40))

        // 🟨 %41 - %70: Pekiştirilmeli
        assertEquals(MasteryLevel.PEKISTIRILMELI, MasteryLevel.fromPercentage(41))
        assertEquals(MasteryLevel.PEKISTIRILMELI, MasteryLevel.fromPercentage(55))
        assertEquals(MasteryLevel.PEKISTIRILMELI, MasteryLevel.fromPercentage(70))

        // 🟦 %71 - %89: Güçlü
        assertEquals(MasteryLevel.GUCLU, MasteryLevel.fromPercentage(71))
        assertEquals(MasteryLevel.GUCLU, MasteryLevel.fromPercentage(80))
        assertEquals(MasteryLevel.GUCLU, MasteryLevel.fromPercentage(89))

        // 🟩 %90 - %100: Tam Ustalık
        assertEquals(MasteryLevel.TAM_USTALIK, MasteryLevel.fromPercentage(90))
        assertEquals(MasteryLevel.TAM_USTALIK, MasteryLevel.fromPercentage(95))
        assertEquals(MasteryLevel.TAM_USTALIK, MasteryLevel.fromPercentage(100))
    }

    @Test
    fun masteryLevel_sinirDegerlerVeAralikDisi_guvenleSinirlanir() {
        // Negatif değerler 0'a kıstırılıp Kritik Eksik olmalı
        assertEquals(MasteryLevel.KRITIK_EKSIK, MasteryLevel.fromPercentage(-15))
        // 100'ün üzerindeki değerler 100'e kıstırılıp Tam Ustalık olmalı
        assertEquals(MasteryLevel.TAM_USTALIK, MasteryLevel.fromPercentage(150))
    }

    @Test
    fun calculateMasteryPercentage_dogruHesaplar() {
        // 0 toplam soru -> %0
        assertEquals(0, calculateMasteryPercentage(0, 0))
        // 0 çözülen, 5 soru -> %0
        assertEquals(0, calculateMasteryPercentage(0, 5))
        // 2 çözülen, 5 toplam -> %40 (Kritik Eksik)
        assertEquals(40, calculateMasteryPercentage(2, 5))
        // 7 çözülen, 10 toplam -> %70 (Pekiştirilmeli)
        assertEquals(70, calculateMasteryPercentage(7, 10))
        // 8 çözülen, 10 toplam -> %80 (Güçlü)
        assertEquals(80, calculateMasteryPercentage(8, 10))
        // 19 çözülen, 20 toplam -> %95 (Tam Ustalık)
        assertEquals(95, calculateMasteryPercentage(19, 20))
    }

    // =========================================================================
    // 2. ÖSYM Soru Frekansı ve 2x2 Acil Eylem Matrisi Testleri
    // =========================================================================

    @Test
    fun osymKonuAnalizi_yuksekFrekansKonulari_dogruTanir() {
        // Yüksek Frekans Örnekleri (TYT / AYT)
        assertTrue(OsymKonuAnalizi.isHighFrequency("TYT", "turkce", "Paragrafta Ana ve Yardımcı Düşünce"))
        assertTrue(OsymKonuAnalizi.isHighFrequency("TYT", "matematik", "Sayı, Kesir ve Yaş Problemleri"))
        assertTrue(OsymKonuAnalizi.isHighFrequency("AYT", "matematik", "Türev ve Fiziksel Uygulamaları"))
        assertTrue(OsymKonuAnalizi.isHighFrequency("AYT", "matematik", "Belirli ve Belirsiz İntegral"))
        assertTrue(OsymKonuAnalizi.isHighFrequency("AYT", "matematik", "Trigonometrik Fonksiyonlar"))
        assertTrue(OsymKonuAnalizi.isHighFrequency("TYT", "geometri", "Üçgende Açı ve Kenar Bağıntıları"))
        assertTrue(OsymKonuAnalizi.isHighFrequency("AYT", "fizik", "Manyetizma ve Elektromanyetik İndüksiyon"))
        assertTrue(OsymKonuAnalizi.isHighFrequency("AYT", "kimya", "Elektrokimya ve Piller"))
        assertTrue(OsymKonuAnalizi.isHighFrequency("AYT", "biyoloji", "Fotosentez ve Hücresel Solunum"))

        // Düşük / Orta Frekans Örnekleri
        assertFalse(OsymKonuAnalizi.isHighFrequency("AYT", "felsefe", "Sanat Felsefesi ve Estetik Görüşler"))
        assertFalse(OsymKonuAnalizi.isHighFrequency("TYT", "din", "İnanç ve İbadet Esasları"))
    }

    @Test
    fun actionQuadrant_cozumleme_dogruBolgeyeAtar() {
        // Yüksek Frekans + Çok Hata -> 🚨 Acil Müdahale
        assertEquals(ActionQuadrant.ACIL_MUDAHALE, ActionQuadrant.resolve(isHighFrequency = true, isHighError = true))

        // Yüksek Frekans + Az Hata -> 🛡️ Güçlü Kale
        assertEquals(ActionQuadrant.GUCLU_KALE, ActionQuadrant.resolve(isHighFrequency = true, isHighError = false))

        // Düşük Frekans + Çok Hata -> ⚠️ Pekiştir
        assertEquals(ActionQuadrant.PEKISTIR, ActionQuadrant.resolve(isHighFrequency = false, isHighError = true))

        // Düşük Frekans + Az Hata -> ☕ Güvenli
        assertEquals(ActionQuadrant.GUVENLI, ActionQuadrant.resolve(isHighFrequency = false, isHighError = false))
    }

    @Test
    fun partitionTopicsIntoQuadrants_sorulariDogruBolgelerVeSiralar() {
        val testSorulari = listOf(
            // 1. Türev (Yüksek Frekans, 3 Hata / Çözülmemiş) -> ACIL_MUDAHALE
            createDummySoru(1L, "AYT", "matematik", "Türev ve Uygulamaları", isSolved = false),
            createDummySoru(2L, "AYT", "matematik", "Türev ve Uygulamaları", isSolved = false),
            createDummySoru(3L, "AYT", "matematik", "Türev ve Uygulamaları", isSolved = false),

            // 2. Paragraf (Yüksek Frekans, 4 Çözülmüş, 0 Hata) -> GUCLU_KALE
            createDummySoru(4L, "TYT", "turkce", "Paragrafta Anlam", isSolved = true),
            createDummySoru(5L, "TYT", "turkce", "Paragrafta Anlam", isSolved = true),
            createDummySoru(6L, "TYT", "turkce", "Paragrafta Anlam", isSolved = true),
            createDummySoru(7L, "TYT", "turkce", "Paragrafta Anlam", isSolved = true),

            // 3. Mantık (Düşük Frekans, 3 Hata) -> PEKISTIR
            createDummySoru(8L, "AYT", "felsefe", "Mantık Kuralları", isSolved = false),
            createDummySoru(9L, "AYT", "felsefe", "Mantık Kuralları", isSolved = false),
            createDummySoru(10L, "AYT", "felsefe", "Mantık Kuralları", isSolved = false),

            // 4. Din (Düşük Frekans, 2 Çözülmüş, 0 Hata) -> GUVENLI
            createDummySoru(11L, "TYT", "din", "İnanç Esasları", isSolved = true),
            createDummySoru(12L, "TYT", "din", "İnanç Esasları", isSolved = true)
        )

        val quadrantMap = partitionTopicsIntoQuadrants(testSorulari, errorThreshold = 2)

        // Bölgelerin doğrulanması
        val acilList = quadrantMap[ActionQuadrant.ACIL_MUDAHALE].orEmpty()
        val gucluList = quadrantMap[ActionQuadrant.GUCLU_KALE].orEmpty()
        val pekistirList = quadrantMap[ActionQuadrant.PEKISTIR].orEmpty()
        val guvenliList = quadrantMap[ActionQuadrant.GUVENLI].orEmpty()

        assertEquals(1, acilList.size)
        assertEquals("Türev ve Uygulamaları", acilList[0].topicName)
        assertEquals(3, acilList[0].errorCount)
        assertTrue(acilList[0].isHighFrequency)

        assertEquals(1, gucluList.size)
        assertEquals("Paragrafta Anlam", gucluList[0].topicName)
        assertEquals(0, gucluList[0].errorCount)
        assertEquals(4, gucluList[0].solvedCount)

        assertEquals(1, pekistirList.size)
        assertEquals("Mantık Kuralları", pekistirList[0].topicName)
        assertEquals(3, pekistirList[0].errorCount)
        assertFalse(pekistirList[0].isHighFrequency)

        assertEquals(1, guvenliList.size)
        assertEquals("İnanç Esasları", guvenliList[0].topicName)
        assertEquals(0, guvenliList[0].errorCount)
    }

    @Test
    fun extractUrgentTopics_enAcil3KonuyuSecer() {
        val testSorulari = listOf(
            // Acil 1: Türev (4 hata)
            createDummySoru(1L, "AYT", "matematik", "Türev", isSolved = false),
            createDummySoru(2L, "AYT", "matematik", "Türev", isSolved = false),
            createDummySoru(3L, "AYT", "matematik", "Türev", isSolved = false),
            createDummySoru(4L, "AYT", "matematik", "Türev", isSolved = false),

            // Acil 2: İntegral (3 hata)
            createDummySoru(5L, "AYT", "matematik", "İntegral", isSolved = false),
            createDummySoru(6L, "AYT", "matematik", "İntegral", isSolved = false),
            createDummySoru(7L, "AYT", "matematik", "İntegral", isSolved = false),

            // Acil 3: Trigonometri (2 hata)
            createDummySoru(8L, "AYT", "matematik", "Trigonometri", isSolved = false),
            createDummySoru(9L, "AYT", "matematik", "Trigonometri", isSolved = false),

            // Acil 4: Sayı Problemleri (2 hata)
            createDummySoru(10L, "TYT", "matematik", "Sayı Problemleri", isSolved = false),
            createDummySoru(11L, "TYT", "matematik", "Sayı Problemleri", isSolved = false)
        )

        val quadrantMap = partitionTopicsIntoQuadrants(testSorulari, errorThreshold = 2)
        val urgent3 = extractUrgentTopics(quadrantMap, limit = 3)

        assertEquals(3, urgent3.size)
        // En yüksek hata yapılan Türev ilk sırada olmalı
        assertEquals("Türev", urgent3[0].topicName)
        assertEquals(4, urgent3[0].errorCount)
        assertEquals("İntegral", urgent3[1].topicName)
        assertEquals(3, urgent3[1].errorCount)
    }

    // =========================================================================
    // 3. "Hocam Bakar Mısınız?" WhatsApp Paylaşım Testleri
    // =========================================================================

    @Test
    fun teacherShareHelper_buildShareMessage_tumAlanlariIcerir() {
        val message = TeacherShareHelper.buildShareMessage(
            greeting = TeacherShareHelper.DEFAULT_WHATSAPP_GREETING,
            dersAdi = "Fizik",
            topic = "Manyetizma ve İndüksiyon",
            examType = "AYT",
            studentAnswer = "C",
            correctAnswer = "E",
            note = "Hocam sağ el kuralında indüksiyon akımının yönünü ters buluyorum, bakar mısınız?"
        )

        assertTrue(message.contains(TeacherShareHelper.DEFAULT_WHATSAPP_GREETING))
        assertTrue(message.contains("Fizik"))
        assertTrue(message.contains("AYT"))
        assertTrue(message.contains("Manyetizma ve İndüksiyon"))
        assertTrue(message.contains("🔴 *Benim Cevabım:* C"))
        assertTrue(message.contains("🟢 *Doğru Cevap:* E"))
        assertTrue(message.contains("sağ el kuralında indüksiyon akımının yönünü ters buluyorum"))
        assertTrue(message.contains("Soru Defterim • YKS Hazırlık"))
    }

    @Test
    fun teacherShareHelper_createTeacherShareIntent_yapilandirmasiDogru() {
        val mockUri = mockk<Uri>(relaxed = true)
        val greeting = "Test selamı"

        val payload = TeacherShareHelper.buildSharePayload(
            imageUri = mockUri,
            greetingText = greeting,
            targetPackage = "com.whatsapp"
        )

        assertEquals("android.intent.action.SEND", payload.action)
        assertEquals("image/jpeg", payload.type)
        assertEquals(greeting, payload.text)
        assertEquals("com.whatsapp", payload.targetPackage)
        assertEquals(mockUri, payload.streamUri)
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, payload.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    // =========================================================================
    // Yardımcı Mock Fabrikası
    // =========================================================================

    private fun createDummySoru(
        id: Long,
        examType: String,
        subjectId: String,
        topic: String,
        isSolved: Boolean
    ): Soru {
        return Soru(
            id = id,
            imagePath = "/mock/path/$id.jpg",
            examType = examType,
            subjectId = subjectId,
            topic = topic,
            isSolved = isSolved,
            mistakeType = if (!isSolved) HataTipi.BILGI_EKSIKLIGI else null,
            correctAnswer = "A",
            studentAnswer = if (!isSolved) "C" else "A"
        )
    }
}
