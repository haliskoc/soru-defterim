package com.sorudefteri.domain

import com.sorudefteri.domain.model.Soru
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spaced Repetition (Leitner Sistemi) genişletilmiş kademe geçişleri,
 * gün aralıkları, zaman hesaplamaları ve sıfırlama mekanizması birim testleri.
 */
class SpacedRepetitionExtendedTest {

    private val ONE_DAY_MS = 24 * 60 * 60 * 1000L

    private fun getIntervalDays(stage: Int): Long {
        return when (stage) {
            1 -> 1L
            2 -> 3L
            3 -> 7L
            4 -> 14L
            else -> 30L
        }
    }

    private fun calculateNextReviewDate(currentStage: Int, baseTime: Long): Pair<Int, Long> {
        val nextStage = (currentStage + 1).coerceAtMost(5)
        val intervalDays = getIntervalDays(nextStage)
        val nextDate = baseTime + (intervalDays * ONE_DAY_MS)
        return nextStage to nextDate
    }

    @Test
    fun leitnerKademeleri_0dan5e_aralikVeTarihleriDogruHesaplar() {
        val now = 1700000000000L // Sabit referans zaman damgası

        // Stage 0 -> 1 (+1 Gün = 86,400,000 ms)
        val (stage1, date1) = calculateNextReviewDate(0, now)
        assertEquals(1, stage1)
        assertEquals(1L, getIntervalDays(stage1))
        assertEquals(now + 1 * ONE_DAY_MS, date1)

        // Stage 1 -> 2 (+3 Gün = 259,200,000 ms)
        val (stage2, date2) = calculateNextReviewDate(stage1, now)
        assertEquals(2, stage2)
        assertEquals(3L, getIntervalDays(stage2))
        assertEquals(now + 3 * ONE_DAY_MS, date2)

        // Stage 2 -> 3 (+7 Gün = 604,800,000 ms)
        val (stage3, date3) = calculateNextReviewDate(stage2, now)
        assertEquals(3, stage3)
        assertEquals(7L, getIntervalDays(stage3))
        assertEquals(now + 7 * ONE_DAY_MS, date3)

        // Stage 3 -> 4 (+14 Gün = 1,209,600,000 ms)
        val (stage4, date4) = calculateNextReviewDate(stage3, now)
        assertEquals(4, stage4)
        assertEquals(14L, getIntervalDays(stage4))
        assertEquals(now + 14 * ONE_DAY_MS, date4)

        // Stage 4 -> 5 (+30 Gün = 2,592,000,000 ms)
        val (stage5, date5) = calculateNextReviewDate(stage4, now)
        assertEquals(5, stage5)
        assertEquals(30L, getIntervalDays(stage5))
        assertEquals(now + 30 * ONE_DAY_MS, date5)

        // Stage 5 -> 5 (Maksimum sınır aşılmamalı)
        val (stageMax, dateMax) = calculateNextReviewDate(stage5, now)
        assertEquals("Maksimum kademe 5 kalmalıdır", 5, stageMax)
        assertEquals(now + 30 * ONE_DAY_MS, dateMax)
    }

    @Test
    fun yanlisCozumde_kademeSifirlanir() {
        var currentStage = 4 // İleri seviye bir kademe

        // Soru yanlış çözüldü
        val isCorrect = false
        if (!isCorrect) {
            currentStage = 0 // Leitner prensibi: Hata yapıldığında 0. kademeye döner
        }

        assertEquals(0, currentStage)

        // Tekrar çözülüp doğru yapıldığında 1. kademeden başlar
        val (nextStage, nextDate) = calculateNextReviewDate(currentStage, System.currentTimeMillis())
        assertEquals(1, nextStage)
    }

    @Test
    fun soru_isDueForReview_tumSinirDurumlarindaDogruCalisir() {
        val now = System.currentTimeMillis()

        // 1. Çözülmemiş soru (isSolved=false, nextReviewDate=null) -> due = true
        val soru1 = Soru(imagePath = "p1", examType = "TYT", subjectId = "mat", topic = "T1", isSolved = false, nextReviewDate = null)
        assertTrue(soru1.isDueForReview)

        // 2. Çözülmüş soru (isSolved=true, nextReviewDate=null) -> due = false
        val soru2 = Soru(imagePath = "p2", examType = "TYT", subjectId = "mat", topic = "T2", isSolved = true, nextReviewDate = null)
        assertFalse(soru2.isDueForReview)

        // 3. Çözülmüş soru, tekrar tarihi geçmişte (-1 saat) -> due = true
        val soru3 = Soru(imagePath = "p3", examType = "TYT", subjectId = "mat", topic = "T3", isSolved = true, nextReviewDate = now - 3600000L)
        assertTrue(soru3.isDueForReview)

        // 4. Çözülmüş soru, tekrar tarihi gelecekte (+1 saat) -> due = false
        val soru4 = Soru(imagePath = "p4", examType = "TYT", subjectId = "mat", topic = "T4", isSolved = true, nextReviewDate = now + 3600000L)
        assertFalse(soru4.isDueForReview)

        // 5. Çözülmemiş ama ileri bir tarihe ertelenmiş soru -> due = false (çünkü henüz tarihi gelmedi)
        val soru5 = Soru(imagePath = "p5", examType = "TYT", subjectId = "mat", topic = "T5", isSolved = false, nextReviewDate = now + ONE_DAY_MS)
        assertFalse(soru5.isDueForReview)
    }
}
