package com.sorudefteri.data.ai

import android.util.Base64
import com.sorudefteri.data.ai.dto.GeminiContent
import com.sorudefteri.data.ai.dto.GeminiInlineData
import com.sorudefteri.data.ai.dto.GeminiModelListResponse
import com.sorudefteri.data.ai.dto.GeminiPart
import com.sorudefteri.data.ai.dto.GeminiRequest
import com.sorudefteri.data.ai.dto.GeminiResponse
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
 * Google Gemini API üzerinden görsel ve metin tabanlı soru çözümü, soru üretimi,
 * OCR temizleme ve benzer soru türetme servisi.
 */
@Singleton
open class GeminiProvider @Inject constructor(
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
        const val FALLBACK_MODEL = "gemini-2.5-flash" // use AiProviderType.GEMINI.defaultModel centrally
        const val MAX_RETRIES = 3
    }

    /**
     * Google Gemini API'sinden kullanıcının API anahtarına açık tüm güncel modelleri canlı çeker.
     */
    open suspend fun listModels(apiKey: String): Result<List<Pair<String, String>>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                AiException(AiErrorType.INVALID_API_KEY, "Gemini API anahtarı boş olamaz.")
            )
        }

        try {
            val url = "https://generativelanguage.googleapis.com/v1/models"
            val httpRequest = Request.Builder()
                .url(url)
                .addHeader("x-goog-api-key", apiKey)
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
                        AiException(
                            if (response.code in listOf(401, 403)) AiErrorType.INVALID_API_KEY else AiErrorType.MODEL_UNAVAILABLE,
                            "Gemini modelleri çekilemedi ($errorDetail). Lütfen API anahtarınızı kontrol edin."
                        )
                    )
                }

                val modelListResponse = json.decodeFromString<GeminiModelListResponse>(responseBodyString)
                val supportedModels = modelListResponse.models.orEmpty()
                    .filter { item ->
                        item.supportedGenerationMethods.contains("generateContent") &&
                                !item.name.contains("embedding", ignoreCase = true) &&
                                !item.name.contains("aqa", ignoreCase = true)
                    }
                    .map { item ->
                        val cleanName = item.name.removePrefix("models/")
                        val label = item.displayName?.ifBlank { cleanName } ?: cleanName
                        cleanName to "$label ($cleanName)"
                    }

                if (supportedModels.isEmpty()) {
                    Result.success(
                        listOf(
                            "gemini-2.5-flash" to "Gemini 2.5 Flash (gemini-2.5-flash)",
                            "gemini-2.5-pro" to "Gemini 2.5 Pro (gemini-2.5-pro)",
                            "gemini-2.0-flash" to "Gemini 2.0 Flash (gemini-2.0-flash)"
                        )
                    )
                } else {
                    Result.success(supportedModels)
                }
            }
        } catch (e: Exception) {
            Result.failure(mapToAiException(e))
        }
    }

    /**
     * CozumProvider standart arayüz metodunun uygulanması.
     */
    override suspend fun cozumIste(
        imageBytes: ByteArray,
        dersAdi: String,
        konu: String,
        correctAnswer: String?,
        modelName: String,
        apiKey: String
    ): Result<String> {
        return cozumIsteDetayli(
            imageBytes = imageBytes,
            dersAdi = dersAdi,
            konu = konu,
            correctAnswer = correctAnswer,
            mistakeType = null,
            soruMetni = null,
            modelName = modelName,
            apiKey = apiKey
        )
    }

    /**
     * Görsel ve/veya metin tabanlı (Koşullu OCR destekli) detaylı çözüm üretme.
     */
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
            return@withContext Result.failure(
                AiException(
                    AiErrorType.INVALID_API_KEY,
                    "Google Gemini API Anahtarı bulunamadı. Lütfen Ayarlar sayfasından API anahtarınızı girin."
                )
            )
        }

        val prompt = promptManager.buildCozumUretimiPrompt(
            dersAdi = dersAdi,
            konu = konu,
            soruMetni = soruMetni,
            correctAnswer = correctAnswer,
            mistakeType = mistakeType,
            customTemplate = customPromptTemplate
        )

        val effectiveModel = modelName.removePrefix("models/").ifBlank { FALLBACK_MODEL }
        executeGenerateContentWithRetry(imageBytes, prompt, effectiveModel, apiKey)
    }

    /**
     * OCR çıktısındaki hataları düzelten ve matematiksel formülleri LaTeX formatına çeviren servis.
     */
    open suspend fun cleanOcrText(
        rawOcrText: String,
        modelName: String,
        apiKey: String,
        customPromptTemplate: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                AiException(AiErrorType.INVALID_API_KEY, "Gemini API anahtarı boş olamaz.")
            )
        }

        val prompt = promptManager.buildOcrTemizlemePrompt(rawOcrText, customPromptTemplate)
        val effectiveModel = modelName.removePrefix("models/").ifBlank { FALLBACK_MODEL }
        // OCR temizleme sadece metin tabanlıdır, görsel göndermeye gerek yoktur
        executeGenerateContentWithRetry(null, prompt, effectiveModel, apiKey)
    }

    /**
     * Mevcut bir sorunun kazanımını koruyarak yeni sayılar/senaryo ile benzer soru türetir (Question Cloner).
     */
    open suspend fun generateSimilarQuestion(
        imageBytes: ByteArray?,
        dersAdi: String,
        konu: String,
        soruMetni: String? = null,
        modelName: String,
        apiKey: String,
        customPromptTemplate: String? = null
    ): Result<SimilarQuestionResult> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                AiException(AiErrorType.INVALID_API_KEY, "Gemini API anahtarı boş olamaz.")
            )
        }

        val prompt = promptManager.buildBenzerSoruPrompt(dersAdi, konu, soruMetni, customPromptTemplate)
        val effectiveModel = modelName.removePrefix("models/").ifBlank { FALLBACK_MODEL }

        val rawResult = executeGenerateContentWithRetry(imageBytes, prompt, effectiveModel, apiKey)
        rawResult.mapCatching { rawText ->
            val parseResult = AiJsonParser.cleanAndParseJson(
                rawText = rawText,
                serializer = SimilarQuestionResult.serializer(),
                fallbackParser = { AiJsonParser.parseSimilarQuestionFallback(it) }
            )
            parseResult.getOrThrow()
        }
    }

    /**
     * Konu, zorluk, alan ve sınav tipine göre sıfırdan test sorusu üretir.
     */
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
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                AiException(AiErrorType.INVALID_API_KEY, "Gemini API anahtarı boş olamaz.")
            )
        }

        val prompt = promptManager.buildSoruUretimiPrompt(
            dersAdi = dersAdi,
            konu = konu,
            zorluk = zorluk,
            alan = alan,
            sinavTipi = sinavTipi,
            customTemplate = customPromptTemplate
        )
        val effectiveModel = modelName.removePrefix("models/").ifBlank { FALLBACK_MODEL }

        val rawResult = executeGenerateContentWithRetry(null, prompt, effectiveModel, apiKey)
        rawResult.mapCatching { rawText ->
            val parseResult = AiJsonParser.cleanAndParseJson(
                rawText = rawText,
                serializer = SimilarQuestionResult.serializer(),
                fallbackParser = { AiJsonParser.parseSimilarQuestionFallback(it) }
            )
            parseResult.getOrThrow()
        }
    }

    /**
     * Soru görselinden sınav türü, ders, konu ve cevap şıkkını tespit eder (Auto-Tagging).
     */
    open suspend fun detectMetadata(
        imageBytes: ByteArray,
        modelName: String,
        apiKey: String
    ): Result<QuestionMetadata> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                AiException(AiErrorType.INVALID_API_KEY, "Gemini API anahtarı boş olamaz.")
            )
        }

        val prompt = """
            Görseldeki YKS (TYT veya AYT) sorusunu incele ve JSON formatında şu bilgileri çıkar:
            1. examType: "TYT" veya "AYT"
            2. subjectId: ["turkce", "matematik", "fizik", "kimya", "biyoloji", "tarih", "cografya", "felsefe", "din_kulturu", "ayt_matematik", "ayt_fizik", "ayt_kimya", "ayt_biyoloji", "ayt_edebiyat", "ayt_tarih1", "ayt_cografya1", "ydt_ingilizce"] değerlerinden en uygun olanı.
            3. topic: Sorunun ait olduğu net YKS konu başlığı (Örn: "Türev", "Paragrafta Anlam", "Kalıtım", "Optik", "Trigonometri", "Polinomlar").
            4. correctAnswer: Görsel üzerinde işaretlenmiş doğru cevap şıkkı veya bariz doğru cevap varsa "A", "B", "C", "D", "E" veya null.

            SADECE geçerli bir JSON objesi döndür:
            {"examType": "TYT", "subjectId": "matematik", "topic": "Fonksiyonlar", "correctAnswer": "C"}
        """.trimIndent()

        val effectiveModel = modelName.removePrefix("models/").ifBlank { FALLBACK_MODEL }
        val rawResult = executeGenerateContentWithRetry(imageBytes, prompt, effectiveModel, apiKey)
        if (rawResult.isSuccess) {
            val rawText = rawResult.getOrNull().orEmpty()
            val parseResult = AiJsonParser.cleanAndParseJson(
                rawText = rawText,
                serializer = QuestionMetadata.serializer(),
                fallbackParser = { AiJsonParser.parseQuestionMetadataFallback(it) }
            )
            if (parseResult.isSuccess) {
                return@withContext parseResult
            }
            // Parse başarısız olsa bile rawResult success; fallback boş metadata yerine hatayı ilet
            return@withContext parseResult
        }
        // Ağ / kota / model hatasını gizleme, doğrudan ilet
        return@withContext rawResult.map { QuestionMetadata() } as Result<QuestionMetadata>
    }

    /**
     * Exponential backoff ile 3 kez otomatik tekrar deneme (Retry) mantığı.
     */
    private suspend fun executeGenerateContentWithRetry(
        imageBytes: ByteArray?,
        prompt: String,
        modelName: String,
        apiKey: String
    ): Result<String> {
        val httpRequest = try {
            buildHttpRequest(imageBytes, prompt, modelName, apiKey)
        } catch (e: Exception) {
            return Result.failure(e)
        }

        var lastResult: Result<String> = Result.failure(
            AiException(AiErrorType.UNKNOWN, "Bilinmeyen bir hata oluştu.")
        )

        for (attempt in 0 until MAX_RETRIES) {
            lastResult = executeHttpCall(httpRequest, modelName)
            if (lastResult.isSuccess) {
                return lastResult
            }

            val exception = lastResult.exceptionOrNull()
            // 404 MODEL_UNAVAILABLE asla retry edilmez
            if (exception is AiException && exception.errorType == AiErrorType.MODEL_UNAVAILABLE && exception.message?.contains("404") == true) {
                break
            }
            val isRetryable = isRetryableException(exception)

            if (isRetryable && attempt < MAX_RETRIES - 1) {
                val base = 1000L * (1L shl attempt) // 1s, 2s, 4s
                val jitter = (500..1000).random().toLong()
                delay(base + jitter)
            } else {
                break
            }
        }
        return lastResult
    }

    private fun isRetryableException(throwable: Throwable?): Boolean {
        if (throwable == null) return false
        // Cancellation asla retry edilmez
        if (throwable is kotlinx.coroutines.CancellationException) return false
        if (throwable is AiException) {
            // 404 MODEL_UNAVAILABLE retry edilmez (model bulunamadı kalıcı hata)
            // QUOTA_EXCEEDED retry edilmez - kota doluyken retry kota yakar
            // Sadece NETWORK_* ve 5xx (MODEL_UNAVAILABLE non-404) transient retry
            return throwable.errorType == AiErrorType.NETWORK_TIMEOUT ||
                    throwable.errorType == AiErrorType.NETWORK_ERROR ||
                    (throwable.errorType == AiErrorType.MODEL_UNAVAILABLE && !throwable.message.orEmpty().contains("404"))
        }
        val msg = throwable.message.orEmpty().lowercase()
        return msg.contains("500") ||
                msg.contains("502") ||
                msg.contains("503") ||
                msg.contains("504") ||
                msg.contains("timeout") ||
                throwable is SocketTimeoutException ||
                throwable is InterruptedIOException
    }

    private fun buildHttpRequest(
        imageBytes: ByteArray?,
        prompt: String,
        modelName: String,
        apiKey: String
    ): Request {
        val parts = mutableListOf<GeminiPart>()
        parts.add(GeminiPart(text = prompt))

        // Koşullu görsel aktarımı: Görsel varsa ekle, yoksa sadece metin ilet
        if (imageBytes != null && imageBytes.isNotEmpty()) {
            // 5MB üstü görsel istek boyutu aşar, erken hata ver
            if (imageBytes.size > 5 * 1024 * 1024) {
                throw AiException(
                    AiErrorType.UNKNOWN,
                    "Görsel boyutu çok büyük (${imageBytes.size / 1024}KB). Lütfen daha küçük bir görsel deneyin."
                )
            }
            val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            parts.add(
                GeminiPart(
                    inlineData = GeminiInlineData(
                        mimeType = "image/jpeg",
                        data = base64Image
                    )
                )
            )
        }

        val requestBody = GeminiRequest(
            contents = listOf(
                GeminiContent(parts = parts)
            )
        )

        val requestJson = json.encodeToString(GeminiRequest.serializer(), requestBody)
        val cleanModel = modelName.removePrefix("models/").trim()
        val url = "https://generativelanguage.googleapis.com/v1/models/$cleanModel:generateContent"

        return Request.Builder()
            .url(url)
            .addHeader("x-goog-api-key", apiKey)
            .post(requestJson.toRequestBody(jsonMediaType))
            .build()
    }

    private fun executeHttpCall(
        httpRequest: Request,
        modelName: String
    ): Result<String> {
        val cleanModel = modelName.removePrefix("models/").trim()
        return try {
            okHttpClient.newCall(httpRequest).execute().use { response ->
                val responseBodyString = response.body?.string().orEmpty()

                when (response.code) {
                    200 -> {
                        val geminiResponse = try {
                            json.decodeFromString<GeminiResponse>(responseBodyString)
                        } catch (e: Exception) {
                            return Result.failure(
                                AiException(
                                    AiErrorType.PARSING_FAILED,
                                    "Gemini API yanıtı çözümlenemedi: ${e.localizedMessage}",
                                    e
                                )
                            )
                        }

                        if (geminiResponse.error != null) {
                            return Result.failure(
                                AiException(
                                    AiErrorType.MODEL_UNAVAILABLE,
                                    geminiResponse.error.message ?: "Google Gemini API hatası oluştu."
                                )
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
                            Result.failure(
                                AiException(
                                    AiErrorType.MODEL_UNAVAILABLE,
                                    "AI modelinden geçerli bir yanıt alınamadı. Lütfen soruyu tekrar göndermeyi deneyin."
                                )
                            )
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
                        Result.failure(
                            AiException(AiErrorType.UNKNOWN, "Gemini API Hatası ($errorDetail)")
                        )
                    }
                    401, 403 -> {
                        Result.failure(
                            AiException(
                                AiErrorType.INVALID_API_KEY,
                                "Geçersiz Gemini API Anahtarı veya yetkisiz erişim. Lütfen Ayarlar sayfasından API anahtarınızı kontrol edin."
                            )
                        )
                    }
                    404 -> {
                        Result.failure(
                            AiException(
                                AiErrorType.MODEL_UNAVAILABLE,
                                "Seçilen '$cleanModel' modeli Google tarafından bulunamadı veya kullanımdan kaldırılmış (HTTP 404)."
                            )
                        )
                    }
                    429 -> {
                        Result.failure(
                            AiException(
                                AiErrorType.QUOTA_EXCEEDED,
                                "API kullanım kotanız doldu veya istek sınırı aşıldı (Rate limit). Lütfen bir süre bekleyip tekrar deneyin."
                            )
                        )
                    }
                    500, 502, 503, 504 -> {
                        Result.failure(
                            AiException(
                                AiErrorType.MODEL_UNAVAILABLE,
                                "Google AI Studio sunucularında geçici bir kesinti yaşanıyor (HTTP ${response.code}). Lütfen daha sonra tekrar deneyin."
                            )
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
                            AiException(
                                AiErrorType.UNKNOWN,
                                "Gemini API Hatası ($errorDetail). Lütfen ayarlarınızı ve internet bağlantınızı kontrol edin."
                            )
                        )
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
            is SocketTimeoutException -> AiException(
                AiErrorType.NETWORK_TIMEOUT,
                "İstek zaman aşımına uğradı. Lütfen internet bağlantınızı kontrol edip tekrar deneyin.",
                e
            )
            is InterruptedIOException -> AiException(
                AiErrorType.NETWORK_TIMEOUT,
                "İstek zaman aşımına uğradı. Lütfen internet bağlantınızı kontrol edip tekrar deneyin.",
                e
            )
            is UnknownHostException -> AiException(
                AiErrorType.NETWORK_ERROR,
                "İnternet bağlantısı kurulamadı. Lütfen ağ bağlantınızı kontrol edin.",
                e
            )
            is IOException -> AiException(
                AiErrorType.NETWORK_ERROR,
                "Ağ iletişim hatası oluştu: ${e.localizedMessage ?: "Sunucuya bağlanılamadı."}",
                e
            )
            else -> AiException(
                AiErrorType.UNKNOWN,
                "Beklenmeyen bir hata oluştu: ${e.localizedMessage ?: "Bilinmeyen hata"}",
                e
            )
        }
    }
}
