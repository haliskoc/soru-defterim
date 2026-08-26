package com.sorudefteri.ui.viewmodel

import android.net.Uri
import com.sorudefteri.data.backup.BackupManager
import com.sorudefteri.data.images.GorselDepo
import com.sorudefteri.data.local.AyarlarDataStore
import com.sorudefteri.data.repository.SoruRepository
import com.sorudefteri.domain.KonuKatalog
import com.sorudefteri.domain.KonuUnite
import com.sorudefteri.domain.model.AiProviderType
import com.sorudefteri.domain.model.Alan
import com.sorudefteri.domain.model.HataTipi
import com.sorudefteri.domain.model.QuestionMetadata
import com.sorudefteri.domain.model.SimilarQuestionResult
import com.sorudefteri.domain.model.SortOrder
import com.sorudefteri.domain.model.Soru
import com.sorudefteri.domain.repository.AiRepository
import com.sorudefteri.ui.ayarlar.AyarlarViewModel
import com.sorudefteri.ui.kutuphane.KutuphaneViewModel
import com.sorudefteri.ui.soruekle.SoruEklemeViewModel
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
    private lateinit var mockAyarlarDataStore: AyarlarDataStore
    private lateinit var fakeAiRepository: FakeAiRepository
    private lateinit var fakeBackupManager: FakeBackupManager
    private lateinit var mockOcrHelper: com.sorudefteri.data.ocr.OcrHelper

    @Before
    fun setUp() {
        mockSoruRepository = mockk(relaxed = true)
        mockGorselDepo = mockk(relaxed = true)
        mockKonuKatalog = mockk(relaxed = true)
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
        every { mockAyarlarDataStore.geminiModel } returns flowOf("gemini-2.5-flash")
        every { mockAyarlarDataStore.groqModel } returns flowOf("meta-llama/llama-4-scout-17b-16e-instruct")
        every { mockAyarlarDataStore.nvidiaModel } returns flowOf("meta/llama-3.1-405b-instruct")
        every { mockAyarlarDataStore.themeMode } returns flowOf(0)
        every { mockSoruRepository.getDistinctTopics(any()) } returns flowOf(listOf("Trigonometri", "Türev"))
        every { mockKonuKatalog.getUniteList(any(), any()) } returns listOf(
            KonuUnite(uniteAdi = "Fonksiyonlar", konular = listOf("Fonksiyon Grafikleri", "Bileşke"))
        )
    }

    class FakeAiRepository : AiRepository {
        var metadataResult: Result<QuestionMetadata> = Result.success(
            QuestionMetadata(examType = "TYT", subjectId = "fizik", topic = "Kaldırma Kuvveti", correctAnswer = "D")
        )
        var liveModelsResult: Result<List<Pair<String, String>>> = Result.success(
            listOf("gemini-2.5-flash" to "Gemini 2.5 Flash", "gemini-2.5-pro" to "Gemini 2.5 Pro")
        )
        var solutionResult: Result<String> = Result.success("Fake Solution")
        var similarResult: Result<SimilarQuestionResult> = Result.success(SimilarQuestionResult("", emptyMap(), "A", ""))

        override suspend fun getAiSolution(soru: Soru, sendImage: Boolean, extractedText: String?): Result<String> = solutionResult
        override suspend fun getAiSolutionFromText(dersAdi: String, konu: String, soruMetni: String, correctAnswer: String?, mistakeType: String?): Result<String> = solutionResult
        override suspend fun cleanOcrText(rawOcrText: String): Result<String> = Result.success(rawOcrText)
        override suspend fun generateSimilarQuestion(imageBytes: ByteArray?, dersAdi: String, konu: String, soruMetni: String?): Result<SimilarQuestionResult> = similarResult
        override suspend fun generateSimilarQuestion(imageBytes: ByteArray, dersAdi: String, konu: String): Result<SimilarQuestionResult> = similarResult
        override suspend fun generateQuestion(dersAdi: String, konu: String, zorluk: String, alan: String, sinavTipi: String): Result<SimilarQuestionResult> = similarResult
        override suspend fun detectQuestionMetadata(imageBytes: ByteArray): Result<QuestionMetadata> = metadataResult
        override suspend fun hasApiKey(): Boolean = true
        override suspend fun fetchLiveModels(provider: AiProviderType): Result<List<Pair<String, String>>> = liveModelsResult
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

        val tumSorularFlow = MutableStateFlow(listOf(s1, s2, s3))
        every { mockSoruRepository.getAllSorular() } returns tumSorularFlow

        val viewModel = KutuphaneViewModel(mockSoruRepository)
        backgroundScope.launch { viewModel.uiState.collect() }
        testScheduler.advanceUntilIdle()

        // 1. Başlangıç durumu (3 soru)
        assertEquals(3, viewModel.uiState.value.filteredSorular.size)

        // 2. Arama sorgusu filtresi (300ms debounce)
        viewModel.onSearchQueryChange("Trigono")
        testScheduler.advanceTimeBy(350L)
        assertEquals(1, viewModel.uiState.value.filteredSorular.size)
        assertEquals("Trigonometri", viewModel.uiState.value.filteredSorular[0].topic)

        // Arama temizle
        viewModel.onSearchQueryChange("")
        testScheduler.advanceTimeBy(350L)
        assertEquals(3, viewModel.uiState.value.filteredSorular.size)

        // 3. Sınav tipi filtresi (AYT)
        viewModel.onExamTypeSelected("AYT")
        testScheduler.advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.filteredSorular.size)
        assertEquals("Optik", viewModel.uiState.value.filteredSorular[0].topic)

        // 4. Sadece favoriler
        viewModel.resetFilters()
        viewModel.onOnlyFavoritesToggle(true)
        testScheduler.advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.filteredSorular.size)
        assertTrue(viewModel.uiState.value.filteredSorular[0].isFavorite)

        // 5. Sadece çözülmemişler
        viewModel.resetFilters()
        viewModel.onOnlyUnsolvedToggle(true)
        testScheduler.advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.filteredSorular.size)
    }

    @Test
    fun kutuphaneViewModel_topluSecimVeAksiyonlar() = runTest {
        val s1 = Soru(id = 1, imagePath = "1", examType = "TYT", subjectId = "mat", topic = "A")
        val s2 = Soru(id = 2, imagePath = "2", examType = "TYT", subjectId = "mat", topic = "B")
        every { mockSoruRepository.getAllSorular() } returns flowOf(listOf(s1, s2))

        val viewModel = KutuphaneViewModel(mockSoruRepository)
        backgroundScope.launch { viewModel.uiState.collect() }
        testScheduler.advanceUntilIdle()

        // Tekli seçim
        viewModel.toggleSelectSoru(1L)
        assertTrue(viewModel.uiState.value.isSelectionMode)
        assertEquals(setOf(1L), viewModel.uiState.value.selectedSoruIds)

        // Tümünü seç
        viewModel.selectAll()
        assertEquals(setOf(1L, 2L), viewModel.uiState.value.selectedSoruIds)
        assertTrue(viewModel.uiState.value.isAllSelected)

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
            backupManager = fakeBackupManager
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

        // Canlı model listesi çekme (Başarılı)
        fakeAiRepository.liveModelsResult = Result.success(
            listOf("gemini-2.5-flash" to "Gemini 2.5 Flash", "gemini-2.5-pro" to "Gemini 2.5 Pro")
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
            backupManager = fakeBackupManager
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
}
