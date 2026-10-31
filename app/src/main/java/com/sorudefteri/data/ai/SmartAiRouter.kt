package com.sorudefteri.data.ai

import android.util.Log
import com.sorudefteri.data.images.GorselDepo
import com.sorudefteri.data.local.AyarlarDataStore
import com.sorudefteri.data.local.entity.SoruChatMessageEntity
import com.sorudefteri.domain.model.AiErrorType
import com.sorudefteri.domain.model.AiException
import com.sorudefteri.domain.model.AiProviderType
import com.sorudefteri.domain.model.AiTaskRoute
import com.sorudefteri.domain.model.AiTaskType
import kotlinx.coroutines.CancellationException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Otomatik hata kurtarma (Failover/Fallback) ve sağlayıcı yönlendirme servisi.
 *
 * Strateji:
 * 1. Birincil sağlayıcıyı (Gemini veya kullanıcının seçtiği sağlayıcı) dener.
 * 2. İstek sınır aşımı (429), kota bitimi, 5xx sunucu hatası veya zaman aşımı durumunda
 *    otomatik olarak ikincil sağlayıcıya (Groq Llama-3.2-90B Vision) geçer.
 * 3. İkincil sağlayıcı da benzer hatayla başarısız olursa üçüncül olarak Nvidia NIM servisine geçer.
 * 4. Başarılı kurtarmayı şeffaf şekilde günlüğe (Log) kaydeder ve temiz Result döndürür.
 */
@Singleton
open class SmartAiRouter @Inject constructor(
    private val geminiProvider: GeminiProvider,
    private val groqProvider: GroqProvider,
    private val nvidiaProvider: NvidiaProvider,
    private val ayarlarDataStore: AyarlarDataStore,
    private val gorselDepo: GorselDepo,
    private val promptManager: AiPromptManager
) {
    companion object {
        private const val TAG = "SmartAiRouter"
        const val FALLBACK_GROQ_MODEL = "qwen/qwen3.8-27b"
        const val FALLBACK_NVIDIA_MODEL = "meta/llama-3.2-90b-vision-instruct"
        const val FALLBACK_GEMINI_MODEL = "gemini-3.7-flash"
    }

    /**
     * Hatanın otomatik fallback gerektiren geçici bir sorun (429 kota, 5xx sunucu hatası, zaman aşımı,
     * model hatası veya 400/404) olup olmadığını belirler.
     */
    fun isFallbackEligible(throwable: Throwable?): Boolean {
        if (throwable == null) return false
        if (throwable is CancellationException) return false
        val msg = throwable.message.orEmpty().lowercase()
        val isModelOrClientError = msg.contains("model") ||
                msg.contains("decommissioned") ||
                msg.contains("not found") ||
                msg.contains("404") ||
                msg.contains("400")
        if (throwable is AiException) {
            if (throwable.errorType == AiErrorType.QUOTA_EXCEEDED) return true
            if (throwable.errorType == AiErrorType.NETWORK_TIMEOUT) return true
            if (throwable.errorType == AiErrorType.MODEL_UNAVAILABLE) return true
            if (isModelOrClientError) return true
            if (msg.contains("429") || msg.contains("500") || msg.contains("502") ||
                msg.contains("503") || msg.contains("504") || msg.contains("timeout") ||
                msg.contains("zaman aşımı") || msg.contains("kota") || msg.contains("rate limit")
            ) {
                return true
            }
        }
        return isModelOrClientError ||
                msg.contains("429") ||
                msg.contains("500") ||
                msg.contains("502") ||
                msg.contains("503") ||
                msg.contains("504") ||
                msg.contains("timeout") ||
                msg.contains("zaman aşımı") ||
                msg.contains("kota") ||
                msg.contains("rate limit") ||
                throwable is SocketTimeoutException ||
                throwable is InterruptedIOException
    }

    internal data class ProviderTarget(
        val provider: AiProviderType,
        val apiKey: String,
        val modelName: String,
        val isFallback: Boolean
    )

    private suspend fun apiKeyFor(provider: AiProviderType): String = when (provider) {
        AiProviderType.GEMINI -> ayarlarDataStore.getGeminiApiKey()
        AiProviderType.GROQ -> ayarlarDataStore.getGroqApiKey()
        AiProviderType.NVIDIA -> ayarlarDataStore.getNvidiaApiKey()
    }.trim()

    /**
     * Görev için sağlayıcı zincirini oluşturur.
     * Görev route'u tanımlı ve o sağlayıcının API anahtarı doluysa zincirin başına
     * route sağlayıcı + modeli konur; ardından mevcut sıralama (fallback olarak) gelir.
     */
    internal suspend fun buildProviderChain(task: AiTaskType = AiTaskType.COZUM): List<ProviderTarget> {
        val primary = ayarlarDataStore.getAiProvider()
        val order = when (primary) {
            AiProviderType.GEMINI -> listOf(AiProviderType.GEMINI, AiProviderType.GROQ, AiProviderType.NVIDIA)
            AiProviderType.GROQ -> listOf(AiProviderType.GROQ, AiProviderType.NVIDIA, AiProviderType.GEMINI)
            AiProviderType.NVIDIA -> listOf(AiProviderType.NVIDIA, AiProviderType.GROQ, AiProviderType.GEMINI)
        }

        val defaultChain = mutableListOf<ProviderTarget>()
        for ((index, prov) in order.withIndex()) {
            val isFallback = index > 0
            val key = apiKeyFor(prov)
            val model = when (prov) {
                AiProviderType.GEMINI -> if (isFallback) FALLBACK_GEMINI_MODEL
                    else ayarlarDataStore.getGeminiModel().trim().ifBlank { FALLBACK_GEMINI_MODEL }
                AiProviderType.GROQ -> if (isFallback) FALLBACK_GROQ_MODEL
                    else ayarlarDataStore.getGroqModel().trim().ifBlank { FALLBACK_GROQ_MODEL }
                AiProviderType.NVIDIA -> if (isFallback) FALLBACK_NVIDIA_MODEL
                    else ayarlarDataStore.getNvidiaModel().trim().ifBlank { FALLBACK_NVIDIA_MODEL }
            }
            defaultChain.add(ProviderTarget(prov, key, model, isFallback))
        }

        val route: AiTaskRoute? = try {
            ayarlarDataStore.getTaskRoute(task)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        if (route != null) {
            val routeKey = apiKeyFor(route.provider)
            if (routeKey.isNotBlank()) {
                val routeModel = route.model.trim().ifBlank { route.provider.defaultModel }
                val head = ProviderTarget(route.provider, routeKey, routeModel, isFallback = false)
                val rest = defaultChain
                    .filterNot { it.provider == head.provider && it.modelName == head.modelName }
                    .map { it.copy(isFallback = true) }
                return listOf(head) + rest
            } else {
                Log.i(TAG, "SmartAiRouter: ${task.name} route'u (${route.provider.name}) API anahtarı boş, varsayılan zincir kullanılıyor.")
            }
        }
        return defaultChain
    }

    /**
     * Generic görev yönlendirici: görev route'u + fallback zinciri üzerinde [block]'u dener.
     * Birincil hedefin anahtarı boşsa INVALID_API_KEY; fallback'lerde boş anahtar atlanır.
     * Başarısızlık [isFallbackEligible] ise sıradaki sağlayıcıya geçer.
     */
    open suspend fun <T> routeTask(
        task: AiTaskType,
        block: suspend (provider: AiProviderType, apiKey: String, model: String) -> Result<T>
    ): Result<T> {
        val chain = buildProviderChain(task)
        var lastError: Throwable? = null
        for (target in chain) {
            if (target.apiKey.isBlank()) {
                if (!target.isFallback) {
                    return Result.failure(
                        AiException(
                            AiErrorType.INVALID_API_KEY,
                            "API anahtarı bulunamadı. Lütfen Ayarlar sayfasından ${target.provider.name} API anahtarınızı girin."
                        )
                    )
                }
                continue
            }
            Log.d(TAG, "SmartAiRouter: ${task.name} deneniyor -> ${target.provider.name} (model: ${target.modelName}, fallback: ${target.isFallback})")
            val result = try {
                block(target.provider, target.apiKey, target.modelName)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
            if (result.isSuccess) return result
            val exception = result.exceptionOrNull()
            lastError = exception
            if (isFallbackEligible(exception)) {
                Log.w(TAG, "SmartAiRouter: ${task.name} ${target.provider.name} hatası (${exception?.localizedMessage}), fallback deneniyor...")
                continue
            }
            return result
        }
        return Result.failure(lastError ?: AiException(AiErrorType.UNKNOWN, "Tüm AI sağlayıcıları başarısız oldu."))
    }

    /**
     * Soru çözümünü birincil sağlayıcı ile dener; kota, 5xx veya zaman aşımı durumunda
     * sırasıyla ikincil (Groq Llama-3.2-90B Vision) ve üçüncül (Nvidia NIM) sağlayıcılara otomatik aktarır.
     */
    open suspend fun routeSolution(
        imageBytes: ByteArray?,
        dersAdi: String,
        konu: String,
        correctAnswer: String?,
        mistakeType: String?,
        soruMetni: String?,
        customPromptTemplate: String? = null
    ): Result<String> {
        val chain = buildProviderChain(AiTaskType.COZUM)
        var lastError: Throwable? = null

        for (target in chain) {
            if (target.apiKey.isBlank()) {
                if (!target.isFallback) {
                    return Result.failure(
                        AiException(
                            AiErrorType.INVALID_API_KEY,
                            "API anahtarı bulunamadı. Lütfen Ayarlar sayfasından ${target.provider.name} API anahtarınızı girin."
                        )
                    )
                } else {
                    Log.i(TAG, "SmartAiRouter: Fallback sağlayıcı ${target.provider.name} API anahtarı boş olduğu için atlanıyor.")
                    continue
                }
            }

            Log.d(TAG, "SmartAiRouter: Çözüm deneniyor -> ${target.provider.name} (model: ${target.modelName}, fallback: ${target.isFallback})")

            val result = when (target.provider) {
                AiProviderType.GEMINI -> geminiProvider.cozumIsteDetayli(
                    imageBytes = imageBytes,
                    dersAdi = dersAdi,
                    konu = konu,
                    correctAnswer = correctAnswer,
                    mistakeType = mistakeType,
                    soruMetni = soruMetni,
                    modelName = target.modelName,
                    apiKey = target.apiKey,
                    customPromptTemplate = customPromptTemplate
                )
                AiProviderType.GROQ -> groqProvider.cozumIsteDetayli(
                    imageBytes = imageBytes,
                    dersAdi = dersAdi,
                    konu = konu,
                    correctAnswer = correctAnswer,
                    mistakeType = mistakeType,
                    soruMetni = soruMetni,
                    modelName = target.modelName,
                    apiKey = target.apiKey,
                    customPromptTemplate = customPromptTemplate
                )
                AiProviderType.NVIDIA -> nvidiaProvider.cozumIsteDetayli(
                    imageBytes = imageBytes,
                    dersAdi = dersAdi,
                    konu = konu,
                    correctAnswer = correctAnswer,
                    mistakeType = mistakeType,
                    soruMetni = soruMetni,
                    modelName = target.modelName,
                    apiKey = target.apiKey,
                    customPromptTemplate = customPromptTemplate
                )
            }

            if (result.isSuccess) {
                if (target.isFallback) {
                    Log.i(TAG, "SmartAiRouter: Başarıyla ${target.provider.name} (${target.modelName}) üzerinden kurtarıldı.")
                }
                return result
            }

            val exception = result.exceptionOrNull()
            lastError = exception

            if (isFallbackEligible(exception)) {
                Log.w(TAG, "SmartAiRouter: ${target.provider.name} hatası (${exception?.localizedMessage}), sonraki fallback deneniyor...")
                continue
            } else {
                return result
            }
        }

        return Result.failure(lastError ?: AiException(AiErrorType.UNKNOWN, "Tüm AI sağlayıcıları başarısız oldu."))
    }

    /**
     * Sokratik koç sohbet mesajını otomatik fallback desteği ile gönderir.
     */
    open suspend fun routeChat(
        imageBytes: ByteArray?,
        dersAdi: String,
        konu: String,
        correctAnswer: String?,
        soruMetni: String?,
        chatHistory: List<SoruChatMessageEntity>,
        userMessage: String
    ): Result<String> {
        val prompt = promptManager.buildSokratikChatPrompt(
            dersAdi = dersAdi,
            konu = konu,
            soruMetni = soruMetni,
            correctAnswer = correctAnswer,
            chatHistory = chatHistory,
            userMessage = userMessage
        )

        val chain = buildProviderChain(AiTaskType.CHAT)
        var lastError: Throwable? = null

        for (target in chain) {
            if (target.apiKey.isBlank()) {
                if (!target.isFallback) {
                    return Result.failure(
                        AiException(
                            AiErrorType.INVALID_API_KEY,
                            "API anahtarı bulunamadı. Lütfen Ayarlar sayfasından ${target.provider.name} API anahtarınızı girin."
                        )
                    )
                } else {
                    Log.i(TAG, "SmartAiRouter: Fallback sağlayıcı ${target.provider.name} API anahtarı boş, atlanıyor.")
                    continue
                }
            }

            Log.d(TAG, "SmartAiRouter: Chat deneniyor -> ${target.provider.name} (model: ${target.modelName}, fallback: ${target.isFallback})")

            val result = when (target.provider) {
                AiProviderType.GEMINI -> geminiProvider.chatIste(
                    imageBytes = imageBytes,
                    prompt = prompt,
                    modelName = target.modelName,
                    apiKey = target.apiKey
                )
                AiProviderType.GROQ -> groqProvider.chatIste(
                    imageBytes = imageBytes,
                    prompt = prompt,
                    modelName = target.modelName,
                    apiKey = target.apiKey
                )
                AiProviderType.NVIDIA -> nvidiaProvider.chatIste(
                    imageBytes = imageBytes,
                    prompt = prompt,
                    modelName = target.modelName,
                    apiKey = target.apiKey
                )
            }

            if (result.isSuccess) {
                if (target.isFallback) {
                    Log.i(TAG, "SmartAiRouter: Chat başarıyla ${target.provider.name} (${target.modelName}) üzerinden kurtarıldı.")
                }
                return result
            }

            val exception = result.exceptionOrNull()
            lastError = exception

            if (isFallbackEligible(exception)) {
                Log.w(TAG, "SmartAiRouter: Chat ${target.provider.name} hatası (${exception?.localizedMessage}), fallback deneniyor...")
                continue
            } else {
                return result
            }
        }

        return Result.failure(lastError ?: AiException(AiErrorType.UNKNOWN, "Tüm AI sağlayıcıları ile sohbet başarısız oldu."))
    }
}
