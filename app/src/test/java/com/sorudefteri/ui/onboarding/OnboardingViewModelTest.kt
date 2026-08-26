package com.sorudefteri.ui.onboarding

import com.sorudefteri.data.local.AyarlarDataStore
import com.sorudefteri.domain.model.Alan
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val ayarlarDataStore: AyarlarDataStore = mockk(relaxed = true)
    private lateinit var viewModel: OnboardingViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = OnboardingViewModel(ayarlarDataStore)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialUiState_hasNullSelectedAlan() {
        val state = viewModel.uiState.value
        assertNull(state.selectedAlan)
        assertFalse(state.isSaving)
        assertFalse(state.isCompleted)
        assertNull(state.errorMessage)
    }

    @Test
    fun selectAlan_updatesSelectedAlan() {
        viewModel.selectAlan(Alan.SAYISAL)
        assertEquals(Alan.SAYISAL, viewModel.uiState.value.selectedAlan)

        viewModel.selectAlan(Alan.KPSS)
        assertEquals(Alan.KPSS, viewModel.uiState.value.selectedAlan)

        viewModel.selectAlan(Alan.LGS)
        assertEquals(Alan.LGS, viewModel.uiState.value.selectedAlan)
    }

    @Test
    fun completeOnboarding_withoutAlan_showsError() {
        var successCalled = false
        viewModel.completeOnboarding { successCalled = true }

        assertFalse(successCalled)
        assertNotNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun completeOnboarding_withAlan_persistsDataAndCallsSuccess() = runTest {
        coEvery { ayarlarDataStore.setAlan(Alan.ESIT_AGIRLIK) } returns Unit
        coEvery { ayarlarDataStore.setOnboardingSeen(true) } returns Unit

        viewModel.selectAlan(Alan.ESIT_AGIRLIK)

        var successCalled = false
        viewModel.completeOnboarding { successCalled = true }

        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { ayarlarDataStore.setAlan(Alan.ESIT_AGIRLIK) }
        coVerify { ayarlarDataStore.setOnboardingSeen(true) }
        assertTrue(successCalled)
        assertTrue(viewModel.uiState.value.isCompleted)
    }
}
