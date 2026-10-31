package com.sorudefteri.ui.istatistik.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sorudefteri.data.local.entity.DenemeSinaviEntity
import com.sorudefteri.domain.DersTaksonomisi
import com.sorudefteri.domain.model.HataTipi
import com.sorudefteri.domain.model.Soru
import com.sorudefteri.ui.theme.ErrorRed
import com.sorudefteri.ui.theme.SuccessGreen
import com.sorudefteri.ui.theme.Tertiary
import com.sorudefteri.ui.theme.WarningOrange
import java.util.Locale
import kotlin.math.exp

private data class YayineviAnalizSatiri(
    val yayinAdi: String,
    val denemeSayisi: Int,
    val ortalamaNet: Double,
    val soruSayisi: Int,
    val cozulmeOrani: Float,
    val zorlukEtiketi: String,
    val renk: Color
)

/**
 * 1. Yayınevi Performans & Zorluk Kıyaslama Radarı
 */
@Composable
fun YayineviPerformansCard(
    allDenemeler: List<DenemeSinaviEntity>,
    allQuestions: List<Soru>,
    modifier: Modifier = Modifier
) {
    val analizListesi = remember(allDenemeler, allQuestions) {
        val yayinMap = linkedMapOf<String, Pair<MutableList<DenemeSinaviEntity>, MutableList<Soru>>>()

        allDenemeler.forEach { d ->
            val rawName = d.yayinEvi.trim().ifBlank {
                // Başlıktan yayınevi tahmini (3D, Özdebir, Bilgi Sarmal vb.)
                tahminEtYayinevi(d.baslik)
            }
            if (rawName.isNotBlank()) {
                val pair = yayinMap.getOrPut(rawName) { mutableListOf<DenemeSinaviEntity>() to mutableListOf<Soru>() }
                pair.first.add(d)
            }
        }

        allQuestions.forEach { s ->
            val rawName = s.kaynakYayin?.trim()?.ifBlank { null }
                ?: s.kaynakDeneme?.let { tahminEtYayinevi(it) }?.ifBlank { null }
            if (!rawName.isNullOrBlank()) {
                val pair = yayinMap.getOrPut(rawName) { mutableListOf<DenemeSinaviEntity>() to mutableListOf<Soru>() }
                pair.second.add(s)
            }
        }

        val palette = listOf(
            Color(0xFF3F51B5),
            Color(0xFF00897B),
            Color(0xFFE65100),
            Color(0xFF8E24AA),
            Color(0xFFC62828),
            Color(0xFF1565C0)
        )

        val genelOrtNet = allDenemeler.map { it.toplamNet }.takeIf { it.isNotEmpty() }?.average() ?: 75.0

        yayinMap.entries.mapIndexed { idx, (yayin, lists) ->
            val (denemeler, sorular) = lists
            val ortNet = if (denemeler.isNotEmpty()) denemeler.map { it.toplamNet }.average() else 0.0
            val cozulen = sorular.count { it.isSolved }
            val oran = if (sorular.isNotEmpty()) cozulen.toFloat() / sorular.size.toFloat() else 0f
            val zorluk = when {
                denemeler.isNotEmpty() && ortNet < genelOrtNet - 5.0 -> "Zorlayıcı Seçici"
                denemeler.isNotEmpty() && ortNet > genelOrtNet + 5.0 -> "Yüksek Moral / İdeal"
                oran < 0.45f && sorular.size >= 3 -> "Yüksek Zorluk"
                else -> "ÖSYM Dengesinde"
            }

            YayineviAnalizSatiri(
                yayinAdi = yayin,
                denemeSayisi = denemeler.size,
                ortalamaNet = Math.round(ortNet * 10.0) / 10.0,
                soruSayisi = sorular.size,
                cozulmeOrani = oran,
                zorlukEtiketi = zorluk,
                renk = palette[idx % palette.size]
            )
        }.sortedWith(
            compareByDescending<YayineviAnalizSatiri> { it.denemeSayisi }
                .thenByDescending { it.soruSayisi }
        ).take(6)
    }

    if (analizListesi.isEmpty()) return

    val maxNet = remember(analizListesi) {
        (analizListesi.maxOfOrNull { it.ortalamaNet }?.toFloat() ?: 100f).coerceAtLeast(40f)
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Business,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = "🏢 Yayınevi Performans & Zorluk Kıyaslaması",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Farklı yayınlardaki deneme net ortalaman ve soru eritme başarın",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            analizListesi.forEach { item ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(item.renk)
                                )
                                Text(
                                    text = item.yayinAdi,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Surface(
                                    color = item.renk.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = item.zorlukEtiketi,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = item.renk,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            if (item.denemeSayisi > 0) {
                                Text(
                                    text = "Ort: ${item.ortalamaNet} Net",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Text(
                                    text = "%${(item.cozulmeOrani * 100).toInt()} Çözüldü",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = SuccessGreen
                                )
                            }
                        }

                        val barProgress = if (item.denemeSayisi > 0) {
                            (item.ortalamaNet.toFloat() / maxNet).coerceIn(0.08f, 1f)
                        } else {
                            item.cozulmeOrani.coerceIn(0.08f, 1f)
                        }

                        LinearProgressIndicator(
                            progress = { barProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(7.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = item.renk,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )

                        Text(
                            text = buildString {
                                if (item.denemeSayisi > 0) append("${item.denemeSayisi} Deneme Sınavı")
                                if (item.denemeSayisi > 0 && item.soruSayisi > 0) append(" • ")
                                if (item.soruSayisi > 0) {
                                    append("${item.soruSayisi} Arşiv Sorusu (%${(item.cozulmeOrani * 100).toInt()} Çözüm)")
                                }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun tahminEtYayinevi(baslik: String): String {
    val lower = baslik.lowercase(Locale("tr"))
    return when {
        "3d" in lower -> "3D Yayınları"
        "özdebir" in lower || "ozdebir" in lower -> "Özdebir"
        "bilgi sarmal" in lower -> "Bilgi Sarmal"
        "apotemi" in lower -> "Apotemi"
        "orijinal" in lower -> "Orijinal"
        "toprak" in lower -> "Toprak"
        "töder" in lower || "toder" in lower -> "Töder"
        "limit" in lower -> "Limit"
        "krallar" in lower -> "Krallar Karması"
        "345" in lower || "üçdörtbeş" in lower -> "345 Yayınları"
        else -> ""
    }
}

/**
 * 2. Ebbinghaus Unutma Eğrisi & FSRS Hafıza Kalıcılığı Grafiği
 */
@Composable
fun FsrsMemoryRetentionCard(
    allQuestions: List<Soru>,
    onStartDueTest: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (allQuestions.isEmpty()) return

    val avgStability = remember(allQuestions) {
        val stabilities = allQuestions.map { q ->
            q.fsrsStability?.takeIf { it > 0.1 } ?: when (q.reviewStage) {
                0 -> 1.5
                1 -> 3.5
                2 -> 7.0
                3 -> 14.0
                4 -> 25.0
                else -> 45.0
            }
        }
        (stabilities.average()).coerceIn(1.2, 60.0)
    }

    val stageCounts = remember(allQuestions) {
        val kisaSureli = allQuestions.count { it.reviewStage <= 1 }
        val pekisen = allQuestions.count { it.reviewStage in 2..3 }
        val kalici = allQuestions.count { it.reviewStage >= 4 }
        Triple(kisaSureli, pekisen, kalici)
    }

    val retentionAtDay7 = remember(avgStability) {
        (exp(-7.0 / avgStability) * 100.0).toInt().coerceIn(5, 99)
    }
    val retentionAtDay30 = remember(avgStability) {
        (exp(-30.0 / avgStability) * 100.0).toInt().coerceIn(2, 99)
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Tertiary.copy(alpha = 0.15f),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            tint = Tertiary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "🧠 Ebbinghaus Unutma Eğrisi & FSRS Bellek Analizi",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Ortalama Bellek Sağlamlığı (S): ${String.format(Locale.US, "%.1f", avgStability)} Gün",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 30 Günlük Üstel R(t) = e^(-t/S) Eğrisi Canvas
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "30 Günlük Hatırlama Olasılığı R(t) = e^(-t/S)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "7.Gün: %$retentionAtDay7 • 30.Gün: %$retentionAtDay30",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Tertiary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    EbbinghausCurveCanvas(
                        stabilityDays = avgStability.toFloat(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(125.dp)
                    )
                }
            }

            // Bellek Kademeleri (Kısa Süreli -> Pekişen -> Kalıcı Hafıza)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MemoryStagePill(
                    title = "Kısa Süreli",
                    subtitle = "Aşama 0-1",
                    count = stageCounts.first,
                    color = WarningOrange,
                    modifier = Modifier.weight(1f)
                )
                MemoryStagePill(
                    title = "Pekişen",
                    subtitle = "Aşama 2-3",
                    count = stageCounts.second,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                MemoryStagePill(
                    title = "Kalıcı Hafıza",
                    subtitle = "Aşama 4-5",
                    count = stageCounts.third,
                    color = SuccessGreen,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun EbbinghausCurveCanvas(
    stabilityDays: Float,
    modifier: Modifier = Modifier
) {
    val curveColor = Tertiary
    val idealBoostColor = SuccessGreen
    val thresholdColor = ErrorRed

    val currentPath = remember { Path() }
    val boostPath = remember { Path() }
    val fillPath = remember { Path() }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val padL = 12.dp.toPx()
        val padR = 12.dp.toPx()
        val padT = 10.dp.toPx()
        val padB = 14.dp.toPx()

        val plotW = w - padL - padR
        val plotH = h - padT - padB
        if (plotW <= 0f || plotH <= 0f) return@Canvas

        // %80 Kritik Tekrar Eşiği Çizgisi
        val y80 = padT + plotH * (1f - 0.80f)
        drawLine(
            color = thresholdColor.copy(alpha = 0.4f),
            start = Offset(padL, y80),
            end = Offset(w - padR, y80),
            strokeWidth = 1.2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
        )

        // Mevcut S ile R(t) = exp(-t / S) eğrisi (t = 0..30 gün)
        val steps = 30
        currentPath.reset()
        boostPath.reset()
        fillPath.reset()
        val boostedStability = (stabilityDays * 2.2f).coerceAtLeast(7f)

        for (day in 0..steps) {
            val x = padL + (day.toFloat() / steps.toFloat()) * plotW
            val rCurrent = exp(-day.toFloat() / stabilityDays).coerceIn(0f, 1f)
            val rBoost = exp(-day.toFloat() / boostedStability).coerceIn(0f, 1f)

            val yCur = padT + plotH * (1f - rCurrent)
            val yBst = padT + plotH * (1f - rBoost)

            if (day == 0) {
                currentPath.moveTo(x, yCur)
                boostPath.moveTo(x, yBst)
            } else {
                currentPath.lineTo(x, yCur)
                boostPath.lineTo(x, yBst)
            }
        }

        fillPath.addPath(currentPath)
        fillPath.lineTo(padL + plotW, padT + plotH)
        fillPath.lineTo(padL, padT + plotH)
        fillPath.close()

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(curveColor.copy(alpha = 0.28f), curveColor.copy(alpha = 0.02f)),
                startY = padT,
                endY = padT + plotH
            )
        )

        // Bir sonraki tekrar sonrası eğri (Yeşil kesikli)
        drawPath(
            path = boostPath,
            color = idealBoostColor.copy(alpha = 0.65f),
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
            )
        )

        // Mevcut unutma eğrisi
        drawPath(
            path = currentPath,
            color = curveColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun MemoryStagePill(
    title: String,
    subtitle: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = color.copy(alpha = 0.09f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$count Soru",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 3. YKS Hedef Net & Puan Simülatörü (Altın Fırsat Konuları ile)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun YksHedefSimulatorCard(
    allDenemeler: List<DenemeSinaviEntity>,
    allQuestions: List<Soru>,
    modifier: Modifier = Modifier,
    onStartTargetedTest: (examType: String, subjectId: String, topic: String) -> Unit = { _, _, _ -> }
) {
    var selectedExam by remember { mutableStateOf("TYT") }
    var targetNet by remember(selectedExam) {
        mutableFloatStateOf(if (selectedExam == "TYT") 95f else 65f)
    }

    val maxPossibleNet = if (selectedExam == "TYT") 120f else 80f

    val currentAvgNet = remember(allDenemeler, selectedExam) {
        val matching = allDenemeler.filter { it.sinavTuru.equals(selectedExam, ignoreCase = true) }
        if (matching.isNotEmpty()) {
            matching.take(5).map { it.toplamNet }.average().toFloat()
        } else if (allDenemeler.isNotEmpty()) {
            allDenemeler.take(5).map { it.toplamNet }.average().toFloat()
        } else {
            0f
        }
    }

    val netGap = (targetNet - currentAvgNet).coerceAtLeast(0f)

    // En hızlı net getirecek "Altın Fırsat" konuları (çözülmemiş yanlış sayısı yüksek konular)
    val goldenTopics = remember(allQuestions, selectedExam) {
        allQuestions
            .filter { it.examType.equals(selectedExam, ignoreCase = true) && !it.isSolved && it.topic.isNotBlank() }
            .ifEmpty { allQuestions.filter { !it.isSolved && it.topic.isNotBlank() } }
            .groupBy { "${it.examType}|${it.subjectId}|${it.topic.trim()}" }
            .map { (key, list) ->
                val parts = key.split("|")
                val ex = parts.getOrNull(0) ?: selectedExam
                val subj = parts.getOrNull(1) ?: "matematik"
                val top = parts.getOrNull(2) ?: "Genel"
                val potentialNetGain = Math.round(list.size * 1.25 * 100.0) / 100.0
                Quadruple(ex, subj, top, potentialNetGain to list.size)
            }
            .sortedByDescending { it.fourth.first }
            .take(3)
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.EmojiEvents,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "🎯 YKS Hedef Net & Fırsat Simülatörü",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Hedef netine ulaşmak için en hızlı kapatabileceğin açıklar",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("TYT", "AYT").forEach { tur ->
                        FilterChip(
                            selected = selectedExam == tur,
                            onClick = { selectedExam = tur },
                            label = { Text(tur, fontSize = 12.sp) }
                        )
                    }
                }
            }

            // Hedef Net Slider
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Mevcut Ort: ${String.format(Locale.US, "%.1f", currentAvgNet)} Net",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Hedef: ${targetNet.toInt()} Net",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Slider(
                        value = targetNet,
                        onValueChange = { targetNet = it },
                        valueRange = 30f..maxPossibleNet,
                        steps = 17
                    )

                    Text(
                        text = if (netGap <= 0.1f && currentAvgNet > 0f) {
                            "🎉 Tebrikler! Son deneme ortalaman belirlediğin hedefin üzerinde!"
                        } else {
                            "🚀 Hedefe ulaşmak için +${String.format(Locale.US, "%.1f", netGap)} Net artış gerekiyor."
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (netGap <= 0.1f && currentAvgNet > 0f) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (goldenTopics.isNotEmpty()) {
                Text(
                    text = "⚡ En Hızlı Net Getirecek 3 Altın Fırsat Konusu:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                goldenTopics.forEach { (ex, subj, top, gainInfo) ->
                    val (gainNet, qCount) = gainInfo
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = top,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${DersTaksonomisi.getDersAdi(ex, subj)} • $qCount Çözülmemiş Yanlış",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    color = SuccessGreen.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "+${String.format(Locale.US, "%.2f", gainNet)} Net Potansiyeli",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = SuccessGreen,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }

                                Button(
                                    onClick = { onStartTargetedTest(ex, subj, top) },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("Çöz", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 4. Bilişsel Hata Kök Neden & "Bedava Net" Geri Kazanım Kartı
 */
@Composable
fun BilisselHataNetEtkisiCard(
    allQuestions: List<Soru>,
    modifier: Modifier = Modifier
) {
    if (allQuestions.isEmpty()) return

    val analiz = remember(allQuestions) {
        val dikkatVeIslem = allQuestions.count {
            it.mistakeType == HataTipi.DIKKAT_HATASI ||
                it.mistakeType == HataTipi.ISLEM_HATASI ||
                it.mistakeType == HataTipi.SORU_KOKU
        }
        val bilgiVeKonu = allQuestions.count {
            it.mistakeType == HataTipi.BILGI_EKSIKLIGI ||
                it.mistakeType == HataTipi.KONU_EKSIGI ||
                it.mistakeType == HataTipi.FORMUL_HATASI
        }
        val sureVeStrateji = allQuestions.count {
            it.mistakeType == HataTipi.SURE_YETMEDI ||
                it.mistakeType == HataTipi.SORUYU_ANLAMAMA ||
                it.mistakeType == HataTipi.DIGER
        }
        Triple(dikkatVeIslem, bilgiVeKonu, sureVeStrateji)
    }

    val toplamEtiketli = analiz.first + analiz.second + analiz.third
    if (toplamEtiketli == 0) return

    val bedavaNetKaybi = Math.round(analiz.first * 1.25 * 100.0) / 100.0
    val bilgiNetKaybi = Math.round(analiz.second * 1.25 * 100.0) / 100.0

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = WarningOrange.copy(alpha = 0.15f),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.AutoGraph,
                            contentDescription = null,
                            tint = WarningOrange,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = "⚡ Bilişsel Hata Kök Nedeni & Bedava Net Analizi",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Bildiğin halde dikkat/işlem yüzünden kaçan netlerin dağılımı",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = WarningOrange.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, WarningOrange.copy(alpha = 0.35f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "🎁 Geri Kazanılabilir Net",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = WarningOrange
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "+${String.format(Locale.US, "%.2f", bedavaNetKaybi)} Net",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = WarningOrange
                        )
                        Text(
                            text = "${analiz.first} soru (Dikkat / İşlem / Kök)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    color = ErrorRed.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.35f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "📚 Akademik Konu Kaybı",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = ErrorRed
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "-${String.format(Locale.US, "%.2f", bilgiNetKaybi)} Net",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = ErrorRed
                        )
                        Text(
                            text = "${analiz.second} soru (Bilgi / Formül / Konu)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
