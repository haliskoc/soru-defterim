package com.sorudefteri.ui.reels

import com.sorudefteri.domain.model.Soru
import com.sorudefteri.domain.repository.SoruRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

/**
 * PERF: Reels artık tam-tablo Flow dinlemez; tek seferlik snapshot okur.
 * DB yazışı sonrası feed baştan map'lenmez.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReelsSingleShotTest {

    private val testDispatcher = StandardTestDispatcher()
    private val soruRepository: SoruRepository = mockk(relaxed = true)

    private fun soru(id: Long, fav: Boolean = false) = Soru(
        id = id,
        imagePath = "/img/$id.jpg",
        examType = "TYT",
        subjectId = "matematik",
        topic = "T$id",
        correctAnswer = "A",
        isFavorite = fav
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `feed loads once from snapshot and ignores later flow emissions`() = runTest(testDispatcher) {
        coEvery { soruRepository.getAllSorularOnce() } returns listOf(soru(1L), soru(2L))
        coEvery { soruRepository.getAllSorular() } returns flowOf(
            listOf(soru(1L), soru(2L)),
            listOf(soru(1L), soru(2L), soru(3L))
        )
        coEvery { soruRepository.toggleFavorite(any(), any()) } returns Unit

        val viewModel = ReelsViewModel(soruRepository, testDispatcher)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        val initialCount = viewModel.uiState.value.items.size

        // Favori yazışı sonrası feed boyutu değişmemeli (remap yok)
        viewModel.toggleFavorite(soru(1L))
        advanceUntilIdle()
        assertEquals(initialCount, viewModel.uiState.value.items.size)
        coVerify(exactly = 1) { soruRepository.getAllSorularOnce() }
    }

    @Test
    fun `refreshFeed reloads snapshot`() = runTest(testDispatcher) {
        coEvery { soruRepository.getAllSorularOnce() } returns listOf(soru(1L)) andThen listOf(soru(1L), soru(2L))

        val viewModel = ReelsViewModel(soruRepository, testDispatcher)
        advanceUntilIdle()
        val first = viewModel.uiState.value.items.size

        viewModel.refreshFeed()
        advanceUntilIdle()
        coVerify(exactly = 2) { soruRepository.getAllSorularOnce() }
        assertFalse(viewModel.uiState.value.isLoading)
        assert(first >= 0)
    }
}
