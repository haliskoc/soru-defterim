package com.sorudefteri.ui.kutuphane.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sorudefteri.data.pdf.PdfDenemeKitapcikGenerator
import com.sorudefteri.data.pdf.PdfExportConfig
import com.sorudefteri.data.pdf.PdfPrintHelper
import com.sorudefteri.data.pdf.PdfTemplateMode
import com.sorudefteri.data.pdf.WorkspaceType
import com.sorudefteri.domain.model.Soru
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Kütüphaneden seçilen sorulardan çok detaylı ve özelleştirilebilir PDF çıktısı oluşturan diyalog penceresi.
 */
@Composable
fun PdfExportDialog(
    sorular: List<Soru>,
    onDismiss: () -> Unit,
    onSuccess: (File) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Form Durumları
    var selectedTemplate by remember { mutableStateOf(PdfTemplateMode.OSYM_DENEME) }
    var targetMedium by remember { mutableStateOf(com.sorudefteri.data.pdf.PdfTargetMedium.PRINT_PAPER) }
    var density by remember { mutableStateOf(com.sorudefteri.data.pdf.PdfDensity.BALANCED) }
    var title by remember { mutableStateOf("Soru Defterim Özel Çalışma Fasikülü") }
    var subtitle by remember { mutableStateOf("") }
    var studentName by remember { mutableStateOf("") }
    var columnsCount by remember { mutableStateOf(2) } // 1 or 2
    var includeCoverPage by remember { mutableStateOf(true) }
    var includeBackCover by remember { mutableStateOf(true) }
    // Varsayılanlar PdfExportConfig ile hizalandı: AI çözümü, öğrenci notu ve
    // kalem çizimleri dahil edilir. Önceden AI çözümü ve çizim kapalıydı; kullanıcı
    // çözümünü PDF'e taşımak için ayrıca "Çözüm Fasikülü" şablonunu seçmek
    // zorundaydı, aksi halde çözümler sessizce dışarıda kalıyordu.
    var includeAiSolution by remember { mutableStateOf(true) }
    var includeStudentNote by remember { mutableStateOf(true) }
    var includeMistakeType by remember { mutableStateOf(true) }
    var includeStylusDrawing by remember { mutableStateOf(true) }
    var includeSourceInfo by remember { mutableStateOf(true) }
    var includeDifficulty by remember { mutableStateOf(true) }
    var includePrintableOpticalForm by remember { mutableStateOf(true) }
    var includeQuestionQrCodes by remember { mutableStateOf(true) }
    var workspaceType by remember { mutableStateOf(WorkspaceType.STANDARD) }

    var isGenerating by remember { mutableStateOf(false) }
    var generatedPdfFile by remember { mutableStateOf<File?>(null) }

    // Şablon değiştiğinde mantıklı varsayılanları otomatik ayarla
    fun applyTemplateDefaults(mode: PdfTemplateMode) {
        selectedTemplate = mode
        when (mode) {
            PdfTemplateMode.OSYM_DENEME -> {
                title = "ÖSYM / YKS Deneme Sınavı"
                targetMedium = com.sorudefteri.data.pdf.PdfTargetMedium.PRINT_PAPER
                density = com.sorudefteri.data.pdf.PdfDensity.BALANCED
                columnsCount = 2
                includeCoverPage = true
                includeBackCover = true
                includePrintableOpticalForm = true
                includeQuestionQrCodes = true
                // Deneme şablonunda da çözüm ve not kapsama alınsın; kullanıcı
                // istemezse dialog'daki anahtarlardan kapatabilir.
                includeAiSolution = true
                includeStudentNote = true
                includeStylusDrawing = true
                workspaceType = WorkspaceType.STANDARD
            }
            PdfTemplateMode.COZUMLU_FASIKUL -> {
                title = "Açıklamalı Çözüm Fasikülü"
                targetMedium = com.sorudefteri.data.pdf.PdfTargetMedium.DIGITAL_TABLET
                density = com.sorudefteri.data.pdf.PdfDensity.BALANCED
                columnsCount = 1 // Çözümler rahat okunsun diye tek sütun
                includeCoverPage = true
                includeBackCover = false
                includePrintableOpticalForm = false
                includeQuestionQrCodes = true
                includeAiSolution = true
                includeStudentNote = true
                workspaceType = WorkspaceType.NONE
            }
            PdfTemplateMode.OGRETMEN_BULTENI -> {
                title = "Hocama Soracaklarım (Öğretmen Bülteni)"
                targetMedium = com.sorudefteri.data.pdf.PdfTargetMedium.DIGITAL_TABLET
                density = com.sorudefteri.data.pdf.PdfDensity.SPACIOUS
                columnsCount = 1
                includeCoverPage = true
                includeBackCover = false
                includePrintableOpticalForm = false
                includeQuestionQrCodes = true
                includeAiSolution = false
                includeStudentNote = true
                workspaceType = WorkspaceType.LARGE
            }
            PdfTemplateMode.EKO_KOMPAKT -> {
                title = "Kompakt Soru Listesi"
                targetMedium = com.sorudefteri.data.pdf.PdfTargetMedium.PRINT_PAPER
                density = com.sorudefteri.data.pdf.PdfDensity.MAX_COMPACT
                columnsCount = 2
                includeCoverPage = false
                includeBackCover = false
                includePrintableOpticalForm = false
                includeQuestionQrCodes = false
                includeAiSolution = false
                includeStudentNote = false
                workspaceType = WorkspaceType.NONE
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!isGenerating) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth(0.94f)
                .imePadding()
                .padding(vertical = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // 1. ÜST BAŞLIK BARI
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PictureAsPdf,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Detaylı PDF Çıktısı Oluştur",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "📚 ${sorular.size} Soru Seçildi • A4 Çıktı Formatı",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, enabled = !isGenerating) {
                        Icon(Icons.Default.Close, contentDescription = "Kapat")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 2. KAYDIRILABİLİR AYARLAR LİSTESİ
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    // A. ŞABLON SEÇİMİ
                    Text(
                        text = "1. Çıktı Şablonu",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PdfTemplateMode.values().forEach { mode ->
                            val isSelected = selectedTemplate == mode
                            FilterChip(
                                selected = isSelected,
                                onClick = { applyTemplateDefaults(mode) },
                                label = { Text(mode.title, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }

                    // Seçili şablon açıklaması
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        Text(
                            text = selectedTemplate.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // B. BELGE METİNLERİ
                    Text(
                        text = "2. Belge Bilgileri",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Belge Başlığı") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = subtitle,
                            onValueChange = { subtitle = it },
                            label = { Text("Alt Başlık / Konu (Opsiyonel)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = studentName,
                            onValueChange = { studentName = it },
                            label = { Text("Öğrenci Adı (Opsiyonel)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // C. KULLANIM AMACI (BASKI VEYA TABLET)
                    Text(
                        text = "3. Kullanım Amacı",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ColumnCardOption(
                            title = "🖨️ Kağıt Baskı Modu",
                            description = "Mürekkep tasarruflu, yüksek kontrastlı, siyah/beyaz dostu temiz baskı",
                            isSelected = targetMedium == com.sorudefteri.data.pdf.PdfTargetMedium.PRINT_PAPER,
                            onClick = { targetMedium = com.sorudefteri.data.pdf.PdfTargetMedium.PRINT_PAPER },
                            modifier = Modifier.weight(1f)
                        )
                        ColumnCardOption(
                            title = "📱 Tablet / Dijital Mod",
                            description = "Stylus kalemiyle çözüme uygun, noktalı/kareli geniş ızgara alanları",
                            isSelected = targetMedium == com.sorudefteri.data.pdf.PdfTargetMedium.DIGITAL_TABLET,
                            onClick = { targetMedium = com.sorudefteri.data.pdf.PdfTargetMedium.DIGITAL_TABLET },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // D. SORU YOĞUNLUĞU / SIĞDIRMA
                    Text(
                        text = "4. Sayfa Sığdırma & Soru Yoğunluğu",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        com.sorudefteri.data.pdf.PdfDensity.values().forEach { d ->
                            val isSelected = density == d
                            FilterChip(
                                selected = isSelected,
                                onClick = { density = d },
                                label = { Text(d.title, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // E. SAYFA VE SÜTUN DÜZENİ
                    Text(
                        text = "5. Sayfa Sütun Düzeni",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ColumnCardOption(
                            title = "2 Sütunlu (ÖSYM Standart)",
                            description = "Klasik sınav kitapçığı düzeni, dengeli sayfa kullanımı",
                            isSelected = columnsCount == 2,
                            onClick = { columnsCount = 2 },
                            modifier = Modifier.weight(1f)
                        )
                        ColumnCardOption(
                            title = "1 Sütunlu (Geniş Çözüm)",
                            description = "Büyük soru görselleri ve açıklamalı çözümler için ideal",
                            isSelected = columnsCount == 1,
                            onClick = { columnsCount = 1 },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // F. İÇERİK ANAHTARLARI
                    Text(
                        text = "6. İçerik ve Detay Seçenekleri",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    ToggleRow(
                        title = "Kaynak Yayın / Deneme Bilgisini Göster",
                        subtitle = "Sorunun ait olduğu kaynak kitap ve deneme adını basar",
                        checked = includeSourceInfo,
                        onCheckedChange = { includeSourceInfo = it }
                    )

                    ToggleRow(
                        title = "Zorluk Seviyesi Rozetini Göster",
                        subtitle = "Kolay / Orta / Zor etiketini soru başlığına ekler",
                        checked = includeDifficulty,
                        onCheckedChange = { includeDifficulty = it }
                    )

                    ToggleRow(
                        title = "Yapay Zeka Çözümlerini Ekle",
                        subtitle = "Adım adım AI açıklamasını soru altına basar",
                        checked = includeAiSolution,
                        onCheckedChange = { includeAiSolution = it }
                    )

                    ToggleRow(
                        title = "Öğrenci Notu ve Hata Tipini Göster",
                        subtitle = "Soruyu kaydederken aldığınız notu ve hata etiketini basar",
                        checked = includeStudentNote,
                        onCheckedChange = { includeStudentNote = it }
                    )

                    ToggleRow(
                        title = "Tabletteki Kalem Çizimlerini Dahil Et",
                        subtitle = "Soru üzerine çizdiğiniz ek çizgileri ve karalamaları görsele işler",
                        checked = includeStylusDrawing,
                        onCheckedChange = { includeStylusDrawing = it }
                    )

                    ToggleRow(
                        title = "ÖSYM Kapak Sayfası Ekle",
                        subtitle = "Öğrenci bilgileri, hedef ve talimatlar içeren ön kapak",
                        checked = includeCoverPage,
                        onCheckedChange = { includeCoverPage = it }
                    )

                    ToggleRow(
                        title = "Optik Form ve Konu Tablosu Ekle",
                        subtitle = "Arka kapakta optik cevap anahtarı ve kazanım matrisi",
                        checked = includeBackCover,
                        onCheckedChange = { includeBackCover = it }
                    )

                    ToggleRow(
                        title = "📷 Kamera ile Okunabilir Optik Cevap Formu Ekle",
                        subtitle = "4 köşe referans kareli, zamanlama raylı ve Test ID matris kodlu tam sayfa A4 optik kağıdı",
                        checked = includePrintableOpticalForm,
                        onCheckedChange = { includePrintableOpticalForm = it }
                    )

                    ToggleRow(
                        title = "🔗 Sorulara Akıllı Karekod Ekle",
                        subtitle = "Her soru başlığının sağ üst köşesine vektörel soru kimlik matris kodu yerleştirir",
                        checked = includeQuestionQrCodes,
                        onCheckedChange = { includeQuestionQrCodes = it }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 3. AKSİYON BUTONLARI (Yazdır, Paylaş, İndirilenlere Kaydet)
                if (isGenerating) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("PDF Belgesi Hazırlanıyor...", fontWeight = FontWeight.Medium)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Yazdır Butonu
                        OutlinedButton(
                            onClick = {
                                isGenerating = true
                                coroutineScope.launch {
                                    try {
                                        val config = buildConfig(
                                            title, subtitle, studentName, selectedTemplate,
                                            targetMedium, density, columnsCount, includeCoverPage, includeBackCover,
                                            includeAiSolution, includeStudentNote, includeMistakeType,
                                            includeStylusDrawing, includeSourceInfo, includeDifficulty, workspaceType,
                                            includePrintableOpticalForm, includeQuestionQrCodes
                                        )
                                        val file = withContext(Dispatchers.IO) {
                                            PdfDenemeKitapcikGenerator.generatePdfWithConfig(context, sorular, config)
                                        }
                                        generatedPdfFile = file
                                        onSuccess(file)
                                        PdfPrintHelper.printPdf(context, file, config.title)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        Toast.makeText(
                                            context,
                                            "PDF oluşturulamadı: ${e.localizedMessage ?: "Bilinmeyen hata"}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } finally {
                                        isGenerating = false
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Yazdır", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }

                        // Paylaş Butonu
                        Button(
                            onClick = {
                                isGenerating = true
                                coroutineScope.launch {
                                    try {
                                        val config = buildConfig(
                                            title, subtitle, studentName, selectedTemplate,
                                            targetMedium, density, columnsCount, includeCoverPage, includeBackCover,
                                            includeAiSolution, includeStudentNote, includeMistakeType,
                                            includeStylusDrawing, includeSourceInfo, includeDifficulty, workspaceType,
                                            includePrintableOpticalForm, includeQuestionQrCodes
                                        )
                                        val file = withContext(Dispatchers.IO) {
                                            PdfDenemeKitapcikGenerator.generatePdfWithConfig(context, sorular, config)
                                        }
                                        generatedPdfFile = file
                                        onSuccess(file)
                                        PdfPrintHelper.sharePdf(context, file, config.title)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        Toast.makeText(
                                            context,
                                            "PDF oluşturulamadı: ${e.localizedMessage ?: "Bilinmeyen hata"}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } finally {
                                        isGenerating = false
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1.2f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("PDF Paylaş", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }

                        // İndirilenler'e Kaydet Butonu
                        OutlinedButton(
                            onClick = {
                                isGenerating = true
                                coroutineScope.launch {
                                    try {
                                        val config = buildConfig(
                                            title, subtitle, studentName, selectedTemplate,
                                            targetMedium, density, columnsCount, includeCoverPage, includeBackCover,
                                            includeAiSolution, includeStudentNote, includeMistakeType,
                                            includeStylusDrawing, includeSourceInfo, includeDifficulty, workspaceType,
                                            includePrintableOpticalForm, includeQuestionQrCodes
                                        )
                                        val file = withContext(Dispatchers.IO) {
                                            PdfDenemeKitapcikGenerator.generatePdfWithConfig(context, sorular, config)
                                        }
                                        generatedPdfFile = file
                                        onSuccess(file)
                                        PdfPrintHelper.savePdfToDownloads(context, file, config.title)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        Toast.makeText(
                                            context,
                                            "PDF oluşturulamadı: ${e.localizedMessage ?: "Bilinmeyen hata"}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } finally {
                                        isGenerating = false
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Kaydet", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

private fun buildConfig(
    title: String,
    subtitle: String,
    studentName: String,
    templateMode: PdfTemplateMode,
    targetMedium: com.sorudefteri.data.pdf.PdfTargetMedium,
    density: com.sorudefteri.data.pdf.PdfDensity,
    columnsCount: Int,
    includeCoverPage: Boolean,
    includeBackCover: Boolean,
    includeAiSolution: Boolean,
    includeStudentNote: Boolean,
    includeMistakeType: Boolean,
    includeStylusDrawing: Boolean,
    includeSourceInfo: Boolean,
    includeDifficulty: Boolean,
    workspaceType: WorkspaceType,
    includePrintableOpticalForm: Boolean = true,
    includeQuestionQrCodes: Boolean = true
): PdfExportConfig {
    return PdfExportConfig(
        title = title.ifBlank { "Soru Defterim Özel Çalışma Fasikülü" },
        subtitle = subtitle.ifBlank { null },
        studentName = studentName.ifBlank { null },
        templateMode = templateMode,
        targetMedium = targetMedium,
        density = density,
        columnsCount = columnsCount,
        includeCoverPage = includeCoverPage,
        includeBackCover = includeBackCover,
        includeAnswerKey = true,
        includeAiSolution = includeAiSolution,
        includeStudentNote = includeStudentNote,
        includeMistakeType = includeMistakeType,
        includeStylusDrawing = includeStylusDrawing,
        includeSourceInfo = includeSourceInfo,
        includeDifficulty = includeDifficulty,
        workspaceType = workspaceType,
        includePrintableOpticalForm = includePrintableOpticalForm,
        includeQuestionQrCodes = includeQuestionQrCodes
    )
}

@Composable
private fun ColumnCardOption(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else androidx.compose.foundation.BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(2.dp))
            Text(description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 14.sp)
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
            )
        )
    }
}
