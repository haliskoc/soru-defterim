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
import com.sorudefteri.data.backup.GoogleDriveBackupHelper
import com.sorudefteri.domain.model.AiProviderType
import com.sorudefteri.domain.model.AiTaskType
import com.sorudefteri.domain.model.AiTaskRoute
import com.sorudefteri.domain.model.Alan
import com.sorudefteri.domain.repository.AiRepository
import com.sorudefteri.ui.widget.SoruWidgetUpdater
import kotlinx.coroutines.flow.Flow
import com.sorudefteri.ui.ortak.drawing.StylusDoubleTapAction
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEmpty
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
    val isWifiOnly: Boolean = true,
    val driveLastSyncTime: Long = 0L,
    val isDriveSyncing: Boolean = false,
    val driveSyncMessage: String? = null,
    val isDriveSyncError: Boolean = false,
    val stylusDoubleTapAction: StylusDoubleTapAction = StylusDoubleTapAction.PEN_ERASER_TOGGLE,
    val isStylusPalmRejectionEnabled: Boolean = true,
    val isStylusHapticFeedbackEnabled: Boolean = true,
    // PERF: Hatırlatıcı bildirim anahtarları (koşulsuz 2 alarm yerine).
    val isDailyReminderEnabled: Boolean = true,
    val isStreakReminderEnabled: Boolean = true,
    val autoSolveOnSave: Boolean = false,
    val batchSolveDelaySeconds: Int = 3,
    val taskRoutes: Map<AiTaskType, AiTaskRoute?> = emptyMap()
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

private data class DriveSyncState(
    val isSyncing: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false
)

private data class BackupPrefGroup(
    val isAutoBackupEnabled: Boolean,
    val isWifiOnly: Boolean,
    val driveLastSyncTime: Long,
    val driveSyncState: DriveSyncState,
    val isDailyReminderEnabled: Boolean = true,
    val isStreakReminderEnabled: Boolean = true
)

@HiltViewModel
class AyarlarViewModel @Inject constructor(
    private val ayarlarDataStore: AyarlarDataStore,
    private val soruRepository: SoruRepository,
    private val aiRepository: AiRepository,
    private val backupManager: BackupManager,
    private val gorselDepo: GorselDepo,
    @ApplicationContext private val appContext: Context,
    val googleDriveBackupHelper: GoogleDriveBackupHelper? = null
) : ViewModel() {

    private val _dynamicModelsState = MutableStateFlow(DynamicModelsInfo())
    private val _modelStatusState = MutableStateFlow<Pair<String?, Boolean>>(null to false)
    private val _backupState = MutableStateFlow(BackupState())
    private val _driveSyncState = MutableStateFlow(DriveSyncState())
    private val _promptStatusState = MutableStateFlow<Pair<String?, Boolean>>(null to false)

    private val aiAutomationFlow = combine(
        ayarlarDataStore.autoSolveOnSave,
        ayarlarDataStore.batchSolveDelaySeconds,
        ayarlarDataStore.taskRoutes
    ) { autoSolve, batchDelay, routes ->
        AiAutomationPrefGroup(autoSolve, batchDelay, routes)
    }

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
        ayarlarDataStore.isWifiOnlyBackup,
        ayarlarDataStore.driveLastSyncTime.onEmpty { emit(0L) },
        _driveSyncState,
        ayarlarDataStore.isDailyReminderEnabled.onEmpty { emit(true) },
        ayarlarDataStore.isStreakReminderEnabled.onEmpty { emit(true) }
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val a = args as Array<Any?>
        BackupPrefGroup(
            a[0] as Boolean, a[1] as Boolean, a[2] as Long, a[3] as DriveSyncState,
            a[4] as Boolean, a[5] as Boolean
        )
    }

    private val stylusPrefsFlow = combine(
        ayarlarDataStore.stylusDoubleTapActionFlow,
        ayarlarDataStore.stylusPalmRejectionFlow,
        ayarlarDataStore.stylusHapticFeedbackFlow
    ) { action, palm, haptics -> Triple(action, palm, haptics) }

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
        backupPrefsFlow,
        stylusPrefsFlow
    ) { base, prefs, stylus ->
        AyarlarPrefGroup2(
            groqMod = base.groqMod,
            nvidiaKey = base.nvidiaKey,
            nvidiaMod = base.nvidiaMod,
            theme = base.theme,
            dynModels = base.dynModels,
            modelStatus = base.modelStatus,
            backup = base.backup,
            isAutoBackupEnabled = prefs.isAutoBackupEnabled,
            isWifiOnly = prefs.isWifiOnly,
            driveLastSyncTime = prefs.driveLastSyncTime,
            driveSyncState = prefs.driveSyncState,
            isDailyReminderEnabled = prefs.isDailyReminderEnabled,
            isStreakReminderEnabled = prefs.isStreakReminderEnabled,
            stylusAction = stylus.first,
            stylusPalm = stylus.second,
            stylusHaptics = stylus.third
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
        secondaryFlow,
        aiAutomationFlow
    ) { p1, p2, pAuto ->
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
            isWifiOnly = p2.isWifiOnly,
            driveLastSyncTime = p2.driveLastSyncTime,
            isDriveSyncing = p2.driveSyncState.isSyncing,
            driveSyncMessage = p2.driveSyncState.message,
            isDriveSyncError = p2.driveSyncState.isError,
            isDailyReminderEnabled = p2.isDailyReminderEnabled,
            isStreakReminderEnabled = p2.isStreakReminderEnabled,
            stylusDoubleTapAction = p2.stylusAction,
            isStylusPalmRejectionEnabled = p2.stylusPalm,
            isStylusHapticFeedbackEnabled = p2.stylusHaptics,
            autoSolveOnSave = pAuto.autoSolveOnSave,
            batchSolveDelaySeconds = pAuto.batchSolveDelaySeconds,
            taskRoutes = pAuto.taskRoutes
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

    // ==========================================
    // Widget & Sınav Geri Sayımı
    // ==========================================

    val examTargetDateFlow: Flow<Long> = ayarlarDataStore.examTargetDate

    fun setExamTargetDate(dateMillis: Long) {
        viewModelScope.launch {
            ayarlarDataStore.setExamTargetDate(dateMillis)
            // Widget'taki geri sayımı anında güncelle
            SoruWidgetUpdater.updateAllNow(appContext)
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

    fun setAutoSolveOnSave(enabled: Boolean) {
        viewModelScope.launch {
            ayarlarDataStore.setAutoSolveOnSave(enabled)
        }
    }

    fun setBatchSolveDelaySeconds(seconds: Int) {
        viewModelScope.launch {
            ayarlarDataStore.setBatchSolveDelaySeconds(seconds)
        }
    }

    fun setTaskRoute(task: AiTaskType, route: AiTaskRoute?) {
        viewModelScope.launch {
            ayarlarDataStore.setTaskRoute(task, route)
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

    // PERF: Hatırlatıcı anahtarları (kapalıysa WorkManager işi iptal edilir).
    fun setDailyReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            ayarlarDataStore.setDailyReminderEnabled(enabled)
            try {
                if (enabled) {
                    com.sorudefteri.data.worker.ReminderScheduler.scheduleDailyReminder(appContext)
                } else {
                    com.sorudefteri.data.worker.ReminderScheduler.cancelDailyReminder(appContext)
                }
            } catch (_: Exception) {}
        }
    }

    fun setStreakReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            ayarlarDataStore.setStreakReminderEnabled(enabled)
            try {
                if (enabled) {
                    com.sorudefteri.data.worker.ReminderScheduler.scheduleStreakReminder(appContext)
                } else {
                    com.sorudefteri.data.worker.ReminderScheduler.cancelStreakReminder(appContext)
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

    fun setStylusDoubleTapAction(action: StylusDoubleTapAction) {
        viewModelScope.launch {
            ayarlarDataStore.setStylusDoubleTapAction(action)
        }
    }

    fun setStylusPalmRejection(enabled: Boolean) {
        viewModelScope.launch {
            ayarlarDataStore.setStylusPalmRejection(enabled)
        }
    }

    fun setStylusHapticFeedback(enabled: Boolean) {
        viewModelScope.launch {
            ayarlarDataStore.setStylusHapticFeedback(enabled)
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
                // DÜZELTME: Önceden sadece "N soru eklendi" deniyordu. Yedeğin
                // bir kısmı eksik geldiğinde (görsel bulunamadı, Sokratik Koç /
                // Benzer Soru eşleşemedi) kullanıcı hiçbir uyarı almıyordu ve
                // "çözümlerim gelmedi" diye sanıyordu. Artık ayrıntıyı da gösteriyoruz.
                val ozet = backupManager.sonImportOzeti
                val mesaj = if (ozet != null && ozet.dedupKullanildi) {
                    // Akıllı içe aktarma devreye girdi: yeni/güncel/güncellenen dağılımını göster
                    buildString {
                        append(if (count > 0) "$count yeni soru eklendi ✓" else "Yeni eklenen soru yok")
                        if (ozet.guncellenenSoru > 0) {
                            append("\n🔄 ${ozet.guncellenenSoru} mevcut soru birleştirilerek güncellendi")
                        }
                        if (ozet.guncelSoru > 0) {
                            append("\n✓ ${ozet.guncelSoru} soru arşivinizde zaten günceldi (mükerrer kopya eklenmedi)")
                        }
                        if (ozet.mukerrerTest > 0 || ozet.mukerrerDeneme > 0) {
                            val miktar = ozet.mukerrerTest + ozet.mukerrerDeneme
                            append("\n✓ $miktar mükerrer test/deneme kaydı atlandı")
                        }
                        if (ozet.gorselsizSorular > 0) {
                            append("\n⚠️ ${ozet.gorselsizSorular} sorunun görseli bulunamadı (metin ve çözümler aktarıldı)")
                        }
                        if (ozet.atlananChatMesaji > 0) {
                            append("\n⚠️ ${ozet.atlananChatMesaji} Sokratik Koç mesajı aktarılamadı")
                        }
                        if (ozet.atlananBenzerSoru > 0) {
                            append("\n⚠️ ${ozet.atlananBenzerSoru} benzer soru aktarılamadı")
                        }
                    }
                } else if (ozet != null && ozet.eksikVar) {
                    buildString {
                        append("$count soru eklendi ✓")
                        if (ozet.gorselsizSorular > 0) {
                            append("\n⚠️ ${ozet.gorselsizSorular} sorunun görseli bulunamadı (metin ve çözümler aktarıldı)")
                        }
                        if (ozet.atlananChatMesaji > 0) {
                            append("\n⚠️ ${ozet.atlananChatMesaji} Sokratik Koç mesajı aktarılamadı")
                        }
                        if (ozet.atlananBenzerSoru > 0) {
                            append("\n⚠️ ${ozet.atlananBenzerSoru} benzer soru aktarılamadı")
                        }
                    }
                } else {
                    "$count soru ve görselleri kütüphanenize eklendi ✓"
                }
                _backupState.value = BackupState(
                    isLoading = false,
                    message = mesaj,
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

    fun syncGoogleDrive() {
        viewModelScope.launch {
            _driveSyncState.value = DriveSyncState(isSyncing = true, message = "Google Drive AppData senkronizasyonu başlatılıyor...")
            val helper = googleDriveBackupHelper
            if (helper == null) {
                _driveSyncState.value = DriveSyncState(
                    isSyncing = false,
                    message = "Google Drive senkronizasyon servisi hazır değil.",
                    isError = true
                )
                return@launch
            }

            val result = helper.syncToCloud(appContext)
            result.onSuccess { metadata ->
                _driveSyncState.value = DriveSyncState(
                    isSyncing = false,
                    message = "Google Drive senkronizasyonu başarıyla tamamlandı (${metadata.questionCount} soru) ✓",
                    isError = false
                )
            }.onFailure { e ->
                _driveSyncState.value = DriveSyncState(
                    isSyncing = false,
                    message = "Senkronizasyon hatası: ${e.localizedMessage ?: "Bilinmeyen hata"}",
                    isError = true
                )
            }
        }
    }

    fun clearDriveSyncStatus() {
        _driveSyncState.value = DriveSyncState()
    }

    fun resetAllData(onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                soruRepository.deleteAllSorular()
                // Diskteki görselleri, sesli notları ve çizimleri de temizle (hayalet dosya kalmasın)
                try { gorselDepo.tumGorselleriSil() } catch (_: Exception) {}
                try { java.io.File(appContext.filesDir, "audio_memos").deleteRecursively() } catch (_: Exception) {}
                try { java.io.File(appContext.filesDir, "scratchpads").deleteRecursively() } catch (_: Exception) {}
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
    val isWifiOnly: Boolean,
    val driveLastSyncTime: Long,
    val driveSyncState: DriveSyncState,
    val isDailyReminderEnabled: Boolean,
    val isStreakReminderEnabled: Boolean,
    val stylusAction: StylusDoubleTapAction,
    val stylusPalm: Boolean,
    val stylusHaptics: Boolean
)

private data class AiAutomationPrefGroup(
    val autoSolveOnSave: Boolean = false,
    val batchSolveDelaySeconds: Int = 3,
    val taskRoutes: Map<AiTaskType, AiTaskRoute?> = emptyMap()
)

