package com.sorudefteri.ui.kutuphane

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.outlined.CollectionsBookmark
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sorudefteri.domain.model.SortOrder
import com.sorudefteri.ui.ortak.EmptyState
import com.sorudefteri.ui.ortak.FiltreBar
import com.sorudefteri.ui.ortak.SoruKarti

/**
 * Soru Kütüphanesi Ana Ekranı.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KutuphaneScreen(
    modifier: Modifier = Modifier,
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

    var showBottomSheet by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Fotoğraf Seçici (Photo Picker)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            handleAddClick(uri.toString())
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Soru Defterim",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (!uiState.isLoading && uiState.totalCount > 0) {
                            Text(
                                text = "${uiState.totalCount} Soru • ${uiState.solvedCount} Çözüldü",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showBottomSheet = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Soru Ekle", fontWeight = FontWeight.Bold) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
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
                onlyUnsolved = uiState.onlyUnsolved,
                onOnlyUnsolvedToggle = { viewModel.onOnlyUnsolvedToggle(it) },
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
                        uiState.onlyUnsolved

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
                        onButtonClick = { showBottomSheet = true }
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 88.dp // FAB arkasında kalmaması için
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
                            onClick = { handleSoruClick(soru.id) },
                            onToggleSolved = { viewModel.toggleSolved(soru) }
                        )
                    }
                }
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
