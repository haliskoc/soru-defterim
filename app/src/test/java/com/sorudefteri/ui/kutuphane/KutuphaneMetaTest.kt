package com.sorudefteri.ui.kutuphane

import com.sorudefteri.domain.model.Alan
import com.sorudefteri.domain.model.KutuphaneMeta
import com.sorudefteri.data.local.AyarlarDataStore
import com.sorudefteri.data.repository.SoruRepository
import com.sorudefteri.domain.repository.TestSessionManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * PERF: Kütüphane tam liste tutmaz; meta tekil sorgulardan gelir,
 * "Tümünü Seç" id sorgusu kullanır.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KutuphaneMetaTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repo: SoruRepository
    private lateinit var store: AyarlarDataStore

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repo = mockk(relaxed = true)
        store = mockk(relaxed = true)
        every { store.currentAlan } returns flowOf(Alan.SAYISAL)
        every { repo.totalCount } returns flowOf(5)
        every { repo.getKutuphaneMetaFlow() } returns flowOf(
            KutuphaneMeta(
                totalCount = 5, solvedCount = 2, unsolvedCount = 3,
                favoriteCount = 1, dueReviewCount = 4,
                availablePublishers = listOf("3D"), availableExamNames = listOf("Deneme 1")
            )
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `uiState carries meta without full lists`() = runTest(testDispatcher) {
        val vm = KutuphaneViewModel(repo, store, TestSessionManager(), testDispatcher)
        backgroundScope.launch { vm.uiState.collect() }
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(5, state.totalCount)
        assertEquals(2, state.solvedCount)
        assertEquals(3, state.unsolvedCount)
        assertEquals(1, state.favoriteCount)
        assertEquals(4, state.dueReviewCount)
        assertEquals(listOf("3D"), state.availablePublishers)
        assertEquals(listOf("Deneme 1"), state.availableExamNames)
        assertTrue(state.isLoading == false)
    }

    @Test
    fun `keystrokes only update raw query, no full-list emission`() = runTest(testDispatcher) {
        val vm = KutuphaneViewModel(repo, store, TestSessionManager(), testDispatcher)
        backgroundScope.launch { vm.uiState.collect() }
        advanceUntilIdle()

        vm.onSearchQueryChange("a")
        vm.onSearchQueryChange("ab")
        vm.onSearchQueryChange("abc")
        testScheduler.advanceTimeBy(400L)
        advanceUntilIdle()

        assertEquals("abc", vm.uiState.value.searchQuery)
        // Meta değişmedi (tam liste yayını yok)
        assertEquals(5, vm.uiState.value.totalCount)
    }

    @Test
    fun `meta flow emits`() = runTest {
        val meta = repo.getKutuphaneMetaFlow().first()
        assertEquals(5, meta.totalCount)
    }
}
