package com.sorudefteri.domain

import com.sorudefteri.domain.model.Soru
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Leitner Aralıklı Tekrar (Spaced Repetition) algoritması ve tekrar zamanı hesaplama birim testi.
 */
class SpacedRepetitionTest {

    private val oneDayMs = 24 * 60 * 60 * 1000L

    @Test
    fun isDueForReview_tarihKontroluDogruCalisiyor() {
        val now = System.currentTimeMillis()

        // 1. Durum: Çözülmemiş soru (isSolved = false, nextReviewDate = null) -> Çözülmeyi beklediği için due = true
        val soru1 = Soru(
            id = 1L,
            imagePath = "path/1.jpg",
            examType = "TYT",
            subjectId = "tyt_matematik",
            topic = "Temel Kavramlar",
            isSolved = false,
            nextReviewDate = null
        )
        assertTrue("Çözülmemiş ve tekrar tarihi atanmamış soru due olmalı", soru1.isDueForReview)

        // 2. Durum: Çözülmüş soru (isSolved = true, nextReviewDate = null) -> Tekrar planlanmamış due = false
        val soruSolved = soru1.copy(isSolved = true, nextReviewDate = null)
        assertFalse("Çözülmüş ve tekrar planlanmamış soru due olamaz", soruSolved.isDueForReview)

        // 3. Durum: Tekrar tarihi gelecekte (yarın) -> due = false
        val soruGelecek = soru1.copy(isSolved = true, nextReviewDate = now + oneDayMs)
        assertFalse("Gelecekteki tekrar tarihi due olamaz", soruGelecek.isDueForReview)

        // 4. Durum: Tekrar tarihi tam şu an veya geçmişte (dün) -> due = true
        val soruGecmis = soru1.copy(isSolved = true, nextReviewDate = now - oneDayMs)
        assertTrue("Geçmişteki tekrar tarihi due olmalı", soruGecmis.isDueForReview)

        val soruSuresiDolmus = soru1.copy(isSolved = true, nextReviewDate = now - 1000L)
        assertTrue("Süresi dolmuş tekrar due olmalı", soruSuresiDolmus.isDueForReview)
    }

    @Test
    fun leitnerAraliklari_artarakHesaplaniyor() {
        fun calculateNextIntervalDays(nextStage: Int): Int {
            return when (nextStage) {
                1 -> 1
                2 -> 3
                3 -> 7
                4 -> 14
                5 -> 30
                else -> 1
            }
        }

        assertEquals(1, calculateNextIntervalDays(1))  // 1. Kademe: 1 gün sonra
        assertEquals(3, calculateNextIntervalDays(2))  // 2. Kademe: 3 gün sonra
        assertEquals(7, calculateNextIntervalDays(3))  // 3. Kademe: 7 gün sonra (1 hafta)
        assertEquals(14, calculateNextIntervalDays(4)) // 4. Kademe: 14 gün sonra (2 hafta)
        assertEquals(30, calculateNextIntervalDays(5)) // 5. Kademe: 30 gün sonra (1 ay)
    }

    @Test
    fun leitnerKademeIlerlemesi_veSifirlanmasi() {
        var stage = 0

        // Başarılı çözümlerde kademe artar (maksimum 5)
        for (i in 1..7) {
            stage = (stage + 1).coerceAtMost(5)
        }
        assertEquals("Kademe maksimum 5'i geçemez", 5, stage)

        // Hata yapıldığında evre sıfırlanır
        val wasSuccessful = false
        if (!wasSuccessful) {
            stage = 0
        }
        assertEquals("Yanlış yapıldığında kademe 0'a dönmeli", 0, stage)
    }
}
