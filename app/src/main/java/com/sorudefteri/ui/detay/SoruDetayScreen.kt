package com.sorudefteri.ui.detay

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.sorudefteri.domain.DersTaksonomisi
import com.sorudefteri.domain.model.HataTipi
import com.sorudefteri.domain.model.Soru
import com.sorudefteri.ui.ortak.ZoomableImageDialog
import com.sorudefteri.ui.ortak.math.MathMarkdownText
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
 * Soru Detay Ekranı.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SoruDetayScreen(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
    onNavigateToAyarlar: () -> Unit = {},
    onNavigateToKameraForSolution: () -> Unit = {},
    onCameraLaunch: ((Boolean) -> Unit)? = null,
    viewModel: SoruDetayViewModel = hiltViewModel()
) {
    val handleCameraSolution = {
        if (onCameraLaunch != null) {
            onCameraLaunch(true)
        } else {
            onNavigateToKameraForSolution()
        }
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val soru = uiState.soru

    // Çözüm görseli için Photo Picker
    val solutionPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.updateSolutionImage(uri.toString())
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    if (soru != null) {
                        Column {
                            Text(
                                text = DersTaksonomisi.getDersAdi(soru.subjectId),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${soru.examType} • ${soru.topic}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Text("Soru Detayı")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Geri"
                        )
                    }
                },
                actions = {
                    if (soru != null) {
                        // Favori Butonu
                        IconButton(onClick = { viewModel.toggleFavorite() }) {
                            Icon(
                                imageVector = if (soru.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = if (soru.isFavorite) "Favorilerden Çıkar" else "Favorilere Ekle",
                                tint = if (soru.isFavorite) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Çözüldü / Çözülmedi Durum Butonu
                        IconButton(onClick = { viewModel.toggleCozumDurumu() }) {
                            Icon(
                                imageVector = if (soru.isSolved) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = if (soru.isSolved) "Çözüldü" else "Çözülmedi",
                                tint = if (soru.isSolved) SolvedGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Silme Butonu
                        IconButton(onClick = { viewModel.setShowDeleteDialog(true) }) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Soruyu Sil",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (soru == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Soru bulunamadı veya silinmiş olabilir.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(scrollState)
            ) {
                // Görsel Sekmeleri (Soru Görseli / Çözüm Görseli)
                TabRow(
                    selectedTabIndex = if (uiState.activeTab == DetayTab.SORU) 0 else 1,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Tab(
                        selected = uiState.activeTab == DetayTab.SORU,
                        onClick = { viewModel.setActiveTab(DetayTab.SORU) },
                        text = { Text("Soru Görseli", fontWeight = FontWeight.Bold) },
                        icon = { Icon(Icons.Default.Image, contentDescription = null) }
                    )
                    Tab(
                        selected = uiState.activeTab == DetayTab.COZUM,
                        onClick = { viewModel.setActiveTab(DetayTab.COZUM) },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Çözüm Görseli", fontWeight = FontWeight.Bold)
                                if (!soru.solutionImagePath.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Surface(
                                        shape = CircleShape,
                                        color = SolvedGreen,
                                        modifier = Modifier.size(8.dp)
                                    ) {}
                                }
                            }
                        },
                        icon = { Icon(Icons.Default.Lightbulb, contentDescription = null) }
                    )
                }

                // Aktif Görsel Alanı (Tablette ve telefonda uyumlu yükseklik)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp, max = 400.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    val activePath = if (uiState.activeTab == DetayTab.SORU) {
                        soru.imagePath
                    } else {
                        soru.solutionImagePath
                    }

                    if (activePath.isNullOrBlank()) {
                        // Çözüm Görseli Eklenmemiş Durumu
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                modifier = Modifier.size(64.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.AddPhotoAlternate,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Çözüm Fotoğrafı Eklenmemiş",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Hocanın veya kendinin çözümünü fotoğraflayarak ekleyebilirsin.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = handleCameraSolution,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Fotoğraf Çek")
                                }
                                OutlinedButton(
                                    onClick = {
                                        solutionPhotoPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Galeriden Seç")
                                }
                            }
                        }
                    } else {
                        // Görsel Gösterimi
                        val imageModel = if (activePath.startsWith("http") || activePath.startsWith("content://")) {
                            activePath
                        } else {
                            File(activePath)
                        }

                        SubcomposeAsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(imageModel)
                                .crossfade(true)
                                .build(),
                            contentDescription = if (uiState.activeTab == DetayTab.SORU) "Soru Görseli" else "Çözüm Görseli",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable {
                                    viewModel.openZoomImage(
                                        imagePath = activePath,
                                        title = if (uiState.activeTab == DetayTab.SORU) "Soru Görseli" else "Çözüm Görseli"
                                    )
                                },
                            loading = {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            },
                            error = {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.ImageNotSupported,
                                        contentDescription = "Görsel yüklenemedi",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        )

                        // Büyütme İpucu Rozeti
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(12.dp),
                            shape = RoundedCornerShape(20.dp),
                            color = Color.Black.copy(alpha = 0.6f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ZoomIn,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Büyütmek için dokunun",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                // Çözüm Görseli Yönetim Çubuğu (Eğer Çözüm Görseli Varsa)
                if (uiState.activeTab == DetayTab.COZUM && !soru.solutionImagePath.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                solutionPhotoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Görseli Değiştir")
                        }

                        TextButton(
                            onClick = { viewModel.updateSolutionImage(null) },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Görseli Kaldır")
                        }
                    }
                }

                // Soru Bilgileri ve Rozetler Kartı
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // Üst Bilgi Satırı: TYT/AYT & Ders
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val isTyt = soru.examType.equals("TYT", ignoreCase = true)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isTyt) TytBadgeContainer else AytBadgeContainer,
                                border = BorderStroke(1.dp, if (isTyt) TytBadgeColor else AytBadgeColor)
                            ) {
                                Text(
                                    text = soru.examType,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isTyt) TytBadgeColor else AytBadgeColor,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }

                            Text(
                                text = DersTaksonomisi.getDersAdi(soru.subjectId),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Konu Başlığı
                        Text(
                            text = soru.topic.ifBlank { "Konu Belirtilmemiş" },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Rozetler: Doğru Cevap Şıkkı ve Hata Tipi
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Doğru Cevap Rozeti
                            if (!soru.correctAnswer.isNullOrBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Key,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Doğru Cevap: ",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = soru.correctAnswer,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            // Hata Tipi Rozeti
                            if (soru.mistakeType != null) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MistakeBadgeContainer,
                                    border = BorderStroke(1.dp, MistakeBadgeColor.copy(alpha = 0.5f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "Hata: ${soru.mistakeType.displayName}",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MistakeBadgeColor
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        // Tarihler ve Durum
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Eklenme Tarihi",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = formatTimestampDetailed(soru.createdAt),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            if (soru.isSolved && soru.solvedAt != null) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Çözülme Tarihi",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SolvedGreen
                                    )
                                    Text(
                                        text = formatTimestampDetailed(soru.solvedAt),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = SolvedGreen
                                    )
                                }
                            }
                        }
                    }
                }

                // Öğrenci Notu Alanı
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Notes,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Notlarım",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            IconButton(
                                onClick = { viewModel.setShowEditNoteDialog(true) }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Notu Düzenle",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        if (!soru.note.isNullOrBlank()) {
                            Text(
                                text = soru.note,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        } else {
                            Text(
                                text = "Henüz not eklenmemiş. Kendine hatırlatıcı veya çözüm ipucu yazabilirsin.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // AI Çözüm Kartı
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
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
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = "Yapay Zeka Çözümü",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            if (!soru.aiExplanation.isNullOrBlank()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    // Kopyala Butonu
                                    IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("AI Çözümü", soru.aiExplanation)
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, "Çözüm metni kopyalandı", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Çözümü Kopyala",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Durum 1: Yükleniyor
                        if (uiState.isAiLoading) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Soru inceleniyor ve adım adım çözüm üretiliyor...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        // Durum 2: Hata Var
                        else if (uiState.aiError != null) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ErrorOutline,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                        Text(
                                            text = "Çözüm Alınamadı",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }

                                    Text(
                                        text = uiState.aiError!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (uiState.isApiKeyMissing) {
                                            Button(
                                                onClick = onNavigateToAyarlar,
                                                shape = RoundedCornerShape(10.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.error
                                                )
                                            ) {
                                                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Ayarlara Git")
                                            }
                                        } else {
                                            TextButton(
                                                onClick = { viewModel.requestAiSolution() }
                                            ) {
                                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Tekrar Dene")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        // Durum 3: Çözüm Mevcut
                        else if (!soru.aiExplanation.isNullOrBlank()) {
                            val extractedAnswer = remember(soru.aiExplanation) {
                                viewModel.extractAnswerLetter(soru.aiExplanation)
                            }

                            // Math / LaTeX / Formülleri Zengin Renderla
                            MathMarkdownText(
                                text = soru.aiExplanation,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Onay & Kalıcı Saklama Çubuğu
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (soru.isSolved) Color(0xFF2E7D32).copy(alpha = 0.12f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                border = BorderStroke(1.dp, if (soru.isSolved) Color(0xFF2E7D32).copy(alpha = 0.4f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (soru.isSolved) Icons.Default.CheckCircle else Icons.Default.Check,
                                                contentDescription = null,
                                                tint = if (soru.isSolved) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Text(
                                                text = if (soru.isSolved) "✅ Çözüm Onaylandı (Kalıcı Kaydedildi)" else "Çözüm Doğru mu?",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (soru.isSolved) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface
                                            )
                                        }

                                        if (!soru.isSolved) {
                                            Button(
                                                onClick = {
                                                    viewModel.onaylaCozumu(extractedAnswer)
                                                    Toast.makeText(context, "Çözüm onaylandı ve soru 'Çözüldü' olarak kaydedildi ✓", Toast.LENGTH_SHORT).show()
                                                },
                                                shape = RoundedCornerShape(10.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                                            ) {
                                                Text("Çözümü Onayla", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    // Doğru şık eşitleme çipi (soruda şık boş ama AI bulduysa)
                                    if (soru.correctAnswer.isNullOrBlank() && !extractedAnswer.isNullOrBlank()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Tespit Edilen Cevap: $extractedAnswer Şıkkı",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            TextButton(
                                                onClick = {
                                                    viewModel.onaylaCozumu(extractedAnswer)
                                                    Toast.makeText(context, "Doğru şık '$extractedAnswer' olarak güncellendi ✓", Toast.LENGTH_SHORT).show()
                                                }
                                            ) {
                                                Text("Şıkkı Kaydet ($extractedAnswer)")
                                            }
                                        }
                                    }

                                    Text(
                                        text = "💾 Bu çözüm cihazınızda kalıcıdır. Yeniden açtığınızda API çağrısı yapılmaz ve kotanız korunur.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = { viewModel.requestAiSolution() }
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Yeniden Çözüm İste")
                                }
                            }
                        }
                        // Durum 4: Henüz AI Çözümü İstenmemiş
                        else {
                            Text(
                                text = "Sorunun fotoğrafı incelenerek yapay zeka tarafından adım adım ve öğretici bir çözüm üretilir." +
                                        if (!soru.correctAnswer.isNullOrBlank()) " Doğru cevap (${soru.correctAnswer} şıkkı) dikkate alınarak çözüm doğrulanır." else "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = { viewModel.requestAiSolution() },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Yapay Zekadan Çözüm İste",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        // Not Düzenleme Dialogu
        if (uiState.showEditNoteDialog && soru != null) {
            var tempNote by remember { mutableStateOf(soru.note ?: "") }

            AlertDialog(
                onDismissRequest = { viewModel.setShowEditNoteDialog(false) },
                title = { Text("Notu Düzenle", fontWeight = FontWeight.Bold) },
                text = {
                    OutlinedTextField(
                        value = tempNote,
                        onValueChange = { tempNote = it },
                        label = { Text("Öğrenci Notu / İpuçları") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        maxLines = 6,
                        shape = RoundedCornerShape(12.dp)
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.updateNote(tempNote) },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Kaydet")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.setShowEditNoteDialog(false) }) {
                        Text("İptal")
                    }
                }
            )
        }

        // Soru Silme Onay Dialogu
        if (uiState.showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.setShowDeleteDialog(false) },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                title = { Text("Soruyu Sil", fontWeight = FontWeight.Bold) },
                text = {
                    Text("Bu soruyu silmek istediğinizden emin misiniz? Bu işlem geri alınamaz ve görseller cihazınızdan silinir.")
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.deleteSoru(onSuccess = onNavigateBack) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Sil")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.setShowDeleteDialog(false) }) {
                        Text("İptal")
                    }
                }
            )
        }

        // Zoomable Image Dialog
        if (uiState.zoomImagePath != null) {
            ZoomableImageDialog(
                imagePath = uiState.zoomImagePath,
                title = uiState.zoomImageTitle,
                onDismissRequest = { viewModel.closeZoomImage() }
            )
        }
    }
}

private fun formatTimestampDetailed(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale("tr", "TR"))
    return sdf.format(Date(timestamp))
}
