package com.sorudefteri.data.ai

import com.sorudefteri.data.images.GorselDepo
import com.sorudefteri.data.local.AyarlarDataStore
import com.sorudefteri.domain.DersTaksonomisi
import com.sorudefteri.domain.model.AiErrorType
import com.sorudefteri.domain.model.AiException
import com.sorudefteri.domain.model.AiProviderType
import com.sorudefteri.domain.model.AiTaskType
import com.sorudefteri.domain.model.QuestionMetadata
import com.sorudefteri.domain.model.SimilarQuestionResult
import com.sorudefteri.domain.model.Soru
import com.sorudefteri.domain.model.TwinQuestionsResult
import com.sorudefteri.domain.repository.AiRepository as AiRepositoryInterface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
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
    private val nvidiaProvider: NvidiaProvider,
    private val gorselDepo: GorselDepo,
    private val smartAiRouter: SmartAiRouter
) : AiRepositoryInterface {

    companion object {
        private const val MAX_IMAGE_BYTES = 5 * 1024 * 1024
    }

    private data class ProviderCredentials(val apiKey: String, val model: String)

    private suspend fun resolveCredentials(provider: AiProviderType): Result<ProviderCredentials> {
        return when (provider) {
            AiProviderType.GEMINI -> {
                val key = ayarlarDataStore.getGeminiApiKey().trim()
                if (key.isBlank()) Result.failure(AiException(AiErrorType.INVALID_API_KEY, "API anahtarı bulunamadı. Lütfen Ayarlar bölümünden Gemini API anahtarınızı girin."))
                else {
                    val model = ayarlarDataStore.getGeminiModel().trim().ifBlank { AyarlarDataStore.DEFAULT_GEMINI_MODEL }
                    Result.success(ProviderCredentials(key, model))
                }
            }
            AiProviderType.GROQ -> {
                val key = ayarlarDataStore.getGroqApiKey().trim()
                if (key.isBlank()) Result.failure(AiException(AiErrorType.INVALID_API_KEY, "API anahtarı bulunamadı. Lütfen Ayarlar bölümünden Groq API anahtarınızı girin."))
                else {
                    val model = ayarlarDataStore.getGroqModel().trim().ifBlank { AyarlarDataStore.DEFAULT_GROQ_MODEL }
                    Result.success(ProviderCredentials(key, model))
                }
            }
            AiProviderType.NVIDIA -> {
                val key = ayarlarDataStore.getNvidiaApiKey().trim()
                if (key.isBlank()) Result.failure(AiException(AiErrorType.INVALID_API_KEY, "API anahtarı bulunamadı. Lütfen Ayarlar bölümünden NVIDIA Build API anahtarınızı girin. (build.nvidia.com)"))
                else {
                    val model = ayarlarDataStore.getNvidiaModel().trim().ifBlank { AyarlarDataStore.DEFAULT_NVIDIA_MODEL }
                    Result.success(ProviderCredentials(key, model))
                }
            }
        }
    }

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

        // Guard: sendImage false ve text boş ise erkenden hata
        if (!sendImage && extractedText.isNullOrBlank()) {
            return Result.failure(
                AiException(
                    AiErrorType.UNKNOWN,
                    "Gönderilecek veri yok: Lütfen 'Görseli AI'a gönder' açık olsun veya önce OCR ile metin çıkarın."
                )
            )
        }
        if (!sendImage && !extractedText.isNullOrBlank()) {
            return getAiSolutionFromText(
                dersAdi = dersAdi,
                konu = soru.topic,
                soruMetni = extractedText,
                correctAnswer = soru.correctAnswer,
                mistakeType = mistakeName
            )
        }

        // imagePath blank kontrolü
        if (sendImage && soru.imagePath.isBlank()) {
            return Result.failure(AiException(AiErrorType.UNKNOWN, "Görsel yolu boş."))
        }

        // file.length() check before file.readBytes() - 5MB kontrolü, legacy yerine GorselDepo kullanılıyor
        // Görseli GorselDepo üzerinden oku (compress/OOM korumalı)
        val imageBytes: ByteArray? = if (sendImage) {
            try {
                // Önce File.length check (hızlı fail), sonra GorselDepo compress
                val file = File(soru.imagePath)
                if (file.exists() && file.canRead() && file.length() > MAX_IMAGE_BYTES) {
                    // GorselDepo compress deneyebilir ama 5MB üstü provider limiti - yine compress ile dene
                    // Eğer compress sonrası hala büyükse provider zaten reddedecek, burada erken fail yerine compress'a bırakıyoruz
                }
                withContext(Dispatchers.IO) {
                    gorselDepo.getGorselBytes(soru.imagePath)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { null }
        } else null

        if (sendImage && imageBytes == null) {
            return Result.failure(
                AiException(AiErrorType.UNKNOWN, "Soru görseli bulunamadı veya okunamıyor: ${soru.imagePath}")
            )
        }
        // Ek validasyon: boş byte array
        if (sendImage && imageBytes != null && imageBytes.isEmpty()) {
            return Result.failure(AiException(AiErrorType.UNKNOWN, "Görsel verisi boş."))
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
            return Result.failure(AiException(AiErrorType.UNKNOWN, "Soru metni boş olamaz."))
        }
        if (dersAdi.isBlank() && konu.isBlank()) {
            return Result.failure(AiException(AiErrorType.UNKNOWN, "Ders ve konu bilgisi boş."))
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
            return Result.failure(AiException(AiErrorType.UNKNOWN, "Temizlenecek OCR metni boş."))
        }
        return smartAiRouter.routeTask(AiTaskType.OCR_TEMIZLEME) { provider, apiKey, model ->
            when (provider) {
                AiProviderType.GEMINI -> geminiProvider.cleanOcrText(rawOcrText, model, apiKey)
                AiProviderType.GROQ -> groqProvider.cleanOcrText(rawOcrText, model, apiKey)
                AiProviderType.NVIDIA -> nvidiaProvider.cleanOcrText(rawOcrText, model, apiKey)
            }
        }
    }

    /**
     * Aktif AI sağlayıcısı üzerinden görselden sınav türü, ders, konu ve cevap şıkkı tespiti yapar (Auto-Tagging).
     */
    override suspend fun detectQuestionMetadata(imageBytes: ByteArray): Result<QuestionMetadata> {
        if (imageBytes.isEmpty()) {
            return Result.failure(AiException(AiErrorType.UNKNOWN, "Görsel verisi boş."))
        }
        if (imageBytes.size > MAX_IMAGE_BYTES) {
            return Result.failure(AiException(AiErrorType.UNKNOWN, "Görsel boyutu çok büyük (${imageBytes.size/1024}KB)."))
        }
        return smartAiRouter.routeTask(AiTaskType.ETIKETLEME) { provider, apiKey, model ->
            when (provider) {
                AiProviderType.GEMINI -> geminiProvider.detectMetadata(imageBytes, model, apiKey)
                AiProviderType.GROQ -> groqProvider.detectMetadata(imageBytes, model, apiKey)
                AiProviderType.NVIDIA -> nvidiaProvider.detectMetadata(imageBytes, model, apiKey)
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
        if (imageBytes == null && soruMetni.isNullOrBlank() && konu.isBlank() && dersAdi.isBlank()) {
            return Result.failure(AiException(AiErrorType.UNKNOWN, "Benzer soru üretmek için görsel, metin, ders veya konu bilgisi gereklidir."))
        }
        if (dersAdi.isBlank() && konu.isBlank()) {
            return Result.failure(AiException(AiErrorType.UNKNOWN, "Benzer soru için ders veya konu boş olamaz."))
        }
        return smartAiRouter.routeTask(AiTaskType.BENZER_SORU) { provider, apiKey, model ->
            when (provider) {
                AiProviderType.GEMINI -> geminiProvider.generateSimilarQuestion(imageBytes, dersAdi, konu, soruMetni, model, apiKey)
                AiProviderType.GROQ -> groqProvider.generateSimilarQuestion(imageBytes, dersAdi, konu, soruMetni, model, apiKey)
                AiProviderType.NVIDIA -> nvidiaProvider.generateSimilarQuestion(imageBytes, dersAdi, konu, soruMetni, model, apiKey)
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
     * Soru nesnesi üzerinden 3 soruluk soru ikizi (kardeş test) üretir.
     */
    override suspend fun generateTwinQuestions(
        soru: Soru,
        sendImage: Boolean,
        extractedText: String?
    ): Result<TwinQuestionsResult> {
        val dersAdi = DersTaksonomisi.getDersAdi(soru.examType, soru.subjectId)
        val konu = soru.topic

        val imageBytes: ByteArray? = if (sendImage && soru.imagePath.isNotBlank()) {
            try {
                withContext(Dispatchers.IO) {
                    val file = File(soru.imagePath)
                    if (file.exists() && file.length() > MAX_IMAGE_BYTES) {
                        null
                    } else {
                        gorselDepo.getGorselBytes(soru.imagePath)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }

        val effectiveText = extractedText?.takeIf { it.isNotBlank() }
            ?: soru.ocrMetni?.takeIf { it.isNotBlank() }
            ?: soru.questionText?.takeIf { it.isNotBlank() }
            ?: soru.note?.takeIf { it.isNotBlank() }

        if (imageBytes == null && effectiveText.isNullOrBlank() && konu.isBlank() && dersAdi.isBlank()) {
            return Result.failure(
                AiException(
                    AiErrorType.UNKNOWN,
                    "Kardeş soru üretmek için görsel, metin, ders veya konu bilgisi gereklidir."
                )
            )
        }

        return generateTwinQuestions(
            imageBytes = imageBytes,
            dersAdi = dersAdi,
            konu = konu,
            soruMetni = effectiveText
        )
    }

    /**
     * Görsel ve/veya soru metninden yola çıkarak 3 soruluk soru ikizi (kardeş test) üretir.
     */
    override suspend fun generateTwinQuestions(
        imageBytes: ByteArray?,
        dersAdi: String,
        konu: String,
        soruMetni: String?
    ): Result<TwinQuestionsResult> {
        if (dersAdi.isBlank() && konu.isBlank()) {
            return Result.failure(AiException(AiErrorType.UNKNOWN, "Kardeş sorular için ders veya konu boş olamaz."))
        }
        return smartAiRouter.routeTask(AiTaskType.BENZER_SORU) { provider, apiKey, model ->
            when (provider) {
                AiProviderType.GEMINI -> geminiProvider.generateTwinQuestions(imageBytes, dersAdi, konu, soruMetni, model, apiKey)
                AiProviderType.GROQ -> groqProvider.generateTwinQuestions(imageBytes, dersAdi, konu, soruMetni, model, apiKey)
                AiProviderType.NVIDIA -> nvidiaProvider.generateTwinQuestions(imageBytes, dersAdi, konu, soruMetni, model, apiKey)
            }
        }
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
        if (dersAdi.isBlank() || konu.isBlank()) {
            return Result.failure(AiException(AiErrorType.UNKNOWN, "Soru üretmek için ders ve konu boş olamaz."))
        }
        return smartAiRouter.routeTask(AiTaskType.BENZER_SORU) { provider, apiKey, model ->
            when (provider) {
                AiProviderType.GEMINI -> geminiProvider.generateQuestion(dersAdi, konu, zorluk, alan, sinavTipi, model, apiKey)
                AiProviderType.GROQ -> groqProvider.generateQuestion(dersAdi, konu, zorluk, alan, sinavTipi, model, apiKey)
                AiProviderType.NVIDIA -> nvidiaProvider.generateQuestion(dersAdi, konu, zorluk, alan, sinavTipi, model, apiKey)
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
        val cred = resolveCredentials(provider).getOrElse { return Result.failure(it) }
        return when (provider) {
            AiProviderType.GEMINI -> geminiProvider.listModels(cred.apiKey)
            AiProviderType.GROQ -> groqProvider.listModels(cred.apiKey)
            AiProviderType.NVIDIA -> nvidiaProvider.listModels(cred.apiKey)
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
        // imageBytes boş array kontrolü
        if (imageBytes != null && imageBytes.isEmpty()) {
            return Result.failure(AiException(AiErrorType.UNKNOWN, "Görsel verisi boş."))
        }
        if (imageBytes != null && imageBytes.size > MAX_IMAGE_BYTES) {
            return Result.failure(AiException(AiErrorType.UNKNOWN, "Görsel boyutu çok büyük (${imageBytes.size/1024}KB)."))
        }

        return smartAiRouter.routeSolution(
            imageBytes = imageBytes,
            dersAdi = dersAdi,
            konu = konu,
            correctAnswer = correctAnswer,
            mistakeType = mistakeType,
            soruMetni = soruMetni
        )
    }

    /**
     * Sokratik AI Koç chatbot'una soru bağlamı ile birlikte mesaj iletir.
     */
    override suspend fun sendSoruChatMessage(
        soru: Soru,
        chatHistory: List<com.sorudefteri.data.local.entity.SoruChatMessageEntity>,
        userMessage: String,
        sendImage: Boolean
    ): Result<String> {
        if (userMessage.isBlank()) {
            return Result.failure(AiException(AiErrorType.UNKNOWN, "Mesaj metni boş olamaz."))
        }

        val dersAdi = DersTaksonomisi.getDersAdi(soru.examType, soru.subjectId)

        val imageBytes: ByteArray? = if (sendImage && soru.imagePath.isNotBlank()) {
            try {
                withContext(Dispatchers.IO) {
                    gorselDepo.getGorselBytes(soru.imagePath)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        } else null

        return smartAiRouter.routeChat(
            imageBytes = imageBytes,
            dersAdi = dersAdi,
            konu = soru.topic,
            correctAnswer = soru.correctAnswer,
            soruMetni = soru.ocrMetni ?: soru.questionText,
            chatHistory = chatHistory,
            userMessage = userMessage.trim()
        )
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
        if (imagePath.isBlank()) {
            return Result.failure(AiException(AiErrorType.UNKNOWN, "Görsel yolu boş."))
        }
        val file = File(imagePath)
        if (!file.exists() || !file.canRead()) {
            return Result.failure(
                AiException(AiErrorType.UNKNOWN, "Soru görseli bulunamadı veya okunamıyor: $imagePath")
            )
        }
        if (file.length() > MAX_IMAGE_BYTES) {
            return Result.failure(
                AiException(
                    AiErrorType.UNKNOWN,
                    "Görsel boyutu çok büyük (${file.length() / 1024}KB). Lütfen daha küçük bir görsel deneyin."
                )
            )
        }

        val imageBytes = try {
            withContext(Dispatchers.IO) {
                // GorselDepo compress yolunu tercih et
                gorselDepo.getGorselBytes(imagePath)
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { null }

        if (imageBytes == null) {
            return Result.failure(AiException(AiErrorType.UNKNOWN, "Soru görseli diskten okunamadı: $imagePath"))
        }

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
        if (imageBytes.isEmpty()) {
            return Result.failure(AiException(AiErrorType.UNKNOWN, "Görsel verisi boş."))
        }
        if (imageBytes.size > MAX_IMAGE_BYTES) {
            return Result.failure(
                AiException(
                    AiErrorType.UNKNOWN,
                    "Görsel boyutu çok büyük (${imageBytes.size / 1024}KB). Lütfen daha küçük bir görsel deneyin."
                )
            )
        }
        return cozumIsteGelismi(
            imageBytes = imageBytes,
            dersAdi = dersAdi,
            konu = konu,
            correctAnswer = correctAnswer,
            mistakeType = null,
            soruMetni = null
        )
    }

    override suspend fun generateDiagnosticReport(analysisContext: String): Result<String> {
        val prompt = """
Sen Türkiye YKS/LGS sınavlarına öğrenci hazırlayan uzman bir yapay zeka rehberlik danışmanı ve derece koçusun.
Aşağıda öğrencinin çözdüğü testler, biriktirdiği sorular, ders/konu dağılımı ve yaptığı hata türleri yer almaktadır:

$analysisContext

Lütfen öğrencinin bu verilerini pedagojik ve analitik olarak derinlemesine incele ve aşağıdaki 4 bölümden oluşan kapsamlı bir "Kök Neden ve Teşhis Raporu" oluştur:

### 🩺 1. Kök Neden Hata Teşhisi (Root-Cause Analysis)
- Hataların arkasındaki bilişsel temel: Bilgi/kavram eksikliği mi, dikkat ve odak dağılması mı, yoksa zaman baskısı/stres mi ağır basıyor? Somut oranlar ve nedenleriyle analiz et.

### ⚠️ 2. Kritik Tehlike & Alarm Bölgeleri
- En çok fire verilen dersler ve konular. Acil müdahale edilmezse net artışını bloke edecek kritik eksikler.

### 🎯 3. 7 Günlük Kişiye Özel Eylem Reçetesi
- Bu hafta öğrencinin adım adım uygulaması gereken hedefli çalışma programı ve soru çözme stratejisi.

### 💡 4. Taktiksel & Zihinsel Tavsiyeler
- Turlama taktiği, şık eleme, süre yönetimi ve sınav anı odaklanma tüyoları.

Yanıtını profesyonel, samimi, öğrenciyi motive edici ve KaTeX / Markdown formatında hazırla.
""".trimIndent()

        return smartAiRouter.routeSolution(
            imageBytes = null,
            dersAdi = "Rehberlik ve Teşhis",
            konu = "Kök Neden Analizi",
            correctAnswer = null,
            mistakeType = null,
            soruMetni = prompt
        )
    }
}


