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
    val onlyFavorites: Boolean = false,
    val onlyDueReview: Boolean = false,
    val sortOrder: SortOrder = SortOrder.DATE_DESC,
    val isLoading: Boolean = true,
    val availableSubjects: List<Ders> = emptyList(),
    val totalCount: Int = 0,
    val solvedCount: Int = 0,
    val unsolvedCount: Int = 0,
    val favoriteCount: Int = 0,
    val dueReviewCount: Int = 0,
    val selectedSoruIds: Set<Long> = emptySet(),
    val isSelectionMode: Boolean = false
) {
    val isAllSelected: Boolean
        get() = filteredSorular.isNotEmpty() && selectedSoruIds.size == filteredSorular.size
}

private data class FilterParams(
    val query: String,
    val examType: String?,
    val subjectId: String?,
    val mistakeType: HataTipi?,
    val onlyUnsolved: Boolean,
    val onlyFavorites: Boolean,
    val onlyDueReview: Boolean,
    val sortOrder: SortOrder,
    val selectedIds: Set<Long>,
    val isSelectionMode: Boolean
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
    private val _onlyFavorites = MutableStateFlow(false)
    private val _onlyDueReview = MutableStateFlow(false)
    private val _sortOrder = MutableStateFlow(SortOrder.DATE_DESC)
    private val _selectedSoruIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _isSelectionMode = MutableStateFlow(false)

    private val filterParamsFlow = combine(
        combine(
            _searchQuery,
            _selectedExamType,
            _selectedSubjectId,
            _selectedMistakeType,
            _onlyUnsolved
        ) { query, examType, subjectId, mistakeType, onlyUnsolved ->
            Tuple5(query, examType, subjectId, mistakeType, onlyUnsolved)
        },
        combine(
            _onlyFavorites,
            _onlyDueReview,
            _sortOrder,
            _selectedSoruIds,
            _isSelectionMode
        ) { onlyFavorites, onlyDueReview, sortOrder, selectedIds, isSelectionMode ->
            Tuple5(onlyFavorites, onlyDueReview, sortOrder, selectedIds, isSelectionMode)
        }
    ) { (query, examType, subjectId, mistakeType, onlyUnsolved),
        (onlyFavorites, onlyDueReview, sortOrder, selectedIds, isSelectionMode) ->
        FilterParams(
            query = query,
            examType = examType,
            subjectId = subjectId,
            mistakeType = mistakeType,
            onlyUnsolved = onlyUnsolved,
            onlyFavorites = onlyFavorites,
            onlyDueReview = onlyDueReview,
            sortOrder = sortOrder,
            selectedIds = selectedIds,
            isSelectionMode = isSelectionMode
        )
    }

    val uiState: StateFlow<KutuphaneUiState> = combine(
        soruRepository.getAllSorular(),
        filterParamsFlow
    ) { sorular, filters ->
        val availableSubjects = if (filters.examType == null) {
            DersTaksonomisi.TYT_DERSLER + DersTaksonomisi.getAytDersler(Alan.SAYISAL)
        } else {
            DersTaksonomisi.getDersler(filters.examType, Alan.SAYISAL)
        }

        val trLocale = Locale("tr", "TR")
        val cleanQuery = filters.query.lowercase(trLocale).trim()
        val hasQuery = cleanQuery.isNotBlank()

        val filtered = sorular.filter { soru ->
            val matchesExamType = filters.examType == null || soru.examType.equals(filters.examType, ignoreCase = true)
            val matchesSubject = filters.subjectId == null || soru.subjectId.equals(filters.subjectId, ignoreCase = true)
            val matchesMistake = filters.mistakeType == null || soru.mistakeType == filters.mistakeType
            val matchesSolved = !filters.onlyUnsolved || !soru.isSolved
            val matchesFavorites = !filters.onlyFavorites || soru.isFavorite
            val matchesDueReview = !filters.onlyDueReview || soru.isDueForReview

            val matchesQuery = if (!hasQuery) {
                true
            } else {
                val dersName = DersTaksonomisi.getDersAdi(soru.subjectId).lowercase(trLocale)
                val topic = soru.topic.lowercase(trLocale)
                val note = soru.note?.lowercase(trLocale) ?: ""

                dersName.contains(cleanQuery) ||
                        topic.contains(cleanQuery) ||
                        note.contains(cleanQuery) ||
                        soru.examType.lowercase(trLocale).contains(cleanQuery)
            }

            matchesExamType && matchesSubject && matchesMistake && matchesSolved && matchesFavorites && matchesDueReview && matchesQuery
        }

        val sorted = when (filters.sortOrder) {
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
        val favorites = sorular.count { it.isFavorite }
        val dueReviews = sorular.count { it.isDueForReview }

        KutuphaneUiState(
            tumSorular = sorular,
            filteredSorular = sorted,
            searchQuery = filters.query,
            selectedExamType = filters.examType,
            selectedSubjectId = filters.subjectId,
            selectedMistakeType = filters.mistakeType,
            onlyUnsolved = filters.onlyUnsolved,
            onlyFavorites = filters.onlyFavorites,
            onlyDueReview = filters.onlyDueReview,
            sortOrder = filters.sortOrder,
            isLoading = false,
            availableSubjects = availableSubjects,
            totalCount = total,
            solvedCount = solved,
            unsolvedCount = unsolved,
            favoriteCount = favorites,
            dueReviewCount = dueReviews,
            selectedSoruIds = filters.selectedIds,
            isSelectionMode = filters.isSelectionMode
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

    fun onOnlyFavoritesToggle(onlyFavorites: Boolean) {
        _onlyFavorites.value = onlyFavorites
    }

    fun onOnlyDueReviewToggle(onlyDueReview: Boolean) {
        _onlyDueReview.value = onlyDueReview
    }

    fun onSortOrderChange(sortOrder: SortOrder) {
        _sortOrder.value = sortOrder
    }

    fun resetFilters() {
        _selectedExamType.value = null
        _selectedSubjectId.value = null
        _selectedMistakeType.value = null
        _onlyUnsolved.value = false
        _onlyFavorites.value = false
        _onlyDueReview.value = false
        _searchQuery.value = ""
    }

    fun toggleSelectionMode(enabled: Boolean) {
        _isSelectionMode.value = enabled
        if (!enabled) {
            _selectedSoruIds.value = emptySet()
        }
    }

    fun toggleSelectSoru(id: Long) {
        val current = _selectedSoruIds.value.toMutableSet()
        if (current.contains(id)) {
            current.remove(id)
        } else {
            current.add(id)
        }
        _selectedSoruIds.value = current
        if (current.isNotEmpty()) {
            _isSelectionMode.value = true
        }
    }

    fun selectAll() {
        val allIds = uiState.value.filteredSorular.map { it.id }.toSet()
        _selectedSoruIds.value = allIds
        if (allIds.isNotEmpty()) {
            _isSelectionMode.value = true
        }
    }

    fun clearSelection() {
        _selectedSoruIds.value = emptySet()
        _isSelectionMode.value = false
    }

    fun batchDeleteSelected() {
        val ids = _selectedSoruIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            soruRepository.deleteSorularByIds(ids)
            clearSelection()
        }
    }

    fun batchMarkSolvedSelected(isSolved: Boolean) {
        val ids = _selectedSoruIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            soruRepository.updateSolvedStatusBatch(ids, isSolved)
            clearSelection()
        }
    }

    fun batchStarSelected(isFavorite: Boolean) {
        val ids = _selectedSoruIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            soruRepository.updateFavoriteStatusBatch(ids, isFavorite)
            clearSelection()
        }
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

    fun toggleFavorite(soru: Soru) {
        viewModelScope.launch {
            soruRepository.toggleFavorite(soru.id, !soru.isFavorite)
        }
    }

    fun deleteSoru(soru: Soru) {
        viewModelScope.launch {
            soruRepository.soruSil(soru)
        }
    }
}

private data class Tuple5<A, B, C, D, E>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E
)

