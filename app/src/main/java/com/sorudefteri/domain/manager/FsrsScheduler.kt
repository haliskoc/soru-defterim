package com.sorudefteri.domain.manager

import com.sorudefteri.domain.model.Soru
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Kullanıcı Derecelendirmesi (FSRS Rating)
 */
enum class FsrsRating(val value: Int, val label: String) {
    AGAIN(1, "Tekrar"),
    HARD(2, "Zor"),
    GOOD(3, "İyi"),
    EASY(4, "Kolay")
}

/**
 * FSRS Durum Çıktısı
 */
data class FsrsState(
    val stability: Double,
    val difficulty: Double,
    val reps: Int,
    val lapses: Int,
    val intervalDays: Long,
    val nextReviewDate: Long,
    val mappedReviewStage: Int,
    val retrievability: Double
)

/**
 * Free Spaced Repetition Scheduler (FSRS v4/v5 tabanlı modern aralıklı tekrar algoritması).
 * SuperMemo-2 (SM-2) statik katsayıları yerine dinamik unutma eğrisi (Retrievability),
 * soru zorluğu (Difficulty) ve hafıza kararlılığı (Stability) modelleri ile çalışır.
 */
object FsrsScheduler {

    // Hedef Unutmama Oranı (%90)
    const val REQUESTED_RETENTION = 0.90
    private const val FACTOR = 19.0 / 81.0 // ~0.234567
    private const val DECAY = -0.5

    // FSRS Varsayılan Ağırlıkları (Default Parameters)
    private val W = doubleArrayOf(
        0.40255, 1.18385, 3.173, 8.001, // w[0..3]: İlk değerlendirme kararlılıkları (Again, Hard, Good, Easy)
        5.0,     // w[4]: Başlangıç zorluğu merkezi (Difficulty mean)
        1.2,     // w[5]: Zorluk adım katsayısı
        0.15,    // w[6]: Zorluk merkezine çekilme (Mean reversion)
        1.5,     // w[7]: Kararlılık artış çarpanı
        0.14,    // w[8]: Kararlılık üssü
        0.94,    // w[9]: Retrievability etkisi
        0.25,    // w[10]: Unutma sonrası kararlılık tabanı
        2.0,     // w[11]: Unutma zorluk katsayısı
        0.12,    // w[12]: Unutma zorluk üssü
        0.35,    // w[13]: Unutma kararlılık sönümlemesi
        0.22     // w[14]: Unutma R etkisi
    )

    /**
     * Geçen süreye (t gün) ve hafıza kararlılığına (S) göre hatırlama olasılığını hesaplar.
     * R(t, S) = (1 + FACTOR * (t / S))^DECAY
     */
    fun calculateRetrievability(stability: Double, elapsedDays: Double): Double {
        if (stability <= 0.0) return 0.0
        if (elapsedDays <= 0.0) return 1.0
        val r = (1.0 + FACTOR * (elapsedDays / stability)).pow(DECAY)
        return r.coerceIn(0.0, 1.0)
    }

    /**
     * Hedeflenen retrievability (%90) için sonraki aralığı (gün) hesaplar.
     */
    fun calculateNextInterval(stability: Double, targetRetention: Double = REQUESTED_RETENTION): Long {
        if (stability <= 0.0) return 1L
        // (1 + FACTOR * (I / S))^DECAY = R  ==> 1 + FACTOR * (I / S) = R^(1/DECAY)
        // I = S / FACTOR * (R^(1/DECAY) - 1)
        val interval = (stability / FACTOR) * (targetRetention.pow(1.0 / DECAY) - 1.0)
        return max(1L, interval.roundToLong())
    }

    /**
     * FSRS Aralığını Leitner Aşama Sistemine (0..5) eşler (Geriye Dönük Uyumluluk).
     */
    fun mapIntervalToLeitnerStage(intervalDays: Long): Int {
        return when {
            intervalDays <= 1L -> 1
            intervalDays <= 4L -> 2
            intervalDays <= 9L -> 3
            intervalDays <= 18L -> 4
            else -> 5
        }
    }

    /**
     * Soruya verilen FSRS derecelendirmesine göre bir sonraki durumu hesaplar.
     */
    fun calculateNextState(
        currentStability: Double?,
        currentDifficulty: Double?,
        currentReps: Int,
        currentLapses: Int,
        lastReviewedAt: Long?,
        rating: FsrsRating,
        reviewTimestamp: Long = System.currentTimeMillis()
    ): FsrsState {
        val grade = rating.value
        val isFirstReview = currentStability == null || currentDifficulty == null || lastReviewedAt == null || lastReviewedAt <= 0L

        val (newStability, newDifficulty, newReps, newLapses, r) = if (isFirstReview) {
            // İlk İnceleme (Initial Review)
            val initD = (W[4] - (grade - 3) * W[5]).coerceIn(1.0, 10.0)
            val initS = W[grade - 1].coerceAtLeast(0.1)
            val reps = if (grade >= 2) 1 else 0
            val lapses = if (grade == 1) 1 else 0
            val initialR = 1.0
            FsrsCalcResult(initS, initD, reps, lapses, initialR)
        } else {
            // Sonraki İncelemeler (Repeat Review)
            val elapsedMillis = max(0L, reviewTimestamp - lastReviewedAt)
            val elapsedDays = elapsedMillis.toDouble() / (1000.0 * 60.0 * 60.0 * 24.0)
            val curS = currentStability.coerceAtLeast(0.1)
            val curD = currentDifficulty.coerceIn(1.0, 10.0)
            val retrievability = calculateRetrievability(curS, elapsedDays)

            // Yeni Zorluk (Mean reversion ile güncellenir)
            val rawNextD = curD - W[5] * (grade - 3)
            val nextD = (W[6] * W[4] + (1.0 - W[6]) * rawNextD).coerceIn(1.0, 10.0)

            if (grade == 1) {
                // AGAIN (Unutuldu / Yanlış)
                val nextS = (W[10] * curD.pow(-W[11]) * ((curS + 1.0).pow(W[12]) - 1.0) * exp(W[13] * (1.0 - retrievability))).coerceIn(0.1, curS)
                FsrsCalcResult(nextS, nextD, currentReps, currentLapses + 1, retrievability)
            } else {
                // HARD (2), GOOD (3), EASY (4)
                val hardPenalty = if (grade == 2) 0.65 else 1.0
                val easyBonus = if (grade == 4) 1.35 else 1.0
                val factorR = exp(W[9] * (1.0 - retrievability)) - 1.0
                val stabilityInc = 1.0 + exp(W[7]) * (11.0 - nextD) * curS.pow(-W[8]) * factorR * hardPenalty * easyBonus
                val nextS = (curS * stabilityInc).coerceAtLeast(curS * 1.05)
                FsrsCalcResult(nextS, nextD, currentReps + 1, currentLapses, retrievability)
            }
        }

        val nextIntervalDays = calculateNextInterval(newStability)
        val nextReviewDate = reviewTimestamp + (nextIntervalDays * 24L * 60L * 60L * 1000L)
        val mappedStage = mapIntervalToLeitnerStage(nextIntervalDays)

        return FsrsState(
            stability = newStability,
            difficulty = newDifficulty,
            reps = newReps,
            lapses = newLapses,
            intervalDays = nextIntervalDays,
            nextReviewDate = nextReviewDate,
            mappedReviewStage = mappedStage,
            retrievability = r
        )
    }

    private data class FsrsCalcResult(
        val stability: Double,
        val difficulty: Double,
        val reps: Int,
        val lapses: Int,
        val retrievability: Double
    )

    /**
     * Soru nesnesi üzerinden doğrudan sonraki durumu hesaplayan extension/yardımcı metot.
     */
    fun review(soru: Soru, rating: FsrsRating, now: Long = System.currentTimeMillis()): FsrsState {
        return calculateNextState(
            currentStability = soru.fsrsStability,
            currentDifficulty = soru.fsrsDifficulty,
            currentReps = soru.fsrsReps,
            currentLapses = soru.fsrsLapses,
            lastReviewedAt = soru.lastReviewedAt,
            rating = rating,
            reviewTimestamp = now
        )
    }
}
