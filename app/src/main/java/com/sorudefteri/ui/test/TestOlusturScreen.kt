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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.MenuBook
import com.sorudefteri.ui.ortak.StreakBadge
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.sorudefteri.ui.ortak.DailyDueRescueCard

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
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

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
                        Column {
                            Text(
                                text = "Test Oluştur",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Adım adım: tür → süre → ders → başlat",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    StreakBadge(
                        streakCount = uiState.currentStreak,
                        shieldCount = uiState.streakShieldCount,
                        modifier = Modifier.padding(end = 12.dp)
                    )
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter
            ) {
                LazyColumn(
                    modifier = Modifier
                        .widthIn(max = 840.dp)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                // Günün Kurtarma Paketi (Tekrar vakti gelmiş sorular varsa en tepede gösterilir)
                if (uiState.dueReviewCount > 0) {
                    item {
                        DailyDueRescueCard(
                            dueCount = uiState.dueReviewCount,
                            onStartClick = {
                                viewModel.startDueReviewTest(onStartTest)
                            }
                        )
                    }
                }

                // Bilgilendirme Hero Kartı
                item {
                    HeroCard(totalQuestions = uiState.allQuestions.size)
                }

                // 1. Sınav Türü Seçimi
                item {
                    SectionContainer(
                        title = "Sınav Türü",
                        icon = Icons.Outlined.Analytics,
                        adim = 1
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

                // 2. Zaman Baskısı Modu (Speed Run)
                item {
                    SectionContainer(
                        title = "Zaman Baskısı Modu (Speed Run)",
                        icon = Icons.Default.Timer,
                        adim = 2
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Soru başına süre sınırı koyarak sınav anındaki zaman baskısını simüle et.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val timeModes = listOf(
                                "Süresiz" to 0L,
                                "TYT (75 sn / soru)" to 75L,
                                "AYT (120 sn / soru)" to 120L
                            )
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                timeModes.forEach { (label, seconds) ->
                                    val isSelected = uiState.timeLimitPerQuestionSeconds == seconds
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { viewModel.setTimeLimitPerQuestion(seconds) },
                                        label = {
                                            Text(
                                                text = if (seconds > 0) "⚡ $label" else label,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        leadingIcon = if (isSelected) {
                                            {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        } else null,
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = if (seconds > 0) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = if (seconds > 0) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Soru Durumu (Sadece Çözülmemişler)
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
                        icon = Icons.Default.FilterList,
                        adim = 3
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
                        adim = 4,
                        action = {
                            Row {
                                TextButton(
                                    onClick = { viewModel.selectAllSubjects() },
                                    modifier = Modifier.heightIn(min = 44.dp)
                                ) {
                                    Text("Tümünü Seç", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                                TextButton(
                                    onClick = { viewModel.clearSubjects() },
                                    modifier = Modifier.heightIn(min = 44.dp)
                                ) {
                                    Text("Temizle", fontSize = 13.sp)
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
                            adim = 5,
                            action = {
                                if (uiState.selectedTopics.isNotEmpty()) {
                                    TextButton(
                                        onClick = { viewModel.clearTopics() },
                                        modifier = Modifier.heightIn(min = 44.dp)
                                    ) {
                                        Text("Temizle", fontSize = 13.sp)
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
                        adim = 6,
                        action = {
                            if (uiState.selectedMistakeTypes.isNotEmpty()) {
                                TextButton(
                                    onClick = { viewModel.clearMistakeTypes() },
                                    modifier = Modifier.heightIn(min = 44.dp)
                                ) {
                                    Text("Temizle", fontSize = 13.sp)
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
    adim: Int? = null,
    action: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    // TELEFON: adım rozetli kart (tablet de kullanır — küçük düzeltme, nefes alan)
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    if (adim != null) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Text(
                                text = "$adim",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                    } else {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                action?.invoke()
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SurfaceBottomBar(
    matchingCount: Int,
    selectedCount: Int,
    onStartTest: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 6.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Icon(
                        imageVector = if (matchingCount > 0) Icons.Default.CheckCircle else Icons.Default.Info,
                        contentDescription = null,
                        tint = if (matchingCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (matchingCount > 0) {
                            "Kriterlere uygun $matchingCount soru bulundu"
                        } else {
                            "Uygun soru bulunamadı"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (matchingCount > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                    )
                }

                if (matchingCount > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Hedef: $selectedCount Soru",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onStartTest,
                enabled = matchingCount > 0,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
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
