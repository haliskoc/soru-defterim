package com.sorudefteri.domain

import com.sorudefteri.domain.model.Soru
import com.sorudefteri.domain.model.TestFiltre
import com.sorudefteri.domain.model.TestQuestion
import com.sorudefteri.domain.model.TestResult
import com.sorudefteri.domain.repository.TestSessionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Test ve Deneme çözme oturumu, YKS net hesaplamaları ve şık değerlendirme birim testi.
 */
class TestSessionManagerTest {

    private lateinit var sessionManager: TestSessionManager

    @Before
    fun setUp() {
        sessionManager = TestSessionManager()
    }

    private fun createDummySoru(id: Long, correctAnswer: String?): Soru {
        return Soru(
            id = id,
            imagePath = "/data/user/0/com.sorudefteri/files/images/soru_$id.jpg",
            correctAnswer = correctAnswer,
            examType = "TYT",
            subjectId = "tyt_matematik",
            topic = "Problemler"
        )
    }

    @Test
    fun netHesaplama_yksKuraliylaDogruCalisiyor() {
        // 10 Doğru, 4 Yanlış -> 10 - 1 = 9.00 Net
        val res1 = TestResult(
            totalQuestions = 14,
            correctCount = 10,
            wrongCount = 4,
            emptyCount = 0,
            totalTimeSeconds = 600,
            questions = emptyList()
        )
        assertEquals(9.0f, res1.netCount, 0.001f)
        assertEquals(71, res1.successRate)

        // 12 Doğru, 0 Yanlış -> 12.00 Net, %100 Başarı
        val res2 = TestResult(
            totalQuestions = 12,
            correctCount = 12,
            wrongCount = 0,
            emptyCount = 0,
            totalTimeSeconds = 300,
            questions = emptyList()
        )
        assertEquals(12.0f, res2.netCount, 0.001f)
        assertEquals(100, res2.successRate)

        // 0 Doğru, 4 Yanlış -> 0 Net (negatife düşmez)
        val res3 = TestResult(
            totalQuestions = 4,
            correctCount = 0,
            wrongCount = 4,
            emptyCount = 0,
            totalTimeSeconds = 120,
            questions = emptyList()
        )
        assertEquals(0.0f, res3.netCount, 0.001f)
        assertEquals(0, res3.successRate)
    }

    @Test
    fun testOturumu_cevaplariDogruDegerlendiriyor() {
        val q1 = TestQuestion(soru = createDummySoru(1L, "A"))
        val q2 = TestQuestion(soru = createDummySoru(2L, "B"))
        val q3 = TestQuestion(soru = createDummySoru(3L, "C"))
        val q4 = TestQuestion(soru = createDummySoru(4L, "D"))

        sessionManager.startSession(listOf(q1, q2, q3, q4), TestFiltre())

        // 1. Soru: Doğru cevap ("a" küçük harf testi)
        sessionManager.updateAnswer(0, "a")
        // 2. Soru: Yanlış cevap ("C" verildi, doğru "B")
        sessionManager.updateAnswer(1, "C")
        // 3. Soru: Boş bırakıldı (null)
        sessionManager.updateAnswer(2, null)
        // 4. Soru: Doğru cevap ("D")
        sessionManager.updateAnswer(3, "D")

        val result = sessionManager.finishSession(
            totalTimeSeconds = 240L,
            questionTimes = mapOf(0 to 60L, 1 to 50L, 2 to 30L, 3 to 100L)
        )

        assertEquals(4, result.totalQuestions)
        assertEquals(2, result.correctCount)
        assertEquals(1, result.wrongCount)
        assertEquals(1, result.emptyCount)
        assertEquals(240L, result.totalTimeSeconds)

        // 2 Doğru - (1 Yanlış * 0.25) = 1.75 Net
        assertEquals(1.75f, result.netCount, 0.001f)

        // Soruların bireysel değerlendirmesini kontrol et
        assertEquals(true, result.questions[0].isCorrect)
        assertEquals(false, result.questions[1].isCorrect)
        assertNull(result.questions[2].isCorrect)
        assertEquals(true, result.questions[3].isCorrect)
    }
}
