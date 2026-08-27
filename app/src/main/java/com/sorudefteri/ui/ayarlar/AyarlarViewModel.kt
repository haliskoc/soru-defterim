package com.sorudefteri.ui.ayarlar

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sorudefteri.data.backup.BackupManager
import com.sorudefteri.data.local.AyarlarDataStore
import com.sorudefteri.data.repository.SoruRepository
import com.sorudefteri.domain.model.AiProviderType
import com.sorudefteri.domain.model.Alan
import com.sorudefteri.domain.repository.AiRepository
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
    val nvidiaApiKey: String = "",
    val geminiModel: String = AiProviderType.GEMINI.defaultModel,
    val groqModel: String = AiProviderType.GROQ.defaultModel,
    val nvidiaModel: String = AiProviderType.NVIDIA.defaultModel,
    val themeMode: Int = 0, // 0: Sistem, 1: Açık, 2: Koyu
    val isResetting: Boolean = false,
    val resetSuccess: Boolean = false,
    val errorMessage: String? = null,
    val isFetchingModels: Boolean = false,
    val fetchedGeminiModels: List<Pair<String, String>> = emptyList(),
    val fetchedGroqModels: List<Pair<String, String>> = emptyList(),
    val fetchedNvidiaModels: List<Pair<String, String>> = emptyList(),
    val modelFetchStatus: String? = null,
    val isModelFetchError: Boolean = false,
    val isBackupLoading: Boolean = false,
    val backupMessage: String? = null,
    val isBackupError: Boolean = false,
    val isAutoBackupEnabled: Boolean = false,
    val isWifiOnly: Boolean = true
)

private data class DynamicModelsInfo(
    val isFetching: Boolean = false,
    val geminiModels: List<Pair<String, String>> = emptyList(),
    val groqModels: List<Pair<String, String>> = emptyList(),
    val nvidiaModels: List<Pair<String, String>> = emptyList()
)

private data class BackupState(
    val isLoading: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false
)

@HiltViewModel
class AyarlarViewModel @Inject constructor(
    private val ayarlarDataStore: AyarlarDataStore,
    private val soruRepository: SoruRepository,
    private val aiRepository: AiRepository,
    private val backupManager: BackupManager
) : ViewModel() {

    private val _dynamicModelsState = MutableStateFlow(DynamicModelsInfo())
    private val _modelStatusState = MutableStateFlow<Pair<String?, Boolean>>(null to false)
    private val _backupState = MutableStateFlow(BackupState())

    private val preferencesFlow = combine(
        ayarlarDataStore.currentAlan,
        ayarlarDataStore.aiProvider,
        ayarlarDataStore.geminiApiKey,
        ayarlarDataStore.groqApiKey,
        ayarlarDataStore.geminiModel
    ) { alan, provider, geminiKey, groqKey, geminiMod ->
        AyarlarPrefGroup1(alan, provider, geminiKey, groqKey, geminiMod)
    }

    private val nvidiaCombinedFlow = combine(
        ayarlarDataStore.groqModel,
        ayarlarDataStore.nvidiaApiKey,
        ayarlarDataStore.nvidiaModel
    ) { groqMod, nvidiaKey, nvidiaMod -> Triple(groqMod, nvidiaKey, nvidiaMod) }

    private val backupPrefsFlow = combine(
        ayarlarDataStore.isAutoBackupEnabled,
        ayarlarDataStore.isWifiOnlyBackup
    ) { auto, wifi -> auto to wifi }

    private val baseSecondaryFlow = combine(
        nvidiaCombinedFlow,
        ayarlarDataStore.themeMode,
        _dynamicModelsState,
        _modelStatusState,
        _backupState
    ) { triple, theme, dynModels, modelStatus, backup ->
        AyarlarPrefGroup2Base(triple.first, triple.second, triple.third, theme, dynModels, modelStatus, backup)
    }

    private val secondaryFlow = combine(
        baseSecondaryFlow,
        backupPrefsFlow
    ) { base, prefs ->
        AyarlarPrefGroup2(
            groqMod = base.groqMod,
            nvidiaKey = base.nvidiaKey,
            nvidiaMod = base.nvidiaMod,
            theme = base.theme,
            dynModels = base.dynModels,
            modelStatus = base.modelStatus,
            backup = base.backup,
            isAutoBackupEnabled = prefs.first,
            isWifiOnly = prefs.second
        )
    }

    val uiState: StateFlow<AyarlarUiState> = combine(
        preferencesFlow,
        secondaryFlow
    ) { p1, p2 ->
        AyarlarUiState(
            currentAlan = p1.alan,
            aiProvider = p1.provider,
            geminiApiKey = p1.geminiKey,
            groqApiKey = p1.groqKey,
            nvidiaApiKey = p2.nvidiaKey,
            geminiModel = p1.geminiMod,
            groqModel = p2.groqMod,
            nvidiaModel = p2.nvidiaMod,
            themeMode = p2.theme,
            isFetchingModels = p2.dynModels.isFetching,
            fetchedGeminiModels = p2.dynModels.geminiModels,
            fetchedGroqModels = p2.dynModels.groqModels,
            fetchedNvidiaModels = p2.dynModels.nvidiaModels,
            modelFetchStatus = p2.modelStatus.first,
            isModelFetchError = p2.modelStatus.second,
            isBackupLoading = p2.backup.isLoading,
            backupMessage = p2.backup.message,
            isBackupError = p2.backup.isError,
            isAutoBackupEnabled = p2.isAutoBackupEnabled,
            isWifiOnly = p2.isWifiOnly
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

    fun setNvidiaApiKey(key: String) {
        viewModelScope.launch {
            ayarlarDataStore.setNvidiaApiKey(key)
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

    fun setNvidiaModel(model: String) {
        viewModelScope.launch {
            ayarlarDataStore.setNvidiaModel(model)
        }
    }

    fun setThemeMode(mode: Int) {
        viewModelScope.launch {
            ayarlarDataStore.setThemeMode(mode)
        }
    }

    fun setAutoBackupEnabled(enabled: Boolean) {
        viewModelScope.launch {
            ayarlarDataStore.setAutoBackupEnabled(enabled)
            if (enabled) {
                // schedule will be handled by Application observer; immediate trigger via datastore
            }
        }
    }

    fun setWifiOnly(enabled: Boolean) {
        viewModelScope.launch {
            ayarlarDataStore.setWifiOnlyBackup(enabled)
        }
    }

    fun fetchLiveModels(provider: AiProviderType) {
        viewModelScope.launch {
            _dynamicModelsState.update { it.copy(isFetching = true) }
            _modelStatusState.value = null to false

            val result = aiRepository.fetchLiveModels(provider)
            result.onSuccess { models ->
                _dynamicModelsState.update { current ->
                    when (provider) {
                        AiProviderType.GEMINI -> current.copy(isFetching = false, geminiModels = models)
                        AiProviderType.GROQ -> current.copy(isFetching = false, groqModels = models)
                        AiProviderType.NVIDIA -> current.copy(isFetching = false, nvidiaModels = models)
                    }
                }
                val providerLabel = when (provider) {
                    AiProviderType.GEMINI -> "Google AI Studio"
                    AiProviderType.GROQ -> "Groq"
                    AiProviderType.NVIDIA -> "NVIDIA Build"
                }
                _modelStatusState.value = "$providerLabel üzerinden ${models.size} güncel model başarıyla çekildi ✓" to false
            }.onFailure { e ->
                _dynamicModelsState.update { it.copy(isFetching = false) }
                val errorMsg = e.localizedMessage ?: "Model listesi alınırken hata oluştu."
                _modelStatusState.value = errorMsg to true
            }
        }
    }

    fun clearModelFetchStatus() {
        _modelStatusState.value = null to false
    }

    fun exportBackup(targetUri: Uri, password: String? = null) {
        viewModelScope.launch {
            _backupState.value = BackupState(isLoading = true, message = "Yedek paketi (.sdefteri) hazırlanıyor...")
            val result = backupManager.exportBackup(targetUri, password)
            result.onSuccess { count ->
                _backupState.value = BackupState(
                    isLoading = false,
                    message = "$count soru ve tüm görseller başarıyla dışa aktarıldı ✓",
                    isError = false
                )
            }.onFailure { e ->
                _backupState.value = BackupState(
                    isLoading = false,
                    message = "Dışa aktarma hatası: ${e.localizedMessage}",
                    isError = true
                )
            }
        }
    }

    @Deprecated("Drive senkron kaldırıldı, yerel yedek kullanın", ReplaceWith("exportBackup(uri, password)"))
    fun shareBackupToDriveOrCloud(context: android.content.Context, password: String? = null) {
        viewModelScope.launch {
            _backupState.value = BackupState(isLoading = true, message = "Yerel yedek paketi hazırlanıyor (Bulut kaldırıldı)...")
            val result = backupManager.exportBackupToTempFile(password)
            result.onSuccess { (file, count) ->
                _backupState.value = BackupState(
                    isLoading = false,
                    message = "$count soru hazırlandı. Paylaşım menüsü açılıyor...",
                    isError = false
                )
                com.sorudefteri.ui.ortak.ShareHelper.shareBackupFile(context, file)
            }.onFailure { e ->
                _backupState.value = BackupState(
                    isLoading = false,
                    message = "Yedekleme hatası: ${e.localizedMessage}",
                    isError = true
                )
            }
        }
    }

    fun shareBackupFile(context: android.content.Context, password: String? = null) = shareBackupToDriveOrCloud(context, password)

    fun importBackup(sourceUri: Uri, password: String? = null) {
        viewModelScope.launch {
            _backupState.value = BackupState(isLoading = true, message = "Yedek paketi açılıyor ve görseller aktarılıyor...")
            val result = backupManager.importBackup(sourceUri, password)
            result.onSuccess { count ->
                _backupState.value = BackupState(
                    isLoading = false,
                    message = "$count soru ve görselleri kütüphanenize eklendi ✓",
                    isError = false
                )
            }.onFailure { e ->
                _backupState.value = BackupState(
                    isLoading = false,
                    message = "İçe aktarma hatası: ${e.localizedMessage}",
                    isError = true
                )
            }
        }
    }

    fun clearBackupStatus() {
        _backupState.value = BackupState()
    }

    fun clearResetSuccess() {}

    fun clearErrorMessage() {}

    fun resetAllData(onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                soruRepository.deleteAllSorular()
                ayarlarDataStore.clearSettings()
                onSuccess()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

private data class AyarlarPrefGroup1(
    val alan: Alan,
    val provider: AiProviderType,
    val geminiKey: String,
    val groqKey: String,
    val geminiMod: String
)

private data class AyarlarPrefGroup2Base(
    val groqMod: String,
    val nvidiaKey: String,
    val nvidiaMod: String,
    val theme: Int,
    val dynModels: DynamicModelsInfo,
    val modelStatus: Pair<String?, Boolean>,
    val backup: BackupState
)

private data class AyarlarPrefGroup2(
    val groqMod: String,
    val nvidiaKey: String,
    val nvidiaMod: String,
    val theme: Int,
    val dynModels: DynamicModelsInfo,
    val modelStatus: Pair<String?, Boolean>,
    val backup: BackupState,
    val isAutoBackupEnabled: Boolean,
    val isWifiOnly: Boolean
)

