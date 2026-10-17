package com.sorudefteri.ui.ayarlar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sorudefteri.ui.ayarlar.components.AiPromptAyarlarTab
import com.sorudefteri.ui.ayarlar.components.GenelAyarlarTab
import com.sorudefteri.ui.ayarlar.components.YedeklemeGuvenlikTab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AyarlarScreen(
    viewModel: AyarlarViewModel = hiltViewModel(),
    // Dosya yöneticisinden .sdefteri ile açılışta gelen bekleyen içe aktarma Uri'si
    pendingImportUri: String? = null,
    onPendingImportHandled: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTabIndex by rememberSaveable { mutableStateOf(0) }
    var showResetConfirmDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(uiState.resetSuccess) {
        if (uiState.resetSuccess) {
            snackbarHostState.showSnackbar("Tüm veriler ve ayarlar başarıyla sıfırlandı.")
            viewModel.clearResetSuccess()
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearErrorMessage()
        }
    }

    val promptStatus by viewModel.promptStatus.collectAsStateWithLifecycle()

    // Sınav geri sayım tarihi (widget ile senkron) — GenelAyarlarTab'a geçirilir
    val examTargetDate by viewModel.examTargetDateFlow.collectAsStateWithLifecycle(initialValue = 0L)
    LaunchedEffect(promptStatus) {
        promptStatus.first?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearPromptStatus()
        }
    }

    // Dosyadan .sdefteri ile açılış: doğrudan Yedek sekmesine geç
    LaunchedEffect(pendingImportUri) {
        if (pendingImportUri != null) selectedTabIndex = 2
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Ayarlar",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = when (selectedTabIndex) {
                                1 -> "Adım 2 — AI sağlayıcı & promptlar"
                                2 -> "Adım 3 — Yedekleme & güvenlik"
                                else -> "Adım 1 — Genel tercihler"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 3'lü Tab Seçici — TELEFON: kısa etiketler (360dp'de taşmıyordu)
            PrimaryTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text("1 Genel", fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Normal, maxLines = 1) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Genel Ayarlar") }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text("2 AI", fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Normal, maxLines = 1) },
                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "AI & Prompt") }
                )
                Tab(
                    selected = selectedTabIndex == 2,
                    onClick = { selectedTabIndex = 2 },
                    text = { Text("3 Yedek", fontWeight = if (selectedTabIndex == 2) FontWeight.Bold else FontWeight.Normal, maxLines = 1) },
                    icon = { Icon(Icons.Default.CloudUpload, contentDescription = "Yedek & Güvenlik") }
                )
            }

            // Tab İçeriği
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                when (selectedTabIndex) {
                    0 -> GenelAyarlarTab(
                        currentAlan = uiState.currentAlan,
                        themeMode = uiState.themeMode,
                        onAlanSelected = { viewModel.setAlan(it) },
                        onThemeModeSelected = { viewModel.setThemeMode(it) },
                        onResetAllDataClick = { showResetConfirmDialog = true },
                        examTargetDateMillis = examTargetDate,
                        onExamDateSelected = { viewModel.setExamTargetDate(it) },
                        stylusDoubleTapAction = uiState.stylusDoubleTapAction,
                        isStylusPalmRejectionEnabled = uiState.isStylusPalmRejectionEnabled,
                        isStylusHapticFeedbackEnabled = uiState.isStylusHapticFeedbackEnabled,
                        onStylusDoubleTapActionSelected = { viewModel.setStylusDoubleTapAction(it) },
                        onStylusPalmRejectionToggle = { viewModel.setStylusPalmRejection(it) },
                        onStylusHapticFeedbackToggle = { viewModel.setStylusHapticFeedback(it) }
                    )

                    1 -> {
                        val promptSoru by viewModel.promptSoruUretimiFlow.collectAsStateWithLifecycle()
                        val promptCozum by viewModel.promptCozumUretimiFlow.collectAsStateWithLifecycle()
                        val promptOcr by viewModel.promptOcrTemizlemeFlow.collectAsStateWithLifecycle()
                        val promptBenzer by viewModel.promptBenzerSoruFlow.collectAsStateWithLifecycle()
                        AiPromptAyarlarTab(
                            aiProvider = uiState.aiProvider,
                            geminiApiKey = uiState.geminiApiKey,
                            groqApiKey = uiState.groqApiKey,
                            nvidiaApiKey = uiState.nvidiaApiKey,
                            geminiModel = uiState.geminiModel,
                            groqModel = uiState.groqModel,
                            nvidiaModel = uiState.nvidiaModel,
                            isFetchingModels = uiState.isFetchingModels,
                            fetchedGeminiModels = uiState.fetchedGeminiModels,
                            fetchedGroqModels = uiState.fetchedGroqModels,
                            fetchedNvidiaModels = uiState.fetchedNvidiaModels,
                            modelFetchStatus = uiState.modelFetchStatus,
                            isModelFetchError = uiState.isModelFetchError,
                            onAiProviderSelected = { viewModel.setAiProvider(it) },
                            onGeminiApiKeyChanged = { viewModel.setGeminiApiKey(it) },
                            onGroqApiKeyChanged = { viewModel.setGroqApiKey(it) },
                            onNvidiaApiKeyChanged = { viewModel.setNvidiaApiKey(it) },
                            onGeminiModelSelected = { viewModel.setGeminiModel(it) },
                            onGroqModelSelected = { viewModel.setGroqModel(it) },
                            onNvidiaModelSelected = { viewModel.setNvidiaModel(it) },
                            onFetchLiveModelsClick = { viewModel.fetchLiveModels(it) },
                            customPrompts = mapOf(
                                com.sorudefteri.data.ai.AiPromptType.SORU_URETIMI to promptSoru,
                                com.sorudefteri.data.ai.AiPromptType.COZUM_URETIMI to promptCozum,
                                com.sorudefteri.data.ai.AiPromptType.OCR_TEMIZLEME to promptOcr,
                                com.sorudefteri.data.ai.AiPromptType.BENZER_SORU to promptBenzer
                            ),
                            onSavePrompt = { type, text -> viewModel.setCustomPrompt(type, text) },
                            onResetPrompt = { type -> viewModel.resetPrompt(type) },
                            onResetAllPrompts = { viewModel.resetAllPrompts() }
                        )
                    }

                    2 -> YedeklemeGuvenlikTab(
                        isBackupLoading = uiState.isBackupLoading,
                        backupMessage = uiState.backupMessage,
                        isBackupError = uiState.isBackupError,
                        isAutoBackupEnabled = uiState.isAutoBackupEnabled,
                        isWifiOnly = uiState.isWifiOnly,
                        driveLastSyncTime = uiState.driveLastSyncTime,
                        isDriveSyncing = uiState.isDriveSyncing,
                        driveSyncMessage = uiState.driveSyncMessage,
                        isDriveSyncError = uiState.isDriveSyncError,
                        onExportBackup = { uri, pwd -> viewModel.exportBackup(uri, pwd) },
                        onImportBackup = { uri, pwd -> viewModel.importBackup(uri, pwd) },
                        onAutoBackupToggle = { viewModel.setAutoBackupEnabled(it) },
                        onWifiOnlyToggle = { viewModel.setWifiOnly(it) },
                        onSyncDrive = { viewModel.syncGoogleDrive() },
                        // Dosyadan açma: Yedek sekmesinde değilsek sekmeyi aç, onay dialogu tab'da çıkar
                        pendingImportUri = pendingImportUri,
                        onPendingImportHandled = onPendingImportHandled
                    )
                }
            }
        }
    }

    // Verileri Sıfırlama Onay Dialogu
    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = { Text("Tüm Verileri Sıfırla", fontWeight = FontWeight.Bold) },
            text = {
                Text("Bu işlem veritabanındaki tüm soruları, görselleri, test geçmişini ve kişiselleştirilmiş ayarlarınızı silecektir. Devam etmek istiyor musunuz?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirmDialog = false
                        viewModel.resetAllData()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Evet, Her Şeyi Sıfırla")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showResetConfirmDialog = false }) {
                    Text("İptal")
                }
            }
        )
    }
}
