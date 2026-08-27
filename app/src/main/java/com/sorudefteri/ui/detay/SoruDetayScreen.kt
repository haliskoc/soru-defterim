package com.sorudefteri.ui.detay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.sorudefteri.ui.ortak.ZoomableImageDialog

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

    var selectedTabIndex by rememberSaveable { mutableStateOf(0) }
    var editedNoteText by rememberSaveable { mutableStateOf("") }

    val soru = uiState.soru

    LaunchedEffect(uiState.ocrMessage) {
        uiState.ocrMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearOcrMessage()
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

    Scaffold(
        topBar = {
            SoruDetayTopBar(
                isFavorite = soru.isFavorite,
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
                }
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
            // Tab Seçici (Soru Detayı, AI Çözümü, Benzer Sorular)
            PrimaryTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text("Soru Detayı", fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Normal) },
                    icon = { Icon(Icons.Default.Description, contentDescription = "Soru Detayı") }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text("AI Çözümü", fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Normal) },
                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "AI Çözümü") }
                )
                Tab(
                    selected = selectedTabIndex == 2,
                    onClick = { selectedTabIndex = 2 },
                    text = { Text("Benzer Soru", fontWeight = if (selectedTabIndex == 2) FontWeight.Bold else FontWeight.Normal) },
                    icon = { Icon(Icons.Default.Psychology, contentDescription = "Benzer Soru") }
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
                    0 -> SoruDetayTab(
                        soru = soru,
                        extractedOcrText = uiState.extractedOcrText ?: soru.ocrMetni,
                        sendImageToAi = uiState.sendImageToAi,
                        isOcrLoading = uiState.isOcrLoading,
                        onZoomImage = { path, title -> viewModel.openZoomImage(path, title) },
                        onToggleSolved = { viewModel.toggleCozumDurumu() },
                        onEditNoteClick = {
                            editedNoteText = soru.note ?: ""
                            viewModel.showEditNoteDialog(true)
                        },
                        onPerformOcr = { viewModel.performOcrOnQuestionImage() },
                        onToggleSendImageToAi = { viewModel.setSendImageToAi(it) },
                        onAdvanceLeitnerStage = { _ -> viewModel.advanceReviewStage() }
                    )

                    1 -> AiCozumTab(
                        aiExplanation = soru.aiExplanation,
                        isAiLoading = uiState.isAiLoading,
                        aiError = uiState.aiError,
                        isApiKeyMissing = uiState.isApiKeyMissing,
                        isQueuedForRetry = uiState.isAiQueuedForRetry,
                        onGenerateSolution = { viewModel.requestAiSolution() },
                        onNavigateToSettings = onNavigateToSettings,
                        onManualSolution = { viewModel.setShowManualSolutionDialog(true) }
                    )

                    2 -> BenzerSorularTab(
                        similarQuestion = uiState.similarQuestion,
                        isGeneratingSimilar = uiState.isGeneratingSimilar,
                        similarError = uiState.similarQuestionError,
                        userSelectedOption = uiState.userSelectedSimilarOption,
                        isAnswerRevealed = uiState.isSimilarAnswerRevealed,
                        onGenerateSimilar = { viewModel.generateSimilarQuestion() },
                        onSelectOption = { viewModel.selectSimilarOption(it) },
                        onRevealAnswer = { viewModel.revealSimilarAnswer() }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
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
}
