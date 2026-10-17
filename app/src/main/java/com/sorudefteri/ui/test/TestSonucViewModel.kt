package com.sorudefteri.ui.test

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sorudefteri.data.ai.AiRepository
import com.sorudefteri.domain.model.Soru
import com.sorudefteri.domain.model.TestResult
import com.sorudefteri.domain.repository.SoruRepository
import com.sorudefteri.domain.repository.TestSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Test Sonuç Ekranı UI Durumu
 */
data class TestSonucUiState(
    val testResult: TestResult? = null,
    val isMarkingSolved: Boolean = false,
    val hasMarkedSolved: Boolean = false,
    val markedCount: Int = 0,
    val aiLoadingIds: Set<Long> = emptySet(),
    val aiSolutions: Map<Long, String> = emptyMap(),
    val aiErrors: Map<Long, String> = emptyMap(),
    val expandedSolutionIds: Set<Long> = emptySet(),
    val zoomImagePath: String? = null,
    val zoomImageTitle: String? = null
)

@HiltViewModel
class TestSonucViewModel @Inject constructor(
    private val testSessionManager: TestSessionManager,
    private val soruRepository: SoruRepository,
    private val aiRepository: AiRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TestSonucUiState())
    val uiState: StateFlow<TestSonucUiState> = _uiState.asStateFlow()

    private var hasProcessedReviewStages = false

    init {
        loadResult()
    }

    fun loadResult() {
        val result = testSessionManager.lastResult.value
        val initialSolutions = mutableMapOf<Long, String>()
        result?.questions?.forEach { tq ->
            tq.soru.aiExplanation?.let { exp ->
                if (exp.isNotBlank()) {
                    initialSolutions[tq.soru.id] = exp
                }
            }
        }
        _uiState.update {
            it.copy(
                testResult = result,
                aiSolutions = initialSolutions
            )
        }

        if (!hasProcessedReviewStages && result != null) {
            hasProcessedReviewStages = true
            viewModelScope.launch {
                result.questions.forEach { tq ->
                    when (tq.isCorrect) {
                        true -> soruRepository.advanceReviewStage(tq.soru.id)
                        false -> soruRepository.regressReviewStage(tq.soru.id)
                        null -> {
                            // Boş bırakılan veya cevap anahtarı olmayan soruların aşaması korunur
                        }
                    }
                }
            }
        }
    }

    /**
     * Testte doğru yapılan soruları arşivde 'Çözüldü' olarak işaretler
     */
    fun markCorrectAsSolved() {
        val result = _uiState.value.testResult ?: return
        val correctQuestions = result.questions.filter { it.isCorrect == true }
        if (correctQuestions.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isMarkingSolved = true) }
            correctQuestions.forEach { tq ->
                soruRepository.cozumDurumuDegistir(tq.soru.id, true)
            }
            _uiState.update {
                it.copy(
                    isMarkingSolved = false,
                    hasMarkedSolved = true,
                    markedCount = correctQuestions.size
                )
            }
        }
    }

    /**
     * Yanlış veya boş soru için tek tıkla AI çözümü ister
     */
    fun requestAiSolution(soru: Soru) {
        val soruId = soru.id
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    aiLoadingIds = state.aiLoadingIds + soruId,
                    aiErrors = state.aiErrors - soruId
                )
            }

            val hasKey = aiRepository.hasApiKey()
            if (!hasKey) {
                _uiState.update { state ->
                    state.copy(
                        aiLoadingIds = state.aiLoadingIds - soruId,
                        aiErrors = state.aiErrors + (soruId to "API anahtarı bulunamadı. Lütfen Ayarlar sayfasından API anahtarınızı girin.")
                    )
                }
                return@launch
            }

            val result = aiRepository.getAiSolution(soru)
            result.onSuccess { explanation ->
                // Veritabanına ve oturuma kaydet
                soruRepository.aiCozumuKaydet(soruId, explanation)
                testSessionManager.updateQuestionAiExplanation(soruId, explanation)

                _uiState.update { state ->
                    state.copy(
                        aiLoadingIds = state.aiLoadingIds - soruId,
                        aiSolutions = state.aiSolutions + (soruId to explanation),
                        expandedSolutionIds = state.expandedSolutionIds + soruId,
                        aiErrors = state.aiErrors - soruId
                    )
                }
            }.onFailure { throwable ->
                val error = throwable.localizedMessage ?: "Yapay zeka çözümü alınamadı. Bağlantınızı kontrol edin."
                _uiState.update { state ->
                    state.copy(
                        aiLoadingIds = state.aiLoadingIds - soruId,
                        aiErrors = state.aiErrors + (soruId to error)
                    )
                }
            }
        }
    }

    fun toggleSolutionExpanded(soruId: Long) {
        _uiState.update { state ->
            val newExpanded = if (state.expandedSolutionIds.contains(soruId)) {
                state.expandedSolutionIds - soruId
            } else {
                state.expandedSolutionIds + soruId
            }
            state.copy(expandedSolutionIds = newExpanded)
        }
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
}
