package com.sorudefteri.data.ai

import com.sorudefteri.data.local.AyarlarDataStore
import com.sorudefteri.domain.model.AiProviderType
import com.sorudefteri.domain.DersTaksonomisi
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
 * soru görselini ilgili sağlayıcıya iletir ve adım adım çözümü döndürür.
 */
@Singleton
class AiRepository @Inject constructor(
    private val ayarlarDataStore: AyarlarDataStore,
    private val geminiProvider: GeminiProvider,
    private val groqProvider: GroqProvider
) : AiRepositoryInterface {

    /**
     * Domain Soru nesnesi üzerinden AI çözümü üretir.
     */
    override suspend fun getAiSolution(soru: Soru): Result<String> {
        val dersAdi = DersTaksonomisi.getDersAdi(soru.subjectId)
        return cozumIste(
            imagePath = soru.imagePath,
            dersAdi = dersAdi,
            konu = soru.topic,
            correctAnswer = soru.correctAnswer
        )
    }

    /**
     * Aktif AI sağlayıcısı üzerinden görselden sınav türü, ders, konu ve cevap şıkkı tespiti yapar (Auto-Tagging).
     */
    override suspend fun detectQuestionMetadata(imageBytes: ByteArray): Result<com.sorudefteri.domain.model.QuestionMetadata> {
        val providerType = ayarlarDataStore.getAiProvider()
        return when (providerType) {
            AiProviderType.GEMINI -> {
                val apiKey = ayarlarDataStore.getGeminiApiKey().trim()
                if (apiKey.isBlank()) return Result.failure(IllegalStateException("Gemini API anahtarı girilmemiş."))
                val model = ayarlarDataStore.getGeminiModel().ifBlank { AyarlarDataStore.DEFAULT_GEMINI_MODEL }
                geminiProvider.detectMetadata(imageBytes, model, apiKey)
            }
            AiProviderType.GROQ -> {
                val apiKey = ayarlarDataStore.getGroqApiKey().trim()
                if (apiKey.isBlank()) return Result.failure(IllegalStateException("Groq API anahtarı girilmemiş."))
                val model = ayarlarDataStore.getGroqModel().ifBlank { AyarlarDataStore.DEFAULT_GROQ_MODEL }
                groqProvider.detectMetadata(imageBytes, model, apiKey)
            }
        }
    }

    /**
     * Mevcut soru görselinden ve kazanımlarından yola çıkarak birebir benzer yeni bir soru türetir (Question Cloner).
     */
    override suspend fun generateSimilarQuestion(
        imageBytes: ByteArray,
        dersAdi: String,
        konu: String
    ): Result<com.sorudefteri.domain.model.SimilarQuestionResult> {
        val providerType = ayarlarDataStore.getAiProvider()
        return when (providerType) {
            AiProviderType.GEMINI -> {
                val apiKey = ayarlarDataStore.getGeminiApiKey().trim()
                if (apiKey.isBlank()) return Result.failure(IllegalStateException("Gemini API anahtarı girilmemiş."))
                val model = ayarlarDataStore.getGeminiModel().ifBlank { AyarlarDataStore.DEFAULT_GEMINI_MODEL }
                geminiProvider.generateSimilarQuestion(imageBytes, dersAdi, konu, model, apiKey)
            }
            AiProviderType.GROQ -> {
                val apiKey = ayarlarDataStore.getGroqApiKey().trim()
                if (apiKey.isBlank()) return Result.failure(IllegalStateException("Groq API anahtarı girilmemiş."))
                val model = ayarlarDataStore.getGroqModel().ifBlank { AyarlarDataStore.DEFAULT_GROQ_MODEL }
                groqProvider.generateSimilarQuestion(imageBytes, dersAdi, konu, model, apiKey)
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
                    Result.failure(IllegalArgumentException("Gemini API anahtarı girilmemiş."))
                } else {
                    geminiProvider.listModels(apiKey)
                }
            }
            AiProviderType.GROQ -> {
                val apiKey = ayarlarDataStore.getGroqApiKey().trim()
                if (apiKey.isBlank()) {
                    Result.failure(IllegalArgumentException("Groq API anahtarı girilmemiş."))
                } else {
                    groqProvider.listModels(apiKey)
                }
            }
        }
    }

    /**
     * Disk üzerindeki soru görseli dosya yolundan çözüm üretir.
     *
     * @param imagePath Soru görselinin cihazdaki mutlak dosya yolu.
     * @param dersAdi Sorunun ait olduğu ders adı.
     * @param konu Sorunun ait olduğu konu başlığı.
     * @param correctAnswer Varsa doğru cevap şıkkı (örn. "A", "B", "C", "D", "E") veya null.
     * @return Başarılı ise çözüm metni [Result.success], aksi takdirde hata detayı [Result.failure].
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
     * Bellekteki görsel byte dizisinden çözüm üretir.
     *
     * @param imageBytes Soru görselinin byte dizisi.
     * @param dersAdi Sorunun ait olduğu ders adı.
     * @param konu Sorunun ait olduğu konu başlığı.
     * @param correctAnswer Varsa doğru cevap şıkkı veya null.
     * @return Başarılı ise çözüm metni [Result.success], aksi takdirde hata detayı [Result.failure].
     */
    suspend fun cozumIste(
        imageBytes: ByteArray,
        dersAdi: String,
        konu: String,
        correctAnswer: String? = null
    ): Result<String> {
        if (imageBytes.isEmpty()) {
            return Result.failure(IllegalArgumentException("Soru görseli verisi boş olamaz."))
        }

        val providerType = ayarlarDataStore.getAiProvider()

        return when (providerType) {
            AiProviderType.GEMINI -> {
                val apiKey = ayarlarDataStore.getGeminiApiKey().trim()
                if (apiKey.isBlank()) {
                    return Result.failure(
                        IllegalStateException("API anahtarı bulunamadı. Lütfen Ayarlar bölümünden API anahtarınızı girin.")
                    )
                }
                val model = ayarlarDataStore.getGeminiModel().ifBlank { AyarlarDataStore.DEFAULT_GEMINI_MODEL }
                geminiProvider.cozumIste(
                    imageBytes = imageBytes,
                    dersAdi = dersAdi,
                    konu = konu,
                    correctAnswer = correctAnswer,
                    modelName = model,
                    apiKey = apiKey
                )
            }
            AiProviderType.GROQ -> {
                val apiKey = ayarlarDataStore.getGroqApiKey().trim()
                if (apiKey.isBlank()) {
                    return Result.failure(
                        IllegalStateException("API anahtarı bulunamadı. Lütfen Ayarlar bölümünden API anahtarınızı girin.")
                    )
                }
                val model = ayarlarDataStore.getGroqModel().ifBlank { AyarlarDataStore.DEFAULT_GROQ_MODEL }
                groqProvider.cozumIste(
                    imageBytes = imageBytes,
                    dersAdi = dersAdi,
                    konu = konu,
                    correctAnswer = correctAnswer,
                    modelName = model,
                    apiKey = apiKey
                )
            }
        }
    }
}
