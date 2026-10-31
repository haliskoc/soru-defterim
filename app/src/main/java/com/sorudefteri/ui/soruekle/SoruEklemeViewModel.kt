package com.sorudefteri.ui.soruekle

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sorudefteri.data.images.GorselDepo
import com.sorudefteri.data.local.AyarlarDataStore
import com.sorudefteri.data.ocr.OcrHelper
import com.sorudefteri.data.worker.AiSolutionRetryWorker
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.sorudefteri.data.repository.SoruRepository
import com.sorudefteri.domain.DersTaksonomisi
import com.sorudefteri.domain.KonuKatalog
import com.sorudefteri.domain.KonuUnite
import com.sorudefteri.domain.model.Alan
import com.sorudefteri.domain.model.Ders
import com.sorudefteri.domain.model.HataTipi
import com.sorudefteri.domain.model.Soru
import com.sorudefteri.domain.repository.AiRepository
import com.sorudefteri.domain.manager.BadgeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
    val examType: String = "TYT",
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
    val denemeId: Long? = null,
    val denemeSoruNo: String = "",
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
    val isOcrExtracting: Boolean = false,
    val extractedText: String? = null,
    val sendImageToAi: Boolean = true,
    val isCleaningOcr: Boolean = false,
    val ocrStatusMessage: String? = null,
    val isCropperOpen: Boolean = false,
    val cropTargetIsSolution: Boolean = false,
    val isBatchCropperOpen: Boolean = false,
    val pendingBatchImages: List<String> = emptyList(),
    val totalBatchCount: Int = 0,
    val currentBatchIndex: Int = 0
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
    private val ocrHelper: OcrHelper,
    private val badgeManager: BadgeManager? = null,
    @ApplicationContext private val appContext: Context? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(SoruEklemeUiState())
    val uiState: StateFlow<SoruEklemeUiState> = _uiState.asStateFlow()

    private val sessionCreatedImagePaths = mutableSetOf<String>()
    internal var cleanupScope: CoroutineScope = CoroutineScope(Dispatchers.IO)

    init {
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

    public override fun onCleared() {
        super.onCleared()
        // PERF: OcrHelper @Singleton'dur; ekran kapanınca kapatılmaz.
        // Önceden burada close() vardı: ortak tanıyıcı kapanıyor, sonraki
        // OCR'da model yeniden yükleniyordu (getRecognizer zaten kapalıysa
        // yeniden açar; açık istek kaldıysa yıkılıyordu).

        if (!_uiState.value.isSaved) {
            val pathsToDelete = synchronized(sessionCreatedImagePaths) {
                sessionCreatedImagePaths.toList()
            }
            if (pathsToDelete.isNotEmpty()) {
                cleanupScope.launch {
                    pathsToDelete.forEach { path ->
                        try {
                            gorselDepo.gorselSil(path)
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    /**
     * Dışarıdan veya kameradan/galeriden gelen başlangıç görsel yolunu veya URI'sini yükler.
     * Fix: isSolution ayrımı ve persist Uri -> GorselDepo kopyalama doğru akış.
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
                synchronized(sessionCreatedImagePaths) { sessionCreatedImagePaths.add(path) }
                _uiState.update { it.copy(soruImagePath = path, errorMessage = null) }
            } catch (e: Exception) {
                if (File(uriOrPath).exists()) {
                    synchronized(sessionCreatedImagePaths) { sessionCreatedImagePaths.add(uriOrPath) }
                    _uiState.update { it.copy(soruImagePath = uriOrPath, errorMessage = null) }
                } else {
                    _uiState.update { it.copy(errorMessage = "Görsel yüklenemedi: ${e.localizedMessage}") }
                }
            } catch (oom: OutOfMemoryError) {
                _uiState.update { it.copy(errorMessage = "Görsel çok büyük, bellek yetersiz.") }
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
                synchronized(sessionCreatedImagePaths) { sessionCreatedImagePaths.add(path) }
                _uiState.update { it.copy(solutionImagePath = path, errorMessage = null) }
            } catch (e: Exception) {
                if (File(uriOrPath).exists()) {
                    synchronized(sessionCreatedImagePaths) { sessionCreatedImagePaths.add(uriOrPath) }
                    _uiState.update { it.copy(solutionImagePath = uriOrPath, errorMessage = null) }
                } else {
                    _uiState.update { it.copy(errorMessage = "Çözüm görseli yüklenemedi: ${e.localizedMessage}") }
                }
            } catch (oom: OutOfMemoryError) {
                _uiState.update { it.copy(errorMessage = "Görsel çok büyük, bellek yetersiz.") }
            }
        }
    }

    // Yeni akış: galeriden seçilen Uri doğrudan GorselDepo'ya kopyala (persist + crop flow öncesi)
    fun onImageSelected(uri: Uri, isSolution: Boolean) {
        if (isSolution) setSolutionImageUri(uri) else setSoruImageUri(uri)
    }

    fun setSoruImageUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val path = gorselDepo.saveImageFromUri(uri, isSolution = false)
                synchronized(sessionCreatedImagePaths) { sessionCreatedImagePaths.add(path) }
                _uiState.update { it.copy(soruImagePath = path, errorMessage = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Görsel kaydedilemedi: ${e.localizedMessage}") }
            } catch (oom: OutOfMemoryError) {
                _uiState.update { it.copy(errorMessage = "Görsel çok büyük, sıkıştırılamadı.") }
            }
        }
    }

    fun setSolutionImageUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val path = gorselDepo.saveImageFromUri(uri, isSolution = true)
                synchronized(sessionCreatedImagePaths) { sessionCreatedImagePaths.add(path) }
                _uiState.update { it.copy(solutionImagePath = path, errorMessage = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Çözüm görseli kaydedilemedi: ${e.localizedMessage}") }
            } catch (oom: OutOfMemoryError) {
                _uiState.update { it.copy(errorMessage = "Görsel çok büyük, sıkıştırılamadı.") }
            }
        }
    }

    fun setSoruImagePath(path: String) {
        synchronized(sessionCreatedImagePaths) { sessionCreatedImagePaths.add(path) }
        _uiState.update { it.copy(soruImagePath = path, errorMessage = null) }
    }

    fun setSolutionImagePath(path: String) {
        synchronized(sessionCreatedImagePaths) { sessionCreatedImagePaths.add(path) }
        _uiState.update { it.copy(solutionImagePath = path, errorMessage = null) }
    }

    fun removeSoruImage() {
        val currentPath = _uiState.value.soruImagePath
        if (currentPath != null) {
            synchronized(sessionCreatedImagePaths) { sessionCreatedImagePaths.remove(currentPath) }
            viewModelScope.launch { gorselDepo.gorselSil(currentPath) }
        }
        _uiState.update { it.copy(soruImagePath = null, extractedText = null) }
    }

    fun removeSolutionImage() {
        val currentPath = _uiState.value.solutionImagePath
        if (currentPath != null) {
            synchronized(sessionCreatedImagePaths) { sessionCreatedImagePaths.remove(currentPath) }
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
            val units = konuKatalog.getUniteList(examType, subjectId)
            val catalogTopics = units.flatMap { it.konular }
            val userTopics = try {
                soruRepository.getDistinctTopics(subjectId).first()
            } catch (e: Exception) {
                emptyList()
            }
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
        _uiState.update {
            it.copy(
                kaynakSoruNo = no,
                denemeSoruNo = if (it.denemeId != null && it.denemeSoruNo.isBlank()) no else it.denemeSoruNo
            )
        }
    }

    fun setDenemeSoruNo(no: String) {
        _uiState.update {
            it.copy(
                denemeSoruNo = no,
                kaynakSoruNo = no
            )
        }
    }

    /**
     * Deneme sınavından veya OMR optik sonuç ekranından doğrudan yanlış/boş soru eklenirken
     * deneme kimliğini, yayın/deneme adını, kitapçık soru numarasını ve varsa ders/konu/cevap
     * bilgilerini otomatik doldurur.
     */
    fun setDenemeContext(
        denemeId: Long? = null,
        denemeBaslik: String? = null,
        denemeYayin: String? = null,
        denemeSoruNo: Int? = null,
        examType: String? = null,
        subjectId: String? = null,
        topic: String? = null,
        correctAnswer: String? = null,
        studentAnswer: String? = null
    ) {
        _uiState.update { current ->
            val resolvedExamType = examType?.takeIf { it.isNotBlank() } ?: current.examType
            val subjects = DersTaksonomisi.getDersler(resolvedExamType, current.userAlan)
            val resolvedSubjectId = when {
                !subjectId.isNullOrBlank() && subjects.any { it.id.equals(subjectId, ignoreCase = true) } ->
                    subjects.first { it.id.equals(subjectId, ignoreCase = true) }.id
                !subjectId.isNullOrBlank() -> subjectId
                subjects.any { it.id == current.subjectId } -> current.subjectId
                else -> subjects.firstOrNull()?.id ?: current.subjectId
            }
            val soruNoStr = denemeSoruNo?.takeIf { it > 0 }?.toString() ?: current.denemeSoruNo

            current.copy(
                denemeId = denemeId ?: current.denemeId,
                kaynakDeneme = denemeBaslik?.takeIf { it.isNotBlank() } ?: current.kaynakDeneme,
                kaynakYayin = denemeYayin?.takeIf { it.isNotBlank() } ?: current.kaynakYayin,
                denemeSoruNo = soruNoStr,
                kaynakSoruNo = soruNoStr.ifBlank { current.kaynakSoruNo },
                examType = resolvedExamType,
                availableSubjects = subjects,
                subjectId = resolvedSubjectId,
                topic = topic?.takeIf { it.isNotBlank() } ?: current.topic,
                correctAnswer = correctAnswer?.takeIf { it.isNotBlank() } ?: current.correctAnswer,
                studentAnswer = studentAnswer?.takeIf { it.isNotBlank() } ?: current.studentAnswer
            )
        }
        updateTopicSuggestions()
    }

    fun clearDenemeContext() {
        _uiState.update {
            it.copy(
                denemeId = null,
                denemeSoruNo = ""
            )
        }
    }

    fun openCropper(isSolution: Boolean) {
        _uiState.update { it.copy(isCropperOpen = true, cropTargetIsSolution = isSolution) }
    }

    fun closeCropper() {
        _uiState.update { it.copy(isCropperOpen = false) }
    }

    /**
     * Kırpma sonrası String path akışı (cache dosya -> kalıcı GorselDepo kopya, uiState güncelle)
     */
    fun applyCroppedImage(newPath: String) {
        viewModelScope.launch {
            try {
                val isSolution = _uiState.value.cropTargetIsSolution
                val permanentPath = try {
                    val file = File(newPath)
                    if (file.exists() && file.isFile) {
                        // cache'den kalıcıya kopyala ve sıkıştır
                        gorselDepo.kaydetDosyadan(file, isSolution)
                    } else newPath
                } catch (_: Exception) { newPath }
                synchronized(sessionCreatedImagePaths) { sessionCreatedImagePaths.add(permanentPath) }
                _uiState.update {
                    if (isSolution) {
                        it.copy(solutionImagePath = permanentPath, isCropperOpen = false, errorMessage = null)
                    } else {
                        it.copy(soruImagePath = permanentPath, isCropperOpen = false, errorMessage = null)
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isCropperOpen = false, errorMessage = "Kırpma kaydedilemedi: ${e.localizedMessage}") }
            } catch (oom: OutOfMemoryError) {
                _uiState.update { it.copy(isCropperOpen = false, errorMessage = "Kırpma bellek yetersiz.") }
            }
        }
    }

    fun openBatchCropper() {
        _uiState.update { it.copy(isBatchCropperOpen = true) }
    }

    fun closeBatchCropper() {
        _uiState.update { it.copy(isBatchCropperOpen = false) }
    }

    fun applyBatchCroppedImages(paths: List<String>) {
        if (paths.isEmpty()) {
            closeBatchCropper()
            return
        }
        // PERF: Dosya taşıma/karşılaştırma IO'dadır (önceden Main'de jank).
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val permanentPaths = paths.map { p ->
                    try {
                        val file = File(p)
                        if (file.exists() && file.isFile) {
                            gorselDepo.kaydetDosyadan(file, isSolution = false)
                        } else p
                    } catch (_: Exception) { p }
                }
                synchronized(sessionCreatedImagePaths) {
                    sessionCreatedImagePaths.addAll(permanentPaths)
                }
                val first = permanentPaths.first()
                val remaining = permanentPaths.drop(1)
                _uiState.update {
                    it.copy(
                        soruImagePath = first,
                        pendingBatchImages = remaining,
                        totalBatchCount = permanentPaths.size,
                        currentBatchIndex = 1,
                        isBatchCropperOpen = false,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isBatchCropperOpen = false, errorMessage = "Çoklu kırpma hatası: ${e.localizedMessage}") }
            }
        }
    }

    fun loadNextBatchImage() {
        val state = _uiState.value
        val remaining = state.pendingBatchImages
        if (remaining.isNotEmpty()) {
            val nextPath = remaining.first()
            val nextRemaining = remaining.drop(1)
            val nextSoruNoStr = (state.denemeSoruNo.toIntOrNull() ?: state.kaynakSoruNo.toIntOrNull())
                ?.let { (it + 1).toString() } ?: ""
            _uiState.update {
                it.copy(
                    soruImagePath = nextPath,
                    solutionImagePath = null,
                    extractedText = null,
                    correctAnswer = null,
                    studentAnswer = null,
                    denemeSoruNo = if (it.denemeId != null && nextSoruNoStr.isNotBlank()) nextSoruNoStr else it.denemeSoruNo,
                    kaynakSoruNo = if (nextSoruNoStr.isNotBlank()) nextSoruNoStr else it.kaynakSoruNo,
                    pendingBatchImages = nextRemaining,
                    currentBatchIndex = it.currentBatchIndex + 1,
                    isSaved = false
                )
            }
        }
    }

    /**
     * Kırpma sonrası Bitmap akışı: GorselDepo.kaydetBitmap ile kaydet ve uiState güncelle
     * ImageCropperDialog doğru Uri ile çağrıldıktan sonra bitmap döndüğünde kullanılır.
     */
    fun onCropComplete(croppedBitmap: Bitmap) {
        viewModelScope.launch {
            try {
                val isSolution = _uiState.value.cropTargetIsSolution
                val newPath = gorselDepo.kaydetBitmap(croppedBitmap, isSolution)
                synchronized(sessionCreatedImagePaths) { sessionCreatedImagePaths.add(newPath) }
                _uiState.update {
                    if (isSolution) {
                        it.copy(solutionImagePath = newPath, isCropperOpen = false, errorMessage = null)
                    } else {
                        it.copy(soruImagePath = newPath, isCropperOpen = false, errorMessage = null)
                    }
                }
                try { if (!croppedBitmap.isRecycled) croppedBitmap.recycle() } catch (_: Exception) {}
            } catch (e: Exception) {
                _uiState.update { it.copy(isCropperOpen = false, errorMessage = "Kırpma kaydedilemedi: ${e.localizedMessage}") }
            } catch (oom: OutOfMemoryError) {
                _uiState.update { it.copy(isCropperOpen = false, errorMessage = "Kırpma bellek yetersiz.") }
            }
        }
    }

    // Alias for screen
    fun handleCroppedBitmap(bitmap: Bitmap) = onCropComplete(bitmap)

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
                        kaynakSoruNo = (soru.kaynakSoruNo ?: soru.denemeSoruNo)?.toString() ?: "",
                        denemeId = soru.denemeId,
                        denemeSoruNo = (soru.denemeSoruNo ?: soru.kaynakSoruNo)?.toString() ?: "",
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
        if (state.isSaving || state.isSaved) return
        val imagePath = state.soruImagePath
        if (imagePath.isNullOrBlank()) {
            _uiState.update { it.copy(errorMessage = "Lütfen bir soru fotoğrafı ekleyin.") }
            return
        }
        if (state.topic.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Lütfen bir konu başlığı seçin veya yazın.") }
            return
        }
        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val yilParsed = state.kaynakYil.trim().toIntOrNull()
                val soruNoParsed = state.kaynakSoruNo.trim().toIntOrNull()
                val existingSoru = state.editingSoruId?.let { soruRepository.getSoru(it) }
                val denemeSoruNoParsed = state.denemeSoruNo.trim().toIntOrNull()
                    ?: soruNoParsed
                    ?: existingSoru?.denemeSoruNo
                val finalDenemeId = state.denemeId ?: existingSoru?.denemeId
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
                    kaynakSoruNo = soruNoParsed ?: denemeSoruNoParsed,
                    ocrMetni = state.extractedText?.trim()?.ifBlank { null },
                    isOcrUsed = !state.extractedText.isNullOrBlank() && !state.sendImageToAi,
                    questionText = state.extractedText?.trim()?.ifBlank { null },
                    audioPath = existingSoru?.audioPath,
                    fsrsStability = existingSoru?.fsrsStability,
                    fsrsDifficulty = existingSoru?.fsrsDifficulty,
                    fsrsReps = existingSoru?.fsrsReps ?: 0,
                    fsrsLapses = existingSoru?.fsrsLapses ?: 0,
                    createdAt = existingSoru?.createdAt ?: System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    contentHash = existingSoru?.contentHash ?: "",
                    evrenselId = existingSoru?.evrenselId ?: "",
                    isDeleted = existingSoru?.isDeleted ?: 0,
                    denemeId = finalDenemeId,
                    denemeSoruNo = denemeSoruNoParsed
                )
                if (state.editingSoruId != null) {
                    if (existingSoru != null) {
                        if (existingSoru.imagePath != imagePath) {
                            gorselDepo.gorselSil(existingSoru.imagePath)
                        }
                        if (!existingSoru.solutionImagePath.isNullOrBlank() && existingSoru.solutionImagePath != state.solutionImagePath) {
                            gorselDepo.gorselSil(existingSoru.solutionImagePath)
                        }
                    }
                    soruRepository.updateSoru(soru)
                } else {
                    val newId = soruRepository.soruEkle(soru)
                    badgeManager?.onQuestionAdded()
                    maybeEnqueueAutoSolve(newId, soru, state)
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
            } catch (oom: OutOfMemoryError) {
                _uiState.update { it.copy(isSaving = false, errorMessage = "Bellek yetersiz, soru kaydedilemedi.") }
            }
        }
    }

    /**
     * "Kaydederken otomatik çöz" açıksa yeni eklenen soruyu arka planda AI çözüm kuyruğuna ekler.
     * Hata olsa bile kaydetme akışını etkilemez; kullanıcı beklemez.
     */
    private suspend fun maybeEnqueueAutoSolve(newId: Long, soru: Soru, state: SoruEklemeUiState) {
        val ctx = appContext ?: return
        if (newId <= 0L) return
        try {
            if (ayarlarDataStore.getAutoSolveOnSave() &&
                aiRepository.hasApiKey() &&
                soru.aiExplanation.isNullOrBlank()
            ) {
                AiSolutionRetryWorker.enqueueAutoSolve(
                    context = ctx,
                    soruId = newId,
                    sendImageToAi = state.sendImageToAi,
                    extractedText = state.extractedText?.trim()?.ifBlank { null }
                )
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("SoruEklemeViewModel", "Otomatik AI çözüm kuyruğa alınamadı", e)
        }
    }
}
