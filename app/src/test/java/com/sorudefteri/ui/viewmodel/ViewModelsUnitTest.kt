package com.sorudefteri.ui.viewmodel

import android.net.Uri
import com.sorudefteri.data.backup.BackupManager
import com.sorudefteri.data.images.GorselDepo
import com.sorudefteri.data.local.AyarlarDataStore
import com.sorudefteri.data.local.entity.TestSonucEntity
import com.sorudefteri.data.repository.SoruRepository
import com.sorudefteri.domain.KonuKatalog
import com.sorudefteri.domain.KonuUnite
import com.sorudefteri.domain.model.AiProviderType
import com.sorudefteri.domain.model.Alan
import com.sorudefteri.domain.model.DersSoruSayisi
import com.sorudefteri.domain.model.GunlukAktivite
import com.sorudefteri.domain.model.HataTipi
import com.sorudefteri.domain.model.HataTipiDagilimi
import com.sorudefteri.domain.model.QuestionMetadata
import com.sorudefteri.domain.model.SimilarQuestionResult
import com.sorudefteri.domain.model.SortOrder
import com.sorudefteri.domain.model.Soru
import com.sorudefteri.domain.model.TestFiltre
import com.sorudefteri.domain.model.TestQuestion
import com.sorudefteri.domain.model.TestResult
import com.sorudefteri.domain.repository.AiRepository
import com.sorudefteri.domain.repository.TestSessionManager
import com.sorudefteri.ui.ayarlar.AyarlarViewModel
import com.sorudefteri.ui.istatistik.IstatistikViewModel
import com.sorudefteri.ui.kutuphane.KutuphaneViewModel
import com.sorudefteri.ui.soruekle.SoruEklemeViewModel
import com.sorudefteri.ui.test.TestOlusturViewModel
import com.sorudefteri.ui.test.TestSonucViewModel
import kotlinx.coroutines.CoroutineScope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

/**
 * SoruEklemeViewModel, KutuphaneViewModel ve AyarlarViewModel için
 * durum yönetimi, filtreleme, arama, AI aksiyonları ve veri bağlama birim testleri.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelsUnitTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // Mock & Fake Nesneler
    private lateinit var mockSoruRepository: SoruRepository
    private lateinit var mockGorselDepo: GorselDepo
    private lateinit var mockKonuKatalog: KonuKatalog
    private lateinit var mockTestSessionManager: TestSessionManager
    private lateinit var mockAyarlarDataStore: AyarlarDataStore
    private lateinit var fakeAiRepository: FakeAiRepository
    private lateinit var fakeBackupManager: FakeBackupManager
    private lateinit var mockOcrHelper: com.sorudefteri.data.ocr.OcrHelper

    @Before
    fun setUp() {
        mockSoruRepository = mockk(relaxed = true)
        mockGorselDepo = mockk(relaxed = true)
        mockKonuKatalog = mockk(relaxed = true)
        mockTestSessionManager = mockk(relaxed = true)
        mockAyarlarDataStore = mockk(relaxed = true)
        fakeAiRepository = FakeAiRepository()
        fakeBackupManager = FakeBackupManager()
        mockOcrHelper = mockk(relaxed = true)

        // Varsayılan DataStore akışları
        every { mockAyarlarDataStore.currentAlan } returns flowOf(Alan.SAYISAL)
        every { mockAyarlarDataStore.aiProvider } returns flowOf(AiProviderType.GEMINI)
        every { mockAyarlarDataStore.geminiApiKey } returns flowOf("test_gemini_key")
        every { mockAyarlarDataStore.groqApiKey } returns flowOf("test_groq_key")
        every { mockAyarlarDataStore.nvidiaApiKey } returns flowOf("test_nvidia_key")
        every { mockAyarlarDataStore.geminiModel } returns flowOf("gemini-2.0-flash")
        every { mockAyarlarDataStore.groqModel } returns flowOf("llama-3.2-11b-vision-preview")
        every { mockAyarlarDataStore.nvidiaModel } returns flowOf("meta/llama-3.2-11b-vision-instruct")
        every { mockAyarlarDataStore.themeMode } returns flowOf(0)
        every { mockAyarlarDataStore.isAutoBackupEnabled } returns flowOf(false)
        every { mockAyarlarDataStore.isWifiOnlyBackup } returns flowOf(true)
        every { mockAyarlarDataStore.driveLastSyncTime } returns flowOf(0L)
        every { mockAyarlarDataStore.autoBackupEnabledFlow } returns flowOf(false)
        every { mockAyarlarDataStore.wifiOnlyBackupFlow } returns flowOf(true)
        every { mockAyarlarDataStore.stylusDoubleTapActionFlow } returns flowOf(com.sorudefteri.ui.ortak.drawing.StylusDoubleTapAction.PEN_ERASER_TOGGLE)
        every { mockAyarlarDataStore.stylusPalmRejectionFlow } returns flowOf(true)
        every { mockAyarlarDataStore.stylusHapticFeedbackFlow } returns flowOf(true)
        every { mockAyarlarDataStore.autoSolveOnSave } returns flowOf(false)
        every { mockAyarlarDataStore.batchSolveDelaySeconds } returns flowOf(3)
        every { mockAyarlarDataStore.taskRoutes } returns flowOf(emptyMap())
        every { mockAyarlarDataStore.promptSoruUretimiFlow } returns flowOf("")
        every { mockAyarlarDataStore.promptCozumUretimiFlow } returns flowOf("")
        every { mockAyarlarDataStore.promptOcrTemizlemeFlow } returns flowOf("")
        every { mockAyarlarDataStore.promptBenzerSoruFlow } returns flowOf("")
        every { mockSoruRepository.getDistinctTopics(any()) } returns flowOf(listOf("Trigonometri", "Türev"))
        every { mockSoruRepository.totalCount } returns flowOf(0)
        every { mockSoruRepository.solvedCount } returns flowOf(0)
        every { mockSoruRepository.unsolvedCount } returns flowOf(0)
        every { mockSoruRepository.dueReviewCount } returns flowOf(0)
        every { mockSoruRepository.getSubjectStats() } returns flowOf(emptyList())
        every { mockSoruRepository.getMistakeStats() } returns flowOf(emptyList())
        every { mockSoruRepository.getWeeklyActivity() } returns flowOf(emptyList())
        every { mockSoruRepository.getAllTestResults() } returns flowOf(emptyList())
        every { mockSoruRepository.getAllSorular() } returns flowOf(emptyList())
        every { mockSoruRepository.getAllDenemeler() } returns flowOf(emptyList())
        every { mockSoruRepository.getKutuphaneMetaFlow() } returns flowOf(com.sorudefteri.domain.model.KutuphaneMeta())
        coEvery { mockSoruRepository.getKutuphaneMeta() } returns com.sorudefteri.domain.model.KutuphaneMeta()
        coEvery { mockSoruRepository.countAddedBetween(any(), any()) } returns 0
        coEvery { mockSoruRepository.countSolvedBetween(any(), any()) } returns 0
        coEvery { mockSoruRepository.getFilteredSoruIds(any(), any(), any(), any(), any(), any(), any(), any()) } returns emptyList()
        coEvery { mockSoruRepository.getSorularByIds(any()) } returns emptyList()
        every { mockKonuKatalog.getUniteList(any(), any()) } returns listOf(
            KonuUnite(uniteAdi = "Fonksiyonlar", konular = listOf("Fonksiyon Grafikleri", "Bileşke"))
        )
    }

    class FakeAiRepository : AiRepository {
        var metadataResult: Result<QuestionMetadata> = Result.success(
            QuestionMetadata(examType = "TYT", subjectId = "fizik", topic = "Kaldırma Kuvveti", correctAnswer = "D")
        )
        var liveModelsResult: Result<List<Pair<String, String>>> = Result.success(
            listOf("gemini-2.0-flash" to "Gemini 2.0 Flash", "gemini-1.5-pro" to "Gemini 1.5 Pro")
        )
        var solutionResult: Result<String> = Result.success("Fake Solution")
        var similarResult: Result<SimilarQuestionResult> = Result.success(SimilarQuestionResult("", emptyMap(), "A", ""))
        var twinQuestionsResult: Result<com.sorudefteri.domain.model.TwinQuestionsResult> = Result.success(com.sorudefteri.domain.model.TwinQuestionsResult())

        override suspend fun getAiSolution(soru: Soru, sendImage: Boolean, extractedText: String?): Result<String> = solutionResult
        override suspend fun getAiSolutionFromText(dersAdi: String, konu: String, soruMetni: String, correctAnswer: String?, mistakeType: String?): Result<String> = solutionResult
        override suspend fun cleanOcrText(rawOcrText: String): Result<String> = Result.success(rawOcrText)
        override suspend fun generateSimilarQuestion(imageBytes: ByteArray?, dersAdi: String, konu: String, soruMetni: String?): Result<SimilarQuestionResult> = similarResult
        override suspend fun generateSimilarQuestion(imageBytes: ByteArray, dersAdi: String, konu: String): Result<SimilarQuestionResult> = similarResult
        override suspend fun generateTwinQuestions(soru: Soru, sendImage: Boolean, extractedText: String?): Result<com.sorudefteri.domain.model.TwinQuestionsResult> = twinQuestionsResult
        override suspend fun generateQuestion(dersAdi: String, konu: String, zorluk: String, alan: String, sinavTipi: String): Result<SimilarQuestionResult> = similarResult
        override suspend fun detectQuestionMetadata(imageBytes: ByteArray): Result<QuestionMetadata> = metadataResult
        override suspend fun hasApiKey(): Boolean = true
        override suspend fun fetchLiveModels(provider: AiProviderType): Result<List<Pair<String, String>>> = liveModelsResult
        override suspend fun sendSoruChatMessage(
            soru: Soru,
            chatHistory: List<com.sorudefteri.data.local.entity.SoruChatMessageEntity>,
            userMessage: String,
            sendImage: Boolean
        ): Result<String> = solutionResult
    }

    class FakeBackupManager : BackupManager(mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true)) {
        var exportResult: Result<Int> = Result.success(15)
        var importResult: Result<Int> = Result.success(20)

        override suspend fun exportBackup(targetUri: Uri, password: String?): Result<Int> = exportResult
        override suspend fun importBackup(sourceUri: Uri, password: String?): Result<Int> = importResult
    }

    // =========================================================================
    // 1. SoruEklemeViewModel Testleri
    // =========================================================================

    @Test
    fun soruEklemeViewModel_alanVeDersSecimi_uiStateGuncellenir() = runTest {
        val viewModel = SoruEklemeViewModel(
            soruRepository = mockSoruRepository,
            gorselDepo = mockGorselDepo,
            konuKatalog = mockKonuKatalog,
            ayarlarDataStore = mockAyarlarDataStore,
            aiRepository = fakeAiRepository,
            ocrHelper = mockOcrHelper
        )

        // Sınav türü değiştirme
        viewModel.setExamType("AYT")
        assertEquals("AYT", viewModel.uiState.value.examType)

        // Ders seçimi
        viewModel.setSubject("ayt_fizik")
        assertEquals("ayt_fizik", viewModel.uiState.value.subjectId)

        // Konu yazma
        viewModel.setTopic("Basit Harmonik Hareket")
        assertEquals("Basit Harmonik Hareket", viewModel.uiState.value.topic)

        // Hata tipi ve şık
        viewModel.setMistakeType(HataTipi.FORMUL_HATASI)
        assertEquals(HataTipi.FORMUL_HATASI, viewModel.uiState.value.mistakeType)

        viewModel.setCorrectAnswer("A")
        assertEquals("A", viewModel.uiState.value.correctAnswer)
    }

    @Test
    fun soruEklemeViewModel_saveSoru_validasyonVeKayit() = runTest {
        val viewModel = SoruEklemeViewModel(
            soruRepository = mockSoruRepository,
            gorselDepo = mockGorselDepo,
            konuKatalog = mockKonuKatalog,
            ayarlarDataStore = mockAyarlarDataStore,
            aiRepository = fakeAiRepository,
            ocrHelper = mockOcrHelper
        )

        var successCallbackCalled = false

        // Görsel olmadan kaydetmeye çalışma
        viewModel.saveSoru { successCallbackCalled = true }
        assertFalse("Görsel yokken kayıt başarısız olmalı", successCallbackCalled)
        assertNotNull(viewModel.uiState.value.errorMessage)

        // Görsel ve konu ekle
        viewModel.setSoruImagePath("/path/soru_test.jpg")
        viewModel.setTopic("Olasılık")

        assertTrue("Görsel ve konu varken canSave true olmalı", viewModel.uiState.value.canSave)

        coEvery { mockSoruRepository.soruEkle(any()) } returns 101L

        viewModel.saveSoru { successCallbackCalled = true }
        assertTrue("Kayıt başarılı olmalı ve callback çağrılmalı", successCallbackCalled)
        coVerify(exactly = 1) { mockSoruRepository.soruEkle(match { it.topic == "Olasılık" && it.imagePath == "/path/soru_test.jpg" }) }
    }

    @Test
    fun soruEklemeViewModel_autoTagWithAi_basariliAnalizdeAlanlariDoldurur() = runTest {
        val viewModel = SoruEklemeViewModel(
            soruRepository = mockSoruRepository,
            gorselDepo = mockGorselDepo,
            konuKatalog = mockKonuKatalog,
            ayarlarDataStore = mockAyarlarDataStore,
            aiRepository = fakeAiRepository,
            ocrHelper = mockOcrHelper
        )

        backgroundScope.launch { viewModel.uiState.collect() }

        viewModel.setSoruImagePath("/path/soru.jpg")
        val fakeBytes = "fake_bytes".toByteArray()
        coEvery { mockGorselDepo.getGorselBytes("/path/soru.jpg") } returns fakeBytes
        fakeAiRepository.metadataResult = Result.success(
            QuestionMetadata(examType = "TYT", subjectId = "fizik", topic = "Kaldırma Kuvveti", correctAnswer = "D")
        )

        viewModel.autoTagWithAi()
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("TYT", state.examType)
        assertEquals("fizik", state.subjectId)
        assertEquals("Kaldırma Kuvveti", state.topic)
        assertEquals("D", state.correctAnswer)
        assertNotNull(state.autoTagSuccessMessage)
        assertNull(state.errorMessage)
    }

    // =========================================================================
    // 2. KutuphaneViewModel Testleri
    // =========================================================================

    @Test
    fun kutuphaneViewModel_filtrelemeVeArama_dogruCalisir() = runTest {
        val s1 = Soru(id = 1, imagePath = "1", examType = "TYT", subjectId = "matematik", topic = "Trigonometri", note = "Önemli formül", isFavorite = true, isSolved = false)
        val s2 = Soru(id = 2, imagePath = "2", examType = "AYT", subjectId = "fizik", topic = "Optik", isFavorite = false, isSolved = true)
        val s3 = Soru(id = 3, imagePath = "3", examType = "TYT", subjectId = "turkce", topic = "Paragraf", isFavorite = false, isSolved = false)

        every { mockSoruRepository.getKutuphaneMetaFlow() } returns flowOf(
            com.sorudefteri.domain.model.KutuphaneMeta(
                totalCount = 3, solvedCount = 1, unsolvedCount = 2,
                favoriteCount = 1, dueReviewCount = 2
            )
        )

        val viewModel = KutuphaneViewModel(mockSoruRepository, mockAyarlarDataStore, TestSessionManager(), mainDispatcherRule.testDispatcher)
        backgroundScope.launch { viewModel.uiState.collect() }
        testScheduler.advanceUntilIdle()

        // 1. Başlangıç meta sayıları (tam liste state'te tutulmaz)
        assertEquals(3, viewModel.uiState.value.totalCount)
        assertEquals(1, viewModel.uiState.value.solvedCount)
        assertEquals(1, viewModel.uiState.value.favoriteCount)

        // 2. Arama sorgusu state'e yansır (filtreleme Paging tarafında yapılır)
        viewModel.onSearchQueryChange("Trigono")
        testScheduler.advanceTimeBy(350L)
        assertEquals("Trigono", viewModel.uiState.value.searchQuery)

        // Arama temizle
        viewModel.onSearchQueryChange("")
        testScheduler.advanceTimeBy(350L)
        assertEquals("", viewModel.uiState.value.searchQuery)

        // 3. Sınav tipi filtresi state'e yansır
        viewModel.onExamTypeSelected("AYT")
        testScheduler.advanceUntilIdle()
        assertEquals("AYT", viewModel.uiState.value.selectedExamType)

        // 4. Sadece favoriler
        viewModel.resetFilters()
        viewModel.onOnlyFavoritesToggle(true)
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.onlyFavorites)

        // 5. Sadece çözülmemişler
        viewModel.resetFilters()
        viewModel.onOnlyUnsolvedToggle(true)
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.onlyUnsolved)

        // 6. Export yolu id->satır okur (mock repository relaxed: boş liste)
        coEvery { mockSoruRepository.getFilteredSoruIds(any(), any(), any(), any(), any(), any(), any(), any()) } returns listOf(1L, 2L, 3L)
        coEvery { mockSoruRepository.getSorularByIds(listOf(1L, 2L, 3L)) } returns listOf(s1, s2, s3)
        val exported = viewModel.getFilteredSorularForExport()
        assertEquals(3, exported.size)
    }

    @Test
    fun kutuphaneViewModel_topluSecimVeAksiyonlar() = runTest {
        val s1 = Soru(id = 1, imagePath = "1", examType = "TYT", subjectId = "mat", topic = "A")
        val s2 = Soru(id = 2, imagePath = "2", examType = "TYT", subjectId = "mat", topic = "B")
        every { mockSoruRepository.getKutuphaneMetaFlow() } returns flowOf(
            com.sorudefteri.domain.model.KutuphaneMeta(totalCount = 2)
        )

        val viewModel = KutuphaneViewModel(mockSoruRepository, mockAyarlarDataStore, TestSessionManager(), mainDispatcherRule.testDispatcher)
        backgroundScope.launch { viewModel.uiState.collect() }
        testScheduler.advanceUntilIdle()

        // Tekli seçim
        viewModel.toggleSelectSoru(1L)
        assertTrue(viewModel.uiState.value.isSelectionMode)
        assertEquals(setOf(1L), viewModel.uiState.value.selectedSoruIds)

        // Tümünü seç (DAO id sorgusu üzerinden)
        coEvery { mockSoruRepository.getFilteredSoruIds(any(), any(), any(), any(), any(), any(), any(), any()) } returns listOf(1L, 2L)
        viewModel.selectAll()
        testScheduler.advanceUntilIdle()
        assertEquals(setOf(1L, 2L), viewModel.uiState.value.selectedSoruIds)

        // Toplu çözüldü yap
        viewModel.batchMarkSolvedSelected(true)
        coVerify(exactly = 1) { mockSoruRepository.updateSolvedStatusBatch(listOf(1L, 2L), true) }
        assertFalse(viewModel.uiState.value.isSelectionMode)

        // Toplu sil
        viewModel.toggleSelectSoru(1L)
        viewModel.batchDeleteSelected()
        coVerify(exactly = 1) { mockSoruRepository.deleteSorularByIds(listOf(1L)) }
    }

    // =========================================================================
    // 3. AyarlarViewModel Testleri
    // =========================================================================

    @Test
    fun ayarlarViewModel_tercihlerVeCanliModelCekme() = runTest {
        val viewModel = AyarlarViewModel(
            ayarlarDataStore = mockAyarlarDataStore,
            soruRepository = mockSoruRepository,
            aiRepository = fakeAiRepository,
            backupManager = fakeBackupManager,
            gorselDepo = mockGorselDepo,
            appContext = mockk(relaxed = true)
        )
        backgroundScope.launch { viewModel.uiState.collect() }
        testScheduler.advanceUntilIdle()

        // Alan değiştirme
        viewModel.setAlan(Alan.ESIT_AGIRLIK)
        coVerify(exactly = 1) { mockAyarlarDataStore.setAlan(Alan.ESIT_AGIRLIK) }

        // AI Provider değiştirme
        viewModel.setAiProvider(AiProviderType.GROQ)
        coVerify(exactly = 1) { mockAyarlarDataStore.setAiProvider(AiProviderType.GROQ) }

        // API anahtarı güncelleme
        viewModel.setGeminiApiKey("new_gemini_key")
        coVerify(exactly = 1) { mockAyarlarDataStore.setGeminiApiKey("new_gemini_key") }

        // Tema modu değiştirme
        viewModel.setThemeMode(2)
        coVerify(exactly = 1) { mockAyarlarDataStore.setThemeMode(2) }

        // AI Otomasyon ve Görev Yönlendirme
        viewModel.setAutoSolveOnSave(true)
        coVerify(exactly = 1) { mockAyarlarDataStore.setAutoSolveOnSave(true) }

        viewModel.setBatchSolveDelaySeconds(5)
        coVerify(exactly = 1) { mockAyarlarDataStore.setBatchSolveDelaySeconds(5) }

        val sampleRoute = com.sorudefteri.domain.model.AiTaskRoute(AiProviderType.GROQ, "qwen/qwen3.8-27b")
        viewModel.setTaskRoute(com.sorudefteri.domain.model.AiTaskType.COZUM, sampleRoute)
        coVerify(exactly = 1) { mockAyarlarDataStore.setTaskRoute(com.sorudefteri.domain.model.AiTaskType.COZUM, sampleRoute) }

        // Canlı model listesi çekme (Başarılı)
        fakeAiRepository.liveModelsResult = Result.success(
            listOf("gemini-2.0-flash" to "Gemini 2.0 Flash", "gemini-1.5-pro" to "Gemini 1.5 Pro")
        )

        viewModel.fetchLiveModels(AiProviderType.GEMINI)
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.fetchedGeminiModels.size)
        assertFalse(state.isModelFetchError)
        assertNotNull(state.modelFetchStatus)
    }

    @Test
    fun ayarlarViewModel_yedeklemeAksiyonlari() = runTest {
        val viewModel = AyarlarViewModel(
            ayarlarDataStore = mockAyarlarDataStore,
            soruRepository = mockSoruRepository,
            aiRepository = fakeAiRepository,
            backupManager = fakeBackupManager,
            gorselDepo = mockGorselDepo,
            appContext = mockk(relaxed = true)
        )
        backgroundScope.launch { viewModel.uiState.collect() }
        testScheduler.advanceUntilIdle()

        val mockUri = mockk<Uri>()
        fakeBackupManager.exportResult = Result.success(15)

        // Dışa aktar
        viewModel.exportBackup(mockUri)
        testScheduler.advanceUntilIdle()
        // İçe aktar
        fakeBackupManager.importResult = Result.success(20)
        viewModel.importBackup(mockUri)
        testScheduler.advanceUntilIdle()
        val importState = viewModel.uiState.value
        assertTrue(importState.backupMessage?.contains("20 soru") == true)

        // Tüm verileri sıfırla
        var resetDone = false
        coEvery { mockSoruRepository.deleteAllSorular() } returns Unit
        viewModel.resetAllData { resetDone = true }
        testScheduler.advanceUntilIdle()
        assertTrue(resetDone)
        coVerify(exactly = 1) { mockSoruRepository.deleteAllSorular() }
    }

    // =========================================================================
    // 4. Faz 1: Görsel Temizleme, Leitner Motoru & Öncelikli Soru Seçimi Testleri
    // =========================================================================

    @Test
    fun soruEklemeViewModel_onCleared_kaydedilmemisIseGorselleriSiler() = runTest {
        val viewModel = SoruEklemeViewModel(
            soruRepository = mockSoruRepository,
            gorselDepo = mockGorselDepo,
            konuKatalog = mockKonuKatalog,
            ayarlarDataStore = mockAyarlarDataStore,
            aiRepository = fakeAiRepository,
            ocrHelper = mockOcrHelper
        )
        viewModel.cleanupScope = CoroutineScope(mainDispatcherRule.testDispatcher)

        viewModel.setSoruImagePath("/path/temp_soru.jpg")
        viewModel.setSolutionImagePath("/path/temp_cozum.jpg")

        // Kaydedilmeden ekrandan çıkış (onCleared)
        viewModel.onCleared()
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { mockGorselDepo.gorselSil("/path/temp_soru.jpg") }
        coVerify(exactly = 1) { mockGorselDepo.gorselSil("/path/temp_cozum.jpg") }
    }

    @Test
    fun soruEklemeViewModel_onCleared_kaydedilmisIseGorselleriSilmez() = runTest {
        val viewModel = SoruEklemeViewModel(
            soruRepository = mockSoruRepository,
            gorselDepo = mockGorselDepo,
            konuKatalog = mockKonuKatalog,
            ayarlarDataStore = mockAyarlarDataStore,
            aiRepository = fakeAiRepository,
            ocrHelper = mockOcrHelper
        )
        viewModel.cleanupScope = CoroutineScope(mainDispatcherRule.testDispatcher)

        viewModel.setSoruImagePath("/path/saved_soru.jpg")
        viewModel.setTopic("Trigonometri")
        coEvery { mockSoruRepository.soruEkle(any()) } returns 1L

        viewModel.saveSoru { }
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isSaved)

        // Kaydedildikten sonra onCleared
        viewModel.onCleared()
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 0) { mockGorselDepo.gorselSil("/path/saved_soru.jpg") }
    }

    @Test
    fun testSonucViewModel_sonuclarYuklendigindeDogruVeYanlisSorularIcinTekrarKademesiniGunceller() = runTest {
        val s1 = Soru(id = 10, imagePath = "1", examType = "TYT", subjectId = "mat", topic = "A")
        val s2 = Soru(id = 20, imagePath = "2", examType = "TYT", subjectId = "mat", topic = "B")
        val s3 = Soru(id = 30, imagePath = "3", examType = "TYT", subjectId = "mat", topic = "C")

        val tq1 = TestQuestion(soru = s1, userAnswer = "A", isCorrect = true)
        val tq2 = TestQuestion(soru = s2, userAnswer = "B", isCorrect = false)
        val tq3 = TestQuestion(soru = s3, userAnswer = null, isCorrect = null) // boş

        val testResult = TestResult(
            totalQuestions = 3,
            correctCount = 1,
            wrongCount = 1,
            emptyCount = 1,
            questions = listOf(tq1, tq2, tq3),
            totalTimeSeconds = 120L
        )

        val mockSessionManager = mockk<TestSessionManager>(relaxed = true)
        every { mockSessionManager.lastResult } returns MutableStateFlow(testResult)

        val viewModel = TestSonucViewModel(
            testSessionManager = mockSessionManager,
            soruRepository = mockSoruRepository,
            aiRepository = mockk(relaxed = true)
        )
        testScheduler.advanceUntilIdle()

        // tq1 doğru -> advanceReviewStageBatch(listOf(10L))
        coVerify(exactly = 1) { mockSoruRepository.advanceReviewStageBatch(listOf(10L)) }
        // tq2 yanlış -> regressReviewStageBatch(listOf(20L))
        coVerify(exactly = 1) { mockSoruRepository.regressReviewStageBatch(listOf(20L)) }
        // tq3 boş -> regressReviewStage(30L) ÇAĞRILMAMALI (Item 1.1 pedagojik düzeltmesi)
        coVerify(exactly = 0) { mockSoruRepository.regressReviewStage(30L) }

        // loadResult() tekrar çağrılsa bile flag sayesinde tekrar tetiklenmemeli
        viewModel.loadResult()
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { mockSoruRepository.advanceReviewStageBatch(listOf(10L)) }
        coVerify(exactly = 1) { mockSoruRepository.regressReviewStageBatch(listOf(20L)) }
        coVerify(exactly = 0) { mockSoruRepository.regressReviewStage(30L) }
    }

    @Test
    fun testOlusturViewModel_startTest_tekrarZamaniGelenSorularaOncelikVerir() = runTest {
        val now = System.currentTimeMillis()
        // due sorular (nextReviewDate geçmişte)
        val sDue1 = Soru(id = 1, imagePath = "1", examType = "TYT", subjectId = "mat", topic = "Türev", nextReviewDate = now - 10000L, isSolved = false)
        val sDue2 = Soru(id = 2, imagePath = "2", examType = "TYT", subjectId = "mat", topic = "İntegral", nextReviewDate = now - 5000L, isSolved = false)
        // non-due sorular (nextReviewDate gelecekte)
        val sOther1 = Soru(id = 3, imagePath = "3", examType = "TYT", subjectId = "mat", topic = "Limit", nextReviewDate = now + 100000L, isSolved = false)
        val sOther2 = Soru(id = 4, imagePath = "4", examType = "TYT", subjectId = "mat", topic = "Polinom", nextReviewDate = now + 100000L, isSolved = false)

        val questionsFlow = MutableStateFlow(listOf(sOther1, sDue1, sOther2, sDue2))
        every { mockSoruRepository.getAllSorular() } returns questionsFlow

        val testSessionManager = mockk<TestSessionManager>(relaxed = true)
        val viewModel = TestOlusturViewModel(
            soruRepository = mockSoruRepository,
            konuKatalog = mockKonuKatalog,
            testSessionManager = testSessionManager,
            ayarlarDataStore = mockAyarlarDataStore
        )
        backgroundScope.launch { viewModel.uiState.collect() }
        testScheduler.advanceUntilIdle()

        // Soru sayısını 2 olarak belirle
        viewModel.setQuestionCount(2)
        viewModel.startTest { }

        // Seçilen 2 soru da due olanlardan (id 1 ve 2) olmalı
        coVerify(exactly = 1) {
            testSessionManager.startSession(
                match { list ->
                    list.size == 2 && list.all { it.soru.id in listOf(1L, 2L) }
                },
                any()
            )
        }
    }

    // =========================================================================
    // 5. IstatistikViewModel Testleri (Faz 1)
    // =========================================================================

    @Test
    fun istatistikViewModel_bosVeri_varsayilanDegerlerDogru() = runTest {
        val viewModel = IstatistikViewModel(mockSoruRepository, mockKonuKatalog, mockTestSessionManager, null, null, null, mainDispatcherRule.testDispatcher)
        backgroundScope.launch { viewModel.uiState.collect() }
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(0, state.totalCount)
        assertEquals(0, state.solvedCount)
        assertEquals(0, state.unsolvedCount)
        assertEquals(0f, state.successRate, 0.01f)
        assertEquals(0, state.totalTestCount)
        assertEquals(0f, state.averageNet, 0.01f)
        assertTrue(state.recentTestResults.isEmpty())
        assertTrue(state.isEmpty)
        assertFalse(state.isLoading)
    }

    @Test
    fun istatistikViewModel_testSonuclariVeSorularBirlestirilir_metriklerHesaplanir() = runTest {
        every { mockSoruRepository.totalCount } returns flowOf(10)
        every { mockSoruRepository.solvedCount } returns flowOf(7)
        every { mockSoruRepository.unsolvedCount } returns flowOf(3)

        val t1 = TestSonucEntity(id = 1L, totalQuestions = 20, correctCount = 16, wrongCount = 4, emptyCount = 0, netCount = 15.0f, successRate = 75, totalTimeSeconds = 1200L, examType = "TYT")
        val t2 = TestSonucEntity(id = 2L, totalQuestions = 20, correctCount = 18, wrongCount = 2, emptyCount = 0, netCount = 17.5f, successRate = 88, totalTimeSeconds = 1100L, examType = "AYT")
        val t3 = TestSonucEntity(id = 3L, totalQuestions = 10, correctCount = 9, wrongCount = 1, emptyCount = 0, netCount = 8.75f, successRate = 90, totalTimeSeconds = 600L, examType = "Genel")

        every { mockSoruRepository.getAllTestResults() } returns flowOf(listOf(t1, t2, t3))

        val viewModel = IstatistikViewModel(mockSoruRepository, mockKonuKatalog, mockTestSessionManager, null, null, null, mainDispatcherRule.testDispatcher)
        backgroundScope.launch { viewModel.uiState.collect() }
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(10, state.totalCount)
        assertEquals(7, state.solvedCount)
        assertEquals(3, state.unsolvedCount)
        assertEquals(70f, state.successRate, 0.01f)
        assertEquals(3, state.totalTestCount)
        // Ortalama Net: (15.0 + 17.5 + 8.75) / 3 = 41.25 / 3 = 13.75f
        assertEquals(13.75f, state.averageNet, 0.01f)
        assertEquals(3, state.recentTestResults.size)
        assertEquals("TYT", state.recentTestResults[0].examType)
        assertFalse(state.isEmpty)
    }

    @Test
    fun istatistikViewModel_recentTestResults_enFazla10AdetDoner() = runTest {
        val testList = (1..15).map { index ->
            TestSonucEntity(
                id = index.toLong(),
                totalQuestions = 10,
                correctCount = 8,
                wrongCount = 2,
                emptyCount = 0,
                netCount = 7.5f,
                successRate = 80,
                totalTimeSeconds = 500L,
                examType = "TYT"
            )
        }
        every { mockSoruRepository.getAllTestResults() } returns flowOf(testList)

        val viewModel = IstatistikViewModel(mockSoruRepository, mockKonuKatalog, mockTestSessionManager, null, null, null, mainDispatcherRule.testDispatcher)
        backgroundScope.launch { viewModel.uiState.collect() }
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(15, state.totalTestCount)
        assertEquals(10, state.recentTestResults.size)
        assertEquals(7.5f, state.averageNet, 0.01f)
    }
}
