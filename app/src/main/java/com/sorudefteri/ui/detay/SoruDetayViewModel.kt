package com.sorudefteri.ui.detay

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sorudefteri.data.ai.AiRepository
import com.sorudefteri.data.images.GorselDepo
import com.sorudefteri.data.local.entity.SoruChatMessageEntity
import com.sorudefteri.data.ocr.OcrHelper
import com.sorudefteri.data.worker.AiSolutionRetryWorker
import com.sorudefteri.domain.model.AiErrorType
import com.sorudefteri.domain.model.AiException
import com.sorudefteri.domain.model.BenzerSoru
import com.sorudefteri.domain.model.SimilarQuestionResult
import com.sorudefteri.domain.model.Soru
import com.sorudefteri.domain.model.toBenzerSoru
import com.sorudefteri.domain.repository.SoruRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val isAiQueuedForRetry: Boolean = false,
    val activeTab: DetayTab = DetayTab.SORU,
    val showDeleteDialog: Boolean = false,
    val showEditNoteDialog: Boolean = false,
    val showManualSolutionDialog: Boolean = false,
    val zoomImagePath: String? = null,
    val zoomImageTitle: String? = null,
    val showDrawingDialog: Boolean = false,
    val isGeneratingSimilar: Boolean = false,
    val similarQuestion: SimilarQuestionResult? = null,
    val similarQuestionError: String? = null,
    val showSimilarQuestionDialog: Boolean = false,
    val userSelectedSimilarOption: String? = null,
    val isSimilarAnswerRevealed: Boolean = false,
    // Soruya bağlı kalıcı benzer soru arşivi
    val savedSimilarQuestions: List<BenzerSoru> = emptyList(),
    // Kayıtlı benzer soruların DB'den okunup okunmadığı. false iken "boş durum"
    // kartı gösterilmez, yükleniyor kartı gösterilir (içerik zıplamasını önler).
    val isSavedSimilarLoaded: Boolean = false,
    val isSavingSimilar: Boolean = false,
    val similarSaveMessage: String? = null,
    // Koşullu OCR & AI İletim Alanları
    val isOcrLoading: Boolean = false,
    val extractedOcrText: String? = null,
    val sendImageToAi: Boolean = true,
    val isCleaningOcr: Boolean = false,
    val ocrMessage: String? = null,
    // Harici AI Chatbot Paylaşım & Manuel Benzer Soru State
    val showExternalAiShareDialog: Boolean = false,
    val externalAiShareMode: com.sorudefteri.ui.detay.components.ExternalAiMode = com.sorudefteri.ui.detay.components.ExternalAiMode.SOLUTION,
    val showManualSimilarQuestionDialog: Boolean = false,
    // 3 Soruluk Soru İkizi (Kardeş Test) State
    val showManualTwinQuestionsDialog: Boolean = false,
    val showTwinQuestionsTestDialog: Boolean = false,
    val activeTwinQuestionsResult: com.sorudefteri.domain.model.TwinQuestionsResult? = null,
    val isGeneratingTwinQuestions: Boolean = false,
    val twinQuestionsError: String? = null,
    // Sokratik AI Chatbot
    val chatMessages: List<SoruChatMessageEntity> = emptyList(),
    val isChatSending: Boolean = false,
    val chatError: String? = null,
    val showChatBottomSheet: Boolean = false
)

@HiltViewModel
class SoruDetayViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val soruRepository: SoruRepository,
    private val aiRepository: AiRepository,
    private val gorselDepo: GorselDepo,
    private val ocrHelper: OcrHelper,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val soruId: Long = savedStateHandle.get<Long>("soruId")
        ?: savedStateHandle.get<String>("soruId")?.toLongOrNull()
        ?: 0L

    private val _uiState = MutableStateFlow(SoruDetayUiState())
    val uiState: StateFlow<SoruDetayUiState> = _uiState.asStateFlow()

    init {
        loadSoru()
        listenCameraCapture()
        loadSavedSimilarQuestions()
        loadChatMessages()
        observeBackgroundWork()
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

    private fun observeBackgroundWork() {
        if (soruId <= 0L) return
        viewModelScope.launch {
            WorkManager.getInstance(appContext)
                .getWorkInfosByTagFlow(AiSolutionRetryWorker.tagForSoru(soruId))
                .collectLatest { workInfos ->
                    val hasActiveWork = workInfos.any {
                        it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING
                    }
                    if (hasActiveWork) {
                        _uiState.update {
                            it.copy(
                                isAiLoading = true,
                                isAiQueuedForRetry = false
                            )
                        }
                    } else if (_uiState.value.isAiLoading) {
                        // Work tamamlandı (SUCCEEDED, FAILED, CANCELLED)
                        val lastFailed = workInfos.firstOrNull { it.state == WorkInfo.State.FAILED }
                        val errorMsg = lastFailed?.outputData?.getString(AiSolutionRetryWorker.KEY_ERROR_MESSAGE)
                        _uiState.update { current ->
                            current.copy(
                                isAiLoading = false,
                                aiError = if (current.soru?.aiExplanation.isNullOrBlank()) {
                                    errorMsg ?: current.aiError
                                } else null
                            )
                        }
                    }
                }
        }
    }

    /**
     * AI çözüm isteği (Koşullu OCR destekli: Görsel toggle'ı ve ayıklanan metin desteğiyle).
     * ViewModelScope iptal olsa bile arka planda çalışması için WorkManager'a devredilir.
     */
    fun requestAiSolution() {
        if (_uiState.value.isAiLoading) return
        val currentSoru = _uiState.value.soru ?: return

        viewModelScope.launch {
            val hasKey = aiRepository.hasApiKey()
            if (!hasKey) {
                _uiState.update {
                    it.copy(
                        isAiLoading = false,
                        isApiKeyMissing = true,
                        aiError = "API anahtarı bulunamadı. Lütfen Ayarlar sayfasından Gemini, Groq veya NVIDIA Build API anahtarınızı girin."
                    )
                }
                return@launch
            }

            // Fresh state after hasApiKey check (avoid stale snapshot)
            val fresh = _uiState.value
            if (!fresh.sendImageToAi && fresh.extractedOcrText.isNullOrBlank()) {
                _uiState.update {
                    it.copy(
                        isAiLoading = false,
                        aiError = "Gönderilecek veri yok: Lütfen 'Görseli AI'a gönder' açık olsun veya önce OCR ile metin çıkarın."
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    isAiLoading = true,
                    aiError = null,
                    isApiKeyMissing = false,
                    isAiQueuedForRetry = false
                )
            }

            try {
                AiSolutionRetryWorker.enqueueManualSolve(
                    context = appContext,
                    soruId = currentSoru.id,
                    sendImageToAi = fresh.sendImageToAi,
                    extractedText = fresh.extractedOcrText
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isAiLoading = false,
                        aiError = "Arka plan işlemi başlatılamadı: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun clearAiError() {
        _uiState.update { it.copy(aiError = null, isApiKeyMissing = false, isAiQueuedForRetry = false) }
    }

    // Manuel çözüm girişi — harici AI (ChatGPT/Claude/Gemini web) veya elle yazılan çözüm
    fun saveManualSolution(manualText: String) {
        val currentSoru = _uiState.value.soru ?: return
        val trimmed = manualText.trim()
        if (trimmed.isBlank()) return
        // 50000 karakter limiti (DB ve UI için güvenli)
        val safeText = if (trimmed.length > 50000) trimmed.take(50000) else trimmed
        viewModelScope.launch {
            val updated = currentSoru.copy(
                aiExplanation = safeText,
                updatedAt = System.currentTimeMillis()
            )
            soruRepository.soruGuncelle(updated)
            _uiState.update {
                it.copy(
                    aiError = null,
                    isAiQueuedForRetry = false,
                    showManualSolutionDialog = false,
                    isAiLoading = false
                )
            }
        }
    }

    fun setShowManualSolutionDialog(show: Boolean) {
        _uiState.update { it.copy(showManualSolutionDialog = show) }
    }

    fun setShowExternalAiShareDialog(show: Boolean, mode: com.sorudefteri.ui.detay.components.ExternalAiMode = com.sorudefteri.ui.detay.components.ExternalAiMode.SOLUTION) {
        _uiState.update {
            it.copy(
                showExternalAiShareDialog = show,
                externalAiShareMode = mode
            )
        }
    }

    fun setShowManualSimilarQuestionDialog(show: Boolean) {
        _uiState.update { it.copy(showManualSimilarQuestionDialog = show) }
    }

    fun setShowManualTwinQuestionsDialog(show: Boolean) {
        _uiState.update { it.copy(showManualTwinQuestionsDialog = show) }
    }

    fun setShowTwinQuestionsTestDialog(show: Boolean, result: com.sorudefteri.domain.model.TwinQuestionsResult? = null) {
        _uiState.update {
            it.copy(
                showTwinQuestionsTestDialog = show,
                activeTwinQuestionsResult = result ?: it.activeTwinQuestionsResult
            )
        }
    }

    /**
     * 3 Soruluk Kardeş Testi sorularını kütüphaneye bağımsız soru kayıtları olarak ekler.
     */
    fun saveTwinQuestionsToLibrary(
        twinResult: com.sorudefteri.domain.model.TwinQuestionsResult,
        onComplete: (Int) -> Unit = {}
    ) {
        val parent = _uiState.value.soru ?: return
        viewModelScope.launch {
            try {
                var count = 0
                twinResult.questions.forEachIndexed { index, q ->
                    val optionsText = q.options.toSortedMap().entries
                        .joinToString("\n") { (k, v) -> "$k) $v" }
                    val noteBuilder = StringBuilder()
                    val role = when (index) {
                        0 -> "Sayı & Değer Varyasyonu"
                        1 -> "Senaryo & Hikaye Varyasyonu"
                        else -> "Tersine Akıl Yürütme & Kavram Tuzağı"
                    }
                    noteBuilder.append("🧩 Kaynak soru #${parent.id} ikizi (Soru ${index + 1}/3 - $role)\n")
                    if (!q.trapWarning.isNullOrBlank()) {
                        noteBuilder.append("⚠️ Dikkat Tuzağı: ${q.trapWarning}\n")
                    }
                    noteBuilder.append("\n")
                    if (optionsText.isNotBlank()) {
                        noteBuilder.append(optionsText).append("\n\n")
                    }
                    parent.note?.takeIf { it.isNotBlank() }?.let {
                        noteBuilder.append("Kaynak notu: $it")
                    }

                    val newSoru = parent.copy(
                        id = 0L,
                        questionText = q.questionText.ifBlank { null },
                        ocrMetni = q.questionText.ifBlank { parent.ocrMetni },
                        correctAnswer = q.correctAnswer.ifBlank { null },
                        aiExplanation = q.solution.ifBlank { null },
                        note = noteBuilder.toString().trim().ifBlank { null },
                        isSolved = false,
                        solvedAt = null,
                        isFavorite = false,
                        reviewStage = 0,
                        nextReviewDate = null,
                        lastReviewedAt = null,
                        createdAt = System.currentTimeMillis() + index,
                        updatedAt = System.currentTimeMillis() + index
                    )
                    soruRepository.soruEkle(newSoru)
                    count++
                }
                _uiState.update {
                    it.copy(similarSaveMessage = "$count kardeş soru kütüphanenize eklendi ✓")
                }
                onComplete(count)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(similarSaveMessage = "Kütüphaneye kayıt başarısız: ${e.localizedMessage}")
                }
            }
        }
    }

    /**
     * Manuel yapıştırılan benzer soruyu önce ekrana yükler, ardından soruya bağlı
     * olarak Room veritabanına kalıcı kaydeder. Kayıt başarısız olsa bile ekran
     * içeriği korunur ve kullanıcı bilgilendirilir.
     */
    fun saveManualSimilarQuestion(result: com.sorudefteri.domain.model.SimilarQuestionResult) {
        if (soruId <= 0L) {
            _uiState.update {
                it.copy(
                    similarQuestion = result,
                    similarQuestionError = null,
                    showManualSimilarQuestionDialog = false,
                    userSelectedSimilarOption = null,
                    isSimilarAnswerRevealed = false,
                    similarSaveMessage = "Önizleme yüklendi (soru kaydı bulunamadı)."
                )
            }
            return
        }
        _uiState.update {
            it.copy(
                similarQuestion = result,
                similarQuestionError = null,
                showManualSimilarQuestionDialog = false,
                userSelectedSimilarOption = null,
                isSimilarAnswerRevealed = false,
                isSavingSimilar = true,
                similarSaveMessage = null
            )
        }
        viewModelScope.launch {
            try {
                soruRepository.saveBenzerSoru(soruId, result)
                _uiState.update {
                    it.copy(
                        isSavingSimilar = false,
                        similarSaveMessage = "Benzer soru bu soruya kaydedildi ✓"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSavingSimilar = false,
                        similarSaveMessage = "Önizleme hazır, ancak kalıcı kayıt başarısız: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    /**
     * Ekrandaki aktif benzer soruyu soruya bağlı arşive kaydeder (tekrar kaydet).
     */
    fun persistCurrentSimilarQuestion() {
        val current = _uiState.value.similarQuestion ?: return
        if (soruId <= 0L || _uiState.value.isSavingSimilar) return
        _uiState.update { it.copy(isSavingSimilar = true, similarSaveMessage = null) }
        viewModelScope.launch {
            try {
                soruRepository.saveBenzerSoru(soruId, current)
                _uiState.update {
                    it.copy(
                        isSavingSimilar = false,
                        similarSaveMessage = "Benzer soru bu soruya kaydedildi ✓"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSavingSimilar = false,
                        similarSaveMessage = "Kayıt başarısız: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    /**
     * Kayıtlı benzer soruyu kütüphaneye bağımsız soru olarak kopyalar.
     * Görseli olmayan metin sorular için ebeveyn görseli paylaşılır.
     */
    fun saveSimilarToLibrary(benzer: BenzerSoru, onSaved: (Long) -> Unit = {}) {
        val parent = _uiState.value.soru ?: return
        viewModelScope.launch {
            try {
                val optionsText = benzer.options.toSortedMap().entries
                    .joinToString("\n") { (k, v) -> "$k) $v" }
                val noteBuilder = StringBuilder()
                noteBuilder.append("📌 Kaynak soru #${parent.id} benzeri (otomatik kopya)\n\n")
                if (optionsText.isNotBlank()) {
                    noteBuilder.append(optionsText).append("\n\n")
                }
                parent.note?.takeIf { it.isNotBlank() }?.let {
                    noteBuilder.append("Kaynak notu: $it")
                }
                val newSoru = parent.copy(
                    id = 0L,
                    questionText = benzer.questionText.ifBlank { null },
                    ocrMetni = benzer.questionText.ifBlank { parent.ocrMetni },
                    correctAnswer = benzer.correctAnswer.ifBlank { null },
                    aiExplanation = benzer.solution.ifBlank { null },
                    note = noteBuilder.toString().trim().ifBlank { null },
                    isSolved = false,
                    solvedAt = null,
                    isFavorite = false,
                    reviewStage = 0,
                    nextReviewDate = null,
                    lastReviewedAt = null,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                val newId = soruRepository.soruEkle(newSoru)
                _uiState.update {
                    it.copy(similarSaveMessage = "Kütüphaneye kaydedildi ✓ (Soru #$newId)")
                }
                onSaved(newId)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(similarSaveMessage = "Kütüphaneye kayıt başarısız: ${e.localizedMessage}")
                }
            }
        }
    }

    /** Ekrandaki aktif benzer soruyu kütüphaneye kaydeder. */
    fun saveCurrentSimilarToLibrary(onSaved: (Long) -> Unit = {}) {
        val current = _uiState.value.similarQuestion ?: return
        saveSimilarToLibrary(current.toBenzerSoru(soruId), onSaved)
    }

    fun deleteSavedSimilarQuestion(id: Long) {
        viewModelScope.launch {
            try {
                soruRepository.deleteBenzerSoru(id)
                _uiState.update { current ->
                    val remaining = current.savedSimilarQuestions.filter { it.id != id }
                    val isCurrentDeleted = current.savedSimilarQuestions.find { it.id == id }?.questionText == current.similarQuestion?.questionText
                    val newSimilar = if (isCurrentDeleted) {
                        remaining.firstOrNull()?.toSimilarResult()
                    } else {
                        current.similarQuestion
                    }
                    current.copy(
                        savedSimilarQuestions = remaining,
                        similarQuestion = newSimilar,
                        similarSaveMessage = "Kayıtlı benzer soru silindi."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(similarSaveMessage = "Silme başarısız: ${e.localizedMessage}")
                }
            }
        }
    }

    fun openSavedSimilarQuestion(benzer: BenzerSoru) {
        _uiState.update {
            it.copy(
                similarQuestion = benzer.toSimilarResult(),
                similarQuestionError = null,
                userSelectedSimilarOption = null,
                isSimilarAnswerRevealed = false,
                similarSaveMessage = null
            )
        }
    }

    fun clearSimilarSaveMessage() {
        _uiState.update { it.copy(similarSaveMessage = null) }
    }

    private fun loadSavedSimilarQuestions() {
        if (soruId <= 0L) return
        viewModelScope.launch {
            soruRepository.getBenzerSorular(soruId)
                .catch {
                    // Akış hatasında sessiz geç (ekran içeriği etkilenmesin)
                }
                .collectLatest { list ->
                    _uiState.update { state ->
                        // Soru detayına girildiğinde daha önce kaydedilmiş benzer soru varsa
                        // en son kaydedilen benzer soru otomatik olarak ekrana yüklensin.
                        val latestSimilar = if (state.similarQuestion == null && list.isNotEmpty()) {
                            list.first().toSimilarResult()
                        } else {
                            state.similarQuestion
                        }
                        state.copy(
                            savedSimilarQuestions = list,
                            similarQuestion = latestSimilar,
                            // DÜZELTME: Benzer Soru sekmesine girildiğinde ilk karede
                            // "boş durum" kartı çiziliyor, bir DB turu sonra kayıtlı soru
                            // gelince TÜM SEKME İÇERİĞİ yer değiştiriyordu. Bu bayrak,
                            // "yok" ile "henüz yüklenmedi" ayrımını yapar.
                            isSavedSimilarLoaded = true
                        )
                    }
                }
        }
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
        if (_uiState.value.isGeneratingSimilar) return
        val currentSoru = _uiState.value.soru ?: return

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

            val fresh = _uiState.value
            val bytes = if (fresh.sendImageToAi) {
                try { gorselDepo.getGorselBytes(currentSoru.imagePath) } catch (_: Exception) { null }
            } else null

            val dersAdi = com.sorudefteri.domain.DersTaksonomisi.getDersAdi(currentSoru.examType, currentSoru.subjectId)
            val result = try {
                aiRepository.generateSimilarQuestion(
                    imageBytes = bytes,
                    dersAdi = dersAdi,
                    konu = currentSoru.topic,
                    soruMetni = fresh.extractedOcrText
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(isGeneratingSimilar = false, similarQuestionError = e.localizedMessage) }
                return@launch
            }
            result.onSuccess { similar ->
                _uiState.update {
                    it.copy(
                        isGeneratingSimilar = false,
                        similarQuestion = similar,
                        similarQuestionError = null,
                        isSavingSimilar = true,
                        similarSaveMessage = null
                    )
                }
                // AI çıktısını da soruya bağlı arşive kalıcı yaz
                try {
                    soruRepository.saveBenzerSoru(currentSoru.id, similar)
                    _uiState.update {
                        it.copy(
                            isSavingSimilar = false,
                            similarSaveMessage = "Benzer soru bu soruya kaydedildi ✓"
                        )
                    }
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(
                            isSavingSimilar = false,
                            similarSaveMessage = "Önizleme hazır, ancak kalıcı kayıt başarısız: ${e.localizedMessage}"
                        )
                    }
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

    /**
     * Bu sorunun kazanımından 3 farklı kardeş soru (Soru İkizleri) türetir ve interaktif testi başlatır.
     */
    fun generateTwinQuestions() {
        if (_uiState.value.isGeneratingTwinQuestions) return
        val currentSoru = _uiState.value.soru ?: return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isGeneratingTwinQuestions = true,
                    twinQuestionsError = null
                )
            }

            val hasKey = aiRepository.hasApiKey()
            if (!hasKey) {
                _uiState.update {
                    it.copy(
                        isGeneratingTwinQuestions = false,
                        twinQuestionsError = "Soru ikizi üretmek için lütfen Ayarlar sayfasından API anahtarınızı girin veya harici AI kullanın."
                    )
                }
                return@launch
            }

            val fresh = _uiState.value
            val bytes = if (fresh.sendImageToAi) {
                try { gorselDepo.getGorselBytes(currentSoru.imagePath) } catch (_: Exception) { null }
            } else null

            val dersAdi = com.sorudefteri.domain.DersTaksonomisi.getDersAdi(currentSoru.examType, currentSoru.subjectId)
            val result = try {
                aiRepository.generateTwinQuestions(
                    imageBytes = bytes,
                    dersAdi = dersAdi,
                    konu = currentSoru.topic,
                    soruMetni = fresh.extractedOcrText ?: currentSoru.ocrMetni
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(isGeneratingTwinQuestions = false, twinQuestionsError = e.localizedMessage) }
                return@launch
            }

            result.onSuccess { twinResult ->
                _uiState.update {
                    it.copy(
                        isGeneratingTwinQuestions = false,
                        twinQuestionsError = null,
                        activeTwinQuestionsResult = twinResult,
                        showTwinQuestionsTestDialog = true
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isGeneratingTwinQuestions = false,
                        twinQuestionsError = err.message ?: "3 soru ikizi üretilemedi."
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
                similarQuestionError = null,
                userSelectedSimilarOption = null,
                isSimilarAnswerRevealed = false
            )
        }
    }

    private fun loadChatMessages() {
        viewModelScope.launch {
            soruRepository.getChatMessagesForSoru(soruId)
                .catch { e -> _uiState.update { it.copy(chatError = e.localizedMessage) } }
                .collect { messages ->
                    _uiState.update { it.copy(chatMessages = messages) }
                }
        }
    }

    fun sendChatMessage(userText: String) {
        val currentSoru = _uiState.value.soru ?: return
        val text = userText.trim()
        if (text.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isChatSending = true, chatError = null) }

            // 1. Öğrenci mesajını kaydet
            val userMsg = SoruChatMessageEntity(
                soruId = currentSoru.id,
                sender = "USER",
                messageText = text,
                timestamp = System.currentTimeMillis()
            )
            soruRepository.insertChatMessage(userMsg)

            // 2. AI Koçundan yanıt iste
            val currentHistory = _uiState.value.chatMessages
            val result = aiRepository.sendSoruChatMessage(
                soru = currentSoru,
                chatHistory = currentHistory,
                userMessage = text,
                sendImage = _uiState.value.sendImageToAi
            )

            result.onSuccess { aiReply ->
                val aiMsg = SoruChatMessageEntity(
                    soruId = currentSoru.id,
                    sender = "AI",
                    messageText = aiReply,
                    timestamp = System.currentTimeMillis()
                )
                soruRepository.insertChatMessage(aiMsg)
                _uiState.update { it.copy(isChatSending = false) }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isChatSending = false,
                        chatError = "AI yanıt veremedi: ${throwable.localizedMessage}"
                    )
                }
            }
        }
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            soruRepository.clearChatMessagesForSoru(soruId)
        }
    }

    fun toggleChatBottomSheet(show: Boolean) {
        _uiState.update { it.copy(showChatBottomSheet = show) }
    }

    /**
     * Kaydedilen ses dosyasının yolunu veritabanına yazar.
     *
     * [NonCancellable] + [Dispatchers.IO] kullanılıyor çünkü kullanıcı kaydettikten
     * hemen sonra geri tuşuna basarsa viewModelScope iptal olur ve yazma işlemi
     * tamamlanmadan soru kalıcı olarak "ses kaydı yok" kalırdı. Bu, kullanıcının
     * "kaydettim ama girdiğimde gidiyor" şikâyetinin ikinci nedeniydi.
     */
    fun saveAudioMemo(audioPath: String) {
        val currentSoru = _uiState.value.soru ?: return
        val updated = currentSoru.copy(audioPath = audioPath)
        _uiState.update { it.copy(soru = updated) }
        viewModelScope.launch {
            withContext(NonCancellable + Dispatchers.IO) {
                soruRepository.soruGuncelle(updated)
            }
        }
    }

    fun deleteAudioMemo() {
        val currentSoru = _uiState.value.soru ?: return
        val oldPath = currentSoru.audioPath
        val updated = currentSoru.copy(audioPath = null)
        _uiState.update { it.copy(soru = updated) }
        viewModelScope.launch {
            // Önce veritabanını temizle, sonra dosyayı sil. Yazma iptal olursa
            // dosya kalıcı kalsın; user tekrar denediğinde yeniden silinir.
            withContext(NonCancellable + Dispatchers.IO) {
                soruRepository.soruGuncelle(updated)
                if (!oldPath.isNullOrBlank()) {
                    try {
                        val f = File(oldPath)
                        if (f.exists()) f.delete()
                        else File(appContext.filesDir, oldPath).delete()
                    } catch (_: Exception) {}
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // PERF: OcrHelper @Singleton'dur; ekran kapanınca kapatılmaz
        // (bkz. SoruEklemeViewModel.onCleared).
    }
}
