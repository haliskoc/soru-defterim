package com.sorudefteri.domain.repository

import com.sorudefteri.domain.model.TestFiltre
import com.sorudefteri.domain.model.TestQuestion
import com.sorudefteri.domain.model.TestResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Aktif test çözme oturumu ve son tamamlanan test sonucunu yöneten oturum servisi
 */
@Singleton
class TestSessionManager @Inject constructor() {

    private val _activeQuestions = MutableStateFlow<List<TestQuestion>>(emptyList())
    val activeQuestions: StateFlow<List<TestQuestion>> = _activeQuestions.asStateFlow()

    private val _currentFilter = MutableStateFlow(TestFiltre())
    val currentFilter: StateFlow<TestFiltre> = _currentFilter.asStateFlow()

    private val _lastResult = MutableStateFlow<TestResult?>(null)
    val lastResult: StateFlow<TestResult?> = _lastResult.asStateFlow()

    /**
     * Yeni bir test oturumu başlatır
     */
    fun startSession(questions: List<TestQuestion>, filter: TestFiltre) {
        _activeQuestions.value = questions
        _currentFilter.value = filter
        _lastResult.value = null
    }

    /**
     * Belirtilen indisteki sorunun kullanıcının verdiği cevabını günceller
     */
    fun updateAnswer(index: Int, answer: String?) {
        val currentList = _activeQuestions.value.toMutableList()
        if (index in currentList.indices) {
            val q = currentList[index]
            currentList[index] = q.copy(userAnswer = answer)
            _activeQuestions.value = currentList
        }
    }

    /**
     * Testi sonlandırır, cevapları cevap anahtarıyla karşılaştırarak değerlendirir ve sonuç nesnesi üretir
     */
    fun finishSession(totalTimeSeconds: Long, questionTimes: Map<Int, Long>): TestResult {
        val currentList = _activeQuestions.value
        val evaluatedQuestions = currentList.mapIndexed { index, tq ->
            val userAns = tq.userAnswer?.trim()?.uppercase()
            val correctAns = tq.soru.correctAnswer?.trim()?.uppercase()
            val timeSpent = questionTimes[index] ?: (if (currentList.isNotEmpty()) totalTimeSeconds / currentList.size else 0L)

            val isCorrect = when {
                userAns == null -> null // Boş bırakılmış
                correctAns.isNullOrBlank() -> null // Doğru cevap şıkkı bilinmiyor
                userAns == correctAns -> true
                else -> false
            }

            tq.copy(
                isCorrect = isCorrect,
                timeSpentSeconds = timeSpent
            )
        }

        val correctCount = evaluatedQuestions.count { it.isCorrect == true }
        val wrongCount = evaluatedQuestions.count { it.isCorrect == false }
        val emptyCount = evaluatedQuestions.count { it.userAnswer == null }

        val result = TestResult(
            totalQuestions = evaluatedQuestions.size,
            correctCount = correctCount,
            wrongCount = wrongCount,
            emptyCount = emptyCount,
            totalTimeSeconds = totalTimeSeconds,
            questions = evaluatedQuestions
        )

        _lastResult.value = result
        return result
    }

    /**
     * Sonuç ekranında çözümü alınan soru için AI açıklamasını günceller
     */
    fun updateQuestionAiExplanation(soruId: Long, explanation: String) {
        val currentResult = _lastResult.value ?: return
        val updatedQuestions = currentResult.questions.map { tq ->
            if (tq.soru.id == soruId) {
                tq.copy(soru = tq.soru.copy(aiExplanation = explanation))
            } else {
                tq
            }
        }
        _lastResult.value = currentResult.copy(questions = updatedQuestions)
    }

    /**
     * Oturumu sıfırlar
     */
    fun clear() {
        _activeQuestions.value = emptyList()
        _lastResult.value = null
    }
}
