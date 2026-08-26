package com.sorudefteri.ui.soruekle

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sorudefteri.data.images.GorselDepo
import com.sorudefteri.data.local.AyarlarDataStore
import com.sorudefteri.data.ocr.OcrHelper
import com.sorudefteri.data.repository.SoruRepository
import com.sorudefteri.domain.DersTaksonomisi
import com.sorudefteri.domain.KonuKatalog
import com.sorudefteri.domain.KonuUnite
import com.sorudefteri.domain.model.Alan
import com.sorudefteri.domain.model.Ders
import com.sorudefteri.domain.model.HataTipi
import com.sorudefteri.domain.model.Soru
import com.sorudefteri.domain.repository.AiRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class SoruEklemeUiState(
    val editingSoruId: Long? = null,
    val soruImagePath: String? = null,
    val solutionImagePath: String? = null,
    val examType: String = "TYT", // "TYT" or "AYT"
    val subjectId: String = "matematik",
    val topic: String = "",
    val mistakeType: HataTipi? = null,
    val difficulty: String? = null,
    val correctAnswer: String? = null,
    val studentAnswer: String? = null,
    val note: String = "",
    val kaynakYayin: String = "",
    val kaynakDeneme: String = "",
    val kaynakYil: String = "",
    val kaynakSoruNo: String = "",
    val topicSuggestions: List<String> = emptyList(),
    val catalogUnits: List<KonuUnite> = emptyList(),
    val frequentTopics: List<String> = emptyList(),
    val availableSubjects: List<Ders> = emptyList(),
    val userAlan: Alan = Alan.SAYISAL,
    val isCatalogSheetOpen: Boolean = false,
    val catalogSearchQuery: String = "",
    val selectedCatalogUnit: String? = null,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val isAutoTagging: Boolean = false,
    val autoTagSuccessMessage: String? = null,
    val errorMessage: String? = null,
    // Koşullu OCR & Metin Çıkarımı
    val isOcrExtracting: Boolean = false,
    val extractedText: String? = null,
    val sendImageToAi: Boolean = true,
    val isCleaningOcr: Boolean = false,
    val ocrStatusMessage: String? = null,
    // Kırpma / Döndürme Dialogu
    val isCropperOpen: Boolean = false,
    val cropTargetIsSolution: Boolean = false
) {
    val isEditMode: Boolean
        get() = editingSoruId != null

    val canSave: Boolean
        get() = !soruImagePath.isNullOrBlank() && topic.isNotBlank() && !isSaving

    val currentSubjectName: String
        get() = availableSubjects.find { it.id == subjectId }?.ad ?: DersTaksonomisi.getDersAdi(examType, subjectId)

    val totalTopicCountInCatalog: Int
        get() = catalogUnits.sumOf { it.konular.size }
}

@HiltViewModel
class SoruEklemeViewModel @Inject constructor(
    private val soruRepository: SoruRepository,
    private val gorselDepo: GorselDepo,
    private val konuKatalog: KonuKatalog,
    private val ayarlarDataStore: AyarlarDataStore,
    private val aiRepository: AiRepository,
    private val ocrHelper: OcrHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(SoruEklemeUiState())
    val uiState: StateFlow<SoruEklemeUiState> = _uiState.asStateFlow()

    init {
        // Kullanıcının alanını dinleyip ders listesini ve konu önerilerini hazırla
        viewModelScope.launch {
            ayarlarDataStore.currentAlan.collectLatest { alan ->
                _uiState.update { current ->
                    val subjects = DersTaksonomisi.getDersler(current.examType, alan)
                    val defaultSubject = subjects.firstOrNull()?.id ?: "matematik"
                    val validSubjectId = if (subjects.any { it.id == current.subjectId }) current.subjectId else defaultSubject
                    current.copy(
                        userAlan = alan,
                        availableSubjects = subjects,
                        subjectId = validSubjectId
                    )
                }
                updateTopicSuggestions()
            }
        }
    }

    /**
     * Dışarıdan veya kameradan/galeriden gelen başlangıç görsel yolunu veya URI'sini yükler.
     */
    fun setInitialImage(pathOrUri: String?, isSolution: Boolean = false) {
        if (pathOrUri.isNullOrBlank()) return
        if (isSolution) {
            setSolutionImageFromUriOrPath(pathOrUri)
        } else {
            setSoruImageFromUriOrPath(pathOrUri)
        }
    }

    fun setSoruImageFromUriOrPath(uriOrPath: String) {
        if (uriOrPath.isBlank()) return
        viewModelScope.launch {
            try {
                val path = if (uriOrPath.startsWith("content://") || uriOrPath.startsWith("file://")) {
                    val uri = Uri.parse(uriOrPath)
                    gorselDepo.saveImageFromUri(uri, isSolution = false)
                } else if (File(uriOrPath).exists()) {
                    gorselDepo.kaydetDosyadan(File(uriOrPath), isSolution = false)
                } else {
                    val uri = Uri.parse(uriOrPath)
                    gorselDepo.saveImageFromUri(uri, isSolution = false)
                }
                _uiState.update { it.copy(soruImagePath = path, errorMessage = null) }
            } catch (e: Exception) {
                if (File(uriOrPath).exists()) {
                    _uiState.update { it.copy(soruImagePath = uriOrPath, errorMessage = null) }
                } else {
                    _uiState.update { it.copy(errorMessage = "Görsel yüklenemedi: ${e.localizedMessage}") }
                }
            }
        }
    }

    fun setSolutionImageFromUriOrPath(uriOrPath: String) {
        if (uriOrPath.isBlank()) return
        viewModelScope.launch {
            try {
                val path = if (uriOrPath.startsWith("content://") || uriOrPath.startsWith("file://")) {
                    val uri = Uri.parse(uriOrPath)
                    gorselDepo.saveImageFromUri(uri, isSolution = true)
                } else if (File(uriOrPath).exists()) {
                    gorselDepo.kaydetDosyadan(File(uriOrPath), isSolution = true)
                } else {
                    val uri = Uri.parse(uriOrPath)
                    gorselDepo.saveImageFromUri(uri, isSolution = true)
                }
                _uiState.update { it.copy(solutionImagePath = path, errorMessage = null) }
            } catch (e: Exception) {
                if (File(uriOrPath).exists()) {
                    _uiState.update { it.copy(solutionImagePath = uriOrPath, errorMessage = null) }
                } else {
                    _uiState.update { it.copy(errorMessage = "Çözüm görseli yüklenemedi: ${e.localizedMessage}") }
                }
            }
        }
    }

    fun setSoruImageUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val path = gorselDepo.saveImageFromUri(uri, isSolution = false)
                _uiState.update { it.copy(soruImagePath = path, errorMessage = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Görsel kaydedilemedi: ${e.localizedMessage}") }
            }
        }
    }

    fun setSolutionImageUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val path = gorselDepo.saveImageFromUri(uri, isSolution = true)
                _uiState.update { it.copy(solutionImagePath = path, errorMessage = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Çözüm görseli kaydedilemedi: ${e.localizedMessage}") }
            }
        }
    }

    fun setSoruImagePath(path: String) {
        _uiState.update { it.copy(soruImagePath = path, errorMessage = null) }
    }

    fun setSolutionImagePath(path: String) {
        _uiState.update { it.copy(solutionImagePath = path, errorMessage = null) }
    }

    fun removeSoruImage() {
        val currentPath = _uiState.value.soruImagePath
        if (currentPath != null) {
            viewModelScope.launch { gorselDepo.gorselSil(currentPath) }
        }
        _uiState.update { it.copy(soruImagePath = null, extractedText = null) }
    }

    fun removeSolutionImage() {
        val currentPath = _uiState.value.solutionImagePath
        if (currentPath != null) {
            viewModelScope.launch { gorselDepo.gorselSil(currentPath) }
        }
        _uiState.update { it.copy(solutionImagePath = null) }
    }

    fun setExamType(type: String) {
        if (_uiState.value.examType == type) return

        val alan = _uiState.value.userAlan
        val subjects = DersTaksonomisi.getDersler(type, alan)
        val firstSubject = subjects.firstOrNull()?.id ?: "matematik"

        _uiState.update {
            it.copy(
                examType = type,
                availableSubjects = subjects,
                subjectId = firstSubject,
                topic = "",
                catalogSearchQuery = "",
                selectedCatalogUnit = null
            )
        }
        updateTopicSuggestions()
    }

    fun setSubject(subjectId: String) {
        _uiState.update {
            it.copy(
                subjectId = subjectId,
                topic = "",
                catalogSearchQuery = "",
                selectedCatalogUnit = null
            )
        }
        updateTopicSuggestions()
    }

    fun setTopic(topic: String) {
        _uiState.update { it.copy(topic = topic, errorMessage = null) }
    }

    fun selectTopicFromCatalog(topic: String) {
        _uiState.update {
            it.copy(
                topic = topic,
                isCatalogSheetOpen = false,
                errorMessage = null
            )
        }
    }

    fun openTopicCatalog(isOpen: Boolean) {
        _uiState.update {
            it.copy(
                isCatalogSheetOpen = isOpen,
                catalogSearchQuery = if (isOpen) it.catalogSearchQuery else ""
            )
        }
    }

    fun setCatalogSearchQuery(query: String) {
        _uiState.update { it.copy(catalogSearchQuery = query) }
    }

    fun setSelectedCatalogUnit(unitName: String?) {
        _uiState.update { it.copy(selectedCatalogUnit = unitName) }
    }

    fun setMistakeType(mistakeType: HataTipi?) {
        _uiState.update { it.copy(mistakeType = mistakeType) }
    }

    fun setDifficulty(difficulty: String?) {
        _uiState.update { it.copy(difficulty = difficulty) }
    }

    fun setCorrectAnswer(answer: String?) {
        _uiState.update { it.copy(correctAnswer = answer) }
    }

    fun setStudentAnswer(answer: String?) {
        _uiState.update { it.copy(studentAnswer = answer) }
    }

    fun setNote(note: String) {
        _uiState.update { it.copy(note = note) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearErrorMessage() = clearError()

    fun clearAutoTagStatus() {
        _uiState.update { it.copy(autoTagSuccessMessage = null) }
    }

    fun clearAutoTagMessage() = clearAutoTagStatus()

    fun clearOcrText() {
        _uiState.update { it.copy(extractedText = null, ocrStatusMessage = null) }
    }

    /**
     * Koşullu OCR Metin Ayıklama Servisi
     */
    fun extractTextFromQuestionImage() {
        val currentImagePath = _uiState.value.soruImagePath
        if (currentImagePath.isNullOrBlank()) {
            _uiState.update { it.copy(errorMessage = "Metin ayıklamak için önce bir soru görseli ekleyin.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isOcrExtracting = true, ocrStatusMessage = null) }
            val result = ocrHelper.recognizeText(currentImagePath)
            result.onSuccess { text ->
                _uiState.update {
                    it.copy(
                        isOcrExtracting = false,
                        extractedText = text,
                        ocrStatusMessage = if (text.isNotBlank()) "Metin başarıyla ayıklandı ✓" else "Görselde okunabilir metin bulunamadı."
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        isOcrExtracting = false,
                        ocrStatusMessage = "OCR hatası: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun performOcrOnQuestionImage() = extractTextFromQuestionImage()

    fun updateExtractedText(newText: String) {
        _uiState.update { it.copy(extractedText = newText) }
    }

    fun updateExtractedOcrText(newText: String) = updateExtractedText(newText)

    fun setSendImageToAi(sendImage: Boolean) {
        _uiState.update { it.copy(sendImageToAi = sendImage) }
    }

    fun toggleSendImageToAi(sendImage: Boolean) = setSendImageToAi(sendImage)

    fun cleanOcrWithAi() {
        val rawText = _uiState.value.extractedText
        if (rawText.isNullOrBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isCleaningOcr = true, ocrStatusMessage = null) }
            val result = aiRepository.cleanOcrText(rawText)
            result.onSuccess { cleaned ->
                _uiState.update {
                    it.copy(
                        isCleaningOcr = false,
                        extractedText = cleaned,
                        ocrStatusMessage = "Metin ve formüller LaTeX olarak düzenlendi ✓"
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        isCleaningOcr = false,
                        ocrStatusMessage = "Düzenleme başarısız: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun cleanOcrTextWithAi() = cleanOcrWithAi()

    /**
     * Soru görselini yapay zekaya analiz ettirerek Ders, Konu, Sınav Türü ve Doğru Cevabı otomatik belirler.
     */
    fun autoTagWithAi() {
        val currentImagePath = _uiState.value.soruImagePath
        if (currentImagePath.isNullOrBlank()) {
            _uiState.update { it.copy(errorMessage = "Otomatik analiz için önce bir soru fotoğrafı ekleyin.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isAutoTagging = true, errorMessage = null, autoTagSuccessMessage = null) }
            val bytes = gorselDepo.getGorselBytes(currentImagePath)
            if (bytes == null || bytes.isEmpty()) {
                _uiState.update { it.copy(isAutoTagging = false, errorMessage = "Soru görseli okunamadı.") }
                return@launch
            }

            val result = aiRepository.detectQuestionMetadata(bytes)
            result.onSuccess { metadata ->
                val detectedExam = if (metadata.examType.equals("AYT", ignoreCase = true)) "AYT" else "TYT"
                val alan = _uiState.value.userAlan
                val subjects = DersTaksonomisi.getDersler(detectedExam, alan)
                val matchingSubject = subjects.find {
                    it.id.equals(metadata.subjectId, ignoreCase = true) ||
                            it.ad.equals(metadata.subjectId, ignoreCase = true) ||
                            metadata.subjectId.contains(it.id, ignoreCase = true)
                }?.id ?: subjects.firstOrNull()?.id ?: "matematik"

                val dersName = DersTaksonomisi.getDersAdi(detectedExam, matchingSubject)
                val successMsg = "✓ AI Analizi Tamamlandı: $dersName > ${metadata.topic}"

                _uiState.update { current ->
                    current.copy(
                        isAutoTagging = false,
                        examType = detectedExam,
                        availableSubjects = subjects,
                        subjectId = matchingSubject,
                        topic = metadata.topic.ifBlank { current.topic },
                        correctAnswer = metadata.correctAnswer ?: current.correctAnswer,
                        autoTagSuccessMessage = successMsg,
                        errorMessage = null
                    )
                }
                updateTopicSuggestions()
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        isAutoTagging = false,
                        errorMessage = "AI otomatik etiketleme başarısız: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    private var topicJob: Job? = null

    private fun updateTopicSuggestions() {
        topicJob?.cancel()
        topicJob = viewModelScope.launch {
            val currentState = _uiState.value
            val examType = currentState.examType
            val subjectId = currentState.subjectId

            // 1. Konu kataloğundaki üniteler ve konular
            val units = konuKatalog.getUniteList(examType, subjectId)
            val catalogTopics = units.flatMap { it.konular }

            // 2. Kullanıcının Room'daki geçmiş konuları
            val userTopics = try {
                soruRepository.getDistinctTopics(subjectId).first()
            } catch (e: Exception) {
                emptyList()
            }

            // Birleştir ve tekilleştir
            val combined = (userTopics + catalogTopics).distinct().filter { it.isNotBlank() }
            val frequent = (userTopics.take(4) + catalogTopics.take(8)).distinct().take(8)

            _uiState.update {
                it.copy(
                    catalogUnits = units,
                    topicSuggestions = combined,
                    frequentTopics = frequent
                )
            }
        }
    }

    fun setKaynakYayin(yayin: String) {
        _uiState.update { it.copy(kaynakYayin = yayin) }
    }

    fun setKaynakDeneme(deneme: String) {
        _uiState.update { it.copy(kaynakDeneme = deneme) }
    }

    fun setKaynakYil(yil: String) {
        _uiState.update { it.copy(kaynakYil = yil) }
    }

    fun setKaynakSoruNo(no: String) {
        _uiState.update { it.copy(kaynakSoruNo = no) }
    }

    fun openCropper(isSolution: Boolean) {
        _uiState.update { it.copy(isCropperOpen = true, cropTargetIsSolution = isSolution) }
    }

    fun closeCropper() {
        _uiState.update { it.copy(isCropperOpen = false) }
    }

    fun applyCroppedImage(newPath: String) {
        val isSolution = _uiState.value.cropTargetIsSolution
        _uiState.update {
            if (isSolution) {
                it.copy(solutionImagePath = newPath, isCropperOpen = false)
            } else {
                it.copy(soruImagePath = newPath, isCropperOpen = false)
            }
        }
    }

    fun loadSoruForEdit(soruId: Long) {
        viewModelScope.launch {
            try {
                val soru = soruRepository.getSoru(soruId) ?: return@launch
                _uiState.update {
                    it.copy(
                        editingSoruId = soru.id,
                        soruImagePath = soru.imagePath,
                        solutionImagePath = soru.solutionImagePath,
                        examType = soru.examType,
                        subjectId = soru.subjectId,
                        topic = soru.topic,
                        mistakeType = soru.mistakeType,
                        correctAnswer = soru.correctAnswer,
                        studentAnswer = soru.studentAnswer,
                        difficulty = soru.difficulty,
                        note = soru.note ?: "",
                        kaynakYayin = soru.kaynakYayin ?: "",
                        kaynakDeneme = soru.kaynakDeneme ?: "",
                        kaynakYil = soru.kaynakYil?.toString() ?: "",
                        kaynakSoruNo = soru.kaynakSoruNo?.toString() ?: "",
                        extractedText = soru.ocrMetni ?: soru.questionText,
                        sendImageToAi = !soru.isOcrUsed
                    )
                }
                updateTopicSuggestions()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Soru yüklenemedi: ${e.localizedMessage}") }
            }
        }
    }

    fun saveSoru(onSuccess: () -> Unit) {
        val state = _uiState.value
        val imagePath = state.soruImagePath

        if (imagePath.isNullOrBlank()) {
            _uiState.update { it.copy(errorMessage = "Lütfen bir soru fotoğrafı ekleyin.") }
            return
        }

        if (state.topic.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Lütfen bir konu başlığı seçin veya yazın.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            try {
                val yilParsed = state.kaynakYil.trim().toIntOrNull()
                val soruNoParsed = state.kaynakSoruNo.trim().toIntOrNull()

                val existingSoru = state.editingSoruId?.let { soruRepository.getSoru(it) }

                val soru = Soru(
                    id = state.editingSoruId ?: 0L,
                    imagePath = imagePath,
                    solutionImagePath = state.solutionImagePath,
                    correctAnswer = state.correctAnswer,
                    examType = state.examType,
                    subjectId = state.subjectId,
                    topic = state.topic.trim(),
                    mistakeType = state.mistakeType,
                    difficulty = state.difficulty,
                    studentAnswer = state.studentAnswer,
                    note = state.note.trim().ifBlank { null },
                    isSolved = existingSoru?.isSolved ?: false,
                    solvedAt = existingSoru?.solvedAt,
                    aiExplanation = existingSoru?.aiExplanation,
                    isFavorite = existingSoru?.isFavorite ?: false,
                    reviewStage = existingSoru?.reviewStage ?: 0,
                    nextReviewDate = existingSoru?.nextReviewDate,
                    lastReviewedAt = existingSoru?.lastReviewedAt,
                    kaynakYayin = state.kaynakYayin.trim().ifBlank { null },
                    kaynakDeneme = state.kaynakDeneme.trim().ifBlank { null },
                    kaynakYil = yilParsed,
                    kaynakSoruNo = soruNoParsed,
                    ocrMetni = state.extractedText?.trim()?.ifBlank { null },
                    isOcrUsed = !state.extractedText.isNullOrBlank() && !state.sendImageToAi,
                    questionText = state.extractedText?.trim()?.ifBlank { null },
                    createdAt = existingSoru?.createdAt ?: System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                if (state.editingSoruId != null) {
                    soruRepository.updateSoru(soru)
                } else {
                    soruRepository.soruEkle(soru)
                }

                _uiState.update { it.copy(isSaving = false, isSaved = true) }
                onSuccess()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = "Soru kaydedilirken hata oluştu: ${e.localizedMessage}"
                    )
                }
            }
        }
    }
}
