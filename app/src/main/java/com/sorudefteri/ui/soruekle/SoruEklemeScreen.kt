package com.sorudefteri.ui.soruekle

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sorudefteri.ui.soruekle.components.CevapSeciciSection
import com.sorudefteri.ui.soruekle.components.CozumNotSection
import com.sorudefteri.ui.soruekle.components.GorselSeciciSection
import com.sorudefteri.ui.soruekle.components.ImageCropperDialog
import com.sorudefteri.ui.soruekle.components.KaynakBilgisiSection
import com.sorudefteri.ui.soruekle.components.OcrResultPreviewSection
import com.sorudefteri.ui.soruekle.components.SoruEklemeTopBar
import com.sorudefteri.ui.soruekle.components.SoruMetadataSection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoruEklemeScreen(
    onNavigateBack: () -> Unit,
    onNavigateToCamera: () -> Unit,
    capturedImageUri: String? = null,
    editingSoruId: Long? = null,
    viewModel: SoruEklemeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Kamera veya Galeri'den dönen görseli ayarla
    LaunchedEffect(capturedImageUri) {
        if (!capturedImageUri.isNullOrBlank() && uiState.soruImagePath != capturedImageUri) {
            viewModel.setInitialImage(capturedImageUri)
        }
    }

    // Düzenleme modunda soru verilerini yükle
    LaunchedEffect(editingSoruId) {
        if (editingSoruId != null && editingSoruId > 0 && uiState.editingSoruId != editingSoruId) {
            viewModel.loadSoruForEdit(editingSoruId)
        }
    }

    // Hata veya başarı mesajları
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.autoTagSuccessMessage) {
        uiState.autoTagSuccessMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearAutoTagStatus()
        }
    }

    Scaffold(
        topBar = {
            SoruEklemeTopBar(
                isEditMode = uiState.isEditMode,
                canSave = uiState.canSave,
                isSaving = uiState.isSaving,
                onBackClick = onNavigateBack,
                onSaveClick = { viewModel.saveSoru(onSuccess = onNavigateBack) }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. Görsel Seçici (Soru & Çözüm Görselleri, Kırpma, OCR tetikleme)
            GorselSeciciSection(
                soruImagePath = uiState.soruImagePath,
                solutionImagePath = uiState.solutionImagePath,
                isAutoTagging = uiState.isAutoTagging,
                isOcrExtracting = uiState.isOcrExtracting,
                onCameraClick = onNavigateToCamera,
                onGallerySoruPicked = { uri -> viewModel.setSoruImageUri(uri) },
                onGallerySolutionPicked = { uri -> viewModel.setSolutionImageUri(uri) },
                onSolutionDrawingSaved = { path -> viewModel.setSolutionImagePath(path) },
                onDeleteSoruImage = { viewModel.removeSoruImage() },
                onDeleteSolutionImage = { viewModel.removeSolutionImage() },
                onOpenCropper = { isSol -> viewModel.openCropper(isSol) },
                onPerformOcr = { viewModel.extractTextFromQuestionImage() },
                onAutoTagWithAi = { viewModel.autoTagWithAi() }
            )

            // 2. OCR Metin Önizleme ve Düzenleme (Koşullu Görsel/Metin Gönderimi)
            OcrResultPreviewSection(
                extractedText = uiState.extractedText,
                sendImageToAi = uiState.sendImageToAi,
                isOcrExtracting = uiState.isOcrExtracting,
                isCleaningOcr = uiState.isCleaningOcr,
                ocrStatusMessage = uiState.ocrStatusMessage,
                onExtractedTextChanged = { viewModel.updateExtractedText(it) },
                onSendImageToAiToggle = { viewModel.setSendImageToAi(it) },
                onCleanWithAiClick = { viewModel.cleanOcrWithAi() },
                onClearOcrClick = { viewModel.updateExtractedText("") }
            )

            // 3. Soru Metadata (Sınav Tipi, Ders, Konu, Zorluk, Hata Tipi)
            SoruMetadataSection(
                examType = uiState.examType,
                subjectId = uiState.subjectId,
                topic = uiState.topic,
                difficulty = uiState.difficulty,
                mistakeType = uiState.mistakeType,
                availableSubjects = uiState.availableSubjects,
                frequentTopics = uiState.frequentTopics,
                currentSubjectName = uiState.currentSubjectName,
                totalTopicCountInCatalog = uiState.totalTopicCountInCatalog,
                onExamTypeChanged = { viewModel.setExamType(it) },
                onSubjectChanged = { viewModel.setSubject(it) },
                onTopicChanged = { viewModel.setTopic(it) },
                onDifficultyChanged = { viewModel.setDifficulty(it) },
                onMistakeTypeChanged = { viewModel.setMistakeType(it) },
                onOpenCatalogClick = { viewModel.openTopicCatalog(true) }
            )

            // 4. Kaynak Bilgisi (Yayın, Deneme, Yıl, Soru No)
            KaynakBilgisiSection(
                kaynakYayin = uiState.kaynakYayin,
                kaynakDeneme = uiState.kaynakDeneme,
                kaynakYil = uiState.kaynakYil,
                kaynakSoruNo = uiState.kaynakSoruNo,
                onYayinChanged = { viewModel.setKaynakYayin(it) },
                onDenemeChanged = { viewModel.setKaynakDeneme(it) },
                onYilChanged = { viewModel.setKaynakYil(it) },
                onSoruNoChanged = { viewModel.setKaynakSoruNo(it) }
            )

            // 5. Doğru Cevap & Öğrenci Cevap Şıkları
            CevapSeciciSection(
                correctAnswer = uiState.correctAnswer,
                studentAnswer = uiState.studentAnswer,
                onCorrectAnswerSelected = { viewModel.setCorrectAnswer(it) },
                onStudentAnswerSelected = { viewModel.setStudentAnswer(it) }
            )

            // 6. Özel Notlar & Hatırlatıcılar
            CozumNotSection(
                note = uiState.note,
                onNoteChanged = { viewModel.setNote(it) }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Kırpma / Döndürme Dialogu
    if (uiState.isCropperOpen) {
        val targetPath = if (uiState.cropTargetIsSolution) uiState.solutionImagePath else uiState.soruImagePath
        targetPath?.let { path ->
            ImageCropperDialog(
                imagePath = path,
                onDismiss = { viewModel.closeCropper() },
                onCropped = { newPath -> viewModel.applyCroppedImage(newPath) }
            )
        }
    }

    // Konu Kataloğu BottomSheet
    if (uiState.isCatalogSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.openTopicCatalog(false) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${uiState.currentSubjectName} Müfredat Konuları",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { viewModel.openTopicCatalog(false) }) {
                        Icon(Icons.Default.Close, contentDescription = "Kapat")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = uiState.catalogSearchQuery,
                    onValueChange = { viewModel.setCatalogSearchQuery(it) },
                    placeholder = { Text("Konu veya ünite ara...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                val filteredUnits = if (uiState.catalogSearchQuery.isBlank()) {
                    uiState.catalogUnits
                } else {
                    uiState.catalogUnits.mapNotNull { unite ->
                        val matched = unite.konular.filter { it.contains(uiState.catalogSearchQuery, ignoreCase = true) }
                        if (matched.isNotEmpty() || unite.uniteAdi.contains(uiState.catalogSearchQuery, ignoreCase = true)) {
                            unite.copy(konular = if (matched.isNotEmpty()) matched else unite.konular)
                        } else null
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    filteredUnits.forEach { unite ->
                        item {
                            Text(
                                text = unite.uniteAdi,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }

                        items(unite.konular) { konuAdi ->
                            val isSelected = uiState.topic == konuAdi
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.setTopic(konuAdi)
                                        viewModel.openTopicCatalog(false)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = konuAdi,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
