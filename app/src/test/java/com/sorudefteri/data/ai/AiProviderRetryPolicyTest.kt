package com.sorudefteri.data.ai

import com.sorudefteri.domain.model.AiErrorType
import com.sorudefteri.domain.model.AiException
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InterruptedIOException
import java.net.SocketTimeoutException

/**
 * Fix 1 & 2 doğrulama testleri:
 * - QUOTA_EXCEEDED asla retry değil (kota yakar)
 * - CancellationException asla retry değil
 * - InterruptedIOException retry doğru (true)
 * - 404 MODEL_UNAVAILABLE retry false, diğer MODEL_UNAVAILABLE (5xx) retry true
 * - Sadece NETWORK_ERROR/TIMEOUT + 5xx retry true, 429 false
 * - Jitter aralığı 500..1000 olmalı (0..500 değil)
 */
class AiProviderRetryPolicyTest {

    private val gemini = GeminiProvider(mockk(relaxed = true), mockk(relaxed = true))
    private val groq = GroqProvider(mockk(relaxed = true), mockk(relaxed = true))
    private val nvidia = NvidiaProvider(mockk(relaxed = true), mockk(relaxed = true))

    private fun invokeIsRetryable(provider: Any, throwable: Throwable?): Boolean {
        val method = provider.javaClass.getDeclaredMethod("isRetryableException", Throwable::class.java)
        method.isAccessible = true
        return method.invoke(provider, throwable) as Boolean
    }

    @Test
    fun gemini_quotaExceeded_retryFalse() {
        val ex = AiException(AiErrorType.QUOTA_EXCEEDED, "API kullanım kotanız doldu (429)")
        assertFalse("QUOTA_EXCEEDED retry edilmemeli", invokeIsRetryable(gemini, ex))
    }

    @Test
    fun groq_quotaExceeded_retryFalse() {
        val ex = AiException(AiErrorType.QUOTA_EXCEEDED, "Rate limit aşıldı")
        assertFalse(invokeIsRetryable(groq, ex))
    }

    @Test
    fun nvidia_quotaExceeded_retryFalse() {
        val ex = AiException(AiErrorType.QUOTA_EXCEEDED, "429 kota doldu")
        assertFalse(invokeIsRetryable(nvidia, ex))
    }

    @Test
    fun allProviders_cancellationException_retryFalse() {
        val cancel = CancellationException("Job cancelled")
        assertFalse(invokeIsRetryable(gemini, cancel))
        assertFalse(invokeIsRetryable(groq, cancel))
        assertFalse(invokeIsRetryable(nvidia, cancel))
    }

    @Test
    fun allProviders_interruptedIOException_retryTrue() {
        val interrupted = InterruptedIOException("timeout")
        assertTrue(invokeIsRetryable(gemini, interrupted))
        assertTrue(invokeIsRetryable(groq, interrupted))
        assertTrue(invokeIsRetryable(nvidia, interrupted))
    }

    @Test
    fun allProviders_socketTimeout_retryTrue() {
        val timeout = SocketTimeoutException("timeout")
        assertTrue(invokeIsRetryable(gemini, timeout))
        assertTrue(invokeIsRetryable(groq, timeout))
        assertTrue(invokeIsRetryable(nvidia, timeout))
    }

    @Test
    fun networkError_retryTrue_quotaFalse() {
        val network = AiException(AiErrorType.NETWORK_ERROR, "ağ hatası")
        val networkTimeout = AiException(AiErrorType.NETWORK_TIMEOUT, "timeout")
        val quota = AiException(AiErrorType.QUOTA_EXCEEDED, "quota")
        for (provider in listOf(gemini, groq, nvidia)) {
            assertTrue(invokeIsRetryable(provider, network))
            assertTrue(invokeIsRetryable(provider, networkTimeout))
            assertFalse(invokeIsRetryable(provider, quota))
        }
    }

    @Test
    fun modelUnavailable_5xx_retryTrue_404_retryFalse() {
        val unavailableWith404 = AiException(AiErrorType.MODEL_UNAVAILABLE, "Model bulunamadı (HTTP 404)")
        val unavailable5xx = AiException(AiErrorType.MODEL_UNAVAILABLE, "Sunucuda kesinti (HTTP 503)")
        val unavailable500 = AiException(AiErrorType.MODEL_UNAVAILABLE, "HTTP 500")
        for (provider in listOf(gemini, groq, nvidia)) {
            assertFalse("404 asla retry edilmez", invokeIsRetryable(provider, unavailableWith404))
            assertTrue("5xx retry edilmeli (503)", invokeIsRetryable(provider, unavailable5xx))
            assertTrue("5xx retry edilmeli (500)", invokeIsRetryable(provider, unavailable500))
        }
    }

    @Test
    fun invalidApiKey_parsingFailed_unknown_retryFalse() {
        val invalid = AiException(AiErrorType.INVALID_API_KEY, "invalid")
        val parsing = AiException(AiErrorType.PARSING_FAILED, "parse")
        val unknown = AiException(AiErrorType.UNKNOWN, "unknown")
        for (provider in listOf(gemini, groq, nvidia)) {
            assertFalse(invokeIsRetryable(provider, invalid))
            assertFalse(invokeIsRetryable(provider, parsing))
            assertFalse(invokeIsRetryable(provider, unknown))
        }
    }

    @Test
    fun generic429Message_retryFalseOnly5xxRetryTrue() {
        // Generic throwable with 429 string should NOT retry (fix: quota false, only 5xx)
        val rateLimitMsg = RuntimeException("HTTP 429 rate limit exceeded")
        val msg500 = RuntimeException("HTTP 500 Internal Server Error")
        val msg502 = RuntimeException("502 Bad Gateway")
        val msg503 = RuntimeException("503 Service Unavailable")
        val msg404 = RuntimeException("404 Not Found")
        for (provider in listOf(gemini, groq, nvidia)) {
            assertFalse("429 generic mesaj retry false olmalı", invokeIsRetryable(provider, rateLimitMsg))
            assertFalse("404 generic retry false", invokeIsRetryable(provider, msg404))
            assertTrue("500 generic retry true", invokeIsRetryable(provider, msg500))
            assertTrue("502 generic retry true", invokeIsRetryable(provider, msg502))
            assertTrue("503 generic retry true", invokeIsRetryable(provider, msg503))
        }
    }

    private fun readSource(relativeFromRoot: String): String {
        // Try absolute path first
        val abs = java.io.File("/home/lenovo/Desktop/soru_uyg/$relativeFromRoot")
        if (abs.exists()) return abs.readText()
        // Try relative to user.dir
        val rel = java.io.File(relativeFromRoot)
        if (rel.exists()) return rel.readText()
        val rel2 = java.io.File("app/$relativeFromRoot")
        if (rel2.exists()) return rel2.readText()
        val rel3 = java.io.File("src/main/java/com/sorudefteri/data/ai/${relativeFromRoot.substringAfterLast("/")}")
        if (rel3.exists()) return rel3.readText()
        // last resort search upward
        var dir: java.io.File? = java.io.File(System.getProperty("user.dir") ?: ".")
        repeat(5) {
            val currentDir = dir ?: return@repeat
            val cand = java.io.File(currentDir, relativeFromRoot)
            if (cand.exists()) return cand.readText()
            val cand2 = java.io.File(currentDir, "app/$relativeFromRoot")
            if (cand2.exists()) return cand2.readText()
            dir = currentDir.parentFile
        }
        throw AssertionError("Source not found: $relativeFromRoot in ${System.getProperty("user.dir")}")
    }

    @Test
    fun jitterRange_shouldBe500to1000() {
        val geminiSource = readSource("app/src/main/java/com/sorudefteri/data/ai/GeminiProvider.kt")
        val groqSource = readSource("app/src/main/java/com/sorudefteri/data/ai/GroqProvider.kt")
        val nvidiaSource = readSource("app/src/main/java/com/sorudefteri/data/ai/NvidiaProvider.kt")
        for (source in listOf(geminiSource, groqSource, nvidiaSource)) {
            assertTrue("jitter (500..1000) olmalı", source.contains("(500..1000)"))
            assertFalse("jitter (0..500) kalmamalı", source.contains("(0..500)"))
        }
    }

    @Test
    fun quotaShouldNotContainInIsRetryable() {
        val sources = listOf(
            readSource("app/src/main/java/com/sorudefteri/data/ai/GeminiProvider.kt"),
            readSource("app/src/main/java/com/sorudefteri/data/ai/GroqProvider.kt"),
            readSource("app/src/main/java/com/sorudefteri/data/ai/NvidiaProvider.kt")
        )
        for (src in sources) {
            val isRetryableBlock = src.substringAfter("private fun isRetryableException").substringBefore("private fun execute")
            val hasBadQuotaTrue = Regex("""\|\|\s*throwable\.errorType == AiErrorType\.QUOTA_EXCEEDED\s*($|\n)""").containsMatchIn(isRetryableBlock)
            assertFalse("QUOTA_EXCEEDED retry true olarak kalmamalı", hasBadQuotaTrue)
        }
    }

    @Test
    fun worker_retryPolicy_quotaFalse_parsingFalse_interruptedTrue() {
        val workerSource = readSource("app/src/main/java/com/sorudefteri/data/worker/AiSolutionRetryWorker.kt")
        // PARSING_FAILED should be false
        assertTrue("Worker PARSING_FAILED false olmalı", workerSource.contains("PARSING_FAILED"))
        // QUOTA_EXCEEDED should be in false list (with INVALID_API_KEY, PARSING_FAILED etc)
        // Check that QUOTA is not in true list
        val trueBlock = workerSource.substringAfter("val isRetryable = when").substringBefore("else -> false")
        // True list should contain NETWORK_ERROR, NETWORK_TIMEOUT, MODEL_UNAVAILABLE but NOT QUOTA
        assertTrue(trueBlock.contains("NETWORK_ERROR"))
        assertTrue(trueBlock.contains("NETWORK_TIMEOUT"))
        assertTrue(trueBlock.contains("MODEL_UNAVAILABLE"))
        // QUOTA should not be in true list separate line; check trueBlock before false list
        // Extract true branch: between "AiErrorType.NETWORK_ERROR" and "-> true"
        val trueBranch = trueBlock.substringBefore("-> true").substringAfter("throwable is AiException")
        // In true branch, QUOTA should not appear
        assertFalse("Worker QUOTA_EXCEEDED true listesinde olmamalı", trueBranch.contains("QUOTA_EXCEEDED"))
        // InterruptedIOException should be handled
        assertTrue("Worker InterruptedIOException eklenmeli", workerSource.contains("InterruptedIOException"))
    }

    @Test
    fun appModule_okHttpConfig_retryTrue_callTimeout120() {
        val appModuleSource = readSource("app/src/main/java/com/sorudefteri/di/AppModule.kt")
        assertTrue("retryOnConnectionFailure(true) olmalı", appModuleSource.contains("retryOnConnectionFailure(true)"))
        assertTrue("callTimeout en az 120 olmalı", appModuleSource.contains("callTimeout(240") || appModuleSource.contains("callTimeout(120"))
        assertTrue("readTimeout en az 90 olmalı", appModuleSource.contains("readTimeout(180") || appModuleSource.contains("readTimeout(90"))
    }

    @Test
    fun aiRepository_5mbCheck_beforeReadBytes() {
        val repoSource = readSource("app/src/main/java/com/sorudefteri/data/ai/AiRepository.kt")
        // File.length() check öncesi
        assertTrue("File.length() kontrolü olmalı", repoSource.contains("file.length()"))
        assertTrue("5 * 1024 * 1024 check olmalı", repoSource.contains("5 * 1024 * 1024"))
        // Ensure file.length check appears before readBytes
        val idxLength = repoSource.indexOf("file.length()")
        val idxReadBytes = repoSource.indexOf("file.readBytes()")
        assertTrue("File.length() readBytes öncesi olmalı", idxLength in 0 until idxReadBytes)
    }
}
