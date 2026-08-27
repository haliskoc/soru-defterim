package com.sorudefteri.data.ai

import android.util.Base64
import com.sorudefteri.data.ai.dto.GroqChatRequest
import com.sorudefteri.data.ai.dto.GroqChatResponse
import com.sorudefteri.data.ai.dto.GroqContentPart
import com.sorudefteri.data.ai.dto.GroqImageUrl
import com.sorudefteri.data.ai.dto.GroqMessage
import com.sorudefteri.data.ai.dto.GroqModelListResponse
import com.sorudefteri.domain.model.AiErrorType
import com.sorudefteri.domain.model.AiException
import com.sorudefteri.domain.model.QuestionMetadata
import com.sorudefteri.domain.model.SimilarQuestionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NVIDIA Build (NIM) OpenAI uyumlu API üzerinden görsel ve metin tabanlı soru çözümü.
 * Endpoint: https://integrate.api.nvidia.com/v1/chat/completions
 * Docs: https://build.nvidia.com/explore/reasoning
 */
@Singleton
open class NvidiaProvider @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val promptManager: AiPromptManager,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        coerceInputValues = true
    }
) : CozumProvider {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    companion object {
        const val FALLBACK_MODEL = "meta/llama-3.1-405b-instruct"
        const val MAX_RETRIES = 3
    }

    open suspend fun listModels(apiKey: String): Result<List<Pair<String, String>>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                AiException(AiErrorType.INVALID_API_KEY, "NVIDIA API anahtarı boş olamaz.")
            )
        }
        try {
            val url = "https://integrate.api.nvidia.com/v1/models"
            val httpRequest = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .get()
                .build()

            okHttpClient.newCall(httpRequest).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val listResponse = try {
                        json.decodeFromString<GroqModelListResponse>(bodyStr)
                    } catch (e: Exception) {
                        return@withContext Result.failure(
                            AiException(AiErrorType.PARSING_FAILED, "Model listesi çözümlenemedi: ${e.localizedMessage}", e)
                        )
                    }
                    val models = listResponse.data.orEmpty()
                        .filter { it.active != false }
                        .map { it.id to it.id }
                    if (models.isEmpty()) {
                        Result.failure(AiException(AiErrorType.MODEL_UNAVAILABLE, "Kullanılabilir NVIDIA modeli bulunamadı."))
                    } else {
                        Result.success(models)
                    }
                } else {
                    val errorType = if (response.code in listOf(401, 403)) AiErrorType.INVALID_API_KEY else AiErrorType.MODEL_UNAVAILABLE
                    Result.failure(AiException(errorType, "NVIDIA modelleri alınamadı: HTTP ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(mapToAiException(e))
        }
    }

    override suspend fun cozumIste(
        imageBytes: ByteArray,
        dersAdi: String,
        konu: String,
        correctAnswer: String?,
        modelName: String,
        apiKey: String
    ): Result<String> {
        return cozumIsteDetayli(imageBytes, dersAdi, konu, correctAnswer, null, null, modelName, apiKey)
    }

    open suspend fun cozumIsteDetayli(
        imageBytes: ByteArray?,
        dersAdi: String,
        konu: String,
        correctAnswer: String?,
        mistakeType: String? = null,
        soruMetni: String? = null,
        modelName: String,
        apiKey: String,
        customPromptTemplate: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(AiException(AiErrorType.INVALID_API_KEY, "NVIDIA API anahtarı boş olamaz. Lütfen Ayarlar sayfasından anahtarınızı girin."))
        }
        val prompt = promptManager.buildCozumUretimiPrompt(dersAdi, konu, soruMetni, correctAnswer, mistakeType, customPromptTemplate)
        val effectiveModel = modelName.trim().removePrefix("models/").ifBlank { FALLBACK_MODEL }
        executeNvidiaWithRetry(imageBytes, prompt, effectiveModel, apiKey)
    }

    open suspend fun cleanOcrText(
        rawOcrText: String,
        modelName: String,
        apiKey: String,
        customPromptTemplate: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(AiException(AiErrorType.INVALID_API_KEY, "NVIDIA API anahtarı boş olamaz."))
        val prompt = promptManager.buildOcrTemizlemePrompt(rawOcrText, customPromptTemplate)
        val effectiveModel = modelName.trim().removePrefix("models/").ifBlank { FALLBACK_MODEL }
        executeNvidiaWithRetry(null, prompt, effectiveModel, apiKey)
    }

    open suspend fun generateSimilarQuestion(
        imageBytes: ByteArray?,
        dersAdi: String,
        konu: String,
        soruMetni: String? = null,
        modelName: String,
        apiKey: String,
        customPromptTemplate: String? = null
    ): Result<SimilarQuestionResult> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(AiException(AiErrorType.INVALID_API_KEY, "NVIDIA API anahtarı boş olamaz."))
        val prompt = promptManager.buildBenzerSoruPrompt(dersAdi, konu, soruMetni, customPromptTemplate)
        val effectiveModel = modelName.trim().removePrefix("models/").ifBlank { FALLBACK_MODEL }
        val rawResult = executeNvidiaWithRetry(imageBytes, prompt, effectiveModel, apiKey)
        rawResult.mapCatching { rawText ->
            val parseResult = AiJsonParser.cleanAndParseJson(rawText, SimilarQuestionResult.serializer()) { AiJsonParser.parseSimilarQuestionFallback(it) }
            parseResult.getOrThrow()
        }
    }

    open suspend fun generateQuestion(
        dersAdi: String,
        konu: String,
        zorluk: String,
        alan: String,
        sinavTipi: String,
        modelName: String,
        apiKey: String,
        customPromptTemplate: String? = null
    ): Result<SimilarQuestionResult> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(AiException(AiErrorType.INVALID_API_KEY, "NVIDIA API anahtarı boş olamaz."))
        val prompt = promptManager.buildSoruUretimiPrompt(dersAdi, konu, zorluk, alan, sinavTipi, customPromptTemplate)
        val effectiveModel = modelName.trim().removePrefix("models/").ifBlank { FALLBACK_MODEL }
        val rawResult = executeNvidiaWithRetry(null, prompt, effectiveModel, apiKey)
        rawResult.mapCatching { rawText ->
            val parseResult = AiJsonParser.cleanAndParseJson(rawText, SimilarQuestionResult.serializer()) { AiJsonParser.parseSimilarQuestionFallback(it) }
            parseResult.getOrThrow()
        }
    }

    open suspend fun detectMetadata(
        imageBytes: ByteArray,
        modelName: String,
        apiKey: String
    ): Result<QuestionMetadata> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(AiException(AiErrorType.INVALID_API_KEY, "NVIDIA API anahtarı boş olamaz."))
        val effectiveModel = modelName.trim().removePrefix("models/").ifBlank { FALLBACK_MODEL }
        val prompt = """
            Görseldeki YKS (TYT veya AYT) sorusunu incele ve JSON formatında şu bilgileri çıkar:
            1. examType: "TYT" veya "AYT"
            2. subjectId: ["turkce", "matematik", "fizik", "kimya", "biyoloji", "tarih", "cografya", "felsefe", "din_kulturu", "ayt_matematik", "ayt_fizik", "ayt_kimya", "ayt_biyoloji", "ayt_edebiyat", "ayt_tarih1", "ayt_cografya1", "ydt_ingilizce"] değerlerinden en uygun olanı.
            3. topic: Sorunun ait olduğu net YKS konu başlığı (Örn: "Türev", "Paragrafta Anlam", "Kalıtım", "Optik", "Trigonometri", "Polinomlar").
            4. correctAnswer: Görsel üzerinde işaretlenmiş doğru cevap şıkkı veya bariz doğru cevap varsa "A", "B", "C", "D", "E" veya null.

            SADECE geçerli bir JSON objesi döndür:
            {"examType": "TYT", "subjectId": "matematik", "topic": "Fonksiyonlar", "correctAnswer": "C"}
        """.trimIndent()
        val rawResult = executeNvidiaWithRetry(imageBytes, prompt, effectiveModel, apiKey)
        if (rawResult.isSuccess) {
            val rawText = rawResult.getOrNull().orEmpty()
            val parseResult = AiJsonParser.cleanAndParseJson(rawText, QuestionMetadata.serializer()) { AiJsonParser.parseQuestionMetadataFallback(it) }
            if (parseResult.isSuccess) return@withContext parseResult
            return@withContext parseResult
        }
        return@withContext rawResult.map { QuestionMetadata() } as Result<QuestionMetadata>
    }

    private suspend fun executeNvidiaWithRetry(
        imageBytes: ByteArray?,
        prompt: String,
        modelName: String,
        apiKey: String
    ): Result<String> {
        var lastResult: Result<String> = Result.failure(AiException(AiErrorType.UNKNOWN, "Bilinmeyen bir hata oluştu."))
        for (attempt in 0 until MAX_RETRIES) {
            lastResult = executeNvidiaRequest(imageBytes, prompt, modelName, apiKey)
            if (lastResult.isSuccess) return lastResult
            val ex = lastResult.exceptionOrNull()
            if (ex is AiException && ex.errorType == AiErrorType.MODEL_UNAVAILABLE && ex.message?.contains("404") == true) break
            if (isRetryableException(ex) && attempt < MAX_RETRIES - 1) {
                val base = 1000L * (1L shl attempt)
                val jitter = (500..1000).random().toLong()
                delay(base + jitter)
            } else break
        }
        return lastResult
    }

    private fun isRetryableException(throwable: Throwable?): Boolean {
        if (throwable == null) return false
        if (throwable is kotlinx.coroutines.CancellationException) return false
        if (throwable is AiException) {
            return throwable.errorType == AiErrorType.NETWORK_TIMEOUT ||
                    throwable.errorType == AiErrorType.NETWORK_ERROR ||
                    (throwable.errorType == AiErrorType.MODEL_UNAVAILABLE && !throwable.message.orEmpty().contains("404"))
        }
        val msg = throwable.message.orEmpty().lowercase()
        return msg.contains("500") || msg.contains("502") || msg.contains("503") || msg.contains("504") || msg.contains("timeout") || throwable is SocketTimeoutException || throwable is InterruptedIOException
    }

    private fun executeNvidiaRequest(
        imageBytes: ByteArray?,
        prompt: String,
        modelName: String,
        apiKey: String
    ): Result<String> {
        return try {
            val contentParts = mutableListOf<GroqContentPart>()
            contentParts.add(GroqContentPart(type = "text", text = prompt))
            if (imageBytes != null && imageBytes.isNotEmpty()) {
                if (imageBytes.size > 5 * 1024 * 1024) {
                    return Result.failure(AiException(AiErrorType.UNKNOWN, "Görsel boyutu çok büyük (${imageBytes.size / 1024}KB). Lütfen daha küçük bir görsel deneyin."))
                }
                val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
                val dataUrl = "data:image/jpeg;base64,$base64Image"
                contentParts.add(GroqContentPart(type = "image_url", image_url = GroqImageUrl(url = dataUrl)))
            }
            val requestBody = GroqChatRequest(
                model = modelName,
                messages = listOf(GroqMessage(role = "user", content = contentParts)),
                temperature = 0.2
            )
            val requestJson = json.encodeToString(GroqChatRequest.serializer(), requestBody)
            val url = "https://integrate.api.nvidia.com/v1/chat/completions"
            val httpRequest = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Accept", "application/json")
                .post(requestJson.toRequestBody(jsonMediaType))
                .build()
            okHttpClient.newCall(httpRequest).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                when (response.code) {
                    200 -> {
                        val groqResponse = try {
                            json.decodeFromString<GroqChatResponse>(bodyStr)
                        } catch (e: Exception) {
                            return Result.failure(AiException(AiErrorType.PARSING_FAILED, "NVIDIA API yanıtı çözümlenemedi: ${e.localizedMessage}", e))
                        }
                        if (groqResponse.error != null) {
                            return Result.failure(AiException(AiErrorType.MODEL_UNAVAILABLE, groqResponse.error.message ?: "NVIDIA API hatası oluştu."))
                        }
                        val solutionText = groqResponse.choices?.firstOrNull()?.message?.content?.trim()
                        if (solutionText.isNullOrBlank()) {
                            Result.failure(AiException(AiErrorType.MODEL_UNAVAILABLE, "AI modelinden geçerli bir yanıt alınamadı. Lütfen soruyu tekrar göndermeyi deneyin."))
                        } else {
                            Result.success(solutionText)
                        }
                    }
                    400 -> Result.failure(AiException(AiErrorType.UNKNOWN, "Geçersiz istek veya hatalı model parametresi (HTTP 400). Lütfen NVIDIA model ayarlarınızı kontrol edin."))
                    401, 403 -> Result.failure(AiException(AiErrorType.INVALID_API_KEY, "Geçersiz NVIDIA API Anahtarı veya yetkisiz erişim. Lütfen Ayarlar sayfasından anahtarınızı kontrol edin."))
                    404 -> Result.failure(AiException(AiErrorType.MODEL_UNAVAILABLE, "Seçilen '$modelName' modeli NVIDIA tarafından bulunamadı veya kullanımdan kaldırılmış (HTTP 404). Model ayarınızı güncelleyin."))
                    429 -> Result.failure(AiException(AiErrorType.QUOTA_EXCEEDED, "NVIDIA API kullanım kotanız doldu veya istek sınırı aşıldı (Rate limit). Lütfen biraz bekleyip tekrar deneyin."))
                    500, 502, 503, 504 -> Result.failure(AiException(AiErrorType.MODEL_UNAVAILABLE, "NVIDIA sunucularında geçici bir kesinti yaşanıyor (HTTP ${response.code}). Lütfen daha sonra tekrar deneyin."))
                    else -> {
                        val parsedError = try { json.decodeFromString<GroqChatResponse>(bodyStr).error?.message } catch (_: Exception) { null }
                        val detail = parsedError ?: bodyStr.take(200).ifBlank { "HTTP ${response.code}" }
                        Result.failure(AiException(AiErrorType.UNKNOWN, "NVIDIA API Hatası ($detail). Lütfen ayarlarınızı ve internet bağlantınızı kontrol edin."))
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(mapToAiException(e))
        }
    }

    private fun mapToAiException(e: Exception): AiException {
        if (e is kotlinx.coroutines.CancellationException) throw e
        return when (e) {
            is AiException -> e
            is SocketTimeoutException -> AiException(AiErrorType.NETWORK_TIMEOUT, "İstek zaman aşımına uğradı. Lütfen internet bağlantınızı kontrol edip tekrar deneyin.", e)
            is InterruptedIOException -> AiException(AiErrorType.NETWORK_TIMEOUT, "İstek zaman aşımına uğradı. Lütfen internet bağlantınızı kontrol edip tekrar deneyin.", e)
            is UnknownHostException -> AiException(AiErrorType.NETWORK_ERROR, "İnternet bağlantısı yok veya sunucuya erişilemiyor. Lütfen ağ bağlantınızı kontrol edin.", e)
            is IOException -> AiException(AiErrorType.NETWORK_ERROR, "Ağ iletişim hatası oluştu: ${e.localizedMessage ?: "Sunucuya bağlanılamadı."}", e)
            else -> AiException(AiErrorType.UNKNOWN, "Çözüm alınırken beklenmeyen bir hata oluştu: ${e.localizedMessage ?: "Bilinmeyen hata"}", e)
        }
    }
}
