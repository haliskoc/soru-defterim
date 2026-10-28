package com.sorudefteri.ui.istatistik

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.LinearProgressIndicator
import com.sorudefteri.domain.model.Badge
import com.sorudefteri.domain.model.LevelInfo
import com.sorudefteri.domain.model.UserLevel
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.sorudefteri.ui.istatistik.components.StoryCardGenerator
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sorudefteri.data.local.entity.TestSonucEntity
import com.sorudefteri.domain.model.DersSoruSayisi
import com.sorudefteri.domain.model.GunlukAktivite
import com.sorudefteri.domain.model.HataTipiDagilimi
import com.sorudefteri.ui.ortak.EmptyState
import com.sorudefteri.ui.theme.AytBadgeColor
import com.sorudefteri.ui.theme.AytBadgeContainer
import com.sorudefteri.ui.theme.ErrorRed
import com.sorudefteri.ui.theme.SuccessGreen
import com.sorudefteri.ui.theme.Tertiary
import com.sorudefteri.ui.theme.TytBadgeColor
import com.sorudefteri.ui.theme.TytBadgeContainer
import com.sorudefteri.ui.theme.WarningOrange
import com.sorudefteri.ui.istatistik.components.AcilEylemMatrisiCard
import com.sorudefteri.ui.istatistik.components.BugunkuAkilliOnerilerCard
import com.sorudefteri.ui.istatistik.components.DenemeEkleDialog
import com.sorudefteri.ui.istatistik.components.DenemeTakipCard
import com.sorudefteri.ui.istatistik.components.DiagnosticAiInsightsCard
import com.sorudefteri.ui.istatistik.components.KonuIsiHaritasiCard
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IstatistikScreen(
    onNavigateToAdd: () -> Unit = {},
    onStartTargetedTest: () -> Unit = {},
    onStartDueTest: () -> Unit = onStartTargetedTest,
    viewModel: IstatistikViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    var showDenemeEkleDialog by remember { mutableStateOf(false) }

    // YENİ SADELEŞTİRME: uzun tek dikey akış yerine 4 net sekme.
    // Her sekme tek başına sade görünsün; ayrıntı yeni karşılaştırma kartı gibi
    // ek metriklerle içeride saklı.
    var selectedStatTab by rememberSaveable { mutableStateOf(0) }
    val statTabTitles = listOf("Özet", "Dersler", "Denemeler", "Kişisel")
    val statTabAltBasliks = listOf(
        "Bugünün önerileri ve haftalık trendin",
        "Konu ısı haritan ve dağılımlar",
        "Net gelişimin ve geçmiş testlerin",
        "Seviyen, rozetler ve hikaye kartın"
    )

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "İstatistikler",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = statTabAltBasliks.getOrElse(selectedStatTab) { "" },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) }
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
        } else if (uiState.isEmpty) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    icon = Icons.Outlined.BarChart,
                    title = "Henüz İstatistik Yok",
                    message = "Çözemediğin soruları ekledikçe veya deneme/test çözdükçe ders dağılımı, hata analizi ve net gelişimini burada görebilirsin.",
                    actionButtonText = "İlk Soruyu Ekle",
                    onActionClick = onNavigateToAdd
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // SEKME SEÇİCİ: sade üst bant (Özet / Dersler / Denemeler / Kişisel)
                TabRow(selectedTabIndex = selectedStatTab) {
                    statTabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedStatTab == index,
                            onClick = { selectedStatTab = index },
                            text = {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (selectedStatTab == index) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 900.dp)
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                    when (selectedStatTab) {
                        // ─────────────────────────────────────────────────
                        // SEKME 1 — ÖZET: kısa KPI'lar + haftalık karşılaştırma + bugün öneri + aktivite
                        // ─────────────────────────────────────────────────
                        0 -> {
                            if (uiState.totalCount > 0) {
                                KpiGridSection(
                                    totalCount = uiState.totalCount,
                                    solvedCount = uiState.solvedCount,
                                    unsolvedCount = uiState.unsolvedCount,
                                    successRate = uiState.successRate
                                )
                            }

                            // YENİ: son 7 gün ↔ önceki 7 gün detay karşılaştırması
                            HaftalikKarsilastirmaCard(
                                karsilastirma = uiState.haftalikKarsilastirma
                            )

                            BugunkuAkilliOnerilerCard(
                                dueCount = uiState.dueReviewCount,
                                allQuestions = uiState.allQuestions,
                                mistakeStats = uiState.mistakeStats,
                                recentTestResults = uiState.recentTestResults,
                                onStartDueTest = {
                                    viewModel.startDueReviewTest(
                                        onSuccess = onStartDueTest
                                    )
                                },
                                onStartTargetedTest = { examType, subjectId, topic ->
                                    viewModel.startTargetedTest(
                                        examType = examType,
                                        subjectId = subjectId,
                                        topic = topic,
                                        onSuccess = { onStartTargetedTest() }
                                    )
                                }
                            )

                            if (uiState.weeklyActivity.isNotEmpty()) {
                                HaftalikAktiviteCard(weeklyActivity = uiState.weeklyActivity)
                            }
                        }

                        // ─────────────────────────────────────────────────
                        // SEKME 2 — DERSLER & KONULAR: nereye odaklanmalı + harita + dağılımlar
                        // ─────────────────────────────────────────────────
                        1 -> {
                            if (uiState.allQuestions.isNotEmpty()) {
                                AcilEylemMatrisiCard(
                                    allQuestions = uiState.allQuestions,
                                    onStartTargetedTest = { examType, subjectId, topic ->
                                        viewModel.startTargetedTest(
                                            examType = examType,
                                            subjectId = subjectId,
                                            topic = topic,
                                            onSuccess = { onStartTargetedTest() }
                                        )
                                    }
                                )
                            }

                            KonuIsiHaritasiCard(
                                allQuestions = uiState.allQuestions,
                                konuKatalog = viewModel.konuKatalog,
                                onStartTestForTopic = { examType, subjectId, topic ->
                                    viewModel.startTargetedTest(
                                        examType = examType,
                                        subjectId = subjectId,
                                        topic = topic,
                                        onSuccess = { onStartTargetedTest() }
                                    )
                                }
                            )

                            if (uiState.subjectStats.isNotEmpty()) {
                                DersDagilimiCard(subjectStats = uiState.subjectStats)
                            }

                            if (uiState.mistakeStats.isNotEmpty()) {
                                HataTipiDagilimiCard(mistakeStats = uiState.mistakeStats)
                            }
                        }

                        // ─────────────────────────────────────────────────
                        // SEKME 3 — DENEMELER & TESTLER & AI TEŞHİS
                        // ─────────────────────────────────────────────────
                        2 -> {
                            DenemeTakipCard(
                                allDenemeler = uiState.allDenemeler,
                                onAddDeneme = { showDenemeEkleDialog = true },
                                onDeleteDeneme = { viewModel.deleteDeneme(it) }
                            )

                            if (uiState.recentTestResults.isNotEmpty()) {
                                SonTestlerSection(
                                    recentTests = uiState.recentTestResults,
                                    totalTestCount = uiState.totalTestCount,
                                    averageNet = uiState.averageNet
                                )
                            }

                            DiagnosticAiInsightsCard(
                                report = uiState.diagnosticReport,
                                isGenerating = uiState.isGeneratingDiagnostic,
                                error = uiState.diagnosticError,
                                onGenerateReport = { viewModel.generateDiagnosticReport() },
                                onClearReport = { viewModel.clearDiagnosticReport() }
                            )
                        }

                        // ─────────────────────────────────────────────────
                        // SEKME 4 — KİŞİSEL: seviye, rozetler, hikaye kartı
                        // ─────────────────────────────────────────────────
                        else -> {
                            LevelProgressCard(
                                levelInfo = uiState.levelInfo,
                                currentStreak = uiState.currentStreak,
                                streakShieldCount = uiState.streakShieldCount
                            )

                            BadgesShowcaseCard(
                                unlockedBadges = uiState.unlockedBadges
                            )

                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        try {
                                            val bitmap = withContext(Dispatchers.IO) {
                                                StoryCardGenerator.generateStoryBitmap(
                                                    context = context,
                                                    currentStreak = uiState.currentStreak,
                                                    successRate = uiState.successRate,
                                                    levelTitle = uiState.levelInfo.level.title,
                                                    levelNumber = uiState.levelInfo.level.levelNumber,
                                                    totalXp = uiState.userXp,
                                                    unlockedBadges = uiState.unlockedBadges,
                                                    studentTarget = "Hedef: Cerrahpaşa Tıp"
                                                )
                                            }
                                            StoryCardGenerator.shareToStory(context, bitmap)
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6A1B9A))
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("📱 Hikayede Paylaş (9:16)", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }

                    Spacer(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .height(24.dp)
                    )
                    }
                }
            }
        }
    }

    if (showDenemeEkleDialog) {
        DenemeEkleDialog(
            onDismiss = { showDenemeEkleDialog = false },
            onSave = { entity ->
                viewModel.insertDeneme(entity)
                showDenemeEkleDialog = false
            }
        )
    }
}

/**
 * YENİ DETAY KARTI (sade + detaylı istatistik yenilemesi):
 * Son 7 günü önceki 7 günle karşılaştırır. Küçük ama bilgilendirici:
 * eklenen soru, çözülen soru, çözülen test ve ortalama net satırları;
 * değişim yönü renkli ok ile gösterilir.
 */
@Composable
private fun HaftalikKarsilastirmaCard(
    karsilastirma: HaftalikKarsilastirma
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.TrendingUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Son 7 Gün ↔ Önceki 7 Gün",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Haftalık gelişim karşılaştırması",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HaftaKarsilastirmaSatiri(
                baslik = "Eklenen soru",
                buHaftaDeger = karsilastirma.buHaftaEklenenSoru.toString(),
                gecenHaftaDeger = karsilastirma.gecenHaftaEklenenSoru.toString()
            )
            HaftaKarsilastirmaSatiri(
                baslik = "Çözülen soru",
                buHaftaDeger = karsilastirma.buHaftaCozulenSoru.toString(),
                gecenHaftaDeger = karsilastirma.gecenHaftaCozulenSoru.toString()
            )
            HaftaKarsilastirmaSatiri(
                baslik = "Çözülen test",
                buHaftaDeger = karsilastirma.buHaftaTestSayisi.toString(),
                gecenHaftaDeger = karsilastirma.gecenHaftaTestSayisi.toString()
            )
            HaftaKarsilastirmaSatiri(
                baslik = "Ortalama net",
                buHaftaDeger = String.format(Locale.getDefault(), "%.1f", karsilastirma.buHaftaOrtNet),
                gecenHaftaDeger = String.format(Locale.getDefault(), "%.1f", karsilastirma.gecenHaftaOrtNet)
            )
        }
    }
}

/**
 * Karşılaştırma kartının tek satırı: metin sonda, önceki / bu hafta değerleri sağda.
 * Artış yeşil yukarı ok, düşüş turuncu aşağı ok, değişim yok ise gri çizgi ile işaretlenir.
 */
@Composable
private fun HaftaKarsilastirmaSatiri(
    baslik: String,
    buHaftaDeger: String,
    gecenHaftaDeger: String
) {
    val buHaftaSayi = buHaftaDeger.replace(',', '.').toFloatOrNull() ?: 0f
    val gecenHaftaSayi = gecenHaftaDeger.replace(',', '.').toFloatOrNull() ?: 0f
    val fark = buHaftaSayi - gecenHaftaSayi

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = baslik,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "Önceki: $gecenHaftaDeger",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 12.dp)
        )
        Text(
            text = buHaftaDeger,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = when {
                fark > 0.01f -> " ▲"
                fark < -0.01f -> " ▼"
                else -> " –"
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = when {
                fark > 0.01f -> SuccessGreen
                fark < -0.01f -> WarningOrange
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
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

            val formattedRate = String.format(Locale.getDefault(), "%.1f%%", successRate)
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

/**
 * Son Denemeler & Testler Bölümü (Faz 1)
 */
@Composable
private fun SonTestlerSection(
    recentTests: List<TestSonucEntity>,
    totalTestCount: Int,
    averageNet: Float
) {
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
            // Başlık Satırı
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.TrendingUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Geçmiş Deneme & Test Sonuçları",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Tüm çözülen testlerin net ve başarı dökümü",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = "$totalTestCount Test",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Özet KPI Kartları (Toplam Test & Ortalama Net)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Toplam Çözülen Test Kartı
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoStories,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Toplam Test",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$totalTestCount",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Ortalama Net Kartı
                val formattedAvgNet = String.format(Locale.getDefault(), "%.1f", averageNet)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(SuccessGreen.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = SuccessGreen,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Ortalama Net",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formattedAvgNet,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = SuccessGreen
                            )
                        }
                    }
                }
            }

            // Son Testler Listesi
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                recentTests.forEach { test ->
                    RecentTestItemCard(test = test)
                }
            }
        }
    }
}

/**
 * Tekil Deneme/Test Sonuç Satır Kartı
 */
@Composable
private fun RecentTestItemCard(test: TestSonucEntity) {
    val examType = test.examType?.trim() ?: "Genel"
    val isTyt = examType.equals("TYT", ignoreCase = true)
    val isAyt = examType.equals("AYT", ignoreCase = true)

    val badgeBg = when {
        isTyt -> TytBadgeContainer
        isAyt -> AytBadgeContainer
        else -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
    }
    val badgeFg = when {
        isTyt -> TytBadgeColor
        isAyt -> AytBadgeColor
        else -> MaterialTheme.colorScheme.secondary
    }
    val badgeLabel = when {
        isTyt -> "TYT"
        isAyt -> "AYT"
        examType.equals("TÜMÜ", ignoreCase = true) -> "Genel"
        else -> examType.ifBlank { "Genel" }
    }

    val dateFormatter = remember { SimpleDateFormat("d MMM yyyy, HH:mm", Locale("tr", "TR")) }
    val dateStr = remember(test.completedAt) {
        if (test.completedAt > 0) dateFormatter.format(Date(test.completedAt)) else ""
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Üst Satır: Sınav Tipi Rozeti, Tarih ve Vurgulu Net Skoru
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Sınav Tipi Rozeti
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = badgeBg
                    ) {
                        Text(
                            text = badgeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = badgeFg,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // Tarih
                    if (dateStr.isNotBlank()) {
                        Text(
                            text = dateStr,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Vurgulu Net Skoru
                val formattedNet = String.format(Locale.getDefault(), "%.1f", test.netCount)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "$formattedNet Net",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Alt Satır: Soru Sayısı, Doğru, Yanlış, Boş ve Başarı Oranı
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${test.totalQuestions} Soru",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${test.correctCount} D",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = SuccessGreen
                    )
                    Text(
                        text = "${test.wrongCount} Y",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = ErrorRed
                    )
                    Text(
                        text = "${test.emptyCount} B",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Başarı Oranı Rozeti
                Text(
                    text = "%${test.successRate}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = when {
                        test.successRate >= 70 -> SuccessGreen
                        test.successRate >= 40 -> WarningOrange
                        else -> ErrorRed
                    }
                )
            }
        }
    }
}

/**
 * Seviye & XP İlerleme Kartı (Faz 4.3)
 */
@Composable
private fun LevelProgressCard(
    levelInfo: LevelInfo,
    currentStreak: Int,
    streakShieldCount: Int
) {
    val level = levelInfo.level
    val levelEmoji = when (level) {
        UserLevel.CIRAK -> "🌱"
        UserLevel.HATA_AVCISI -> "🎯"
        UserLevel.ANALIZ_UZMANI -> "📊"
        UserLevel.DERECE_ADAYI -> "🎖️"
        UserLevel.SINAV_FATIHI -> "👑"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = levelEmoji, fontSize = 20.sp)
                        }
                    }
                    Column {
                        Text(
                            text = "Seviye ${level.levelNumber}: ${level.title}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${levelInfo.currentXp} XP",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Streak & Shield Mini Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, Color(0xFFFFB74D).copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "🔥 $currentStreak",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (streakShieldCount > 0) {
                            Text(
                                text = "🛡️",
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // Progress Bar
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LinearProgressIndicator(
                    progress = { levelInfo.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (level == UserLevel.SINAV_FATIHI) "Maksimum Seviye 🏆" else "Sonraki seviyeye ${levelInfo.xpRemainingForNextLevel} XP",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${(levelInfo.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * Başarım Rozetleri Vitrini (Faz 4.3)
 */
@Composable
private fun BadgesShowcaseCard(
    unlockedBadges: Set<String>
) {
    val allBadges = Badge.entries
    val unlockedCount = allBadges.count { unlockedBadges.contains(it.id) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = "🏆", fontSize = 20.sp)
                    Text(
                        text = "Başarım Rozetleri",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "$unlockedCount / ${allBadges.size}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            allBadges.chunked(2).forEach { rowBadges ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowBadges.forEach { badge ->
                        val isUnlocked = unlockedBadges.contains(badge.id)
                        SingleBadgeItem(
                            badge = badge,
                            isUnlocked = isUnlocked,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rowBadges.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SingleBadgeItem(
    badge: Badge,
    isUnlocked: Boolean,
    modifier: Modifier = Modifier
) {
    val badgeEmoji = when (badge) {
        Badge.ISLEM_HATASI_AVCISI -> "🧮"
        Badge.GEOMETRI_BUKUCU -> "📐"
        Badge.GECE_KUSU -> "🦉"
        Badge.HATA_GECIRMEZ -> "🛡️"
        Badge.HIZ_CANAVARI -> "⚡"
        Badge.YEDILI_SERI -> "🔥"
    }

    val alpha = if (isUnlocked) 1f else 0.45f
    val bgColor = if (isUnlocked) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
    val borderColor = if (isUnlocked) Color(0xFFFFB74D).copy(alpha = 0.7f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = badgeEmoji,
                    fontSize = 22.sp,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                if (isUnlocked) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = "+${badge.xpReward} XP",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                } else {
                    Text(
                        text = "🔒",
                        fontSize = 12.sp
                    )
                }
            }
            Text(
                text = badge.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = if (isUnlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
            )
            Text(
                text = badge.description,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                lineHeight = 12.sp,
                maxLines = 2
            )
        }
    }
}


