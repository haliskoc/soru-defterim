package com.sorudefteri.data.ai

import com.sorudefteri.data.local.AyarlarDataStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * AiPromptManager için 4 senaryo şablonu, parametre yerleştirme,
 * özel istem (custom prompt) kaydetme ve varsayılana sıfırlama birim testleri.
 */
class AiPromptManagerTest {

    private lateinit var mockAyarlarDataStore: AyarlarDataStore
    private lateinit var promptManager: AiPromptManager

    @Before
    fun setUp() {
        mockAyarlarDataStore = mockk(relaxed = true)
        promptManager = AiPromptManager(mockAyarlarDataStore)
    }

    @Test
    fun varsayilanPromptlar_tum4SenaryoIcinDoluVeGecerli() {
        for (type in AiPromptType.entries) {
            val defaultPrompt = promptManager.getDefaultPrompt(type)
            assertNotNull(defaultPrompt)
            assertTrue("Varsayılan prompt boş olamaz: $type", defaultPrompt.isNotBlank())
            assertTrue("Başlık bilgisi boş olamaz: ${type.title}", type.title.isNotBlank())
            assertTrue("Açıklama bilgisi boş olamaz: ${type.description}", type.description.isNotBlank())
        }
    }

    @Test
    fun buildSoruUretimiPrompt_tumParametreleriEksiksizYerlestirir() = runTest {
        coEvery { mockAyarlarDataStore.getCustomPromptValue(AiPromptType.SORU_URETIMI) } returns ""

        val prompt = promptManager.buildSoruUretimiPrompt(
            dersAdi = "Fizik",
            konu = "Kuvvet ve Hareket",
            zorluk = "Zor",
            alan = "Sayısal",
            sinavTipi = "AYT"
        )

        // Ders/konu artık wrapTrustedBlock ile sarılı, direkt "Ders: Fizik" yerine içerik + tag kontrolü
        assertTrue(prompt.contains("Fizik") && prompt.contains("<ders>"))
        assertTrue(prompt.contains("Kuvvet ve Hareket") && prompt.contains("<konu>"))
        assertTrue(prompt.contains("Zor") && prompt.contains("<zorluk>"))
        assertTrue(prompt.contains("Sayısal") && prompt.contains("<alan>"))
        assertTrue(prompt.contains("AYT") && prompt.contains("<sinav_turu>"))
    }

    @Test
    fun buildCozumUretimiPrompt_tumCozumVeHataBilgileriniEkler() = runTest {
        coEvery { mockAyarlarDataStore.getCustomPromptValue(AiPromptType.COZUM_URETIMI) } returns ""

        val prompt = promptManager.buildCozumUretimiPrompt(
            dersAdi = "Matematik",
            konu = "Türev",
            soruMetni = "f(x) = x^3 - 3x fonksiyonunun yerel ekstremum noktaları...",
            correctAnswer = "B",
            mistakeType = "İşlem Hatası"
        )

        assertTrue(prompt.contains("Matematik") && prompt.contains("<ders>"))
        assertTrue(prompt.contains("Türev") && prompt.contains("<konu>"))
        assertTrue(prompt.contains("Doğru cevap şıkkı: B"))
        assertTrue(prompt.contains("İşlem Hatası"))
        assertTrue(prompt.contains("f(x) = x^3 - 3x"))
    }

    @Test
    fun buildOcrTemizlemePrompt_hamMetniEksiksizEkler() = runTest {
        coEvery { mockAyarlarDataStore.getCustomPromptValue(AiPromptType.OCR_TEMIZLEME) } returns ""

        val rawText = "f(x)=2x2+5x-3 fonksıyonunun koklerı toplamı kactır?"
        val prompt = promptManager.buildOcrTemizlemePrompt(rawOcrText = rawText)

        assertTrue(prompt.contains("ham_ocr_metni"))
        assertTrue(prompt.contains(rawText))
        // Injection guard: içerik veri olarak sarılmalı
        assertTrue(prompt.contains("&lt;ham_ocr_metni&gt;") || prompt.contains("<ham_ocr_metni>"))
    }

    @Test
    fun buildBenzerSoruPrompt_kaynakSoruyuVeKazanimlariEkler() = runTest {
        coEvery { mockAyarlarDataStore.getCustomPromptValue(AiPromptType.BENZER_SORU) } returns ""

        val prompt = promptManager.buildBenzerSoruPrompt(
            dersAdi = "Kimya",
            konu = "Gaz Yasaları",
            kaynakSoruMetni = "P1.V1 = P2.V2 ideal gaz denklemi sorusu"
        )

        assertTrue(prompt.contains("Kimya") && prompt.contains("<ders>"))
        assertTrue(prompt.contains("Gaz Yasaları") && prompt.contains("<konu>"))
        assertTrue(prompt.contains("kaynak_soru_metni"))
        assertTrue(prompt.contains("P1.V1 = P2.V2"))
    }

    @Test
    fun ozelPrompt_varsayilaniEzerekEtkinlesir_veSifirlanabilir() = runTest {
        val customPrompt = "Sen özel bir soru hazırlayıcısısın. Sadece çok zor sorular yaz."

        // 1. Özel prompt ayarlanmış durumu
        coEvery { mockAyarlarDataStore.getCustomPromptValue(AiPromptType.SORU_URETIMI) } returns customPrompt
        val effective = promptManager.getEffectivePrompt(AiPromptType.SORU_URETIMI)
        assertEquals(customPrompt, effective)

        // 2. buildPrompt içinde özel prompt kullanımı (wrap sonrası da içermeli)
        val built = promptManager.buildSoruUretimiPrompt("Biyoloji", "Fotosentez")
        assertTrue(built.contains(customPrompt))
        assertTrue(built.contains("Biyoloji") && built.contains("Fotosentez"))

        // 3. Özel prompt sıfırlandığında varsayılana dönme
        coEvery { mockAyarlarDataStore.getCustomPromptValue(AiPromptType.SORU_URETIMI) } returns ""
        val reverted = promptManager.getEffectivePrompt(AiPromptType.SORU_URETIMI)
        assertEquals(AiPromptManager.PROMPT_SORU_URETIMI_DEFAULT, reverted)
    }

    @Test
    fun customTemplateParametresi_geciciOlarakBasePromptuEzebilir() = runTest {
        val oneOffTemplate = "Geçici tek seferlik şablon."
        coEvery { mockAyarlarDataStore.getCustomPromptValue(AiPromptType.SORU_URETIMI) } returns ""
        val prompt = promptManager.buildSoruUretimiPrompt(
            dersAdi = "Tarih",
            konu = "Kurtuluş Savaşı",
            customTemplate = oneOffTemplate
        )
        // customTemplate basePrompt olarak en başta yer almalı (wrap sonrası da olsa içermeli)
        assertTrue(prompt.contains(oneOffTemplate))
        assertTrue(prompt.contains("Tarih") && prompt.contains("Kurtuluş Savaşı"))
    }
}
