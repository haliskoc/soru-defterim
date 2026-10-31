package com.sorudefteri.data.ai

import com.sorudefteri.data.ai.dto.GeminiContent
import com.sorudefteri.data.ai.dto.GeminiPart
import com.sorudefteri.data.ai.dto.GeminiRequest
import com.sorudefteri.data.local.AyarlarDataStore
import com.sorudefteri.di.AppModule
import com.sorudefteri.domain.model.AiErrorType
import com.sorudefteri.domain.model.AiException
import com.sorudefteri.domain.model.AiProviderType
import io.mockk.mockk
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AiInfrastructureUpdateTest {

    @Test
    fun groqProviderType_modelConfig_qwenDefault_andNoDecommissioned() {
        val groq = AiProviderType.GROQ
        assertEquals("qwen/qwen3.8-27b", groq.defaultModel)

        val modelIds = groq.supportedModels.map { it.first }
        assertTrue("qwen/qwen3.8-27b desteklenmeli", modelIds.contains("qwen/qwen3.8-27b"))
        assertTrue("llama-3.3-70b-versatile desteklenmeli", modelIds.contains("llama-3.3-70b-versatile"))
        assertTrue("llama-3.1-8b-instant desteklenmeli", modelIds.contains("llama-3.1-8b-instant"))
        assertFalse("llama-3.2-11b-vision-preview kaldırılmış olmalı", modelIds.contains("llama-3.2-11b-vision-preview"))
        assertFalse("llama-3.2-90b-vision-preview kaldırılmış olmalı", modelIds.contains("llama-3.2-90b-vision-preview"))
    }

    @Test
    fun ayarlarDataStore_defaultGroqModel_qwen() {
        assertEquals("qwen/qwen3.8-27b", AyarlarDataStore.DEFAULT_GROQ_MODEL)
    }

    @Test
    fun smartAiRouter_fallbackModel_and_fallbackEligibility() {
        assertEquals("qwen/qwen3.8-27b", SmartAiRouter.FALLBACK_GROQ_MODEL)

        val router = SmartAiRouter(
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true)
        )

        // Decommissioned model
        val decommissionedEx = RuntimeException("The model `llama-3.2-11b-vision-preview` has been decommissioned")
        assertTrue("decommissioned hatası fallback almalı", router.isFallbackEligible(decommissionedEx))

        // Model not found
        val notFoundEx = RuntimeException("Model not found: qwen")
        assertTrue("not found hatası fallback almalı", router.isFallbackEligible(notFoundEx))

        // 404 & 400
        val ex404 = AiException(AiErrorType.MODEL_UNAVAILABLE, "HTTP 404 Not Found")
        val ex400 = AiException(AiErrorType.MODEL_UNAVAILABLE, "Groq API Hatası (HTTP 400 bad request)")
        assertTrue("404 fallback almalı", router.isFallbackEligible(ex404))
        assertTrue("400 fallback almalı", router.isFallbackEligible(ex400))
    }

    @Test
    fun appModule_json_explicitNullsFalse() {
        val json = AppModule.provideJson()
        // Test serializing a DTO with null field
        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(
                        GeminiPart(text = "test soru", inlineData = null)
                    )
                )
            )
        )
        val serialized = json.encodeToString(GeminiRequest.serializer(), request)
        assertFalse("inlineData null olarak JSON çıktısında yer almamalı", serialized.contains("inlineData"))
        assertTrue("text serialized edilmeli", serialized.contains("test soru"))
    }

    @Test
    fun nvidiaProvider_mediaType_noCharset() {
        val nvidia = NvidiaProvider(mockk(relaxed = true), mockk(relaxed = true))
        val field = NvidiaProvider::class.java.getDeclaredField("jsonMediaType")
        field.isAccessible = true
        val mediaType = field.get(nvidia) as okhttp3.MediaType
        assertEquals("application/json", mediaType.toString())
    }

    @Test
    fun groqProvider_fallbackModelConstant() {
        assertEquals("qwen/qwen3.8-27b", GroqProvider.FALLBACK_MODEL)
    }

    private fun readSource(relativeFromRoot: String): String {
        val rel = File(relativeFromRoot)
        if (rel.exists()) return rel.readText()
        val rel2 = File("app/$relativeFromRoot")
        if (rel2.exists()) return rel2.readText()
        val rel3 = File(relativeFromRoot.removePrefix("app/"))
        if (rel3.exists()) return rel3.readText()
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        repeat(5) {
            val currentDir = dir ?: return@repeat
            val cand = File(currentDir, relativeFromRoot)
            if (cand.exists()) return cand.readText()
            val cand2 = File(currentDir, relativeFromRoot.removePrefix("app/"))
            if (cand2.exists()) return cand2.readText()
            val cand3 = File(currentDir, "app/$relativeFromRoot")
            if (cand3.exists()) return cand3.readText()
            dir = currentDir.parentFile
        }
        throw java.io.FileNotFoundException("Could not find file: $relativeFromRoot in ${System.getProperty("user.dir")}")
    }

    @Test
    fun aiSolutionRetryWorker_refetchesLatestQuestion() {
        val workerSource = readSource("app/src/main/java/com/sorudefteri/data/worker/AiSolutionRetryWorker.kt")
        assertTrue(
            "Worker en güncel soruyu veri tabanından tekrar çekmeli",
            workerSource.contains("val latest = soruRepository.getSoruById(soruId).first() ?: soru")
        )
    }
}
