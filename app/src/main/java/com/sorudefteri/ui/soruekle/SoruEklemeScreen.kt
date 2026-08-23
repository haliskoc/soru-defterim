package com.sorudefteri.ui.soruekle

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.sorudefteri.domain.KonuUnite
import com.sorudefteri.domain.model.HataTipi
import com.sorudefteri.ui.ortak.CevapAnahtariSecici
import com.sorudefteri.ui.ortak.FullScreenImageViewer
import com.sorudefteri.ui.ortak.dashedBorder
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SoruEklemeScreen(
    onNavigateBack: () -> Unit,
    onOpenKamera: ((isSolution: Boolean) -> Unit)? = null,
    onCameraLaunch: ((isSolution: Boolean) -> Unit)? = null,
    viewModel: SoruEklemeViewModel = hiltViewModel(),
    initialSoruImagePath: String? = null,
    initialSolutionImagePath: String? = null
) {
    val handleCamera = onCameraLaunch ?: onOpenKamera
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()

    // Fullscreen visual viewer state
    var previewImagePath by remember { mutableStateOf<String?>(null) }
    var previewTitle by remember { mutableStateOf("") }

    // Çözüm görseli paneli açık/kapalı durumu
    var solutionSectionExpanded by remember { mutableStateOf(false) }

    // Dışarıdan gelen başlangıç görseli varsa ViewModel'e aktar
    LaunchedEffect(initialSoruImagePath) {
        if (!initialSoruImagePath.isNullOrBlank()) {
            viewModel.setInitialImage(initialSoruImagePath, isSolution = false)
        }
    }
    LaunchedEffect(initialSolutionImagePath) {
        if (!initialSolutionImagePath.isNullOrBlank()) {
            viewModel.setInitialImage(initialSolutionImagePath, isSolution = true)
        }
    }

    // Hata mesajı varsa Snackbar ile göster
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    // Photo Picker launchers (soru ve çözüm için)
    val soruGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.setSoruImageUri(it) }
    }

    val solutionGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.setSolutionImageUri(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Soru Ekle",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Arşivine yeni soru kaydet",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Geri Dön"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            // Kaydet Butonu Kartı
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (!uiState.canSave && !uiState.isSaving) {
                        val missingInfo = when {
                            uiState.soruImagePath.isNullOrBlank() -> "• Soru fotoğrafı eklenmelidir"
                            uiState.topic.isBlank() -> "• Konu seçilmeli veya yazılmalıdır"
                            else -> ""
                        }
                        if (missingInfo.isNotBlank()) {
                            Text(
                                text = "Kaydetmek için: $missingInfo",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                    }

                    Button(
                        onClick = { viewModel.saveSoru(onSuccess = onNavigateBack) },
                        enabled = uiState.canSave,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Kaydediliyor...", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Soruyu Arşive Kaydet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            // ==========================================
            // ADIM 1: Soru Fotoğrafı (Zorunlu)
            // ==========================================
            StepCard(
                stepNumber = 1,
                title = "Soru Fotoğrafı",
                isRequired = true,
                badgeText = if (uiState.soruImagePath != null) "Fotoğraf Eklendi" else null
            ) {
                if (uiState.soruImagePath == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .dashedBorder(
                                strokeWidth = 1.5.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                cornerRadius = 16.dp
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(56.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.AddAPhoto,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "Çözemediğin sorunun fotoğrafını ekle",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = {
                                        if (handleCamera != null) {
                                            handleCamera(false)
                                        } else {
                                            soruGalleryLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Fotoğraf Çek")
                                }

                                OutlinedButton(
                                    onClick = {
                                        soruGalleryLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PhotoLibrary,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Galeriden Seç")
                                }
                            }
                        }
                    }
                } else {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(240.dp)
                                    .background(Color.Black.copy(alpha = 0.05f))
                                    .clickable {
                                        previewImagePath = uiState.soruImagePath
                                        previewTitle = "Soru Fotoğrafı"
                                    }
                            ) {
                                AsyncImage(
                                    model = File(uiState.soruImagePath!!),
                                    contentDescription = "Soru Görseli",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )

                                // Üst sağ sil butonu
                                IconButton(
                                    onClick = { viewModel.removeSoruImage() },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.65f))
                                        .size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Fotoğrafı Kaldır",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        previewImagePath = uiState.soruImagePath
                                        previewTitle = "Soru Fotoğrafı"
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ZoomIn,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Büyüt", style = MaterialTheme.typography.labelMedium)
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                OutlinedButton(
                                    onClick = {
                                        if (handleCamera != null) {
                                            handleCamera(false)
                                        } else {
                                            soruGalleryLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Değiştir", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // ADIM 2: Çözüm Görseli (Opsiyonel)
            // ==========================================
            StepCard(
                stepNumber = 2,
                title = "Çözüm Görseli",
                isRequired = false,
                badgeText = if (uiState.solutionImagePath != null) "✓ Çözüm Eklendi" else null
            ) {
                if (uiState.solutionImagePath == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .dashedBorder(
                                strokeWidth = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                cornerRadius = 14.dp
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    if (handleCamera != null) {
                                        handleCamera(true)
                                    } else {
                                        solutionGalleryLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Çözüm Çek")
                            }

                            OutlinedButton(
                                onClick = {
                                    solutionGalleryLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Galeriden Seç")
                            }
                        }
                    }
                } else {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clickable {
                                        previewImagePath = uiState.solutionImagePath
                                        previewTitle = "Çözüm Fotoğrafı"
                                    }
                            ) {
                                AsyncImage(
                                    model = File(uiState.solutionImagePath!!),
                                    contentDescription = "Çözüm Görseli",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )

                                IconButton(
                                    onClick = { viewModel.removeSolutionImage() },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.65f))
                                        .size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Çözümü Kaldır",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(6.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        previewImagePath = uiState.solutionImagePath
                                        previewTitle = "Çözüm Fotoğrafı"
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(Icons.Default.ZoomIn, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Büyüt", style = MaterialTheme.typography.labelSmall)
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                OutlinedButton(
                                    onClick = {
                                        if (handleCamera != null) {
                                            handleCamera(true)
                                        } else {
                                            solutionGalleryLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Değiştir", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // ADIM 3: Sınav Türü (TYT / AYT)
            // ==========================================
            StepCard(
                stepNumber = 3,
                title = "Sınav Türü",
                isRequired = true
            ) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val examOptions = listOf("TYT", "AYT")
                    examOptions.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = uiState.examType == option,
                            onClick = { viewModel.setExamType(option) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = examOptions.size),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = MaterialTheme.colorScheme.primary,
                                activeContentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text(
                                text = if (option == "TYT") "TYT (Temel Yeterlilik)" else "AYT (Alan Yeterlilik)",
                                fontWeight = if (uiState.examType == option) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            // ==========================================
            // ADIM 4: Ders Seçimi
            // ==========================================
            StepCard(
                stepNumber = 4,
                title = "Ders Seçimi",
                isRequired = true,
                badgeText = if (uiState.examType == "AYT") uiState.userAlan.displayName else null
            ) {
                val scrollStateSubjects = rememberScrollState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollStateSubjects),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    uiState.availableSubjects.forEach { ders ->
                        val isSelected = uiState.subjectId == ders.id
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.setSubject(ders.id) },
                            label = { Text(ders.ad, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            leadingIcon = {
                                Icon(
                                    imageVector = getDersIcon(ders.id),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            // ==========================================
            // ADIM 5: Konu Seçimi (Katalog + Serbest Metin)
            // ==========================================
            StepCard(
                stepNumber = 5,
                title = "Konu Seçimi",
                isRequired = true,
                badgeText = if (uiState.topic.isNotBlank()) "Seçildi: ${uiState.topic}" else null
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Konu Giriş Metin Alanı (Serbest Metin)
                    OutlinedTextField(
                        value = uiState.topic,
                        onValueChange = { viewModel.setTopic(it) },
                        label = { Text("Konu Adı *") },
                        placeholder = { Text("Örn: Türev, Polinomlar, Paragraf...") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.MenuBook,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingIcon = {
                            if (uiState.topic.isNotBlank()) {
                                IconButton(onClick = { viewModel.setTopic("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Temizle")
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Konu Kataloğu Açma Butonu
                    Button(
                        onClick = { viewModel.openTopicCatalog(true) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${uiState.currentSubjectName} YKS Konu Kataloğunu Aç (${uiState.totalTopicCountInCatalog} Konu)",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Hızlı Konu Önerileri (En sık kullanılanlar)
                    if (uiState.frequentTopics.isNotEmpty()) {
                        Text(
                            text = "Önerilen Konular:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val scrollStateFrequent = rememberScrollState()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(scrollStateFrequent),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            uiState.frequentTopics.forEach { topicSuggestion ->
                                val isSelected = uiState.topic == topicSuggestion
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.clickable { viewModel.setTopic(topicSuggestion) }
                                ) {
                                    Text(
                                        text = topicSuggestion,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // ADIM 6: Doğru Cevap Şıkkı
            // ==========================================
            StepCard(
                stepNumber = 6,
                title = "Doğru Cevap Şıkkı",
                isRequired = false
            ) {
                CevapAnahtariSecici(
                    selectedAnswer = uiState.correctAnswer,
                    onAnswerSelected = { viewModel.setCorrectAnswer(it) }
                )
            }

            // ==========================================
            // ADIM 7: Hata Tipi (Neden Yanlış Yaptın?)
            // ==========================================
            StepCard(
                stepNumber = 7,
                title = "Hata Tipi",
                isRequired = false,
                badgeText = uiState.mistakeType?.displayName
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Bu soruyu neden yanlış yaptın veya yapamadın?",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HataTipi.entries.forEach { hata ->
                            val isSelected = uiState.mistakeType == hata
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    if (isSelected) viewModel.setMistakeType(null) else viewModel.setMistakeType(hata)
                                },
                                label = { Text(hata.displayName, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = hata.defaultColor,
                                    selectedLabelColor = Color.White
                                ),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                }
            }

            // ==========================================
            // ADIM 8: Not / Hatırlatma
            // ==========================================
            StepCard(
                stepNumber = 8,
                title = "Not / Hatırlatma",
                isRequired = false
            ) {
                OutlinedTextField(
                    value = uiState.note,
                    onValueChange = { viewModel.setNote(it) },
                    placeholder = { Text("Örn: Formülü unuttuğum için yapamadım, soru tarzı çok iyi, sınavda benzeri çıkar...") },
                    shape = RoundedCornerShape(12.dp),
                    minLines = 3,
                    maxLines = 5,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }

    // ==========================================
    // MODAL BOTTOM SHEET: YKS Konu Kataloğu
    // ==========================================
    if (uiState.isCatalogSheetOpen) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { viewModel.openTopicCatalog(false) },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            KonuKatalogBottomSheetContent(
                examType = uiState.examType,
                subjectName = uiState.currentSubjectName,
                units = uiState.catalogUnits,
                searchQuery = uiState.catalogSearchQuery,
                onSearchQueryChange = { viewModel.setCatalogSearchQuery(it) },
                selectedTopic = uiState.topic,
                onTopicSelected = { viewModel.selectTopicFromCatalog(it) },
                onDismiss = { viewModel.openTopicCatalog(false) }
            )
        }
    }

    // Tam ekran görsel önizleme dialogu
    previewImagePath?.let { path ->
        FullScreenImageViewer(
            imagePath = path,
            title = previewTitle,
            onDismiss = { previewImagePath = null }
        )
    }
}

/**
 * Adım Başlık ve İçerik Kartı Bileşeni
 */
@Composable
private fun StepCard(
    stepNumber: Int,
    title: String,
    isRequired: Boolean,
    badgeText: String? = null,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Adım Rozeti
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "$stepNumber",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }

                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (isRequired) {
                        Text(
                            text = "*",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                if (badgeText != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            content()
        }
    }
}

/**
 * Konu Kataloğu Modal Bottom Sheet İçeriği
 */
@Composable
private fun KonuKatalogBottomSheetContent(
    examType: String,
    subjectName: String,
    units: List<KonuUnite>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedTopic: String,
    onTopicSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedUnitFilter by remember { mutableStateOf<String?>(null) }

    val cleanQuery = searchQuery.lowercase(Locale("tr", "TR")).trim()

    // Filtrelenmiş üniteler ve konular
    val filteredUnits = remember(units, cleanQuery, selectedUnitFilter) {
        units.mapNotNull { unite ->
            if (selectedUnitFilter != null && unite.uniteAdi != selectedUnitFilter) {
                return@mapNotNull null
            }

            if (cleanQuery.isBlank()) {
                unite
            } else {
                val unitMatches = unite.uniteAdi.lowercase(Locale("tr", "TR")).contains(cleanQuery)
                val matchingTopics = unite.konular.filter { konu ->
                    konu.lowercase(Locale("tr", "TR")).contains(cleanQuery)
                }

                if (unitMatches) {
                    unite
                } else if (matchingTopics.isNotEmpty()) {
                    unite.copy(konular = matchingTopics)
                } else {
                    null
                }
            }
        }
    }

    val totalMatchingTopics = filteredUnits.sumOf { it.konular.size }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f)
            .padding(horizontal = 20.dp)
    ) {
        // Başlık ve Kapat Butonu
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "$subjectName Konu Kataloğu",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$examType • $totalMatchingTopics Konu Listeleniyor",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Kapat")
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Arama Çubuğu
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Konu veya ünite adı ara...") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Temizle")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Ünite Filtre Çipleri (Kategoriler)
        if (units.size > 1) {
            val scrollStateUnits = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollStateUnits),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedUnitFilter == null,
                    onClick = { selectedUnitFilter = null },
                    label = { Text("Tümü (${units.sumOf { it.konular.size }})") },
                    shape = RoundedCornerShape(8.dp)
                )

                units.forEach { unite ->
                    FilterChip(
                        selected = selectedUnitFilter == unite.uniteAdi,
                        onClick = {
                            selectedUnitFilter = if (selectedUnitFilter == unite.uniteAdi) null else unite.uniteAdi
                        },
                        label = { Text("${unite.uniteAdi} (${unite.konular.size})") },
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }

        HorizontalDivider()

        // Konu Listesi
        if (filteredUnits.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Katalogda eşleşen konu bulunamadı",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (searchQuery.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { onTopicSelected(searchQuery.trim()) },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Özel Konu Olarak Ekle: \"$searchQuery\"")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Eğer arama metni yazıldıysa ve listede yoksa serbest metin olarak ekleme seçeneği
                if (searchQuery.isNotBlank()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onTopicSelected(searchQuery.trim()) }
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Yazdığınız konuyu kullan: \"$searchQuery\"",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                items(filteredUnits, key = { it.uniteAdi }) { unite ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        // Ünite Başlığı
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = unite.uniteAdi,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "${unite.konular.size} konu",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Konular
                        unite.konular.forEach { konu ->
                            val isSelected = selectedTopic == konu
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onTopicSelected(konu) }
                                    .padding(vertical = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = konu,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )

                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
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

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Ders ID'sine göre uygun Compose Material ikonu döner
 */
private fun getDersIcon(subjectId: String): ImageVector {
    val clean = subjectId.lowercase()
    return when {
        clean.contains("matematik") || clean.contains("geometri") -> Icons.Default.Calculate
        clean.contains("turkce") || clean.contains("edebiyat") -> Icons.Default.AutoStories
        clean.contains("fizik") -> Icons.Default.Science
        clean.contains("kimya") -> Icons.Default.Science
        clean.contains("biyoloji") -> Icons.Default.Biotech
        clean.contains("tarih") -> Icons.Default.HistoryEdu
        clean.contains("cografya") -> Icons.Default.Public
        clean.contains("felsefe") -> Icons.Default.Psychology
        clean.contains("din") -> Icons.Default.MenuBook
        clean.contains("ydt") || clean.contains("ingilizce") || clean.contains("almanca") || clean.contains("fransizca") -> Icons.Default.Translate
        else -> Icons.Default.MenuBook
    }
}

