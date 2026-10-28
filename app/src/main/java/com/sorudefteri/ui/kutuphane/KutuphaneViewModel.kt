package com.sorudefteri.ui.kutuphane

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sorudefteri.data.local.AyarlarDataStore
import com.sorudefteri.data.repository.SoruRepository
import com.sorudefteri.domain.DersTaksonomisi
import com.sorudefteri.domain.model.Alan
import com.sorudefteri.domain.model.Ders
import com.sorudefteri.domain.model.HataTipi
import com.sorudefteri.domain.model.SortOrder
import com.sorudefteri.domain.model.Soru
import com.sorudefteri.domain.model.TestFiltre
import com.sorudefteri.domain.model.TestQuestion
import com.sorudefteri.domain.repository.TestSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/**
 * Kütüphane ekranı UI durumu.
 *
 * PERF: Tam listeler (tumSorular/filteredSorular) state'ten KALDIRILDI.
 * Liste yalnızca Paging (`pagedSorular`) ile beslenir; meta sayılar ve
 * filtre seçenekleri tekil Room sorgularıyla gelir. Her keystroke'ta
 * tam listeli state yayını bitmiştir.
 */
data class KutuphaneUiState(
    val searchQuery: String = "",
    val selectedExamType: String? = null, // null: Tümü, "TYT", "AYT"
    val selectedSubjectId: String? = null,
    val selectedMistakeType: HataTipi? = null,
    val selectedPublisher: String? = null,
    val selectedExamName: String? = null,
    val onlyUnsolved: Boolean = false,
    val onlyFavorites: Boolean = false,
    val onlyDueReview: Boolean = false,
    val sortOrder: SortOrder = SortOrder.DATE_DESC,
    val isLoading: Boolean = true,
    val availableSubjects: List<Ders> = emptyList(),
    val availablePublishers: List<String> = emptyList(),
    val availableExamNames: List<String> = emptyList(),
    val totalCount: Int = 0,
    val solvedCount: Int = 0,
    val unsolvedCount: Int = 0,
    val favoriteCount: Int = 0,
    val dueReviewCount: Int = 0,
    val selectedSoruIds: Set<Long> = emptySet(),
    val isSelectionMode: Boolean = false,
    /** "Tümünü Seç" yalnızca filtre eşleşen id sayısını bilir (tam liste yok). */
    val filteredIdCount: Int = 0
) {
    val isAllSelected: Boolean
        get() = filteredIdCount > 0 && selectedSoruIds.size >= filteredIdCount
}

private data class FilterParams(
    val query: String,
    val examType: String?,
    val subjectId: String?,
    val mistakeType: HataTipi?,
    val publisher: String?,
    val examName: String?,
    val onlyUnsolved: Boolean,
    val onlyFavorites: Boolean,
    val onlyDueReview: Boolean,
    val sortOrder: SortOrder
)

@OptIn(FlowPreview::class)
@HiltViewModel
class KutuphaneViewModel @Inject constructor(
    private val soruRepository: SoruRepository,
    private val ayarlarDataStore: AyarlarDataStore,
    private val testSessionManager: TestSessionManager,
    private val defaultDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Arama temizlendiğinde anında (0ms), yazarken 300ms debounce uygula
    private val _debouncedSearchQuery = _searchQuery.debounce { query ->
        if (query.isBlank()) 0L else 300L
    }

    private val _selectedExamType = MutableStateFlow<String?>(null)
    private val _selectedSubjectId = MutableStateFlow<String?>(null)
    private val _selectedMistakeType = MutableStateFlow<HataTipi?>(null)
    private val _selectedPublisher = MutableStateFlow<String?>(null)
    private val _selectedExamName = MutableStateFlow<String?>(null)
    private val _onlyUnsolved = MutableStateFlow(false)
    private val _onlyFavorites = MutableStateFlow(false)
    private val _onlyDueReview = MutableStateFlow(false)
    private val _sortOrder = MutableStateFlow(SortOrder.DATE_DESC)
    private val _selectedSoruIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _isSelectionMode = MutableStateFlow(false)

    private val _uiEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val uiEvent: SharedFlow<String> = _uiEvent.asSharedFlow()

    private var lastDeletedSoru: Soru? = null

    // Alan-aware: track current Alan for subject availability
    private val _currentAlan = MutableStateFlow(Alan.SAYISAL)

    init {
        viewModelScope.launch {
            ayarlarDataStore.currentAlan.collect { alan ->
                _currentAlan.value = alan
            }
        }
    }

    // Yalnızca filtreleme ve sıralamayı etkileyen parametreleri birleştir (seçim durumu burayı tetiklemez)
    private val filterParamsFlow = combine(
        combine(
            _debouncedSearchQuery,
            _selectedExamType,
            _selectedSubjectId,
            _selectedMistakeType,
            _selectedPublisher
        ) { query, examType, subjectId, mistakeType, publisher ->
            Tuple5(query, examType, subjectId, mistakeType, publisher)
        },
        combine(
            _selectedExamName,
            _onlyUnsolved,
            _onlyFavorites,
            _onlyDueReview,
            _sortOrder
        ) { examName, onlyUnsolved, onlyFavorites, onlyDueReview, sortOrder ->
            Tuple5(examName, onlyUnsolved, onlyFavorites, onlyDueReview, sortOrder)
        }
    ) { (query, examType, subjectId, mistakeType, publisher),
        (examName, onlyUnsolved, onlyFavorites, onlyDueReview, sortOrder) ->
        FilterParams(
            query = query,
            examType = examType,
            subjectId = subjectId,
            mistakeType = mistakeType,
            publisher = publisher,
            examName = examName,
            onlyUnsolved = onlyUnsolved,
            onlyFavorites = onlyFavorites,
            onlyDueReview = onlyDueReview,
            sortOrder = sortOrder
        )
    }

    /**
     * PERF: Tam-liste filtreleme KALDIRILDI. Liste yolu yalnızca Paging'dir.
     * Meta sayılar + filtre seçenekleri tekil Room sorgularıyla (IO) gelir;
     * ders listesi saf CPU işidir (Default).
     */
    private val metaFlow: Flow<com.sorudefteri.domain.model.KutuphaneMeta> =
        soruRepository.totalCount
            .flatMapLatest { soruRepository.getKutuphaneMetaFlow() }
            .flowOn(defaultDispatcher)

    // UI durumu: meta + filtre + ham arama metni + seçim (tam liste YOK)
    val uiState: StateFlow<KutuphaneUiState> = combine(
        metaFlow,
        filterParamsFlow,
        _searchQuery,
        _selectedSoruIds,
        _isSelectionMode,
        _currentAlan
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val a = args as Array<Any?>
        val meta = a[0] as com.sorudefteri.domain.model.KutuphaneMeta
        val filters = a[1] as FilterParams
        val rawQuery = a[2] as String
        @Suppress("UNCHECKED_CAST")
        val selectedIds = a[3] as Set<Long>
        val isSelectionMode = a[4] as Boolean
        val currentAlan = a[5] as Alan
        val availableSubjects = when {
            currentAlan == Alan.LGS || filters.examType.equals("LGS", ignoreCase = true) -> DersTaksonomisi.lgsDersleri
            currentAlan == Alan.KPSS || filters.examType.equals("KPSS", ignoreCase = true) -> DersTaksonomisi.kpssDersleri
            filters.examType == null -> {
                DersTaksonomisi.tytDersleri + DersTaksonomisi.getAytDersler(currentAlan)
            }
            else -> DersTaksonomisi.getDersler(filters.examType, currentAlan)
        }.distinctBy { it.id }
        KutuphaneUiState(
            searchQuery = rawQuery,
            selectedExamType = filters.examType,
            selectedSubjectId = filters.subjectId,
            selectedMistakeType = filters.mistakeType,
            selectedPublisher = filters.publisher,
            selectedExamName = filters.examName,
            onlyUnsolved = filters.onlyUnsolved,
            onlyFavorites = filters.onlyFavorites,
            onlyDueReview = filters.onlyDueReview,
            sortOrder = filters.sortOrder,
            isLoading = false,
            availableSubjects = availableSubjects,
            availablePublishers = meta.availablePublishers,
            availableExamNames = meta.availableExamNames,
            totalCount = meta.totalCount,
            solvedCount = meta.solvedCount,
            unsolvedCount = meta.unsolvedCount,
            favoriteCount = meta.favoriteCount,
            dueReviewCount = meta.dueReviewCount,
            selectedSoruIds = selectedIds,
            isSelectionMode = isSelectionMode
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = KutuphaneUiState()
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val pagedSorular: Flow<PagingData<Soru>> = filterParamsFlow
        .flatMapLatest { filters ->
            soruRepository.getPagedSorular(
                query = filters.query.ifBlank { null },
                examType = filters.examType,
                subjectId = filters.subjectId,
                mistakeType = filters.mistakeType?.name,
                isSolved = if (filters.onlyUnsolved) false else null,
                isFavorite = if (filters.onlyFavorites) true else null,
                onlyDueReview = if (filters.onlyDueReview) true else null,
                kaynak = filters.publisher ?: filters.examName,
                sortOrder = filters.sortOrder.name
            )
        }
        .cachedIn(viewModelScope)

    val currentStreak: StateFlow<Int> = ayarlarDataStore.currentStreak.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    val streakShieldCount: StateFlow<Int> = ayarlarDataStore.streakShieldCount.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 1
    )

    fun onSearchQueryChange(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun onExamTypeSelected(examType: String?) {
        _selectedExamType.value = examType
        if (examType != null && _selectedSubjectId.value != null) {
            val currentAlan = _currentAlan.value
            val dersler = DersTaksonomisi.getDersler(examType, currentAlan)
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

    fun onPublisherSelected(publisher: String?) {
        _selectedPublisher.value = publisher
    }

    fun onExamNameSelected(examName: String?) {
        _selectedExamName.value = examName
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
        _selectedPublisher.value = null
        _selectedExamName.value = null
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
        _isSelectionMode.value = current.isNotEmpty()
    }

    /**
     * PERF: PDF/Anki dışa aktarma için filtreye uyan tam satırlar.
     * Yalnızca kullanıcı dışa aktarmaya dokunduğunda bir kez okunur;
     * sürekli state'te tam liste tutulmaz.
     */
    suspend fun getFilteredSorularForExport(): List<Soru> {
        val f = filterParamsFlow.first()
        return try {
            val ids = soruRepository.getFilteredSoruIds(
                query = f.query.ifBlank { null },
                examType = f.examType,
                subjectId = f.subjectId,
                mistakeType = f.mistakeType?.name,
                isSolved = if (f.onlyUnsolved) false else null,
                isFavorite = if (f.onlyFavorites) true else null,
                onlyDueReview = if (f.onlyDueReview) true else null,
                kaynak = f.publisher ?: f.examName
            )
            if (ids.isEmpty()) emptyList() else soruRepository.getSorularByIds(ids)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * PERF: Seçili id'lerin tam satırları (seçim çubuğu PDF/Anki).
     */
    suspend fun getSelectedSorular(): List<Soru> {
        val ids = _selectedSoruIds.value.toList()
        if (ids.isEmpty()) return emptyList()
        return try {
            soruRepository.getSorularByIds(ids)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * PERF: "Tümünü Seç" tam liste çekmez; filtreye uyan id'leri
     * tek DAO sorgusuyla alır.
     */
    fun selectAll() {
        viewModelScope.launch {
            val f = filterParamsFlow.first()
            val ids = try {
                soruRepository.getFilteredSoruIds(
                    query = f.query.ifBlank { null },
                    examType = f.examType,
                    subjectId = f.subjectId,
                    mistakeType = f.mistakeType?.name,
                    isSolved = if (f.onlyUnsolved) false else null,
                    isFavorite = if (f.onlyFavorites) true else null,
                    onlyDueReview = if (f.onlyDueReview) true else null,
                    kaynak = f.publisher ?: f.examName
                ).toSet()
            } catch (_: Exception) {
                emptySet()
            }
            _selectedSoruIds.value = ids
            if (ids.isNotEmpty()) {
                _isSelectionMode.value = true
            }
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
            lastDeletedSoru = soru
            soruRepository.soruSil(soru)
        }
    }

    fun undoDelete() {
        val toRestore = lastDeletedSoru ?: return
        viewModelScope.launch {
            soruRepository.soruEkle(toRestore)
            lastDeletedSoru = null
        }
    }

    /**
     * Spaced Repetition tekrar vakti gelmiş sorularla hızlı test oturumu başlatır.
     * Liste boşsa sessizce geçmek yerine kullanıcıya bilgi mesajı gönderir.
     */
    fun startDueReviewTest(onSuccess: () -> Unit) {
        viewModelScope.launch {
            val dueQuestions = try {
                soruRepository.getDueReviewSorularList()
            } catch (e: Exception) {
                _uiEvent.tryEmit("Tekrar listesi yüklenemedi: ${e.localizedMessage}")
                return@launch
            }
            if (dueQuestions.isNotEmpty()) {
                val testQuestions = dueQuestions.map { TestQuestion(soru = it) }
                val filter = TestFiltre(
                    examType = null,
                    selectedSubjects = emptySet(),
                    selectedTopics = emptySet(),
                    selectedMistakeTypes = emptySet(),
                    onlyUnsolved = false,
                    questionCount = testQuestions.size
                )
                testSessionManager.startSession(testQuestions, filter)
                onSuccess()
            } else {
                _uiEvent.tryEmit("Şu an tekrar vakti gelmiş soru yok. Yeni soru çözdükçe burada listelenecek.")
            }
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

