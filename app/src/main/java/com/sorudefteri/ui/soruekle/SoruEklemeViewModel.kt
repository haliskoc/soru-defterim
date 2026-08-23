package com.sorudefteri.ui.soruekle

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sorudefteri.data.images.GorselDepo
import com.sorudefteri.data.local.AyarlarDataStore
import com.sorudefteri.data.repository.SoruRepository
import com.sorudefteri.domain.DersTaksonomisi
import com.sorudefteri.domain.KonuKatalog
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
    val availableSubjects: List<Ders> = emptyList(),
    val userAlan: Alan = Alan.SAYISAL,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessage: String? = null
) {
    val canSave: Boolean
        get() = !soruImagePath.isNullOrBlank() && topic.isNotBlank() && !isSaving
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
        val firstSubject = subjects.firstOrNull()?.id ?: ""

        _uiState.update {
            it.copy(
                examType = type,
                availableSubjects = subjects,
                subjectId = firstSubject,
                topic = ""
            )
        }
        updateTopicSuggestions()
    }

    fun setSubject(subjectId: String) {
        _uiState.update { it.copy(subjectId = subjectId, topic = "") }
        updateTopicSuggestions()
    }

    fun setTopic(topic: String) {
        _uiState.update { it.copy(topic = topic, errorMessage = null) }
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

            // 1. Konu kataloğundaki öneriler (konular.json)
            val catalogTopics = konuKatalog.getTopics(examType, subjectId)

            // 2. Kullanıcının Room'daki geçmiş konuları
            val userTopics = try {
                soruRepository.getDistinctTopics(subjectId).first()
            } catch (e: Exception) {
                emptyList()
            }

            // Birleştir ve tekilleştir (sıralı olarak katalog önde)
            val combined = (catalogTopics + userTopics).distinct().filter { it.isNotBlank() }

            _uiState.update { it.copy(topicSuggestions = combined) }
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
            _uiState.update { it.copy(errorMessage = "Lütfen bir konu başlığı girin.") }
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
