package com.sorudefteri.ui.ortak

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FilterListOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sorudefteri.domain.model.Ders
import com.sorudefteri.domain.model.HataTipi

/**
 * Kütüphane ekranı için yatay kaydırılabilir filtre çubuğu.
 */
@Composable
fun FiltreBar(
    selectedExamType: String?,
    onExamTypeSelected: (String?) -> Unit,
    selectedSubjectId: String?,
    availableSubjects: List<Ders>,
    onSubjectSelected: (String?) -> Unit,
    selectedMistakeType: HataTipi?,
    onMistakeTypeSelected: (HataTipi?) -> Unit,
    onlyUnsolved: Boolean,
    onOnlyUnsolvedToggle: (Boolean) -> Unit,
    onResetFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var subjectMenuExpanded by remember { mutableStateOf(false) }
    var mistakeMenuExpanded by remember { mutableStateOf(false) }

    val hasActiveFilter = selectedExamType != null ||
            selectedSubjectId != null ||
            selectedMistakeType != null ||
            onlyUnsolved

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Filtreleri Sıfırla Butonu (Herhangi bir filtre aktifse gösterilir)
        if (hasActiveFilter) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                onClick = onResetFilters
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterListOff,
                        contentDescription = "Filtreleri Sıfırla",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Temizle",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        // Sınav Türü: Tümü
        FilterChip(
            selected = selectedExamType == null,
            onClick = { onExamTypeSelected(null) },
            label = { Text("Tümü") },
            leadingIcon = if (selectedExamType == null) {
                {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else null
        )

        // Sınav Türü: TYT
        FilterChip(
            selected = selectedExamType == "TYT",
            onClick = { onExamTypeSelected(if (selectedExamType == "TYT") null else "TYT") },
            label = { Text("TYT") },
            leadingIcon = if (selectedExamType == "TYT") {
                {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else null
        )

        // Sınav Türü: AYT
        FilterChip(
            selected = selectedExamType == "AYT",
            onClick = { onExamTypeSelected(if (selectedExamType == "AYT") null else "AYT") },
            label = { Text("AYT") },
            leadingIcon = if (selectedExamType == "AYT") {
                {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else null
        )

        // Ders Filtresi Dropdown
        Box {
            val selectedSubjectName = availableSubjects.find { it.id == selectedSubjectId }?.name
            val isSubjectSelected = selectedSubjectId != null

            FilterChip(
                selected = isSubjectSelected,
                onClick = { subjectMenuExpanded = true },
                label = {
                    Text(
                        text = if (isSubjectSelected) "Ders: $selectedSubjectName" else "Ders Seç"
                    )
                },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Ders Seç",
                        modifier = Modifier.size(18.dp)
                    )
                },
                colors = if (isSubjectSelected) {
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else FilterChipDefaults.filterChipColors()
            )

            DropdownMenu(
                expanded = subjectMenuExpanded,
                onDismissRequest = { subjectMenuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Tüm Dersler", fontWeight = FontWeight.Bold) },
                    onClick = {
                        onSubjectSelected(null)
                        subjectMenuExpanded = false
                    },
                    leadingIcon = {
                        if (selectedSubjectId == null) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                )

                availableSubjects.forEach { ders ->
                    val isSelected = ders.id == selectedSubjectId
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "${ders.name} (${ders.examType})",
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            onSubjectSelected(ders.id)
                            subjectMenuExpanded = false
                        },
                        leadingIcon = {
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    )
                }
            }
        }

        // Hata Tipi Filtresi Dropdown
        Box {
            val isMistakeSelected = selectedMistakeType != null

            FilterChip(
                selected = isMistakeSelected,
                onClick = { mistakeMenuExpanded = true },
                label = {
                    Text(
                        text = if (isMistakeSelected) "Hata: ${selectedMistakeType?.displayName}" else "Hata Tipi"
                    )
                },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Hata Tipi Seç",
                        modifier = Modifier.size(18.dp)
                    )
                },
                colors = if (isMistakeSelected) {
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                } else FilterChipDefaults.filterChipColors()
            )

            DropdownMenu(
                expanded = mistakeMenuExpanded,
                onDismissRequest = { mistakeMenuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Tüm Hata Tipleri", fontWeight = FontWeight.Bold) },
                    onClick = {
                        onMistakeTypeSelected(null)
                        mistakeMenuExpanded = false
                    },
                    leadingIcon = {
                        if (selectedMistakeType == null) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                )

                HataTipi.entries.forEach { hata ->
                    val isSelected = hata == selectedMistakeType
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = hata.displayName,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            onMistakeTypeSelected(hata)
                            mistakeMenuExpanded = false
                        },
                        leadingIcon = {
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    )
                }
            }
        }

        // Sadece Çözülmeyenler Toggle
        FilterChip(
            selected = onlyUnsolved,
            onClick = { onOnlyUnsolvedToggle(!onlyUnsolved) },
            label = { Text("Çözülmeyenler") },
            leadingIcon = if (onlyUnsolved) {
                {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else null
        )
    }
}
