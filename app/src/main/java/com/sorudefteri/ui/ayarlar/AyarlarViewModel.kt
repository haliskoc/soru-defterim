package com.sorudefteri.ui.ayarlar

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sorudefteri.data.ai.AiPromptType
import com.sorudefteri.data.backup.AutoBackupWorker
import com.sorudefteri.data.backup.BackupManager
import com.sorudefteri.data.images.GorselDepo
import com.sorudefteri.data.local.AyarlarDataStore
import com.sorudefteri.data.repository.SoruRepository
import com.sorudefteri.domain.model.AiProviderType
import com.sorudefteri.domain.model.Alan
import com.sorudefteri.domain.repository.AiRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
    private val backupManager: BackupManager,
    private val gorselDepo: GorselDepo,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _dynamicModelsState = MutableStateFlow(DynamicModelsInfo())
    private val _modelStatusState = MutableStateFlow<Pair<String?, Boolean>>(null to false)
    private val _backupState = MutableStateFlow(BackupState())
    private val _promptStatusState = MutableStateFlow<Pair<String?, Boolean>>(null to false)

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

    // Prompt flows exposed directly for UI collect
    val promptSoruUretimiFlow = ayarlarDataStore.promptSoruUretimiFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val promptCozumUretimiFlow = ayarlarDataStore.promptCozumUretimiFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val promptOcrTemizlemeFlow = ayarlarDataStore.promptOcrTemizlemeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val promptBenzerSoruFlow = ayarlarDataStore.promptBenzerSoruFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val promptStatus: StateFlow<Pair<String?, Boolean>> = _promptStatusState

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
            try {
                if (enabled) {
                    val wifiOnly = ayarlarDataStore.isWifiOnlyBackup.first()
                    AutoBackupWorker.schedulePeriodic(appContext, wifiOnly)
                } else {
                    AutoBackupWorker.cancel(appContext)
                }
            } catch (_: Exception) {}
        }
    }

    fun setWifiOnly(enabled: Boolean) {
        viewModelScope.launch {
            ayarlarDataStore.setWifiOnlyBackup(enabled)
            try {
                val isAutoEnabled = ayarlarDataStore.isAutoBackupEnabled.first()
                if (isAutoEnabled) {
                    AutoBackupWorker.schedulePeriodic(appContext, enabled)
                }
            } catch (_: Exception) {}
        }
    }

    fun setCustomPrompt(type: AiPromptType, prompt: String) {
        viewModelScope.launch {
            // Validasyon: uzunluk
            if (prompt.length > 8000) {
                _promptStatusState.value = "Prompt çok uzun (${prompt.length}/8000)" to true
                return@launch
            }
            // JSON beklenen tiplerde şema anahtarı kontrolü
            if (type == AiPromptType.SORU_URETIMI || type == AiPromptType.BENZER_SORU) {
                if (prompt.isNotBlank() && (!prompt.contains("questionText") || !prompt.contains("correctAnswer"))) {
                    _promptStatusState.value = "Uyarı: JSON şeması questionText/correctAnswer içermiyor, parse hatası olabilir." to true
                    // yine de kaydet, sadece uyar
                }
            }
            try {
                ayarlarDataStore.setCustomPrompt(type, prompt)
                _promptStatusState.value = "${type.title} kaydedildi ✓" to false
            } catch (e: Exception) {
                _promptStatusState.value = "Kaydetme hatası: ${e.localizedMessage}" to true
            }
        }
    }

    fun resetPrompt(type: AiPromptType) {
        viewModelScope.launch {
            try {
                ayarlarDataStore.resetPromptToDefault(type)
                _promptStatusState.value = "${type.title} varsayılana döndü ✓" to false
            } catch (e: Exception) {
                _promptStatusState.value = "Sıfırlama hatası: ${e.localizedMessage}" to true
            }
        }
    }

    fun resetAllPrompts() {
        viewModelScope.launch {
            try {
                ayarlarDataStore.resetAllPromptsToDefault()
                _promptStatusState.value = "Tüm promptlar varsayılana döndü ✓" to false
            } catch (e: Exception) {
                _promptStatusState.value = "Sıfırlama hatası: ${e.localizedMessage}" to true
            }
        }
    }

    fun clearPromptStatus() {
        _promptStatusState.value = null to false
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

    fun shareBackupToDriveOrCloud(context: android.content.Context? = null, password: String? = null) {
        val targetContext = context?.applicationContext ?: appContext
        viewModelScope.launch {
            _backupState.value = BackupState(isLoading = true, message = "Yerel yedek paketi hazırlanıyor (Bulut kaldırıldı)...")
            val result = backupManager.exportBackupToTempFile(password)
            result.onSuccess { (file, count) ->
                _backupState.value = BackupState(
                    isLoading = false,
                    message = "$count soru hazırlandı. Paylaşım menüsü açılıyor...",
                    isError = false
                )
                com.sorudefteri.ui.ortak.ShareHelper.shareBackupFile(targetContext, file)
            }.onFailure { e ->
                _backupState.value = BackupState(
                    isLoading = false,
                    message = "Yedekleme hatası: ${e.localizedMessage}",
                    isError = true
                )
            }
        }
    }

    fun shareBackupFile(context: android.content.Context? = null, password: String? = null) = shareBackupToDriveOrCloud(context, password)

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
                // Diskteki görselleri de temizle (hayalet dosya kalmasın)
                try { gorselDepo.tumGorselleriSil() } catch (_: Exception) {}
                // Ayarları temizle ama onboarding flag'ini resetleme? Şimdilik tümü siliniyor, kullanıcı onboarding tekrar görecek
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

