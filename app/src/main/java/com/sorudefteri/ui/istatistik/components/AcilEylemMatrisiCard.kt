package com.sorudefteri.ui.istatistik.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CrisisAlert
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sorudefteri.domain.DersTaksonomisi
import com.sorudefteri.domain.model.ActionQuadrant
import com.sorudefteri.domain.model.QuadrantTopicItem
import com.sorudefteri.domain.model.Soru
import com.sorudefteri.domain.model.extractUrgentTopics
import com.sorudefteri.domain.model.partitionTopicsIntoQuadrants

/**
 * ÖSYM Ağırlıklı "Acil Eylem Matrisi" (Priority Quadrant)
 *
 * 2x2 Öncelik Matrisi:
 * - X Ekseni: ÖSYM Soru Frekansı (Yüksek vs Orta/Düşük)
 * - Y Ekseni: Öğrencinin Hata / Çözülmemiş Sayısı
 *
 * Bölgeler:
 * - 🚨 Acil Müdahale (Yüksek Frekans, Çok Hata): "Sınavı kazandıracak / kaybettirecek acil konular!"
 * - 🛡️ Güçlü Kale (Yüksek Frekans, Az Hata): "Netlerini garantileyen konular"
 * - ⚠️ Pekiştir (Düşük Frekans, Çok Hata): "Fırsat buldukça bak"
 * - ☕ Güvenli (Düşük Frekans, Az Hata): "Sorun yok"
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AcilEylemMatrisiCard(
    allQuestions: List<Soru>,
    modifier: Modifier = Modifier,
    onStartTargetedTest: (examType: String, subjectId: String, topic: String) -> Unit
) {
    val quadrantData = remember(allQuestions) {
        partitionTopicsIntoQuadrants(allQuestions)
    }

    val urgentTopics = remember(quadrantData) {
        extractUrgentTopics(quadrantData, limit = 3)
    }

    var expandedQuadrant by remember { mutableStateOf<ActionQuadrant?>(null) }

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
            // Başlık & Açıklama
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF5350).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CrisisAlert,
                        contentDescription = null,
                        tint = Color(0xFFEF5350),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column {
                    Text(
                        text = "ÖSYM Ağırlıklı Acil Eylem Matrisi",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "ÖSYM soru frekansı x Öğrenci hata analizi",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 1. ÖNE ÇIKAN ACİL 3 KONU (Top 3 Urgent Topics)
            if (urgentTopics.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFEF5350).copy(alpha = 0.07f),
                    border = BorderStroke(1.5.dp, Color(0xFFEF5350).copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(text = "🚨", fontSize = 16.sp)
                            Text(
                                text = "Sınavı Kazandıracak Acil Konular",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD32F2F)
                            )
                        }
                        Text(
                            text = "Bu konular son 5 yılın YKS sınavlarında yüksek frekansa sahip olup, en çok hata yaptığın yerlerdir. İlk olarak buraya odaklan!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )

                        // Konu Listesi ve [Çalışmaya Başla] Butonları
                        urgentTopics.forEachIndexed { index, item ->
                            UrgentTopicRow(
                                rank = index + 1,
                                item = item,
                                onStartTest = {
                                    onStartTargetedTest(item.examType, item.subjectId, item.topicName)
                                }
                            )
                        }
                    }
                }
            } else if (allQuestions.isNotEmpty()) {
                // Hata yoksa tebrik kartı
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF43A047).copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, Color(0xFF43A047).copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(text = "🛡️", fontSize = 24.sp)
                        Column {
                            Text(
                                text = "Tebrikler! Acil Müdahale Konun Yok",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32)
                            )
                            Text(
                                text = "Yüksek soru ağırlığına sahip konularda netlerin güvende.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 2. 2x2 MATRİS GÖRSELİ (Axes & 4 Quadrants)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Matris Başlığı ve Eksen Rehberi
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Öncelik Haritası (2x2)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Y: Hata | X: ÖSYM Frekansı",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Üst Satır: Acil Müdahale (Sol) & Güçlü Kale (Sağ) -> [Yüksek Frekans]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val acilList = quadrantData[ActionQuadrant.ACIL_MUDAHALE].orEmpty()
                    QuadrantCell(
                        quadrant = ActionQuadrant.ACIL_MUDAHALE,
                        count = acilList.size,
                        isExpanded = expandedQuadrant == ActionQuadrant.ACIL_MUDAHALE,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            expandedQuadrant = if (expandedQuadrant == ActionQuadrant.ACIL_MUDAHALE) null else ActionQuadrant.ACIL_MUDAHALE
                        }
                    )

                    val gucluList = quadrantData[ActionQuadrant.GUCLU_KALE].orEmpty()
                    QuadrantCell(
                        quadrant = ActionQuadrant.GUCLU_KALE,
                        count = gucluList.size,
                        isExpanded = expandedQuadrant == ActionQuadrant.GUCLU_KALE,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            expandedQuadrant = if (expandedQuadrant == ActionQuadrant.GUCLU_KALE) null else ActionQuadrant.GUCLU_KALE
                        }
                    )
                }

                // Alt Satır: Pekiştir (Sol) & Güvenli (Sağ) -> [Düşük / Orta Frekans]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val pekistirList = quadrantData[ActionQuadrant.PEKISTIR].orEmpty()
                    QuadrantCell(
                        quadrant = ActionQuadrant.PEKISTIR,
                        count = pekistirList.size,
                        isExpanded = expandedQuadrant == ActionQuadrant.PEKISTIR,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            expandedQuadrant = if (expandedQuadrant == ActionQuadrant.PEKISTIR) null else ActionQuadrant.PEKISTIR
                        }
                    )

                    val guvenliList = quadrantData[ActionQuadrant.GUVENLI].orEmpty()
                    QuadrantCell(
                        quadrant = ActionQuadrant.GUVENLI,
                        count = guvenliList.size,
                        isExpanded = expandedQuadrant == ActionQuadrant.GUVENLI,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            expandedQuadrant = if (expandedQuadrant == ActionQuadrant.GUVENLI) null else ActionQuadrant.GUVENLI
                        }
                    )
                }
            }

            // 3. SEÇİLİ BÖLGE DETAY AÇILIR LİSTESİ (Expanded Quadrant Topics)
            AnimatedVisibility(
                visible = expandedQuadrant != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                expandedQuadrant?.let { quad ->
                    val topics = quadrantData[quad].orEmpty()
                    ExpandedQuadrantDetail(
                        quadrant = quad,
                        topics = topics,
                        onStartTest = onStartTargetedTest
                    )
                }
            }
        }
    }
}

/**
 * Acil Konu Satırı ([Çalışmaya Başla] Hızlı Aksiyon Butonu)
 */
@Composable
private fun UrgentTopicRow(
    rank: Int,
    item: QuadrantTopicItem,
    onStartTest: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, Color(0xFFEF5350).copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Sıra Rozeti
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF5350)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = rank.toString(),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.topicName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "${item.examType} • ${DersTaksonomisi.getDersAdi(item.examType, item.subjectId)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${item.errorCount} Hata",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFD32F2F),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // [Çalışmaya Başla] Hızlı Aksiyon Butonu
            Button(
                onClick = onStartTest,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350)),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Çalışmaya Başla",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * 2x2 Matris Hücre Görünümü
 */
@Composable
private fun QuadrantCell(
    quadrant: ActionQuadrant,
    count: Int,
    isExpanded: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val badgeColor = Color(quadrant.badgeColorHex)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = badgeColor.copy(alpha = if (isExpanded) 0.18f else 0.08f),
        border = BorderStroke(if (isExpanded) 2.dp else 1.dp, badgeColor.copy(alpha = if (isExpanded) 0.8f else 0.35f)),
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
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
                Text(text = quadrant.emoji, fontSize = 20.sp)
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = badgeColor.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "$count Konu",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = quadrant.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = badgeColor
            )

            Text(
                text = quadrant.subtitle,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = badgeColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Tıklanan bölgedeki konuların ayrıntılı dökümü
 */
@Composable
private fun ExpandedQuadrantDetail(
    quadrant: ActionQuadrant,
    topics: List<QuadrantTopicItem>,
    onStartTest: (examType: String, subjectId: String, topic: String) -> Unit
) {
    val badgeColor = Color(quadrant.badgeColorHex)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(text = quadrant.emoji)
                Text(
                    text = "${quadrant.title} Konuları (${topics.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = badgeColor
                )
            }

            if (topics.isEmpty()) {
                Text(
                    text = "Bu bölgede henüz analiz edilen bir konu bulunmuyor.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                topics.forEach { topic ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = topic.topicName,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${topic.examType} • ${DersTaksonomisi.getDersAdi(topic.examType, topic.subjectId)} • ${topic.errorCount} Hata / ${topic.totalCount} Soru",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Button(
                            onClick = { onStartTest(topic.examType, topic.subjectId, topic.topicName) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = badgeColor),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Test", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
