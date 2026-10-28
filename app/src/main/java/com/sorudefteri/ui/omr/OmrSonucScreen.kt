package com.sorudefteri.ui.omr

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sorudefteri.domain.model.DenemeDersNeti
import com.sorudefteri.domain.omr.model.OmrAnswerStatus
import com.sorudefteri.domain.omr.model.OmrQuestionEvaluation
import com.sorudefteri.domain.omr.model.OmrScanResult
import com.sorudefteri.domain.omr.model.OmrTemplate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class SoruFiltresi(val label: String) {
    TUMU("Tümü"),
    YANLIS("Yanlış"),
    BOS("Boş"),
    DOGRU("Doğru")
}

/**
 * Optik Okuma Sonuç & Karne (OMR Result) Ekranı.
 *
 * Özellikler:
 * - ÖSYM Formatında Toplam Net, Doğru, Yanlış, Boş ve Başarı Oranı Karnesi.
 * - Ders bazlı net ve dağılım kartları (Türkçe, Matematik, Fen, Sosyal vb.).
 * - İnteraktif Soru Listesi (1..N): Her satırda işaretlenen şık, doğru cevap, durum.
 * - Dokunarak manuel şık ve cevap anahtarı düzeltme imkanı.
 * - **KRİTİK:** Yanlış veya boş olan soruların yanında "➕ Soruyu Ekle" butonu;
 *   tıklandığında soruyu kütüphaneye ve bu denemeye eklemek üzere SoruEkleme akışına paslar!
 * - Alt kısımda "📋 Deneme Detayına Git" yönlendirme butonu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OmrSonucScreen(
    denemeId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToDenemeDetay: (Long) -> Unit,
    onAddWrongQuestion: (
        denemeId: Long,
        denemeBaslik: String,
        yayinEvi: String,
        soruNo: Int,
        sinavTuru: String,
        subjectId: String,
        correctAnswer: String,
        studentAnswer: String
    ) -> Unit,
    viewModel: OmrViewModel = hiltViewModel()
) {
    // Deneme kaydını ve sonuçlarını yükle
    LaunchedEffect(denemeId) {
        viewModel.loadDenemeResult(denemeId)
    }

    val scanResult by viewModel.sonucResult.collectAsStateWithLifecycle()
    val eklenenSoruNumaralari by viewModel.eklenenSoruNumaralari.collectAsStateWithLifecycle()

    var selectedFilter by remember { mutableStateOf(SoruFiltresi.TUMU) }
    var editingQuestion by remember { mutableStateOf<OmrQuestionEvaluation?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Optik Okuma Karnesi",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        scanResult?.let { res ->
                            Text(
                                text = "${res.baslik} • ${res.yayinEvi.ifBlank { res.sinavTuru }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.navigationBarsPadding()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Button(
                        onClick = { onNavigateToDenemeDetay(denemeId) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Assessment,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "📋 Deneme Detayına Git",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        val currentResult = scanResult

        if (currentResult == null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Optik sonuçlar yükleniyor...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            val filteredQuestions = remember(currentResult.questionResults, selectedFilter) {
                when (selectedFilter) {
                    SoruFiltresi.TUMU -> currentResult.questionResults
                    SoruFiltresi.YANLIS -> currentResult.questionResults.filter { it.isWrong }
                    SoruFiltresi.BOS -> currentResult.questionResults.filter { it.isEmpty }
                    SoruFiltresi.DOGRU -> currentResult.questionResults.filter { it.isCorrect }
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(
                    top = paddingValues.calculateTopPadding() + 8.dp,
                    bottom = paddingValues.calculateBottomPadding() + 16.dp,
                    start = 16.dp,
                    end = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // 1. Özet Başlık Bilgisi
                item {
                    val dateStr = remember(currentResult.scanTimestamp) {
                        SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale("tr")).format(Date(currentResult.scanTimestamp))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${currentResult.sinavTuru} • ${currentResult.totalQuestions} Soru",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = dateStr,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 2. ÖSYM Tarzı Özet Kartı
                item {
                    OsymOzetKarti(result = currentResult)
                }

                // 3. Ders Bazlı Net Kartları
                if (currentResult.dersSonuclari.isNotEmpty()) {
                    item {
                        Column {
                            Text(
                                text = "Ders Bazlı Net Dağılımı",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(currentResult.dersSonuclari) { ders ->
                                    DersNetKarti(ders = ders)
                                }
                            }
                        }
                    }
                }

                // 4. Soru Listesi Filtreleme Çipleri
                item {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Optik Soru Cevap Karnesi",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${filteredQuestions.size} Soru Listeleniyor",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SoruFiltresi.values().forEach { filter ->
                                val count = when (filter) {
                                    SoruFiltresi.TUMU -> currentResult.questionResults.size
                                    SoruFiltresi.YANLIS -> currentResult.toplamYanlis
                                    SoruFiltresi.BOS -> currentResult.toplamBos
                                    SoruFiltresi.DOGRU -> currentResult.toplamDogru
                                }
                                val selected = selectedFilter == filter
                                FilterChip(
                                    selected = selected,
                                    onClick = { selectedFilter = filter },
                                    label = { Text("${filter.label} ($count)") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = when (filter) {
                                            SoruFiltresi.YANLIS -> Color(0xFFFEE2E2)
                                            SoruFiltresi.BOS -> Color(0xFFF1F5F9)
                                            SoruFiltresi.DOGRU -> Color(0xFFDCFCE7)
                                            SoruFiltresi.TUMU -> MaterialTheme.colorScheme.primaryContainer
                                        },
                                        selectedLabelColor = when (filter) {
                                            SoruFiltresi.YANLIS -> Color(0xFF991B1B)
                                            SoruFiltresi.BOS -> Color(0xFF334155)
                                            SoruFiltresi.DOGRU -> Color(0xFF166534)
                                            SoruFiltresi.TUMU -> MaterialTheme.colorScheme.onPrimaryContainer
                                        }
                                    )
                                )
                            }
                        }
                    }
                }

                // 5. İnteraktif Soru Satırları
                items(filteredQuestions, key = { it.questionNumber }) { q ->
                    val isAlreadyAdded = eklenenSoruNumaralari.contains(q.questionNumber)
                    OmrSoruSatiri(
                        evaluation = q,
                        isAlreadyAdded = isAlreadyAdded,
                        onEditClick = { editingQuestion = q },
                        onAddWrongQuestionClick = {
                            onAddWrongQuestion(
                                denemeId,
                                currentResult.baslik,
                                currentResult.yayinEvi,
                                q.questionNumber,
                                currentResult.sinavTuru,
                                q.subjectId,
                                q.correctAnswer ?: "A",
                                q.studentAnswer ?: ""
                            )
                        }
                    )
                }
            }
        }
    }

    // Manuel Soru Düzeltme Dialogu
    editingQuestion?.let { eval ->
        OmrQuestionEditDialog(
            question = eval,
            onDismiss = { editingQuestion = null },
            onSave = { newStudent, newCorrect ->
                viewModel.updateQuestionAnswerInteractive(
                    questionNumber = eval.questionNumber,
                    newStudentAnswer = newStudent,
                    newCorrectAnswer = newCorrect
                )
                editingQuestion = null
            }
        )
    }
}

/**
 * ÖSYM Formatında Toplam Net ve Başarı Oranı Özet Kartı
 */
@Composable
private fun OsymOzetKarti(result: OmrScanResult) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "TOPLAM NET",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = String.format(Locale.US, "%.2f", result.toplamNet),
                        fontSize = 38.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Başarı Yüzdesi Rozeti
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "%${String.format(Locale.US, "%.1f", result.basariYuzdesi)}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Text(
                            text = "Başarı",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(12.dp))

            // Doğru / Yanlış / Boş Dağılım Çipleri
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatBadge(
                    count = result.toplamDogru,
                    label = "Doğru",
                    color = Color(0xFF16A34A),
                    bgColor = Color(0xFFDCFCE7)
                )
                StatBadge(
                    count = result.toplamYanlis,
                    label = "Yanlış",
                    color = Color(0xFFDC2626),
                    bgColor = Color(0xFFFEE2E2)
                )
                StatBadge(
                    count = result.toplamBos,
                    label = "Boş",
                    color = Color(0xFF475569),
                    bgColor = Color(0xFFF1F5F9)
                )
            }
        }
    }
}

@Composable
private fun StatBadge(
    count: Int,
    label: String,
    color: Color,
    bgColor: Color
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        modifier = Modifier.width(96.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Text(
                text = count.toString(),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = color.copy(alpha = 0.9f)
            )
        }
    }
}

/**
 * Ders Bazlı Net Kartı (Türkçe, Matematik, Fen vb.)
 */
@Composable
private fun DersNetKarti(ders: DenemeDersNeti) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        ),
        modifier = Modifier.width(160.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = ders.dersAdi,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "${String.format(Locale.US, "%.2f", ders.net)} Net",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${ders.dogruSayisi} D  •  ${ders.yanlisSayisi} Y  •  ${ders.bosSayisi} B",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Tek bir sorunun satır kartı:
 * Soru No, Ders Adı, İşaretlenen Şık, Doğru Cevap, Durum İkonu,
 * Dokunarak Düzeltme ve KRİTİK "➕ Soruyu Ekle" butonu!
 */
@Composable
private fun OmrSoruSatiri(
    evaluation: OmrQuestionEvaluation,
    isAlreadyAdded: Boolean,
    onEditClick: () -> Unit,
    onAddWrongQuestionClick: () -> Unit
) {
    val statusColor = when (evaluation.status) {
        OmrAnswerStatus.CORRECT -> Color(0xFF16A34A)
        OmrAnswerStatus.WRONG, OmrAnswerStatus.MULTI_INVALID -> Color(0xFFDC2626)
        OmrAnswerStatus.EMPTY -> Color(0xFF64748B)
    }

    val statusBgColor = when (evaluation.status) {
        OmrAnswerStatus.CORRECT -> Color(0xFFF0FDF4)
        OmrAnswerStatus.WRONG, OmrAnswerStatus.MULTI_INVALID -> Color(0xFFFEF2F2)
        OmrAnswerStatus.EMPTY -> Color(0xFFF8FAFC)
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = statusBgColor),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            statusColor.copy(alpha = 0.35f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEditClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            // Durum İkonu
            Icon(
                imageVector = when (evaluation.status) {
                    OmrAnswerStatus.CORRECT -> Icons.Default.CheckCircle
                    OmrAnswerStatus.WRONG, OmrAnswerStatus.MULTI_INVALID -> Icons.Default.Cancel
                    OmrAnswerStatus.EMPTY -> Icons.Default.RemoveCircleOutline
                },
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(22.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            // Soru Numarası ve Ders Adı
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Soru ${evaluation.questionNumber}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "• ${evaluation.dersAdi}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Şık Bilgisi: İşaretlenen vs Doğru
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val markText = when (evaluation.studentAnswer) {
                        null -> "BOŞ"
                        "MULTI" -> "ÇİFT"
                        else -> evaluation.studentAnswer
                    }

                    Text(
                        text = "İşaretlenen: $markText",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = statusColor
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = "Doğru: ${evaluation.correctAnswer ?: "-"}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Düzenle Butonu (Küçük Kalem)
            IconButton(
                onClick = onEditClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Düzelt",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // KRİTİK: Yanlış veya Boş olan sorular için "➕ Soruyu Ekle" butonu!
            if (evaluation.isActionableForLibrary) {
                if (isAlreadyAdded) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFDCFCE7),
                        modifier = Modifier.padding(start = 2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color(0xFF166534),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Eklendi",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF166534)
                            )
                        }
                    }
                } else {
                    FilledTonalButton(
                        onClick = onAddWrongQuestionClick,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Soruyu Ekle",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Bir sorunun öğrenci cevabını veya doğru cevabını manuel düzenleme penceresi.
 */
@Composable
private fun OmrQuestionEditDialog(
    question: OmrQuestionEvaluation,
    onDismiss: () -> Unit,
    onSave: (newStudent: String?, newCorrect: String) -> Unit
) {
    var selectedStudent by remember { mutableStateOf(question.studentAnswer) }
    var selectedCorrect by remember { mutableStateOf(question.correctAnswer ?: "A") }

    val options = listOf("A", "B", "C", "D", "E")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Soru ${question.questionNumber} Düzelt (${question.dersAdi})",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 1. Öğrenci İşareti Seçimi
                Text(
                    text = "Öğrenci İşareti:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    options.forEach { opt ->
                        val isSelected = selectedStudent == opt
                        OptionChoiceChip(
                            text = opt,
                            isSelected = isSelected,
                            onClick = { selectedStudent = opt },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OptionChoiceChip(
                        text = "Boş",
                        isSelected = selectedStudent == null,
                        onClick = { selectedStudent = null },
                        modifier = Modifier.weight(1.2f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 2. Doğru Cevap Seçimi
                Text(
                    text = "Doğru Cevap:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    options.forEach { opt ->
                        val isSelected = selectedCorrect == opt
                        OptionChoiceChip(
                            text = opt,
                            isSelected = isSelected,
                            onClick = { selectedCorrect = opt },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(selectedStudent, selectedCorrect) }) {
                Text("Kaydet")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Vazgeç")
            }
        }
    )
}

@Composable
private fun OptionChoiceChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
            .height(36.dp)
            .clickable { onClick() }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}
