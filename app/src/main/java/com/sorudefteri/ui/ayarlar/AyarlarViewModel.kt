package com.sorudefteri.ui.ayarlar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sorudefteri.data.local.AyarlarDataStore
import com.sorudefteri.data.repository.SoruRepository
import com.sorudefteri.domain.model.AiProviderType
import com.sorudefteri.domain.model.Alan
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AyarlarUiState(
    val currentAlan: Alan = Alan.SAYISAL,
    val aiProvider: AiProviderType = AiProviderType.GEMINI,
    val geminiApiKey: String = "",
    val groqApiKey: String = "",
    val geminiModel: String = AiProviderType.GEMINI.defaultModel,
    val groqModel: String = AiProviderType.GROQ.defaultModel,
    val themeMode: Int = 0, // 0: Sistem, 1: Açık, 2: Koyu
    val isResetting: Boolean = false,
    val resetSuccess: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class AyarlarViewModel @Inject constructor(
    private val ayarlarDataStore: AyarlarDataStore,
    private val soruRepository: SoruRepository
) : ViewModel() {

    val uiState: StateFlow<AyarlarUiState> = combine(
        ayarlarDataStore.currentAlan,
        ayarlarDataStore.aiProvider,
        ayarlarDataStore.geminiApiKey,
        ayarlarDataStore.groqApiKey,
        ayarlarDataStore.geminiModel,
        ayarlarDataStore.groqModel,
        ayarlarDataStore.themeMode
    ) { alan, provider, geminiKey, groqKey, geminiMod, groqMod, theme ->
        AyarlarUiState(
            currentAlan = alan,
            aiProvider = provider,
            geminiApiKey = geminiKey,
            groqApiKey = groqKey,
            geminiModel = geminiMod,
            groqModel = groqMod,
            themeMode = theme
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AyarlarUiState()
    )

    fun setAlan(alan: Alan) {
        viewModelScope.launch {
            ayarlarDataStore.setAlan(alan)
        }
    }

    fun setAiProvider(provider: AiProviderType) {
        viewModelScope.launch {
            ayarlarDataStore.setAiProvider(provider)
        }
    }

    fun setGeminiApiKey(key: String) {
        viewModelScope.launch {
            ayarlarDataStore.setGeminiApiKey(key)
        }
    }

    fun setGroqApiKey(key: String) {
        viewModelScope.launch {
            ayarlarDataStore.setGroqApiKey(key)
        }
    }

    fun setGeminiModel(model: String) {
        viewModelScope.launch {
            ayarlarDataStore.setGeminiModel(model)
        }
    }

    fun setGroqModel(model: String) {
        viewModelScope.launch {
            ayarlarDataStore.setGroqModel(model)
        }
    }

    fun setThemeMode(mode: Int) {
        viewModelScope.launch {
            ayarlarDataStore.setThemeMode(mode)
        }
    }

    fun resetAllData(onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                soruRepository.deleteAllSorular()
                onSuccess()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
