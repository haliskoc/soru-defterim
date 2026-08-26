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
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.sorudefteri.domain.DersTaksonomisi
import com.sorudefteri.domain.model.SortOrder
import com.sorudefteri.domain.model.Soru
import com.sorudefteri.ui.ortak.EmptyState
import com.sorudefteri.ui.ortak.FiltreBar
import com.sorudefteri.ui.ortak.SoruKarti
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
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var showBottomSheet by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    var selectedTabletSoruId by remember { mutableStateOf<Long?>(null) }
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Soru Defterim",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            if (!uiState.isLoading && uiState.totalCount > 0) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                ) {
                                    Text(
                                        text = "${uiState.totalCount} Soru • ${uiState.solvedCount} Çözüldü",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    },
                    actions = {
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
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        bottomBar = {
            if (uiState.isSelectionMode && uiState.selectedSoruIds.isNotEmpty()) {
                Surface(
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { viewModel.batchMarkSolvedSelected(true) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Çözüldü")
                        }
                        Button(
                            onClick = { viewModel.batchStarSelected(true) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300))
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Yıldızla", color = Color.Black)
                        }
                        Button(
                            onClick = { showBatchDeleteDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sil")
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (!uiState.isSelectionMode) {
                ExtendedFloatingActionButton(
                    onClick = { showBottomSheet = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
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
                        viewModel = viewModel,
                        onSoruSelected = { id ->
                            if (uiState.isSelectionMode) {
                                viewModel.toggleSelectSoru(id)
                            } else {
                                selectedTabletSoruId = id
                            }
                        },
                        onOpenBottomSheet = { showBottomSheet = true },
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
            // PHONE STANDART SINGLE-COLUMN LAYOUT
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                QuestionListHeaderAndGrid(
                    uiState = uiState,
                    viewModel = viewModel,
                    onSoruSelected = { id ->
                        if (uiState.isSelectionMode) {
                            viewModel.toggleSelectSoru(id)
                        } else {
                            handleSoruClick(id)
                        }
                    },
                    onOpenBottomSheet = { showBottomSheet = true },
                    minGridSize = 180.dp
                )
            }
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
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "Yeni Soru Ekle",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Sorunun fotoğrafını nasıl eklemek istersin?",
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
                                Text("Fotoğraf Çek", fontWeight = FontWeight.Bold)
                            },
                            supportingContent = {
                                Text("Kamera ile sorunun net fotoğrafını çek")
                            },
                            leadingContent = {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.CameraAlt,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(24.dp)
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
                                Text("Galeriden Seç", fontWeight = FontWeight.Bold)
                            },
                            supportingContent = {
                                Text("Cihazındaki mevcut bir görseli yükle")
                            },
                            leadingContent = {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.PhotoLibrary,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSecondary,
                                            modifier = Modifier.size(24.dp)
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
                                Text("Form ile Ekle", fontWeight = FontWeight.Bold)
                            },
                            supportingContent = {
                                Text("Görseli daha sonra eklemek üzere devam et")
                            },
                            leadingContent = {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.EditNote,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onTertiary,
                                            modifier = Modifier.size(24.dp)
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
    }
}

@Composable
private fun QuestionListHeaderAndGrid(
    uiState: KutuphaneUiState,
    viewModel: KutuphaneViewModel,
    onSoruSelected: (Long) -> Unit,
    onOpenBottomSheet: () -> Unit,
    minGridSize: androidx.compose.ui.unit.Dp
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Arama Çubuğu
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = { viewModel.onSearchQueryChange(it) },
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
                if (uiState.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Aramayı Temizle"
                        )
                    }
                }
            },
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
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
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.filteredSorular.isEmpty()) {
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
                    bottom = if (uiState.isSelectionMode) 88.dp else 88.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = uiState.filteredSorular,
                    key = { it.id }
                ) { soru ->
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
