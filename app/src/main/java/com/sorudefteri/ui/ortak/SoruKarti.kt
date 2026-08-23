package com.sorudefteri.ui.ortak

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.sorudefteri.domain.DersTaksonomisi
import com.sorudefteri.domain.model.HataTipi
import com.sorudefteri.domain.model.Soru
import com.sorudefteri.ui.theme.AytBadgeColor
import com.sorudefteri.ui.theme.AytBadgeContainer
import com.sorudefteri.ui.theme.MistakeBadgeColor
import com.sorudefteri.ui.theme.MistakeBadgeContainer
import com.sorudefteri.ui.theme.SolvedGreen
import com.sorudefteri.ui.theme.SolvedGreenContainer
import com.sorudefteri.ui.theme.TytBadgeColor
import com.sorudefteri.ui.theme.TytBadgeContainer
import com.sorudefteri.ui.theme.UnsolvedOrange
import com.sorudefteri.ui.theme.UnsolvedOrangeContainer
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Kütüphane listesindeki soru kartı bileşeni.
 */
@OptIn(ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SoruKarti(
    soru: Soru,
    onClick: () -> Unit,
    onToggleSolved: (() -> Unit)? = null,
    onToggleFavorite: (() -> Unit)? = null,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isTyt = soru.examType.equals("TYT", ignoreCase = true)
    val dersAdi = DersTaksonomisi.getDersName(soru.subjectId)

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 6.dp
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Görsel Thumbnail Alanı
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                val imageModel = if (soru.imagePath.startsWith("http") || soru.imagePath.startsWith("content://")) {
                    soru.imagePath
                } else {
                    File(soru.imagePath)
                }

                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imageModel)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Soru Görseli",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    error = {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ImageNotSupported,
                                    contentDescription = "Görsel yüklenemedi",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Görsel Yok",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                )

                // Görsel Üzerindeki Rozetler (Top-Start: Seçim veya TYT/AYT, Top-End: Favori & AI)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSelectionMode) {
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.6f)
                        ) {
                            Icon(
                                imageVector = if (isSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                contentDescription = if (isSelected) "Seçili" else "Seçilmedi",
                                tint = Color.White,
                                modifier = Modifier
                                    .padding(4.dp)
                                    .size(16.dp)
                            )
                        }
                    } else {
                        // TYT / AYT Rozeti
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isTyt) TytBadgeContainer else AytBadgeContainer,
                            border = BorderStroke(
                                1.dp,
                                if (isTyt) TytBadgeColor.copy(alpha = 0.6f) else AytBadgeColor.copy(alpha = 0.6f)
                            )
                        ) {
                            Text(
                                text = if (isTyt) "TYT" else "AYT",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isTyt) TytBadgeColor else AytBadgeColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Favori butonu
                        if (onToggleFavorite != null || soru.isFavorite) {
                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .size(28.dp)
                                    .clickable(enabled = onToggleFavorite != null) { onToggleFavorite?.invoke() }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (soru.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = "Favori",
                                        tint = if (soru.isFavorite) Color(0xFFFFB300) else Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        // AI Çözüm rozeti varsa göster
                        if (!soru.aiExplanation.isNullOrBlank()) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shadowElevation = 2.dp
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "AI Çözümü Mevcut",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .size(14.dp)
                                    )
                            }
                        }
                    }
                }
            }

            // Kart İçerik Alanı
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                // Ders ve Konu Bilgisi
                Text(
                    text = dersAdi,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = soru.topic.ifBlank { "Konu Belirtilmemiş" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Rozetler (Cevap Şıkkı, Çözümlü, Hata Tipi)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Doğru Cevap Şıkkı Rozeti
                    if (!soru.correctAnswer.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Cevap: ",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = soru.correctAnswer,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    // Çözümlü Rozeti
                    if (!soru.solutionImagePath.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = SolvedGreenContainer,
                            border = BorderStroke(1.dp, SolvedGreen.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = SolvedGreen,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "Çözümlü",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = SolvedGreen
                                )
                            }
                        }
                    }

                    // Hata Tipi Rozeti
                    if (soru.mistakeType != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MistakeBadgeContainer,
                            border = BorderStroke(1.dp, MistakeBadgeColor.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = soru.mistakeType.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MistakeBadgeColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Alt Satır: Tarih ve Çözüldü Durumu
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val dateStr = formatTimestamp(soru.createdAt)

                    Text(
                        text = dateStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Çözüldü / Çözülmedi Durum Butonu/Rozeti
                    val solvedBgColor by animateColorAsState(
                        targetValue = if (soru.isSolved) SolvedGreenContainer else UnsolvedOrangeContainer,
                        animationSpec = tween(300),
                        label = "solvedBg"
                    )
                    val solvedTextColor by animateColorAsState(
                        targetValue = if (soru.isSolved) SolvedGreen else UnsolvedOrange,
                        animationSpec = tween(300),
                        label = "solvedText"
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = solvedBgColor,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).then(
                            if (onToggleSolved != null) {
                                Modifier.clickable { onToggleSolved() }
                            } else Modifier
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (soru.isSolved) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = if (soru.isSolved) "Çözüldü" else "Çözülmedi",
                                tint = solvedTextColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (soru.isSolved) "Çözüldü" else "Çözülmedi",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = solvedTextColor
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd MMM yyyy", Locale("tr", "TR"))
    return sdf.format(Date(timestamp))
}
