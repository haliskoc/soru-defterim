package com.sorudefteri.ui.detay

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.School
import com.sorudefteri.ui.detay.components.ExternalAiMode
import com.sorudefteri.ui.detay.components.ExternalAiShareDialog
import com.sorudefteri.ui.detay.components.ManualSimilarQuestionDialog
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sorudefteri.ui.detay.components.AiCozumTab
import com.sorudefteri.ui.detay.components.BenzerSorularTab
import com.sorudefteri.ui.detay.components.ManualSolutionDialog
import com.sorudefteri.ui.detay.components.SoruDetayTab
import com.sorudefteri.ui.detay.components.SoruDetayTopBar
import com.sorudefteri.ui.ortak.ShareHelper
import com.sorudefteri.ui.ortak.TeacherShareDialog
import com.sorudefteri.ui.ortak.TeacherShareHelper
import com.sorudefteri.ui.ortak.ZoomableImageDialog
import com.sorudefteri.ui.detay.components.SoruChatBottomSheet
import com.sorudefteri.ui.detay.components.SoruChatContent
import com.sorudefteri.ui.ortak.audio.AudioMemoCard
import com.sorudefteri.ui.ortak.audio.AudioMemoManager
import com.sorudefteri.ui.ortak.drawing.DrawingOverlayCanvas
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoruDetayScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (Long) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: SoruDetayViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedTabIndex by rememberSaveable { mutableStateOf(0) }
    var editedNoteText by rememberSaveable { mutableStateOf("") }
    var showTeacherShareDialog by rememberSaveable { mutableStateOf(false) }

    val soru = uiState.soru

    // Sesli Not Yöneticisi (AAC/M4A 60s)
    val audioMemoManager = remember { AudioMemoManager(context, coroutineScope) }
    DisposableEffect(audioMemoManager) {
        onDispose {
            audioMemoManager.release()
        }
    }

    // Kalem / Karalama Modu (Varsayılan olarak PASİF / KAPALI)
    var isDrawingModeActive by rememberSaveable(soru?.id) { mutableStateOf(false) }

    val handleCopyImageToClipboard: () -> Unit = {
        if (soru != null) {
            val copied = ShareHelper.copyQuestionImageToClipboard(context, soru.imagePath)
            if (copied) {
                Toast.makeText(context, "Soru görseli panoya kopyalandı", Toast.LENGTH_SHORT).show()
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Soru görseli panoya kopyalandı")
                }
            } else {
                Toast.makeText(context, "Görsel kopyalanamadı (dosya bulunamadı)", Toast.LENGTH_SHORT).show()
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Görsel kopyalanamadı (dosya bulunamadı)")
                }
            }
        }
    }

    LaunchedEffect(uiState.ocrMessage) {
        uiState.ocrMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearOcrMessage()
        }
    }

    LaunchedEffect(uiState.similarSaveMessage) {
        uiState.similarSaveMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (soru == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Soru bulunamadı veya silinmiş.", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onNavigateBack) { Text("Geri Dön") }
            }
        }
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isTabletLayout = maxWidth >= 700.dp
        var selectedTabletTabIndex by rememberSaveable { mutableStateOf(0) }

        Scaffold(
            topBar = {
                SoruDetayTopBar(
                    isFavorite = soru.isFavorite,
                    isDrawingActive = isDrawingModeActive,
                    compactActions = !isTabletLayout,
                    onToggleDrawing = {
                        isDrawingModeActive = !isDrawingModeActive
                    },
                    onCopyImageClick = handleCopyImageToClipboard,
                    onBackClick = onNavigateBack,
                    onFavoriteToggle = { viewModel.toggleFavorite() },
                    onEditClick = { onNavigateToEdit(soru.id) },
                    onDeleteClick = { viewModel.showDeleteConfirmDialog(true) },
                    onShareClick = {
                        ShareHelper.shareQuestion(
                            context = context,
                            imagePath = soru.imagePath,
                            dersAdi = soru.subjectId,
                            topic = soru.topic,
                            correctAnswer = soru.correctAnswer,
                            note = soru.note,
                            aiSolution = soru.aiExplanation
                        )
                    },
                    onShareExternalAiClick = {
                        viewModel.setShowExternalAiShareDialog(true, ExternalAiMode.SOLUTION)
                    },
                    onTeacherShareClick = {
                        showTeacherShareDialog = true
                    }
                )
            },
            floatingActionButton = {
                if (!isTabletLayout) {
                    ExtendedFloatingActionButton(
                        onClick = { viewModel.toggleChatBottomSheet(true) },
                        icon = { Icon(Icons.Default.Psychology, contentDescription = null) },
                        text = { Text("Sokratik Koç") },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isTabletLayout) {
                    // 2.3 Tablet Split-Pane Studio: %55 Sol (Soru + Çizim Canvas + Sesli Not), %45 Sağ (AI Koçu / Chatbot / Benzer Soru)
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Sol Panel: Soru Detayı & Çizim Canvas & Sesli Not
                    Box(
                        modifier = Modifier
                            .weight(0.55f)
                            .fillMaxHeight()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 16.dp)
                        ) {
                            SoruDetayTab(
                                soru = soru,
                                extractedOcrText = uiState.extractedOcrText ?: soru.ocrMetni,
                                sendImageToAi = uiState.sendImageToAi,
                                isOcrLoading = uiState.isOcrLoading,
                                onZoomImage = { path, title -> viewModel.openZoomImage(path, title) },
                                onCopyImageClick = handleCopyImageToClipboard,
                                onToggleSolved = { viewModel.toggleCozumDurumu() },
                                onEditNoteClick = {
                                    editedNoteText = soru.note ?: ""
                                    viewModel.showEditNoteDialog(true)
                                },
                                onPerformOcr = { viewModel.performOcrOnQuestionImage() },
                                onToggleSendImageToAi = { viewModel.setSendImageToAi(it) },
                                onAdvanceLeitnerStage = { _ -> viewModel.advanceReviewStage() },
                                onTeacherShareClick = { showTeacherShareDialog = true },
                                audioMemoContent = {
                                    AudioMemoCard(
                                        audioPath = soru.audioPath,
                                        soruId = soru.id,
                                        audioManager = audioMemoManager,
                                        onAudioRecorded = { path -> viewModel.saveAudioMemo(path) },
                                        onAudioDeleted = { viewModel.deleteAudioMemo() }
                                    )
                                }
                            )

                            Spacer(modifier = Modifier.navigationBarsPadding().height(48.dp))
                        }

                        // Tablet Çizim Katmanı
                        DrawingOverlayCanvas(
                            soruId = soru.id,
                            modifier = Modifier.fillMaxSize(),
                            isDrawingActive = isDrawingModeActive,
                            onDrawingActiveChange = { active ->
                                isDrawingModeActive = active
                            },
                            onCoachHintRequested = {
                                selectedTabletTabIndex = 0
                            }
                        )
                    }

                    // Panel Ayırıcı Dikey Çizgi
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    )

                    // Sağ Panel: AI Çözümü & Sokratik Koç & Benzer Sorular
                    Column(
                        modifier = Modifier
                            .weight(0.45f)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        PrimaryTabRow(
                            selectedTabIndex = selectedTabletTabIndex,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ) {
                            Tab(
                                selected = selectedTabletTabIndex == 0,
                                onClick = { selectedTabletTabIndex = 0 },
                                text = { Text("AI Çözüm & Koç", fontWeight = if (selectedTabletTabIndex == 0) FontWeight.Bold else FontWeight.Normal) },
                                icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "AI Çözüm") }
                            )
                            Tab(
                                selected = selectedTabletTabIndex == 1,
                                onClick = { selectedTabletTabIndex = 1 },
                                text = { Text("Sokratik Koç", fontWeight = if (selectedTabletTabIndex == 1) FontWeight.Bold else FontWeight.Normal) },
                                icon = { Icon(Icons.Default.Psychology, contentDescription = "Sokratik Koç") }
                            )
                            Tab(
                                selected = selectedTabletTabIndex == 2,
                                onClick = { selectedTabletTabIndex = 2 },
                                text = { Text("Benzer Soru", fontWeight = if (selectedTabletTabIndex == 2) FontWeight.Bold else FontWeight.Normal) },
                                icon = { Icon(Icons.Default.School, contentDescription = "Benzer Soru") }
                            )
                        }

                        when (selectedTabletTabIndex) {
                            0 -> Column(
                                modifier = Modifier
                                    // fillMaxSize() bir Column içinde SADECE o Column'ın
                                    // kendi maxHeight'ine göre çözülür; PrimaryTabRow'un
                                    // kapladığı yüksekliği DÜŞMEZ. Sonuç: kaydırma
                                    // alanı panelden ~1 sekme kadar uzun olur ve listenin
                                    // sonundaki kartlar hiçbir zaman görünür alana
                                    // gelemez. weight(1f) kalan gerçek yüksekliği alır
                                    // (telefon yolu zaten doğru olanı yapıyordu).
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 16.dp, vertical = 16.dp)
                            ) {
                                AiCozumTab(
                                    aiExplanation = soru.aiExplanation,
                                    isAiLoading = uiState.isAiLoading,
                                    aiError = uiState.aiError,
                                    isApiKeyMissing = uiState.isApiKeyMissing,
                                    isQueuedForRetry = uiState.isAiQueuedForRetry,
                                    onGenerateSolution = { viewModel.requestAiSolution() },
                                    onNavigateToSettings = onNavigateToSettings,
                                    onManualSolution = { viewModel.setShowManualSolutionDialog(true) },
                                    onExternalAiShare = { viewModel.setShowExternalAiShareDialog(true, ExternalAiMode.SOLUTION) }
                                )
                                Spacer(modifier = Modifier.navigationBarsPadding().height(88.dp))
                            }

                            1 -> SoruChatContent(
                                soru = soru,
                                messages = uiState.chatMessages,
                                isSending = uiState.isChatSending,
                                errorMessage = uiState.chatError,
                                onSendMessage = { viewModel.sendChatMessage(it) },
                                onClearHistory = { viewModel.clearChatHistory() }
                            )

                            2 -> Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 16.dp, vertical = 16.dp)
                            ) {
                                BenzerSorularTab(
                                    similarQuestion = uiState.similarQuestion,
                                    isGeneratingSimilar = uiState.isGeneratingSimilar,
                                    similarError = uiState.similarQuestionError,
                                    userSelectedOption = uiState.userSelectedSimilarOption,
                                    isAnswerRevealed = uiState.isSimilarAnswerRevealed,
                                    onGenerateSimilar = { viewModel.generateSimilarQuestion() },
                                    onSelectOption = { viewModel.selectSimilarOption(it) },
                                    onRevealAnswer = { viewModel.revealSimilarAnswer() },
                                    onExternalAiShare = { viewModel.setShowExternalAiShareDialog(true, ExternalAiMode.SIMILAR_QUESTION) },
                                    onPasteManualSimilar = { viewModel.setShowManualSimilarQuestionDialog(true) },
                                    savedSimilarQuestions = uiState.savedSimilarQuestions,
                                    isSavedSimilarLoaded = uiState.isSavedSimilarLoaded,
                                    isSavingSimilar = uiState.isSavingSimilar,
                                    similarSaveMessage = uiState.similarSaveMessage,
                                    onPersistCurrent = { viewModel.persistCurrentSimilarQuestion() },
                                    onSaveToLibrary = { viewModel.saveCurrentSimilarToLibrary() },
                                    onOpenSaved = { viewModel.openSavedSimilarQuestion(it) },
                                    onDeleteSaved = { viewModel.deleteSavedSimilarQuestion(it) },
                                    onDismissSaveMessage = { viewModel.clearSimilarSaveMessage() }
                                )
                                Spacer(modifier = Modifier.navigationBarsPadding().height(48.dp))
                            }
                        }
                    }
                }
            } else {
                // TELEFON: DİKEY ADIM AKIŞI (çift panel yok)
                // Adım 1 Soru → Adım 2 AI → Adım 3 Benzer. Kısa etiketler 360dp'ye sığar.
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Adım göstergesi
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf("1 Soru", "2 AI", "3 Benzer").forEachIndexed { i, label ->
                            val aktif = selectedTabIndex == i
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (aktif) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 40.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable { selectedTabIndex = i }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = if (aktif) "● $label" else label,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = if (aktif) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                    PrimaryTabRow(
                        selectedTabIndex = selectedTabIndex,
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        Tab(
                            selected = selectedTabIndex == 0,
                            onClick = { selectedTabIndex = 0 },
                            text = { Text("Soru", fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Normal) },
                            icon = { Icon(Icons.Default.Description, contentDescription = "Soru Detayı") }
                        )
                        Tab(
                            selected = selectedTabIndex == 1,
                            onClick = { selectedTabIndex = 1 },
                            text = { Text("AI", fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Normal) },
                            icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "AI Çözümü") }
                        )
                        Tab(
                            selected = selectedTabIndex == 2,
                            onClick = { selectedTabIndex = 2 },
                            text = { Text("Benzer", fontWeight = if (selectedTabIndex == 2) FontWeight.Bold else FontWeight.Normal) },
                            icon = { Icon(Icons.Default.Psychology, contentDescription = "Benzer Soru") }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 16.dp)
                        ) {
                            when (selectedTabIndex) {
                                0 -> SoruDetayTab(
                                    soru = soru,
                                    extractedOcrText = uiState.extractedOcrText ?: soru.ocrMetni,
                                    sendImageToAi = uiState.sendImageToAi,
                                    isOcrLoading = uiState.isOcrLoading,
                                    onZoomImage = { path, title -> viewModel.openZoomImage(path, title) },
                                    onCopyImageClick = handleCopyImageToClipboard,
                                    onToggleSolved = { viewModel.toggleCozumDurumu() },
                                    onEditNoteClick = {
                                        editedNoteText = soru.note ?: ""
                                        viewModel.showEditNoteDialog(true)
                                    },
                                    onPerformOcr = { viewModel.performOcrOnQuestionImage() },
                                    onToggleSendImageToAi = { viewModel.setSendImageToAi(it) },
                                    onAdvanceLeitnerStage = { _ -> viewModel.advanceReviewStage() },
                                    onTeacherShareClick = { showTeacherShareDialog = true },
                                    audioMemoContent = {
                                        AudioMemoCard(
                                            audioPath = soru.audioPath,
                                            soruId = soru.id,
                                            audioManager = audioMemoManager,
                                            onAudioRecorded = { path -> viewModel.saveAudioMemo(path) },
                                            onAudioDeleted = { viewModel.deleteAudioMemo() }
                                        )
                                    }
                                )

                                1 -> AiCozumTab(
                                    aiExplanation = soru.aiExplanation,
                                    isAiLoading = uiState.isAiLoading,
                                    aiError = uiState.aiError,
                                    isApiKeyMissing = uiState.isApiKeyMissing,
                                    isQueuedForRetry = uiState.isAiQueuedForRetry,
                                    onGenerateSolution = { viewModel.requestAiSolution() },
                                    onNavigateToSettings = onNavigateToSettings,
                                    onManualSolution = { viewModel.setShowManualSolutionDialog(true) },
                                    onExternalAiShare = { viewModel.setShowExternalAiShareDialog(true, ExternalAiMode.SOLUTION) }
                                )

                                2 -> BenzerSorularTab(
                                    similarQuestion = uiState.similarQuestion,
                                    isGeneratingSimilar = uiState.isGeneratingSimilar,
                                    similarError = uiState.similarQuestionError,
                                    userSelectedOption = uiState.userSelectedSimilarOption,
                                    isAnswerRevealed = uiState.isSimilarAnswerRevealed,
                                    onGenerateSimilar = { viewModel.generateSimilarQuestion() },
                                    onSelectOption = { viewModel.selectSimilarOption(it) },
                                    onRevealAnswer = { viewModel.revealSimilarAnswer() },
                                    onExternalAiShare = { viewModel.setShowExternalAiShareDialog(true, ExternalAiMode.SIMILAR_QUESTION) },
                                    onPasteManualSimilar = { viewModel.setShowManualSimilarQuestionDialog(true) },
                                    savedSimilarQuestions = uiState.savedSimilarQuestions,
                                    isSavedSimilarLoaded = uiState.isSavedSimilarLoaded,
                                    isSavingSimilar = uiState.isSavingSimilar,
                                    similarSaveMessage = uiState.similarSaveMessage,
                                    onPersistCurrent = { viewModel.persistCurrentSimilarQuestion() },
                                    onSaveToLibrary = { viewModel.saveCurrentSimilarToLibrary() },
                                    onOpenSaved = { viewModel.openSavedSimilarQuestion(it) },
                                    onDeleteSaved = { viewModel.deleteSavedSimilarQuestion(it) },
                                    onDismissSaveMessage = { viewModel.clearSimilarSaveMessage() }
                                )
                            }

                            Spacer(modifier = Modifier.navigationBarsPadding().height(88.dp))
                        }

                        // Lenovo Stylus Scratchpad Karalama Katmanı (Tab 0 üzerinde, kullanıcı aktif ettiğinde çalışır)
                        if (selectedTabIndex == 0) {
                            DrawingOverlayCanvas(
                                soruId = soru.id,
                                modifier = Modifier.fillMaxSize(),
                                isDrawingActive = isDrawingModeActive,
                                onDrawingActiveChange = { active ->
                                    isDrawingModeActive = active
                                },
                                onCoachHintRequested = {
                                    selectedTabIndex = 1
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Zoom Dialog
    uiState.zoomImagePath?.let { path ->
        ZoomableImageDialog(
            imagePath = path,
            onDismissRequest = { viewModel.closeZoomImage() }
        )
    }

    // Silme Onay Dialogu
    if (uiState.showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showDeleteConfirmDialog(false) },
            title = { Text("Soruyu Sil", fontWeight = FontWeight.Bold) },
            text = { Text("Bu soruyu ve ekli tüm görselleri silmek istediğinizden emin misiniz? Bu işlem geri alınamaz.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSoru { onNavigateBack() }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Sil")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { viewModel.showDeleteConfirmDialog(false) }) {
                    Text("İptal")
                }
            }
        )
    }

    // Manuel Çözüm Dialogu
    if (uiState.showManualSolutionDialog) {
        ManualSolutionDialog(
            initialText = soru.aiExplanation,
            onDismiss = { viewModel.setShowManualSolutionDialog(false) },
            onSave = { viewModel.saveManualSolution(it) }
        )
    }

    // Harici Benzer Soru Yapıştırma Dialogu
    if (uiState.showManualSimilarQuestionDialog) {
        ManualSimilarQuestionDialog(
            onDismiss = { viewModel.setShowManualSimilarQuestionDialog(false) },
            onSaveSimilarQuestion = { viewModel.saveManualSimilarQuestion(it) }
        )
    }

    // Harici AI Paylaşım Dialogu (Hem Çözüm Hem Benzer Soru Modu Destekler)
    if (uiState.showExternalAiShareDialog) {
        ExternalAiShareDialog(
            mode = uiState.externalAiShareMode,
            dersAdi = soru.subjectId,
            topic = soru.topic,
            questionText = uiState.extractedOcrText ?: soru.ocrMetni,
            correctAnswer = soru.correctAnswer,
            mistakeType = soru.mistakeType?.displayName,
            imagePath = soru.imagePath,
            onDismiss = { viewModel.setShowExternalAiShareDialog(false) },
            onOpenPasteDialog = {
                if (uiState.externalAiShareMode == ExternalAiMode.SOLUTION) {
                    viewModel.setShowManualSolutionDialog(true)
                } else {
                    viewModel.setShowManualSimilarQuestionDialog(true)
                }
            }
        )
    }

    // Not Düzenleme Dialogu
    if (uiState.showEditNoteDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showEditNoteDialog(false) },
            title = { Text("Özel Notu Düzenle", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = editedNoteText,
                    onValueChange = { editedNoteText = it },
                    label = { Text("Notunuz") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    minLines = 3,
                    maxLines = 6
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateNote(editedNoteText)
                        viewModel.showEditNoteDialog(false)
                    }
                ) {
                    Text("Kaydet")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showEditNoteDialog(false) }) {
                    Text("İptal")
                }
            }
        )
    }

    // Hocama Sor (WhatsApp) Soru Kartı Diyaloğu
    if (showTeacherShareDialog) {
        TeacherShareDialog(
            soru = soru,
            studentAnswer = soru.studentAnswer,
            onDismiss = { showTeacherShareDialog = false },
            onSend = { note ->
                coroutineScope.launch {
                    TeacherShareHelper.shareToTeacher(
                        context = context,
                        soru = soru,
                        studentAnswer = soru.studentAnswer,
                        customNote = note
                    )
                    showTeacherShareDialog = false
                }
            }
        )
    }

    // Sokratik AI Chatbot Bottom Sheet
    if (uiState.showChatBottomSheet) {
        SoruChatBottomSheet(
            soru = soru,
            messages = uiState.chatMessages,
            isSending = uiState.isChatSending,
            errorMessage = uiState.chatError,
            onSendMessage = { viewModel.sendChatMessage(it) },
            onClearHistory = { viewModel.clearChatHistory() },
            onDismiss = { viewModel.toggleChatBottomSheet(false) }
        )
    }
}
}



