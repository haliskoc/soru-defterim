package com.sorudefteri.data.ai

import android.util.Base64
import com.sorudefteri.data.ai.dto.GeminiContent
import com.sorudefteri.data.ai.dto.GeminiInlineData
import com.sorudefteri.data.ai.dto.GeminiPart
import com.sorudefteri.data.ai.dto.GeminiRequest
import com.sorudefteri.data.ai.dto.GeminiResponse
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
                IllegalArgumentException("Gemini API anahtarı boş olamaz. Lütfen Ayarlar sayfasından anahtarınızı girin.")
            )
        }

        val effectiveModel = modelName.ifBlank { "gemini-2.0-flash" }

        val answerInfo = if (!correctAnswer.isNullOrBlank()) {
            "Öğrencinin belirttiği / Sorunun doğru cevap şıkkı: $correctAnswer"
        } else {
            "Doğru cevap şıkkı belirtilmemiş. Lütfen soruyu çözerek doğru şıkkı ve sonucu net olarak belirle."
        }

        val prompt = """
            Sen Türkiye YKS (TYT / AYT) sınavlarına hazırlanan öğrenciler için alanında uzman, pedagojik formasyona sahip kıdemli bir öğretmensin.
            Görseldeki soruyu öğrencinin eksiksiz anlayacağı şekilde, adım adım ve pedagojik yöntemlerle açıkla.

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

        try {
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
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$effectiveModel:generateContent?key=$apiKey"

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
                            return@withContext Result.failure(
                                Exception("Gemini API yanıtı çözümlenemedi: ${e.localizedMessage}", e)
                            )
                        }

                        if (geminiResponse.error != null) {
                            return@withContext Result.failure(
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
                        Result.failure(
                            Exception("Geçersiz istek veya hatalı API parametresi (HTTP 400). Lütfen API anahtarınızı ve model ayarlarınızı kontrol edin.")
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
            Result.failure(
                Exception("İstek zaman aşımına uğradı. Lütfen internet bağlantınızı kontrol edip tekrar deneyin.", e)
            )
        } catch (e: UnknownHostException) {
            Result.failure(
                Exception("İnternet bağlantısı kurulamadı. Lütfen ağ bağlantınızı kontrol edin.", e)
            )
        } catch (e: IOException) {
            Result.failure(
                Exception("Ağ iletişim hatası oluştu: ${e.localizedMessage ?: "Sunucuya bağlanılamadı."}", e)
            )
        } catch (e: Exception) {
            Result.failure(
                Exception("Çözüm alınırken beklenmeyen bir hata oluştu: ${e.localizedMessage ?: "Bilinmeyen hata"}", e)
            )
        }
    }
}
