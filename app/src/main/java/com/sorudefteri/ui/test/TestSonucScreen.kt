package com.sorudefteri.ui.test

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.ZoomIn
import com.sorudefteri.ui.ortak.math.MathMarkdownText
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.sorudefteri.domain.DersTaksonomisi
import com.sorudefteri.domain.model.Soru
import com.sorudefteri.domain.model.TestQuestion
import com.sorudefteri.domain.model.TestResult
import com.sorudefteri.ui.ortak.FullScreenImageViewer
import com.sorudefteri.ui.istatistik.components.StoryCardGenerator
import java.io.File
import java.util.Locale
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.sorudefteri.ui.ortak.TeacherShareDialog
import com.sorudefteri.ui.ortak.TeacherShareHelper

/**
 * Test Sonuç ve Detaylı Analiz Ekranı
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestSonucScreen(
    onNewTest: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    onNavigateToStats: () -> Unit = {},
    viewModel: TestSonucViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val result = uiState.testResult
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var teacherShareQuestion by remember { mutableStateOf<com.sorudefteri.domain.model.TestQuestion?>(null) }

    // Sistem geri tuşuna basıldığında test sonucunu kapatıp kütüphaneye dön
    BackHandler {
        onNavigateToLibrary()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Test Değerlendirmesi",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tamamlanan Test Sonucu",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateToLibrary) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Kapat"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            TestSonucBottomBar(
                onNewTest = onNewTest,
                onNavigateToLibrary = onNavigateToLibrary,
                onNavigateToStats = onNavigateToStats
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        if (result == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        "Görüntülenecek test sonucu bulunamadı.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        "Uygulama yeniden başlatılmış olabilir. Sonuçlar İstatistikler sekmesinde güvenle saklanıyor.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = onNavigateToLibrary) { Text("Kütüphane") }
                        Button(onClick = onNewTest) { Text("Yeni Test") }
                        FilledTonalButton(onClick = onNavigateToStats) { Text("İstatistikler") }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Test Tamamlandı Özet Kartı
                item {
                    TestResultSummaryCard(result = result)
                }

                // 1.5. Instagram / WhatsApp Story Başarı Kartı 9:16 (Faz 5.4)
                item {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                try {
                                    val bitmap = withContext(Dispatchers.IO) {
                                        StoryCardGenerator.generateStoryBitmap(
                                            context = context,
                                            currentStreak = 1,
                                            successRate = result.successRate.toFloat(),
                                            levelTitle = "Test Fatihi",
                                            levelNumber = 1,
                                            totalXp = result.correctCount * 10,
                                            unlockedBadges = if (result.successRate >= 100) setOf("HATA_GECIRMEZ") else emptySet(),
                                            studentTarget = "Hedef: Cerrahpaşa Tıp",
                                            netIncrease = "+${String.format(Locale.getDefault(), "%.2f", result.netCount)} Net"
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

                // 2. Doğru Yapılan Soruları Arşivde 'Çözüldü' Yap Butonu
                if (result.correctCount > 0) {
                    item {
                        MarkCorrectCard(
                            correctCount = result.correctCount,
                            isMarking = uiState.isMarkingSolved,
                            hasMarked = uiState.hasMarkedSolved,
                            markedCount = uiState.markedCount,
                            onMark = { viewModel.markCorrectAsSolved() }
                        )
                    }
                }

                // 3. Süre Tuzağı Sorular Kartı (Hedef sürenin 2 katından fazla zaman harcanan sorular)
                val targetSeconds = if (result.targetTimePerQuestionSeconds > 0) {
                    result.targetTimePerQuestionSeconds
                } else {
                    75L
                }
                val slowQuestions = result.questions.mapIndexed { idx, q -> (idx + 1) to q }
                    .filter { it.second.timeSpentSeconds > 2 * targetSeconds }

                if (slowQuestions.isNotEmpty()) {
                    item {
                        SureTuzagiCard(
                            slowQuestions = slowQuestions,
                            targetSeconds = targetSeconds
                        )
                    }
                }

                // 4. Soru Analiz Başlığı
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Soru Analizi & Çözümleri",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${result.totalQuestions} Soru",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 4. Soruların Detaylı Listesi
                itemsIndexed(result.questions) { index, testQuestion ->
                    val soruId = testQuestion.soru.id
                    val isAiLoading = uiState.aiLoadingIds.contains(soruId)
                    val aiSolution = uiState.aiSolutions[soruId]
                    val aiError = uiState.aiErrors[soruId]
                    val isSolutionExpanded = uiState.expandedSolutionIds.contains(soruId)

                    QuestionResultItemCard(
                        questionNumber = index + 1,
                        testQuestion = testQuestion,
                        isAiLoading = isAiLoading,
                        aiSolution = aiSolution,
                        aiError = aiError,
                        isSolutionExpanded = isSolutionExpanded,
                        onRequestAiSolution = { viewModel.requestAiSolution(testQuestion.soru) },
                        onToggleSolutionExpanded = { viewModel.toggleSolutionExpanded(soruId) },
                        onZoomImage = { path, title -> viewModel.openZoomImage(path, title) },
                        onTeacherShare = { teacherShareQuestion = testQuestion }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    // Tam ekran görsel inceleme
    if (uiState.zoomImagePath != null) {
        FullScreenImageViewer(
            imagePath = uiState.zoomImagePath!!,
            title = uiState.zoomImageTitle ?: "Görsel",
            onDismiss = { viewModel.closeZoomImage() }
        )
    }

    // Faz 4.6: Hocama Sor (WhatsApp) Diyaloğu
    teacherShareQuestion?.let { tq ->
        TeacherShareDialog(
            soru = tq.soru,
            studentAnswer = tq.userAnswer,
            onDismiss = { teacherShareQuestion = null },
            onSend = { note ->
                coroutineScope.launch {
                    TeacherShareHelper.shareToTeacher(
                        context = context,
                        soru = tq.soru,
                        studentAnswer = tq.userAnswer,
                        customNote = note
                    )
                    teacherShareQuestion = null
                }
            }
        )
    }
}

/**
 * Başarı özeti ve 4'lü metrik kartı
 */
@Composable
private fun TestResultSummaryCard(result: TestResult) {
    val totalSec = result.totalTimeSeconds
    val minutes = totalSec / 60
    val remSec = totalSec % 60
    val timeStr = String.format(Locale.getDefault(), "%02d:%02d", minutes, remSec)
    val avgSecPerQ = if (result.totalQuestions > 0) totalSec / result.totalQuestions else 0

    val rateColor = when {
        result.successRate >= 70 -> Color(0xFF4CAF50)
        result.successRate >= 40 -> Color(0xFFFFA726)
        else -> Color(0xFFEF5350)
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Başarı Rozeti & Yüzde
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(rateColor.copy(alpha = 0.15f))
                    .border(3.dp, rateColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "%${result.successRate}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = rateColor
                    )
                    Text(
                        text = "Başarı",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = rateColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = when {
                    result.successRate >= 80 -> "Harika Performans! 🎉"
                    result.successRate >= 50 -> "İyi İş Çıkardın! 👍"
                    else -> "Tekrar Ederek Gelişebilirsin! 💪"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 4'lü Metrik — TELEFON: 2x2 ızgara (360dp'de 4'lü sığmıyordu)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Doğru
                    MetricItem(
                        label = "Doğru",
                        value = "${result.correctCount}",
                        color = Color(0xFF4CAF50),
                        icon = Icons.Default.CheckCircle,
                        modifier = Modifier.weight(1f)
                    )

                    // Yanlış
                    MetricItem(
                        label = "Yanlış",
                        value = "${result.wrongCount}",
                        color = Color(0xFFEF5350),
                        icon = Icons.Default.Cancel,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Boş
                    MetricItem(
                        label = "Boş",
                        value = "${result.emptyCount}",
                        color = Color(0xFF78909C),
                        icon = Icons.Outlined.RadioButtonUnchecked,
                        modifier = Modifier.weight(1f)
                    )

                    // Süre
                    MetricItem(
                        label = "Süre",
                        value = timeStr,
                        color = MaterialTheme.colorScheme.primary,
                        icon = Icons.Default.Timer,
                        subtext = "${avgSecPerQ}s/soru",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Net Skoru
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            ) {
                Text(
                    text = String.format(Locale.getDefault(), "YKS Neti: %.2f Net", result.netCount),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun MetricItem(
    label: String,
    value: String,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    subtext: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (subtext != null) {
            Text(
                text = subtext,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * Doğru Yapılan Soruları Arşivde Çözüldü Olarak İşaretle Butonu Kartı
 */
@Composable
private fun MarkCorrectCard(
    correctCount: Int,
    isMarking: Boolean,
    hasMarked: Boolean,
    markedCount: Int,
    onMark: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (hasMarked) Color(0xFF4CAF50).copy(alpha = 0.12f) else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
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
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (hasMarked) Icons.Default.CheckCircle else Icons.Outlined.CheckCircleOutline,
                    contentDescription = null,
                    tint = if (hasMarked) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (hasMarked) "Arşiv Güncellendi" else "Doğruları Arşivde Güncelle",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (hasMarked) {
                            "$markedCount doğru soru 'Çözüldü' olarak işaretlendi ✓"
                        } else {
                            "$correctCount doğru soruyu 'Çözüldü' olarak işaretle"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!hasMarked) {
                Button(
                    onClick = onMark,
                    enabled = !isMarking,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) {
                    if (isMarking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("İşaretle", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/**
 * Tekil Soru Sonuç & Çözüm Kartı
 */
@Composable
private fun QuestionResultItemCard(
    questionNumber: Int,
    testQuestion: TestQuestion,
    isAiLoading: Boolean,
    aiSolution: String?,
    aiError: String?,
    isSolutionExpanded: Boolean,
    onRequestAiSolution: () -> Unit,
    onToggleSolutionExpanded: () -> Unit,
    onZoomImage: (path: String, title: String) -> Unit,
    onTeacherShare: () -> Unit = {}
) {
    val soru = testQuestion.soru
    val dersAdi = DersTaksonomisi.getDersAdi(soru.examType, soru.subjectId)
    val statusColor = when (testQuestion.isCorrect) {
        true -> Color(0xFF4CAF50)
        false -> Color(0xFFEF5350)
        null -> Color(0xFF78909C)
    }

    val statusText = when (testQuestion.isCorrect) {
        true -> "Doğru"
        false -> "Yanlış"
        null -> "Boş"
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Üst Satır: Soru No, Ders, Süre ve Durum Rozeti
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Soru $questionNumber • $dersAdi",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (soru.topic.isNotBlank()) {
                        Text(
                            text = "${soru.topic} • ${testQuestion.timeSpentSeconds} sn",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Durum Rozeti
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }
                }
            }

            // Soru Görsel Önizlemesi & Cevap Özeti
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Küçük Soru Görseli (Tıklanınca Tam Ekran)
                Box(
                    modifier = Modifier
                        .size(width = 100.dp, height = 75.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable {
                            onZoomImage(soru.imagePath, "Soru $questionNumber - $dersAdi")
                        }
                ) {
                    AsyncImage(
                        model = File(soru.imagePath),
                        contentDescription = "Soru Görseli",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ZoomIn,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // Cevap Karşılaştırması
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Kullanıcı Cevabı
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Senin Cevabın: ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = testQuestion.userAnswer ?: "Boş",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (testQuestion.isCorrect == true) Color(0xFF4CAF50) else if (testQuestion.userAnswer != null) Color(0xFFEF5350) else Color(0xFF78909C)
                        )
                    }

                    // Doğru Cevap
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Doğru Cevap: ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = soru.correctAnswer ?: "Belirtilmemiş",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4CAF50)
                        )
                    }
                }
            }

            // Çözüm Butonları ve AI Çözümü Alanı
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // AI Çözümü Butonu
                if (aiSolution != null) {
                    FilledTonalButton(
                        onClick = onToggleSolutionExpanded,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isSolutionExpanded) "Çözümü Gizle" else "AI Çözümünü Göster",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(
                            imageVector = if (isSolutionExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    Button(
                        onClick = onRequestAiSolution,
                        enabled = !isAiLoading,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isAiLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Çözülüyor...", fontSize = 12.sp)
                        } else {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("AI Çözümü İste", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Hoca Çözüm Görseli Butonu (varsa)
                if (!soru.solutionImagePath.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = {
                            onZoomImage(soru.solutionImagePath!!, "Soru $questionNumber - Hoca Çözümü")
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Hoca Çözümü", fontSize = 12.sp)
                    }
                }
            }

            // Faz 4.6: Yanlış veya Boş Sorular İçin "Hocama Sor (WhatsApp)" Butonu
            if (testQuestion.isCorrect != true) {
                FilledTonalButton(
                    onClick = onTeacherShare,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFF25D366).copy(alpha = 0.12f),
                        contentColor = Color(0xFF1B5E20)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("👨‍🏫", fontSize = 15.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Hocama Sor (WhatsApp)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // AI Hata Mesajı (varsa)
            if (aiError != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = aiError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onRequestAiSolution,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Tekrar Dene",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Genişletilebilir AI Çözüm Metni Kartı
            AnimatedVisibility(
                visible = isSolutionExpanded && aiSolution != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Adım Adım Yapay Zeka Çözümü",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        MathMarkdownText(
                            text = aiSolution.orEmpty(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

/**
 * Sonuç Ekranı Alt Aksiyon Butonları
 */
@Composable
private fun TestSonucBottomBar(
    onNewTest: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    onNavigateToStats: () -> Unit = {}
) {
    Surface(
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onNavigateToLibrary,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Kütüphane")
                }

                Button(
                    onClick = onNewTest,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Yeni Test", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            FilledTonalButton(
                onClick = onNavigateToStats,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.BarChart,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("İstatistikler & Öneriler", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * Süre Tuzağı Sorular Kartı (Zaman Baskısı Modu)
 */
@Composable
private fun SureTuzagiCard(
    slowQuestions: List<Pair<Int, TestQuestion>>,
    targetSeconds: Long
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val containerColor = if (isDark) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f)
    else Color(0xFFFFF3E0)
    val borderColor = if (isDark) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)
    else Color(0xFFFFB74D)
    val titleColor = if (isDark) MaterialTheme.colorScheme.tertiary
    else Color(0xFFE65100)
    val bodyColor = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant
    else Color(0xFF5D4037)
    val itemTitleColor = if (isDark) MaterialTheme.colorScheme.onSurface
    else Color(0xFF3E2723)
    val itemTimeColor = if (isDark) MaterialTheme.colorScheme.error
    else Color(0xFFD84315)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    tint = titleColor,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "⚠️ Süre Tuzağı Sorular",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = titleColor
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Bu sorularda hedef sürenin (${targetSeconds}s) 2 katından fazla zaman harcadın. Gerçek sınavda bu tür sorularda takılmayıp turlama taktiği uygulamak netlerini artırır!",
                style = MaterialTheme.typography.bodySmall,
                color = bodyColor
            )

            Spacer(modifier = Modifier.height(12.dp))

            slowQuestions.forEach { (index, question) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Soru #$index: ${DersTaksonomisi.getDersAdi(question.soru.examType, question.soru.subjectId)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = itemTitleColor
                    )
                    Text(
                        text = "${question.timeSpentSeconds} sn (Hedef: ${targetSeconds}s)",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = itemTimeColor
                    )
                }
            }
        }
    }
}

