package com.sorudefteri.ui.soruekle.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Translate
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.sorudefteri.domain.model.Ders
import com.sorudefteri.domain.model.HataTipi

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SoruMetadataSection(
    examType: String,
    subjectId: String,
    topic: String,
    difficulty: String?,
    mistakeType: HataTipi?,
    availableSubjects: List<Ders>,
    frequentTopics: List<String>,
    currentSubjectName: String,
    totalTopicCountInCatalog: Int,
    onExamTypeChanged: (String) -> Unit,
    onSubjectChanged: (String) -> Unit,
    onTopicChanged: (String) -> Unit,
    onDifficultyChanged: (String?) -> Unit,
    onMistakeTypeChanged: (HataTipi?) -> Unit,
    onOpenCatalogClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // 1. Sınav Tipi (TYT / AYT)
        Text(
            text = "Sınav Tipi *",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        val examTypes = listOf("TYT", "AYT")
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            examTypes.forEachIndexed { index, type ->
                SegmentedButton(
                    selected = examType == type,
                    onClick = { onExamTypeChanged(type) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = examTypes.size)
                ) {
                    Text(type, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 2. Ders Seçimi
        Text(
            text = "Ders *",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        val subjectScrollState = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(subjectScrollState),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            availableSubjects.forEach { ders ->
                val isSelected = ders.id == subjectId
                FilterChip(
                    selected = isSelected,
                    onClick = { onSubjectChanged(ders.id) },
                    label = { Text(ders.ad, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                    leadingIcon = {
                        Icon(
                            imageVector = getDersIcon(ders.id),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }

        // 3. Konu Seçimi
        Text(
            text = "Konu Başlığı *",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        OutlinedTextField(
            value = topic,
            onValueChange = onTopicChanged,
            label = { Text("Konu Adı") },
            placeholder = { Text("Örn: Türev, Paragraf, Hücre...") },
            trailingIcon = {
                if (topic.isNotBlank()) {
                    IconButton(onClick = { onTopicChanged("") }) {
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
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focusManager.clearFocus() }),
            modifier = Modifier.fillMaxWidth()
        )

        // Konu Kataloğu Açma Butonu
        Button(
            onClick = onOpenCatalogClick,
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
                text = "$currentSubjectName Konu Kataloğunu Aç ($totalTopicCountInCatalog Konu)",
                fontWeight = FontWeight.Bold
            )
        }

        // Sık Kullanılan Konular
        if (frequentTopics.isNotEmpty()) {
            Text(
                text = "Önerilen / Sık Konular:",
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
                frequentTopics.forEach { topicSuggestion ->
                    val isSelected = topic == topicSuggestion
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.clickable { onTopicChanged(topicSuggestion) }
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

        // 4. Zorluk Seviyesi
        Text(
            text = "Zorluk Seviyesi",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        val difficulties = listOf("Kolay", "Orta", "Zor")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            difficulties.forEach { diff ->
                val isSelected = difficulty == diff
                FilterChip(
                    selected = isSelected,
                    onClick = { onDifficultyChanged(if (isSelected) null else diff) },
                    label = { Text(diff, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                )
            }
        }

        // 5. Hata Tipi Seçimi
        Text(
            text = "Hata Tipi (Neden Yanlış Yapıldı?)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            HataTipi.entries.forEach { tip ->
                val isSelected = mistakeType == tip
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        onMistakeTypeChanged(if (isSelected) null else tip)
                    },
                    label = { Text(tip.displayName) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                )
            }
        }
    }
}

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
        clean.contains("ydt") || clean.contains("ingilizce") -> Icons.Default.Translate
        else -> Icons.Default.MenuBook
    }
}
