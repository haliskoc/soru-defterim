package com.sorudefteri.ui.detay

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sorudefteri.data.ai.AiRepository
import com.sorudefteri.data.images.GorselDepo
import com.sorudefteri.data.ocr.OcrHelper
import com.sorudefteri.domain.model.SimilarQuestionResult
import com.sorudefteri.domain.model.Soru
import com.sorudefteri.domain.repository.SoruRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
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
    val zoomImageTitle: String? = null,
    val showDrawingDialog: Boolean = false,
    val isGeneratingSimilar: Boolean = false,
    val similarQuestion: SimilarQuestionResult? = null,
    val similarQuestionError: String? = null,
    val showSimilarQuestionDialog: Boolean = false,
    val userSelectedSimilarOption: String? = null,
    val isSimilarAnswerRevealed: Boolean = false,
    // Koşullu OCR & AI İletim Alanları
    val isOcrLoading: Boolean = false,
    val extractedOcrText: String? = null,
    val sendImageToAi: Boolean = true,
    val isCleaningOcr: Boolean = false,
    val ocrMessage: String? = null
)

@HiltViewModel
class SoruDetayViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val soruRepository: SoruRepository,
    private val aiRepository: AiRepository,
    private val gorselDepo: GorselDepo,
    private val ocrHelper: OcrHelper
) : ViewModel() {

    private val soruId: Long = savedStateHandle.get<Long>("soruId")
        ?: savedStateHandle.get<String>("soruId")?.toLongOrNull()
        ?: 0L

    private val _uiState = MutableStateFlow(SoruDetayUiState())
    val uiState: StateFlow<SoruDetayUiState> = _uiState.asStateFlow()

    init {
        loadSoru()
        listenCameraCapture()
    }

    private fun listenCameraCapture() {
        viewModelScope.launch {
            savedStateHandle.getStateFlow<String?>("captured_image_path", null).collectLatest { path ->
                if (!path.isNullOrBlank()) {
                    val isSolution = savedStateHandle.get<Boolean>("is_solution_image") ?: true
                    if (isSolution) {
                        updateSolutionImage(path)
                        savedStateHandle.remove<String>("captured_image_path")
                        savedStateHandle.remove<Boolean>("is_solution_image")
                    }
                }
            }
        }
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
                .collectLatest { soru ->
                    _uiState.update { state ->
                        state.copy(
                            soru = soru,
                            isLoading = false
                        )
                    }
                }
        }
    }

    fun toggleFavorite() {
        val current = _uiState.value.soru ?: return
        viewModelScope.launch {
            soruRepository.toggleFavorite(current.id, !current.isFavorite)
        }
    }

    fun advanceReviewStage() {
        val current = _uiState.value.soru ?: return
        viewModelScope.launch {
            soruRepository.advanceReviewStage(current.id)
        }
    }

    fun setActiveTab(tab: DetayTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun toggleCozumDurumu() {
        val currentSoru = _uiState.value.soru ?: return
        viewModelScope.launch {
            val newStatus = !currentSoru.isSolved
            val now = System.currentTimeMillis()
            val updated = currentSoru.copy(
                isSolved = newStatus,
                solvedAt = if (newStatus) now else null,
                updatedAt = now
            )
            soruRepository.soruGuncelle(updated)
        }
    }

    fun updateNote(newNote: String) {
        val currentSoru = _uiState.value.soru ?: return
        viewModelScope.launch {
            val updated = currentSoru.copy(
                note = newNote.trim().ifEmpty { null },
                updatedAt = System.currentTimeMillis()
            )
            soruRepository.soruGuncelle(updated)
            _uiState.update { it.copy(showEditNoteDialog = false) }
        }
    }

    fun updateSolutionImage(imagePathOrUri: String?) {
        val currentSoru = _uiState.value.soru ?: return
        if (imagePathOrUri == null) {
            viewModelScope.launch {
                val updated = currentSoru.copy(
                    solutionImagePath = null,
                    updatedAt = System.currentTimeMillis()
                )
                soruRepository.soruGuncelle(updated)
            }
            return
        }

        viewModelScope.launch {
            val permanentPath = try {
                if (imagePathOrUri.startsWith("content://") || imagePathOrUri.startsWith("file://")) {
                    val uri = Uri.parse(imagePathOrUri)
                    gorselDepo.saveImageFromUri(uri, isSolution = true)
                } else if (File(imagePathOrUri).exists()) {
                    gorselDepo.kaydetDosyadan(File(imagePathOrUri), isSolution = true)
                } else {
                    imagePathOrUri
                }
            } catch (e: Exception) {
                imagePathOrUri
            }

            val updated = currentSoru.copy(
                solutionImagePath = permanentPath,
                updatedAt = System.currentTimeMillis()
            )
            soruRepository.soruGuncelle(updated)
            _uiState.update { it.copy(activeTab = DetayTab.COZUM) }
        }
    }

    fun deleteSoru(onSuccess: () -> Unit) {
        val currentSoru = _uiState.value.soru ?: return
        viewModelScope.launch {
            soruRepository.soruSil(currentSoru)
            _uiState.update { it.copy(showDeleteDialog = false) }
            onSuccess()
        }
    }

    /**
     * Koşullu OCR Metin Ayıklama Servisi
     */
    fun performOcrOnQuestionImage() {
        val currentSoru = _uiState.value.soru ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isOcrLoading = true, ocrMessage = null) }
            val result = ocrHelper.recognizeText(currentSoru.imagePath)
            result.onSuccess { text ->
                _uiState.update {
                    it.copy(
                        isOcrLoading = false,
                        extractedOcrText = text,
                        ocrMessage = if (text.isNotBlank()) "Metin başarıyla ayıklandı ✓" else "Görselde okunabilir metin bulunamadı."
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        isOcrLoading = false,
                        ocrMessage = "OCR hatası: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun updateExtractedOcrText(newText: String) {
        _uiState.update { it.copy(extractedOcrText = newText) }
    }

    fun setSendImageToAi(sendImage: Boolean) {
        _uiState.update { it.copy(sendImageToAi = sendImage) }
    }

    /**
     * OCR ile okunan metni AI ile temizler ve LaTeX formatına dönüştürür.
     */
    fun cleanExtractedOcrWithAi() {
        val rawText = _uiState.value.extractedOcrText
        if (rawText.isNullOrBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isCleaningOcr = true, ocrMessage = null) }
            val result = aiRepository.cleanOcrText(rawText)
            result.onSuccess { cleaned ->
                _uiState.update {
                    it.copy(
                        isCleaningOcr = false,
                        extractedOcrText = cleaned,
                        ocrMessage = "Metin ve formüller LaTeX olarak düzenlendi ✓"
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        isCleaningOcr = false,
                        ocrMessage = "Düzenleme başarısız: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    /**
     * AI çözüm isteği (Koşullu OCR destekli: Görsel toggle'ı ve ayıklanan metin desteğiyle).
     */
    fun requestAiSolution() {
        val currentSoru = _uiState.value.soru ?: return
        val state = _uiState.value

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

            val result = aiRepository.getAiSolution(
                soru = currentSoru,
                sendImage = state.sendImageToAi,
                extractedText = state.extractedOcrText
            )

            result.onSuccess { explanation ->
                val updated = currentSoru.copy(
                    aiExplanation = explanation,
                    updatedAt = System.currentTimeMillis()
                )
                soruRepository.soruGuncelle(updated)
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

    fun onaylaCozumu(extractedAnswer: String? = null) {
        val currentSoru = _uiState.value.soru ?: return
        viewModelScope.launch {
            val finalAnswer = if (currentSoru.correctAnswer.isNullOrBlank()) {
                extractedAnswer ?: extractAnswerLetter(currentSoru.aiExplanation)
            } else {
                currentSoru.correctAnswer
            }

            val updated = currentSoru.copy(
                isSolved = true,
                solvedAt = currentSoru.solvedAt ?: System.currentTimeMillis(),
                correctAnswer = finalAnswer,
                updatedAt = System.currentTimeMillis()
            )
            soruRepository.soruGuncelle(updated)
        }
    }

    fun extractAnswerLetter(explanation: String?): String? {
        if (explanation.isNullOrBlank()) return null
        val regex = Regex("""(?:Doğru Cevap|Doğru Seçenek|Cevap)\s*[:：*]*\s*([A-Ea-e])\b""")
        val match = regex.find(explanation)
        return match?.groupValues?.getOrNull(1)?.uppercase()
    }

    fun setShowDeleteDialog(show: Boolean) {
        _uiState.update { it.copy(showDeleteDialog = show) }
    }

    fun showDeleteConfirmDialog(show: Boolean) = setShowDeleteDialog(show)

    fun setShowEditNoteDialog(show: Boolean) {
        _uiState.update { it.copy(showEditNoteDialog = show) }
    }

    fun showEditNoteDialog(show: Boolean) = setShowEditNoteDialog(show)

    fun clearOcrMessage() {
        _uiState.update { it.copy(ocrMessage = null) }
    }

    fun setShowDrawingDialog(show: Boolean) {
        _uiState.update { it.copy(showDrawingDialog = show) }
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

    /**
     * Bu soruyla birebir aynı kazanımı ölçen benzer bir soru ve çözümü üretir (Question Cloner).
     */
    fun generateSimilarQuestion() {
        val currentSoru = _uiState.value.soru ?: return
        val state = _uiState.value

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isGeneratingSimilar = true,
                    similarQuestionError = null,
                    showSimilarQuestionDialog = true,
                    userSelectedSimilarOption = null,
                    isSimilarAnswerRevealed = false
                )
            }

            val hasKey = aiRepository.hasApiKey()
            if (!hasKey) {
                _uiState.update {
                    it.copy(
                        isGeneratingSimilar = false,
                        similarQuestionError = "Benzer soru üretmek için lütfen Ayarlar sayfasından API anahtarınızı girin."
                    )
                }
                return@launch
            }

            val bytes = if (state.sendImageToAi) {
                gorselDepo.getGorselBytes(currentSoru.imagePath)
            } else {
                null
            }

            val dersAdi = com.sorudefteri.domain.DersTaksonomisi.getDersAdi(currentSoru.examType, currentSoru.subjectId)
            val result = aiRepository.generateSimilarQuestion(
                imageBytes = bytes,
                dersAdi = dersAdi,
                konu = currentSoru.topic,
                soruMetni = state.extractedOcrText
            )
            result.onSuccess { similar ->
                _uiState.update {
                    it.copy(
                        isGeneratingSimilar = false,
                        similarQuestion = similar,
                        similarQuestionError = null
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isGeneratingSimilar = false,
                        similarQuestionError = "Benzer soru üretilemedi: ${throwable.localizedMessage}"
                    )
                }
            }
        }
    }

    fun selectSimilarOption(option: String) {
        _uiState.update { it.copy(userSelectedSimilarOption = option) }
    }

    fun revealSimilarAnswer() {
        _uiState.update { it.copy(isSimilarAnswerRevealed = true) }
    }

    fun closeSimilarQuestionDialog() {
        _uiState.update {
            it.copy(
                showSimilarQuestionDialog = false,
                similarQuestion = null,
                similarQuestionError = null,
                userSelectedSimilarOption = null,
                isSimilarAnswerRevealed = false
            )
        }
    }
}
