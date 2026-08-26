package com.sorudefteri.ui.ayarlar.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.sorudefteri.data.ai.AiPromptManager
import com.sorudefteri.data.ai.AiPromptType
import com.sorudefteri.domain.model.AiProviderType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiPromptAyarlarTab(
    aiProvider: AiProviderType,
    geminiApiKey: String,
    groqApiKey: String,
    geminiModel: String,
    groqModel: String,
    isFetchingModels: Boolean,
    fetchedGeminiModels: List<Pair<String, String>>,
    fetchedGroqModels: List<Pair<String, String>>,
    modelFetchStatus: String?,
    isModelFetchError: Boolean,
    onAiProviderSelected: (AiProviderType) -> Unit,
    onGeminiApiKeyChanged: (String) -> Unit,
    onGroqApiKeyChanged: (String) -> Unit,
    onGeminiModelSelected: (String) -> Unit,
    onGroqModelSelected: (String) -> Unit,
    onFetchLiveModelsClick: (AiProviderType) -> Unit,
    modifier: Modifier = Modifier
) {
    var isKeyVisible by remember { mutableStateOf(false) }
    var isModelDropdownExpanded by remember { mutableStateOf(false) }
    var expandedPromptType by remember { mutableStateOf<AiPromptType?>(null) }

    val currentKey = if (aiProvider == AiProviderType.GEMINI) geminiApiKey else groqApiKey
    val onKeyChange = if (aiProvider == AiProviderType.GEMINI) onGeminiApiKeyChanged else onGroqApiKeyChanged

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. AI Sağlayıcı Seçimi & API Key
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AI Sağlayıcı & API Anahtarı",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Gemini vs Groq Segmented
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    AiProviderType.entries.forEachIndexed { index, provider ->
                        SegmentedButton(
                            selected = aiProvider == provider,
                            onClick = { onAiProviderSelected(provider) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = AiProviderType.entries.size)
                        ) {
                            Text(provider.displayName, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // API Key Girişi
                OutlinedTextField(
                    value = currentKey,
                    onValueChange = onKeyChange,
                    label = { Text("${aiProvider.displayName} API Key") },
                    placeholder = { Text("API anahtarınızı yapıştırın...") },
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
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

                // Güvenlik Bilgisi Rozeti
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "P0-1 Güvenlik: API anahtarları EncryptedSharedPreferences ile cihazda AES-256 şifreli saklanır.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        // 2. Model Seçimi & Canlı Model Listesi
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Aktif Model Seçimi",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                val activeModel = if (aiProvider == AiProviderType.GEMINI) geminiModel else groqModel
                val onModelSelected = if (aiProvider == AiProviderType.GEMINI) onGeminiModelSelected else onGroqModelSelected
                val availableModels = if (aiProvider == AiProviderType.GEMINI) {
                    if (fetchedGeminiModels.isNotEmpty()) fetchedGeminiModels else aiProvider.supportedModels
                } else {
                    if (fetchedGroqModels.isNotEmpty()) fetchedGroqModels else aiProvider.supportedModels
                }

                ExposedDropdownMenuBox(
                    expanded = isModelDropdownExpanded,
                    onExpandedChange = { isModelDropdownExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = availableModels.find { it.first == activeModel }?.second ?: activeModel,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isModelDropdownExpanded) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )

                    ExposedDropdownMenu(
                        expanded = isModelDropdownExpanded,
                        onDismissRequest = { isModelDropdownExpanded = false }
                    ) {
                        availableModels.forEach { (modelId, modelName) ->
                            DropdownMenuItem(
                                text = { Text(modelName, fontWeight = if (modelId == activeModel) FontWeight.Bold else FontWeight.Normal) },
                                onClick = {
                                    onModelSelected(modelId)
                                    isModelDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                FilledTonalButton(
                    onClick = { onFetchLiveModelsClick(aiProvider) },
                    enabled = !isFetchingModels && currentKey.isNotBlank(),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isFetchingModels) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Modeller Çekiliyor...")
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Güncel Canlı Modelleri Çek")
                    }
                }

                modelFetchStatus?.let { status ->
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isModelFetchError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // 3. Detaylı System Prompt Yönetimi
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "System Prompt Yönetimi",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Yapay zekanın soru üretimi, çözüm adımları ve OCR düzeltme davranışını yöneten sistem promptlarını özelleştirebilirsiniz.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                AiPromptType.entries.forEach { promptType ->
                    PromptAccordionItem(
                        promptType = promptType,
                        isExpanded = expandedPromptType == promptType,
                        onToggleExpand = {
                            expandedPromptType = if (expandedPromptType == promptType) null else promptType
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PromptAccordionItem(
    promptType: AiPromptType,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    val defaultPrompt = when (promptType) {
        AiPromptType.SORU_URETIMI -> AiPromptManager.PROMPT_SORU_URETIMI_DEFAULT
        AiPromptType.COZUM_URETIMI -> AiPromptManager.PROMPT_COZUM_URETIMI_DEFAULT
        AiPromptType.OCR_TEMIZLEME -> AiPromptManager.PROMPT_OCR_TEMIZLEME_DEFAULT
        AiPromptType.BENZER_SORU -> AiPromptManager.PROMPT_BENZER_SORU_DEFAULT
    }

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
                Text(
                    text = promptType.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )

                IconButton(onClick = onToggleExpand, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        text = promptType.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = defaultPrompt,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Varsayılan Sistem Şablonu") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 8,
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }
        }
    }
}
