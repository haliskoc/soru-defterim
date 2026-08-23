package com.sorudefteri.ui.soruekle

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sorudefteri.data.images.GorselDepo
import com.sorudefteri.data.local.AyarlarDataStore
import com.sorudefteri.data.repository.SoruRepository
import com.sorudefteri.domain.DersTaksonomisi
import com.sorudefteri.domain.KonuKatalog
import com.sorudefteri.domain.KonuUnite
import com.sorudefteri.domain.model.Alan
import com.sorudefteri.domain.model.Ders
import com.sorudefteri.domain.model.HataTipi
import com.sorudefteri.domain.model.Soru
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val soruImagePath: String? = null,
    val solutionImagePath: String? = null,
    val examType: String = "TYT", // "TYT" or "AYT"
    val subjectId: String = "matematik",
    val topic: String = "",
    val mistakeType: HataTipi? = null,
    val correctAnswer: String? = null,
    val note: String = "",
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
    val errorMessage: String? = null
) {
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
    private val ayarlarDataStore: AyarlarDataStore
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
                if (uriOrPath.startsWith("content://") || uriOrPath.startsWith("file://")) {
                    val uri = Uri.parse(uriOrPath)
                    val path = gorselDepo.saveImageFromUri(uri, isSolution = false)
                    _uiState.update { it.copy(soruImagePath = path, errorMessage = null) }
                } else if (File(uriOrPath).exists() || uriOrPath.startsWith("/")) {
                    _uiState.update { it.copy(soruImagePath = uriOrPath, errorMessage = null) }
                } else {
                    val uri = Uri.parse(uriOrPath)
                    val path = gorselDepo.saveImageFromUri(uri, isSolution = false)
                    _uiState.update { it.copy(soruImagePath = path, errorMessage = null) }
                }
            } catch (e: Exception) {
                // Hata durumunda dosya yolu olarak doğrudan kullanmayı dene
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
                if (uriOrPath.startsWith("content://") || uriOrPath.startsWith("file://")) {
                    val uri = Uri.parse(uriOrPath)
                    val path = gorselDepo.saveImageFromUri(uri, isSolution = true)
                    _uiState.update { it.copy(solutionImagePath = path, errorMessage = null) }
                } else if (File(uriOrPath).exists() || uriOrPath.startsWith("/")) {
                    _uiState.update { it.copy(solutionImagePath = uriOrPath, errorMessage = null) }
                } else {
                    val uri = Uri.parse(uriOrPath)
                    val path = gorselDepo.saveImageFromUri(uri, isSolution = true)
                    _uiState.update { it.copy(solutionImagePath = path, errorMessage = null) }
                }
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
        _uiState.update { it.copy(soruImagePath = null) }
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

    fun setCorrectAnswer(answer: String?) {
        _uiState.update { it.copy(correctAnswer = answer) }
    }

    fun setNote(note: String) {
        _uiState.update { it.copy(note = note) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun updateTopicSuggestions() {
        viewModelScope.launch {
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
                val soru = Soru(
                    imagePath = imagePath,
                    solutionImagePath = state.solutionImagePath,
                    correctAnswer = state.correctAnswer,
                    examType = state.examType,
                    subjectId = state.subjectId,
                    topic = state.topic.trim(),
                    mistakeType = state.mistakeType,
                    note = state.note.trim().ifBlank { null },
                    isSolved = false,
                    solvedAt = null,
                    aiExplanation = null,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                soruRepository.soruEkle(soru)
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
