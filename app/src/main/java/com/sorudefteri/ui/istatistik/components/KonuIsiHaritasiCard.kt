package com.sorudefteri.ui.istatistik.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sorudefteri.domain.DersTaksonomisi
import com.sorudefteri.domain.KonuKatalog
import com.sorudefteri.domain.model.Alan
import com.sorudefteri.domain.model.Ders
import com.sorudefteri.domain.model.KonuYetkinlikItem
import com.sorudefteri.domain.model.MasteryLevel
import com.sorudefteri.domain.model.Soru
import com.sorudefteri.domain.model.calculateMasteryPercentage
import java.util.Locale

/**
 * Konu Yetkinlik Isı Haritası (Subject Mastery Heatmap)
 *
 * 4 Renk Kademesi:
 * - 🟥 %0 - %40: Kritik Eksik
 * - 🟨 %41 - %70: Pekiştirilmeli
 * - 🟦 %71 - %89: Güçlü
 * - 🟩 %90 - %100: Tam Ustalık
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KonuIsiHaritasiCard(
    allQuestions: List<Soru>,
    konuKatalog: KonuKatalog,
    modifier: Modifier = Modifier,
    onStartTestForTopic: ((examType: String, subjectId: String, topic: String) -> Unit)? = null
) {
    var selectedExamType by remember { mutableStateOf("TYT") }
    val examList = listOf("TYT", "AYT")

    // Seçilen sınav türüne göre dersleri getir
    val availableSubjects = remember(selectedExamType) {
        if (selectedExamType == "TYT") {
            DersTaksonomisi.tytDersleri
        } else {
            DersTaksonomisi.aytSayisalDersleri + DersTaksonomisi.aytEsitAgirlikDersleri.filter { ea ->
                DersTaksonomisi.aytSayisalDersleri.none { it.id == ea.id }
            }
        }
    }

    var selectedSubject by remember(selectedExamType) {
        mutableStateOf(availableSubjects.firstOrNull()?.id ?: "matematik")
    }

    var selectedTopicItem by remember { mutableStateOf<KonuYetkinlikItem?>(null) }

    // Kataloğu ve soruları eşleştirerek üniteler ve konuları hesapla
    val unitsWithTopics = remember(selectedExamType, selectedSubject, allQuestions) {
        val units = konuKatalog.getUniteList(selectedExamType, selectedSubject)
        val subjectQuestions = allQuestions.filter { q ->
            q.examType.equals(selectedExamType, ignoreCase = true) &&
                (q.subjectId.equals(selectedSubject, ignoreCase = true) ||
                    q.subjectId.contains(selectedSubject, ignoreCase = true) ||
                    selectedSubject.contains(q.subjectId, ignoreCase = true))
        }

        units.map { unite ->
            val topicItems = unite.konular.map { topicName ->
                val matchingQuestions = subjectQuestions.filter { q ->
                    isTopicMatching(q.topic, topicName)
                }
                val totalCount = matchingQuestions.size
                val solvedCount = matchingQuestions.count { it.isSolved }
                val unsolvedCount = totalCount - solvedCount
                val percentage = calculateMasteryPercentage(solvedCount, totalCount)
                val level = MasteryLevel.fromPercentage(percentage)

                KonuYetkinlikItem(
                    examType = selectedExamType,
                    subjectId = selectedSubject,
                    unitName = unite.uniteAdi,
                    topicName = topicName,
                    totalCount = totalCount,
                    solvedCount = solvedCount,
                    unsolvedCount = unsolvedCount,
                    masteryPercentage = percentage,
                    level = level
                )
            }
            unite.uniteAdi to topicItems
        }
    }

    // Seçili dersteki genel ortalama yetkinlik
    val allTopicsInSubject = unitsWithTopics.flatMap { it.second }
    val totalQuestionsInSubject = allTopicsInSubject.sumOf { it.totalCount }
    val totalSolvedInSubject = allTopicsInSubject.sumOf { it.solvedCount }
    val averageSubjectMastery = calculateMasteryPercentage(totalSolvedInSubject, totalQuestionsInSubject)
    val overallLevel = MasteryLevel.fromPercentage(averageSubjectMastery)

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Başlık & Bilgi
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GridOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Konu Yetkinlik Isı Haritası",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Müfredat ünitelerindeki ustalık skalası",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 1. TYT / AYT Tab Seçici
            TabRow(
                selectedTabIndex = examList.indexOf(selectedExamType).coerceAtLeast(0),
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.clip(RoundedCornerShape(12.dp))
            ) {
                examList.forEachIndexed { index, exam ->
                    Tab(
                        selected = selectedExamType == exam,
                        onClick = {
                            selectedExamType = exam
                            selectedTopicItem = null
                        },
                        text = {
                            Text(
                                text = exam,
                                fontWeight = if (selectedExamType == exam) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    )
                }
            }

            // 2. Ders Seçim Çipleri (Yatay Kaydırılabilir)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                availableSubjects.forEach { ders ->
                    val isSelected = selectedSubject.equals(ders.id, ignoreCase = true)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedSubject = ders.id
                            selectedTopicItem = null
                        },
                        label = { Text(ders.ad, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            // 3. Seçili Ders Başarı Özeti
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(overallLevel.colorHex).copy(alpha = 0.08f),
                border = BorderStroke(1.dp, Color(overallLevel.colorHex).copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = DersTaksonomisi.getDersAdi(selectedExamType, selectedSubject),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${allTopicsInSubject.size} Konu • $totalSolvedInSubject / $totalQuestionsInSubject Soru Çözüldü",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = overallLevel.emoji,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "%$averageSubjectMastery",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(overallLevel.colorHex)
                        )
                    }
                }
            }

            // 4. Renk Skalası Açıklaması (Legend Bar)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Ustalık Seviyeleri:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MasteryLevel.entries.forEach { level ->
                        LegendBadge(level = level)
                    }
                }
            }

            // 5. Seçilen Konu Detay Tooltip / Banner
            AnimatedVisibility(
                visible = selectedTopicItem != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                selectedTopicItem?.let { item ->
                    TopicDetailTooltip(
                        item = item,
                        onDismiss = { selectedTopicItem = null },
                        onStartTest = {
                            onStartTestForTopic?.invoke(item.examType, item.subjectId, item.topicName)
                        }
                    )
                }
            }

            // 6. İnteraktif Isı Haritası Izgarası (GitHub Katkı Formatı)
            if (unitsWithTopics.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Bu derse ait konu kataloğu yükleniyor...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    unitsWithTopics.forEach { (unitName, topicItems) ->
                        UnitSection(
                            unitName = unitName,
                            topics = topicItems,
                            selectedTopic = selectedTopicItem,
                            onTopicClick = { topic ->
                                selectedTopicItem = if (selectedTopicItem?.topicName == topic.topicName) null else topic
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Renk Skalası Rozeti
 */
@Composable
private fun LegendBadge(level: MasteryLevel) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(level.colorHex).copy(alpha = 0.12f),
        border = BorderStroke(1.dp, Color(level.colorHex).copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(level.colorHex))
            )
            Text(
                text = "${level.emoji} %${level.minPercent}-%${level.maxPercent} ${level.displayName}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Ünite ve altındaki konuların ızgara görünümü
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UnitSection(
    unitName: String,
    topics: List<KonuYetkinlikItem>,
    selectedTopic: KonuYetkinlikItem?,
    onTopicClick: (KonuYetkinlikItem) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Ünite Başlığı
        Text(
            text = unitName,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )

        // Konu Isı Karoları
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            topics.forEach { topicItem ->
                val isSelected = selectedTopic?.topicName == topicItem.topicName
                TopicHeatTile(
                    item = topicItem,
                    isSelected = isSelected,
                    onClick = { onTopicClick(topicItem) }
                )
            }
        }
    }
}

/**
 * Isı Haritası Konu Karosu
 */
@Composable
private fun TopicHeatTile(
    item: KonuYetkinlikItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val levelColor = Color(item.level.colorHex)
    val tileBackground = if (item.isAttempted) {
        levelColor.copy(alpha = if (isSelected) 0.3f else 0.15f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else if (item.isAttempted) {
        levelColor.copy(alpha = 0.6f)
    } else {
        Color.Transparent
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = tileBackground,
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (item.isAttempted) levelColor else Color.Gray.copy(alpha = 0.4f))
            )
            Text(
                text = item.topicName,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.isAttempted) {
                Text(
                    text = "%${item.masteryPercentage}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = levelColor
                )
            }
        }
    }
}

/**
 * Tıklanan konunun detaylarını ve aksiyonunu gösteren interaktif kart
 */
@Composable
private fun TopicDetailTooltip(
    item: KonuYetkinlikItem,
    onDismiss: () -> Unit,
    onStartTest: () -> Unit
) {
    val levelColor = Color(item.level.colorHex)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        border = BorderStroke(1.5.dp, levelColor.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.topicName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${item.unitName} • ${item.examType}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Kapat",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Başarı Çubuğu ve Rozet
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(text = item.level.emoji)
                    Text(
                        text = item.level.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = levelColor
                    )
                }
                Text(
                    text = "%${item.masteryPercentage} Ustalık",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = levelColor
                )
            }

            LinearProgressIndicator(
                progress = { item.masteryPercentage / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = levelColor,
                trackColor = levelColor.copy(alpha = 0.2f)
            )

            // İstatistik Sayıları
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Toplam Soru: ${item.totalCount}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Çözülen: ${item.solvedCount}",
                    fontSize = 12.sp,
                    color = Color(0xFF43A047),
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Hatalı / Kalan: ${item.unsolvedCount}",
                    fontSize = 12.sp,
                    color = Color(0xFFEF5350),
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = item.level.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Hedefli Test Çöz Butonu
            if (item.totalCount > 0) {
                Button(
                    onClick = onStartTest,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Bu Konudan Hedefli Test Çöz (${item.totalCount} Soru)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * İki konu isminin semantik eşleşmesini kontrol eder (büyük/küçük harf, Türkçe karakter ve içerme desteği)
 */
private fun isTopicMatching(dbTopic: String, catalogTopic: String): Boolean {
    val cleanDb = normalizeString(dbTopic)
    val cleanCat = normalizeString(catalogTopic)

    if (cleanDb.isBlank() || cleanCat.isBlank()) return false
    if (cleanDb == cleanCat) return true

    // Parantez içi temizleyerek kontrol (örn: "Sözcükte Anlam (Gerçek, Mecaz...)" -> "Sözcükte Anlam")
    val catBase = cleanCat.substringBefore("(").trim()
    val dbBase = cleanDb.substringBefore("(").trim()

    if (catBase.isNotBlank() && (cleanDb.contains(catBase) || catBase.contains(cleanDb))) {
        return true
    }
    if (dbBase.isNotBlank() && (cleanCat.contains(dbBase) || dbBase.contains(cleanCat))) {
        return true
    }

    return false
}

private fun normalizeString(input: String): String {
    return input.lowercase(Locale("tr", "TR"))
        .replace("ç", "c")
        .replace("ğ", "g")
        .replace("ı", "i")
        .replace("i̇", "i")
        .replace("ö", "o")
        .replace("ş", "s")
        .replace("ü", "u")
        .trim()
}
