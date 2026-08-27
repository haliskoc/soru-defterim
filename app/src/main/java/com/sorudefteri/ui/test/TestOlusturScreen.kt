package com.sorudefteri.ui.test

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sorudefteri.domain.model.HataTipi

/**
 * Test Oluşturma Ekranı.
 * Kullanıcının sınav türü, dersler, konular, hata tipleri ve soru sayısını seçerek özel test oluşturmasını sağlar.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TestOlusturScreen(
    onStartTest: () -> Unit,
    viewModel: TestOlusturViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var isTopicsExpanded by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Quiz,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Test Oluştur",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            // Canlı Soru Sayacı ve Testi Başlat Butonu
            SurfaceBottomBar(
                matchingCount = uiState.matchingQuestions.size,
                selectedCount = uiState.effectiveQuestionCount,
                onStartTest = {
                    viewModel.startTest(onStartTest)
                }
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Bilgilendirme Hero Kartı
                item {
                    HeroCard(totalQuestions = uiState.allQuestions.size)
                }

                // 1. Sınav Türü Seçimi
                item {
                    SectionContainer(
                        title = "Sınav Türü",
                        icon = Icons.Outlined.Analytics
                    ) {
                        val examTypes = listOf("Tümü", "TYT", "AYT", "LGS", "KPSS")
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            examTypes.forEach { type ->
                                val isSelected = when (type) {
                                    "Tümü" -> uiState.examType == null
                                    else -> uiState.examType == type
                                }
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.setExamType(type) },
                                    label = { Text(type, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                    leadingIcon = if (isSelected) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                        }
                    }
                }

                // 2. Soru Durumu (Sadece Çözülmemişler)
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Sadece Çözülmemiş Sorular",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Daha önce doğru/çözüldü olarak işaretlenmemiş sorular",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = uiState.onlyUnsolved,
                                onCheckedChange = { viewModel.setOnlyUnsolved(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                }

                // 3. Soru Sayısı Seçimi
                item {
                    SectionContainer(
                        title = "Soru Sayısı",
                        icon = Icons.Default.FilterList
                    ) {
                        val countOptions = listOf(5, 10, 15, 20, 0)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            countOptions.forEach { count ->
                                val isSelected = uiState.questionCount == count
                                val label = if (count == 0) "Tümü" else "$count Soru"
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.setQuestionCount(count) },
                                    label = { Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                )
                            }
                        }
                    }
                }

                // 4. Ders Seçimi (Çoklu Seçim)
                item {
                    SectionContainer(
                        title = "Dersler (${if (uiState.selectedSubjects.isEmpty()) "Tümü" else "${uiState.selectedSubjects.size} Seçildi"})",
                        icon = Icons.Outlined.MenuBook,
                        action = {
                            Row {
                                TextButton(onClick = { viewModel.selectAllSubjects() }) {
                                    Text("Tümünü Seç", fontSize = 12.sp)
                                }
                                TextButton(onClick = { viewModel.clearSubjects() }) {
                                    Text("Temizle", fontSize = 12.sp)
                                }
                            }
                        }
                    ) {
                        if (uiState.availableSubjects.isEmpty()) {
                            Text(
                                text = "Arşivde bu filtreye uygun ders bulunamadı.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                uiState.availableSubjects.forEach { item ->
                                    val isSelected = uiState.selectedSubjects.contains(item.ders.id)
                                    val countText = if (uiState.onlyUnsolved) " (${item.unsolvedCount})" else " (${item.totalCount})"

                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { viewModel.toggleSubject(item.ders.id) },
                                        label = {
                                            Text(
                                                text = "${item.ders.ad}$countText",
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        leadingIcon = if (isSelected) {
                                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                        } else null
                                    )
                                }
                            }
                        }
                    }
                }

                // 5. Konu Seçimi (Opsiyonel / Dinamik)
                if (uiState.availableTopics.isNotEmpty()) {
                    item {
                        SectionContainer(
                            title = "Konular (${if (uiState.selectedTopics.isEmpty()) "Tümü" else "${uiState.selectedTopics.size} Seçildi"})",
                            icon = Icons.Default.FilterList,
                            action = {
                                if (uiState.selectedTopics.isNotEmpty()) {
                                    TextButton(onClick = { viewModel.clearTopics() }) {
                                        Text("Temizle", fontSize = 12.sp)
                                    }
                                }
                            }
                        ) {
                            val displayTopics = if (isTopicsExpanded) uiState.availableTopics else uiState.availableTopics.take(8)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                displayTopics.forEach { topic ->
                                    val isSelected = uiState.selectedTopics.contains(topic)
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { viewModel.toggleTopic(topic) },
                                        label = {
                                            Text(
                                                text = topic,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 13.sp
                                            )
                                        },
                                        leadingIcon = if (isSelected) {
                                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                        } else null
                                    )
                                }
                            }

                            if (uiState.availableTopics.size > 8) {
                                TextButton(
                                    onClick = { isTopicsExpanded = !isTopicsExpanded },
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Text(
                                        text = if (isTopicsExpanded) "Daha Az Göster ▲" else "Tüm Konuları Göster (${uiState.availableTopics.size}) ▼",
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }

                // 6. Hata Tipi Filtresi (Çoklu Seçim)
                item {
                    SectionContainer(
                        title = "Hata Tipleri (${if (uiState.selectedMistakeTypes.isEmpty()) "Tümü" else "${uiState.selectedMistakeTypes.size} Seçildi"})",
                        icon = Icons.Default.Info,
                        action = {
                            if (uiState.selectedMistakeTypes.isNotEmpty()) {
                                TextButton(onClick = { viewModel.clearMistakeTypes() }) {
                                    Text("Temizle", fontSize = 12.sp)
                                }
                            }
                        }
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HataTipi.entries.forEach { hataTipi ->
                                val isSelected = uiState.selectedMistakeTypes.contains(hataTipi)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.toggleMistakeType(hataTipi) },
                                    label = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(hataTipi.defaultColor)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = hataTipi.displayName,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // Alt boşluk
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun HeroCard(totalQuestions: Int) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Quiz,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Kişisel Deneme Testi",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Arşivinde toplam $totalQuestions soru bulunuyor",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Hatalarını tekrar etmek ve eksiklerini pekiştirmek için dilediğin ders ve konulardan özel test oluştur.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f),
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun SectionContainer(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    action: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            action?.invoke()
        }
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun SurfaceBottomBar(
    matchingCount: Int,
    selectedCount: Int,
    onStartTest: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (matchingCount > 0) Icons.Default.CheckCircle else Icons.Default.Info,
                        contentDescription = null,
                        tint = if (matchingCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (matchingCount > 0) {
                            "Kriterlere uygun $matchingCount soru bulundu"
                        } else {
                            "Uygun soru bulunamadı"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (matchingCount > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                    )
                }

                if (matchingCount > 0) {
                    Text(
                        text = "Hedef: $selectedCount Soru",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onStartTest,
                enabled = matchingCount > 0,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (matchingCount > 0) "Testi Başlat ($selectedCount Soru)" else "Test Oluşturulamıyor",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
