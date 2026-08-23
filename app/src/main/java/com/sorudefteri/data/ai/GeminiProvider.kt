package com.sorudefteri.data.ai

import android.util.Base64
import com.sorudefteri.data.ai.dto.GeminiContent
import com.sorudefteri.data.ai.dto.GeminiInlineData
import com.sorudefteri.data.ai.dto.GeminiModelItem
import com.sorudefteri.data.ai.dto.GeminiModelListResponse
import com.sorudefteri.data.ai.dto.GeminiPart
import com.sorudefteri.data.ai.dto.GeminiRequest
import com.sorudefteri.data.ai.dto.GeminiResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google Gemini API üzerinden görsel tabanlı soru çözümü sağlayan servis.
 */
@Singleton
class GeminiProvider @Inject constructor(
    private val okHttpClient: OkHttpClient
) : CozumProvider {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        coerceInputValues = true
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    companion object {
        const val FALLBACK_MODEL = "gemini-2.5-flash"
        const val SECONDARY_FALLBACK_MODEL = "gemini-2.0-flash"
    }

    /**
     * Google Gemini API'sinden kullanıcının API anahtarına açık tüm güncel modelleri canlı çeker.
     */
    suspend fun listModels(apiKey: String): Result<List<Pair<String, String>>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("Gemini API anahtarı boş olamaz.")
            )
        }

        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"
            val httpRequest = Request.Builder()
                .url(url)
                .get()
                .build()

            okHttpClient.newCall(httpRequest).execute().use { response ->
                val responseBodyString = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val errorDetail = when (response.code) {
                        401, 403 -> "Geçersiz veya yetkisiz API Anahtarı."
                        429 -> "İstek sınırı aşıldı (Kota doldu)."
                        else -> "HTTP ${response.code}"
                    }
                    return@withContext Result.failure(
                        Exception("Gemini modelleri çekilemedi ($errorDetail). Lütfen API anahtarınızı kontrol edin.")
                    )
                }

                val modelListResponse = json.decodeFromString<GeminiModelListResponse>(responseBodyString)
                val supportedModels = modelListResponse.models
                    .filter { item ->
                        item.supportedGenerationMethods.contains("generateContent") &&
                                !item.name.contains("embedding", ignoreCase = true) &&
                                !item.name.contains("aqa", ignoreCase = true)
                    }
                    .map { item ->
                        val cleanId = item.name.removePrefix("models/")
                        val displayName = item.displayName.ifBlank { cleanId }
                        cleanId to "$displayName ($cleanId)"
                    }
                    .sortedByDescending { it.first }

                if (supportedModels.isEmpty()) {
                    Result.success(
                        listOf(
                            FALLBACK_MODEL to "Gemini 2.5 Flash ($FALLBACK_MODEL)",
                            SECONDARY_FALLBACK_MODEL to "Gemini 2.0 Flash ($SECONDARY_FALLBACK_MODEL)"
                        )
                    )
                } else {
                    Result.success(supportedModels)
                }
            }
        } catch (e: Exception) {
            Result.failure(
                Exception("Gemini modelleri alınamadı: ${e.localizedMessage ?: "Bağlantı hatası"}", e)
            )
        }
    }

    override suspend fun solveQuestion(
        imageBytes: ByteArray,
        dersAdi: String,
        konu: String,
        correctAnswer: String?,
        modelName: String,
        apiKey: String
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("Google Gemini API Anahtarı bulunamadı. Lütfen Ayarlar sayfasından API anahtarınızı girin.")
            )
        }

        val effectiveModel = modelName.removePrefix("models/").ifBlank { FALLBACK_MODEL }

        // İlk deneme: Seçili model ile retry ve exponential backoff destekli çağrı
        val primaryResult = executeGenerateContentWithRetry(imageBytes, dersAdi, konu, correctAnswer, effectiveModel, apiKey)
        if (primaryResult.isSuccess) {
            return@withContext primaryResult
        }

        val throwable = primaryResult.exceptionOrNull()
        val errorMsg = throwable?.message.orEmpty().lowercase()

        // Eğer model bulunamadı (404 / deprecated) ve seçilen model zaten varsayılan değilse, fallback yap
        val isModelNotFoundOrDeprecated = errorMsg.contains("not found") ||
                errorMsg.contains("deprecated") ||
                errorMsg.contains("is not supported") ||
                errorMsg.contains("404")

        if (isModelNotFoundOrDeprecated && effectiveModel != FALLBACK_MODEL) {
            val fallbackResult = executeGenerateContentWithRetry(imageBytes, dersAdi, konu, correctAnswer, FALLBACK_MODEL, apiKey)
            if (fallbackResult.isSuccess) {
                return@withContext fallbackResult
            }
            // İkincil fallback dene (örn. 2.0-flash)
            if (FALLBACK_MODEL != SECONDARY_FALLBACK_MODEL) {
                val secFallback = executeGenerateContentWithRetry(imageBytes, dersAdi, konu, correctAnswer, SECONDARY_FALLBACK_MODEL, apiKey)
                if (secFallback.isSuccess) {
                    return@withContext secFallback
                }
            }
        }

        return@withContext primaryResult
    }

    private suspend fun executeGenerateContentWithRetry(
        imageBytes: ByteArray,
        dersAdi: String,
        konu: String,
        correctAnswer: String?,
        modelName: String,
        apiKey: String,
        maxRetries: Int = 3
    ): Result<String> {
        var lastResult: Result<String> = Result.failure(Exception("Bilinmeyen hata"))
        for (attempt in 0 until maxRetries) {
            lastResult = executeGenerateContent(imageBytes, dersAdi, konu, correctAnswer, modelName, apiKey)
            if (lastResult.isSuccess) {
                return lastResult
            }

            val exception = lastResult.exceptionOrNull()
            val msg = exception?.message.orEmpty().lowercase()
            val isRetryable = msg.contains("429") ||
                    msg.contains("rate limit") ||
                    msg.contains("500") ||
                    msg.contains("502") ||
                    msg.contains("503") ||
                    msg.contains("504") ||
                    msg.contains("zaman aşımı") ||
                    msg.contains("timeout") ||
                    exception is SocketTimeoutException

            if (isRetryable && attempt < maxRetries - 1) {
                val backoffMs = 1000L * (1L shl attempt) // 1s, 2s, 4s
                delay(backoffMs)
            } else {
                break
            }
        }
        return lastResult
    }

    private fun executeGenerateContent(
        imageBytes: ByteArray,
        dersAdi: String,
        konu: String,
        correctAnswer: String?,
        modelName: String,
        apiKey: String
    ): Result<String> {
        val answerInfo = if (!correctAnswer.isNullOrBlank()) {
            "Öğrencinin belirttiği / Sorunun doğru cevap şıkkı: $correctAnswer"
        } else {
            "Doğru cevap şıkkı belirtilmemiş. Lütfen soruyu çözerek doğru şıkkı ve sonucu net olarak belirle."
        }

        val prompt = """
            Sen Türkiye YKS (TYT / AYT) sınavlarına hazırlanan öğrenciler için alanında uzman, pedagojik formasyona sahip kıdemli bir öğretmensin.
            Görseldeki soruyu öğrencinin eksiksiz anlayacağı şekilde, adım adım ve pedagojik yöntemlerle açıkla.
            Matematiksel formülleri ve ifadeleri standart LaTeX biçiminde (örn: ${'$'}x^2 + 5x + 6 = 0${'$'}, ${'$'}\\int_a^b f(x)dx${'$'}, ${'$'}\\frac{a}{b}${'$'}, ${'$'}\\sum_{i=1}^n${'$'}) sun.

            Ders: $dersAdi
            Konu: $konu
            $answerInfo

            Lütfen çözümü aşağıdaki yapılandırılmış format ve başlıklarla sun:

            🎯 **Doğru Cevap:** [Net doğru cevap şıkkı ve sonuç]

            📚 **Temel Kavramlar & Formüller:**
            - Soruyu çözmek için gereken temel kurallar, formüller veya kavramlar.

            📝 **Adım Adım Ayrıntılı Çözüm:**
            1. Soruda verilenler ve istenenlerin analizi.
            2. Adım adım matematiksel/mantıksal işlem süreci (tüm adımları sade ve net göster).
            3. Sonuca ulaşma ve doğru şıkla eşleştirme.

            💡 **Püf Noktaları & Sınav Taktikleri (Önemli İpuçları):**
            - YKS'de bu soru tipinde yapılan yaygın çeldirici hatalar.
            - Hızlı çözüm için pratik ipucu veya pratik kontrol yöntemi.
        """.trimIndent()

        return try {
            val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)

            val requestBody = GeminiRequest(
                contents = listOf(
                    GeminiContent(
                        parts = listOf(
                            GeminiPart(text = prompt),
                            GeminiPart(
                                inline_data = GeminiInlineData(
                                    mime_type = "image/jpeg",
                                    data = base64Image
                                )
                            )
                        )
                    )
                )
            )

            val requestJson = json.encodeToString(GeminiRequest.serializer(), requestBody)
            val cleanModel = modelName.removePrefix("models/").trim()
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$cleanModel:generateContent?key=$apiKey"

            val httpRequest = Request.Builder()
                .url(url)
                .post(requestJson.toRequestBody(jsonMediaType))
                .build()

            okHttpClient.newCall(httpRequest).execute().use { response ->
                val responseBodyString = response.body?.string().orEmpty()

                when (response.code) {
                    200 -> {
                        val geminiResponse = try {
                            json.decodeFromString<GeminiResponse>(responseBodyString)
                        } catch (e: Exception) {
                            return Result.failure(
                                Exception("Gemini API yanıtı çözümlenemedi: ${e.localizedMessage}", e)
                            )
                        }

                        if (geminiResponse.error != null) {
                            return Result.failure(
                                Exception(geminiResponse.error.message ?: "Google Gemini API hatası oluştu.")
                            )
                        }

                        val solutionText = geminiResponse.candidates
                            ?.firstOrNull()
                            ?.content
                            ?.parts
                            ?.mapNotNull { it.text }
                            ?.joinToString("\n")
                            ?.trim()

                        if (solutionText.isNullOrBlank()) {
                            Result.failure(Exception("AI modelinden geçerli bir yanıt alınamadı. Lütfen soruyu tekrar göndermeyi deneyin."))
                        } else {
                            Result.success(solutionText)
                        }
                    }
                    400 -> {
                        val parsedError = try {
                            json.decodeFromString<GeminiResponse>(responseBodyString).error?.message
                        } catch (_: Exception) {
                            null
                        }
                        val errorDetail = parsedError ?: "Geçersiz istek veya hatalı API parametresi (HTTP 400)."
                        Result.failure(Exception("Gemini API Hatası ($errorDetail)"))
                    }
                    404 -> {
                        Result.failure(
                            Exception("Seçilen '$cleanModel' modeli Google tarafından bulunamadı veya kullanımdan kaldırılmış (HTTP 404).")
                        )
                    }
                    401, 403 -> {
                        Result.failure(
                            Exception("Geçersiz Gemini API Anahtarı veya yetkisiz erişim. Lütfen Ayarlar sayfasından API anahtarınızı kontrol edin.")
                        )
                    }
                    429 -> {
                        Result.failure(
                            Exception("API kullanım kotanız doldu veya istek sınırı aşıldı (Rate limit). Lütfen bir süre bekleyip tekrar deneyin.")
                        )
                    }
                    500, 502, 503, 504 -> {
                        Result.failure(
                            Exception("Google AI Studio sunucularında geçici bir kesinti yaşanıyor (HTTP ${response.code}). Lütfen daha sonra tekrar deneyin.")
                        )
                    }
                    else -> {
                        val parsedError = try {
                            json.decodeFromString<GeminiResponse>(responseBodyString).error?.message
                        } catch (_: Exception) {
                            null
                        }
                        val errorDetail = parsedError ?: responseBodyString.take(200).ifBlank { "HTTP ${response.code}" }
                        Result.failure(
                            Exception("Gemini API Hatası ($errorDetail). Lütfen ayarlarınızı ve internet bağlantınızı kontrol edin.")
                        )
                    }
                }
            }
        } catch (e: SocketTimeoutException) {
            Result.failure<String>(
                Exception("İstek zaman aşımına uğradı. Lütfen internet bağlantınızı kontrol edip tekrar deneyin.", e)
            )
        } catch (e: UnknownHostException) {
            Result.failure<String>(
                Exception("İnternet bağlantısı kurulamadı. Lütfen ağ bağlantınızı kontrol edin.", e)
            )
        } catch (e: IOException) {
            Result.failure<String>(
                Exception("Ağ iletişim hatası oluştu: ${e.localizedMessage ?: "Sunucuya bağlanılamadı."}", e)
            )
        } catch (e: Exception) {
            Result.failure<String>(
                Exception("Çözüm alınırken beklenmeyen bir hata oluştu: ${e.localizedMessage ?: "Bilinmeyen hata"}", e)
            )
        }
    }
}
