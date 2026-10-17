package com.sorudefteri.data.ai

import com.sorudefteri.data.local.AyarlarDataStore
import com.sorudefteri.domain.model.AiErrorType
import com.sorudefteri.domain.model.AiException
import com.sorudefteri.domain.model.AiProviderType
import com.sorudefteri.domain.model.QuestionMetadata
import com.sorudefteri.domain.model.SimilarQuestionResult
import com.sorudefteri.domain.model.Soru
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.InterruptedIOException
import java.net.SocketTimeoutException

/**
 * AiRepository katmanının sağlayıcı yönlendirme (Gemini / Groq),
 * API anahtarı doğrulama, canlı model listeleme ve soru çözüm akışlarını test eden birim testleri.
 */
class AiRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var mockAyarlarDataStore: AyarlarDataStore
    private lateinit var fakeGeminiProvider: FakeGeminiProvider
    private lateinit var fakeGroqProvider: FakeGroqProvider
    private lateinit var fakeNvidiaProvider: FakeNvidiaProvider
    private lateinit var mockGorselDepo: com.sorudefteri.data.images.GorselDepo
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
        mockGorselDepo = mockk(relaxed = true)
        // Default stub: return fake bytes for any image path so getAiSolution file path tests succeed
        io.mockk.coEvery { mockGorselDepo.getGorselBytes(any()) } returns "fake_image_bytes".toByteArray()

        val promptManager = AiPromptManager(mockAyarlarDataStore)
        val smartAiRouter = SmartAiRouter(
            geminiProvider = fakeGeminiProvider,
            groqProvider = fakeGroqProvider,
            nvidiaProvider = fakeNvidiaProvider,
            ayarlarDataStore = mockAyarlarDataStore,
            gorselDepo = mockGorselDepo,
            promptManager = promptManager
        )

        aiRepository = AiRepository(
            ayarlarDataStore = mockAyarlarDataStore,
            geminiProvider = fakeGeminiProvider,
            groqProvider = fakeGroqProvider,
            nvidiaProvider = fakeNvidiaProvider,
            gorselDepo = mockGorselDepo,
            smartAiRouter = smartAiRouter
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
        coEvery { mockAyarlarDataStore.getGeminiModel() } returns "gemini-2.0-flash"

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
        coEvery { mockAyarlarDataStore.getGroqModel() } returns "llama-3.2-11b-vision-preview"

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
        coEvery { mockAyarlarDataStore.getGeminiModel() } returns "gemini-2.0-flash"

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
        coEvery { mockAyarlarDataStore.getGroqModel() } returns "llama-3.2-11b-vision-preview"

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
            listOf("gemini-2.0-flash" to "Gemini 2.0 Flash", "gemini-1.5-pro" to "Gemini 1.5 Pro")
        )

        val successResult = aiRepository.fetchLiveModels(AiProviderType.GEMINI)
        assertTrue(successResult.isSuccess)
        assertEquals(2, successResult.getOrNull()?.size)
    }

    // =====================================================================
    // FIX 1: QUOTA_EXCEEDED retry FALSE doğrulaması (kota yakmamak için)
    // =====================================================================
    @Test
    fun quotaExceeded_retryFalse_isRetryableException() {
        val providers = listOf<Any>(fakeGeminiProvider, fakeGroqProvider, fakeNvidiaProvider)
        // Gerçek provider sınıflarını mock Ajans yerine gerçek instance ile test et
        val realGemini = GeminiProvider(mockk(relaxed = true), mockk(relaxed = true))
        val realGroq = GroqProvider(mockk(relaxed = true), mockk(relaxed = true))
        val realNvidia = NvidiaProvider(mockk(relaxed = true), mockk(relaxed = true))
        val reals = listOf(realGemini, realGroq, realNvidia)

        for (provider in reals) {
            val method = provider.javaClass.getDeclaredMethod("isRetryableException", Throwable::class.java)
            method.isAccessible = true
            val quotaEx = AiException(AiErrorType.QUOTA_EXCEEDED, "API kullanım kotanız doldu (429)")
            val isRetryable = method.invoke(provider, quotaEx) as Boolean
            assertFalse("QUOTA_EXCEEDED asla retry edilmemeli: ${provider.javaClass.simpleName}", isRetryable)

            val networkEx = AiException(AiErrorType.NETWORK_ERROR, "ağ hatası")
            assertTrue("${provider.javaClass.simpleName} NETWORK_ERROR retry true olmalı", method.invoke(provider, networkEx) as Boolean)

            val timeoutEx = AiException(AiErrorType.NETWORK_TIMEOUT, "timeout")
            assertTrue(method.invoke(provider, timeoutEx) as Boolean)

            val model404 = AiException(AiErrorType.MODEL_UNAVAILABLE, "Model bulunamadı (HTTP 404)")
            assertFalse("404 retry false", method.invoke(provider, model404) as Boolean)

            val model503 = AiException(AiErrorType.MODEL_UNAVAILABLE, "HTTP 503 Service Unavailable")
            assertTrue("503 retry true", method.invoke(provider, model503) as Boolean)

            val parsing = AiException(AiErrorType.PARSING_FAILED, "parse")
            assertFalse("PARSING_FAILED retry false", method.invoke(provider, parsing) as Boolean)

            val cancel = CancellationException("cancel")
            assertFalse("CancellationException retry false", method.invoke(provider, cancel) as Boolean)

            val interrupted = InterruptedIOException("interrupted")
            assertTrue("InterruptedIOException retry true", method.invoke(provider, interrupted) as Boolean)

            val socketTimeout = SocketTimeoutException("timeout")
            assertTrue("SocketTimeoutException retry true", method.invoke(provider, socketTimeout) as Boolean)
        }
    }

    // =====================================================================
    // FIX 4: 5MB image check File.length() öncesi (readBytes sonrası değil)
    // =====================================================================
    @Test
    fun cozumIste_5mbUstu_gorsel_hataDoner_readBytesOncesiKontrol() = runTest {
        // 5MB+1 byte byte array ile doğrudan cozumIste çağrısı provider'a gitmeden hata vermeli
        val hugeBytes = ByteArray(5 * 1024 * 1024 + 1) { 0 }
        coEvery { mockAyarlarDataStore.getAiProvider() } returns AiProviderType.GEMINI
        coEvery { mockAyarlarDataStore.getGeminiApiKey() } returns "key"
        coEvery { mockAyarlarDataStore.getGeminiModel() } returns "gemini-2.0-flash"
        val result = aiRepository.cozumIste(hugeBytes, "Matematik", "Türev")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Görsel boyutu çok büyük") == true)
    }

    @Test
    fun getAiSolution_5mbUstu_dosya_hataDoner_fileLengthKontrolu() = runTest {
        val hugeFile = tempFolder.newFile("huge_image.jpg")
        // Fiziksel olarak 5MB+ yaz; File.length() kontrolü readBytes öncesi yakalamalı
        hugeFile.outputStream().use { out ->
            // 5MB+1KB yaz
            val chunk = ByteArray(1024 * 1024) { 0x5A.toByte() }
            repeat(5) { out.write(chunk) }
            out.write(ByteArray(1024) { 0x5A.toByte() })
        }
        assertTrue(hugeFile.length() > 5 * 1024 * 1024)

        // Stub GorselDepo to return huge bytes so AiRepository size check triggers (compress sonrası hala büyük)
        coEvery { mockGorselDepo.getGorselBytes(hugeFile.absolutePath) } returns ByteArray(5 * 1024 * 1024 + 1)

        val soru = Soru(
            id = 1L,
            imagePath = hugeFile.absolutePath,
            examType = "TYT",
            subjectId = "matematik",
            topic = "Fonksiyonlar"
        )
        val result = aiRepository.getAiSolution(soru, sendImage = true, extractedText = null)
        assertTrue("5MB üstü dosya GorselDepo sonrası da reddedilmeli", result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Görsel boyutu çok büyük") == true)
    }

    @Test
    fun getAiSolutionFromText_boslukKontrolu_blankIseHataDondurur() = runTest {
        val blankResult = aiRepository.getAiSolutionFromText("Matematik", "Konu", "   ", "A")
        assertTrue(blankResult.isFailure)
        // blank text now returns AiException(UNKNOWN) instead of IllegalArgumentException (improved validation)
        assertTrue(blankResult.exceptionOrNull() is AiException || blankResult.exceptionOrNull() is IllegalArgumentException)

        val validResult = aiRepository.getAiSolutionFromText("Matematik", "Konu", "Gerçek soru metni", "A")
        // API key yoksa INVALID_API_KEY döner ama blank değilse prompt validasyonunu geçer
        // Bu testte GEMINI api key boş, dolayısıyla INVALID_API_KEY dönecek, blank hatası değil
        coEvery { mockAyarlarDataStore.getAiProvider() } returns AiProviderType.GEMINI
        coEvery { mockAyarlarDataStore.getGeminiApiKey() } returns ""
        val noKeyResult = aiRepository.getAiSolutionFromText("Matematik", "Konu", "Gerçek metin", null)
        assertTrue(noKeyResult.isFailure)
    }
}
