package com.sorudefteri.ui.reels

import com.sorudefteri.domain.model.ReelsItem
import com.sorudefteri.domain.model.Soru
import com.sorudefteri.domain.repository.SoruRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReelsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val soruRepository: SoruRepository = mockk(relaxed = true)

    private fun createTestSoru(
        id: Long = 1L,
        examType: String = "TYT",
        subjectId: String = "matematik",
        topic: String = "Temel Kavramlar",
        correctAnswer: String? = "A",
        isFavorite: Boolean = false,
        isSolved: Boolean = false
    ) = Soru(
        id = id,
        imagePath = "/data/user/0/com.sorudefteri/files/images/$id.jpg",
        examType = examType,
        subjectId = subjectId,
        topic = topic,
        correctAnswer = correctAnswer,
        isFavorite = isFavorite,
        isSolved = isSolved
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `when questions loaded, feed contains questions and flashcards`() = runTest(testDispatcher) {
        val testSoru1 = createTestSoru(id = 1L, examType = "TYT", subjectId = "matematik", topic = "Fonksiyonlar", correctAnswer = "C")
        val testSoru2 = createTestSoru(id = 2L, examType = "AYT", subjectId = "fizik", topic = "Dinamik", correctAnswer = "A")
        val testSoru3 = createTestSoru(id = 3L, examType = "TYT", subjectId = "kimya", topic = "Mol", correctAnswer = "B")

        every { soruRepository.getAllSorular() } returns kotlinx.coroutines.flow.flowOf(listOf(testSoru1, testSoru2, testSoru3))
        coEvery { soruRepository.getAllSorularOnce() } returns listOf(testSoru1, testSoru2, testSoru3)

        val viewModel = ReelsViewModel(soruRepository, testDispatcher)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.items.isNotEmpty())

        val soruItems = state.items.filterIsInstance<ReelsItem.SoruItem>()
        val flashcardItems = state.items.filterIsInstance<ReelsItem.FlashcardItem>()

        assertEquals(3, soruItems.size)
        // With 3 questions, at least 1 flashcard is interleaved
        assertTrue(flashcardItems.isNotEmpty())
    }

    @Test
    fun `answerQuestion with correct answer advances review and marks solved`() = runTest(testDispatcher) {
        val testSoru = createTestSoru(id = 42L, examType = "TYT", subjectId = "matematik", topic = "Kümeler", correctAnswer = "B")
        coEvery { soruRepository.getAllSorularOnce() } returns listOf(testSoru)

        val viewModel = ReelsViewModel(soruRepository, testDispatcher)
        advanceUntilIdle()

        viewModel.answerQuestion(testSoru, "B")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("B", state.selectedOptions[42L])
        assertEquals(true, state.answeredStatus[42L])
        assertNotNull(state.feedbackMessage)
        assertTrue(state.feedbackMessage!!.contains("Tebrikler"))

        coVerify { soruRepository.advanceReviewStage(42L) }
        coVerify { soruRepository.updateSolvedStatus(42L, true) }
    }

    @Test
    fun `answerQuestion with incorrect answer regresses review`() = runTest(testDispatcher) {
        val testSoru = createTestSoru(id = 42L, examType = "TYT", subjectId = "matematik", topic = "Kümeler", correctAnswer = "B")
        coEvery { soruRepository.getAllSorularOnce() } returns listOf(testSoru)

        val viewModel = ReelsViewModel(soruRepository, testDispatcher)
        advanceUntilIdle()

        viewModel.answerQuestion(testSoru, "E")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("E", state.selectedOptions[42L])
        assertEquals(false, state.answeredStatus[42L])
        assertTrue(state.feedbackMessage!!.contains("Yanlış"))

        coVerify { soruRepository.regressReviewStage(42L) }
    }

    @Test
    fun `toggleFavorite updates repository and local state`() = runTest(testDispatcher) {
        val testSoru = createTestSoru(id = 99L, isFavorite = false)
        coEvery { soruRepository.getAllSorularOnce() } returns listOf(testSoru)

        val viewModel = ReelsViewModel(soruRepository, testDispatcher)
        advanceUntilIdle()

        viewModel.toggleFavorite(testSoru)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.favoritedQuestionIds.contains(99L))
        coVerify { soruRepository.toggleFavorite(99L, true) }

        // Toggle again
        viewModel.toggleFavorite(testSoru)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.favoritedQuestionIds.contains(99L))
        coVerify { soruRepository.toggleFavorite(99L, false) }
    }
}
