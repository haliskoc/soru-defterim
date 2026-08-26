package com.sorudefteri.data.ai

import com.sorudefteri.data.local.AyarlarDataStore
import com.sorudefteri.domain.model.AiProviderType
import com.sorudefteri.domain.model.QuestionMetadata
import com.sorudefteri.domain.model.SimilarQuestionResult
import com.sorudefteri.domain.model.Soru
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * AiRepository katmanının sağlayıcı yönlendirme (Gemini / Groq),
 * API anahtarı doğrulama, canlı model listeleme ve soru çözüm akışlarını test eden birim testleri.
 */
class AiRepositoryTest {

    private lateinit var mockAyarlarDataStore: AyarlarDataStore
    private lateinit var fakeGeminiProvider: FakeGeminiProvider
    private lateinit var fakeGroqProvider: FakeGroqProvider
    private lateinit var fakeNvidiaProvider: FakeNvidiaProvider
    private lateinit var aiRepository: AiRepository

    class FakeGeminiProvider : GeminiProvider(mockk(relaxed = true), mockk(relaxed = true)) {
        var solutionResult: Result<String> = Result.success("Fake Gemini Solution")
        var metadataResult: Result<QuestionMetadata> = Result.success(QuestionMetadata())
        var liveModelsResult: Result<List<Pair<String, String>>> = Result.success(emptyList())

        override suspend fun cozumIsteDetayli(
            imageBytes: ByteArray?,
            dersAdi: String,
            konu: String,
            correctAnswer: String?,
            mistakeType: String?,
            soruMetni: String?,
            modelName: String,
            apiKey: String,
            customPromptTemplate: String?
        ): Result<String> = solutionResult

        override suspend fun detectMetadata(
            imageBytes: ByteArray,
            modelName: String,
            apiKey: String
        ): Result<QuestionMetadata> = metadataResult

        override suspend fun listModels(apiKey: String): Result<List<Pair<String, String>>> = liveModelsResult
    }

    class FakeGroqProvider : GroqProvider(mockk(relaxed = true), mockk(relaxed = true)) {
        var solutionResult: Result<String> = Result.success("Fake Groq Solution")
        var similarResult: Result<SimilarQuestionResult> = Result.success(SimilarQuestionResult("", emptyMap(), "A", ""))

        override suspend fun cozumIsteDetayli(
            imageBytes: ByteArray?,
            dersAdi: String,
            konu: String,
            correctAnswer: String?,
            mistakeType: String?,
            soruMetni: String?,
            modelName: String,
            apiKey: String,
            customPromptTemplate: String?
        ): Result<String> = solutionResult

        override suspend fun generateSimilarQuestion(
            imageBytes: ByteArray?,
            dersAdi: String,
            konu: String,
            soruMetni: String?,
            modelName: String,
            apiKey: String,
            customPromptTemplate: String?
        ): Result<SimilarQuestionResult> = similarResult
    }

    class FakeNvidiaProvider : NvidiaProvider(mockk(relaxed = true), mockk(relaxed = true)) {
        var solutionResult: Result<String> = Result.success("Fake Nvidia Solution")
        var similarResult: Result<SimilarQuestionResult> = Result.success(SimilarQuestionResult("", emptyMap(), "A", ""))

        override suspend fun cozumIsteDetayli(
            imageBytes: ByteArray?,
            dersAdi: String,
            konu: String,
            correctAnswer: String?,
            mistakeType: String?,
            soruMetni: String?,
            modelName: String,
            apiKey: String,
            customPromptTemplate: String?
        ): Result<String> = solutionResult

        override suspend fun generateSimilarQuestion(
            imageBytes: ByteArray?,
            dersAdi: String,
            konu: String,
            soruMetni: String?,
            modelName: String,
            apiKey: String,
            customPromptTemplate: String?
        ): Result<SimilarQuestionResult> = similarResult
    }

    @Before
    fun setUp() {
        mockAyarlarDataStore = mockk(relaxed = true)
        fakeGeminiProvider = FakeGeminiProvider()
        fakeGroqProvider = FakeGroqProvider()
        fakeNvidiaProvider = FakeNvidiaProvider()

        aiRepository = AiRepository(
            ayarlarDataStore = mockAyarlarDataStore,
            geminiProvider = fakeGeminiProvider,
            groqProvider = fakeGroqProvider,
            nvidiaProvider = fakeNvidiaProvider
        )
    }

    @Test
    fun hasApiKey_apiKeyVarsaTrue_yoksaFalseDoner() = runTest {
        coEvery { mockAyarlarDataStore.getActiveApiKey() } returns "AIzaSyFakeKey123"
        assertTrue(aiRepository.hasApiKey())

        coEvery { mockAyarlarDataStore.getActiveApiKey() } returns ""
        assertFalse(aiRepository.hasApiKey())

        coEvery { mockAyarlarDataStore.getActiveApiKey() } returns "   "
        assertFalse(aiRepository.hasApiKey())
    }

    @Test
    fun geminiSeciliyken_cozumIste_geminiProvideraYonlendirilir() = runTest {
        val testBytes = "fake_image_bytes".toByteArray()
        val expectedSolution = "🎯 **Doğru Cevap:** B\n\nAdım 1: ..."
        fakeGeminiProvider.solutionResult = Result.success(expectedSolution)

        coEvery { mockAyarlarDataStore.getAiProvider() } returns AiProviderType.GEMINI
        coEvery { mockAyarlarDataStore.getGeminiApiKey() } returns "gemini_key_123"
        coEvery { mockAyarlarDataStore.getGeminiModel() } returns "gemini-2.5-flash"

        val result = aiRepository.cozumIste(
            imageBytes = testBytes,
            dersAdi = "Matematik",
            konu = "Polinomlar",
            correctAnswer = "B"
        )

        assertTrue(result.isSuccess)
        assertEquals(expectedSolution, result.getOrNull())
    }

    @Test
    fun groqSeciliyken_cozumIste_groqProvideraYonlendirilir() = runTest {
        val testBytes = "fake_image_bytes".toByteArray()
        val expectedSolution = "🎯 **Doğru Cevap:** A\n\nGroq Çözümü..."
        fakeGroqProvider.solutionResult = Result.success(expectedSolution)

        coEvery { mockAyarlarDataStore.getAiProvider() } returns AiProviderType.GROQ
        coEvery { mockAyarlarDataStore.getGroqApiKey() } returns "gsk_groq_key_456"
        coEvery { mockAyarlarDataStore.getGroqModel() } returns "meta-llama/llama-4-scout-17b-16e-instruct"

        val result = aiRepository.cozumIste(
            imageBytes = testBytes,
            dersAdi = "Fizik",
            konu = "Kuvvet ve Hareket",
            correctAnswer = null
        )

        assertTrue(result.isSuccess)
        assertEquals(expectedSolution, result.getOrNull())
    }

    @Test
    fun apiKeyYoksa_cozumIste_hataFirlatir() = runTest {
        val testBytes = "test_bytes".toByteArray()

        coEvery { mockAyarlarDataStore.getAiProvider() } returns AiProviderType.GEMINI
        coEvery { mockAyarlarDataStore.getGeminiApiKey() } returns ""

        val result = aiRepository.cozumIste(testBytes, "Kimya", "Asitler ve Bazlar")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("API anahtarı bulunamadı") == true)
    }

    @Test
    fun detectQuestionMetadata_aktifSaglayiciyaYonlendirilir() = runTest {
        val testBytes = "metadata_image".toByteArray()
        val expected = QuestionMetadata(examType = "TYT", subjectId = "biyoloji", topic = "Hücre", correctAnswer = "E")
        fakeGeminiProvider.metadataResult = Result.success(expected)

        coEvery { mockAyarlarDataStore.getAiProvider() } returns AiProviderType.GEMINI
        coEvery { mockAyarlarDataStore.getGeminiApiKey() } returns "gemini_key"
        coEvery { mockAyarlarDataStore.getGeminiModel() } returns "gemini-2.5-flash"

        val result = aiRepository.detectQuestionMetadata(testBytes)
        assertTrue(result.isSuccess)
        assertEquals("biyoloji", result.getOrNull()?.subjectId)
        assertEquals("Hücre", result.getOrNull()?.topic)
        assertEquals("E", result.getOrNull()?.correctAnswer)
    }

    @Test
    fun generateSimilarQuestion_aktifSaglayiciyaYonlendirilir() = runTest {
        val testBytes = "similar_image".toByteArray()
        val expected = SimilarQuestionResult(
            questionText = "Özgün soru metni...",
            options = mapOf("A" to "1", "B" to "2"),
            correctAnswer = "B",
            solution = "Çözüm..."
        )
        fakeGroqProvider.similarResult = Result.success(expected)

        coEvery { mockAyarlarDataStore.getAiProvider() } returns AiProviderType.GROQ
        coEvery { mockAyarlarDataStore.getGroqApiKey() } returns "groq_key"
        coEvery { mockAyarlarDataStore.getGroqModel() } returns "meta-llama/llama-4-scout-17b-16e-instruct"

        val result = aiRepository.generateSimilarQuestion(testBytes, "Geometri", "Üçgenler")
        assertTrue(result.isSuccess)
        assertEquals("B", result.getOrNull()?.correctAnswer)
        assertEquals("Çözüm...", result.getOrNull()?.solution)
    }

    @Test
    fun fetchLiveModels_apiKeyEksikligindeHata_varsaModelListesiDondurur() = runTest {
        // 1. API key eksik
        coEvery { mockAyarlarDataStore.getGeminiApiKey() } returns ""
        val failResult = aiRepository.fetchLiveModels(AiProviderType.GEMINI)
        assertTrue(failResult.isFailure)

        // 2. API key mevcut
        coEvery { mockAyarlarDataStore.getGeminiApiKey() } returns "valid_gemini_key"
        fakeGeminiProvider.liveModelsResult = Result.success(
            listOf("gemini-2.5-flash" to "Gemini 2.5 Flash", "gemini-2.5-pro" to "Gemini 2.5 Pro")
        )

        val successResult = aiRepository.fetchLiveModels(AiProviderType.GEMINI)
        assertTrue(successResult.isSuccess)
        assertEquals(2, successResult.getOrNull()?.size)
    }
}
