package com.sorudefteri.data.ai

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalAiPromptHelperTest {

    @Test
    fun `buildExternalTwinQuestionsPrompt should contain all 3 question roles and required instructions`() {
        val prompt = ExternalAiPromptHelper.buildExternalTwinQuestionsPrompt(
            ders = "Matematik",
            konu = "Türev ve Değişim Oranı",
            kaynakSoruMetni = "f(x) = x^3 - 3x fonksiyonunun yerel ekstremum noktaları nelerdir?",
            dogruCevap = "B",
            hataTuru = "Türev köklerini ters işaretli alma",
            sinavTuru = "AYT"
        )

        // Temel bağlam kontrolleri
        assertTrue("Ders adı bulunmalı", prompt.contains("Matematik"))
        assertTrue("Konu bulunmalı", prompt.contains("Türev ve Değişim Oranı"))
        assertTrue("Sınav türü bulunmalı", prompt.contains("AYT"))
        assertTrue("Doğru cevap bulunmalı", prompt.contains("B"))
        assertTrue("Hata türü bulunmalı", prompt.contains("Türev köklerini ters işaretli alma"))
        assertTrue("Kaynak metin bulunmalı", prompt.contains("x^3 - 3x"))

        // 3 Kardeş Soru Rolleri
        assertTrue("1. Soru rolü bulunmalı", prompt.contains("Sayı & Değer Varyasyonu"))
        assertTrue("2. Soru rolü bulunmalı", prompt.contains("Hikaye & Bağlam Varyasyonu"))
        assertTrue("3. Soru rolü bulunmalı", prompt.contains("Tersine Akıl Yürütme & Kavram Tuzağı"))

        // Katı LaTeX ve JSON kuralları
        assertTrue("LaTeX kuralı bulunmalı", prompt.contains("\\frac"))
        assertTrue("JSON formatı şeması bulunmalı", prompt.contains("originalTopic"))
        assertTrue("questions dizisi şeması bulunmalı", prompt.contains("questions"))
        assertTrue("trapWarning alanı bulunmalı", prompt.contains("trapWarning"))
    }

    @Test
    fun `buildExternalTwinQuestionsPrompt without text should ask to inspect image`() {
        val prompt = ExternalAiPromptHelper.buildExternalTwinQuestionsPrompt(
            ders = "Fizik",
            konu = "Dinamik",
            kaynakSoruMetni = null,
            dogruCevap = null,
            hataTuru = null
        )

        assertTrue(prompt.contains("Fizik"))
        assertTrue(prompt.contains("Dinamik"))
        assertTrue(prompt.contains("KAYNAK / REFERANS SORU GÖRSELİ") || prompt.contains("görseldeki soruyu"))
    }

    @Test
    fun `mock output matching external twin prompt should be parsed by AiJsonParser`() {
        val mockChatbotReply = """
            Tabii ki, işte sorunun 3 kardeş ikizi:
            ```json
            {
              "originalTopic": "Türev",
              "questions": [
                {
                  "id": 1,
                  "questionText": "f(x) = x^2 - 4x + 3 parabolünün tepe noktasının apsisi kaçtır?",
                  "options": {
                    "A": "1",
                    "B": "2",
                    "C": "3",
                    "D": "4",
                    "E": "5"
                  },
                  "correctAnswer": "B",
                  "solution": "f'(x) = 2x - 4 = 0 => x = 2.",
                  "trapWarning": "Köklerle tepe noktasını karıştırmayın."
                },
                {
                  "id": 2,
                  "questionText": "Bir fabrikanın maliyet fonksiyonu C(x) = x^2 - 6x + 10 olarak modellenmiştir. Maliyeti minimum yapan üretim miktarı kaçtır?",
                  "options": {
                    "A": "2",
                    "B": "3",
                    "C": "4",
                    "D": "5",
                    "E": "6"
                  },
                  "correctAnswer": "B",
                  "solution": "C'(x) = 2x - 6 = 0 => x = 3.",
                  "trapWarning": "Maliyeti değil x üretim miktarını soruyor."
                },
                {
                  "id": 3,
                  "questionText": "f(x) = ax^2 + bx fonksiyonunun x = 1 noktasında yerel minimumu olduğuna göre ve f'(1) = 0 ise a ve b arasındaki bağıntı nedir?",
                  "options": {
                    "A": "b = -2a",
                    "B": "b = 2a",
                    "C": "b = -a",
                    "D": "b = a",
                    "E": "b = 0"
                  },
                  "correctAnswer": "A",
                  "solution": "f'(x) = 2ax + b. f'(1) = 2a + b = 0 => b = -2a.",
                  "trapWarning": "İşaret hatasına dikkat edin."
                }
              ]
            }
            ```
        """.trimIndent()

        val parseResult = AiJsonParser.parseTwinQuestionsResult(mockChatbotReply)
        assertTrue(parseResult.isSuccess)
        val twinResult = parseResult.getOrNull()
        assertNotNull(twinResult)
        org.junit.Assert.assertEquals(3, twinResult?.questions?.size)
        org.junit.Assert.assertEquals("Türev", twinResult?.originalTopic)
        org.junit.Assert.assertEquals("B", twinResult?.questions?.get(0)?.correctAnswer)
        org.junit.Assert.assertEquals("B", twinResult?.questions?.get(1)?.correctAnswer)
        org.junit.Assert.assertEquals("A", twinResult?.questions?.get(2)?.correctAnswer)
        org.junit.Assert.assertEquals("Köklerle tepe noktasını karıştırmayın.", twinResult?.questions?.get(0)?.trapWarning)
    }
}
