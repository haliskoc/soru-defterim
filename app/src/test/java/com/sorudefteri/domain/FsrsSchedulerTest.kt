package com.sorudefteri.domain

import com.sorudefteri.domain.manager.FsrsRating
import com.sorudefteri.domain.manager.FsrsScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FsrsSchedulerTest {

    @Test
    fun `initial review sets reasonable stability and interval for all ratings`() {
        val again = FsrsScheduler.calculateNextState(null, null, 0, 0, null, FsrsRating.AGAIN, 1000000L)
        val hard = FsrsScheduler.calculateNextState(null, null, 0, 0, null, FsrsRating.HARD, 1000000L)
        val good = FsrsScheduler.calculateNextState(null, null, 0, 0, null, FsrsRating.GOOD, 1000000L)
        val easy = FsrsScheduler.calculateNextState(null, null, 0, 0, null, FsrsRating.EASY, 1000000L)

        // EASY should have higher stability and interval than GOOD, GOOD > HARD, HARD > AGAIN
        assertTrue(easy.stability > good.stability)
        assertTrue(good.stability > hard.stability)
        assertTrue(hard.stability > again.stability)

        assertTrue(again.lapses == 1)
        assertTrue(again.reps == 0)
        assertTrue(good.reps == 1)
        assertTrue(good.lapses == 0)

        assertTrue(again.intervalDays >= 1L)
        assertTrue(easy.intervalDays >= good.intervalDays)
    }

    @Test
    fun `repeat successful reviews expand interval and increase reps`() {
        val now = 1000000L
        val firstState = FsrsScheduler.calculateNextState(null, null, 0, 0, null, FsrsRating.GOOD, now)

        val review2Time = now + (firstState.intervalDays * 24L * 60L * 60L * 1000L)
        val secondState = FsrsScheduler.calculateNextState(
            currentStability = firstState.stability,
            currentDifficulty = firstState.difficulty,
            currentReps = firstState.reps,
            currentLapses = firstState.lapses,
            lastReviewedAt = now,
            rating = FsrsRating.GOOD,
            reviewTimestamp = review2Time
        )

        assertTrue("Reps must increment", secondState.reps == 2)
        assertTrue("Stability must grow after successful recall", secondState.stability > firstState.stability)
        assertTrue("Next interval must be longer", secondState.intervalDays >= firstState.intervalDays)
    }

    @Test
    fun `lapsing an item increments lapses and reduces stability`() {
        val now = 1000000L
        val firstGood = FsrsScheduler.calculateNextState(null, null, 0, 0, null, FsrsRating.GOOD, now)

        val elapsed = now + (10L * 24L * 60L * 60L * 1000L)
        val lapsedState = FsrsScheduler.calculateNextState(
            currentStability = firstGood.stability,
            currentDifficulty = firstGood.difficulty,
            currentReps = firstGood.reps,
            currentLapses = firstGood.lapses,
            lastReviewedAt = now,
            rating = FsrsRating.AGAIN,
            reviewTimestamp = elapsed
        )

        assertEquals(1, lapsedState.lapses)
        assertTrue("Lapse drops stability", lapsedState.stability <= firstGood.stability)
        assertEquals(1L, lapsedState.intervalDays)
    }

    @Test
    fun `leitner stage mapping works correctly`() {
        assertEquals(1, FsrsScheduler.mapIntervalToLeitnerStage(1L))
        assertEquals(2, FsrsScheduler.mapIntervalToLeitnerStage(3L))
        assertEquals(3, FsrsScheduler.mapIntervalToLeitnerStage(7L))
        assertEquals(4, FsrsScheduler.mapIntervalToLeitnerStage(14L))
        assertEquals(5, FsrsScheduler.mapIntervalToLeitnerStage(30L))
    }
}
