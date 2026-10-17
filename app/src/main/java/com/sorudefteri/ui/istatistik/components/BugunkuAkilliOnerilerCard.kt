package com.sorudefteri.ui.istatistik.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CrisisAlert
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sorudefteri.data.local.entity.TestSonucEntity
import com.sorudefteri.domain.DersTaksonomisi
import com.sorudefteri.domain.model.HataTipiDagilimi
import com.sorudefteri.domain.model.Soru
import com.sorudefteri.ui.theme.AytBadgeColor
import com.sorudefteri.ui.theme.AytBadgeContainer
import com.sorudefteri.ui.theme.ErrorRed
import com.sorudefteri.ui.theme.SuccessGreen
import com.sorudefteri.ui.theme.TytBadgeColor
import com.sorudefteri.ui.theme.TytBadgeContainer
import com.sorudefteri.ui.theme.WarningOrange
import java.util.Locale

/**
 * Zayıf konu önerisi için yardımcı veri modeli
 */
data class WeakTopicSuggestion(
    val examType: String,
    val subjectId: String,
    val topic: String,
    val mistakeCount: Int,
    val unsolvedCount: Int,
    val totalQuestions: Int
)

/**
 * Bugünkü Akıllı Öneriler & Tekrar Hatırlatıcıları Kartı.
 * İstatistik ekranının merkezinde yer alarak öğrencinin unutulma eğrisindeki
 * tekrarlarını ve en çok hata yaptığı konuları aksiyona dönüştürür.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BugunkuAkilliOnerilerCard(
    dueCount: Int,
    allQuestions: List<Soru>,
    mistakeStats: List<HataTipiDagilimi>,
    recentTestResults: List<TestSonucEntity>,
    onStartDueTest: () -> Unit,
    onStartTargetedTest: (examType: String, subjectId: String, topic: String) -> Unit,
    modifier: Modifier = Modifier
) {
    // 1) En çok hata yapılan ve çözülemeyen konuları analiz et
    val weakTopics = remember(allQuestions) {
        if (allQuestions.isEmpty()) emptyList()
        else {
            allQuestions
                .groupBy { "${it.examType}_${it.subjectId}_${it.topic.trim().lowercase()}" }
                .mapNotNull { (_, questions) ->
                    val sample = questions.firstOrNull() ?: return@mapNotNull null
                    val mistakeCount = questions.count { it.mistakeType != null }
                    val unsolvedCount = questions.count { !it.isSolved }
                    if (mistakeCount == 0 && unsolvedCount == 0) return@mapNotNull null
                    WeakTopicSuggestion(
                        examType = sample.examType,
                        subjectId = sample.subjectId,
                        topic = sample.topic.trim(),
                        mistakeCount = mistakeCount,
                        unsolvedCount = unsolvedCount,
                        totalQuestions = questions.size
                    )
                }
                .sortedWith(
                    compareByDescending<WeakTopicSuggestion> { it.mistakeCount }
                        .thenByDescending { it.unsolvedCount }
                )
                .take(3)
        }
    }

    // 2) En sık karşılaşılan hata tipini tespit et
    val topMistake = remember(mistakeStats) {
        mistakeStats.maxByOrNull { it.count }
    }

    // 3) Net gelişim trendi
    val latestTest = remember(recentTestResults) {
        recentTestResults.firstOrNull()
    }
    val avgNet = remember(recentTestResults) {
        if (recentTestResults.isNotEmpty()) recentTestResults.map { it.netCount }.average().toFloat() else 0f
    }

    val gradientBrush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.05f),
            MaterialTheme.colorScheme.surface
        )
    )

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradientBrush)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Başlık
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Bugünkü Akıllı Öneriler",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Kişisel Analiz & Tekrar Hatırlatıcıları",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "Aktif Plan",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // A) Günün Kurtarma Paketi (Spaced Repetition) Hatırlatıcısı
            if (dueCount > 0) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                text = "🎯 Günün Kurtarma Paketi: $dueCount Soru Hazır!",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Text(
                            text = "Aralıklı tekrar (Spaced Repetition) algoritmasına göre hafızandan silinmeye yaklaşan $dueCount soru tespit edildi. Bilgileri taze tutmak için şimdi çöz!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = onStartDueTest,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Tekrar Testine Başla ⚡", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = SuccessGreen.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = SuccessGreen,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Tüm tekrarların güncel! Bugün kurtarma paketinde bekleyen soru yok.",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // B) En Çok Hata Yapılan Konular & Hedefli Pekiştirme
            if (weakTopics.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CrisisAlert,
                            contentDescription = null,
                            tint = ErrorRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "En Çok Hata Yaptığın Konular (Hemen Pekiştir)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    weakTopics.forEach { item ->
                        val dersAdi = DersTaksonomisi.getDersAdi(item.examType, item.subjectId)
                        val isTyt = item.examType.equals("TYT", ignoreCase = true)
                        val badgeBg = if (isTyt) TytBadgeContainer else AytBadgeContainer
                        val badgeFg = if (isTyt) TytBadgeColor else AytBadgeColor

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = badgeBg
                                        ) {
                                            Text(
                                                text = item.examType,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = badgeFg,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Text(
                                            text = dersAdi,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = item.topic,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(modifier = Modifier.height(2.dp))

                                    Text(
                                        text = "${item.mistakeCount} Hatalı Soru • Toplam ${item.totalQuestions} Soru",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (item.mistakeCount > 0) ErrorRed else WarningOrange,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                FilledTonalButton(
                                    onClick = {
                                        onStartTargetedTest(item.examType, item.subjectId, item.topic)
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = "Pekiştir 🎯",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // C) Akıllı Koçluk Notu (En Sık Yapılan Hata Tipi Analizi)
            if (topMistake != null && topMistake.count > 0) {
                val advice = getCoachingAdvice(topMistake.displayName)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Akıllı Koçluk Analizi: ${topMistake.displayName} (${topMistake.count} Soru)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = advice,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }
            }

            // D) Net Gelişim Motivasyonu
            if (latestTest != null) {
                val formattedLatest = String.format(Locale.getDefault(), "%.1f", latestTest.netCount)
                val formattedAvg = String.format(Locale.getDefault(), "%.1f", avgNet)
                val isAhead = latestTest.netCount >= avgNet
                val trendColor = if (isAhead) SuccessGreen else WarningOrange
                val trendText = if (isAhead) {
                    "Son test netin ($formattedLatest), genel ortalamanın ($formattedAvg) üzerinde! Bu ivmeyi koru."
                } else {
                    "Son test netin ($formattedLatest) net. Eksik konuları pekiştirerek ortalamanı ($formattedAvg) hızla yükseltebilirsin."
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = trendColor.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TrendingUp,
                            contentDescription = null,
                            tint = trendColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = trendText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * Hata tipine özel pedagojik koçluk tavsiyeleri üretir.
 */
private fun getCoachingAdvice(mistakeName: String): String {
    val clean = mistakeName.lowercase(Locale("tr", "TR"))
    return when {
        clean.contains("dikkat") ->
            "En çok 'Dikkat Hatası' yapıyorsun. Soru köklerindeki olumsuz ifadelere (değildir, olamaz) ve işlem adımlarına odaklanarak net kaybını önleyebilirsin."
        clean.contains("bilgi") ->
            "En sık 'Bilgi Eksikliği' nedeniyle soru kaçırıyorsun. İlgili konunun formül ve temel kavramlarına göz atıp pekiştirme testi çözmek netlerini hızla artıracaktır."
        clean.contains("zaman") || clean.contains("süre") ->
            "Zaman yönetimi nedeniyle puan kaybediyorsun. Takıldığın sorularda inatlaşmayıp turlama taktiği uygulamak gerçek sınavda sana 10-15 dakika kazandırır."
        clean.contains("yorum") ->
            "Yorumlama kaynaklı hatalar mevcut. Soru metnindeki doğrudan verilen ipuçlarına bağlı kalıp kendi kişisel ön kabullerini katmamaya özen göster."
        clean.contains("işlem") || clean.contains("islem") ->
            "İşlem hatası yapma eğilimin var. Zihinden hesaplama yerine karalama alanını aktif kullanmak bu hataları sıfıra indirebilir."
        else ->
            "Hatalarını analiz edip belirli aralıklarla yeniden çözmek eksiklerini kalıcı öğrenmeye dönüştürecektir."
    }
}
