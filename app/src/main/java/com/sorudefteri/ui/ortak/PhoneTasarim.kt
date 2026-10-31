package com.sorudefteri.ui.ortak

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.sorudefteri.data.images.GorselDepo
import com.sorudefteri.domain.DersTaksonomisi
import com.sorudefteri.domain.model.Ders
import com.sorudefteri.domain.model.HataTipi
import com.sorudefteri.domain.model.Soru
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * TELEFON (Compact) TASARIM SİSTEMİ
 *
 * Tablet dalına dokunmaz. Sadece telefon (dikey tek sütun) dallarında kullanılır.
 * İlkeler:
 * - Dikey tek sütun, çift panelli (tablet) yapı yok
 * - Her bölüm "Adım N — Başlık" kartı içinde, detaylı ama nefes alan
 * - Dokunma hedefleri min 44-48dp
 * - Yatay kaydırmalı tek satır filtre yerine: hızlı çipler + "Tüm Filtreler" detay sayfası
 * - Liste kartı: yatay kompakt (sol thumb + sağ içerik), dev 16:10 kart yok
 */

// ---------------------------------------------------------------------------
// 1) Adım kartı: telefon bölümlerini dikey adım adım gösterir
// ---------------------------------------------------------------------------

@Composable
fun PhoneAdimKarti(
    adim: Int,
    baslik: String,
    aciklama: String? = null,
    ikon: ImageVector? = null,
    tamamlandi: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Adım rozeti
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (tamamlandi) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.primaryContainer
                        )
                ) {
                    if (tamamlandi) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Text(
                            text = "$adim",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (ikon != null) {
                            Icon(
                                imageVector = ikon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = baslik,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (!aciklama.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = aciklama,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
            content()
        }
    }
}

@Composable
fun PhoneBolumBasligi(
    baslik: String,
    altBaslik: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = baslik,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (!altBaslik.isNullOrBlank()) {
            Text(
                text = altBaslik,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 2) Telefon arama çubuğu: daha yüksek, nefes alan, sayaç rozetli
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneAramaCubugu(
    value: String,
    onValueChange: (String) -> Unit,
    sonucSayisi: Int? = null,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        placeholder = {
            Text(
                text = "Konu, ders veya not ara...",
                style = MaterialTheme.typography.bodyLarge
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Ara",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (sonucSayisi != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "$sonucSayisi",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                if (value.isNotEmpty()) {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Temizle"
                        )
                    }
                }
            }
        },
        shape = RoundedCornerShape(18.dp),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface
        )
    )
}

// ---------------------------------------------------------------------------
// 3) Telefon hızlı filtreler: 1 satır ana çipler + detay sayfası butonu
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhoneHizliFiltreler(
    selectedExamType: String?,
    onExamTypeSelected: (String?) -> Unit,
    onlyUnsolved: Boolean,
    onOnlyUnsolvedToggle: (Boolean) -> Unit,
    onlyFavorites: Boolean,
    onOnlyFavoritesToggle: (Boolean) -> Unit,
    onlyDueReview: Boolean,
    onOnlyDueReviewToggle: (Boolean) -> Unit,
    aktifFiltreSayisi: Int,
    onTumFiltrelerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf("TYT", "AYT").forEach { type ->
            val selected = selectedExamType == type
            FilterChip(
                selected = selected,
                onClick = { onExamTypeSelected(if (selected) null else type) },
                label = {
                    Text(
                        text = type,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                leadingIcon = if (selected) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null,
                modifier = Modifier.heightIn(min = 40.dp)
            )
        }
        FilterChip(
            selected = onlyUnsolved,
            onClick = { onOnlyUnsolvedToggle(!onlyUnsolved) },
            label = { Text("Çözülmeyenler") },
            modifier = Modifier.heightIn(min = 40.dp)
        )
        FilterChip(
            selected = onlyFavorites,
            onClick = { onOnlyFavoritesToggle(!onlyFavorites) },
            label = { Text("★ Favoriler") },
            modifier = Modifier.heightIn(min = 40.dp)
        )
        FilterChip(
            selected = onlyDueReview,
            onClick = { onOnlyDueReviewToggle(!onlyDueReview) },
            label = { Text("Tekrar Zamanı") },
            modifier = Modifier.heightIn(min = 40.dp)
        )
        // Detaylı filtre sayfası girişi — her zaman görünür, dokunma hedefi büyük
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (aktifFiltreSayisi > 0) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier
                .heightIn(min = 40.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable { onTumFiltrelerClick() }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = if (aktifFiltreSayisi > 0) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (aktifFiltreSayisi > 0) "Filtreler ($aktifFiltreSayisi)" else "Tüm Filtreler",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (aktifFiltreSayisi > 0) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

/**
 * ModalBottomSheet ile detaylı filtreleme sayfası (PhoneFiltreSheet).
 * Adım 4 ve Adım 5 içeriği uzun olduğunda dikey kaydırılabilir (verticalScroll).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PhoneFiltreSheet(
    onDismiss: () -> Unit,
    selectedExamType: String?,
    onExamTypeSelected: (String?) -> Unit,
    selectedSubjectId: String?,
    availableSubjects: List<Ders>,
    onSubjectSelected: (String?) -> Unit,
    selectedMistakeType: HataTipi?,
    onMistakeTypeSelected: (HataTipi?) -> Unit,
    selectedPublisher: String?,
    availablePublishers: List<String>,
    onPublisherSelected: (String?) -> Unit,
    selectedExamName: String?,
    availableExamNames: List<String>,
    onExamNameSelected: (String?) -> Unit,
    onlyUnsolved: Boolean,
    onOnlyUnsolvedToggle: (Boolean) -> Unit,
    onlyFavorites: Boolean,
    onOnlyFavoritesToggle: (Boolean) -> Unit,
    onlyDueReview: Boolean,
    onOnlyDueReviewToggle: (Boolean) -> Unit,
    onResetFilters: () -> Unit
) {
    PhoneFiltreDetaySayfasi(
        onDismiss = onDismiss,
        selectedExamType = selectedExamType,
        onExamTypeSelected = onExamTypeSelected,
        selectedSubjectId = selectedSubjectId,
        availableSubjects = availableSubjects,
        onSubjectSelected = onSubjectSelected,
        selectedMistakeType = selectedMistakeType,
        onMistakeTypeSelected = onMistakeTypeSelected,
        selectedPublisher = selectedPublisher,
        availablePublishers = availablePublishers,
        onPublisherSelected = onPublisherSelected,
        selectedExamName = selectedExamName,
        availableExamNames = availableExamNames,
        onExamNameSelected = onExamNameSelected,
        onlyUnsolved = onlyUnsolved,
        onOnlyUnsolvedToggle = onOnlyUnsolvedToggle,
        onlyFavorites = onlyFavorites,
        onOnlyFavoritesToggle = onOnlyFavoritesToggle,
        onlyDueReview = onlyDueReview,
        onOnlyDueReviewToggle = onOnlyDueReviewToggle,
        onResetFilters = onResetFilters
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PhoneFiltreDetaySayfasi(
    onDismiss: () -> Unit,
    selectedExamType: String?,
    onExamTypeSelected: (String?) -> Unit,
    selectedSubjectId: String?,
    availableSubjects: List<Ders>,
    onSubjectSelected: (String?) -> Unit,
    selectedMistakeType: HataTipi?,
    onMistakeTypeSelected: (HataTipi?) -> Unit,
    selectedPublisher: String?,
    availablePublishers: List<String>,
    onPublisherSelected: (String?) -> Unit,
    selectedExamName: String?,
    availableExamNames: List<String>,
    onExamNameSelected: (String?) -> Unit,
    onlyUnsolved: Boolean,
    onOnlyUnsolvedToggle: (Boolean) -> Unit,
    onlyFavorites: Boolean,
    onOnlyFavoritesToggle: (Boolean) -> Unit,
    onlyDueReview: Boolean,
    onOnlyDueReviewToggle: (Boolean) -> Unit,
    onResetFilters: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Adım Adım Filtrele",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Temizle",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onResetFilters() }
                        .padding(8.dp)
                )
            }

            PhoneAdimKarti(
                adim = 1,
                baslik = "Sınav Türü",
                aciklama = "Hangi sınava ait soruları görmek istiyorsun?"
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedExamType == null,
                        onClick = { onExamTypeSelected(null) },
                        label = { Text("Tümü") },
                        modifier = Modifier.heightIn(min = 44.dp)
                    )
                    listOf("TYT", "AYT", "LGS", "KPSS").forEach { t ->
                        FilterChip(
                            selected = selectedExamType == t,
                            onClick = { onExamTypeSelected(if (selectedExamType == t) null else t) },
                            label = { Text(t) },
                            modifier = Modifier.heightIn(min = 44.dp)
                        )
                    }
                }
            }

            PhoneAdimKarti(
                adim = 2,
                baslik = "Ders",
                aciklama = "Tek bir derse odaklan veya tümünü gör.",
                tamamlandi = selectedSubjectId != null
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedSubjectId == null,
                        onClick = { onSubjectSelected(null) },
                        label = { Text("Tüm Dersler") },
                        modifier = Modifier.heightIn(min = 44.dp)
                    )
                    availableSubjects.forEach { ders ->
                        FilterChip(
                            selected = ders.id == selectedSubjectId,
                            onClick = { onSubjectSelected(ders.id) },
                            label = { Text("${ders.name} (${ders.examType})") },
                            modifier = Modifier.heightIn(min = 44.dp)
                        )
                    }
                }
            }

            PhoneAdimKarti(
                adim = 3,
                baslik = "Hata Tipi",
                aciklama = "En çok takıldığın hata tipini seç.",
                tamamlandi = selectedMistakeType != null
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedMistakeType == null,
                        onClick = { onMistakeTypeSelected(null) },
                        label = { Text("Tümü") },
                        modifier = Modifier.heightIn(min = 44.dp)
                    )
                    HataTipi.entries.forEach { h ->
                        FilterChip(
                            selected = h == selectedMistakeType,
                            onClick = { onMistakeTypeSelected(if (selectedMistakeType == h) null else h) },
                            label = { Text(h.displayName) },
                            modifier = Modifier.heightIn(min = 44.dp)
                        )
                    }
                }
            }

            if (availablePublishers.isNotEmpty() || availableExamNames.isNotEmpty()) {
                PhoneAdimKarti(
                    adim = 4,
                    baslik = "Kaynak & Deneme",
                    aciklama = "Yayın veya deneme adına göre daralt."
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (availablePublishers.isNotEmpty()) {
                            Text("Yayın", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = selectedPublisher == null,
                                    onClick = { onPublisherSelected(null) },
                                    label = { Text("Tümü") },
                                    modifier = Modifier.heightIn(min = 44.dp)
                                )
                                availablePublishers.forEach { p ->
                                    FilterChip(
                                        selected = p == selectedPublisher,
                                        onClick = { onPublisherSelected(if (selectedPublisher == p) null else p) },
                                        label = { Text(p) },
                                        modifier = Modifier.heightIn(min = 44.dp)
                                    )
                                }
                            }
                        }
                        if (availableExamNames.isNotEmpty()) {
                            Text("Deneme", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = selectedExamName == null,
                                    onClick = { onExamNameSelected(null) },
                                    label = { Text("Tümü") },
                                    modifier = Modifier.heightIn(min = 44.dp)
                                )
                                availableExamNames.forEach { d ->
                                    FilterChip(
                                        selected = d == selectedExamName,
                                        onClick = { onExamNameSelected(if (selectedExamName == d) null else d) },
                                        label = { Text(d) },
                                        modifier = Modifier.heightIn(min = 44.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            PhoneAdimKarti(
                adim = 5,
                baslik = "Durum",
                aciklama = "Çözüm ve tekrar durumuna göre listele."
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = onlyUnsolved,
                        onClick = { onOnlyUnsolvedToggle(!onlyUnsolved) },
                        label = { Text("Çözülmeyenler") },
                        modifier = Modifier.heightIn(min = 44.dp)
                    )
                    FilterChip(
                        selected = onlyFavorites,
                        onClick = { onOnlyFavoritesToggle(!onlyFavorites) },
                        label = { Text("★ Favoriler") },
                        modifier = Modifier.heightIn(min = 44.dp)
                    )
                    FilterChip(
                        selected = onlyDueReview,
                        onClick = { onOnlyDueReviewToggle(!onlyDueReview) },
                        label = { Text("Tekrar Zamanı") },
                        modifier = Modifier.heightIn(min = 44.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// 4) Telefon liste kartı: yatay, kompakt, dikey akışa uygun (tek sütun)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun PhoneSoruListeKarti(
    soru: Soru,
    onClick: () -> Unit,
    onToggleSolved: (() -> Unit)? = null,
    onToggleFavorite: (() -> Unit)? = null,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    gorselDepo: GorselDepo? = null,
    thumbSize: Dp = 92.dp,
    modifier: Modifier = Modifier
) {
    val dersAdi = remember(soru.subjectId, soru.examType) {
        DersTaksonomisi.getDersName(soru.subjectId)
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(
            2.dp,
            MaterialTheme.colorScheme.primary
        ) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sol: seçim kutusu veya görsel
            if (isSelectionMode) {
                IconButton(
                    onClick = { onLongClick?.invoke() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(thumbSize)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onClick() }
            ) {
                val displayPath = remember(soru.imagePath, gorselDepo) {
                    gorselDepo?.getThumbnailPath(soru.imagePath)
                        ?: GorselDepo.getThumbnailPath(soru.imagePath)
                        ?: soru.imagePath
                }
                val imageModel = remember(displayPath) {
                    if (displayPath.startsWith("http") || displayPath.startsWith("content://")) displayPath
                    else File(displayPath)
                }
                val context = LocalContext.current
                val imageRequest = remember(context, imageModel, soru.id) {
                    ImageRequest.Builder(context)
                        .data(imageModel)
                        .size(240, 240)
                        .memoryCacheKey("liste_${soru.id}")
                        .crossfade(false)
                        .build()
                }
                // PERF: Izgarada SubcomposeAsyncImage yerine AsyncImage (crossfade kapalı, remember'lanmış request).
                AsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            }

            // Sağ: içerik
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    ) {
                        Text(
                            text = soru.examType.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = dersAdi,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (!soru.aiExplanation.isNullOrBlank()) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Text(
                    text = soru.topic.ifBlank { "Konu belirtilmemiş" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 19.sp
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (!soru.correctAnswer.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = "Cevap: ${soru.correctAnswer}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (soru.mistakeType != null) {
                        Text(
                            text = "• ${soru.mistakeType.displayName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = phoneCardDate(soru.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Büyük dokunma hedefli çözüldü hapı (min 40dp)
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (soru.isSolved) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable(enabled = onToggleSolved != null) { onToggleSolved?.invoke() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = if (soru.isSolved) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (soru.isSolved) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (soru.isSolved) "Çözüldü" else "Çözülmedi",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Sağ kenar: favori (48dp hedef)
            IconButton(
                onClick = { onToggleFavorite?.invoke() },
                enabled = onToggleFavorite != null || soru.isFavorite,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (soru.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "Favori",
                    tint = if (soru.isFavorite) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

private val phoneCardDateFormatter = SimpleDateFormat("d MMM yyyy", Locale("tr", "TR"))
private fun phoneCardDate(timestamp: Long): String {
    return try {
        synchronized(phoneCardDateFormatter) {
            phoneCardDateFormatter.format(Date(timestamp))
        }
    } catch (_: Exception) { "" }
}

// ---------------------------------------------------------------------------
// 5) Telefon seçim aksiyon çubuğu: ana 3 + overflow (taşma yok)
// ---------------------------------------------------------------------------

@Composable
fun PhoneSecimAltBar(
    seciliSayi: Int,
    onCozuldu: () -> Unit,
    onYildizla: () -> Unit,
    onPdf: () -> Unit,
    onAnki: () -> Unit,
    onSil: () -> Unit,
    onAiCoz: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var overflowAcik by rememberSaveable { mutableStateOf(false) }
    Surface(
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "$seciliSayi soru seçildi — 1) işaretle, 2) dışa aktar, 3) sil",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (onAiCoz != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onAiCoz() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "⚡ AI ile Çöz ($seciliSayi)",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onCozuldu() }
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(8.dp)) {
                        Text("✓ Çözüldü", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                    }
                }
                Surface(
                    color = Color(0xFFFFB300),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onYildizla() }
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(8.dp)) {
                        Text("★ Yıldızla", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.error,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSil() }
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(8.dp)) {
                        Text("Sil", color = MaterialTheme.colorScheme.onError, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                androidx.compose.material3.OutlinedButton(
                    onClick = onPdf,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("📄 Detaylı PDF", fontWeight = FontWeight.Bold)
                }
                androidx.compose.material3.OutlinedButton(
                    onClick = onAnki,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("📇 Anki Paketi", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
