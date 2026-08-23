package com.sorudefteri.ui.ayarlar

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sorudefteri.domain.model.AiProviderType
import com.sorudefteri.domain.model.Alan
import com.sorudefteri.ui.theme.ErrorRed
import com.sorudefteri.ui.theme.SuccessGreen
import com.sorudefteri.ui.theme.WarningOrange
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AyarlarScreen(
    viewModel: AyarlarViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Şifre görünürlüğü durumu
    var apiKeyVisible by remember { mutableStateOf(false) }

    // Model dropdown açılır menü durumu
    var modelDropdownExpanded by remember { mutableStateOf(false) }

    // Özel model girişi dialog durumu
    var showCustomModelDialog by remember { mutableStateOf(false) }
    var customModelInput by remember { mutableStateOf("") }

    // Veri sıfırlama onay dialogu
    var showResetDialog by remember { mutableStateOf(false) }

    // Yedekleme Dışa Aktarma / İçe Aktarma Başlatıcıları
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            viewModel.exportBackup(uri)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importBackup(uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Ayarlar",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            // 1) YKS Alanım Bölümü
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "YKS Alanım",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = "AYT ders listesi ve soru sınıflandırması seçtiğin alana göre otomatik uyarlanır.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Alan.entries.forEach { alan ->
                            val isSelected = uiState.currentAlan == alan
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                        else Color.Transparent
                                    )
                                    .clickable { viewModel.setAlan(alan) }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { viewModel.setAlan(alan) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = alan.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = alan.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2) Yapay Zeka (AI) Çözüm Ayarları
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Yapay Zeka (AI) Çözüm",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // API Anahtarı Durum Rozeti
                        val currentApiKey = if (uiState.aiProvider == AiProviderType.GEMINI) uiState.geminiApiKey else uiState.groqApiKey
                        val hasKey = currentApiKey.isNotBlank()

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (hasKey) SuccessGreen.copy(alpha = 0.15f)
                                    else WarningOrange.copy(alpha = 0.15f)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (hasKey) "✓ Anahtar Tanımlı" else "⚠ Anahtar Eksik",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (hasKey) SuccessGreen else WarningOrange
                            )
                        }
                    }

                    Text(
                        text = "Soru detay ekranından adım adım yapay zeka çözümü almak için kendi ücretsiz API anahtarınızı girin.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // AI Provider Selector (Gemini vs Groq)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Sağlayıcı (Provider)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            val providers = AiProviderType.entries
                            providers.forEachIndexed { index, provider ->
                                SegmentedButton(
                                    selected = uiState.aiProvider == provider,
                                    onClick = { viewModel.setAiProvider(provider) },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = providers.size),
                                    colors = SegmentedButtonDefaults.colors(
                                        activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                ) {
                                    Text(
                                        text = provider.displayName,
                                        fontWeight = if (uiState.aiProvider == provider) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    // API Key Input
                    val activeProvider = uiState.aiProvider
                    val currentKey = if (activeProvider == AiProviderType.GEMINI) uiState.geminiApiKey else uiState.groqApiKey

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "${activeProvider.displayName} API Anahtarı",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        OutlinedTextField(
                            value = currentKey,
                            onValueChange = {
                                if (activeProvider == AiProviderType.GEMINI) {
                                    viewModel.setGeminiApiKey(it)
                                } else {
                                    viewModel.setGroqApiKey(it)
                                }
                            },
                            placeholder = { Text("API anahtarınızı buraya yapıştırın") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingIcon = {
                                IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                                    Icon(
                                        imageVector = if (apiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (apiKeyVisible) "Gizle" else "Göster"
                                    )
                                }
                            },
                            visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Helper Text & External Link
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(activeProvider.consoleUrl))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Ücretsiz API anahtarı almak için ${activeProvider.consoleName} sayfasını açın",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = "Dış Bağlantı",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    // Model Selector & Canlı Güncelleme
                    val currentModel = if (activeProvider == AiProviderType.GEMINI) uiState.geminiModel else uiState.groqModel
                    val dynamicModels = if (activeProvider == AiProviderType.GEMINI) uiState.fetchedGeminiModels else uiState.fetchedGroqModels
                    val standardModels = activeProvider.supportedModels
                    val combinedModels = if (dynamicModels.isNotEmpty()) {
                        val dynamicIds = dynamicModels.map { it.first }.toSet()
                        dynamicModels + standardModels.filter { it.first !in dynamicIds }
                    } else {
                        standardModels
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Model Seçimi",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            // Canlı Güncelle Butonu
                            val hasKey = if (activeProvider == AiProviderType.GEMINI) uiState.geminiApiKey.isNotBlank() else uiState.groqApiKey.isNotBlank()
                            TextButton(
                                onClick = { viewModel.fetchLiveModels(activeProvider) },
                                enabled = hasKey && !uiState.isFetchingModels,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                if (uiState.isFetchingModels) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Çekiliyor...", style = MaterialTheme.typography.labelSmall)
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Modelleri Canlı Yenile",
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Canlı Modelleri Çek", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }

                        // Canlı model çekme durumu bildirimi
                        if (uiState.modelFetchStatus != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (uiState.isModelFetchError) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f) else SuccessGreen.copy(alpha = 0.15f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = uiState.modelFetchStatus!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (uiState.isModelFetchError) MaterialTheme.colorScheme.error else SuccessGreen,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { viewModel.clearModelFetchStatus() },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Kapat",
                                            modifier = Modifier.size(14.dp),
                                            tint = if (uiState.isModelFetchError) MaterialTheme.colorScheme.error else SuccessGreen
                                        )
                                    }
                                }
                            }
                        }

                        ExposedDropdownMenuBox(
                            expanded = modelDropdownExpanded,
                            onExpandedChange = { modelDropdownExpanded = it },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val selectedModelLabel = combinedModels.firstOrNull { it.first == currentModel }?.second
                                ?: "Özel Model: $currentModel"

                            OutlinedTextField(
                                value = selectedModelLabel,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelDropdownExpanded) },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )

                            ExposedDropdownMenu(
                                expanded = modelDropdownExpanded,
                                onDismissRequest = { modelDropdownExpanded = false }
                            ) {
                                combinedModels.forEach { (modelId, modelLabel) ->
                                    DropdownMenuItem(
                                        text = { Text(modelLabel) },
                                        onClick = {
                                            if (activeProvider == AiProviderType.GEMINI) {
                                                viewModel.setGeminiModel(modelId)
                                            } else {
                                                viewModel.setGroqModel(modelId)
                                            }
                                            modelDropdownExpanded = false
                                        }
                                    )
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                                // Özel Model Girme Seçeneği
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = "✍️ Özel Model Adı Belirt...",
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    },
                                    onClick = {
                                        customModelInput = currentModel
                                        showCustomModelDialog = true
                                        modelDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 3) Görünüm (Tema)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Görünüm",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        val themeOptions = listOf("Sistem" to Icons.Default.BrightnessAuto, "Açık" to Icons.Default.LightMode, "Koyu" to Icons.Default.DarkMode)
                        themeOptions.forEachIndexed { index, (label, icon) ->
                            SegmentedButton(
                                selected = uiState.themeMode == index,
                                onClick = { viewModel.setThemeMode(index) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = themeOptions.size),
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = label)
                            }
                        }
                    }
                }
            }

            // 4) Yedekleme ve Dışa/İçe Aktarma (.sdefteri)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderZip,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "Yedekleme & Arşiv Paketi",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = ".sdefteri veya .zip formatında tam yedek",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(
                        text = "Sorularınızı, fotoğraf ve AI çözüm açıklamalarını tek bir arşiv dosyasına paketleyip cihazınıza veya Google Drive'a yedekleyebilirsiniz.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Durum Bildirim Alanı
                    if (uiState.isBackupLoading) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Text(
                                    text = uiState.backupMessage ?: "İşlem yapılıyor...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    } else if (uiState.backupMessage != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (uiState.isBackupError) ErrorRed.copy(alpha = 0.15f) else SuccessGreen.copy(alpha = 0.15f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = if (uiState.isBackupError) Icons.Default.Warning else Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (uiState.isBackupError) ErrorRed else SuccessGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = uiState.backupMessage ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = if (uiState.isBackupError) ErrorRed else SuccessGreen,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Dışa Aktar Butonu
                        Button(
                            onClick = {
                                viewModel.clearBackupStatus()
                                val defaultFilename = "soru_defteri_yedek_${System.currentTimeMillis()}.sdefteri"
                                exportLauncher.launch(defaultFilename)
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f),
                            enabled = !uiState.isBackupLoading
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Dışa Aktar", fontWeight = FontWeight.Bold)
                        }

                        // İçe Aktar Butonu
                        OutlinedButton(
                            onClick = {
                                viewModel.clearBackupStatus()
                                importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f),
                            enabled = !uiState.isBackupLoading
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("İçe Aktar", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 5) Veri Yönetimi
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = null,
                            tint = ErrorRed,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Veri Yönetimi",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = "Kaydedilen tüm soruları ve fotoğrafları cihazdan kalıcı olarak temizler.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedButton(
                        onClick = { showResetDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Tüm Soruları ve Verileri Sıfırla",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // 5) Hakkında
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Text(
                        text = "Soru Defterim",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "Sürüm 1.0.0 (YKS Hazırlık Soru Arşivi)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "Öğrenciler için sevgiyle geliştirildi ❤️",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Sıfırlama Onay Dialogu
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = ErrorRed,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Tüm Verileri Sıfırla?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text("Kaydedilmiş tüm sorular, fotoğraflar ve istatistikler kalıcı olarak silinecektir. Bu işlem geri alınamaz.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetAllData {
                            showResetDialog = false
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Tüm veriler başarıyla sıfırlandı.")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) {
                    Text("Evet, Hepsini Sil", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Vazgeç")
                }
            }
        )
    }

    // Özel Model Girişi Dialogu
    if (showCustomModelDialog) {
        AlertDialog(
            onDismissRequest = { showCustomModelDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Özel Model Belirt",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Sağlayıcının yeni çıkardığı veya kullanmak istediğiniz model kodunu girin (örn. gemini-2.5-flash, gemini-2.5-pro vb.):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = customModelInput,
                        onValueChange = { customModelInput = it },
                        label = { Text("Model Kodu / ID") },
                        placeholder = { Text("örn: gemini-2.5-flash") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = customModelInput.trim()
                        if (trimmed.isNotBlank()) {
                            if (uiState.aiProvider == AiProviderType.GEMINI) {
                                viewModel.setGeminiModel(trimmed)
                            } else {
                                viewModel.setGroqModel(trimmed)
                            }
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Model güncellendi: $trimmed")
                            }
                        }
                        showCustomModelDialog = false
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Kaydet ve Kullan", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomModelDialog = false }) {
                    Text("İptal")
                }
            }
        )
    }
}
