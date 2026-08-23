package com.sorudefteri.data.ai

import android.util.Base64
import com.sorudefteri.data.ai.dto.GroqChatRequest
import com.sorudefteri.data.ai.dto.GroqChatResponse
import com.sorudefteri.data.ai.dto.GroqContentPart
import com.sorudefteri.data.ai.dto.GroqImageUrl
import com.sorudefteri.data.ai.dto.GroqMessage
import kotlinx.coroutines.Dispatchers
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
 * Groq OpenAI uyumlu API üzerinden görsel destekli soru çözümü sağlayan servis.
 */
@Singleton
class GroqProvider @Inject constructor(
    private val okHttpClient: OkHttpClient
) : CozumProvider {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        coerceInputValues = true
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun cozumIste(
        imageBytes: ByteArray,
        dersAdi: String,
        konu: String,
        correctAnswer: String?,
        modelName: String,
        apiKey: String
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("Groq API anahtarı boş olamaz. Lütfen Ayarlar sayfasından anahtarınızı girin.")
            )
        }

        val effectiveModel = modelName.ifBlank { "meta-llama/llama-4-scout-17b-16e-instruct" }

        val answerText = if (!correctAnswer.isNullOrBlank()) {
            "Sorunun doğru cevabı $correctAnswer şıkkıdır."
        } else {
            ""
        }

        val prompt = "Sen uzman bir YKS öğretmenisin. Bu görseldeki soruyu incele. Ders: $dersAdi, Konu: $konu. $answerText\n" +
                "Lütfen şu adımlarla öğretici ve net bir çözüm sun:\n" +
                "1) Doğru Cevap Şıkkı ve Net Sonuç\n" +
                "2) Adım Adım Ayrıntılı Çözüm\n" +
                "3) Bu Soru Tipi İçin Önemli İpuçları ve Püf Noktaları."

        try {
            val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            val dataUrl = "data:image/jpeg;base64,$base64Image"

            val requestBody = GroqChatRequest(
                model = effectiveModel,
                messages = listOf(
                    GroqMessage(
                        role = "user",
                        content = listOf(
                            GroqContentPart(
                                type = "text",
                                text = prompt
                            ),
                            GroqContentPart(
                                type = "image_url",
                                image_url = GroqImageUrl(url = dataUrl)
                            )
                        )
                    )
                ),
                temperature = 0.2
            )

            val requestJson = json.encodeToString(GroqChatRequest.serializer(), requestBody)
            val url = "https://api.groq.com/openai/v1/chat/completions"

            val httpRequest = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .post(requestJson.toRequestBody(jsonMediaType))
                .build()

            okHttpClient.newCall(httpRequest).execute().use { response ->
                val responseBodyString = response.body?.string().orEmpty()

                when (response.code) {
                    200 -> {
                        val groqResponse = try {
                            json.decodeFromString<GroqChatResponse>(responseBodyString)
                        } catch (e: Exception) {
                            return@withContext Result.failure(
                                Exception("Groq API yanıtı çözümlenemedi: ${e.localizedMessage}", e)
                            )
                        }

                        if (groqResponse.error != null) {
                            return@withContext Result.failure(
                                Exception(groqResponse.error.message ?: "Groq API hatası oluştu.")
                            )
                        }

                        val solutionText = groqResponse.choices
                            ?.firstOrNull()
                            ?.message
                            ?.content
                            ?.trim()

                        if (solutionText.isNullOrBlank()) {
                            Result.failure(Exception("AI modelinden geçerli bir yanıt alınamadı."))
                        } else {
                            Result.success(solutionText)
                        }
                    }
                    429 -> {
                        Result.failure(
                            Exception("Groq API kullanım kotanız doldu veya istek sınırı aşıldı (Rate limit). Lütfen biraz bekleyip tekrar deneyin.")
                        )
                    }
                    400, 401, 403 -> {
                        Result.failure(
                            Exception("Geçersiz Groq API Anahtarı. Lütfen Ayarlar sayfasından anahtarınızı kontrol edin.")
                        )
                    }
                    else -> {
                        val parsedError = try {
                            json.decodeFromString<GroqChatResponse>(responseBodyString).error?.message
                        } catch (_: Exception) {
                            null
                        }
                        val errorDetail = parsedError ?: responseBodyString.take(200).ifBlank { "HTTP ${response.code}" }
                        Result.failure(
                            Exception("Groq API Hatası ($errorDetail). Lütfen ayarlarınızı kontrol edin.")
                        )
                    }
                }
            }
        } catch (e: SocketTimeoutException) {
            Result.failure(
                Exception("İstek zaman aşımına uğradı. Lütfen internet bağlantınızı kontrol edip tekrar deneyin.", e)
            )
        } catch (e: UnknownHostException) {
            Result.failure(
                Exception("İnternet bağlantısı kurulamadı. Lütfen ağ bağlantınızı kontrol edin.", e)
            )
        } catch (e: IOException) {
            Result.failure(
                Exception("Ağ iletişim hatası: ${e.localizedMessage ?: "Sunucuya bağlanılamadı."}", e)
            )
        } catch (e: Exception) {
            Result.failure(
                Exception("Çözüm alınırken beklenmeyen bir hata oluştu: ${e.localizedMessage ?: "Bilinmeyen hata"}", e)
            )
        }
    }
}
