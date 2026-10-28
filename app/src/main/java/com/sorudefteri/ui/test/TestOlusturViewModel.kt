package com.sorudefteri.ui.test

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sorudefteri.data.local.AyarlarDataStore
import com.sorudefteri.domain.DersTaksonomisi
import com.sorudefteri.domain.KonuKatalog
import com.sorudefteri.domain.model.Alan
import com.sorudefteri.domain.model.Ders
import com.sorudefteri.domain.model.HataTipi
import com.sorudefteri.domain.model.Soru
import com.sorudefteri.domain.model.TestFiltre
import com.sorudefteri.domain.model.TestQuestion
import com.sorudefteri.domain.repository.SoruRepository
import com.sorudefteri.domain.repository.TestSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Ders ve o dersteki soru sayısını gösteren yardımcı model
 */
data class DersSecimItem(
    val ders: Ders,
    val totalCount: Int,
    val unsolvedCount: Int
)

/**
 * Test Oluşturma ekranının UI durumu
 */
data class TestOlusturUiState(
    val examType: String? = null, // "TYT", "AYT", null (Tümü)
    val selectedSubjects: Set<String> = emptySet(),
    val selectedTopics: Set<String> = emptySet(),
    val selectedMistakeTypes: Set<HataTipi> = emptySet(),
    val onlyUnsolved: Boolean = true,
    val questionCount: Int = 10, // 5, 10, 15, 20, 0 (0 = Tümü)
    val allQuestions: List<Soru> = emptyList(),
    val matchingQuestions: List<Soru> = emptyList(),
    val availableSubjects: List<DersSecimItem> = emptyList(),
    val availableTopics: List<String> = emptyList(),
    val dueReviewCount: Int = 0,
    val timeLimitPerQuestionSeconds: Long = 0L, // 0: Süresiz, 75: TYT, 120: AYT
    val currentStreak: Int = 0,
    val streakShieldCount: Int = 1,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
) {
    val totalAvailableCount: Int get() = matchingQuestions.size
    val effectiveQuestionCount: Int
        get() = if (questionCount <= 0 || questionCount > matchingQuestions.size) {
            matchingQuestions.size
        } else {
            questionCount
        }
}

@HiltViewModel
class TestOlusturViewModel @Inject constructor(
    private val soruRepository: SoruRepository,
    private val konuKatalog: KonuKatalog,
    private val testSessionManager: TestSessionManager,
    private val ayarlarDataStore: AyarlarDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(TestOlusturUiState())
    val uiState: StateFlow<TestOlusturUiState> = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val uiEvent: SharedFlow<String> = _uiEvent.asSharedFlow()

    private val _currentAlan = MutableStateFlow(Alan.SAYISAL)

    init {
        viewModelScope.launch {
            ayarlarDataStore.currentAlan.collect { alan ->
                _currentAlan.value = alan
                // Alan değiştiğinde UI'yi yeniden hesapla
                _uiState.update { state -> recomputeUi(state) }
            }
        }
        viewModelScope.launch {
            ayarlarDataStore.currentStreak.collect { streak ->
                _uiState.update { it.copy(currentStreak = streak) }
            }
        }
        viewModelScope.launch {
            ayarlarDataStore.streakShieldCount.collect { shield ->
                _uiState.update { it.copy(streakShieldCount = shield) }
            }
        }
        loadQuestions()
    }

    private fun loadQuestions() {
        viewModelScope.launch {
            soruRepository.getAllSorular()
                .catch { e ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = e.localizedMessage) }
                }
                .collect { questions ->
                    _uiState.update { current ->
                        recomputeUi(
                            current.copy(
                                allQuestions = questions,
                                isLoading = false
                            )
                        )
                    }
                }
        }
    }

    private fun recomputeUi(state: TestOlusturUiState): TestOlusturUiState {
        val all = state.allQuestions
        val alan = _currentAlan.value

        // Sınav türüne ve kullanıcının Alan'ına göre uygun dersleri hazırla
        val rawDersler: List<Ders> = when (state.examType) {
            "TYT" -> DersTaksonomisi.tytDersleri
            "AYT" -> DersTaksonomisi.getAytDersler(alan)
            "LGS" -> DersTaksonomisi.lgsDersleri
            "KPSS" -> DersTaksonomisi.kpssDersleri
            else -> when (alan) {
                Alan.LGS -> DersTaksonomisi.lgsDersleri
                Alan.KPSS -> DersTaksonomisi.kpssDersleri
                Alan.SAYISAL, Alan.ESIT_AGIRLIK, Alan.SOZEL -> DersTaksonomisi.tytDersleri + DersTaksonomisi.getAytDersler(alan)
                Alan.YDT -> DersTaksonomisi.tytDersleri + DersTaksonomisi.getAytDersler(alan)
            }.distinctBy { it.id }
        }

        val subjectItems = rawDersler.map { ders ->
            val dersSorulari = all.filter {
                it.subjectId.equals(ders.id, ignoreCase = true) ||
                        it.subjectId.equals(ders.jsonKey, ignoreCase = true) ||
                        (state.examType != null && it.examType.equals(state.examType, ignoreCase = true) && it.subjectId.contains(ders.jsonKey, ignoreCase = true))
            }
            DersSecimItem(
                ders = ders,
                totalCount = dersSorulari.size,
                unsolvedCount = dersSorulari.count { !it.isSolved }
            )
        }.filter { it.totalCount > 0 } // Sadece arşivde sorusu olan dersleri öne çıkar veya hepsini listele

        // Filtreye eşleşen soruları hesapla
        val matching = all.filter { soru ->
            val matchExam = state.examType == null || soru.examType.equals(state.examType, ignoreCase = true)

            val matchSubject = if (state.selectedSubjects.isEmpty()) {
                true
            } else {
                state.selectedSubjects.any { subId ->
                    soru.subjectId.equals(subId, ignoreCase = true) ||
                            soru.subjectId.contains(subId.removePrefix("ayt_").removePrefix("tyt_"), ignoreCase = true)
                }
            }

            val matchTopic = if (state.selectedTopics.isEmpty()) {
                true
            } else {
                state.selectedTopics.contains(soru.topic)
            }

            val matchMistake = if (state.selectedMistakeTypes.isEmpty()) {
                true
            } else {
                soru.mistakeType != null && state.selectedMistakeTypes.contains(soru.mistakeType)
            }

            val matchSolved = if (state.onlyUnsolved) !soru.isSolved else true

            matchExam && matchSubject && matchTopic && matchMistake && matchSolved
        }

        // Mevcut konuları hesapla
        val topics = mutableSetOf<String>()
        if (state.selectedSubjects.isNotEmpty()) {
            state.selectedSubjects.forEach { subId ->
                val catalogTopics = konuKatalog.getKonular(state.examType ?: "TYT", subId)
                topics.addAll(catalogTopics)
                val archiveTopics = all.filter { it.subjectId.equals(subId, ignoreCase = true) }.map { it.topic }
                topics.addAll(archiveTopics)
            }
        } else {
            val relevantQuestions = if (state.examType != null) {
                all.filter { it.examType.equals(state.examType, ignoreCase = true) }
            } else {
                all
            }
            topics.addAll(relevantQuestions.map { it.topic }.filter { it.isNotBlank() })
        }

        return state.copy(
            matchingQuestions = matching,
            availableSubjects = subjectItems,
            availableTopics = topics.filter { it.isNotBlank() }.sorted(),
            dueReviewCount = all.count { it.isDueForReview }
        )
    }

    fun setExamType(examType: String?) {
        _uiState.update { state ->
            val cleanExam = if (examType.equals("Tümü", ignoreCase = true)) null else examType
            val newState = state.copy(
                examType = cleanExam,
                selectedSubjects = emptySet(),
                selectedTopics = emptySet()
            )
            recomputeUi(newState)
        }
    }

    fun toggleSubject(subjectId: String) {
        _uiState.update { state ->
            val newSubjects = if (state.selectedSubjects.contains(subjectId)) {
                state.selectedSubjects - subjectId
            } else {
                state.selectedSubjects + subjectId
            }
            val newState = state.copy(selectedSubjects = newSubjects)
            recomputeUi(newState)
        }
    }

    fun selectAllSubjects() {
        _uiState.update { state ->
            val allIds = state.availableSubjects.map { it.ders.id }.toSet()
            val newState = state.copy(selectedSubjects = allIds)
            recomputeUi(newState)
        }
    }

    fun clearSubjects() {
        _uiState.update { state ->
            val newState = state.copy(
                selectedSubjects = emptySet(),
                selectedTopics = emptySet()
            )
            recomputeUi(newState)
        }
    }

    fun toggleTopic(topic: String) {
        _uiState.update { state ->
            val newTopics = if (state.selectedTopics.contains(topic)) {
                state.selectedTopics - topic
            } else {
                state.selectedTopics + topic
            }
            val newState = state.copy(selectedTopics = newTopics)
            recomputeUi(newState)
        }
    }

    fun clearTopics() {
        _uiState.update { state ->
            val newState = state.copy(selectedTopics = emptySet())
            recomputeUi(newState)
        }
    }

    fun toggleMistakeType(hataTipi: HataTipi) {
        _uiState.update { state ->
            val newMistakes = if (state.selectedMistakeTypes.contains(hataTipi)) {
                state.selectedMistakeTypes - hataTipi
            } else {
                state.selectedMistakeTypes + hataTipi
            }
            val newState = state.copy(selectedMistakeTypes = newMistakes)
            recomputeUi(newState)
        }
    }

    fun clearMistakeTypes() {
        _uiState.update { state ->
            val newState = state.copy(selectedMistakeTypes = emptySet())
            recomputeUi(newState)
        }
    }

    fun setOnlyUnsolved(onlyUnsolved: Boolean) {
        _uiState.update { state ->
            val newState = state.copy(onlyUnsolved = onlyUnsolved)
            recomputeUi(newState)
        }
    }

    fun setQuestionCount(count: Int) {
        _uiState.update { state ->
            state.copy(questionCount = count)
        }
    }

    fun setTimeLimitPerQuestion(seconds: Long) {
        _uiState.update { state ->
            state.copy(timeLimitPerQuestionSeconds = seconds)
        }
    }

    fun startTest(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.matchingQuestions.isEmpty()) return

        val dueQuestions = state.matchingQuestions.filter { it.isDueForReview }.shuffled()
        val otherQuestions = state.matchingQuestions.filterNot { it.isDueForReview }.shuffled()
        val prioritizedQuestions = dueQuestions + otherQuestions

        val countToTake = if (state.questionCount <= 0 || state.questionCount > prioritizedQuestions.size) {
            prioritizedQuestions.size
        } else {
            state.questionCount
        }

        val selectedList = prioritizedQuestions.take(countToTake).map { soru ->
            TestQuestion(soru = soru)
        }

        val filtre = TestFiltre(
            examType = state.examType,
            selectedSubjects = state.selectedSubjects,
            selectedTopics = state.selectedTopics,
            selectedMistakeTypes = state.selectedMistakeTypes,
            onlyUnsolved = state.onlyUnsolved,
            questionCount = countToTake,
            timeLimitPerQuestionSeconds = state.timeLimitPerQuestionSeconds
        )

        testSessionManager.startSession(selectedList, filtre, state.timeLimitPerQuestionSeconds)
        onSuccess()
    }

    /**
     * Spaced repetition tekrar vakti gelmiş sorularla doğrudan test oturumu başlatır.
     * Seçili zaman baskısı modunu korur; liste boşsa bilgi mesajı yayınlar.
     */
    fun startDueReviewTest(onSuccess: () -> Unit) {
        viewModelScope.launch {
            val dueQuestions = try {
                soruRepository.getDueReviewSorularList()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Tekrar listesi yüklenemedi: ${e.localizedMessage}") }
                _uiEvent.tryEmit("Tekrar listesi yüklenemedi.")
                return@launch
            }
            if (dueQuestions.isNotEmpty()) {
                val testQuestions = dueQuestions.map { TestQuestion(soru = it) }
                val timeLimit = _uiState.value.timeLimitPerQuestionSeconds
                val filter = TestFiltre(
                    examType = null,
                    selectedSubjects = emptySet(),
                    selectedTopics = emptySet(),
                    selectedMistakeTypes = emptySet(),
                    onlyUnsolved = false,
                    questionCount = testQuestions.size,
                    timeLimitPerQuestionSeconds = timeLimit
                )
                testSessionManager.startSession(testQuestions, filter, timeLimit)
                _uiState.update { it.copy(errorMessage = null) }
                onSuccess()
            } else {
                _uiState.update { it.copy(errorMessage = null) }
                _uiEvent.tryEmit("Şu an tekrar vakti gelmiş soru yok. Filtrelerle normal test oluşturabilirsin.")
            }
        }
    }
}
