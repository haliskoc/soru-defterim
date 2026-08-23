package com.sorudefteri.ui.istatistik

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sorudefteri.domain.model.DersSoruSayisi
import com.sorudefteri.domain.model.GunlukAktivite
import com.sorudefteri.domain.model.HataTipiDagilimi
import com.sorudefteri.ui.ortak.EmptyState
import com.sorudefteri.ui.theme.ErrorRed
import com.sorudefteri.ui.theme.SuccessGreen
import com.sorudefteri.ui.theme.Tertiary
import com.sorudefteri.ui.theme.WarningOrange
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IstatistikScreen(
    onNavigateToAdd: () -> Unit = {},
    viewModel: IstatistikViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "İstatistikler",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.totalCount == 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    icon = Icons.Outlined.BarChart,
                    title = "Henüz İstatistik Yok",
                    message = "Çözemediğin soruları ekledikçe ders dağılımı, hata analizi ve haftalık çalışma aktiviteni burada görebilirsin.",
                    actionButtonText = "İlk Soruyu Ekle",
                    onActionClick = onNavigateToAdd
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // 1) Özet KPI Kartları (2x2 Grid)
                KpiGridSection(
                    totalCount = uiState.totalCount,
                    solvedCount = uiState.solvedCount,
                    unsolvedCount = uiState.unsolvedCount,
                    successRate = uiState.successRate
                )

                // 2) Ders Dağılımı Grafiği
                if (uiState.subjectStats.isNotEmpty()) {
                    DersDagilimiCard(subjectStats = uiState.subjectStats)
                }

                // 3) Hata Tipi Dağılımı Grafiği
                if (uiState.mistakeStats.isNotEmpty()) {
                    HataTipiDagilimiCard(mistakeStats = uiState.mistakeStats)
                }

                // 4) Haftalık Aktivite Grafiği (Son 7 Gün)
                if (uiState.weeklyActivity.isNotEmpty()) {
                    HaftalikAktiviteCard(weeklyActivity = uiState.weeklyActivity)
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/**
 * 2x2 Özet KPI Kartları Bölümü.
 */
@Composable
private fun KpiGridSection(
    totalCount: Int,
    solvedCount: Int,
    unsolvedCount: Int,
    successRate: Float
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            KpiCard(
                title = "Toplam Soru",
                value = totalCount.toString(),
                icon = Icons.Default.AutoStories,
                tintColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )

            KpiCard(
                title = "Çözülen",
                value = solvedCount.toString(),
                icon = Icons.Default.CheckCircle,
                tintColor = SuccessGreen,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            KpiCard(
                title = "Çözülmeyen",
                value = unsolvedCount.toString(),
                icon = Icons.Default.PendingActions,
                tintColor = WarningOrange,
                modifier = Modifier.weight(1f)
            )

            val formattedRate = String.format(Locale.getDefault(), "%%%.1f", successRate)
            KpiCard(
                title = "Başarı Oranı",
                value = formattedRate,
                icon = Icons.Default.TrendingUp,
                tintColor = Tertiary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun KpiCard(
    title: String,
    value: String,
    icon: ImageVector,
    tintColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = tintColor.copy(alpha = 0.08f)
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(tintColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tintColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Ders bazlı yatay çubuk grafik kartı.
 */
@Composable
private fun DersDagilimiCard(
    subjectStats: List<DersSoruSayisi>
) {
    val animationProgress = remember { Animatable(0f) }
    LaunchedEffect(subjectStats) {
        animationProgress.snapTo(0f)
        animationProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
        )
    }

    val maxCount = remember(subjectStats) {
        subjectStats.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
    }

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
                Text(
                    text = "Ders Dağılımı",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${subjectStats.size} Ders",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                subjectStats.forEach { stat ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stat.subjectName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Text(
                                text = "${stat.count} soru (${stat.solvedCount} çözüldü)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Custom Canvas Bar
                        val barColor = if (stat.color != Color.Unspecified) stat.color else MaterialTheme.colorScheme.primary
                        val solvedBarColor = SuccessGreen

                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(14.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            val totalWidth = size.width
                            val barRatio = (stat.count.toFloat() / maxCount.toFloat()) * animationProgress.value
                            val filledWidth = totalWidth * barRatio

                            // Toplam çubuk (Unsolved / Total bar)
                            drawRoundRect(
                                color = barColor.copy(alpha = 0.4f),
                                size = Size(filledWidth, size.height),
                                cornerRadius = CornerRadius(7.dp.toPx(), 7.dp.toPx())
                            )

                            // Çözülen kısmı (Solved segment)
                            if (stat.count > 0 && stat.solvedCount > 0) {
                                val solvedRatio = (stat.solvedCount.toFloat() / stat.count.toFloat())
                                val solvedWidth = filledWidth * solvedRatio
                                drawRoundRect(
                                    color = barColor,
                                    size = Size(solvedWidth, size.height),
                                    cornerRadius = CornerRadius(7.dp.toPx(), 7.dp.toPx())
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Hata Tipi Dağılımı Donut Grafiği Kartı.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HataTipiDagilimiCard(
    mistakeStats: List<HataTipiDagilimi>
) {
    val animationProgress = remember { Animatable(0f) }
    LaunchedEffect(mistakeStats) {
        animationProgress.snapTo(0f)
        animationProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing)
        )
    }

    val totalMistakes = remember(mistakeStats) { mistakeStats.sumOf { it.count } }

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
            Text(
                text = "Hata Tipi Dağılımı",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Donut Chart Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier.size(170.dp)
                ) {
                    val strokeWidth = 26.dp.toPx()
                    var startAngle = -90f

                    mistakeStats.forEach { stat ->
                        val sweepAngle = (stat.percentage / 100f) * 360f * animationProgress.value
                        val sliceColor = if (stat.color != Color.Unspecified) stat.color else Color.Gray

                        drawArc(
                            color = sliceColor,
                            startAngle = startAngle,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                        startAngle += (stat.percentage / 100f) * 360f
                    }
                }

                // Donut Center Text
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = totalMistakes.toString(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Toplam Hata",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Legend / Açıklamalar
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                mistakeStats.forEach { stat ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (stat.color != Color.Unspecified) stat.color else Color.Gray)
                        )
                        Text(
                            text = "${stat.displayName}: ${stat.count} (%${String.format(Locale.getDefault(), "%.0f", stat.percentage)})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

/**
 * Son 7 Günlük Aktivite Çubuk Grafiği Kartı.
 */
@Composable
private fun HaftalikAktiviteCard(
    weeklyActivity: List<GunlukAktivite>
) {
    val animationProgress = remember { Animatable(0f) }
    LaunchedEffect(weeklyActivity) {
        animationProgress.snapTo(0f)
        animationProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
        )
    }

    val maxCount = remember(weeklyActivity) {
        weeklyActivity.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
    }

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
                Text(
                    text = "Son 7 Günlük Aktivite",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Eklenen Sorular",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }

            // Weekly Bars Canvas
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                val primaryColor = MaterialTheme.colorScheme.primary
                val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
                val textColor = MaterialTheme.colorScheme.onSurfaceVariant

                weeklyActivity.forEach { day ->
                    val isToday = day == weeklyActivity.lastOrNull()
                    val barHeightFraction = (day.count.toFloat() / maxCount.toFloat()) * animationProgress.value

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier.width(36.dp)
                    ) {
                        // Soru sayısı etiketi (varsa)
                        if (day.count > 0) {
                            Text(
                                text = day.count.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isToday) primaryColor else textColor
                            )
                        } else {
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Çubuk Canvas
                        Canvas(
                            modifier = Modifier
                                .width(18.dp)
                                .height(100.dp)
                        ) {
                            val barHeight = (size.height * barHeightFraction).coerceAtLeast(6.dp.toPx())
                            val topOffset = size.height - barHeight

                            // Arka plan kılavuz çubuk
                            drawRoundRect(
                                color = surfaceVariant,
                                topLeft = Offset(0f, 0f),
                                size = Size(size.width, size.height),
                                cornerRadius = CornerRadius(9.dp.toPx(), 9.dp.toPx())
                            )

                            // Dolu çubuk
                            if (day.count > 0) {
                                drawRoundRect(
                                    color = if (isToday) primaryColor else primaryColor.copy(alpha = 0.7f),
                                    topLeft = Offset(0f, topOffset),
                                    size = Size(size.width, barHeight),
                                    cornerRadius = CornerRadius(9.dp.toPx(), 9.dp.toPx())
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Gün Adı Etiketi
                        Text(
                            text = day.dayName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (isToday) primaryColor else textColor
                        )
                    }
                }
            }
        }
    }
}
