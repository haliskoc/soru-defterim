package com.sorudefteri.ui.reels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sorudefteri.domain.model.ReelsItem
import com.sorudefteri.domain.model.Soru
import com.sorudefteri.domain.model.YksFlashcardsRepository
import com.sorudefteri.domain.repository.SoruRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.sorudefteri.domain.reels.ReelsAkisAlgoritmasi
import javax.inject.Inject
import kotlin.random.Random

data class ReelsUiState(
    val items: List<ReelsItem> = emptyList(),
    val isLoading: Boolean = true,
    val selectedOptions: Map<Long, String> = emptyMap(), // soruId -> selectedOption ("A", "B", etc.)
    val answeredStatus: Map<Long, Boolean> = emptyMap(), // soruId -> isCorrect (true/false)
    val favoritedQuestionIds: Set<Long> = emptySet(),
    val commentsSoru: Soru? = null, // BottomSheet'te çözümü açılan soru
    val feedbackMessage: String? = null
)

@HiltViewModel
class ReelsViewModel @Inject constructor(
    private val soruRepository: SoruRepository,
    private val defaultDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Default
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReelsUiState())
    val uiState: StateFlow<ReelsUiState> = _uiState.asStateFlow()

    private var allRawQuestions: List<Soru> = emptyList()

    init {
        loadFeedOnce()
    }

    /**
     * PERF: Tam-tablo Flow dinlenmez; tablo bir kez okunur, feed bir kez
     * kurulur. Favori/cevap yazışları yalnızca lokal state'i günceller —
     * her DB yazışında tüm feed'in baştan map'lenmesi biter.
     * Yeni sorular için [refreshFeed] (örn. pull-to-refresh) çağrılır.
     */
    private fun loadFeedOnce() {
        viewModelScope.launch(defaultDispatcher) {
            try {
                val questions = soruRepository.getAllSorularOnce()
                allRawQuestions = questions
                buildFeedInternal(questions)
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    /** Yeni eklenen soruları akışa almak için açık yenileme. */
    fun refreshFeed() {
        loadFeedOnce()
    }

    /**
     * Reels akışı: [ReelsAkisAlgoritmasi] ile önceliklendirilir.
     * 1. Tekrarı gelen (due) sorular akışın başında; gecikme arttıkça öne.
     * 2. Yeni eklenen sorular yenilik (yarı-ömür zaman decay) etkisiyle öne çıkar.
     * 3. Ağırlıklı karıştırma (E&S) → sıralama korunur, doğalcı dağılım sağlanır.
     * 4. Taban ağırlık: eski sorular seyrek ama daima akışta kalır (unutma sigortası).
     * 5. Her 3-4 soruda bir kritik YKS Hap Bilgi / Formül kartı serpiştirilir.
     *
     * [tohum]: gün tohumu ile feed gün içinde kararlı; "karıştır" düğmesi
     * yeni tohumla yeniden dağıtır (bakınız [shuffleFeed]).
     */
    fun buildFeed(questions: List<Soru> = allRawQuestions, tohum: Long = ReelsAkisAlgoritmasi.gunTohumu(System.currentTimeMillis())) {
        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch(defaultDispatcher) {
            buildFeedInternal(questions, tohum)
        }
    }

    private suspend fun buildFeedInternal(
        questions: List<Soru>,
        tohum: Long = ReelsAkisAlgoritmasi.gunTohumu(System.currentTimeMillis())
    ) {
        val prioritizedQuestions = ReelsAkisAlgoritmasi.akisKuras(
            sorular = questions,
            anZaman = System.currentTimeMillis(),
            tohum = tohum
        )

        val flashcards = YksFlashcardsRepository.curatedCards.shuffled()
        val combinedItems = mutableListOf<ReelsItem>()

        var cardIndex = 0
        prioritizedQuestions.forEachIndexed { index, soru ->
            combinedItems.add(ReelsItem.SoruItem(soru))
            // Her 3 soruda bir veya liste boşsa araya hap bilgi ekle
            if ((index + 1) % 3 == 0 && cardIndex < flashcards.size) {
                combinedItems.add(flashcards[cardIndex])
                cardIndex++
            }
        }

        // Eğer henüz soru yoksa veya azsa en azından hap bilgi kartlarını doldur
        if (combinedItems.isEmpty()) {
            combinedItems.addAll(flashcards)
        }

        val favs = questions.filter { it.isFavorite }.map { it.id }.toSet()

        _uiState.update {
            it.copy(
                items = combinedItems,
                isLoading = false,
                favoritedQuestionIds = favs
            )
        }
    }

    fun shuffleFeed() {
        // "Karıştır" düğmesi: algoritma çerçevesi korunur, dağılım yeni tohumla yenilenir.
        buildFeed(allRawQuestions, tohum = Random.nextLong(1L, Long.MAX_VALUE))
    }

    fun toggleFavorite(soru: Soru) {
        viewModelScope.launch {
            val isFav = _uiState.value.favoritedQuestionIds.contains(soru.id)
            val newFavState = !isFav
            soruRepository.toggleFavorite(soru.id, newFavState)
            _uiState.update { current ->
                val newFavs = if (newFavState) {
                    current.favoritedQuestionIds + soru.id
                } else {
                    current.favoritedQuestionIds - soru.id
                }
                current.copy(favoritedQuestionIds = newFavs)
            }
        }
    }

    fun answerQuestion(soru: Soru, option: String) {
        val cleanCorrect = soru.correctAnswer?.trim()?.uppercase()
        val isCorrect = cleanCorrect != null && cleanCorrect == option.trim().uppercase()

        _uiState.update { current ->
            current.copy(
                selectedOptions = current.selectedOptions + (soru.id to option),
                answeredStatus = current.answeredStatus + (soru.id to isCorrect),
                feedbackMessage = if (isCorrect) "Tebrikler! Doğru Cevap: $option 🎉" else "Yanlış! Doğru Cevap: ${cleanCorrect ?: "?"} ❌"
            )
        }

        viewModelScope.launch {
            if (isCorrect) {
                soruRepository.advanceReviewStage(soru.id)
                soruRepository.updateSolvedStatus(soru.id, true)
            } else {
                soruRepository.regressReviewStage(soru.id)
            }
        }
    }

    fun openCommentsSheet(soru: Soru) {
        val latest = allRawQuestions.find { it.id == soru.id } ?: soru
        _uiState.update { it.copy(commentsSoru = latest) }
    }

    fun closeCommentsSheet() {
        _uiState.update { it.copy(commentsSoru = null) }
    }

    fun clearFeedbackMessage() {
        _uiState.update { it.copy(feedbackMessage = null) }
    }
}
