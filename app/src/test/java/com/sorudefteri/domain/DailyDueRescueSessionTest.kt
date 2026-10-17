package com.sorudefteri.domain

import com.sorudefteri.data.local.AyarlarDataStore
import com.sorudefteri.data.repository.SoruRepository
import com.sorudefteri.domain.model.Alan
import com.sorudefteri.domain.model.Soru
import com.sorudefteri.domain.model.TestFiltre
import com.sorudefteri.domain.model.TestQuestion
import com.sorudefteri.domain.repository.TestSessionManager
import com.sorudefteri.ui.kutuphane.KutuphaneViewModel
import com.sorudefteri.ui.test.TestOlusturViewModel
import com.sorudefteri.ui.viewmodel.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Faz 2.5: Günün Kurtarma Paketi (Daily Due Queue & Test Session) Birim Testleri.
 * Aralıklı tekrar (Spaced Repetition) vakti gelmiş soruların sorgulanması,
 * TestSessionManager üzerinden oturum başlatılması ve ViewModel seviyesinde akış kontrolünü test eder.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DailyDueRescueSessionTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var mockSoruRepository: SoruRepository
    private lateinit var mockAyarlarDataStore: AyarlarDataStore
    private lateinit var mockKonuKatalog: KonuKatalog
    private lateinit var testSessionManager: TestSessionManager

    @Before
    fun setUp() {
        mockSoruRepository = mockk(relaxed = true)
        mockAyarlarDataStore = mockk(relaxed = true)
        mockKonuKatalog = mockk(relaxed = true)
        testSessionManager = TestSessionManager()

        every { mockAyarlarDataStore.currentAlan } returns flowOf(Alan.SAYISAL)
        every { mockSoruRepository.getAllSorular() } returns flowOf(emptyList())
    }

    private fun createDummySoru(
        id: Long,
        nextReviewDate: Long? = null,
        isSolved: Boolean = false,
        correctAnswer: String = "C"
    ): Soru {
        return Soru(
            id = id,
            imagePath = "/images/soru_$id.jpg",
            correctAnswer = correctAnswer,
            examType = "TYT",
            subjectId = "tyt_matematik",
            topic = "Problemler",
            isSolved = isSolved,
            nextReviewDate = nextReviewDate
        )
    }

    @Test
    fun testSessionManager_startSessionWithDueQuestions_oturumDogruBaslatilir() {
        val now = System.currentTimeMillis()
        val s1 = createDummySoru(1L, nextReviewDate = now - 1000L, correctAnswer = "A")
        val s2 = createDummySoru(2L, nextReviewDate = now - 5000L, correctAnswer = "B")
        val s3 = createDummySoru(3L, nextReviewDate = now - 3000L, correctAnswer = "C")

        val dueQuestions = listOf(s1, s2, s3)
        val testQuestions = dueQuestions.map { TestQuestion(soru = it) }
        val filtre = TestFiltre(
            examType = null,
            selectedSubjects = emptySet(),
            selectedTopics = emptySet(),
            selectedMistakeTypes = emptySet(),
            onlyUnsolved = false,
            questionCount = testQuestions.size
        )

        testSessionManager.startSession(testQuestions, filtre)

        assertEquals(3, testSessionManager.activeQuestions.value.size)
        assertEquals(3, testSessionManager.currentFilter.value.questionCount)
        assertEquals(1L, testSessionManager.activeQuestions.value[0].soru.id)
        assertEquals(2L, testSessionManager.activeQuestions.value[1].soru.id)
        assertEquals(3L, testSessionManager.activeQuestions.value[2].soru.id)
    }

    @Test
    fun testOlusturViewModel_startDueReviewTest_sorularVarsaOturumuBaslatirVeNavigasyonuTetikler() = runTest {
        val now = System.currentTimeMillis()
        val dueSoru1 = createDummySoru(10L, nextReviewDate = now - 2000L)
        val dueSoru2 = createDummySoru(20L, nextReviewDate = now - 1000L)
        val dueList = listOf(dueSoru1, dueSoru2)

        coEvery { mockSoruRepository.getDueReviewSorularList(any()) } returns dueList

        val mockSession = mockk<TestSessionManager>(relaxed = true)
        val viewModel = TestOlusturViewModel(
            soruRepository = mockSoruRepository,
            konuKatalog = mockKonuKatalog,
            testSessionManager = mockSession,
            ayarlarDataStore = mockAyarlarDataStore
        )

        var navigationTriggered = false
        viewModel.startDueReviewTest {
            navigationTriggered = true
        }
        testScheduler.advanceUntilIdle()

        assertTrue("Günün Kurtarma Paketi ile test başlatıldığında onSuccess çağrılmalı", navigationTriggered)
        coVerify(exactly = 1) { mockSoruRepository.getDueReviewSorularList(any()) }
        coVerify(exactly = 1) {
            mockSession.startSession(
                match { list -> list.size == 2 && list[0].soru.id == 10L && list[1].soru.id == 20L },
                match { filtre -> filtre.questionCount == 2 }
            )
        }
    }

    @Test
    fun testOlusturViewModel_startDueReviewTest_soruYoksaOturumBaslatmaz() = runTest {
        coEvery { mockSoruRepository.getDueReviewSorularList(any()) } returns emptyList()

        val mockSession = mockk<TestSessionManager>(relaxed = true)
        val viewModel = TestOlusturViewModel(
            soruRepository = mockSoruRepository,
            konuKatalog = mockKonuKatalog,
            testSessionManager = mockSession,
            ayarlarDataStore = mockAyarlarDataStore
        )

        var navigationTriggered = false
        viewModel.startDueReviewTest {
            navigationTriggered = true
        }
        testScheduler.advanceUntilIdle()

        assertFalse("Due soru yoksa test oturumu başlatılmamalı", navigationTriggered)
        coVerify(exactly = 0) { mockSession.startSession(any(), any()) }
    }

    @Test
    fun kutuphaneViewModel_startDueReviewTest_sorularVarsaOturumuBaslatirVeNavigasyonuTetikler() = runTest {
        val now = System.currentTimeMillis()
        val dueSoru1 = createDummySoru(100L, nextReviewDate = now - 5000L)
        val dueSoru2 = createDummySoru(200L, nextReviewDate = now - 3000L)
        val dueList = listOf(dueSoru1, dueSoru2)

        coEvery { mockSoruRepository.getDueReviewSorularList(any()) } returns dueList

        val mockSession = mockk<TestSessionManager>(relaxed = true)
        val viewModel = KutuphaneViewModel(
            soruRepository = mockSoruRepository,
            ayarlarDataStore = mockAyarlarDataStore,
            testSessionManager = mockSession
        )

        var navigationTriggered = false
        viewModel.startDueReviewTest {
            navigationTriggered = true
        }
        testScheduler.advanceUntilIdle()

        assertTrue("Kütüphane ekranından Günün Kurtarma Paketi başlatıldığında onSuccess çağrılmalı", navigationTriggered)
        coVerify(exactly = 1) { mockSoruRepository.getDueReviewSorularList(any()) }
        coVerify(exactly = 1) {
            mockSession.startSession(
                match { list -> list.size == 2 && list[0].soru.id == 100L && list[1].soru.id == 200L },
                match { filtre -> filtre.questionCount == 2 }
            )
        }
    }

    @Test
    fun soru_isDueForReview_hesaplamasiDogruCalisir() {
        val now = System.currentTimeMillis()

        // 1. nextReviewDate geçmişte olan çözülmemiş soru -> DUE
        val dueSoru = createDummySoru(1L, nextReviewDate = now - 1000L, isSolved = false)
        assertTrue(dueSoru.isDueForReview)

        // 2. nextReviewDate gelecekte olan soru -> NOT DUE
        val futureSoru = createDummySoru(2L, nextReviewDate = now + 100000L, isSolved = false)
        assertFalse(futureSoru.isDueForReview)

        // 3. nextReviewDate null ve çözülmemiş soru -> DUE (varsayılan olarak ilk tekrar)
        val unscheduledUnsolved = createDummySoru(3L, nextReviewDate = null, isSolved = false)
        assertTrue(unscheduledUnsolved.isDueForReview)

        // 4. nextReviewDate null ve çözülmüş soru -> NOT DUE
        val solvedNoReview = createDummySoru(4L, nextReviewDate = null, isSolved = true)
        assertFalse(solvedNoReview.isDueForReview)
    }
}
