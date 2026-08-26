package com.sorudefteri.data.ai

import com.sorudefteri.data.local.AyarlarDataStore
import com.sorudefteri.domain.DersTaksonomisi
import com.sorudefteri.domain.model.AiErrorType
import com.sorudefteri.domain.model.AiException
import com.sorudefteri.domain.model.AiProviderType
import com.sorudefteri.domain.model.QuestionMetadata
import com.sorudefteri.domain.model.SimilarQuestionResult
import com.sorudefteri.domain.model.Soru
import com.sorudefteri.domain.repository.AiRepository as AiRepositoryInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI Soru Çözüm Katmanı Deposu (Repository).
 * AyarlarDataStore üzerinden aktif sağlayıcı (Gemini / Groq), model ve API anahtarı bilgilerini okuyarak
 * soru görselini/metnini ilgili sağlayıcıya iletir.
 */
@Singleton
class AiRepository @Inject constructor(
    private val ayarlarDataStore: AyarlarDataStore,
    private val geminiProvider: GeminiProvider,
    private val groqProvider: GroqProvider,
    private val nvidiaProvider: NvidiaProvider
) : AiRepositoryInterface {

    /**
     * Domain Soru nesnesi üzerinden AI çözümü üretir.
     * Koşullu OCR desteği: sendImage = false ise görsel okunmaz, doğrudan extractedText iletilir.
     */
    override suspend fun getAiSolution(
        soru: Soru,
        sendImage: Boolean,
        extractedText: String?
    ): Result<String> {
        val dersAdi = DersTaksonomisi.getDersAdi(soru.examType, soru.subjectId)
        val mistakeName = soru.mistakeType?.displayName

        if (!sendImage && !extractedText.isNullOrBlank()) {
            return getAiSolutionFromText(
                dersAdi = dersAdi,
                konu = soru.topic,
                soruMetni = extractedText,
                correctAnswer = soru.correctAnswer,
                mistakeType = mistakeName
            )
        }

        // Görseli diskten oku
        val file = File(soru.imagePath)
        val imageBytes = if (file.exists() && file.canRead()) {
            withContext(Dispatchers.IO) {
                try {
                    file.readBytes()
                } catch (_: Exception) {
                    null
                }
            }
        } else {
            null
        }

        if (sendImage && imageBytes == null) {
            return Result.failure(
                FileNotFoundException("Soru görseli bulunamadı veya okunamıyor: ${soru.imagePath}")
            )
        }

        return cozumIsteGelismi(
            imageBytes = if (sendImage) imageBytes else null,
            dersAdi = dersAdi,
            konu = soru.topic,
            correctAnswer = soru.correctAnswer,
            mistakeType = mistakeName,
            soruMetni = extractedText
        )
    }

    /**
     * Metin tabanlı sorularda veya OCR çıktısı ile doğrudan AI çözümü üretir (Görsel gönderilmez).
     */
    override suspend fun getAiSolutionFromText(
        dersAdi: String,
        konu: String,
        soruMetni: String,
        correctAnswer: String?,
        mistakeType: String?
    ): Result<String> {
        if (soruMetni.isBlank()) {
            return Result.failure(IllegalArgumentException("Soru metni boş olamaz."))
        }
        return cozumIsteGelismi(
            imageBytes = null,
            dersAdi = dersAdi,
            konu = konu,
            correctAnswer = correctAnswer,
            mistakeType = mistakeType,
            soruMetni = soruMetni
        )
    }

    /**
     * OCR ile okunan ham metni düzeltir ve matematiksel LaTeX formüllerini iyileştirir.
     */
    override suspend fun cleanOcrText(rawOcrText: String): Result<String> {
        if (rawOcrText.isBlank()) {
            return Result.success("")
        }

        val providerType = ayarlarDataStore.getAiProvider()
        return when (providerType) {
            AiProviderType.GEMINI -> {
                val apiKey = ayarlarDataStore.getGeminiApiKey().trim()
                if (apiKey.isBlank()) return Result.failure(AiException(AiErrorType.INVALID_API_KEY, "Gemini API anahtarı girilmemiş."))
                val model = ayarlarDataStore.getGeminiModel().ifBlank { AyarlarDataStore.DEFAULT_GEMINI_MODEL }
                geminiProvider.cleanOcrText(rawOcrText, model, apiKey)
            }
            AiProviderType.GROQ -> {
                val apiKey = ayarlarDataStore.getGroqApiKey().trim()
                if (apiKey.isBlank()) return Result.failure(AiException(AiErrorType.INVALID_API_KEY, "Groq API anahtarı girilmemiş."))
                val model = ayarlarDataStore.getGroqModel().ifBlank { AyarlarDataStore.DEFAULT_GROQ_MODEL }
                groqProvider.cleanOcrText(rawOcrText, model, apiKey)
            }
            AiProviderType.NVIDIA -> {
                val apiKey = ayarlarDataStore.getNvidiaApiKey().trim()
                if (apiKey.isBlank()) return Result.failure(AiException(AiErrorType.INVALID_API_KEY, "NVIDIA API anahtarı girilmemiş. Ayarlar > NVIDIA Build bölümünden ekleyin."))
                val model = ayarlarDataStore.getNvidiaModel().ifBlank { AyarlarDataStore.DEFAULT_NVIDIA_MODEL }
                nvidiaProvider.cleanOcrText(rawOcrText, model, apiKey)
            }
        }
    }

    /**
     * Aktif AI sağlayıcısı üzerinden görselden sınav türü, ders, konu ve cevap şıkkı tespiti yapar (Auto-Tagging).
     */
    override suspend fun detectQuestionMetadata(imageBytes: ByteArray): Result<QuestionMetadata> {
        val providerType = ayarlarDataStore.getAiProvider()
        return when (providerType) {
            AiProviderType.GEMINI -> {
                val apiKey = ayarlarDataStore.getGeminiApiKey().trim()
                if (apiKey.isBlank()) return Result.failure(AiException(AiErrorType.INVALID_API_KEY, "Gemini API anahtarı girilmemiş."))
                val model = ayarlarDataStore.getGeminiModel().ifBlank { AyarlarDataStore.DEFAULT_GEMINI_MODEL }
                geminiProvider.detectMetadata(imageBytes, model, apiKey)
            }
            AiProviderType.GROQ -> {
                val apiKey = ayarlarDataStore.getGroqApiKey().trim()
                if (apiKey.isBlank()) return Result.failure(AiException(AiErrorType.INVALID_API_KEY, "Groq API anahtarı girilmemiş."))
                val model = ayarlarDataStore.getGroqModel().ifBlank { AyarlarDataStore.DEFAULT_GROQ_MODEL }
                groqProvider.detectMetadata(imageBytes, model, apiKey)
            }
            AiProviderType.NVIDIA -> {
                val apiKey = ayarlarDataStore.getNvidiaApiKey().trim()
                if (apiKey.isBlank()) return Result.failure(AiException(AiErrorType.INVALID_API_KEY, "NVIDIA API anahtarı girilmemiş."))
                val model = ayarlarDataStore.getNvidiaModel().ifBlank { AyarlarDataStore.DEFAULT_NVIDIA_MODEL }
                nvidiaProvider.detectMetadata(imageBytes, model, apiKey)
            }
        }
    }

    /**
     * Mevcut soru görselinden ve/veya metninden yola çıkarak benzer soru türetir.
     */
    override suspend fun generateSimilarQuestion(
        imageBytes: ByteArray?,
        dersAdi: String,
        konu: String,
        soruMetni: String?
    ): Result<SimilarQuestionResult> {
        // Geliştirici notu: NVIDIA Build görsel gönderimi destekler ama metin-only de çalışır
        if (imageBytes == null && soruMetni.isNullOrBlank() && konu.isBlank()) {
            return Result.failure(IllegalArgumentException("Benzer soru üretmek için görsel veya konu bilgisi gereklidir."))
        }
        val providerType = ayarlarDataStore.getAiProvider()
        return when (providerType) {
            AiProviderType.GEMINI -> {
                val apiKey = ayarlarDataStore.getGeminiApiKey().trim()
                if (apiKey.isBlank()) return Result.failure(AiException(AiErrorType.INVALID_API_KEY, "Gemini API anahtarı girilmemiş."))
                val model = ayarlarDataStore.getGeminiModel().ifBlank { AyarlarDataStore.DEFAULT_GEMINI_MODEL }
                geminiProvider.generateSimilarQuestion(imageBytes, dersAdi, konu, soruMetni, model, apiKey)
            }
            AiProviderType.GROQ -> {
                val apiKey = ayarlarDataStore.getGroqApiKey().trim()
                if (apiKey.isBlank()) return Result.failure(AiException(AiErrorType.INVALID_API_KEY, "Groq API anahtarı girilmemiş."))
                val model = ayarlarDataStore.getGroqModel().ifBlank { AyarlarDataStore.DEFAULT_GROQ_MODEL }
                groqProvider.generateSimilarQuestion(imageBytes, dersAdi, konu, soruMetni, model, apiKey)
            }
            AiProviderType.NVIDIA -> {
                val apiKey = ayarlarDataStore.getNvidiaApiKey().trim()
                if (apiKey.isBlank()) return Result.failure(AiException(AiErrorType.INVALID_API_KEY, "NVIDIA API anahtarı girilmemiş."))
                val model = ayarlarDataStore.getNvidiaModel().ifBlank { AyarlarDataStore.DEFAULT_NVIDIA_MODEL }
                nvidiaProvider.generateSimilarQuestion(imageBytes, dersAdi, konu, soruMetni, model, apiKey)
            }
        }
    }

    /**
     * Geriye dönük uyumluluk: imageBytes ByteArray olan imza.
     */
    override suspend fun generateSimilarQuestion(
        imageBytes: ByteArray,
        dersAdi: String,
        konu: String
    ): Result<SimilarQuestionResult> {
        return generateSimilarQuestion(
            imageBytes = imageBytes,
            dersAdi = dersAdi,
            konu = konu,
            soruMetni = null
        )
    }

    /**
     * Konu ve kriterlere göre sıfırdan özgün soru üretir.
     */
    override suspend fun generateQuestion(
        dersAdi: String,
        konu: String,
        zorluk: String,
        alan: String,
        sinavTipi: String
    ): Result<SimilarQuestionResult> {
        val providerType = ayarlarDataStore.getAiProvider()
        return when (providerType) {
            AiProviderType.GEMINI -> {
                val apiKey = ayarlarDataStore.getGeminiApiKey().trim()
                if (apiKey.isBlank()) return Result.failure(AiException(AiErrorType.INVALID_API_KEY, "Gemini API anahtarı girilmemiş."))
                val model = ayarlarDataStore.getGeminiModel().ifBlank { AyarlarDataStore.DEFAULT_GEMINI_MODEL }
                geminiProvider.generateQuestion(dersAdi, konu, zorluk, alan, sinavTipi, model, apiKey)
            }
            AiProviderType.GROQ -> {
                val apiKey = ayarlarDataStore.getGroqApiKey().trim()
                if (apiKey.isBlank()) return Result.failure(AiException(AiErrorType.INVALID_API_KEY, "Groq API anahtarı girilmemiş."))
                val model = ayarlarDataStore.getGroqModel().ifBlank { AyarlarDataStore.DEFAULT_GROQ_MODEL }
                groqProvider.generateQuestion(dersAdi, konu, zorluk, alan, sinavTipi, model, apiKey)
            }
            AiProviderType.NVIDIA -> {
                val apiKey = ayarlarDataStore.getNvidiaApiKey().trim()
                if (apiKey.isBlank()) return Result.failure(AiException(AiErrorType.INVALID_API_KEY, "NVIDIA API anahtarı girilmemiş."))
                val model = ayarlarDataStore.getNvidiaModel().ifBlank { AyarlarDataStore.DEFAULT_NVIDIA_MODEL }
                nvidiaProvider.generateQuestion(dersAdi, konu, zorluk, alan, sinavTipi, model, apiKey)
            }
        }
    }

    /**
     * Aktif AI sağlayıcısı için API anahtarı girilip girilmediğini kontrol eder.
     */
    override suspend fun hasApiKey(): Boolean {
        val activeKey = ayarlarDataStore.getActiveApiKey()
        return activeKey.isNotBlank()
    }

    /**
     * Sağlayıcıdan canlı güncel modelleri çeker.
     */
    override suspend fun fetchLiveModels(provider: AiProviderType): Result<List<Pair<String, String>>> {
        return when (provider) {
            AiProviderType.GEMINI -> {
                val apiKey = ayarlarDataStore.getGeminiApiKey().trim()
                if (apiKey.isBlank()) {
                    Result.failure(AiException(AiErrorType.INVALID_API_KEY, "Gemini API anahtarı girilmemiş."))
                } else {
                    geminiProvider.listModels(apiKey)
                }
            }
            AiProviderType.GROQ -> {
                val apiKey = ayarlarDataStore.getGroqApiKey().trim()
                if (apiKey.isBlank()) {
                    Result.failure(AiException(AiErrorType.INVALID_API_KEY, "Groq API anahtarı girilmemiş."))
                } else {
                    groqProvider.listModels(apiKey)
                }
            }
            AiProviderType.NVIDIA -> {
                val apiKey = ayarlarDataStore.getNvidiaApiKey().trim()
                if (apiKey.isBlank()) {
                    Result.failure(AiException(AiErrorType.INVALID_API_KEY, "NVIDIA API anahtarı girilmemiş. Ayarlar > NVIDIA Build bölümünden ekleyin."))
                } else {
                    nvidiaProvider.listModels(apiKey)
                }
            }
        }
    }

    /**
     * Gelişmiş çözüm isteme merkezi metodu.
     */
    private suspend fun cozumIsteGelismi(
        imageBytes: ByteArray?,
        dersAdi: String,
        konu: String,
        correctAnswer: String?,
        mistakeType: String?,
        soruMetni: String?
    ): Result<String> {
        // Kritik validasyon: görsel ve metin ikisi de boşsa anlamsız istek
        if (imageBytes == null && soruMetni.isNullOrBlank()) {
            return Result.failure(
                AiException(
                    AiErrorType.UNKNOWN,
                    "Soru gönderilemedi: ne görsel var ne de metin. Lütfen görsel yükleyin veya OCR ile metin çıkarın ve 'Görseli AI'a gönder' ayarını kontrol edin."
                )
            )
        }
        val providerType = ayarlarDataStore.getAiProvider()

        return when (providerType) {
            AiProviderType.GEMINI -> {
                val apiKey = ayarlarDataStore.getGeminiApiKey().trim()
                if (apiKey.isBlank()) {
                    return Result.failure(
                        AiException(
                            AiErrorType.INVALID_API_KEY,
                            "API anahtarı bulunamadı. Lütfen Ayarlar bölümünden Gemini API anahtarınızı girin."
                        )
                    )
                }
                val model = ayarlarDataStore.getGeminiModel().ifBlank { AyarlarDataStore.DEFAULT_GEMINI_MODEL }
                geminiProvider.cozumIsteDetayli(
                    imageBytes = imageBytes,
                    dersAdi = dersAdi,
                    konu = konu,
                    correctAnswer = correctAnswer,
                    mistakeType = mistakeType,
                    soruMetni = soruMetni,
                    modelName = model,
                    apiKey = apiKey
                )
            }
            AiProviderType.GROQ -> {
                val apiKey = ayarlarDataStore.getGroqApiKey().trim()
                if (apiKey.isBlank()) {
                    return Result.failure(
                        AiException(
                            AiErrorType.INVALID_API_KEY,
                            "API anahtarı bulunamadı. Lütfen Ayarlar bölümünden Groq API anahtarınızı girin."
                        )
                    )
                }
                val model = ayarlarDataStore.getGroqModel().ifBlank { AyarlarDataStore.DEFAULT_GROQ_MODEL }
                groqProvider.cozumIsteDetayli(
                    imageBytes = imageBytes,
                    dersAdi = dersAdi,
                    konu = konu,
                    correctAnswer = correctAnswer,
                    mistakeType = mistakeType,
                    soruMetni = soruMetni,
                    modelName = model,
                    apiKey = apiKey
                )
            }
            AiProviderType.NVIDIA -> {
                val apiKey = ayarlarDataStore.getNvidiaApiKey().trim()
                if (apiKey.isBlank()) {
                    return Result.failure(
                        AiException(
                            AiErrorType.INVALID_API_KEY,
                            "API anahtarı bulunamadı. Lütfen Ayarlar bölümünden NVIDIA Build API anahtarınızı girin. (build.nvidia.com)"
                        )
                    )
                }
                val model = ayarlarDataStore.getNvidiaModel().ifBlank { AyarlarDataStore.DEFAULT_NVIDIA_MODEL }
                nvidiaProvider.cozumIsteDetayli(
                    imageBytes = imageBytes,
                    dersAdi = dersAdi,
                    konu = konu,
                    correctAnswer = correctAnswer,
                    mistakeType = mistakeType,
                    soruMetni = soruMetni,
                    modelName = model,
                    apiKey = apiKey
                )
            }
        }
    }

    /**
     * Eski imza: Dosya yolundan çözüm üretir.
     */
    suspend fun cozumIste(
        imagePath: String,
        dersAdi: String,
        konu: String,
        correctAnswer: String? = null
    ): Result<String> {
        val file = File(imagePath)
        if (!file.exists() || !file.canRead()) {
            return Result.failure(
                FileNotFoundException("Soru görseli bulunamadı veya okunamıyor: $imagePath")
            )
        }

        val imageBytes = withContext(Dispatchers.IO) {
            try {
                file.readBytes()
            } catch (e: Exception) {
                null
            }
        } ?: return Result.failure(IOException("Soru görseli diskten okunamadı: $imagePath"))

        return cozumIste(
            imageBytes = imageBytes,
            dersAdi = dersAdi,
            konu = konu,
            correctAnswer = correctAnswer
        )
    }

    /**
     * Eski imza: Byte dizisinden çözüm üretir.
     */
    suspend fun cozumIste(
        imageBytes: ByteArray,
        dersAdi: String,
        konu: String,
        correctAnswer: String? = null
    ): Result<String> {
        return cozumIsteGelismi(
            imageBytes = imageBytes,
            dersAdi = dersAdi,
            konu = konu,
            correctAnswer = correctAnswer,
            mistakeType = null,
            soruMetni = null
        )
    }
}
