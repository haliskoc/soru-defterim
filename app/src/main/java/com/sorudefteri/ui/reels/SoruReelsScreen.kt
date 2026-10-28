package com.sorudefteri.ui.reels

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.sorudefteri.domain.DersTaksonomisi
import com.sorudefteri.domain.model.ReelsItem
import com.sorudefteri.domain.model.Soru
import com.sorudefteri.ui.ortak.ShareHelper
import com.sorudefteri.ui.ortak.math.CombinedMathMarkdownText
import com.sorudefteri.ui.ortak.math.SmartMathContent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * 🎬 Soru Reels Ekranı (Dikey Kaydırmalı, Hızlı Çözüm, Beğeni ve Hap Bilgi Akışı)
 */

/**
 * Reels sayfasının içerik alanı boşluğu. Görsel ve metin dalları AYNI değeri
 * kullanmalı; önceden farklıydı ve imagePath değişince metin bloğu zıplıyordu.
 * TELEFON: sağ bar payı 74→64dp (içerik ~10dp daha geniş), alt pay 120→150dp (şıklar+nav).
 */
private val REELS_ICERIK_PADDING = PaddingValues(top = 76.dp, bottom = 150.dp, start = 16.dp, end = 64.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoruReelsScreen(
    onNavigateToDetail: (Long) -> Unit,
    viewModel: ReelsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    if (uiState.isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "🎬 Reels Akışı Hazırlanıyor...",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
        return
    }

    if (uiState.items.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Text(text = "🎬", fontSize = 54.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Henüz Gösterilecek Soru Yok",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Kütüphaneye yeni sorular ekleyerek veya test çözerek Reels akışını başlatabilirsiniz.",
                    color = Color.LightGray,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(onClick = { viewModel.shuffleFeed() }) {
                    Icon(Icons.Default.Shuffle, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Hap Bilgileri Yükle")
                }
            }
        }
        return
    }

    val pagerState = rememberPagerState(pageCount = { uiState.items.size })

    // PERF: Sonraki sayfanın görselini önden ısıt (kaydırmanın ortasında decode başlamasın).
    LaunchedEffect(pagerState.settledPage, uiState.items.size) {
        val next = pagerState.settledPage + 1
        if (next < uiState.items.size) {
            val nextItem = uiState.items.getOrNull(next)
            if (nextItem is ReelsItem.SoruItem) {
                val yol = nextItem.soru.imagePath
                if (yol.isNotBlank()) {
                    try {
                        val f = File(yol)
                        if (f.exists() && f.length() > 0) {
                            context.imageLoader.enqueue(
                                ImageRequest.Builder(context)
                                    .data(f)
                                    .size(1080, 1350)
                                    .memoryCacheKey("reels_${nextItem.soru.id}_high")
                                    .build()
                            )
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(
            state = pagerState,
            key = { index -> uiState.items.getOrNull(index)?.id ?: index },
            // PERF: Komşu sayfa bestelensin; kademeli görsel + prefetch ile
            // kaydırma ortasında decode başlamaz.
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val isSettledPage = remember(pagerState, page) {
                derivedStateOf { pagerState.settledPage == page }
            }.value
            when (val item = uiState.items[page]) {
                is ReelsItem.SoruItem -> {
                    SoruReelsPage(
                        soru = item.soru,
                        isFavorited = uiState.favoritedQuestionIds.contains(item.soru.id),
                        userSelectedOption = uiState.selectedOptions[item.soru.id],
                        isAnswerCorrect = uiState.answeredStatus[item.soru.id],
                        onToggleFavorite = { viewModel.toggleFavorite(item.soru) },
                        onOptionSelected = { opt -> viewModel.answerQuestion(item.soru, opt) },
                        onOpenComments = { viewModel.openCommentsSheet(item.soru) },
                        onShuffle = { viewModel.shuffleFeed() },
                        onDetailClick = { onNavigateToDetail(item.soru.id) },
                        onShare = {
                            ShareHelper.shareQuestion(
                                context = context,
                                imagePath = item.soru.imagePath,
                                dersAdi = item.soru.subjectId,
                                topic = item.soru.topic,
                                correctAnswer = item.soru.correctAnswer,
                                note = item.soru.note,
                                aiSolution = item.soru.aiExplanation
                            )
                        },
                        isSettledPage = isSettledPage
                    )
                }
                is ReelsItem.FlashcardItem -> {
                    FlashcardReelsPage(
                        item = item,
                        onShuffle = { viewModel.shuffleFeed() }
                    )
                }
            }
        }

        // Bildirim Toast / Banner
        uiState.feedbackMessage?.let { msg ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 16.dp, start = 20.dp, end = 20.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (msg.contains("Tebrikler")) Color(0xFF166534) else Color(0xFF991B1B),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = msg,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
            LaunchedEffect(msg) {
                delay(2400)
                viewModel.clearFeedbackMessage()
            }
        }

        // Çözüm & Yorum Paneli (Reels BottomSheet)
        uiState.commentsSoru?.let { soru ->
            ReelsSolutionBottomSheet(
                soru = soru,
                onDismiss = { viewModel.closeCommentsSheet() }
            )
        }
    }
}

/**
 * 📱 Tekil Soru Reels Sayfası
 */
@Composable
private fun SoruReelsPage(
    soru: Soru,
    isFavorited: Boolean,
    userSelectedOption: String?,
    isAnswerCorrect: Boolean?,
    onToggleFavorite: () -> Unit,
    onOptionSelected: (String) -> Unit,
    onOpenComments: () -> Unit,
    onShuffle: () -> Unit,
    onDetailClick: () -> Unit,
    onShare: () -> Unit,
    // PERF: Sayfa settled + durgunsa tam çözünürlük, kaydırırken düşük.
    isSettledPage: Boolean = true
) {
    var showBurstHeart by remember { mutableStateOf(false) }
    val heartScale = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        onToggleFavorite()
                        showBurstHeart = true
                        coroutineScope.launch {
                            heartScale.snapTo(0.2f)
                            heartScale.animateTo(
                                targetValue = 1.4f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            )
                            delay(350)
                            showBurstHeart = false
                        }
                    }
                )
            }
    ) {
        // Soru Görseli veya Metin
        //
        // DÜZELTME 1: `File(...).exists()` her yeniden bileşimde ÇALIŞIYORDU
        // (bileşim sırasında senkron disk I/O) ve sonucu her seferinde yeniden
        // hesaplanıyordu. remember ile bir kez hesaplanıyor.
        // DÜZELTME 2: Görsel ve metin dalları FARKLI padding kullanıyordu
        // (70/8/68  vs  80/20/74). imagePath değiştiğinde metin bloğu 10-20 dp
        // zıplıyordu. Artık ikisi de aynı sabiti kullanıyor.
        val imageFile = remember(soru.imagePath) { File(soru.imagePath) }
        val gorselVar = remember(imageFile) { imageFile.exists() && imageFile.length() > 0 }
        if (gorselVar) {
            // PERF: Kaydırırken düşük çözünürlük (540px), sayfa durunca tam
            // (1080×1350). Bellek anahtarı katmana göre ayrılır; prefetch
            // (yukarıda) high katmanını önden ısıtır.
            val targetW = if (isSettledPage) 1080 else 540
            val targetH = if (isSettledPage) 1350 else 675
            val cacheKey = remember(soru.id, isSettledPage) {
                "reels_${soru.id}_${if (isSettledPage) "high" else "low"}"
            }
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageFile)
                    .size(targetW, targetH)
                    .memoryCacheKey(cacheKey)
                    .crossfade(!isSettledPage)
                    .build(),
                contentDescription = "Soru",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxSize()
                    .align(Alignment.Center)
                    .padding(REELS_ICERIK_PADDING)
            )
        } else {
            // Görsel yoksa ortada şık kart halinde soru metnini göster
            //
            // DÜZELTME 3: contentAlignment = Center yerine TopCenter.
            // KaTeX bloğu yüksekliği WebView yüklenince bildirir; Center hizalama,
            // her yükseklik değişiminde kartın ÜST kenarını yukarı/aşağı kaydırıyor
            // (blok büyüdüğünde üst kenar yukarı gider) -> "metin kayıyor" izlenimi.
            // TopCenter'da dikey boşluk aşağıda kalır, konumlanmış metin KİPİTLENMEZ.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(REELS_ICERIK_PADDING),
                contentAlignment = Alignment.TopCenter
            ) {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    modifier = Modifier
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "📝 SORU METNİ",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF38BDF8)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        val textToRender = soru.questionText ?: soru.ocrMetni ?: "Soru metni bulunamadı."
                        SmartMathContent(
                            content = textToRender,
                            textColor = Color.White,
                            fontSizeSp = 15f
                        )
                    }
                }
            }
        }

        // Çift Tıklama Kalp Patlama Animasyonu
        if (showBurstHeart) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = Color(0xFFEF4444).copy(alpha = 0.9f),
                modifier = Modifier
                    .size(110.dp)
                    .align(Alignment.Center)
                    .scale(heartScale.value)
            )
        }

        // Üst Bar: Ders ve Konu Rozetleri
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val examBg = if (soru.examType == "TYT") Color(0xFF0284C7) else Color(0xFFD97706)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = examBg
                ) {
                    Text(
                        text = soru.examType,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                val dersAdi = DersTaksonomisi.getDersAdi(soru.examType, soru.subjectId)
                Text(
                    text = "$dersAdi • ${soru.topic}",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }

            soru.mistakeType?.let { m ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFEA580C).copy(alpha = 0.85f)
                ) {
                    Text(
                        text = m.displayName,
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        fontSize = 10.5.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Sağ Kenar Eylem Çubuğu (Reels Bar) — TELEFON: 48dp hedef, nefes alan aralık
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 8.dp, bottom = 150.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Beğen / Kalp
            ReelsActionButton(
                icon = if (isFavorited) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                tint = if (isFavorited) Color(0xFFEF4444) else Color.White,
                label = if (isFavorited) "Beğenildi" else "Beğen",
                onClick = onToggleFavorite
            )

            // 2. Çözüm & Yorumlar
            ReelsActionButton(
                icon = Icons.AutoMirrored.Filled.Comment,
                tint = Color(0xFF38BDF8),
                label = "Çözüm",
                onClick = onOpenComments
            )

            // 3. Paylaş
            ReelsActionButton(
                icon = Icons.Default.Share,
                tint = Color.White,
                label = "Paylaş",
                onClick = onShare
            )

            // 4. Karıştır / Keşfet
            ReelsActionButton(
                icon = Icons.Default.Shuffle,
                tint = Color(0xFFFBBF24),
                label = "Karıştır",
                onClick = onShuffle
            )
        }

        // Alt Kısım: İnteraktif Şıklar & Soru Detay Linki
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f), Color.Black)
                    )
                )
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Şıklar (A, B, C, D, E)
            Row(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("A", "B", "C", "D", "E").forEach { option ->
                    val isSelected = userSelectedOption == option
                    val isThisCorrect = soru.correctAnswer?.equals(option, ignoreCase = true) == true
                    val hasAnswered = userSelectedOption != null

                    val btnColor = when {
                        hasAnswered && isThisCorrect -> Color(0xFF16A34A) // Yeşil
                        hasAnswered && isSelected && !isThisCorrect -> Color(0xFFDC2626) // Kırmızı
                        else -> Color(0xFF1E293B).copy(alpha = 0.9f)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(btnColor)
                            .border(
                                width = if (isSelected || (hasAnswered && isThisCorrect)) 2.dp else 1.dp,
                                color = if (hasAnswered && isThisCorrect) Color(0xFF4ADE80) else Color(0xFF475569),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                if (userSelectedOption == null) {
                                    onOptionSelected(option)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = option,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Detay & İpucu Alt Bandı — TELEFON: dikey iki satır (taşma yok)
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Adım 1: Şıkkı işaretle → Adım 2: 💬 Çözüm ikonuna dokun",
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Tüm Detay ➔",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onDetailClick)
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                )
            }
        }
    }
}

/**
 * ⚡ YKS Hap Bilgi & Formül Reels Sayfası
 */
@Composable
private fun FlashcardReelsPage(
    item: ReelsItem.FlashcardItem,
    onShuffle: () -> Unit
) {
    val bgColors = remember(item.gradientColors) {
        item.gradientColors.map { Color(it) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(bgColors)),
        // DÜZELTME: Center -> TopCenter. Her sayfa en az bir KaTeX WebView
        // içeriyor (ReelsItem.kt'teki 8 formül kartının tamamı). WebView
        // yüksekliği yüklendikten sonra bildiriliyordu ve Center hizalama
        // başlık/rozet/formül/ipucu bloğunu YAKLAŞIK 0.5 sn sonra yukarı
        // aşağı kaydırıyordu. TopCenter'da dikey boşluk aşağıda toplanır.
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp)
                // DÜZELTME: Flashcard içeriği ekran yüksekliğini aşarsa
                // erişilemez şekilde taşıyordu. Dikey kaydırma ekliyoruz;
                // içerik sığdığında kaydırma boş alan kaplamaz, dikey hamle
                // sayfalayıcıya geçer.
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.15f),
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = item.categoryEmoji, fontSize = 32.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.White.copy(alpha = 0.2f)
            ) {
                Text(
                    text = "${item.ders.uppercase()} • ${item.konu.uppercase()}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = item.title,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 22.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                SmartMathContent(
                    content = item.summary,
                    textColor = Color(0xFFE2E8F0),
                    fontSizeSp = 14.5f
                )
            }

            // Vektörel Formül Kutusu
            if (!item.formula.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(20.dp))
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.35f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        SmartMathContent(
                            content = item.formula,
                            textColor = Color(0xFF38BDF8),
                            fontSizeSp = 18f,
                            displayMode = true
                        )
                    }
                }
            }

            // Altın Kural / Sınav İpucu
            if (!item.tip.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        // DÜZELTME: `Modifier.weight(1f)` KALDIRILDI. Bu Column
                        // sarmalayan (wrap-content) bir Column; ağırlıklı çocuk,
                        // yukarıdaki bloklar yüksekliği doldurduğunda 0'a
                        // düşüyor ve ipucu metni sessizce KIRPILIYORDU. Üstteki
                        // KaTeX formülü geç yükseklik bildirince "kalan yer"
                        // daha da azalıyor, yani aynı metin iki kez kırpılabiliyordu.
                        // Doğal yüksekliğe bırakıldı; Column aşağıda
                        // dikey kaydırılabilir olduğu için uzun içerik de erişilebilir.
                        Box(modifier = Modifier.weight(1f, fill = false)) {
                            SmartMathContent(
                                content = item.tip,
                                textColor = Color(0xFFFEF3C7),
                                fontSizeSp = 13f
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "👆 Bir sonraki soruya geçmek için yukarı kaydırın",
                color = Color.LightGray.copy(alpha = 0.7f),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun ReelsActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Surface(
            shape = CircleShape,
            color = Color(0xFF1E293B).copy(alpha = 0.85f),
            modifier = Modifier.size(46.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 💬 Reels Çözüm ve Yorumlar Bottom Sheet Paneli
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReelsSolutionBottomSheet(
    soru: Soru,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(40.dp)
                    .height(4.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.outlineVariant
            ) {}
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // DÜZELTME: Sheet'in yüksekliği içerik yüksekliğine göre
                // belirlendiği için, her KaTeX bloğu yükseklik bildirdikçe sheet
                // AŞAĞIDAN YUKARIYA büyüyor ve "Çözüm & Analiz Paneli" başlığı ile
                // tüm çözüm metni ekranda yukarı doğru KAYIYORDU. Sabit yükseklik
                // vererek üst kenarı kilitliyoruz; içerik kendi içinde kayar.
                .fillMaxHeight(0.92f)
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "💬 Çözüm & Analiz Paneli",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (!soru.correctAnswer.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF16A34A).copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "✓ Doğru Cevap: ${soru.correctAnswer.uppercase()}",
                            color = Color(0xFF16A34A),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Kişisel Not Varsa
            if (!soru.note.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFFEF9C3),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE047)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "📝 Öğrenci Notu:",
                            color = Color(0xFF854D0E),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = soru.note,
                            color = Color(0xFF713F12),
                            fontSize = 13.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // AI Çözümü (PERF: tek WebView'de birleştirilmiş kartlar)
            if (!soru.aiExplanation.isNullOrBlank()) {
                CombinedMathMarkdownText(
                    text = soru.aiExplanation,
                    modifier = Modifier.fillMaxWidth(),
                    fontSizeSp = 14f
                )
            } else {
                Text(
                    text = "Bu soru için henüz kaydedilmiş bir yapay zeka çözümü bulunmuyor. Soru Detayı ekranından 'AI Çözümü Getir' butonuna dokunabilirsiniz.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.navigationBarsPadding().height(24.dp))
        }
    }
}
