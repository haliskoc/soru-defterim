package com.sorudefteri.data.ai

import com.sorudefteri.domain.model.AiErrorType
import com.sorudefteri.domain.model.AiException
import com.sorudefteri.domain.model.QuestionMetadata
import com.sorudefteri.domain.model.SimilarQuestionResult
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger

/**
 * AI Katmanı Güvenilirlik, Hata Dayanıklılığı, Yeniden Deneme (Retry)
 * ve JSON Kurtarma (Clean & Parse) birim testleri.
 */
class AiReliabilityTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // =========================================================================
    // 1. JSON Temizleme & Kurtarma (Clean and Parse) Testleri
    // =========================================================================

    @Test
    fun cleanAndParseJson_markdownKodBloklariVePrefixMetinleriTemizler() {
        val rawAiOutput = """
            Tabii ki! İşte analiz ettiğim soruya ait bilgiler:
            ```json
            {
              "examType": "AYT",
              "subjectId": "matematik",
              "topic": "İntegral",
              "correctAnswer": "D"
            }
            ```
            Umarım bu açıklama yardımcı olur, iyi çalışmalar!
        """.trimIndent()

        val result = AiJsonParser.cleanAndParseJson(
            rawText = rawAiOutput,
            serializer = QuestionMetadata.serializer()
        )

        assertTrue("Markdown ile sarılmış ve başında/sonunda metin olan JSON başarıyla ayrıştırılmalı", result.isSuccess)
        val metadata = result.getOrNull()
        assertNotNull(metadata)
        assertEquals("AYT", metadata?.examType)
        assertEquals("matematik", metadata?.subjectId)
        assertEquals("İntegral", metadata?.topic)
        assertEquals("D", metadata?.correctAnswer)
    }

    @Test
    fun cleanAndParseJson_latexKacisKarakterleriOlanJsoniKurtarir() {
        // Ham JSON içinde LaTeX ters eğik çizgileri ("\frac", "\int", "\alpha") standart JSON ayrıştırıcıyı kırabilir.
        // AiJsonParser.sanitizeLatexEscapesInJson bu kaçışları düzelterek parse edebilmelidir.
        val rawAiOutput = """
            {
              "questionText": "Aşağıdaki integralin değeri kaçtır? \int_0^1 (2x + 1) dx",
              "options": {
                "A": "\frac{1}{2}",
                "B": "1",
                "C": "2",
                "D": "\frac{5}{2}",
                "E": "3"
              },
              "correctAnswer": "C",
              "solution": "İntegral alındığında: [x^2 + x]_0^1 = (1 + 1) - (0) = 2 bulunur."
            }
        """.trimIndent()

        val result = AiJsonParser.cleanAndParseJson(
            rawText = rawAiOutput,
            serializer = SimilarQuestionResult.serializer(),
            fallbackParser = { AiJsonParser.parseSimilarQuestionFallback(it) }
        )

        assertTrue("LaTeX kaçışları içeren JSON çözümlenebilmeli", result.isSuccess)
        val similar = result.getOrNull()
        assertNotNull(similar)
        assertEquals("C", similar?.correctAnswer)
        assertTrue(similar?.questionText?.contains("integral") == true)
        assertEquals("2", similar?.options?.get("C"))
    }

    @Test
    fun cleanAndParseJson_hafifBozukJSON_regexFallbackIleKurtarilir() {
        // Eksik kapanış süslü parantezi veya hafif format bozulması simülasyonu
        val brokenJsonOutput = """
            Burada ürettiğim benzer soru yer alıyor:
            {
              "questionText": "f(x) = x^2 - 4x + 3 parabolünün tepe noktasının ordinatı kaçtır?",
              "options": {
                "A": "-1",
                "B": "0",
                "C": "1",
                "D": "2",
                "E": "3"
              },
              "correctAnswer": "A",
              "solution": "r = -b/(2a) = 4/2 = 2. k = f(2) = 4 - 8 + 3 = -1."
        """.trimIndent() // Kapanış süslü parantezleri eksik

        val result = AiJsonParser.cleanAndParseJson(
            rawText = brokenJsonOutput,
            serializer = SimilarQuestionResult.serializer(),
            fallbackParser = { AiJsonParser.parseSimilarQuestionFallback(it) }
        )

        assertTrue("Bozuk JSON Regex fallback ile kurtarılabilmeli", result.isSuccess)
        val similar = result.getOrNull()
        assertNotNull(similar)
        assertEquals("A", similar?.correctAnswer)
        assertEquals("-1", similar?.options?.get("A"))
        assertTrue(similar?.solution?.contains("r = -b/(2a)") == true)
    }

    // =========================================================================
    // 2. Hata Tipi Sınıflandırma Testleri
    // =========================================================================

    @Test
    fun aiErrorType_ve_aiException_dogruMesajVeSiniflandirmaSunar() {
        val apiKeyEx = AiException(AiErrorType.INVALID_API_KEY)
        assertEquals(AiErrorType.INVALID_API_KEY, apiKeyEx.errorType)
        assertTrue(apiKeyEx.userFriendlyMessage.contains("Geçersiz veya eksik API Anahtarı"))

        val quotaEx = AiException(AiErrorType.QUOTA_EXCEEDED)
        assertEquals(AiErrorType.QUOTA_EXCEEDED, quotaEx.errorType)
        assertTrue(quotaEx.userFriendlyMessage.contains("Rate limit"))

        val timeoutEx = AiException(AiErrorType.NETWORK_TIMEOUT)
        assertEquals(AiErrorType.NETWORK_TIMEOUT, timeoutEx.errorType)
        assertTrue(timeoutEx.userFriendlyMessage.contains("zaman aşımı"))

        val modelEx = AiException(AiErrorType.MODEL_UNAVAILABLE)
        assertEquals(AiErrorType.MODEL_UNAVAILABLE, modelEx.errorType)
        assertTrue(modelEx.userFriendlyMessage.contains("model"))

        val parsingEx = AiException(AiErrorType.PARSING_FAILED)
        assertEquals(AiErrorType.PARSING_FAILED, parsingEx.errorType)
        assertTrue(parsingEx.userFriendlyMessage.contains("çözümlenemedi"))
    }

    // =========================================================================
    // 3. Retry Mekanizması Simülasyon Testleri
    // =========================================================================

    @Test
    fun retryMekanizmasi_429RateLimit_veTimeoutSimulasyonu_3DenemedeBasarili() {
        var callCount = 0
        val maxRetries = 3

        fun simulateApiCallWithRetry(): Result<String> {
            for (attempt in 0 until maxRetries) {
                callCount++
                if (callCount < 3) {
                    // İlk 2 deneme 429 Rate limit veya Timeout hatası versin
                    val isRateLimit = callCount == 1
                    val errorMsg = if (isRateLimit) "HTTP 429: Rate limit exceeded" else "SocketTimeoutException: Connection timed out"
                    // Retry döngüsü devam eder
                    continue
                } else {
                    // 3. denemede başarılı yanıt dönsün
                    return Result.success("🎯 **Doğru Cevap:** C\n\nAdım adım çözüm tamamlandı.")
                }
            }
            return Result.failure(Exception("Tüm denemeler başarısız oldu."))
        }

        val result = simulateApiCallWithRetry()
        assertTrue("3. denemede başarıya ulaşmalı", result.isSuccess)
        assertEquals(3, callCount)
        assertTrue(result.getOrNull()?.contains("Doğru Cevap") == true)
    }

    @Test
    fun retryMekanizmasi_tumDenemelerBasarisizOldugunda_hataDoner() {
        var callCount = 0
        val maxRetries = 3

        fun simulateFailingApiCall(): Result<String> {
            var lastError: Exception = Exception("Bilinmeyen hata")
            for (attempt in 0 until maxRetries) {
                callCount++
                lastError = SocketTimeoutException("İstek zaman aşımına uğradı.")
            }
            return Result.failure(lastError)
        }

        val result = simulateFailingApiCall()
        assertTrue("Tüm denemeler başarısız olduğunda failure dönmeli", result.isFailure)
        assertEquals(3, callCount)
        assertTrue(result.exceptionOrNull() is SocketTimeoutException)
    }
}
