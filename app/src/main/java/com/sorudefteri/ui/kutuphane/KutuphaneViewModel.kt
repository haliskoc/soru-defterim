package com.sorudefteri.ui.kutuphane

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sorudefteri.data.repository.SoruRepository
import com.sorudefteri.domain.DersTaksonomisi
import com.sorudefteri.domain.model.Alan
import com.sorudefteri.domain.model.Ders
import com.sorudefteri.domain.model.HataTipi
import com.sorudefteri.domain.model.SortOrder
import com.sorudefteri.domain.model.Soru
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/**
 * Kütüphane ekranı UI durumu
 */
data class KutuphaneUiState(
    val tumSorular: List<Soru> = emptyList(),
    val filteredSorular: List<Soru> = emptyList(),
    val searchQuery: String = "",
    val selectedExamType: String? = null, // null: Tümü, "TYT", "AYT"
    val selectedSubjectId: String? = null,
    val selectedMistakeType: HataTipi? = null,
    val onlyUnsolved: Boolean = false,
    val sortOrder: SortOrder = SortOrder.DATE_DESC,
    val isLoading: Boolean = true,
    val availableSubjects: List<Ders> = emptyList(),
    val totalCount: Int = 0,
    val solvedCount: Int = 0,
    val unsolvedCount: Int = 0
)

@HiltViewModel
class KutuphaneViewModel @Inject constructor(
    private val soruRepository: SoruRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedExamType = MutableStateFlow<String?>(null)
    private val _selectedSubjectId = MutableStateFlow<String?>(null)
    private val _selectedMistakeType = MutableStateFlow<HataTipi?>(null)
    private val _onlyUnsolved = MutableStateFlow(false)
    private val _sortOrder = MutableStateFlow(SortOrder.DATE_DESC)

    private val sorularFlow = soruRepository.getAllSorular()

    val uiState: StateFlow<KutuphaneUiState> = combine(
        sorularFlow,
        _searchQuery,
        _selectedExamType,
        _selectedSubjectId,
        _selectedMistakeType,
        _onlyUnsolved,
        _sortOrder
    ) { params ->
        @Suppress("UNCHECKED_CAST")
        val sorular = params[0] as List<Soru>
        val query = params[1] as String
        val examType = params[2] as String?
        val subjectId = params[3] as String?
        val mistakeType = params[4] as HataTipi?
        val onlyUnsolved = params[5] as Boolean
        val sortOrder = params[6] as SortOrder

        val availableSubjects = if (examType == null) {
            DersTaksonomisi.TYT_DERSLER + DersTaksonomisi.getAytDersler(Alan.SAYISAL)
        } else {
            DersTaksonomisi.getDersler(examType, Alan.SAYISAL)
        }

        // Filtreleme mantığı
        val filtered = sorular.filter { soru ->
            // Sınav türü filtresi
            val matchesExamType = examType == null || soru.examType.equals(examType, ignoreCase = true)

            // Ders filtresi
            val matchesSubject = subjectId == null || soru.subjectId.equals(subjectId, ignoreCase = true)

            // Hata tipi filtresi
            val matchesMistake = mistakeType == null || soru.mistakeType == mistakeType

            // Sadece çözülmeyenler filtresi
            val matchesSolved = !onlyUnsolved || !soru.isSolved

            // Arama sorgusu (konu, ders adı, not içeriğinde arar)
            val matchesQuery = if (query.isBlank()) {
                true
            } else {
                val trLocale = Locale("tr", "TR")
                val cleanQuery = query.lowercase(trLocale).trim()
                val dersName = DersTaksonomisi.getDersAdi(soru.subjectId).lowercase(trLocale)
                val topic = soru.topic.lowercase(trLocale)
                val note = soru.note?.lowercase(trLocale) ?: ""

                dersName.contains(cleanQuery) ||
                        topic.contains(cleanQuery) ||
                        note.contains(cleanQuery) ||
                        soru.examType.lowercase(trLocale).contains(cleanQuery)
            }

            matchesExamType && matchesSubject && matchesMistake && matchesSolved && matchesQuery
        }

        // Sıralama mantığı
        val sorted = when (sortOrder) {
            SortOrder.DATE_DESC -> filtered.sortedByDescending { it.createdAt }
            SortOrder.DATE_ASC -> filtered.sortedBy { it.createdAt }
            SortOrder.SUBJECT -> filtered.sortedWith(
                compareBy(
                    { it.examType },
                    { DersTaksonomisi.getDersAdi(it.subjectId) },
                    { -it.createdAt }
                )
            )
        }

        val total = sorular.size
        val solved = sorular.count { it.isSolved }
        val unsolved = total - solved

        KutuphaneUiState(
            tumSorular = sorular,
            filteredSorular = sorted,
            searchQuery = query,
            selectedExamType = examType,
            selectedSubjectId = subjectId,
            selectedMistakeType = mistakeType,
            onlyUnsolved = onlyUnsolved,
            sortOrder = sortOrder,
            isLoading = false,
            availableSubjects = availableSubjects,
            totalCount = total,
            solvedCount = solved,
            unsolvedCount = unsolved
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = KutuphaneUiState()
    )

    fun onSearchQueryChange(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun onExamTypeSelected(examType: String?) {
        _selectedExamType.value = examType
        if (examType != null && _selectedSubjectId.value != null) {
            val dersler = DersTaksonomisi.getDersler(examType, Alan.SAYISAL)
            if (dersler.none { it.id == _selectedSubjectId.value }) {
                _selectedSubjectId.value = null
            }
        }
    }

    fun onSubjectSelected(subjectId: String?) {
        _selectedSubjectId.value = subjectId
    }

    fun onMistakeTypeSelected(mistakeType: HataTipi?) {
        _selectedMistakeType.value = mistakeType
    }

    fun onOnlyUnsolvedToggle(onlyUnsolved: Boolean) {
        _onlyUnsolved.value = onlyUnsolved
    }

    fun onSortOrderChange(sortOrder: SortOrder) {
        _sortOrder.value = sortOrder
    }

    fun resetFilters() {
        _selectedExamType.value = null
        _selectedSubjectId.value = null
        _selectedMistakeType.value = null
        _onlyUnsolved.value = false
        _searchQuery.value = ""
    }

    fun toggleSolved(soru: Soru) {
        viewModelScope.launch {
            val newSolvedState = !soru.isSolved
            val solvedAt = if (newSolvedState) System.currentTimeMillis() else null
            val updatedSoru = soru.copy(
                isSolved = newSolvedState,
                solvedAt = solvedAt,
                updatedAt = System.currentTimeMillis()
            )
            soruRepository.soruGuncelle(updatedSoru)
        }
    }

    fun deleteSoru(soru: Soru) {
        viewModelScope.launch {
            soruRepository.soruSil(soru)
        }
    }
}
