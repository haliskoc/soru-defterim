package com.sorudefteri.ui.detay

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sorudefteri.data.ai.AiRepository
import com.sorudefteri.data.repository.SoruRepository
import com.sorudefteri.domain.model.Soru
import com.sorudefteri.domain.model.toDomain
import com.sorudefteri.domain.model.toEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class DetayTab {
    SORU,
    COZUM
}

data class SoruDetayUiState(
    val soru: Soru? = null,
    val isLoading: Boolean = true,
    val isAiLoading: Boolean = false,
    val aiError: String? = null,
    val isApiKeyMissing: Boolean = false,
    val activeTab: DetayTab = DetayTab.SORU,
    val showDeleteDialog: Boolean = false,
    val showEditNoteDialog: Boolean = false,
    val zoomImagePath: String? = null,
    val zoomImageTitle: String? = null
)

@HiltViewModel
class SoruDetayViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val soruRepository: SoruRepository,
    private val aiRepository: AiRepository
) : ViewModel() {

    private val soruId: Long = savedStateHandle.get<Long>("soruId")
        ?: savedStateHandle.get<String>("soruId")?.toLongOrNull()
        ?: 0L

    private val _uiState = MutableStateFlow(SoruDetayUiState())
    val uiState: StateFlow<SoruDetayUiState> = _uiState.asStateFlow()

    init {
        loadSoru()
    }

    private fun loadSoru() {
        viewModelScope.launch {
            if (soruId <= 0L) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }

            soruRepository.getSoruById(soruId)
                .catch {
                    _uiState.update { state -> state.copy(isLoading = false) }
                }
                .collect { entity ->
                    _uiState.update { state ->
                        state.copy(
                            soru = entity?.toDomain(),
                            isLoading = false
                        )
                    }
                }
        }
    }

    fun setActiveTab(tab: DetayTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun toggleSolved() {
        val currentSoru = _uiState.value.soru ?: return
        viewModelScope.launch {
            val newSolvedState = !currentSoru.isSolved
            val solvedAt = if (newSolvedState) System.currentTimeMillis() else null
            val updated = currentSoru.copy(
                isSolved = newSolvedState,
                solvedAt = solvedAt,
                updatedAt = System.currentTimeMillis()
            )
            soruRepository.updateSoru(updated.toEntity())
        }
    }

    fun updateNote(newNote: String) {
        val currentSoru = _uiState.value.soru ?: return
        viewModelScope.launch {
            val updated = currentSoru.copy(
                note = newNote.trim().ifEmpty { null },
                updatedAt = System.currentTimeMillis()
            )
            soruRepository.updateSoru(updated.toEntity())
            _uiState.update { it.copy(showEditNoteDialog = false) }
        }
    }

    fun updateSolutionImage(imagePath: String?) {
        val currentSoru = _uiState.value.soru ?: return
        viewModelScope.launch {
            val updated = currentSoru.copy(
                solutionImagePath = imagePath,
                updatedAt = System.currentTimeMillis()
            )
            soruRepository.updateSoru(updated.toEntity())
            if (imagePath != null) {
                _uiState.update { it.copy(activeTab = DetayTab.COZUM) }
            }
        }
    }

    fun deleteSoru(onSuccess: () -> Unit) {
        val currentSoru = _uiState.value.soru ?: return
        viewModelScope.launch {
            soruRepository.deleteSoru(currentSoru.toEntity())
            _uiState.update { it.copy(showDeleteDialog = false) }
            onSuccess()
        }
    }

    fun requestAiSolution() {
        val currentSoru = _uiState.value.soru ?: return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isAiLoading = true,
                    aiError = null,
                    isApiKeyMissing = false
                )
            }

            val hasKey = aiRepository.hasApiKey()
            if (!hasKey) {
                _uiState.update {
                    it.copy(
                        isAiLoading = false,
                        isApiKeyMissing = true,
                        aiError = "API anahtarı bulunamadı. Lütfen Ayarlar sayfasından Gemini veya Groq API anahtarınızı girin."
                    )
                }
                return@launch
            }

            val result = aiRepository.getAiSolution(currentSoru)
            result.onSuccess { explanation ->
                val updated = currentSoru.copy(
                    aiExplanation = explanation,
                    updatedAt = System.currentTimeMillis()
                )
                soruRepository.updateSoru(updated.toEntity())
                _uiState.update {
                    it.copy(
                        isAiLoading = false,
                        aiError = null
                    )
                }
            }.onFailure { throwable ->
                val errorMessage = throwable.localizedMessage
                    ?: "Yapay zeka çözümü alınırken bir hata oluştu. Lütfen bağlantınızı veya API anahtarınızı kontrol edin."
                _uiState.update {
                    it.copy(
                        isAiLoading = false,
                        aiError = errorMessage
                    )
                }
            }
        }
    }

    fun clearAiError() {
        _uiState.update { it.copy(aiError = null, isApiKeyMissing = false) }
    }

    fun setShowDeleteDialog(show: Boolean) {
        _uiState.update { it.copy(showDeleteDialog = show) }
    }

    fun setShowEditNoteDialog(show: Boolean) {
        _uiState.update { it.copy(showEditNoteDialog = show) }
    }

    fun openZoomImage(imagePath: String?, title: String?) {
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
