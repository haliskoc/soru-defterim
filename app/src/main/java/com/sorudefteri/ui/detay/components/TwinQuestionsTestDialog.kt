package com.sorudefteri.ui.detay.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sorudefteri.domain.model.TwinQuestion
import com.sorudefteri.domain.model.TwinQuestionsResult
import com.sorudefteri.ui.ortak.math.MathDisplayHelper
import com.sorudefteri.ui.ortak.math.MathMarkdownText
import com.sorudefteri.ui.ortak.math.SmartMathContent
import com.sorudefteri.ui.ortak.math.formatMathText

/**
 * Yanlış Soru İkizi testindeki her bir sorunun pedagojik rolü.
 */
data class TwinQuestionRole(
    val badgeText: String,
    val roleName: String,
    val purpose: String,
    val icon: ImageVector,
    val containerColorLight: Color,
    val contentColorLight: Color,
    val containerColorDark: Color,
    val contentColorDark: Color
)

private fun getTwinQuestionRole(index: Int): TwinQuestionRole {
    return when (index) {
        0 -> TwinQuestionRole(
            badgeText = "🔢 Sayı & Değer Varyasyonu (Pekiştirme)",
            roleName = "Sayı & Değer Varyasyonu",
            purpose = "Aynı kavram ve formül mantığını farklı sayısal değerlerle pekiştirir.",
            icon = Icons.Default.Calculate,
            containerColorLight = Color(0xFFE0F2FE),
            contentColorLight = Color(0xFF0369A1),
            containerColorDark = Color(0xFF0C4A6E).copy(alpha = 0.55f),
            contentColorDark = Color(0xFF7DD3FC)
        )
        1 -> TwinQuestionRole(
            badgeText = "📖 Hikaye & Senaryo Varyasyonu (Bağlam)",
            roleName = "Hikaye & Senaryo Varyasyonu",
            purpose = "Kazanımı günlük hayat / yeni nesil farklı bir hikaye bağlamında test eder.",
            icon = Icons.AutoMirrored.Filled.MenuBook,
            containerColorLight = Color(0xFFEDE9FE),
            contentColorLight = Color(0xFF6D28D9),
            containerColorDark = Color(0xFF4C1D95).copy(alpha = 0.55f),
            contentColorDark = Color(0xFFC4B5FD)
        )
        2 -> TwinQuestionRole(
            badgeText = "⚠️ Tersine Akıl Yürütme & Kavram Tuzağı (Derinleşme)",
            roleName = "Tersine Akıl Yürütme & Kavram Tuzağı",
            purpose = "Sık düşülen çeldiriciler, tersine akıl yürütme ve kavram yanılgılarını yakalar.",
            icon = Icons.Default.Warning,
            containerColorLight = Color(0xFFFEF3C7),
            contentColorLight = Color(0xFFB45309),
            containerColorDark = Color(0xFF78350F).copy(alpha = 0.55f),
            contentColorDark = Color(0xFFFCD34D)
        )
        else -> TwinQuestionRole(
            badgeText = "🎯 Özel İkiz Soru #${index + 1}",
            roleName = "Kazanım Pekiştirme",
            purpose = "Bu soru hedef kazanıma özel olarak yapay zeka tarafından türetilmiştir.",
            icon = Icons.Default.AutoAwesome,
            containerColorLight = Color(0xFFF1F5F9),
            contentColorLight = Color(0xFF334155),
            containerColorDark = Color(0xFF1E293B),
            contentColorDark = Color(0xFF94A3B8)
        )
    }
}

/**
 * 3 Soruluk Kardeş Testi İnteraktif Çözüm Dialogu (TwinQuestionsResult parametreli aşırı yükleme).
 */
@Composable
fun TwinQuestionsTestDialog(
    twinResult: TwinQuestionsResult,
    onDismiss: () -> Unit,
    onSaveToLibrary: ((TwinQuestionsResult) -> Unit)? = null,
    onSaveAllToLibrary: ((List<TwinQuestion>) -> Unit)? = null
) {
    TwinQuestionsTestDialog(
        questions = twinResult.questions,
        topic = twinResult.originalTopic,
        onDismiss = onDismiss,
        onSaveAllToLibrary = { questionList ->
            if (onSaveAllToLibrary != null) {
                onSaveAllToLibrary(questionList)
            } else if (onSaveToLibrary != null) {
                onSaveToLibrary(twinResult.copy(questions = questionList))
            }
        }
    )
}

/**
 * Yanlış yapılan sorunun ardından 3 soruluk interaktif mini testi çözerken
 * kullanılan modern ve zengin Compose Dialog'u.
 *
 * @param questions 3 soruluk kardeş test listesi
 * @param onDismiss Diyalog kapatıldığında çağrılır
 * @param onSaveAllToLibrary 3 soruyu bağımsız soru olarak kütüphaneye kaydetmek için callback
 * @param topic İsteğe bağlı orijinal soru konusu başlığı
 */
@Composable
fun TwinQuestionsTestDialog(
    questions: List<TwinQuestion>,
    onDismiss: () -> Unit,
    onSaveAllToLibrary: (List<TwinQuestion>) -> Unit = {},
    topic: String = ""
) {
    if (questions.isEmpty()) {
        Dialog(onDismissRequest = onDismiss) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "İkiz soru verisi bulunamadı.",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Button(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) {
                        Text("Kapat")
                    }
                }
            }
        }
        return
    }

    // Durumlar
    var currentIndex by rememberSaveable { mutableIntStateOf(0) }
    // index -> seçilen şık ("A", "B", ... veya "PAS")
    val userAnswers = remember { mutableStateMapOf<Int, String>() }
    var isTestCompleted by rememberSaveable { mutableStateOf(false) }
    var isSavedToLibrary by rememberSaveable { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    // Soru veya ekran değiştiğinde scroll en üste dönsün
    LaunchedEffect(currentIndex, isTestCompleted) {
        scrollState.scrollTo(0)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // 1. ÜST BAŞLIK ALANI
                TwinTestHeader(
                    topic = topic,
                    isCompleted = isTestCompleted,
                    onDismiss = onDismiss
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (!isTestCompleted) {
                    // TEST DEVAM EDİYOR: Üst İlerleme ve Rozet
                    val currentQuestion = questions.getOrElse(currentIndex) { questions.first() }
                    val currentRole = getTwinQuestionRole(currentIndex)

                    TwinTestProgressSection(
                        currentIndex = currentIndex,
                        totalQuestions = questions.size,
                        questions = questions,
                        userAnswers = userAnswers,
                        currentRole = currentRole,
                        isDark = isDark
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 2. SORU GÖVDESİ & ŞIKLAR & ÇÖZÜM
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Soru Metni Kartı (MathMarkdownText)
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            ),
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Soru ${currentIndex + 1}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = currentRole.roleName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))

                                // Soru Metni KaTeX/LaTeX Render
                                MathMarkdownText(
                                    text = currentQuestion.questionText,
                                    textColor = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // Seçenekler Başlığı
                        Text(
                            text = "Seçenekler:",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // 5 Seçenek (A, B, C, D, E)
                        val selectedOption = userAnswers[currentIndex]
                        val isAnswered = selectedOption != null && selectedOption != "PAS"
                        val isSkipped = selectedOption == "PAS"

                        val standardKeys = listOf("A", "B", "C", "D", "E")
                        val optionKeys = remember(currentQuestion) {
                            val presentStandard = standardKeys.filter { currentQuestion.options.containsKey(it) }
                            if (presentStandard.isNotEmpty()) {
                                presentStandard
                            } else {
                                currentQuestion.options.keys.sorted()
                            }
                        }

                        optionKeys.forEach { key ->
                            val optionText = currentQuestion.options[key].orEmpty()
                            val isThisSelected = selectedOption == key
                            val isThisCorrect = key.equals(currentQuestion.correctAnswer.trim(), ignoreCase = true)

                            TwinOptionItem(
                                key = key,
                                text = optionText,
                                isAnswered = isAnswered,
                                isSelected = isThisSelected,
                                isCorrect = isThisCorrect,
                                isDark = isDark,
                                onSelect = {
                                    if (!isAnswered && !isSkipped) {
                                        userAnswers[currentIndex] = key
                                    }
                                }
                            )
                        }

                        // ANINDA GERİ BİLDİRİM BANNERI
                        if (isAnswered) {
                            val isUserCorrect = selectedOption?.equals(currentQuestion.correctAnswer.trim(), ignoreCase = true) == true
                            FeedbackBanner(
                                isCorrect = isUserCorrect,
                                correctAnswer = currentQuestion.correctAnswer,
                                isDark = isDark
                            )
                        } else if (isSkipped) {
                            SkippedBanner(
                                correctAnswer = currentQuestion.correctAnswer,
                                isDark = isDark
                            )
                        }

                        // OTOMATİK AÇILAN KARTLAR: ÇÖZÜM VE TUZAK UYARISI
                        AnimatedVisibility(
                            visible = isAnswered || isSkipped,
                            enter = fadeIn() + slideInVertically { it / 3 }
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // 1. Adım Adım Çözüm Kartı
                                SolutionCard(
                                    solutionText = currentQuestion.solution,
                                    isDark = isDark
                                )

                                // 2. Varsa Tuzak Uyarısı Kartı
                                if (!currentQuestion.trapWarning.isNullOrBlank()) {
                                    TrapWarningCard(
                                        trapWarning = currentQuestion.trapWarning,
                                        isDark = isDark
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 3. GEZİNME BUTONLARI (BOTTOM BAR)
                    TwinTestNavigationBar(
                        currentIndex = currentIndex,
                        totalQuestions = questions.size,
                        isAnsweredOrSkipped = userAnswers.containsKey(currentIndex),
                        onPrevious = {
                            if (currentIndex > 0) currentIndex--
                        },
                        onNext = {
                            if (currentIndex < questions.size - 1) {
                                currentIndex++
                            }
                        },
                        onSkip = {
                            if (!userAnswers.containsKey(currentIndex)) {
                                userAnswers[currentIndex] = "PAS"
                            }
                        },
                        onFinishTest = {
                            isTestCompleted = true
                        }
                    )
                } else {
                    // 4. KARNE / SONUÇ EKRANI
                    TwinTestScoreCardScreen(
                        questions = questions,
                        userAnswers = userAnswers,
                        isSavedToLibrary = isSavedToLibrary,
                        isDark = isDark,
                        onSaveAllToLibrary = {
                            onSaveAllToLibrary(questions)
                            isSavedToLibrary = true
                        },
                        onRestartTest = {
                            userAnswers.clear()
                            currentIndex = 0
                            isTestCompleted = false
                            isSavedToLibrary = false
                        },
                        onDismiss = onDismiss
                    )
                }
            }
        }
    }
}

/**
 * Dialog Tepe Başlık Alanı
 */
@Composable
private fun TwinTestHeader(
    topic: String,
    isCompleted: Boolean,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isCompleted) Icons.Default.EmojiEvents else Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = if (isCompleted) "İkiz Test Karnesi 🎯" else "Yanlış Soru İkizi",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (topic.isNotBlank()) "Kazanım: $topic" else "3 Soruluk Pekiştirme Mini Testi",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Kapat",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * İlerleme Göstergesi ve Soru Rolü Etiketi (Badge)
 */
@Composable
private fun TwinTestProgressSection(
    currentIndex: Int,
    totalQuestions: Int,
    questions: List<TwinQuestion>,
    userAnswers: Map<Int, String>,
    currentRole: TwinQuestionRole,
    isDark: Boolean
) {
    val animatedProgress by animateFloatAsState(
        targetValue = ((currentIndex + 1).toFloat() / totalQuestions.toFloat()).coerceIn(0f, 1f),
        label = "ProgressAnimation"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Soru 1 / 3 ve Nokta Göstergeleri
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Soru ${currentIndex + 1} / $totalQuestions",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // Segmentli Dot / Adım Göstergesi
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until totalQuestions) {
                    val isDone = userAnswers.containsKey(i)
                    val isCurrent = i == currentIndex
                    val ans = userAnswers[i]
                    val isCorrect = ans != null && ans != "PAS" &&
                            ans.equals(questions.getOrNull(i)?.correctAnswer?.trim(), ignoreCase = true)

                    val dotColor = when {
                        isDone && isCorrect -> Color(0xFF4CAF50)
                        isDone && !isCorrect && ans != "PAS" -> Color(0xFFE53935)
                        isDone && ans == "PAS" -> Color(0xFF9E9E9E)
                        isCurrent -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    }

                    Box(
                        modifier = Modifier
                            .size(
                                width = if (isCurrent) 22.dp else 9.dp,
                                height = 9.dp
                            )
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                }
            }
        }

        // Çizgisel İlerleme Çubuğu
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        // Soru Rolü Etiketi (Badge)
        val roleBg = if (isDark) currentRole.containerColorDark else currentRole.containerColorLight
        val roleContent = if (isDark) currentRole.contentColorDark else currentRole.contentColorLight

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = roleBg,
            border = BorderStroke(1.dp, roleContent.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = currentRole.icon,
                    contentDescription = null,
                    tint = roleContent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentRole.badgeText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = roleContent
                    )
                    Text(
                        text = currentRole.purpose,
                        style = MaterialTheme.typography.bodySmall,
                        color = roleContent.copy(alpha = 0.85f),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

/**
 * Tekil Şık Bileşeni (A, B, C, D, E)
 * Anında yeşil / kırmızı görsel geri bildirim.
 */
@Composable
private fun TwinOptionItem(
    key: String,
    text: String,
    isAnswered: Boolean,
    isSelected: Boolean,
    isCorrect: Boolean,
    isDark: Boolean,
    onSelect: () -> Unit
) {
    val backgroundColor = when {
        isAnswered && isSelected && isCorrect -> if (isDark) Color(0xFF1B5E20).copy(alpha = 0.55f) else Color(0xFFE8F5E9)
        isAnswered && isSelected && !isCorrect -> if (isDark) Color(0xFFB71C1C).copy(alpha = 0.55f) else Color(0xFFFFEBEE)
        isAnswered && !isSelected && isCorrect -> if (isDark) Color(0xFF1B5E20).copy(alpha = 0.25f) else Color(0xFFF1F8E9)
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    }

    val borderColor = when {
        isAnswered && isCorrect -> Color(0xFF4CAF50) // Doğru şık daima yeşil çerçeveyle gösterilir
        isAnswered && isSelected && !isCorrect -> Color(0xFFE53935)
        isSelected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    }

    val borderWidth = when {
        isAnswered && (isCorrect || isSelected) -> 2.dp
        isSelected -> 2.dp
        else -> 1.dp
    }

    val circleColor = when {
        isAnswered && isCorrect -> Color(0xFF4CAF50)
        isAnswered && isSelected && !isCorrect -> Color(0xFFE53935)
        isSelected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val circleTextColor = when {
        isAnswered && (isCorrect || (isSelected && !isCorrect)) -> Color.White
        isSelected -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = backgroundColor,
        border = BorderStroke(borderWidth, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = !isAnswered, onClick = onSelect)
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Şık Harfi Yuvarlağı
            Surface(
                shape = CircleShape,
                color = circleColor,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isAnswered && (isCorrect || isSelected)) Color.Transparent else MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = key,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = circleTextColor
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Şık Metni (KaTeX veya Formatlı)
            Box(modifier = Modifier.weight(1f)) {
                if (MathDisplayHelper.hasMathOrLatex(text)) {
                    SmartMathContent(
                        content = text,
                        fontSizeSp = 14f,
                        textColor = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    Text(
                        text = formatMathText(text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Durum İkonu ve Rozeti
            if (isAnswered) {
                Spacer(modifier = Modifier.width(8.dp))
                when {
                    isSelected && isCorrect -> {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF4CAF50),
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Doğru",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    isSelected && !isCorrect -> {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFE53935),
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Yanlış",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    !isSelected && isCorrect -> {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF4CAF50).copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, Color(0xFF4CAF50)),
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Text(
                                text = "Doğru Şık",
                                color = Color(0xFF2E7D32),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Şık seçildiğinde anında beliren tebrik veya açıklama şeridi
 */
@Composable
private fun FeedbackBanner(
    isCorrect: Boolean,
    correctAnswer: String,
    isDark: Boolean
) {
    val bgColor = if (isCorrect) {
        if (isDark) Color(0xFF1B5E20).copy(alpha = 0.45f) else Color(0xFFE8F5E9)
    } else {
        if (isDark) Color(0xFFB71C1C).copy(alpha = 0.45f) else Color(0xFFFFEBEE)
    }

    val borderColor = if (isCorrect) Color(0xFF4CAF50) else Color(0xFFE53935)
    val contentColor = if (isCorrect) Color(0xFF2E7D32) else Color(0xFFC62828)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = bgColor,
        border = BorderStroke(1.5.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isCorrect) Icons.Default.CheckCircle else Icons.Default.Close,
                contentDescription = null,
                tint = borderColor,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = if (isCorrect) "✓ Tebrikler, Doğru!" else "✕ Yanlış Seçenek (Doğru Cevap: $correctAnswer)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else contentColor
                )
                Text(
                    text = if (isCorrect) {
                        "Kazanımı çok iyi anladın! Çözüm adımlarını aşağıdan teyit edebilirsin."
                    } else {
                        "Sorun değil! Adım adım çözümü ve tuzak uyarısını inceleyerek mantığı öğren."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = (if (isDark) Color.White else contentColor).copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
private fun SkippedBanner(
    correctAnswer: String,
    isDark: Boolean
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Lightbulb,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Bu soru pas geçildi. Doğru Cevap: $correctAnswer",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Çözümü aşağıdan inceleyebilir veya sonraki soruya geçebilirsin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 📝 Adım Adım Çözüm Kartı
 */
@Composable
private fun SolutionCard(
    solutionText: String,
    isDark: Boolean
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else Color(0xFFF8FAFC)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "📝 Adım Adım Çözüm",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            MathMarkdownText(
                text = solutionText,
                textColor = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * ⚠️ Dikkat Edilmesi Gereken Tuzak (trapWarning) Kartı
 */
@Composable
private fun TrapWarningCard(
    trapWarning: String,
    isDark: Boolean
) {
    val containerColor = if (isDark) Color(0xFF78350F).copy(alpha = 0.35f) else Color(0xFFFEF3C7)
    val contentColor = if (isDark) Color(0xFFFDE68A) else Color(0xFF92400E)
    val borderColor = if (isDark) Color(0xFFB45309) else Color(0xFFF59E0B)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.5.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = CircleShape,
                    color = borderColor.copy(alpha = 0.2f),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = borderColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "⚠️ Dikkat Edilmesi Gereken Tuzak!",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (MathDisplayHelper.hasMathOrLatex(trapWarning)) {
                SmartMathContent(
                    content = trapWarning,
                    fontSizeSp = 14f,
                    textColor = contentColor
                )
            } else {
                Text(
                    text = formatMathText(trapWarning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

/**
 * Gezinme Butonları (Önceki Soru, Pas Geç, Sonraki Soru / Testi Tamamla)
 */
@Composable
private fun TwinTestNavigationBar(
    currentIndex: Int,
    totalQuestions: Int,
    isAnsweredOrSkipped: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onFinishTest: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Önceki Soru Butonu
        OutlinedButton(
            onClick = onPrevious,
            enabled = currentIndex > 0,
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Önceki")
        }

        // Eğer henüz cevap verilmediyse pas geçme butonu
        if (!isAnsweredOrSkipped) {
            TextButton(
                onClick = onSkip,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Pas Geç",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Sonraki Soru veya Testi Tamamla
        if (currentIndex < totalQuestions - 1) {
            Button(
                onClick = onNext,
                enabled = isAnsweredOrSkipped,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Sonraki Soru")
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        } else {
            Button(
                onClick = onFinishTest,
                enabled = isAnsweredOrSkipped,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Testi Tamamla & Karne")
            }
        }
    }
}

/**
 * 4. KARNE / SONUÇ EKRANI
 */
@Composable
private fun TwinTestScoreCardScreen(
    questions: List<TwinQuestion>,
    userAnswers: Map<Int, String>,
    isSavedToLibrary: Boolean,
    isDark: Boolean,
    onSaveAllToLibrary: () -> Unit,
    onRestartTest: () -> Unit,
    onDismiss: () -> Unit
) {
    val total = questions.size
    val correctCount = questions.indices.count { idx ->
        val ans = userAnswers[idx]
        ans != null && ans != "PAS" &&
                ans.equals(questions[idx].correctAnswer.trim(), ignoreCase = true)
    }
    val wrongCount = questions.indices.count { idx ->
        val ans = userAnswers[idx]
        ans != null && ans != "PAS" &&
                !ans.equals(questions[idx].correctAnswer.trim(), ignoreCase = true)
    }
    val skippedCount = total - correctCount - wrongCount

    // Tuzak soru (3. soru) sonucu
    val isTrapQuestionCorrect = userAnswers[2]?.equals(questions.getOrNull(2)?.correctAnswer?.trim(), ignoreCase = true) == true

    // Motive Edici Koçluk Mesajı
    val coachingMessage = remember(correctCount, isTrapQuestionCorrect) {
        when {
            correctCount == total ->
                "Tebrikler Şampiyon! 🏆 Konunun tüm varyasyonlarına, yeni nesil hikaye bağlamına ve kavram tuzaklarına tam hakimsin!"
            correctCount == 2 && !isTrapQuestionCorrect ->
                "Kavramı kavradın, sadece tuzak sorularda dikkatli olmalısın! 🎯 3. sorudaki çeldiriciye takıldın ama genel mantık harika!"
            correctCount == 2 ->
                "Harika bir başarı! 🚀 Konunun temel mantığını çok iyi kavramışsın. Küçük bir dikkatle sınavda soru kaçırmazsın!"
            correctCount == 1 ->
                "Kazanım oturmaya başladı! 💡 Çözüm adımlarını ve tuzak uyarılarını incelemek kavram eksiklerini hızla kapatacaktır."
            else ->
                "Pes etmek yok! 🌱 İkiz sorular zorlandığın yerleri tespit etmek için var. Çözümleri dikkatlice inceleyip testi baştan çözerek pekiştirebilirsin! 💪"
        }
    }

    val scoreTitle = when (correctCount) {
        3 -> "3'te 3 Mükemmel! 🏆"
        2 -> "3'te 2 Doğru! 🎯"
        1 -> "3'te 1 Doğru! 👍"
        else -> "3'te 0 - Tekrarla Güçlen! 💪"
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Kupa / Rozet Animasyonu Alanı
        Surface(
            shape = CircleShape,
            color = when (correctCount) {
                3 -> Color(0xFFFFD700).copy(alpha = 0.2f)
                2 -> Color(0xFF4CAF50).copy(alpha = 0.2f)
                1 -> Color(0xFF2196F3).copy(alpha = 0.2f)
                else -> Color(0xFFFF9800).copy(alpha = 0.2f)
            },
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = when (correctCount) {
                        3 -> Color(0xFFFFB300)
                        2 -> Color(0xFF4CAF50)
                        1 -> Color(0xFF2196F3)
                        else -> Color(0xFFFF9800)
                    },
                    modifier = Modifier.size(46.dp)
                )
            }
        }

        // Skor Başlığı ve İstatistikler
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = scoreTitle,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$correctCount Doğru  •  $wrongCount Yanlış  •  $skippedCount Boş",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Motive Edici Koçluk Mesajı Kartı
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "AI Koç Değerlendirmesi",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = coachingMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Soru Soru Detay Dökümü
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Soru Analizleri:",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            questions.forEachIndexed { idx, question ->
                val role = getTwinQuestionRole(idx)
                val userChoice = userAnswers[idx]
                val isCorrect = userChoice != null && userChoice != "PAS" &&
                        userChoice.equals(question.correctAnswer.trim(), ignoreCase = true)
                val isSkipped = userChoice == null || userChoice == "PAS"

                QuestionScoreRow(
                    index = idx,
                    role = role,
                    question = question,
                    userChoice = userChoice,
                    isCorrect = isCorrect,
                    isSkipped = isSkipped,
                    isDark = isDark
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Aksiyon Butonları
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Buton 1: Kütüphaneme Kaydet
            Button(
                onClick = onSaveAllToLibrary,
                enabled = !isSavedToLibrary,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = if (isSavedToLibrary) Icons.Default.Check else Icons.Default.BookmarkAdd,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isSavedToLibrary) "✓ 3 Soru Kütüphanene Kaydedildi" else "💾 Bu 3 Soruyu Kütüphaneme Kaydet",
                    fontWeight = FontWeight.Bold
                )
            }

            // Buton 2: Testi Baştan Çöz
            OutlinedButton(
                onClick = onRestartTest,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.RestartAlt,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("🔄 Testi Baştan Çöz")
            }

            // Buton 3: Kapat
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Kapat")
            }
        }
    }
}

/**
 * Karne Ekranındaki Soru Satırı (Açılıp kapanabilir çözüm detayı ile)
 */
@Composable
private fun QuestionScoreRow(
    index: Int,
    role: TwinQuestionRole,
    question: TwinQuestion,
    userChoice: String?,
    isCorrect: Boolean,
    isSkipped: Boolean,
    isDark: Boolean
) {
    var expanded by remember { mutableStateOf(false) }

    val statusColor = when {
        isCorrect -> Color(0xFF4CAF50)
        isSkipped -> Color(0xFF9E9E9E)
        else -> Color(0xFFE53935)
    }

    val statusText = when {
        isCorrect -> "Doğru ✓"
        isSkipped -> "Pas -"
        else -> "Yanlış ✗ (Cevabın: $userChoice, Doğru: ${question.correctAnswer})"
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = statusColor.copy(alpha = 0.15f),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${index + 1}",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium,
                                color = statusColor
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = role.badgeText,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = statusColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Detay",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Soru:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    MathMarkdownText(
                        text = question.questionText,
                        fontSizeSp = 13.5f,
                        textColor = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Çözüm Adımları:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    MathMarkdownText(
                        text = question.solution,
                        fontSizeSp = 13.5f,
                        textColor = MaterialTheme.colorScheme.onSurface
                    )

                    if (!question.trapWarning.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "⚠️ Tuzak Notu:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFD97706)
                        )
                        Text(
                            text = question.trapWarning,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isDark) Color(0xFFFDE68A) else Color(0xFF92400E)
                        )
                    }
                }
            }
        }
    }
}
