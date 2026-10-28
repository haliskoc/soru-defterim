package com.sorudefteri.ui.detay.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sorudefteri.domain.model.BenzerSoru
import com.sorudefteri.domain.model.SimilarQuestionResult
import com.sorudefteri.ui.ortak.math.CombinedMathMarkdownText
import com.sorudefteri.ui.ortak.math.formatMathText

@Composable
fun BenzerSorularTab(
    similarQuestion: SimilarQuestionResult?,
    isGeneratingSimilar: Boolean,
    similarError: String?,
    userSelectedOption: String?,
    isAnswerRevealed: Boolean,
    onGenerateSimilar: () -> Unit,
    onSelectOption: (String) -> Unit,
    onRevealAnswer: () -> Unit,
    onExternalAiShare: () -> Unit = {},
    onPasteManualSimilar: () -> Unit = {},
    savedSimilarQuestions: List<BenzerSoru> = emptyList(),
    // DB okuması tamamlandı mı? false ise boş durum yerine yükleniyor gösterilir.
    isSavedSimilarLoaded: Boolean = true,
    isSavingSimilar: Boolean = false,
    similarSaveMessage: String? = null,
    onPersistCurrent: () -> Unit = {},
    onSaveToLibrary: () -> Unit = {},
    onOpenSaved: (BenzerSoru) -> Unit = {},
    onDeleteSaved: (Long) -> Unit = {},
    onDismissSaveMessage: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (similarQuestion == null && !isGeneratingSimilar && isSavedSimilarLoaded) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Benzer Soru Türetici (Question Cloner)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Bu sorunun mantığını, kavramlarını ve zorluğunu temel alarak yeni sayılarla orijinal bir benzer soru türetin ve kendinizi test edin.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = onGenerateSimilar,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Benzer Soru Türet")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    FilledTonalButton(
                        onClick = onExternalAiShare,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Harici AI ile Türet & Paylaş (Ücretsiz)")
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedButton(
                        onClick = onPasteManualSimilar,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Harici Benzer Soru Yapıştır")
                    }

                    similarError?.let { err ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            // DÜZELTME: SavedSimilarList burada VE aşağıda (satır ~424, koşulsuz
            // dal) İKİ KEZ çiziliyordu. Kayıtlı benzer soru varken ve güncel soru
            // yokken liste iki kez görünüyor, o bölgenin yüksekliği ikiye
            // katlanıyordu. Aşağıdaki koşulsuz kopya yeterli.
        } else if (isGeneratingSimilar) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(40.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Özgün Benzer Soru Üretiliyor...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Aynı kazanım korunarak yeni sayılar, şıklar ve çözüm hazırlanıyor.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (similarQuestion != null) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "AI Tarafından Üretilen Benzer Soru",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onExternalAiShare) {
                                Icon(Icons.Default.Share, contentDescription = "Harici AI ile Türet", tint = MaterialTheme.colorScheme.primary)
                            }
                            FilledTonalButton(
                                onClick = onGenerateSimilar,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Yeni Türet")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Soru Metni
                    CombinedMathMarkdownText(
                        text = similarQuestion.questionText,
                        textColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Şıklar
                    val optionKeys = listOf("A", "B", "C", "D", "E")
                    val optionsMap = similarQuestion.options
                    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        optionKeys.forEach { key ->
                            val optionText = optionsMap[key] ?: return@forEach
                            val isSelected = userSelectedOption == key
                            val isCorrect = key == similarQuestion.correctAnswer

                            val containerColor = when {
                                isAnswerRevealed && isCorrect -> if (isDark) Color(0xFF1B5E20).copy(alpha = 0.45f) else Color(0xFFE8F5E9)
                                isAnswerRevealed && isSelected && !isCorrect -> if (isDark) Color(0xFFB71C1C).copy(alpha = 0.45f) else Color(0xFFFFEBEE)
                                isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            }

                            val borderColor = when {
                                isAnswerRevealed && isCorrect -> Color(0xFF4CAF50)
                                isAnswerRevealed && isSelected && !isCorrect -> Color(0xFFE53935)
                                isSelected -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.outlineVariant
                            }

                            val borderWidth = if (isSelected || (isAnswerRevealed && (isCorrect || isSelected))) 2.dp else 1.dp
                            val optionTextColor = MaterialTheme.colorScheme.onSurface

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = containerColor,
                                border = BorderStroke(borderWidth, borderColor),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !isAnswerRevealed) { onSelectOption(key) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        border = BorderStroke(
                                            width = if (isSelected) 1.5.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                        ),
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = key,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Text(
                                        text = formatMathText(optionText),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = optionTextColor,
                                        modifier = Modifier.weight(1f)
                                    )

                                    Box(
                                        modifier = Modifier.size(24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isAnswerRevealed) {
                                            if (isCorrect) {
                                                Icon(Icons.Default.Check, contentDescription = "Doğru", tint = Color(0xFF4CAF50))
                                            } else if (isSelected) {
                                                Icon(Icons.Default.Close, contentDescription = "Yanlış", tint = Color(0xFFE53935))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (!isAnswerRevealed && userSelectedOption != null) {
                        Button(
                            onClick = onRevealAnswer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Cevabı ve Çözümü Gör")
                        }
                    }

                    if (isAnswerRevealed) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "Doğru Cevap: ${similarQuestion.correctAnswer}",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                CombinedMathMarkdownText(
                                    text = similarQuestion.solution,
                                    textColor = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // DÜZELTME: Burada ayrıca bir "kaydedildi" kartı vardı ve DB
                    // yazımı bittikten SONRA ekleniyordu; çözümün altındaki iki aksiyon
                    // düğmesini ~68 dp aşağı itiyordu (görünür zıplama).
                    // AYRICA bu mesaj zaten SoruDetayScreen'de Snackbar olarak
                    // gösteriliyor — yani hem görsel zıplama hem de çift bildirim
                    // vardı. Kart kaldırıldı, Snackbar kalıyor.

                    // Bu soruya kaydet + kütüphaneye kaydet aksiyonları
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onPersistCurrent,
                            enabled = !isSavingSimilar,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isSavingSimilar) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                            } else {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Bu Soruya Kaydet")
                        }
                        Button(
                            onClick = onSaveToLibrary,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.LibraryAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Kütüphaneye Kaydet")
                        }
                    }
                }
            }
        }

        // Soruya kayıtlı benzer sorular arşivi
        SavedSimilarList(
            savedSimilarQuestions = savedSimilarQuestions,
            onOpenSaved = onOpenSaved,
            onDeleteSaved = onDeleteSaved
        )
    }
}

@Composable
private fun SavedSimilarList(
    savedSimilarQuestions: List<BenzerSoru>,
    onOpenSaved: (BenzerSoru) -> Unit,
    onDeleteSaved: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (savedSimilarQuestions.isEmpty()) return
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth()
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
                    text = "Bu Soruya Kayıtlı (${savedSimilarQuestions.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    Icons.Default.BookmarkAdd,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            savedSimilarQuestions.forEach { saved ->
                OutlinedCard(
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = saved.questionText.ifBlank { "(Soru metni yok)" }.take(140),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${saved.options.size} şık • Doğru: ${saved.correctAnswer}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { onOpenSaved(saved) },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Aç")
                            }
                            OutlinedButton(
                                onClick = { onDeleteSaved(saved.id) },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Sil", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
