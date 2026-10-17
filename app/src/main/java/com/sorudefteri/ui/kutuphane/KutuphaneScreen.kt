package com.sorudefteri.ui.kutuphane

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.paging.compose.LazyPagingItems
import androidx.paging.LoadState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import com.sorudefteri.data.export.AnkiExportHelper
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.sorudefteri.data.pdf.PdfDenemeKitapcikGenerator
import com.sorudefteri.ui.kutuphane.components.PdfExportDialog
import com.sorudefteri.domain.DersTaksonomisi
import com.sorudefteri.domain.model.SortOrder
import com.sorudefteri.domain.model.Soru
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.sorudefteri.ui.ortak.DailyDueRescueCard
import com.sorudefteri.ui.ortak.EmptyState
import com.sorudefteri.ui.ortak.FiltreBar
import com.sorudefteri.ui.ortak.PhoneAramaCubugu
import com.sorudefteri.ui.ortak.PhoneFiltreDetaySayfasi
import com.sorudefteri.ui.ortak.PhoneHizliFiltreler
import com.sorudefteri.ui.ortak.PhoneSecimAltBar
import com.sorudefteri.ui.ortak.PhoneSoruListeKarti
import com.sorudefteri.ui.ortak.SoruKarti
import com.sorudefteri.ui.ortak.StreakBadge
import com.sorudefteri.ui.theme.AytBadgeColor
import com.sorudefteri.ui.theme.AytBadgeContainer
import com.sorudefteri.ui.theme.MistakeBadgeColor
import com.sorudefteri.ui.theme.MistakeBadgeContainer
import com.sorudefteri.ui.theme.SolvedGreen
import com.sorudefteri.ui.theme.SolvedGreenContainer
import com.sorudefteri.ui.theme.TytBadgeColor
import com.sorudefteri.ui.theme.TytBadgeContainer
import com.sorudefteri.ui.theme.UnsolvedOrange
import com.sorudefteri.ui.theme.UnsolvedOrangeContainer
import java.io.File

/**
 * Soru Kütüphanesi Ana Ekranı.
 * Telefonlarda tek sütun, Tablet ve Geniş Ekranlarda 2 sütunlu (List-Detail Pane) görünümü destekler.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KutuphaneScreen(
    modifier: Modifier = Modifier,
    isExpandedScreen: Boolean = false,
    onNavigateToSoruDetay: (Long) -> Unit = {},
    onNavigateToKamera: () -> Unit = {},
    onNavigateToSoruEkle: (imageUri: String?) -> Unit = {},
    onSoruClick: ((Long) -> Unit)? = null,
    onCameraClick: (() -> Unit)? = null,
    onAddClick: (() -> Unit)? = null,
    onStartDueTest: () -> Unit = {},
    viewModel: KutuphaneViewModel = hiltViewModel()
) {
    val handleSoruClick = onSoruClick ?: onNavigateToSoruDetay
    val handleCameraClick = onCameraClick ?: onNavigateToKamera
    val handleAddClick: (String?) -> Unit = { uri ->
        if (uri != null) {
            onNavigateToSoruEkle(uri)
        } else {
            onAddClick?.invoke() ?: onNavigateToSoruEkle(null)
        }
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pagedSorular = viewModel.pagedSorular.collectAsLazyPagingItems()
    val currentStreak by viewModel.currentStreak.collectAsStateWithLifecycle()
    val streakShieldCount by viewModel.streakShieldCount.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var showBottomSheet by rememberSaveable { mutableStateOf(false) }
    var sortMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var overflowMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var showBatchDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showPdfExportDialog by rememberSaveable { mutableStateOf(false) }
    var questionsForPdfExport by remember { mutableStateOf<List<Soru>>(emptyList()) }
    var selectedTabletSoruId by rememberSaveable { mutableStateOf<Long?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val deleteSoruWithUndo: (Soru) -> Unit = { soru ->
        viewModel.deleteSoru(soru)
        coroutineScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Soru silindi",
                actionLabel = "Geri Al",
                duration = androidx.compose.material3.SnackbarDuration.Short
            )
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                viewModel.undoDelete()
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // Fotoğraf Seçici (Photo Picker)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            handleAddClick(uri.toString())
        }
    }

    if (showBatchDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteDialog = false },
            title = { Text("Seçili Soruları Sil") },
            text = { Text("Seçtiğiniz ${uiState.selectedSoruIds.size} soruyu silmek istediğinizden emin misiniz? Bu işlem geri alınamaz.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.batchDeleteSelected()
                        showBatchDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Evet, Sil")
                }
            },
            dismissButton = {
                Button(
                    onClick = { showBatchDeleteDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text("İptal", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (uiState.isSelectionMode) {
                TopAppBar(
                    title = {
                        Text(
                            text = "${uiState.selectedSoruIds.size} Soru Seçildi",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Seçimi Kapat"
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            if (uiState.isAllSelected) viewModel.clearSelection() else viewModel.selectAll()
                        }) {
                            Icon(
                                imageVector = Icons.Default.SelectAll,
                                contentDescription = if (uiState.isAllSelected) "Seçimi Kaldır" else "Tümünü Seç",
                                tint = if (uiState.isAllSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        // TELEFON DOSTU KOMPAKT BAŞLIK: dikey iki satır, taşma yok.
                        // Tablet'te de aynı kalır (küçük düzeltme: hap yerine alt satır sayaç).
                        Column {
                            Text(
                                text = "Kütüphane",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1
                            )
                            if (!uiState.isLoading && uiState.totalCount > 0) {
                                Text(
                                    text = "${uiState.totalCount} soru • ${uiState.solvedCount} çözüldü" +
                                        if (uiState.dueReviewCount > 0) " • ${uiState.dueReviewCount} tekrar" else "",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1
                                )
                            }
                        }
                    },
                    actions = {
                        StreakBadge(
                            streakCount = currentStreak,
                            shieldCount = streakShieldCount
                        )
                        // Sıralama Menüsü
                        Box {
                            IconButton(onClick = { sortMenuExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Default.Sort,
                                    contentDescription = "Sıralama Seçenekleri"
                                )
                            }

                            DropdownMenu(
                                expanded = sortMenuExpanded,
                                onDismissRequest = { sortMenuExpanded = false }
                            ) {
                                SortOrder.entries.forEach { order ->
                                    val isSelected = uiState.sortOrder == order
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = order.displayName,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            viewModel.onSortOrderChange(order)
                                            sortMenuExpanded = false
                                        },
                                        leadingIcon = {
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        // Faz 5.3: Daha Fazla Seçenek Menüsü (Anki Deste Dışa Aktar)
                        Box {
                            IconButton(onClick = { overflowMenuExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Daha Fazla Seçenek"
                                )
                            }

                            DropdownMenu(
                                expanded = overflowMenuExpanded,
                                onDismissRequest = { overflowMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("📄 Filtrelenen Soruları PDF Yap (${uiState.filteredSorular.size})") },
                                    onClick = {
                                        overflowMenuExpanded = false
                                        if (uiState.filteredSorular.isEmpty()) {
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("Dışa aktarılacak soru bulunamadı.")
                                            }
                                        } else {
                                            questionsForPdfExport = uiState.filteredSorular
                                            showPdfExportDialog = true
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("📇 Anki Deste Dışa Aktar (.csv)") },
                                    onClick = {
                                        overflowMenuExpanded = false
                                        coroutineScope.launch {
                                            val questionsToExport = uiState.filteredSorular
                                            if (questionsToExport.isEmpty()) {
                                                snackbarHostState.showSnackbar("Dışa aktarılacak soru bulunamadı.")
                                                return@launch
                                            }
                                            try {
                                                val ankiFile = withContext(Dispatchers.IO) {
                                                    // DÜZELTME: exportQuestionsToAnki (düz CSV)
                                                    // <img src="soru_X.jpg"> referansı yazıyor
                                                    // ama görselleri paketin içine koymuyordu;
                                                    // Anki'ye aktarınca tüm kartlarda kırık
                                                    // görsel çıkıyordu. Paket (ZIP) sürümü
                                                    // görselleri de gömüyor.
                                                    AnkiExportHelper.exportQuestionsToAnkiPackage(context, questionsToExport)
                                                }
                                                AnkiExportHelper.shareExportedDeck(context, ankiFile)
                                                snackbarHostState.showSnackbar("Anki destesi hazırlandı (${questionsToExport.size} soru)")
                                            } catch (e: Exception) {
                                                snackbarHostState.showSnackbar("Anki destesi oluşturulamadı: ${e.localizedMessage ?: "Bilinmeyen hata"}")
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        bottomBar = {
            if (uiState.isSelectionMode && uiState.selectedSoruIds.isNotEmpty()) {
                // TELEFON: taşmayan 2 satırlı seçim çubuğu (tablet de kullanır — küçük düzeltme)
                PhoneSecimAltBar(
                    seciliSayi = uiState.selectedSoruIds.size,
                    onCozuldu = { viewModel.batchMarkSolvedSelected(true) },
                    onYildizla = { viewModel.batchStarSelected(true) },
                    onPdf = {
                        val selectedQuestions = uiState.filteredSorular.filter { it.id in uiState.selectedSoruIds }
                        if (selectedQuestions.isEmpty()) {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Lütfen en az bir soru seçin.")
                            }
                        } else {
                            questionsForPdfExport = selectedQuestions
                            showPdfExportDialog = true
                        }
                    },
                    onAnki = {
                        coroutineScope.launch {
                            val selectedQuestions = uiState.filteredSorular.filter { it.id in uiState.selectedSoruIds }
                            if (selectedQuestions.isEmpty()) {
                                snackbarHostState.showSnackbar("Lütfen en az bir soru seçin.")
                                return@launch
                            }
                            try {
                                val ankiFile = withContext(Dispatchers.IO) {
                                    AnkiExportHelper.exportQuestionsToAnkiPackage(context, selectedQuestions)
                                }
                                AnkiExportHelper.shareExportedDeck(context, ankiFile)
                                snackbarHostState.showSnackbar("Anki destesi hazırlandı (${selectedQuestions.size} soru)")
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar("Anki destesi oluşturulamadı: ${e.localizedMessage ?: "Bilinmeyen hata"}")
                            }
                        }
                    },
                    onSil = { showBatchDeleteDialog = true }
                )
            }
        },
        floatingActionButton = {
            if (!uiState.isSelectionMode) {
                ExtendedFloatingActionButton(
                    onClick = { showBottomSheet = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = "Soru Ekle") },
                    text = { Text("Soru Ekle", fontWeight = FontWeight.Bold) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }
    ) { innerPadding ->
        if (isExpandedScreen) {
            // TABLET RESPONSIVE 2-COLUMN (List-Detail Pane) LAYOUT
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Sol Sütun: Arama, Filtre ve Soru Listesi
                Column(
                    modifier = Modifier
                        .weight(1.1f)
                        .fillMaxHeight()
                ) {
                    QuestionListHeaderAndGrid(
                        uiState = uiState,
                        pagedSorular = pagedSorular,
                        viewModel = viewModel,
                        onSoruSelected = { id ->
                            if (uiState.isSelectionMode) {
                                viewModel.toggleSelectSoru(id)
                            } else {
                                selectedTabletSoruId = id
                            }
                        },
                        onOpenBottomSheet = { showBottomSheet = true },
                        onStartDueTest = onStartDueTest,
                        minGridSize = 140.dp
                    )
                }

                // Dikey Ayırıcı Çizgi
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                )

                // Sağ Sütun: Seçili Soru Detay Önizleme Paneli
                val activeSoru = uiState.filteredSorular.find { it.id == selectedTabletSoruId }
                    ?: uiState.filteredSorular.firstOrNull()

                Box(
                    modifier = Modifier
                        .weight(1.3f)
                        .fillMaxHeight()
                        .padding(16.dp)
                ) {
                    if (activeSoru != null) {
                        TabletDetailPane(
                            soru = activeSoru,
                            onOpenFullDetail = { handleSoruClick(activeSoru.id) },
                            onToggleSolved = { viewModel.toggleSolved(activeSoru) },
                            onToggleFavorite = { viewModel.toggleFavorite(activeSoru) }
                        )
                    } else {
                        EmptyState(
                            title = "Soru Önizleme Paneli",
                            description = "Detaylarını incelemek, çözümünü görmek veya AI analizi yapmak için sol listeden bir soru seçin.",
                            icon = Icons.Outlined.TouchApp
                        )
                    }
                }
            }
        } else {
            // TELEFON: DİKEY TEK SÜTUN, ADIM ADIM DETAYLI AKIŞ
            // Adım 1: Ara → Adım 2: Hızlı filtre + detay sayfası → Adım 3: Dikey liste
            // Tablet ızgarası (SoruKarti + Adaptive grid) aynen korunur, buraya dokunulmaz.
            PhoneKutuphaneIcerik(
                uiState = uiState,
                pagedSorular = pagedSorular,
                viewModel = viewModel,
                onSoruSelected = { id ->
                    if (uiState.isSelectionMode) {
                        viewModel.toggleSelectSoru(id)
                    } else {
                        handleSoruClick(id)
                    }
                },
                onOpenBottomSheet = { showBottomSheet = true },
                onStartDueTest = onStartDueTest
            )
        }

        // Soru Ekleme Bottom Sheet'i
        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                sheetState = sheetState,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "Adım Adım Soru Ekle",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "1) Fotoğrafı seç → 2) Ders & konuyu işaretle → 3) Kaydet. Önce yöntemi seç:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Seçenek 1: Fotoğraf Çek (CameraX)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                showBottomSheet = false
                                handleCameraClick()
                            },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ) {
                        ListItem(
                            headlineContent = {
                                Text("Adım 1 — Fotoğraf Çek", fontWeight = FontWeight.Bold)
                            },
                            supportingContent = {
                                Text("Kamera ile sorunun net fotoğrafını çek")
                            },
                            leadingContent = {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(52.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.CameraAlt,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Seçenek 2: Galeriden Seç (Photo Picker)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                showBottomSheet = false
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    ) {
                        ListItem(
                            headlineContent = {
                                Text("Adım 2 — Galeriden Seç", fontWeight = FontWeight.Bold)
                            },
                            supportingContent = {
                                Text("Cihazındaki mevcut bir görseli yükle")
                            },
                            leadingContent = {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(52.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.PhotoLibrary,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSecondary,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Seçenek 3: Manuel / Form ile Ekle
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                showBottomSheet = false
                                handleAddClick(null)
                            },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        ListItem(
                            headlineContent = {
                                Text("Adım 3 — Form ile Ekle", fontWeight = FontWeight.Bold)
                            },
                            supportingContent = {
                                Text("Görseli daha sonra eklemek üzere devam et")
                            },
                            leadingContent = {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(52.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.EditNote,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onTertiary,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }

        // Çok Detaylı PDF Çıktı Sihirbazı
        if (showPdfExportDialog && questionsForPdfExport.isNotEmpty()) {
            PdfExportDialog(
                sorular = questionsForPdfExport,
                onDismiss = { showPdfExportDialog = false },
                onSuccess = {
                    showPdfExportDialog = false
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("PDF belgesi başarıyla oluşturuldu (${questionsForPdfExport.size} soru)")
                    }
                }
            )
        }
    }
}

@Composable
private fun PhoneKutuphaneIcerik(
    uiState: KutuphaneUiState,
    pagedSorular: LazyPagingItems<Soru>,
    viewModel: KutuphaneViewModel,
    onSoruSelected: (Long) -> Unit,
    onOpenBottomSheet: () -> Unit,
    onStartDueTest: () -> Unit = {}
) {
    val focusManager = LocalFocusManager.current
    var localSearchText by rememberSaveable { mutableStateOf(uiState.searchQuery) }
    var showFiltreSayfasi by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(uiState.searchQuery) {
        if (localSearchText != uiState.searchQuery) localSearchText = uiState.searchQuery
    }

    val aktifFiltreSayisi =
        (if (uiState.selectedExamType != null) 1 else 0) +
            (if (uiState.selectedSubjectId != null) 1 else 0) +
            (if (uiState.selectedMistakeType != null) 1 else 0) +
            (if (uiState.selectedPublisher != null) 1 else 0) +
            (if (uiState.selectedExamName != null) 1 else 0) +
            (if (uiState.onlyUnsolved) 1 else 0) +
            (if (uiState.onlyFavorites) 1 else 0) +
            (if (uiState.onlyDueReview) 1 else 0)

    Column(modifier = Modifier.fillMaxSize()) {
        // Adım 1 — Ara (nefes alan, 56dp, sonuç rozetli)
        PhoneAramaCubugu(
            value = localSearchText,
            onValueChange = {
                localSearchText = it
                viewModel.onSearchQueryChange(it)
            },
            sonucSayisi = pagedSorular.itemCount.takeIf { it > 0 },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // Adım 2 — Hızlı filtreler (tek bakışta) + detay sayfası
        PhoneHizliFiltreler(
            selectedExamType = uiState.selectedExamType,
            onExamTypeSelected = { viewModel.onExamTypeSelected(it) },
            onlyUnsolved = uiState.onlyUnsolved,
            onOnlyUnsolvedToggle = { viewModel.onOnlyUnsolvedToggle(it) },
            onlyFavorites = uiState.onlyFavorites,
            onOnlyFavoritesToggle = { viewModel.onOnlyFavoritesToggle(it) },
            onlyDueReview = uiState.onlyDueReview,
            onOnlyDueReviewToggle = { viewModel.onOnlyDueReviewToggle(it) },
            aktifFiltreSayisi = aktifFiltreSayisi,
            onTumFiltrelerClick = { showFiltreSayfasi = true },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        if (showFiltreSayfasi) {
            PhoneFiltreDetaySayfasi(
                onDismiss = { showFiltreSayfasi = false },
                selectedExamType = uiState.selectedExamType,
                onExamTypeSelected = { viewModel.onExamTypeSelected(it) },
                selectedSubjectId = uiState.selectedSubjectId,
                availableSubjects = uiState.availableSubjects,
                onSubjectSelected = { viewModel.onSubjectSelected(it) },
                selectedMistakeType = uiState.selectedMistakeType,
                onMistakeTypeSelected = { viewModel.onMistakeTypeSelected(it) },
                selectedPublisher = uiState.selectedPublisher,
                availablePublishers = uiState.availablePublishers,
                onPublisherSelected = { viewModel.onPublisherSelected(it) },
                selectedExamName = uiState.selectedExamName,
                availableExamNames = uiState.availableExamNames,
                onExamNameSelected = { viewModel.onExamNameSelected(it) },
                onlyUnsolved = uiState.onlyUnsolved,
                onOnlyUnsolvedToggle = { viewModel.onOnlyUnsolvedToggle(it) },
                onlyFavorites = uiState.onlyFavorites,
                onOnlyFavoritesToggle = { viewModel.onOnlyFavoritesToggle(it) },
                onlyDueReview = uiState.onlyDueReview,
                onOnlyDueReviewToggle = { viewModel.onOnlyDueReviewToggle(it) },
                onResetFilters = {
                    viewModel.resetFilters()
                    showFiltreSayfasi = false
                }
            )
        }

        // Adım 3 — Dikey liste (LazyColumn, yatay kompakt kartlar)
        val isInitialLoading = pagedSorular.loadState.refresh is LoadState.Loading && pagedSorular.itemCount == 0
        if (uiState.isLoading || isInitialLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text(
                        text = "Sorular yükleniyor...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (pagedSorular.itemCount == 0) {
            val isFiltering = uiState.searchQuery.isNotBlank() ||
                uiState.selectedExamType != null ||
                uiState.selectedSubjectId != null ||
                uiState.selectedMistakeType != null ||
                uiState.onlyUnsolved ||
                uiState.onlyFavorites ||
                uiState.onlyDueReview
            if (isFiltering) {
                EmptyState(
                    title = "Eşleşen Soru Bulunamadı",
                    description = "Adım 2'deki filtreleri genişletmeyi veya aramayı sadeleştirmeyi dene.",
                    icon = Icons.Default.SearchOff,
                    buttonText = "Filtreleri Temizle",
                    buttonIcon = Icons.Default.Clear,
                    onButtonClick = { viewModel.resetFilters() }
                )
            } else {
                EmptyState(
                    title = "Henüz Soru Eklenmedi",
                    description = "Adım 1: Aşağıdaki butonla ilk sorunun fotoğrafını çek, Adım 2'de dersini seç, Adım 3'te listede gör.",
                    icon = Icons.Outlined.CollectionsBookmark,
                    buttonText = "İlk Sorunu Ekle",
                    buttonIcon = Icons.Default.Add,
                    onButtonClick = onOpenBottomSheet
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (uiState.dueReviewCount > 0 && !uiState.isSelectionMode) {
                    item {
                        DailyDueRescueCard(
                            dueCount = uiState.dueReviewCount,
                            onStartClick = { viewModel.startDueReviewTest(onStartDueTest) },
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                }
                // TELEFON DİKEY LİSTE: paging verisini index anahtarlı tekil item'larla dizer
                // (LazyListScope.items(count) yerine for+item: derleyici çıkarım sorunu yok)
                for (index in 0 until pagedSorular.itemCount) {
                    item(key = pagedSorular[index]?.id ?: index) {
                        val soru = pagedSorular[index]
                        if (soru != null) {
                            PhoneSoruListeKarti(
                                soru = soru,
                                onClick = { onSoruSelected(soru.id) },
                                onLongClick = { viewModel.toggleSelectSoru(soru.id) },
                                isSelected = uiState.selectedSoruIds.contains(soru.id),
                                isSelectionMode = uiState.isSelectionMode,
                                onToggleSolved = { viewModel.toggleSolved(soru) },
                                onToggleFavorite = { viewModel.toggleFavorite(soru) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionListHeaderAndGrid(
    uiState: KutuphaneUiState,
    pagedSorular: LazyPagingItems<Soru>,
    viewModel: KutuphaneViewModel,
    onSoruSelected: (Long) -> Unit,
    onOpenBottomSheet: () -> Unit,
    onStartDueTest: () -> Unit = {},
    minGridSize: androidx.compose.ui.unit.Dp
) {
    val focusManager = LocalFocusManager.current
    var localSearchText by rememberSaveable { mutableStateOf(uiState.searchQuery) }

    // Dış kaynaklı (ör. resetFilters) arama sıfırlamalarını yerel durumla senkronize et
    LaunchedEffect(uiState.searchQuery) {
        if (localSearchText != uiState.searchQuery) {
            localSearchText = uiState.searchQuery
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Arama Çubuğu (Anında tepki veren reaktif yapı)
        OutlinedTextField(
            value = localSearchText,
            onValueChange = { newText ->
                localSearchText = newText
                viewModel.onSearchQueryChange(newText)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            placeholder = {
                Text(
                    text = "Konu, ders veya not içinde ara...",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Ara",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                if (localSearchText.isNotEmpty()) {
                    IconButton(onClick = {
                        localSearchText = ""
                        viewModel.onSearchQueryChange("")
                    }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Aramayı Temizle"
                        )
                    }
                }
            },
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            )
        )

        // Filtre Çubuğu
        FiltreBar(
            selectedExamType = uiState.selectedExamType,
            onExamTypeSelected = { viewModel.onExamTypeSelected(it) },
            selectedSubjectId = uiState.selectedSubjectId,
            availableSubjects = uiState.availableSubjects,
            onSubjectSelected = { viewModel.onSubjectSelected(it) },
            selectedMistakeType = uiState.selectedMistakeType,
            onMistakeTypeSelected = { viewModel.onMistakeTypeSelected(it) },
            selectedPublisher = uiState.selectedPublisher,
            availablePublishers = uiState.availablePublishers,
            onPublisherSelected = { viewModel.onPublisherSelected(it) },
            selectedExamName = uiState.selectedExamName,
            availableExamNames = uiState.availableExamNames,
            onExamNameSelected = { viewModel.onExamNameSelected(it) },
            onlyUnsolved = uiState.onlyUnsolved,
            onOnlyUnsolvedToggle = { viewModel.onOnlyUnsolvedToggle(it) },
            onlyFavorites = uiState.onlyFavorites,
            onOnlyFavoritesToggle = { viewModel.onOnlyFavoritesToggle(it) },
            onlyDueReview = uiState.onlyDueReview,
            onOnlyDueReviewToggle = { viewModel.onOnlyDueReviewToggle(it) },
            onResetFilters = { viewModel.resetFilters() }
        )

        // İçerik Alanı: Yükleniyor / Boş / Izgara
        val isInitialLoading = pagedSorular.loadState.refresh is LoadState.Loading && pagedSorular.itemCount == 0
        if (uiState.isLoading || isInitialLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (pagedSorular.itemCount == 0) {
            val isFiltering = uiState.searchQuery.isNotBlank() ||
                    uiState.selectedExamType != null ||
                    uiState.selectedSubjectId != null ||
                    uiState.selectedMistakeType != null ||
                    uiState.onlyUnsolved ||
                    uiState.onlyFavorites ||
                    uiState.onlyDueReview

            if (isFiltering) {
                EmptyState(
                    title = "Eşleşen Soru Bulunamadı",
                    description = "Arama kriterlerinize uygun soru yok. Filtreleri temizlemeyi deneyin.",
                    icon = Icons.Default.SearchOff,
                    buttonText = "Filtreleri Temizle",
                    buttonIcon = Icons.Default.Clear,
                    onButtonClick = { viewModel.resetFilters() }
                )
            } else {
                EmptyState(
                    title = "Henüz Soru Eklenmedi",
                    description = "Çözemediğin veya yanlış yaptığın soruların fotoğrafını çekerek arşivlemeye başla!",
                    icon = Icons.Outlined.CollectionsBookmark,
                    buttonText = "İlk Sorunu Ekle",
                    buttonIcon = Icons.Default.Add,
                    onButtonClick = onOpenBottomSheet
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = minGridSize),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = if (uiState.isSelectionMode) 96.dp else 88.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (uiState.dueReviewCount > 0 && !uiState.isSelectionMode) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        DailyDueRescueCard(
                            dueCount = uiState.dueReviewCount,
                            onStartClick = {
                                viewModel.startDueReviewTest(onStartDueTest)
                            },
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }
                items(
                    count = pagedSorular.itemCount,
                    key = pagedSorular.itemKey { it.id }
                ) { index ->
                    val soru = pagedSorular[index]
                    if (soru != null) {
                        SoruKarti(
                            soru = soru,
                            onClick = { onSoruSelected(soru.id) },
                            onLongClick = {
                                viewModel.toggleSelectSoru(soru.id)
                            },
                            isSelected = uiState.selectedSoruIds.contains(soru.id),
                            isSelectionMode = uiState.isSelectionMode,
                            onToggleSolved = { viewModel.toggleSolved(soru) },
                            onToggleFavorite = { viewModel.toggleFavorite(soru) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabletDetailPane(
    soru: Soru,
    onOpenFullDetail: () -> Unit,
    onToggleSolved: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val scrollState = rememberScrollState()
    val dersAdi = DersTaksonomisi.getDersAdi(soru.examType, soru.subjectId)

    Card(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Üst Başlık & Badges & Yıldız Butonu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (soru.examType == "TYT") TytBadgeContainer else AytBadgeContainer
                        ) {
                            Text(
                                text = soru.examType,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (soru.examType == "TYT") TytBadgeColor else AytBadgeColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }

                        Text(
                            text = dersAdi,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = soru.topic,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (soru.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Yıldızla",
                            tint = if (soru.isFavorite) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = onToggleSolved) {
                        Icon(
                            imageVector = if (soru.isSolved) Icons.Default.Check else Icons.Default.RadioButtonUnchecked,
                            contentDescription = "Çözüldü",
                            tint = if (soru.isSolved) SolvedGreen else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Hata Tipi ve Çözüm Durumu Rozetleri
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (soru.mistakeType != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MistakeBadgeContainer
                    ) {
                        Text(
                            text = "⚠ ${soru.mistakeType.displayName}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MistakeBadgeColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (soru.isSolved) SolvedGreenContainer else UnsolvedOrangeContainer
                ) {
                    Text(
                        text = if (soru.isSolved) "✓ Çözüldü" else "○ Çözülmedi",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (soru.isSolved) SolvedGreen else UnsolvedOrange,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                if (soru.correctAnswer != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = "Doğru Cevap: ${soru.correctAnswer}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Soru Görseli
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                val imageFile = File(soru.imagePath)
                AsyncImage(
                    model = if (imageFile.exists()) imageFile else soru.imagePath,
                    contentDescription = "Soru Görseli",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
            }

            // Çözüm Görseli (Varsa)
            if (!soru.solutionImagePath.isNullOrBlank()) {
                Text(
                    text = "Eklenen Çözüm Görseli",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val solFile = File(soru.solutionImagePath)
                    AsyncImage(
                        model = if (solFile.exists()) solFile else soru.solutionImagePath,
                        contentDescription = "Çözüm Görseli",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                }
            }

            // Notlar
            if (!soru.note.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Notun:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = soru.note,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Alt Eylemler
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onOpenFullDetail,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tam Ekranda Aç & AI Çözüm", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
