package com.sorudefteri.ui.istatistik.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import com.sorudefteri.data.local.entity.toEntity
import com.sorudefteri.domain.DersTaksonomisi
import com.sorudefteri.domain.model.DenemeSinavi
import com.sorudefteri.domain.model.Soru
import com.sorudefteri.ui.theme.ErrorRed
import com.sorudefteri.ui.theme.SuccessGreen
import com.sorudefteri.ui.theme.WarningOrange
import kotlinx.serialization.json.Json
import java.util.Locale

private data class YksKonuKayipAnalizi(
    val examType: String,
    val subjectId: String,
    val dersAdi: String,
    val topic: String,
    val yanlisSayisi: Int,
    val denemeKaynakliSayi: Int,
    val cozulmusSayisi: Int,
    val netKaybi: Double
) {
    val cozulmeOrani: Float
        get() = if (yanlisSayisi > 0) cozulmusSayisi.toFloat() / yanlisSayisi.toFloat() else 0f
}

/**
 * YKS Konu & Kazanım Bazlı Net Kayıp Matrisi ve Kronolojik Çoklu-Ders Net Trendi Kartı.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DenemeDetayliAnalizCard(
    allDenemeler: List<DenemeSinaviEntity>,
    allQuestions: List<Soru>,
    modifier: Modifier = Modifier,
    onStartTargetedTest: (examType: String, subjectId: String, topic: String) -> Unit = { _, _, _ -> }
) {
    var selectedExamFilter by remember { mutableStateOf("TÜMÜ") }
    var onlyDenemeLinkedQuestions by remember { mutableStateOf(false) }

    val hasDenemeLinkedQuestions = remember(allQuestions) {
        allQuestions.any { it.denemeId != null || !it.kaynakDeneme.isNullOrBlank() }
    }

    val filteredDenemeler = remember(allDenemeler, selectedExamFilter) {
        val list = if (selectedExamFilter == "TÜMÜ") {
            allDenemeler
        } else {
            allDenemeler.filter { it.sinavTuru.equals(selectedExamFilter, ignoreCase = true) }
        }
        list.sortedBy { it.tarih }
    }

    val konuKayipListesi = remember(allQuestions, selectedExamFilter, onlyDenemeLinkedQuestions) {
        val baseQuestions = allQuestions.filter { s ->
            val examMatch = selectedExamFilter == "TÜMÜ" || s.examType.equals(selectedExamFilter, ignoreCase = true)
            val denemeMatch = !onlyDenemeLinkedQuestions || (s.denemeId != null || !s.kaynakDeneme.isNullOrBlank())
            examMatch && denemeMatch && s.topic.isNotBlank()
        }.ifEmpty {
            allQuestions.filter { s ->
                (selectedExamFilter == "TÜMÜ" || s.examType.equals(selectedExamFilter, ignoreCase = true)) && s.topic.isNotBlank()
            }
        }

        baseQuestions
            .groupBy { "${it.examType}|${it.subjectId}|${it.topic.trim()}" }
            .map { (key, list) ->
                val parts = key.split("|")
                val examType = parts.getOrNull(0) ?: "TYT"
                val subjectId = parts.getOrNull(1) ?: "matematik"
                val topicName = parts.getOrNull(2) ?: "Genel"
                val denemeCount = list.count { it.denemeId != null || !it.kaynakDeneme.isNullOrBlank() }
                val penaltyFactor = if (examType.equals("LGS", ignoreCase = true)) 1.33 else 1.25
                val kayip = Math.round(list.size * penaltyFactor * 100.0) / 100.0

                YksKonuKayipAnalizi(
                    examType = examType,
                    subjectId = subjectId,
                    dersAdi = DersTaksonomisi.getDersAdi(examType, subjectId),
                    topic = topicName,
                    yanlisSayisi = list.size,
                    denemeKaynakliSayi = denemeCount,
                    cozulmusSayisi = list.count { it.isSolved },
                    netKaybi = kayip
                )
            }
            .sortedWith(
                compareByDescending<YksKonuKayipAnalizi> { it.netKaybi }
                    .thenBy { it.cozulmeOrani }
            )
            .take(8)
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
            // Başlık & Sınav Filtresi
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
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Analytics,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "📊 YKS Konu Bazlı Net Kayıp & Trend Matrisi",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Denemelerde en çok net kaybettiren kazanımlar ve ivme analizi",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Filtre Çipleri (TÜMÜ / TYT / AYT + Sadece Deneme Kaynaklı)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("TÜMÜ", "TYT", "AYT").forEach { filter ->
                    FilterChip(
                        selected = selectedExamFilter == filter,
                        onClick = { selectedExamFilter = filter },
                        label = { Text(filter, fontSize = 12.sp) }
                    )
                }
                if (hasDenemeLinkedQuestions) {
                    FilterChip(
                        selected = onlyDenemeLinkedQuestions,
                        onClick = { onlyDenemeLinkedQuestions = !onlyDenemeLinkedQuestions },
                        label = { Text("📌 Sadece Deneme Soruları", fontSize = 12.sp) }
                    )
                }
            }

            // 1. KRONOLOJİK NET İVMESİ & BEZIER TREND ÖZETİ
            if (filteredDenemeler.isNotEmpty()) {
                val avgNet = remember(filteredDenemeler) {
                    filteredDenemeler.map { it.toplamNet }.average()
                }
                val maxNet = remember(filteredDenemeler) {
                    filteredDenemeler.maxOfOrNull { it.toplamNet } ?: 0.0
                }
                val netIvme = remember(filteredDenemeler) {
                    if (filteredDenemeler.size >= 2) {
                        val first = filteredDenemeler.first().toplamNet
                        val last = filteredDenemeler.last().toplamNet
                        (last - first) / (filteredDenemeler.size - 1).coerceAtLeast(1)
                    } else 0.0
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TrendingUp,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Kronolojik Net Trendi (${filteredDenemeler.size} Deneme)",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            val ivmeSign = if (netIvme >= 0) "+" else ""
                            Surface(
                                color = if (netIvme >= 0) SuccessGreen.copy(alpha = 0.15f) else WarningOrange.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "İvme: $ivmeSign${String.format(Locale.US, "%.1f", netIvme)} Net/Deneme",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (netIvme >= 0) SuccessGreen else WarningOrange,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MiniTrendStatBox(
                                label = "Ortalama Net",
                                value = String.format(Locale.US, "%.1f", avgNet),
                                modifier = Modifier.weight(1f)
                            )
                            MiniTrendStatBox(
                                label = "Rekor Net",
                                value = "🏆 ${String.format(Locale.US, "%.1f", maxNet)}",
                                modifier = Modifier.weight(1f)
                            )
                            MiniTrendStatBox(
                                label = "Son Deneme",
                                value = String.format(Locale.US, "%.1f", filteredDenemeler.last().toplamNet),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (filteredDenemeler.size >= 2) {
                            BezierNetTrendCanvas(
                                denemeler = filteredDenemeler.takeLast(12),
                                targetNet = if (selectedExamFilter == "AYT") 65f else 95f,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp)
                            )
                        }
                    }
                }
            }

            // 2. YKS KONU BAZLI NET KAYIP LİSTESİ
            if (konuKayipListesi.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Seçili filtrede henüz konu bazlı yanlış soru verisi bulunmuyor. Deneme yanlışlarını ekledikçe en çok net kaybettiğin konular burada sıralanacak.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            } else {
                Text(
                    text = "En Çok Puan Kaybettiren Kritik YKS Konuları:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    konuKayipListesi.forEachIndexed { index, item ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                1.dp,
                                if (index < 3) ErrorRed.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            ),
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
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = if (index < 2) "🔴" else if (index < 5) "🟠" else "🟡",
                                                fontSize = 12.sp
                                            )
                                            Text(
                                                text = item.topic,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Text(
                                            text = buildString {
                                                append("${item.examType} ${item.dersAdi} • ${item.yanlisSayisi} Yanlış/Boş")
                                                if (item.denemeKaynakliSayi > 0) {
                                                    append(" (${item.denemeKaynakliSayi} denemede)")
                                                }
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.errorContainer,
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = "-${String.format(Locale.US, "%.2f", item.netKaybi)} Net",
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                onStartTargetedTest(item.examType, item.subjectId, item.topic)
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary
                                            ),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text("Konu Testi", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                // Çözülme ilerleme çubuğu
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    LinearProgressIndicator(
                                        progress = { item.cozulmeOrani },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(5.dp)
                                            .clip(RoundedCornerShape(3.dp)),
                                        color = if (item.cozulmeOrani >= 0.7f) SuccessGreen else MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    Text(
                                        text = "${item.cozulmusSayisi}/${item.yanlisSayisi} telafi edildi",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
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
 * Mimari dokümandaki alternatif çağrı imzası ile tam uyumluluk:
 */
@Composable
fun DenemeDetayliAnalizCard(
    denemeler: List<DenemeSinavi>,
    denemeSorulari: List<Soru>,
    onKonuSecildi: (String) -> Unit
) {
    val json = remember { Json { ignoreUnknownKeys = true } }
    val entities = remember(denemeler) { denemeler.map { it.toEntity(json) } }
    DenemeDetayliAnalizCard(
        allDenemeler = entities,
        allQuestions = denemeSorulari,
        onStartTargetedTest = { _, _, topic -> onKonuSecildi(topic) }
    )
}

@Composable
private fun MiniTrendStatBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BezierNetTrendCanvas(
    denemeler: List<DenemeSinaviEntity>,
    targetNet: Float,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val targetColor = SuccessGreen

    val nets = remember(denemeler) { denemeler.map { it.toplamNet.toFloat() } }
    val minNet = remember(nets, targetNet) {
        ((nets.minOrNull() ?: 0f) - 5f).coerceAtLeast(0f)
    }
    val maxNet = remember(nets, targetNet) {
        maxOf(nets.maxOrNull() ?: 100f, targetNet) + 5f
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val padLeft = 16.dp.toPx()
        val padRight = 16.dp.toPx()
        val padTop = 12.dp.toPx()
        val padBottom = 14.dp.toPx()

        val plotW = w - padLeft - padRight
        val plotH = h - padTop - padBottom
        if (nets.size < 2 || plotW <= 0f || plotH <= 0f) return@Canvas

        val range = (maxNet - minNet).coerceAtLeast(1f)
        val stepX = plotW / (nets.size - 1)

        // Hedef Net Kesikli Çizgisi
        val targetRatio = ((targetNet - minNet) / range).coerceIn(0f, 1f)
        val targetY = padTop + plotH - (targetRatio * plotH)
        drawLine(
            color = targetColor.copy(alpha = 0.55f),
            start = Offset(padLeft, targetY),
            end = Offset(w - padRight, targetY),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
        )

        val points = nets.mapIndexed { idx, net ->
            val x = padLeft + idx * stepX
            val r = ((net - minNet) / range).coerceIn(0f, 1f)
            val y = padTop + plotH - (r * plotH)
            Offset(x, y)
        }

        // Yumuşak Bezier Eğrisi
        val bezierPath = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 0 until points.size - 1) {
                val p0 = points[i]
                val p1 = points[i + 1]
                val controlX1 = (p0.x + p1.x) / 2f
                val controlX2 = (p0.x + p1.x) / 2f
                cubicTo(controlX1, p0.y, controlX2, p1.y, p1.x, p1.y)
            }
        }

        val fillPath = Path().apply {
            addPath(bezierPath)
            lineTo(points.last().x, padTop + plotH)
            lineTo(points.first().x, padTop + plotH)
            close()
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(primaryColor.copy(alpha = 0.28f), primaryColor.copy(alpha = 0.02f)),
                startY = padTop,
                endY = padTop + plotH
            )
        )

        drawPath(
            path = bezierPath,
            color = primaryColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )

        points.forEach { pt ->
            drawCircle(color = Color.White, radius = 5.dp.toPx(), center = pt)
            drawCircle(color = primaryColor, radius = 3.5.dp.toPx(), center = pt)
        }
    }
}
