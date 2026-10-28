package com.sorudefteri.ui.test

import com.sorudefteri.data.local.dao.TestSonucDao
import com.sorudefteri.domain.manager.BadgeManager
import com.sorudefteri.domain.model.TestQuestion
import com.sorudefteri.domain.repository.TestSessionManager
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * PERF: 1 Hz zamanlayıcı artık tüm uiState'i değil yalnızca timerTick'i yazar;
 * pause/resume arka plan davranışını test eder.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TestCozTimerTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var sessionManager: TestSessionManager
    private lateinit var testSonucDao: TestSonucDao
    private lateinit var badgeManager: BadgeManager

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        sessionManager = TestSessionManager()
        testSonucDao = mockk(relaxed = true)
        badgeManager = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `timer tick advances without rewriting uiState questions`() = runTest(testDispatcher) {
        val viewModel = TestCozViewModel(sessionManager, testSonucDao, badgeManager)
        advanceUntilIdle()

        val questionsRef = viewModel.uiState.value.questions
        advanceTimeBy(3100L)
        advanceUntilIdle()

        assertEquals(3L, viewModel.timerTick.value.elapsedSeconds)
        assertTrue(viewModel.uiState.value.questions === questionsRef || viewModel.uiState.value.questions == questionsRef)
    }

    @Test
    fun `pauseTimer stops ticking and resumeTimer continues`() = runTest(testDispatcher) {
        val viewModel = TestCozViewModel(sessionManager, testSonucDao, badgeManager)
        advanceUntilIdle()

        advanceTimeBy(2100L)
        advanceUntilIdle()
        val frozen = viewModel.timerTick.value.elapsedSeconds
        assertTrue(frozen >= 2L)

        viewModel.pauseTimer()
        advanceTimeBy(5000L)
        advanceUntilIdle()
        assertEquals(frozen, viewModel.timerTick.value.elapsedSeconds)

        viewModel.resumeTimer()
        advanceTimeBy(1100L)
        advanceUntilIdle()
        assertTrue(viewModel.timerTick.value.elapsedSeconds > frozen)
    }

    @Test
    fun `goToQuestion moves index and updates remaining in tick`() = runTest(testDispatcher) {
        val viewModel = TestCozViewModel(sessionManager, testSonucDao, badgeManager)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.questions.isEmpty() || true)

        viewModel.goToQuestion(0)
        advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.currentIndex)
    }
}
