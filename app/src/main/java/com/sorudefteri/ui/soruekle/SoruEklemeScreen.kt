package com.sorudefteri.ui.soruekle

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sorudefteri.ui.soruekle.components.BatchCropDialog
import com.sorudefteri.ui.soruekle.components.CevapSeciciSection
import com.sorudefteri.ui.soruekle.components.CozumNotSection
import com.sorudefteri.ui.soruekle.components.GorselSeciciSection
import com.sorudefteri.ui.soruekle.components.ImageCropperDialog
import com.sorudefteri.ui.soruekle.components.KaynakBilgisiSection
import com.sorudefteri.ui.soruekle.components.OcrResultPreviewSection
import com.sorudefteri.ui.soruekle.components.SoruEklemeTopBar
import com.sorudefteri.ui.soruekle.components.SoruMetadataSection
import com.sorudefteri.ui.ortak.PhoneAdimKarti

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    val focusManager = LocalFocusManager.current
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val context = LocalContext.current

    // --- Recompose fix: launchers SoruEklemeScreen seviyesinde (GorselSeciciSection içinden taşındı) ---
    val soruGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            uri?.let {
                // Uri persist: takePersistableUriPermission veya hemen GorselDepo'ya kopyala
                try {
                    context.contentResolver.takePersistableUriPermission(
                        it,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) {
                } catch (_: Exception) {
                }
                // Hemen GorselDepo'ya kopyala (ViewModel saveUriToFile çağır)
                viewModel.setSoruImageUri(it)
            }
        }
    )

    val solutionGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            uri?.let {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        it,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) {
                } catch (_: Exception) {
                }
                viewModel.setSolutionImageUri(it)
            }
        }
    )

    LaunchedEffect(capturedImageUri) {
        if (!capturedImageUri.isNullOrBlank() && uiState.soruImagePath != capturedImageUri) {
            viewModel.setInitialImage(capturedImageUri)
        }
    }

    LaunchedEffect(editingSoruId) {
        if (editingSoruId != null && editingSoruId > 0 && uiState.editingSoruId != editingSoruId) {
            viewModel.loadSoruForEdit(editingSoruId)
        }
    }

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
                .imePadding()
                .navigationBarsPadding()
                .bringIntoViewRequester(bringIntoViewRequester)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            if (uiState.totalBatchCount > 1) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "📦 Çoklu Çekim: Soru ${uiState.currentBatchIndex} / ${uiState.totalBatchCount}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        if (uiState.pendingBatchImages.isNotEmpty()) {
                            Button(
                                onClick = { viewModel.loadNextBatchImage() },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("Sıradakine Geç", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // GorselSeciciSection artık launcher içermez; parent'ten gelen onSoruGalleryClick vs kullanır
            // Crop flow: ImageCropperDialog doğru Uri/path ile çağrılır, kırpma sonrası ViewModel'e bitmap iletilir
            // TELEFON: Adım 1 — Görsel (dikey akışın girişi)
            PhoneAdimKarti(
                adim = 1,
                baslik = "Soru Görseli",
                aciklama = "Önce sorunun fotoğrafını çek veya galeriden seç. Netlik önemli.",
                tamamlandi = !uiState.soruImagePath.isNullOrBlank()
            ) {
                GorselSeciciSection(
                soruImagePath = uiState.soruImagePath,
                solutionImagePath = uiState.solutionImagePath,
                isAutoTagging = uiState.isAutoTagging,
                isOcrExtracting = uiState.isOcrExtracting,
                onCameraClick = onNavigateToCamera,
                onSoruGalleryClick = {
                    soruGalleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onSolutionGalleryClick = {
                    solutionGalleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onSolutionDrawingSaved = { path -> viewModel.setSolutionImagePath(path) },
                onDeleteSoruImage = { viewModel.removeSoruImage() },
                onDeleteSolutionImage = { viewModel.removeSolutionImage() },
                onOpenCropper = { isSol -> viewModel.openCropper(isSol) },
                onOpenBatchCropper = { viewModel.openBatchCropper() },
                onPerformOcr = { viewModel.extractTextFromQuestionImage() },
                onAutoTagWithAi = { viewModel.autoTagWithAi() }
                )
            }

            // TELEFON: Adım 2 — Metin & OCR
            PhoneAdimKarti(
                adim = 2,
                baslik = "Soru Metni (OCR)",
                aciklama = "Görseldeki yazıyı kontrol et, gerekirse AI ile temizle.",
                tamamlandi = !uiState.extractedText.isNullOrBlank()
            ) {
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
            }

            // TELEFON: Adım 3 — Ders & Konu
            PhoneAdimKarti(
                adim = 3,
                baslik = "Ders & Konu",
                aciklama = "Sınav türünü, dersi ve konuyu işaretle.",
                tamamlandi = uiState.subjectId != null && uiState.topic.isNotBlank()
            ) {
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
            }

            // TELEFON: Adım 4 — Kaynak
            PhoneAdimKarti(
                adim = 4,
                baslik = "Kaynak Bilgisi",
                aciklama = "Yayın, deneme, yıl ve soru no (opsiyonel ama önerilir)."
            ) {
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
            }

            // TELEFON: Adım 5 — Cevaplar
            PhoneAdimKarti(
                adim = 5,
                baslik = "Cevaplar",
                aciklama = "Doğru cevabı ve kendi verdiğin cevabı işaretle.",
                tamamlandi = !uiState.correctAnswer.isNullOrBlank()
            ) {
                CevapSeciciSection(
                correctAnswer = uiState.correctAnswer,
                studentAnswer = uiState.studentAnswer,
                onCorrectAnswerSelected = { viewModel.setCorrectAnswer(it) },
                onStudentAnswerSelected = { viewModel.setStudentAnswer(it) }
            )
            }

            // TELEFON: Adım 6 — Not & Kaydet
            PhoneAdimKarti(
                adim = 6,
                baslik = "Çözüm Notu",
                aciklama = "Son adım: notunu ekle, üstteki Kaydet ile bitir."
            ) {
                CozumNotSection(
                note = uiState.note,
                onNoteChanged = { viewModel.setNote(it) }
            )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Kırpma / Döndürme Dialogu - doğru Uri/path ile çağrılır, kırpma sonrası bitmap GorselDepo.kaydetBitmap ile kaydedilir
    if (uiState.isCropperOpen) {
        val targetPath: String? = if (uiState.cropTargetIsSolution) uiState.solutionImagePath else uiState.soruImagePath
        targetPath?.let { path: String ->
            ImageCropperDialog(
                imagePath = path,
                onDismiss = { viewModel.closeCropper() },
                onCropped = { bitmap: android.graphics.Bitmap ->
                    // Kırpma sonrası uiState güncellensin: ViewModel flow düzeltildi
                    viewModel.onCropComplete(bitmap)
                }
            )
        }
    }

    if (uiState.isBatchCropperOpen) {
        uiState.soruImagePath?.let { path ->
            BatchCropDialog(
                imagePath = path,
                onDismiss = { viewModel.closeBatchCropper() },
                onBatchCropped = { croppedPaths ->
                    viewModel.applyBatchCroppedImages(croppedPaths)
                }
            )
        }
    }

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
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Ara") },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
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

                        items(unite.konular) { konuAdi: String ->
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
                                            contentDescription = "Seçili",
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
