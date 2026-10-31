package com.sorudefteri.ui.ayarlar.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sorudefteri.data.ai.AiPromptManager
import com.sorudefteri.data.ai.AiPromptType
import com.sorudefteri.domain.model.AiProviderType
import com.sorudefteri.domain.model.AiTaskRoute
import com.sorudefteri.domain.model.AiTaskType
import com.sorudefteri.ui.theme.SolvedGreen
import com.sorudefteri.ui.theme.SolvedGreenContainer
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiPromptAyarlarTab(
    aiProvider: AiProviderType,
    geminiApiKey: String,
    groqApiKey: String,
    nvidiaApiKey: String = "",
    geminiModel: String,
    groqModel: String,
    nvidiaModel: String = AiProviderType.NVIDIA.defaultModel,
    isFetchingModels: Boolean,
    fetchedGeminiModels: List<Pair<String, String>>,
    fetchedGroqModels: List<Pair<String, String>>,
    fetchedNvidiaModels: List<Pair<String, String>> = emptyList(),
    modelFetchStatus: String?,
    isModelFetchError: Boolean,
    onAiProviderSelected: (AiProviderType) -> Unit,
    onGeminiApiKeyChanged: (String) -> Unit,
    onGroqApiKeyChanged: (String) -> Unit,
    onNvidiaApiKeyChanged: (String) -> Unit = {},
    onGeminiModelSelected: (String) -> Unit,
    onGroqModelSelected: (String) -> Unit,
    onNvidiaModelSelected: (String) -> Unit = {},
    onFetchLiveModelsClick: (AiProviderType) -> Unit,
    autoSolveOnSave: Boolean = false,
    batchSolveDelaySeconds: Int = 3,
    taskRoutes: Map<AiTaskType, AiTaskRoute?> = emptyMap(),
    onAutoSolveOnSaveChanged: (Boolean) -> Unit = {},
    onBatchSolveDelayChanged: (Int) -> Unit = {},
    onTaskRouteChanged: (AiTaskType, AiTaskRoute?) -> Unit = { _, _ -> },
    customPrompts: Map<AiPromptType, String> = emptyMap(),
    onSavePrompt: (AiPromptType, String) -> Unit = { _, _ -> },
    onResetPrompt: (AiPromptType) -> Unit = {},
    onResetAllPrompts: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current

    // API anahtarı görünürlük durumları
    var isGeminiKeyVisible by rememberSaveable { mutableStateOf(false) }
    var isGroqKeyVisible by rememberSaveable { mutableStateOf(false) }
    var isNvidiaKeyVisible by rememberSaveable { mutableStateOf(false) }

    // Sağlayıcı kartlarının açılır/kapanır durumları
    var isGeminiExpanded by rememberSaveable { mutableStateOf(true) }
    var isGroqExpanded by rememberSaveable { mutableStateOf(true) }
    var isNvidiaExpanded by rememberSaveable { mutableStateOf(true) }

    // Model seçim dropdown durumları (Sağlayıcı genel modelleri)
    var isGeminiDropdownExpanded by rememberSaveable { mutableStateOf(false) }
    var isGroqDropdownExpanded by rememberSaveable { mutableStateOf(false) }
    var isNvidiaDropdownExpanded by rememberSaveable { mutableStateOf(false) }

    // Görev bazlı yönlendirme (Task Routing) açık olan dropdown
    var expandedTaskDropdown by rememberSaveable { mutableStateOf<AiTaskType?>(null) }

    // Canlı model çekilen sağlayıcı takibi
    var lastFetchedProvider by rememberSaveable { mutableStateOf<AiProviderType?>(null) }

    // Prompt akordeon durumu
    var expandedPromptType by rememberSaveable { mutableStateOf<AiPromptType?>(null) }

    // Model listeleri
    val availableGeminiModels = if (fetchedGeminiModels.isNotEmpty()) fetchedGeminiModels else AiProviderType.GEMINI.supportedModels
    val availableGroqModels = if (fetchedGroqModels.isNotEmpty()) fetchedGroqModels else AiProviderType.GROQ.supportedModels
    val availableNvidiaModels = if (fetchedNvidiaModels.isNotEmpty()) fetchedNvidiaModels else AiProviderType.NVIDIA.supportedModels

    val hasGeminiKey = geminiApiKey.isNotBlank()
    val hasGroqKey = groqApiKey.isNotBlank()
    val hasNvidiaKey = nvidiaApiKey.isNotBlank()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // =====================================================================
        // BÖLÜM 1: ⚡ AI OTOMASYONLARI KARTI (Auto-Solve & Toplu Gecikme)
        // =====================================================================
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Bolt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "⚡ AI Otomasyonları & Arka Plan",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Otomatik arka plan çözümü ve API kota yönetim ayarları",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // 1. Soru Eklerken Otomatik Çöz (Switch)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Soru Eklerken Otomatik Çöz",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (autoSolveOnSave) SolvedGreenContainer else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = if (autoSolveOnSave) "Aktif" else "Kapalı",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (autoSolveOnSave) SolvedGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Soruyu kaydettiğiniz anda arka planda otomatik AI çözümü üretilir. İnternet kesilirse tekrar dener.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = autoSolveOnSave,
                        onCheckedChange = onAutoSolveOnSaveChanged
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // 2. Toplu Çözüm Gecikmesi (Slider: 1 - 10 saniye)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Toplu Çözüm Gecikmesi (Kota Koruyucu)",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Toplu çözümde sağlayıcı API kota aşımını (429 Rate Limit) önler.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = "$batchSolveDelaySeconds sn",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Slider(
                        value = batchSolveDelaySeconds.toFloat(),
                        onValueChange = { onBatchSolveDelayChanged(it.roundToInt().coerceIn(1, 10)) },
                        valueRange = 1f..10f,
                        steps = 8,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "1 sn (Hızlı)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Varsayılan: 3 sn",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "10 sn (Güvenli)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // =====================================================================
        // BÖLÜM 2: 🎯 İŞLEM BAZLI MODEL SEÇİMİ (TASK ROUTING)
        // =====================================================================
        TaskRoutingCard(
            taskRoutes = taskRoutes,
            primaryProvider = aiProvider,
            hasGeminiKey = hasGeminiKey,
            hasGroqKey = hasGroqKey,
            hasNvidiaKey = hasNvidiaKey,
            availableGeminiModels = availableGeminiModels,
            availableGroqModels = availableGroqModels,
            availableNvidiaModels = availableNvidiaModels,
            expandedTaskDropdown = expandedTaskDropdown,
            onTaskDropdownExpandedChange = { expandedTaskDropdown = it },
            onTaskRouteChanged = onTaskRouteChanged
        )

        // =====================================================================
        // BÖLÜM 3: 🔑 AI SAĞLAYICILARI VE API ANAHTARLARI (KOMPAKT & SADE KARTLAR)
        // =====================================================================
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Key,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "AI Sağlayıcıları & API Anahtarları",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Anahtarları yapılandırın ve genel Birincil Sağlayıcıyı belirleyin",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Birincil Sağlayıcı Seçici
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    AiProviderType.entries.forEachIndexed { index, provider ->
                        SegmentedButton(
                            selected = aiProvider == provider,
                            onClick = { onAiProviderSelected(provider) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = AiProviderType.entries.size)
                        ) {
                            Text(
                                text = when (provider) {
                                    AiProviderType.GEMINI -> "Gemini"
                                    AiProviderType.GROQ -> "Groq"
                                    AiProviderType.NVIDIA -> "NVIDIA"
                                },
                                fontWeight = if (aiProvider == provider) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                // Otomatik Failover Akış Bilgisi
                SmartAiRouterFlowCard(
                    primaryProvider = aiProvider,
                    hasGeminiKey = hasGeminiKey,
                    hasGroqKey = hasGroqKey,
                    hasNvidiaKey = hasNvidiaKey
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Hızlı API Anahtarı Alma Kısayolları (Doğrudan Konsol Sayfaları)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "🌐 Doğrudan API Anahtarı Alma Sayfaları (Tek Tıkla Aç):",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                try { uriHandler.openUri(AiProviderType.GEMINI.consoleUrl) } catch (_: Exception) {}
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("AI Studio", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = {
                                try { uriHandler.openUri(AiProviderType.GROQ.consoleUrl) } catch (_: Exception) {}
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Groq", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = {
                                try { uriHandler.openUri(AiProviderType.NVIDIA.consoleUrl) } catch (_: Exception) {}
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("NVIDIA", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
            }
        }

        // 1. Google Gemini Kartı
        AiProviderCard(
            provider = AiProviderType.GEMINI,
            cardTitle = "Google Gemini",
            cardSubtitle = "Multimodal Hızlı & Güçlü Görsel Çözüm",
            icon = Icons.Default.AutoAwesome,
            isPrimary = aiProvider == AiProviderType.GEMINI,
            apiKey = geminiApiKey,
            onApiKeyChange = onGeminiApiKeyChanged,
            isKeyVisible = isGeminiKeyVisible,
            onToggleKeyVisibility = { isGeminiKeyVisible = !isGeminiKeyVisible },
            hintText = "Ücretsiz anahtar: aistudio.google.com/app/apikey",
            consoleUrl = AiProviderType.GEMINI.consoleUrl,
            selectedModel = geminiModel,
            availableModels = availableGeminiModels,
            onModelSelected = onGeminiModelSelected,
            isModelDropdownExpanded = isGeminiDropdownExpanded,
            onModelDropdownExpandedChange = { isGeminiDropdownExpanded = it },
            isExpanded = isGeminiExpanded,
            onToggleExpand = { isGeminiExpanded = !isGeminiExpanded },
            isFetchingModels = isFetchingModels && (lastFetchedProvider == AiProviderType.GEMINI || (lastFetchedProvider == null && aiProvider == AiProviderType.GEMINI)),
            onFetchLiveModelsClick = {
                lastFetchedProvider = AiProviderType.GEMINI
                onFetchLiveModelsClick(AiProviderType.GEMINI)
            },
            modelFetchStatus = if (lastFetchedProvider == AiProviderType.GEMINI || (lastFetchedProvider == null && aiProvider == AiProviderType.GEMINI)) modelFetchStatus else null,
            isModelFetchError = isModelFetchError,
            onSetAsPrimaryClick = { onAiProviderSelected(AiProviderType.GEMINI) }
        )

        // 2. Groq Cloud Kartı
        AiProviderCard(
            provider = AiProviderType.GROQ,
            cardTitle = "Groq (LPU Cloud)",
            cardSubtitle = "Ultra Yüksek Hızlı LPU Çıkarım Motoru",
            icon = Icons.Default.Bolt,
            isPrimary = aiProvider == AiProviderType.GROQ,
            apiKey = groqApiKey,
            onApiKeyChange = onGroqApiKeyChanged,
            isKeyVisible = isGroqKeyVisible,
            onToggleKeyVisibility = { isGroqKeyVisible = !isGroqKeyVisible },
            hintText = "Ücretsiz yüksek hızlı anahtar: console.groq.com/keys",
            consoleUrl = AiProviderType.GROQ.consoleUrl,
            selectedModel = groqModel,
            availableModels = availableGroqModels,
            onModelSelected = onGroqModelSelected,
            isModelDropdownExpanded = isGroqDropdownExpanded,
            onModelDropdownExpandedChange = { isGroqDropdownExpanded = it },
            isExpanded = isGroqExpanded,
            onToggleExpand = { isGroqExpanded = !isGroqExpanded },
            isFetchingModels = isFetchingModels && (lastFetchedProvider == AiProviderType.GROQ || (lastFetchedProvider == null && aiProvider == AiProviderType.GROQ)),
            onFetchLiveModelsClick = {
                lastFetchedProvider = AiProviderType.GROQ
                onFetchLiveModelsClick(AiProviderType.GROQ)
            },
            modelFetchStatus = if (lastFetchedProvider == AiProviderType.GROQ || (lastFetchedProvider == null && aiProvider == AiProviderType.GROQ)) modelFetchStatus else null,
            isModelFetchError = isModelFetchError,
            onSetAsPrimaryClick = { onAiProviderSelected(AiProviderType.GROQ) }
        )

        // 3. NVIDIA Build (NIM) Kartı
        AiProviderCard(
            provider = AiProviderType.NVIDIA,
            cardTitle = "NVIDIA Build (NIM)",
            cardSubtitle = "Llama 3.2 90B Vision & Güçlü Akıl Yürütme",
            icon = Icons.Default.Tune,
            isPrimary = aiProvider == AiProviderType.NVIDIA,
            apiKey = nvidiaApiKey,
            onApiKeyChange = onNvidiaApiKeyChanged,
            isKeyVisible = isNvidiaKeyVisible,
            onToggleKeyVisibility = { isNvidiaKeyVisible = !isNvidiaKeyVisible },
            hintText = "Ücretsiz 1000 kredi: build.nvidia.com",
            consoleUrl = AiProviderType.NVIDIA.consoleUrl,
            selectedModel = nvidiaModel,
            availableModels = availableNvidiaModels,
            onModelSelected = onNvidiaModelSelected,
            isModelDropdownExpanded = isNvidiaDropdownExpanded,
            onModelDropdownExpandedChange = { isNvidiaDropdownExpanded = it },
            isExpanded = isNvidiaExpanded,
            onToggleExpand = { isNvidiaExpanded = !isNvidiaExpanded },
            isFetchingModels = isFetchingModels && (lastFetchedProvider == AiProviderType.NVIDIA || (lastFetchedProvider == null && aiProvider == AiProviderType.NVIDIA)),
            onFetchLiveModelsClick = {
                lastFetchedProvider = AiProviderType.NVIDIA
                onFetchLiveModelsClick(AiProviderType.NVIDIA)
            },
            modelFetchStatus = if (lastFetchedProvider == AiProviderType.NVIDIA || (lastFetchedProvider == null && aiProvider == AiProviderType.NVIDIA)) modelFetchStatus else null,
            isModelFetchError = isModelFetchError,
            onSetAsPrimaryClick = { onAiProviderSelected(AiProviderType.NVIDIA) }
        )

        // =====================================================================
        // BÖLÜM 4: 🛡️ GÜVENLİK ROZETİ VE 📝 SİSTEM İSTEMLERİ (SYSTEM PROMPTS)
        // =====================================================================
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "P0-1 Donanım Destekli Güvenlik",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Tüm API anahtarları Android Keystore & EncryptedSharedPreferences ile AES-256 GCM şifreli saklanır. Anahtarlar yalnızca cihazınızda tutulur ve hiçbir harici sunucuya iletilmez.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Sistem İstemleri Düzenleyicisi
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
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "System Prompt Yönetimi",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = onResetAllPrompts) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Tümünü Sıfırla")
                    }
                }
                Text(
                    text = "Yapay zekanın soru üretimi, çözüm adımları ve OCR düzeltme davranışını yöneten sistem promptlarını özelleştirebilirsiniz. Boş bırakırsanız varsayılan kullanılır. 8000 karakter limiti.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                AiPromptType.entries.forEach { promptType ->
                    PromptAccordionItem(
                        promptType = promptType,
                        customPrompt = customPrompts[promptType] ?: "",
                        isExpanded = expandedPromptType == promptType,
                        onToggleExpand = {
                            expandedPromptType = if (expandedPromptType == promptType) null else promptType
                        },
                        onSave = { text -> onSavePrompt(promptType, text) },
                        onReset = { onResetPrompt(promptType) }
                    )
                }
            }
        }
    }
}

/**
 * BÖLÜM 2: 🎯 Görev Bazlı Model Yönlendirmesi Kartı.
 * Kullanıcı her işlem (Çözüm, Sohbet, Etiketleme, Benzer Soru, OCR) için
 * o an bağlı olan tüm API anahtarlarından istediği modeli seçebilir.
 */
@Composable
private fun TaskRoutingCard(
    taskRoutes: Map<AiTaskType, AiTaskRoute?>,
    primaryProvider: AiProviderType,
    hasGeminiKey: Boolean,
    hasGroqKey: Boolean,
    hasNvidiaKey: Boolean,
    availableGeminiModels: List<Pair<String, String>>,
    availableGroqModels: List<Pair<String, String>>,
    availableNvidiaModels: List<Pair<String, String>>,
    expandedTaskDropdown: AiTaskType?,
    onTaskDropdownExpandedChange: (AiTaskType?) -> Unit,
    onTaskRouteChanged: (AiTaskType, AiTaskRoute?) -> Unit,
    modifier: Modifier = Modifier
) {
    val anyKeyConfigured = hasGeminiKey || hasGroqKey || hasNvidiaKey

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Başlık
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Filled.AltRoute,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "🎯 İşlem Bazlı Model Seçimi (Task Routing)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Her işlem için bağlı API anahtarlarınızdan dilediğiniz modeli atayın",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Bilgi Rozeti
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Atama yapılmayan görevler otomatik olarak genel Birincil Sağlayıcıyı (${primaryProvider.displayName}) kullanır.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!anyKeyConfigured) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Henüz hiçbir AI API anahtarı girilmedi. Aşağıdaki sağlayıcı kartlarından en az bir API anahtarı ekleyin.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // 5 Görev Türü İçin Seçiciler
            AiTaskType.entries.forEach { task ->
                TaskRouteItem(
                    task = task,
                    currentRoute = taskRoutes[task],
                    primaryProvider = primaryProvider,
                    hasGeminiKey = hasGeminiKey,
                    hasGroqKey = hasGroqKey,
                    hasNvidiaKey = hasNvidiaKey,
                    availableGeminiModels = availableGeminiModels,
                    availableGroqModels = availableGroqModels,
                    availableNvidiaModels = availableNvidiaModels,
                    isDropdownExpanded = expandedTaskDropdown == task,
                    onDropdownExpandedChange = { expanded ->
                        onTaskDropdownExpandedChange(if (expanded) task else null)
                    },
                    onRouteSelected = { route ->
                        onTaskRouteChanged(task, route)
                    }
                )
            }
        }
    }
}

/**
 * Tek bir AI görevine özel model seçici bileşeni.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskRouteItem(
    task: AiTaskType,
    currentRoute: AiTaskRoute?,
    primaryProvider: AiProviderType,
    hasGeminiKey: Boolean,
    hasGroqKey: Boolean,
    hasNvidiaKey: Boolean,
    availableGeminiModels: List<Pair<String, String>>,
    availableGroqModels: List<Pair<String, String>>,
    availableNvidiaModels: List<Pair<String, String>>,
    isDropdownExpanded: Boolean,
    onDropdownExpandedChange: (Boolean) -> Unit,
    onRouteSelected: (AiTaskRoute?) -> Unit,
    modifier: Modifier = Modifier
) {
    val taskIcon = when (task) {
        AiTaskType.COZUM -> Icons.Default.School
        AiTaskType.CHAT -> Icons.Default.Psychology
        AiTaskType.ETIKETLEME -> Icons.AutoMirrored.Filled.Label
        AiTaskType.BENZER_SORU -> Icons.Default.AutoAwesome
        AiTaskType.OCR_TEMIZLEME -> Icons.Default.AutoFixHigh
    }

    // Seçili model adı gösterimi
    val displayText = if (currentRoute == null) {
        "★ Varsayılan (${primaryProvider.displayName})"
    } else {
        val models = when (currentRoute.provider) {
            AiProviderType.GEMINI -> availableGeminiModels
            AiProviderType.GROQ -> availableGroqModels
            AiProviderType.NVIDIA -> availableNvidiaModels
        }
        val label = models.find { it.first == currentRoute.model }?.second ?: currentRoute.model
        "${currentRoute.provider.displayName}: $label"
    }

    // Seçili sağlayıcının API key durumu
    val isAssignedProviderKeyMissing = if (currentRoute != null) {
        when (currentRoute.provider) {
            AiProviderType.GEMINI -> !hasGeminiKey
            AiProviderType.GROQ -> !hasGroqKey
            AiProviderType.NVIDIA -> !hasNvidiaKey
        }
    } else false

    OutlinedCard(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (currentRoute != null) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.08f)
            else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (currentRoute != null) 1.5.dp else 1.dp,
            color = if (currentRoute != null) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Görev Başlığı ve İkonu
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = taskIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = task.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (task.requiresVision) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = "👁️ Görsel Analiz",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Text(
                text = task.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Model Seçim Dropdown'ı
            ExposedDropdownMenuBox(
                expanded = isDropdownExpanded,
                onExpandedChange = onDropdownExpandedChange,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = displayText,
                    onValueChange = {},
                    readOnly = true,
                    leadingIcon = {
                        Icon(
                            imageVector = if (currentRoute == null) Icons.Default.Star else Icons.Default.Tune,
                            contentDescription = null,
                            tint = if (currentRoute == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDropdownExpanded)
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )

                ExposedDropdownMenu(
                    expanded = isDropdownExpanded,
                    onDismissRequest = { onDropdownExpandedChange(false) }
                ) {
                    // 1. Seçenek: Varsayılan (Birincil Sağlayıcı)
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = "★ Varsayılan (Birincil Sağlayıcıyı Kullan)",
                                    fontWeight = if (currentRoute == null) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (currentRoute == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Genel birincil sağlayıcı (${primaryProvider.displayName}) ve failover zincirini kullanır",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        leadingIcon = if (currentRoute == null) {
                            { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                        } else null,
                        onClick = {
                            onRouteSelected(null)
                            onDropdownExpandedChange(false)
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // 2. Sağlayıcı Modelleri (Anahtarı olanlar aktif, olmayanlar devre dışı)
                    AiProviderType.entries.forEach { provider ->
                        val hasKey = when (provider) {
                            AiProviderType.GEMINI -> hasGeminiKey
                            AiProviderType.GROQ -> hasGroqKey
                            AiProviderType.NVIDIA -> hasNvidiaKey
                        }
                        val models = when (provider) {
                            AiProviderType.GEMINI -> availableGeminiModels
                            AiProviderType.GROQ -> availableGroqModels
                            AiProviderType.NVIDIA -> availableNvidiaModels
                        }

                        if (hasKey) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "── ${provider.displayName} ──",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                )
                            }

                            models.forEach { (modelId, modelName) ->
                                val isSelected = currentRoute?.provider == provider && currentRoute.model == modelId
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "${provider.displayName}: $modelName",
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    leadingIcon = if (isSelected) {
                                        { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                    } else null,
                                    onClick = {
                                        onRouteSelected(AiTaskRoute(provider, modelId))
                                        onDropdownExpandedChange(false)
                                    }
                                )
                            }
                        } else {
                            // Anahtarı olmayan sağlayıcı için devre dışı öğe
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = "${provider.displayName} (Anahtar Tanımlanmadı)",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                        )
                                        Text(
                                            text = "Aşağıdaki sağlayıcı kartından API anahtarı ekleyin",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                        )
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                                },
                                enabled = false,
                                onClick = {}
                            )
                        }
                    }
                }
            }

            // Seçilen sağlayıcının anahtarı yoksa kullanıcıya uyarı
            if (isAssignedProviderKeyMissing) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${currentRoute?.provider?.displayName} için anahtar girilmedi. Genel birincil sağlayıcıya dönülecek.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

/**
 * SmartAiRouter failover sırasını ve durumunu gösteren bilgi kartı.
 */
@Composable
private fun SmartAiRouterFlowCard(
    primaryProvider: AiProviderType,
    hasGeminiKey: Boolean,
    hasGroqKey: Boolean,
    hasNvidiaKey: Boolean,
    modifier: Modifier = Modifier
) {
    val chain = when (primaryProvider) {
        AiProviderType.GEMINI -> listOf(
            Triple(AiProviderType.GEMINI, "Birincil", hasGeminiKey),
            Triple(AiProviderType.GROQ, "Yedek 1", hasGroqKey),
            Triple(AiProviderType.NVIDIA, "Yedek 2", hasNvidiaKey)
        )
        AiProviderType.GROQ -> listOf(
            Triple(AiProviderType.GROQ, "Birincil", hasGroqKey),
            Triple(AiProviderType.NVIDIA, "Yedek 1", hasNvidiaKey),
            Triple(AiProviderType.GEMINI, "Yedek 2", hasGeminiKey)
        )
        AiProviderType.NVIDIA -> listOf(
            Triple(AiProviderType.NVIDIA, "Birincil", hasNvidiaKey),
            Triple(AiProviderType.GROQ, "Yedek 1", hasGroqKey),
            Triple(AiProviderType.GEMINI, "Yedek 2", hasGeminiKey)
        )
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SmartAiRouter Otomatik Failover Akışı",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "Failover Zinciri",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // [★ Gemini (Birincil)] ➔ [Groq (Yedek 1)] ➔ [NVIDIA NIM (Yedek 2)]
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                chain.forEachIndexed { index, (provider, role, hasKey) ->
                    val isPrimary = index == 0
                    val shortName = when (provider) {
                        AiProviderType.GEMINI -> "Gemini"
                        AiProviderType.GROQ -> "Groq"
                        AiProviderType.NVIDIA -> "NVIDIA NIM"
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isPrimary) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else if (hasKey) {
                            MaterialTheme.colorScheme.surface
                        } else {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                        },
                        border = BorderStroke(
                            width = if (isPrimary) 1.5.dp else 1.dp,
                            color = if (isPrimary) {
                                MaterialTheme.colorScheme.primary
                            } else if (hasKey) {
                                SolvedGreen.copy(alpha = 0.5f)
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            }
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                if (isPrimary) {
                                    Icon(
                                        Icons.Default.Star,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                }
                                Text(
                                    text = shortName,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (isPrimary) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "($role)",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = if (isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (hasKey) SolvedGreenContainer else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = if (hasKey) "✓ Hazır" else "Eksik",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = if (hasKey) SolvedGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    if (index < chain.size - 1) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier
                                .padding(horizontal = 2.dp)
                                .size(14.dp)
                        )
                    }
                }
            }

            // Açıklama
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(14.dp)
                        .padding(top = 1.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "SmartAiRouter: Birincil sağlayıcıda kota dolduğunda (429) veya sunucu hatasında otomatik olarak tanımlı yedek sağlayıcılara aktarılır.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Tek bir AI sağlayıcısının API anahtarı, modeli ve canlı modellerini yöneten kompakt & sade kart.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiProviderCard(
    provider: AiProviderType,
    cardTitle: String,
    cardSubtitle: String,
    icon: ImageVector,
    isPrimary: Boolean,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    isKeyVisible: Boolean,
    onToggleKeyVisibility: () -> Unit,
    hintText: String,
    consoleUrl: String,
    selectedModel: String,
    availableModels: List<Pair<String, String>>,
    onModelSelected: (String) -> Unit,
    isModelDropdownExpanded: Boolean,
    onModelDropdownExpandedChange: (Boolean) -> Unit,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    isFetchingModels: Boolean,
    onFetchLiveModelsClick: () -> Unit,
    modelFetchStatus: String?,
    isModelFetchError: Boolean,
    onSetAsPrimaryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val hasKey = apiKey.isNotBlank()

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (isPrimary) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        },
        elevation = CardDefaults.cardElevation(defaultElevation = if (isPrimary) 3.dp else 1.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Başlık Satırı (İkon, Ad, Birincil Rozeti, Tanımlı Rozeti, Birincil Yap Butonu, Genişlet Butonu)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (isPrimary) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = cardTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (isPrimary) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Star,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "★ Birincil",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = cardSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Anahtar Durumu Rozeti
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (hasKey) SolvedGreenContainer else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (hasKey) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (hasKey) SolvedGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (hasKey) "✓ Tanımlı" else "⚠️ Eksik",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (hasKey) SolvedGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onToggleExpand,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Daralt" else "Genişlet"
                        )
                    }
                }
            }

            // Birincil değilse hızlı "Birincil Yap" butonu
            if (!isPrimary) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    FilledTonalButton(
                        onClick = onSetAsPrimaryClick,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Birincil Sağlayıcı Yap",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Genişletilebilir İçerik
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // API Key Giriş Alanı
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = onApiKeyChange,
                        label = { Text("${provider.displayName} API Key") },
                        placeholder = { Text("API anahtarınızı yapıştırın...") },
                        leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = onToggleKeyVisibility) {
                                Icon(
                                    imageVector = if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (isKeyVisible) "Gizle" else "Göster"
                                )
                            }
                        },
                        visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Ücretsiz API Key Alma Butonu (Doğrudan Konsol Sayfasına Yönlendirir)
                    FilledTonalButton(
                        onClick = {
                            try {
                                uriHandler.openUri(consoleUrl)
                            } catch (_: Exception) {}
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "🔑 ${provider.displayName} API Key Sayfasını Aç (Ücretsiz)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        text = "🌐 $hintText",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Model Seçimi Dropdown
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Varsayılan Model",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        ExposedDropdownMenuBox(
                            expanded = isModelDropdownExpanded,
                            onExpandedChange = onModelDropdownExpandedChange,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = availableModels.find { it.first == selectedModel }?.second ?: selectedModel,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = isModelDropdownExpanded)
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            )

                            ExposedDropdownMenu(
                                expanded = isModelDropdownExpanded,
                                onDismissRequest = { onModelDropdownExpandedChange(false) }
                            ) {
                                availableModels.forEach { (modelId, modelName) ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = modelName,
                                                fontWeight = if (modelId == selectedModel) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            onModelSelected(modelId)
                                            onModelDropdownExpandedChange(false)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Canlı Modelleri Çek Butonu
                    FilledTonalButton(
                        onClick = onFetchLiveModelsClick,
                        enabled = !isFetchingModels && hasKey,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isFetchingModels) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Modeller Çekiliyor...")
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Güncel Canlı Modelleri Çek")
                        }
                    }

                    // Canlı Model Çekme Durum Mesajı
                    modelFetchStatus?.let { status ->
                        Text(
                            text = status,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isModelFetchError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PromptAccordionItem(
    promptType: AiPromptType,
    customPrompt: String,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onSave: (String) -> Unit,
    onReset: () -> Unit
) {
    val defaultPrompt = when (promptType) {
        AiPromptType.SORU_URETIMI -> AiPromptManager.PROMPT_SORU_URETIMI_DEFAULT
        AiPromptType.COZUM_URETIMI -> AiPromptManager.PROMPT_COZUM_URETIMI_DEFAULT
        AiPromptType.OCR_TEMIZLEME -> AiPromptManager.PROMPT_OCR_TEMIZLEME_DEFAULT
        AiPromptType.BENZER_SORU -> AiPromptManager.PROMPT_BENZER_SORU_DEFAULT
    }
    var editText by remember(promptType, customPrompt) { mutableStateOf(customPrompt) }
    // Sync when external customPrompt changes
    androidx.compose.runtime.LaunchedEffect(customPrompt) {
        if (editText != customPrompt) editText = customPrompt
    }
    val isCustom = customPrompt.isNotBlank()
    val charCount = editText.length
    val isTooLong = charCount > 8000

    OutlinedCard(
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = promptType.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (isCustom) {
                        Text(
                            text = "Özelleştirildi • $charCount char",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                IconButton(onClick = onToggleExpand, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Daralt" else "Genişlet"
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = promptType.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Varsayılan preview
                    Text(
                        text = "Varsayılan şablon (salt okunur):",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedTextField(
                        value = defaultPrompt,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 6,
                        shape = RoundedCornerShape(8.dp),
                        enabled = false,
                        colors = OutlinedTextFieldDefaults.colors(disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                    Text(
                        text = "Özelleştir (boş = varsayılan, 8000 limit). JSON beklenen tiplerde questionText/correctAnswer içermeli.",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isTooLong) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = editText,
                        onValueChange = { editText = it },
                        label = { Text("Özel Prompt") },
                        placeholder = { Text("Boş bırak = varsayılan kullanılır") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 10,
                        shape = RoundedCornerShape(8.dp),
                        isError = isTooLong,
                        supportingText = {
                            Text("${charCount}/8000", color = if (isTooLong) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onSave(editText) },
                            enabled = !isTooLong,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Kaydet")
                        }
                        OutlinedButton(
                            onClick = {
                                editText = ""
                                onReset()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Varsayılana Döndür")
                        }
                    }
                }
            }
        }
    }
}
