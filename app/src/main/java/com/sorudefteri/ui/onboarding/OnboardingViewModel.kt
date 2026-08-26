package com.sorudefteri.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sorudefteri.data.local.AyarlarDataStore
import com.sorudefteri.domain.model.Alan
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val selectedAlan: Alan? = null,
    val isSaving: Boolean = false,
    val isCompleted: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val ayarlarDataStore: AyarlarDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun selectAlan(alan: Alan) {
        _uiState.update { it.copy(selectedAlan = alan, errorMessage = null) }
    }

    fun completeOnboarding(onSuccess: () -> Unit) {
        val alan = _uiState.value.selectedAlan
        if (alan == null) {
            _uiState.update { it.copy(errorMessage = "Lütfen devam etmek için hazırlık alanınızı seçin.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            try {
                ayarlarDataStore.setAlan(alan)
                ayarlarDataStore.setOnboardingSeen(true)
                _uiState.update { it.copy(isSaving = false, isCompleted = true) }
                onSuccess()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = "Ayarlar kaydedilirken bir hata oluştu: ${e.localizedMessage}"
                    )
                }
            }
        }
    }
}
