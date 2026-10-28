package com.sorudefteri.data.repository

import com.sorudefteri.data.images.GorselDepo
import com.sorudefteri.data.local.FakeSoruDao
import com.sorudefteri.domain.model.Soru
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * PERF: backfillContentHashes toplu transaction ile yazılır;
 * getKutuphaneMeta / getFilteredSoruIds tam liste çekmez.
 */
class PerfRepositoryTest {

    private lateinit var fakeDao: FakeSoruDao
    private lateinit var repository: SoruRepositoryImpl

    @Before
    fun setUp() {
        fakeDao = FakeSoruDao()
        repository = SoruRepositoryImpl(fakeDao, mockk(relaxed = true), mockk(relaxed = true), mockk<GorselDepo>(relaxed = true))
    }

    @Test
    fun backfillContentHashes_eksikHashleriTopluYazar() = runTest {
        val id1 = repository.soruEkle(Soru(imagePath = "a", examType = "TYT", subjectId = "mat", topic = "A", questionText = "Soru 1"))
        val id2 = repository.soruEkle(Soru(imagePath = "b", examType = "TYT", subjectId = "fiz", topic = "B", questionText = "Soru 2"))
        // Hash'leri boşalt
        fakeDao.updateContentHash(id1, "", 0L)
        fakeDao.updateContentHash(id2, "", 0L)

        val updated = repository.backfillContentHashes()
        assertEquals(2, updated)
        assertTrue(repository.getSoru(id1)!!.contentHash.isNotBlank())
        assertTrue(repository.getSoru(id2)!!.contentHash.isNotBlank())

        // İkinci çağrı 0 dönmeli (hepsi doldu)
        assertEquals(0, repository.backfillContentHashes())
    }

    @Test
    fun getKutuphaneMeta_tamListeCekmedenSayilariDoner() = runTest {
        repository.soruEkle(Soru(imagePath = "a", examType = "TYT", subjectId = "mat", topic = "A", isSolved = true, isFavorite = true))
        repository.soruEkle(Soru(imagePath = "b", examType = "AYT", subjectId = "fiz", topic = "B"))

        val meta = repository.getKutuphaneMeta()
        assertEquals(2, meta.totalCount)
        assertEquals(1, meta.solvedCount)
        assertEquals(1, meta.unsolvedCount)
        assertEquals(1, meta.favoriteCount)
    }

    @Test
    fun getFilteredSoruIds_yalnizcaIdDoner() = runTest {
        val id1 = repository.soruEkle(Soru(imagePath = "a", examType = "TYT", subjectId = "mat", topic = "A"))
        repository.soruEkle(Soru(imagePath = "b", examType = "AYT", subjectId = "fiz", topic = "B"))

        val ids = repository.getFilteredSoruIds(examType = "TYT")
        assertEquals(listOf(id1), ids)
    }

    @Test
    fun advanceReviewStageBatch_topluYukseltir() = runTest {
        val id1 = repository.soruEkle(Soru(imagePath = "a", examType = "TYT", subjectId = "mat", topic = "A"))
        val id2 = repository.soruEkle(Soru(imagePath = "b", examType = "TYT", subjectId = "mat", topic = "B"))

        repository.advanceReviewStageBatch(listOf(id1, id2))
        assertEquals(1, repository.getSoru(id1)!!.reviewStage)
        assertEquals(1, repository.getSoru(id2)!!.reviewStage)
    }

    @Test
    fun weeklyActivity_sqlCountIleHesaplanir() = runTest {
        repository.soruEkle(Soru(imagePath = "a", examType = "TYT", subjectId = "mat", topic = "A"))
        val week = repository.getWeeklyActivity().first()
        assertEquals(7, week.size)
        assertTrue(week.sumOf { it.count } >= 1)
    }
}
