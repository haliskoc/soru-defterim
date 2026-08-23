package com.sorudefteri.ui.test

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sorudefteri.data.local.dao.TestSonucDao
import com.sorudefteri.data.local.entity.TestSonucEntity
import com.sorudefteri.domain.model.TestQuestion
import com.sorudefteri.domain.repository.TestSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Test Çözme Ekranının UI Durumu
 */
data class TestCozUiState(
    val questions: List<TestQuestion> = emptyList(),
    val currentIndex: Int = 0,
    val elapsedSeconds: Long = 0L,
    val questionTimes: Map<Int, Long> = emptyMap(),
    val userAnswers: Map<Int, String?> = emptyMap(),
    val showFinishDialog: Boolean = false,
    val showExitDialog: Boolean = false,
    val zoomImagePath: String? = null,
    val zoomImageTitle: String? = null,
    val isFinished: Boolean = false
) {
    val currentQuestion: TestQuestion?
        get() = questions.getOrNull(currentIndex)

    val currentAnswer: String?
        get() = userAnswers[currentIndex]

    val answeredCount: Int
        get() = userAnswers.values.count { !it.isNullOrBlank() }

    val emptyCount: Int
        get() = (questions.size - answeredCount).coerceAtLeast(0)

    val progress: Float
        get() = if (questions.isNotEmpty()) (currentIndex + 1).toFloat() / questions.size.toFloat() else 0f

    val isFirstQuestion: Boolean get() = currentIndex == 0
    val isLastQuestion: Boolean get() = questions.isNotEmpty() && currentIndex == questions.size - 1
}

@HiltViewModel
class TestCozViewModel @Inject constructor(
    private val testSessionManager: TestSessionManager,
    private val testSonucDao: TestSonucDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(TestCozUiState())
    val uiState: StateFlow<TestCozUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null

    init {
        initSession()
        startTimer()
    }

    private fun initSession() {
        val questions = testSessionManager.activeQuestions.value
        val initialAnswers = questions.mapIndexed { index, q -> index to q.userAnswer }.toMap()
        _uiState.update {
            it.copy(
                questions = questions,
                userAnswers = initialAnswers
            )
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000L)
                _uiState.update { state ->
                    val newTotal = state.elapsedSeconds + 1
                    val currentQTime = (state.questionTimes[state.currentIndex] ?: 0L) + 1
                    val newQTimes = state.questionTimes.toMutableMap().apply {
                        put(state.currentIndex, currentQTime)
                    }
                    state.copy(
                        elapsedSeconds = newTotal,
                        questionTimes = newQTimes
                    )
                }
            }
        }
    }

    fun selectAnswer(option: String) {
        val currentIdx = _uiState.value.currentIndex
        val existingAnswer = _uiState.value.userAnswers[currentIdx]
        val newAnswer = if (existingAnswer.equals(option, ignoreCase = true)) null else option

        _uiState.update { state ->
            val updatedAnswers = state.userAnswers.toMutableMap().apply {
                put(currentIdx, newAnswer)
            }
            state.copy(userAnswers = updatedAnswers)
        }

        testSessionManager.updateAnswer(currentIdx, newAnswer)
    }

    fun clearAnswer() {
        val currentIdx = _uiState.value.currentIndex
        _uiState.update { state ->
            val updatedAnswers = state.userAnswers.toMutableMap().apply {
                put(currentIdx, null)
            }
            state.copy(userAnswers = updatedAnswers)
        }
        testSessionManager.updateAnswer(currentIdx, null)
    }

    fun goToQuestion(index: Int) {
        if (index in _uiState.value.questions.indices) {
            _uiState.update { it.copy(currentIndex = index) }
        }
    }

    fun nextQuestion() {
        val state = _uiState.value
        if (state.currentIndex < state.questions.size - 1) {
            _uiState.update { it.copy(currentIndex = state.currentIndex + 1) }
        } else {
            setShowFinishDialog(true)
        }
    }

    fun previousQuestion() {
        val state = _uiState.value
        if (state.currentIndex > 0) {
            _uiState.update { it.copy(currentIndex = state.currentIndex - 1) }
        }
    }

    fun setShowFinishDialog(show: Boolean) {
        _uiState.update { it.copy(showFinishDialog = show) }
    }

    fun setShowExitDialog(show: Boolean) {
        _uiState.update { it.copy(showExitDialog = show) }
    }

    fun openZoomImage(imagePath: String, title: String) {
        _uiState.update {
            it.copy(
                zoomImagePath = imagePath,
                zoomImageTitle = title
            )
        }
    }

    fun closeZoomImage() {
        _uiState.update {
            it.copy(
                zoomImagePath = null,
                zoomImageTitle = null
            )
        }
    }

    fun finishTest(onFinished: () -> Unit) {
        timerJob?.cancel()
        val state = _uiState.value
        val result = testSessionManager.finishSession(
            totalTimeSeconds = state.elapsedSeconds,
            questionTimes = state.questionTimes
        )
        val filter = testSessionManager.currentFilter.value

        viewModelScope.launch {
            try {
                testSonucDao.insertTestResult(
                    TestSonucEntity(
                        totalQuestions = result.totalQuestions,
                        correctCount = result.correctCount,
                        wrongCount = result.wrongCount,
                        emptyCount = result.emptyCount,
                        netCount = result.netCount,
                        successRate = result.successRate,
                        totalTimeSeconds = result.totalTimeSeconds,
                        examType = filter.examType ?: "TÜMÜ",
                        completedAt = result.completedAt
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            _uiState.update { it.copy(isFinished = true, showFinishDialog = false) }
            onFinished()
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}
