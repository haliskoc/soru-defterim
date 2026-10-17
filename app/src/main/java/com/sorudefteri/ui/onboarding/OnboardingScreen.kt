package com.sorudefteri.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sorudefteri.domain.model.Alan
import kotlinx.coroutines.launch

private data class FeaturePage(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val iconGradient: List<Color>,
    val tags: List<String>
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
    modifier: Modifier = Modifier,
    onOnboardingComplete: () -> Unit = {},
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val featurePages = listOf(
        FeaturePage(
            title = "AI Çözüm & Soru Analizi",
            description = "Soru fotoğrafını yükleyin; akıllı OCR ile metni otomatik okuyun, ders ve konuyu anında tespit edin. Gemini & Groq motorlarıyla LaTeX formüllü, adım adım yapay zeka çözümlerine ve benzer soru üretimlerine ulaşın.",
            icon = Icons.Default.AutoAwesome,
            iconGradient = listOf(Color(0xFF6366F1), Color(0xFF8B5CF6)),
            tags = listOf("Akıllı OCR", "Adım Adım Çözüm", "LaTeX Render", "Benzer Soru Üretimi")
        ),
        FeaturePage(
            title = "Aralıklı Tekrar (Spaced Repetition)",
            description = "Unutma eğrisine meydan okuyun! SuperMemo-2 algoritması hatalı ve çözülmemiş sorularınızı 1, 3, 7, 14 ve 30 günlük aralıklarla planlar. Her gün saat 19:00'da bildirim alarak düzenli tekrar yapın.",
            icon = Icons.Default.Timer,
            iconGradient = listOf(Color(0xFFF59E0B), Color(0xFFEA580C)),
            tags = listOf("SM-2 Algoritması", "19:00 Hatırlatıcı", "Aşama Takibi", "Kalıcı Hafıza")
        ),
        FeaturePage(
            title = "Kütüphane & Test Oluşturma",
            description = "Tüm soru arşivinizi ders, konu ve hata tipine göre düzenleyin. Eksiklerinize göre kişiselleştirilmiş deneme testleri oluşturup kendinizi süre kısıtlı sınav ortamında test edin.",
            icon = Icons.AutoMirrored.Filled.MenuBook,
            iconGradient = listOf(Color(0xFF10B981), Color(0xFF059669)),
            tags = listOf("Hata Tipi Filtresi", "Özel Deneme Testleri", "Performans Grafikleri", "Konu Dağılımı")
        ),
        FeaturePage(
            title = "Şifreli Güvenlik & Offline Çalışma",
            description = "Soru arşiviniz tamamen cihazınızda saklanır ve çevrimdışı kullanılabilir. API anahtarlarınız donanım destekli AES-256 GCM ile şifrelenir. Tek dokunuşla şifreli yedek paketi oluşturup yerel depolamada güvenle saklayın.",
            icon = Icons.Default.Security,
            iconGradient = listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8)),
            tags = listOf("AES-256 Şifreleme", "100% Çevrimdışı", "ZIP Yedekleme", "Yerel Depolama")
        )
    )

    val totalPages = featurePages.size + 1 // +1 for Mandatory Track Selection Page
    val pagerState = rememberPagerState(pageCount = { totalPages })

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Header Top Bar: Skip Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Soru Defterim",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                if (pagerState.currentPage < totalPages - 1) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(totalPages - 1)
                            }
                        }
                    ) {
                        Text(
                            text = "Atla",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(48.dp))
                }
            }

            // Pager Content
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                if (page < featurePages.size) {
                    FeatureSlideContent(feature = featurePages[page])
                } else {
                    TrackSelectionSlideContent(
                        selectedAlan = uiState.selectedAlan,
                        onAlanSelected = { viewModel.selectAlan(it) },
                        errorMessage = uiState.errorMessage
                    )
                }
            }

            // Bottom Navigation / Action Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Page Indicator Dots — TELEFON: adım sayılı (Adım 2/5)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Adım ${pagerState.currentPage + 1} / $totalPages",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                    repeat(totalPages) { index ->
                        val isSelected = pagerState.currentPage == index
                        val width by animateDpAsState(
                            targetValue = if (isSelected) 28.dp else 8.dp,
                            label = "dot_width"
                        )
                        val color by animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            label = "dot_color"
                        )
                        Box(
                            modifier = Modifier
                                .height(8.dp)
                                .width(width)
                                .clip(CircleShape)
                                .background(color)
                        )
                    }
                    }
                }

                // Action Buttons
                if (pagerState.currentPage < totalPages - 1) {
                    Button(
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Devam Et",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                } else {
                    // Final Track Selection Action Button
                    val isAlanSelected = uiState.selectedAlan != null
                    Button(
                        onClick = {
                            viewModel.completeOnboarding(onSuccess = onOnboardingComplete)
                        },
                        enabled = isAlanSelected && !uiState.isSaving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = if (isAlanSelected) "Uygulamaya Başla 🚀" else "Lütfen Bir Alan Seçin",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isAlanSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeatureSlideContent(
    feature: FeaturePage
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icon Badge Container
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(Brush.linearGradient(feature.iconGradient)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = feature.icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(56.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Title
        Text(
            text = feature.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Description
        Text(
            text = feature.description,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 24.sp
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Feature Tags
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            feature.tags.forEach { tag ->
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "✓ $tag",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackSelectionSlideContent(
    selectedAlan: Alan?,
    onAlanSelected: (Alan) -> Unit,
    errorMessage: String?
) {
    val scrollState = rememberScrollState()

    val trackList = listOf(
        TrackItem(
            alan = Alan.SAYISAL,
            title = "Sayısal",
            subtitle = "YKS (TYT + AYT)",
            icon = Icons.Default.Calculate,
            color = Color(0xFF6366F1),
            description = "Matematik, Geometri, Fizik, Kimya, Biyoloji"
        ),
        TrackItem(
            alan = Alan.ESIT_AGIRLIK,
            title = "Eşit Ağırlık",
            subtitle = "YKS (TYT + AYT)",
            icon = Icons.Default.Psychology,
            color = Color(0xFFEC4899),
            description = "Matematik, Geometri, Edebiyat, Tarih-1, Coğrafya-1"
        ),
        TrackItem(
            alan = Alan.SOZEL,
            title = "Sözel",
            subtitle = "YKS (TYT + AYT)",
            icon = Icons.Default.HistoryEdu,
            color = Color(0xFFF59E0B),
            description = "Edebiyat, Tarih-1/2, Coğrafya-1/2, Felsefe Grubu, Din"
        ),
        TrackItem(
            alan = Alan.YDT,
            title = "YDT (Dil)",
            subtitle = "Yabancı Dil Testi",
            icon = Icons.Default.Language,
            color = Color(0xFF10B981),
            description = "İngilizce Dilbilgisi, Kelime, Okuma & Çeviri"
        ),
        TrackItem(
            alan = Alan.LGS,
            title = "LGS (8. Sınıf)",
            subtitle = "Liselere Geçiş",
            icon = Icons.Default.School,
            color = Color(0xFF3B82F6),
            description = "Türkçe, Matematik, Fen, İnkılap, Din, İngilizce"
        ),
        TrackItem(
            alan = Alan.KPSS,
            title = "KPSS",
            subtitle = "Genel Yetenek & Genel Kültür",
            icon = Icons.Default.Quiz,
            color = Color(0xFF8B5CF6),
            description = "Genel Yetenek (Türkçe, Matematik), Genel Kültür (Tarih, Coğ, Vat, Güncel)"
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Hazırlık Alanını Seç 🎯",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Ders listesi ve konu kataloğu hedefine göre optimize edilecektir. (Daha sonra Ayarlar'dan değiştirebilirsin)",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 6 Track Cards
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            trackList.forEach { item ->
                val isSelected = selectedAlan == item.alan
                val border = if (isSelected) {
                    androidx.compose.foundation.BorderStroke(2.dp, item.color)
                } else {
                    androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onAlanSelected(item.alan) },
                    shape = RoundedCornerShape(16.dp),
                    border = border,
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) item.color.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 3.dp else 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Track Icon
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) item.color else item.color.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = if (isSelected) Color.White else item.color,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Text content
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) item.color else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "• ${item.subtitle}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2
                            )
                        }

                        // Selected indicator icon
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Seçildi",
                                tint = item.color,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

private data class TrackItem(
    val alan: Alan,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val color: Color,
    val description: String
)
