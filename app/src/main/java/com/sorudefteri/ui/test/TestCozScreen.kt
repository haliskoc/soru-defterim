package com.sorudefteri.ui.test

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.sorudefteri.domain.model.TestQuestion
import com.sorudefteri.ui.ortak.FullScreenImageViewer
import java.io.File
import java.util.Locale

/**
 * İnteraktif Test Çözme Ekranı
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestCozScreen(
    onFinishTest: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: TestCozViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val navListState = rememberLazyListState()

    // Geri tuşuna basıldığında çıkış onay dialogu
    BackHandler {
        viewModel.setShowExitDialog(true)
    }

    // Aktif soru değiştikçe yatay soru şeridini otomatik kaydır
    LaunchedEffect(uiState.currentIndex) {
        if (uiState.questions.isNotEmpty()) {
            navListState.animateScrollToItem(
                (uiState.currentIndex - 2).coerceAtLeast(0)
            )
        }
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                CenterAlignedTopAppBar(
                    title = {
                        // Sayaç / Kronometre
                        TimerBadge(seconds = uiState.elapsedSeconds)
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.setShowExitDialog(true) }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Testi İptal Et"
                            )
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = { viewModel.setShowFinishDialog(true) }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Flag,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Bitir",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                // Soru Numarası & İlerleme Çubuğu
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Soru ${uiState.currentIndex + 1} / ${uiState.questions.size}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "${uiState.answeredCount} Cevaplandı • ${uiState.emptyCount} Boş",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                LinearProgressIndicator(
                    progress = { uiState.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        },
        bottomBar = {
            // Alt Kontrol Butonları
            TestCozBottomBar(
                isFirst = uiState.isFirstQuestion,
                isLast = uiState.isLastQuestion,
                hasAnswer = uiState.currentAnswer != null,
                onPrevious = { viewModel.previousQuestion() },
                onClear = { viewModel.clearAnswer() },
                onNext = { viewModel.nextQuestion() },
                onFinish = { viewModel.setShowFinishDialog(true) }
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        if (uiState.questions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Test oturumu bulunamadı.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Soru Gezinme Şeridi (1..N)
                QuestionNumberRibbon(
                    totalQuestions = uiState.questions.size,
                    currentIndex = uiState.currentIndex,
                    userAnswers = uiState.userAnswers,
                    onSelectIndex = { viewModel.goToQuestion(it) },
                    listState = navListState
                )

                // Aktif Soru İçeriği (Animasyonlu Geçiş)
                AnimatedContent(
                    targetState = uiState.currentIndex,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                                slideOutHorizontally { width -> -width } + fadeOut()
                            )
                        } else {
                            (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                                slideOutHorizontally { width -> width } + fadeOut()
                            )
                        }
                    },
                    label = "QuestionAnimation",
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) { targetIndex ->
                    val question = uiState.questions.getOrNull(targetIndex)
                    val currentAnswer = uiState.userAnswers[targetIndex]

                    if (question != null) {
                        QuestionContent(
                            question = question,
                            selectedAnswer = currentAnswer,
                            questionIndex = targetIndex,
                            onAnswerSelected = { option ->
                                viewModel.selectAnswer(option)
                            },
                            onZoomImage = { path, title ->
                                viewModel.openZoomImage(path, title)
                            }
                        )
                    }
                }
            }
        }
    }

    // Tam Ekran Görsel İnceleme
    if (uiState.zoomImagePath != null) {
        FullScreenImageViewer(
            imagePath = uiState.zoomImagePath!!,
            title = uiState.zoomImageTitle ?: "Soru Görseli",
            onDismiss = { viewModel.closeZoomImage() }
        )
    }

    // Testi Bitir Onay Dialogu
    if (uiState.showFinishDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.setShowFinishDialog(false) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Flag,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Testi Bitir",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Testi bitirmek ve sonuçlarınızı görmek istiyor musunuz?")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• Toplam Soru: ${uiState.questions.size}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "• Cevaplanan: ${uiState.answeredCount}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "• Boş Bırakılan: ${uiState.emptyCount}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.finishTest(onFinishTest)
                    }
                ) {
                    Text("Testi Bitir")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setShowFinishDialog(false) }) {
                    Text("Devam Et")
                }
            }
        )
    }

    // Testten Çıkış Onay Dialogu
    if (uiState.showExitDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.setShowExitDialog(false) },
            title = {
                Text(
                    text = "Testten Çıkılsın mı?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text("Testi tamamlamadan çıkarsanız çözümleriniz ve süreniz kaydedilmeyecektir.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setShowExitDialog(false)
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Çık")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setShowExitDialog(false) }) {
                    Text("İptal")
                }
            }
        )
    }
}

/**
 * Üst bardaki sayaç rozeti
 */
@Composable
private fun TimerBadge(seconds: Long) {
    val minutes = seconds / 60
    val remainingSec = seconds % 60
    val timeFormatted = String.format(Locale.getDefault(), "%02d:%02d", minutes, remainingSec)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
        modifier = Modifier.padding(horizontal = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Timer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = timeFormatted,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/**
 * 1..N Soru gezinme tepsisi
 */
@Composable
private fun QuestionNumberRibbon(
    totalQuestions: Int,
    currentIndex: Int,
    userAnswers: Map<Int, String?>,
    onSelectIndex: (Int) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState
) {
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        items(totalQuestions) { index ->
            val isCurrent = index == currentIndex
            val isAnswered = !userAnswers[index].isNullOrBlank()

            val backgroundColor = when {
                isCurrent -> MaterialTheme.colorScheme.primary
                isAnswered -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surface
            }

            val textColor = when {
                isCurrent -> MaterialTheme.colorScheme.onPrimary
                isAnswered -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }

            val borderColor = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(backgroundColor)
                    .border(1.5.dp, borderColor, CircleShape)
                    .clickable { onSelectIndex(index) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isCurrent || isAnswered) FontWeight.Bold else FontWeight.Normal,
                    color = textColor
                )
            }
        }
    }
}

/**
 * Tekil Soru Görseli, Üst Bilgileri ve Şıkları
 */
@Composable
private fun QuestionContent(
    question: TestQuestion,
    selectedAnswer: String?,
    questionIndex: Int,
    onAnswerSelected: (String) -> Unit,
    onZoomImage: (path: String, title: String) -> Unit
) {
    val soru = question.soru
    val dersAdi = DersTaksonomisi.getDersAdi(soru.examType, soru.subjectId)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Soru Üst Bilgi Rozetleri
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Sınav ve Ders Rozeti
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = "${soru.examType} • $dersAdi",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // Konu
                if (soru.topic.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = soru.topic,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Hata Tipi (varsa)
            if (soru.mistakeType != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = soru.mistakeType.defaultColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = soru.mistakeType.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = soru.mistakeType.defaultColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Soru Görsel Kartı (Zoomable)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    onZoomImage(soru.imagePath, "Soru ${questionIndex + 1} - $dersAdi")
                }
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                AsyncImage(
                    model = File(soru.imagePath),
                    contentDescription = "Soru Görseli",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 450.dp)
                        .clip(RoundedCornerShape(16.dp))
                )

                // Büyütme İkonu
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.ZoomIn,
                            contentDescription = "Büyüt",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Şıklar (A, B, C, D, E)
        Text(
            text = "Cevabınızı İşaretleyin",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val options = listOf("A", "B", "C", "D", "E")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEach { option ->
                val isSelected = selectedAnswer.equals(option, ignoreCase = true)

                val backgroundColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    label = "optionBg_$option"
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "optionText_$option"
                )
                val borderColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    label = "optionBorder_$option"
                )

                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(backgroundColor)
                        .border(
                            width = if (isSelected) 2.5.dp else 1.dp,
                            color = borderColor,
                            shape = CircleShape
                        )
                        .clickable {
                            onAnswerSelected(option)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = option,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                        color = textColor,
                        fontSize = 20.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Test Çözme Ekranı Alt Gezinme Barı
 */
@Composable
private fun TestCozBottomBar(
    isFirst: Boolean,
    isLast: Boolean,
    hasAnswer: Boolean,
    onPrevious: () -> Unit,
    onClear: () -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit
) {
    Surface(
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Önceki Soru Butonu
            OutlinedButton(
                onClick = onPrevious,
                enabled = !isFirst,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Önceki")
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Boş Bırak Butonu
            FilledTonalButton(
                onClick = onClear,
                enabled = hasAnswer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Boş Bırak", fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Sonraki / Bitir Butonu
            Button(
                onClick = if (isLast) onFinish else onNext,
                shape = RoundedCornerShape(12.dp),
                colors = if (isLast) {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                } else {
                    ButtonDefaults.buttonColors()
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(if (isLast) "Bitir" else "Sonraki")
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (isLast) Icons.Default.Flag else Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
