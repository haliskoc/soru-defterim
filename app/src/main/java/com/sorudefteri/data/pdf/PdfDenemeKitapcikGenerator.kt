package com.sorudefteri.data.pdf

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.widget.Toast
import androidx.core.content.FileProvider
import com.sorudefteri.data.images.GorselDepo
import com.sorudefteri.domain.DersTaksonomisi
import com.sorudefteri.domain.model.Soru
import com.sorudefteri.domain.omr.model.OmrTemplate
import com.sorudefteri.ui.ortak.drawing.QuestionDrawingData
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Gelişmiş PDF Deneme Kitapçığı ve Çalışma Fasikülü Oluşturucu.
 * 
 * Yerel Android [PdfDocument], [Canvas], [Paint] ve [Typeface] sınıflarını kullanarak
 * 3. parti kütüphane bağımlılığı olmadan A4 formatında yüksek kaliteli çıktılar üretir.
 *
 * Desteklenen Şablonlar:
 * - 1. Resmi ÖSYM Deneme Kitapçığı (2 sütunlu, kapaklı, optikli, kazanım matrisli)
 * - 2. Açıklamalı Çözüm Fasikülü (Soru + AI Çözümü + Doğru Cevap + Konu Notu)
 * - 3. Öğretmen / Etüt Bülteni (Soru + Öğrenci Hatası + Öğretmen Çözüm Boşluğu)
 * - 4. Eko / Kompakt Soru Listesi (Mürekkep ve kağıt tasarruflu liste)
 */
object PdfDenemeKitapcikGenerator {

    private const val PAGE_WIDTH = 595 // A4 genişlik (pt)
    private const val PAGE_HEIGHT = 842 // A4 yükseklik (pt)

    private const val MARGIN_LEFT = 35f
    private const val MARGIN_RIGHT = 35f
    private const val MARGIN_TOP = 46f
    private const val MARGIN_BOTTOM = 46f

    private const val COLUMN_GAP = 22f
    private const val TWO_COL_WIDTH = (PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT - COLUMN_GAP) / 2f // ~251.5f
    private const val ONE_COL_WIDTH = PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT // ~525f

    private const val CONTENT_TOP = MARGIN_TOP + 12f
    private const val CONTENT_BOTTOM = PAGE_HEIGHT - MARGIN_BOTTOM - 10f

    /**
     * Varsayılan geriye dönük uyumluluk fonksiyonu (Deneme Kitapçığı).
     */
    fun generatePdfKitapcik(
        context: Context,
        sorular: List<Soru>,
        targetTitle: String = "YKS Hedefim",
        documentProvider: () -> PdfDocument = { PdfDocument() }
    ): File {
        return generatePdfWithConfig(
            context = context,
            sorular = sorular,
            config = PdfExportConfig(
                title = targetTitle,
                templateMode = PdfTemplateMode.OSYM_DENEME,
                columnsCount = 2
            ),
            documentProvider = documentProvider
        )
    }

    /**
     * Detaylı yapılandırma (PdfExportConfig) ile PDF belgesi üretir.
     */
    fun generatePdfWithConfig(
        context: Context,
        sorular: List<Soru>,
        config: PdfExportConfig,
        documentProvider: () -> PdfDocument = { PdfDocument() }
    ): File {
        val pdfDir = File(context.cacheDir, "pdf").apply { if (!exists()) mkdirs() }
        val prefix = when (config.templateMode) {
            PdfTemplateMode.OSYM_DENEME -> "Deneme_Kitapcigi"
            PdfTemplateMode.COZUMLU_FASIKUL -> "Cozumlu_Fasikul"
            PdfTemplateMode.OGRETMEN_BULTENI -> "Ogretmen_Bulteni"
            PdfTemplateMode.EKO_KOMPAKT -> "Kompakt_Liste"
        }
        val outputFile = File(pdfDir, "${prefix}_${System.currentTimeMillis()}.pdf")

        val pdfDocument = documentProvider()
        var pageNumber = 1

        try {
            // 1. KAPAK SAYFASI (Eğer istendiyse ve eko modda değilse)
            val shouldDrawCover = config.includeCoverPage && config.templateMode != PdfTemplateMode.EKO_KOMPAKT
            if (shouldDrawCover) {
                val coverPageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber++).create()
                val coverPage = pdfDocument.startPage(coverPageInfo)
                drawCoverPage(coverPage.canvas, sorular, config)
                pdfDocument.finishPage(coverPage)
            }

            // 2. SORU SAYFALARI (1 veya 2 Sütun)
            if (sorular.isNotEmpty()) {
                pageNumber = drawContentPages(pdfDocument, context, sorular, config, pageNumber)
            } else {
                val emptyPageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber++).create()
                val emptyPage = pdfDocument.startPage(emptyPageInfo)
                drawEmptyNotice(emptyPage.canvas, config.title)
                pdfDocument.finishPage(emptyPage)
            }

            // 2.5. TAM SAYFA A4 OKUNABİLİR OPTİK CEVAP FORMU
            if (config.includePrintableOpticalForm && config.templateMode == PdfTemplateMode.OSYM_DENEME) {
                val opticalPageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber++).create()
                val opticalPage = pdfDocument.startPage(opticalPageInfo)
                drawPrintableOpticalFormPage(opticalPage.canvas, sorular, config)
                pdfDocument.finishPage(opticalPage)
            }

            // 3. ARKA KAPAK / CEVAP ANAHTARI
            if (config.includeBackCover && config.templateMode == PdfTemplateMode.OSYM_DENEME) {
                val backCoverInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                val backCover = pdfDocument.startPage(backCoverInfo)
                drawBackCoverPage(backCover.canvas, sorular, config.title)
                pdfDocument.finishPage(backCover)
            } else if (config.includeAnswerKey && config.templateMode != PdfTemplateMode.OSYM_DENEME && config.templateMode != PdfTemplateMode.COZUMLU_FASIKUL) {
                // Fasikül harici modlarda arka cevap anahtarı
                val answerPageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                val answerPage = pdfDocument.startPage(answerPageInfo)
                drawCompactAnswerKeyPage(answerPage.canvas, sorular, config.title)
                pdfDocument.finishPage(answerPage)
            }

            // Dosyaya yaz
            FileOutputStream(outputFile).use { out ->
                pdfDocument.writeTo(out)
                out.flush()
            }
        } finally {
            try {
                pdfDocument.close()
            } catch (_: Exception) {}
        }

        return outputFile
    }

    /**
     * Oluşturulan PDF dosyasını FileProvider ile paylaşır.
     */
    fun shareOrPrintPdf(context: Context, pdfFile: File) {
        PdfPrintHelper.sharePdf(context, pdfFile, "Soru Defterim PDF Çıktısı")
    }

    // ==========================================
    // 1. BÖLÜM: KAPAK SAYFASI ÇİZİMİ
    // ==========================================

    private fun drawCoverPage(canvas: Canvas, sorular: List<Soru>, config: PdfExportConfig) {
        val strokePaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            color = Color.BLACK
        }
        val fillPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = Color.BLACK
        }
        val textPaint = Paint().apply {
            isAntiAlias = true
            color = Color.BLACK
        }

        // Çift Dış Çerçeve (Resmi Belge Kenarlığı)
        strokePaint.strokeWidth = 2.2f
        canvas.drawRect(25f, 25f, 570f, 817f, strokePaint)
        strokePaint.strokeWidth = 0.8f
        canvas.drawRect(28.5f, 28.5f, 566.5f, 813.5f, strokePaint)

        // Üst Başlıklar
        textPaint.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        textPaint.textSize = 12f
        drawCenteredText(canvas, "T.C.", 297.5f, 56f, textPaint)

        textPaint.textSize = 13f
        val topTitle = when (config.templateMode) {
            PdfTemplateMode.OSYM_DENEME -> "ÖSYM / YKS DENEME SINAVI"
            PdfTemplateMode.COZUMLU_FASIKUL -> "ÖZEL ÇÖZÜMLÜ ÇALIŞMA FASİKÜLÜ"
            PdfTemplateMode.OGRETMEN_BULTENI -> "ÖĞRETMEN DEĞERLENDİRME BÜLTENİ"
            PdfTemplateMode.EKO_KOMPAKT -> "SORU LİSTESİ"
        }
        drawCenteredText(canvas, topTitle, 297.5f, 74f, textPaint)

        textPaint.textSize = 9.5f
        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        drawCenteredText(canvas, "SORU DEFTERİM AKILLI ÖĞRENME SİSTEMİ", 297.5f, 90f, textPaint)

        // Ana Başlık Şeridi
        val bannerRect = RectF(45f, 105f, 550f, 155f)
        fillPaint.color = when (config.templateMode) {
            PdfTemplateMode.OSYM_DENEME -> Color.rgb(24, 30, 42)
            PdfTemplateMode.COZUMLU_FASIKUL -> Color.rgb(15, 76, 129)
            PdfTemplateMode.OGRETMEN_BULTENI -> Color.rgb(44, 82, 130)
            PdfTemplateMode.EKO_KOMPAKT -> Color.rgb(51, 65, 85)
        }
        canvas.drawRoundRect(bannerRect, 6f, 6f, fillPaint)

        val bannerTextPaint = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            textSize = 14f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        drawCenteredText(canvas, config.title.uppercase(Locale("tr")), 297.5f, 134f, bannerTextPaint)

        // Kitapçık Türü / Şablon Rozeti
        val kitaptikBox = RectF(230f, 168f, 365f, 236f)
        strokePaint.strokeWidth = 1.2f
        strokePaint.color = Color.BLACK
        canvas.drawRoundRect(kitaptikBox, 4f, 4f, strokePaint)

        textPaint.textSize = 7.5f
        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        val boxSub = if (config.templateMode == PdfTemplateMode.OSYM_DENEME) "KİTAPÇIK TÜRÜ" else "FASİKÜL TÜRÜ"
        drawCenteredText(canvas, boxSub, 297.5f, 183f, textPaint)

        textPaint.textSize = 28f
        textPaint.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        val modeLetter = when (config.templateMode) {
            PdfTemplateMode.OSYM_DENEME -> "A"
            PdfTemplateMode.COZUMLU_FASIKUL -> "ÇÖZÜM"
            PdfTemplateMode.OGRETMEN_BULTENI -> "ETÜT"
            PdfTemplateMode.EKO_KOMPAKT -> "EKO"
        }
        textPaint.textSize = if (modeLetter.length > 2) 16f else 32f
        drawCenteredText(canvas, modeLetter, 297.5f, 220f, textPaint)

        // Öğrenci Hedefi ve Sınav Bilgileri Kartı
        val infoCard = RectF(45f, 248f, 550f, 320f)
        val cardBgPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = Color.rgb(245, 247, 250)
        }
        canvas.drawRoundRect(infoCard, 5f, 5f, cardBgPaint)
        strokePaint.strokeWidth = 0.8f
        strokePaint.color = Color.rgb(203, 213, 225)
        canvas.drawRoundRect(infoCard, 5f, 5f, strokePaint)

        val dateStr = SimpleDateFormat("dd.MM.yyyy", Locale("tr")).format(Date())
        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textPaint.textSize = 10.5f
        textPaint.color = Color.rgb(30, 41, 59)
        canvas.drawText("🎯 HEDEF / BELGE: ${config.title}", 60f, 273f, textPaint)
        if (!config.subtitle.isNullOrBlank()) {
            textPaint.textSize = 9f
            textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            canvas.drawText("📌 Kapsam: ${config.subtitle}", 60f, 292f, textPaint)
        } else {
            canvas.drawText("📅 Tarih: $dateStr", 60f, 295f, textPaint)
        }

        canvas.drawText("📝 Toplam Soru: ${sorular.size} Soru", 340f, 273f, textPaint)
        val estimatedMinutes = max(20, (sorular.size * 1.8).toInt())
        canvas.drawText("⏱️ Tahmini Süre: $estimatedMinutes Dakika", 340f, 295f, textPaint)

        // Aday Bilgileri Tablosu
        val adayBox = RectF(45f, 332f, 550f, 470f)
        strokePaint.color = Color.BLACK
        strokePaint.strokeWidth = 1f
        canvas.drawRoundRect(adayBox, 4f, 4f, strokePaint)

        val adayHeaderRect = RectF(45f, 332f, 550f, 356f)
        fillPaint.color = Color.rgb(226, 232, 240)
        canvas.drawRoundRect(adayHeaderRect, 4f, 4f, fillPaint)
        canvas.drawLine(45f, 356f, 550f, 356f, strokePaint)

        textPaint.textSize = 9.5f
        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textPaint.color = Color.BLACK
        drawCenteredText(canvas, "ADAY / ÖĞRENCİ BİLGİLERİ", 297.5f, 348f, textPaint)

        textPaint.textSize = 8.5f
        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        strokePaint.strokeWidth = 0.6f

        // ADI SOYADI
        val studentDisplayName = config.studentName?.ifBlank { null } ?: ""
        canvas.drawText("ADI SOYADI", 60f, 380f, textPaint)
        canvas.drawText(":", 145f, 380f, textPaint)
        if (studentDisplayName.isNotEmpty()) {
            textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            canvas.drawText(studentDisplayName, 155f, 380f, textPaint)
            textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        } else {
            canvas.drawLine(155f, 382f, 535f, 382f, strokePaint)
        }

        // T.C. / ÖĞRENCİ NO
        canvas.drawText("ÖĞRENCİ NO", 60f, 410f, textPaint)
        canvas.drawText(":", 145f, 410f, textPaint)
        var boxX = 155f
        for (i in 0 until 11) {
            canvas.drawRect(boxX, 398f, boxX + 16f, 414f, strokePaint)
            boxX += 19f
        }

        // SALON NO / İMZA
        canvas.drawText("SINIF / SALON :", 60f, 442f, textPaint)
        canvas.drawLine(145f, 444f, 280f, 444f, strokePaint)

        canvas.drawText("İMZA :", 320f, 442f, textPaint)
        canvas.drawLine(360f, 444f, 535f, 444f, strokePaint)

        // Talimatlar Bölümü
        val kurallarBox = RectF(45f, 485f, 550f, 745f)
        strokePaint.strokeWidth = 1f
        canvas.drawRoundRect(kurallarBox, 4f, 4f, strokePaint)

        val kuralHeader = RectF(45f, 485f, 550f, 509f)
        fillPaint.color = Color.rgb(226, 232, 240)
        canvas.drawRoundRect(kuralHeader, 4f, 4f, fillPaint)
        canvas.drawLine(45f, 509f, 550f, 509f, strokePaint)

        textPaint.textSize = 9f
        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        drawCenteredText(canvas, "ÇALIŞMA VE SINAV KILAVUZU", 297.5f, 501f, textPaint)

        val talimatlar = when (config.templateMode) {
            PdfTemplateMode.COZUMLU_FASIKUL -> listOf(
                "1. Bu fasikül, Soru Defterim arşivinizdeki çözemediğiniz ve tekrar etmek istediğiniz sorulardan derlenmiştir.",
                "2. Her sorunun hemen altında yapay zeka tarafından hazırlanan adım adım çözüm mantığı ve doğru cevap yer alır.",
                "3. Önce çözümü kapatarak soruyu kendiniz çözmeyi deneyiniz, ardından AI çözüm adımlarını inceleyiniz.",
                "4. Kişisel aldığınız soru notlarını ve hata türlerini dikkatle gözden geçirerek konu eksiklerinizi kapatınız."
            )
            PdfTemplateMode.OGRETMEN_BULTENI -> listOf(
                "1. Bu bülten, etüt merkezinde veya okulda öğretmeninizle birebir çözmeniz için özel olarak hazırlanmıştır.",
                "2. Soruların altında yapamadığınız yerlere dair aldığınız notlar ve hata türleriniz yer almaktadır.",
                "3. Alt kısımdaki geniş kareli alan, öğretmeninizin kalemle çözüm adımlarını yazması için ayrılmıştır.",
                "4. Çözüm sonrası öğretmeninize onaylatıp soruyu 'Çözüldü' olarak arşivleyebilirsiniz."
            )
            else -> listOf(
                "1. Bu deneme kitapçığı, Soru Defterim arşivinizden kişiselleştirilerek derlenmiştir.",
                "2. Kitapçıktaki her sorunun yalnızca bir doğru cevabı vardır. Çoklu işaretlemeler geçersizdir.",
                "3. Soru altındaki boş alanları (Çözüm Alanı) işlem yapmak ve karalamak için serbestçe kullanabilirsiniz.",
                "4. Cevaplarınızı kitapçığın arka kapağındaki Optik Cevap Formuna kodlayınız.",
                "5. Sınav esnasında gerçek sınav disiplinini sağlamak adına süreyi aşmamaya özen gösteriniz."
            )
        }

        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        textPaint.textSize = 8.5f
        var kuralY = 530f
        val maxKuralWidth = 475f

        for (talimat in talimatlar) {
            kuralY = drawWrappedText(canvas, talimat, 62f, kuralY, maxKuralWidth, textPaint, 13f)
            kuralY += 8f
        }

        // Alt Başarılar Mesajı
        textPaint.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        textPaint.textSize = 13.5f
        textPaint.color = Color.rgb(15, 23, 42)
        drawCenteredText(canvas, "BAŞARILAR DİLERİZ!", 297.5f, 785f, textPaint)
    }

    // ==========================================
    // 2. BÖLÜM: SORU VE ÇÖZÜM SAYFALARI
    // ==========================================

    /**
     * Ölçüm-sonra-yerleştir (measure-then-pack) mimarisi:
     * 1. ÖLÇÜM: her soru iki genişlikte (sütun / tam sayfa) ölçülür ([blokOlc]).
     * 2. PLAN: [PdfSoruYerlesim.yerlesimPlanla] → sayfa/sütun koordinatları,
     *    gerekiyorsa tam genişlik kaçışı ve metin satır bölme.
     * 3. ÇİZİM: plana birebir sadık çizim ([Oge] koordinatları kullanılır); ölçüm
     *    ile çizim aynı ölçüm kaydını tüketir → taşma/kayma/çakışma kalıcı biter.
     *
     * Ara ara tam genişlik sayfa: sütuna hiç sığmayan (yüksek en-boy oranlı)
     * sorular kendi tam sayfasını alır; normal sorular iki sütun düzenini sürer.
     * Soru sırası hiç bozulmaz → numaralandırma ve cevap anahtarı mutabık kalır.
     */
    private fun drawContentPages(
        pdfDocument: PdfDocument,
        context: Context,
        sorular: List<Soru>,
        config: PdfExportConfig,
        startPageNumber: Int
    ): Int {
        var pageNumber = startPageNumber
        if (sorular.isEmpty()) return pageNumber

        val isSingleCol = config.columnsCount == 1
        val marginSide = config.density.marginPt
        val columnGap = config.density.columnGapPt
        val sutunSayisi = if (isSingleCol) 1 else 2

        val sutunGenislik = if (isSingleCol) {
            PAGE_WIDTH - (marginSide * 2f)
        } else {
            (PAGE_WIDTH - (marginSide * 2f) - columnGap) / 2f
        }
        val tamGenislik = PAGE_WIDTH - (marginSide * 2f)
        val icerikUstY = if (config.density == PdfDensity.MAX_COMPACT) 34f else CONTENT_TOP
        val icerikAltY = if (config.density == PdfDensity.MAX_COMPACT) (PAGE_HEIGHT - 32f) else CONTENT_BOTTOM
        val kapasite = icerikAltY - icerikUstY

        // ---------- 1. ÖLÇÜM ----------
        val girdiler = sorular.mapIndexed { index, soru ->
            val bitmapOrani = soruBitmapOrani(context, soru)
            val sutunOlcum = blokOlc(index, soru, sutunGenislik, bitmapOrani, config, isSingleCol)
            val tamOlcum = if (isSingleCol) sutunOlcum else blokOlc(index, soru, tamGenislik, bitmapOrani, config, isSingleCol)
            PdfSoruYerlesim.Girdi(sutunOlcum, tamOlcum)
        }

        val sutunXlar = List(sutunSayisi) { i -> marginSide + i * (sutunGenislik + columnGap) }
        val geometri = PdfSoruYerlesim.Geometri(
            sutunXlar = sutunXlar,
            sutunGenislik = sutunGenislik,
            tamGenislik = tamGenislik,
            icerikUstY = icerikUstY,
            kapasite = kapasite
        )

        // ---------- 2. PLAN ----------
        val plan = PdfSoruYerlesim.yerlesimPlanla(girdiler, geometri)

        // ---------- 3. ÇİZİM ----------
        for (sayfa in plan) {
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber++).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas
            val hasFullWidth = sayfa.ogeler.any { it.genislik > TWO_COL_WIDTH + 10f }
            val drawDivider = !isSingleCol && !hasFullWidth
            drawPageRunningHeaderFooter(canvas, pageNumber - 1, config.title, false, isSingleCol, marginSide, drawVerticalDivider = drawDivider)

            for (oge in sayfa.ogeler) {
                when (oge) {
                    is PdfSoruYerlesim.Oge.TamSoru -> {
                        val girdi = girdiler[oge.soruIndex]
                        val olcum = oge.olcumOverride ?: (if (oge.sutun < 0) girdi.tamGenislikMetriki else girdi.sutunMetriki)
                        val soru = sorular[oge.soruIndex]
                        val bitmap = if (olcum.bitmapRatio > 0f) {
                            loadQuestionBitmap(
                                context = context,
                                soruId = soru.id,
                                imagePath = soru.imagePath,
                                maxWidth = oge.genislik.toInt().coerceAtLeast(1),
                                maxHeight = bitmapMaksYukseklik(isSingleCol, config).toInt(),
                                includeStylusDrawing = config.includeStylusDrawing
                            )
                        } else {
                            null
                        }
                        drawQuestionEntry(
                            canvas = canvas,
                            soru = soru,
                            soruNo = oge.soruIndex + 1,
                            x = oge.x,
                            y = oge.y,
                            olcum = olcum,
                            satirAraligi = oge.satirAraligi,
                            bitmap = bitmap,
                            config = config
                        )
                        if (bitmap != null && !bitmap.isRecycled) {
                            try { bitmap.recycle() } catch (_: Exception) {}
                        }
                    }
                    is PdfSoruYerlesim.Oge.MetinDevami -> {
                        drawMetinDevami(canvas, oge, oge.soruIndex + 1)
                    }
                }
            }

            pdfDocument.finishPage(page)
        }
        return pageNumber
    }

    // ==========================================================
    // YAKLAŞIMINA GÖRE ÖLÇÜM (ölçüm == çizim garantisi buradan dosar)
    // ==========================================================

    /**
     * Bir sorunun, belirtilen görsel genişlikte tam blok ölçümünü üretir.
     * Dönen kayıt hem planlayıcıya hem çiziciye iletilir: yükseklikler
     * İKİNCİ KEZ hesaplanmaz, doğrudan tüketilir → kayma imkansızdır.
     */
    private fun blokOlc(
        soruIndex: Int,
        soru: Soru,
        genislik: Float,
        bitmapOrani: Float,
        config: PdfExportConfig,
        isSingleCol: Boolean
    ): PdfSoruYerlesim.SoruOlcum {
        // ÜST BÖLÜM: başlık barı + ayırıcı (çizimle birebir aynı 24f), kaynak, not
        var ustYukseklik = 24f
        val kaynak = listOfNotNull(
            soru.kaynakYayin?.takeIf { it.isNotBlank() },
            soru.kaynakDeneme?.takeIf { it.isNotBlank() }
        ).joinToString(" - ")
        if (config.includeSourceInfo && kaynak.isNotBlank()) ustYukseklik += 14f
        if (config.includeStudentNote && !soru.note.isNullOrBlank()) ustYukseklik += 22f

        // İÇERİK: metin (varsa) + bitmap (varsa) | placeholder
        val satirYuksekligi = 11f
        val metinSatirlari: List<String>
        var metinIlkBosluk = 0f
        var metinSonraBosluk = 0f
        val metin = soru.questionText ?: soru.ocrMetni
        if (!metin.isNullOrBlank()) {
            metinSatirlari = soruMetniSatirlari(metin, genislik)
            metinIlkBosluk = 8f
            metinSonraBosluk = 6f
        } else {
            metinSatirlari = emptyList()
        }
        val metinYukseklik = if (metinSatirlari.isNotEmpty()) {
            metinIlkBosluk + (metinSatirlari.size * satirYuksekligi) + metinSonraBosluk
        } else 0f

        val gorselYukseklik: Float
        val icerikYukseklik: Float
        if (bitmapOrani > 0f) {
            val maks = bitmapMaksYukseklik(isSingleCol, config)
            val safGorsel = min(genislik * bitmapOrani, maks)
            gorselYukseklik = safGorsel
            icerikYukseklik = metinYukseklik + safGorsel + 6f
        } else {
            gorselYukseklik = 0f
            icerikYukseklik = if (metinSatirlari.isNotEmpty()) metinYukseklik else 46f
        }

        // ÇÖZÜM: şablona göre doğru cevap + AI kutusu + kalem alanı
        var cozumYukseklik = 0f
        when (config.templateMode) {
            PdfTemplateMode.COZUMLU_FASIKUL -> {
                if (!soru.correctAnswer.isNullOrBlank()) cozumYukseklik += 20f
                if (config.includeAiSolution && !soru.aiExplanation.isNullOrBlank()) {
                    cozumYukseklik += aiKutuYuksekligi(aiMetinSatirlari(soru.aiExplanation!!, genislik).size) + 6f
                }
                if (config.workspaceType != WorkspaceType.NONE) cozumYukseklik += config.workspaceType.heightPt
            }
            PdfTemplateMode.OGRETMEN_BULTENI -> {
                cozumYukseklik += maxOf(config.workspaceType.heightPt, 110f)
            }
            else -> {
                if (config.density != PdfDensity.MAX_COMPACT && config.workspaceType != WorkspaceType.NONE) {
                    cozumYukseklik += config.workspaceType.heightPt
                }
            }
        }

        val altBosluk = if (config.density == PdfDensity.MAX_COMPACT) 10f else 18f

        return PdfSoruYerlesim.SoruOlcum(
            soruIndex = soruIndex,
            genislik = genislik,
            ustYukseklik = ustYukseklik,
            icerikYukseklik = icerikYukseklik,
            cozumYukseklik = cozumYukseklik,
            altBosluk = altBosluk,
            metinSatirlari = metinSatirlari,
            satirYuksekligi = satirYuksekligi,
            metinIlkBosluk = metinIlkBosluk,
            metinSonraBosluk = metinSonraBosluk,
            bitmapRatio = bitmapOrani,
            gorselYukseklik = gorselYukseklik
        )
    }

    /** Soru metni sarmalı (çizimle aynı: genişlik − 8f, 8 pt yazı tipi). */
    private fun soruMetniSatirlari(metin: String, genislik: Float): List<String> {
        val p = Paint().apply { isAntiAlias = true; textSize = 8f; typeface = Typeface.SANS_SERIF }
        return PdfSoruYerlesim.MetinAkisi.satirlaraAyir(metin, p, genislik - 8f)
    }

    /** AI açıklaması sarmalı (ölçüm ve çizim aynı kod kullanır). */
    private fun aiMetinSatirlari(aiMetni: String, genislik: Float): List<String> {
        val p = Paint().apply { isAntiAlias = true; textSize = 7.2f; typeface = Typeface.SANS_SERIF }
        return PdfSoruYerlesim.MetinAkisi.satirlaraAyir(aiMetni, p, genislik - 12f)
    }

    /** AI kutu yüksekliği: başlık bandı 22f + satırlar + alt boşluk 8f. */
    private fun aiKutuYuksekligi(aiSatirSayisi: Int): Float = 22f + (aiSatirSayisi * 10f) + 8f

    /** Bitmap'in isterilen sayfa düzeni için üst sınır yüksekliği (pt). */
    private fun bitmapMaksYukseklik(isSingleCol: Boolean, config: PdfExportConfig): Float = when {
        isSingleCol -> 400f
        config.density == PdfDensity.MAX_COMPACT -> 240f
        else -> 300f
    }

    /**
     * Bitmap en-boy oranını piksel yüklemeden (yalnızca sınır dekodu) alır:
     * plan eskisini hiç görsel açmadan iki genişlik için de hesaplayabilir.
     */
    private fun soruBitmapOrani(context: Context, soru: Soru): Float {
        val dosya = gorselDosyasi(context, soru.imagePath) ?: return 0f
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(dosya.absolutePath, opts)
            if (opts.outWidth <= 0 || opts.outHeight <= 0) 0f
            else opts.outHeight.toFloat() / opts.outWidth.toFloat()
        } catch (_: Exception) {
            0f
        }
    }

    /** Görsel dosya çözümleme zinciri (GörselDepo → mutlak yol → iç klasörler). */
    private fun gorselDosyasi(context: Context, imagePath: String?): File? {
        if (imagePath.isNullOrBlank()) return null
        return try {
            GorselDepo(context).getGorselFile(imagePath)
                ?: File(imagePath).takeIf { it.exists() && it.isFile }
                ?: File(context.filesDir, "images/${File(imagePath).name}").takeIf { it.exists() && it.isFile }
                ?: File(context.filesDir, imagePath).takeIf { it.exists() && it.isFile }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Soru bloğu çizimi: [olcum] kaydırdaki ölçüm değerleriyle birebir aynı
     * yükseklikleri çizimde tüketir → plan == çizim (kayma imkansız).
     * [satirAraligi]: metin bölünmüş ilk parçaysa 0 until k gönderilir.
     */
    private fun drawQuestionEntry(
        canvas: Canvas,
        soru: Soru,
        soruNo: Int,
        x: Float,
        y: Float,
        olcum: PdfSoruYerlesim.SoruOlcum,
        satirAraligi: IntRange?,
        bitmap: Bitmap?,
        config: PdfExportConfig
    ) {
        val width = olcum.genislik
        var cursorY = y

        // A. BAŞLIK BARI (Soru No + Sınav Türü + Ders/Konu + Hata Tipi)
        val badgeRect = RectF(x, cursorY, x + 24f, cursorY + 16f)
        val badgePaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = Color.rgb(30, 41, 59)
        }
        canvas.drawRoundRect(badgeRect, 3f, 3f, badgePaint)

        val badgeTextPaint = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            textSize = 9f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        drawCenteredText(canvas, "$soruNo", badgeRect.centerX(), badgeRect.centerY() + 3f, badgeTextPaint)

        // Sınav Türü [TYT] / [AYT]
        val examType = soru.examType.ifBlank { "TYT" }.uppercase()
        val examBadgeRect = RectF(x + 28f, cursorY, x + 56f, cursorY + 16f)
        val examBadgePaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = if (examType == "TYT") Color.rgb(224, 242, 254) else Color.rgb(254, 243, 199)
        }
        canvas.drawRoundRect(examBadgeRect, 3f, 3f, examBadgePaint)

        val examTextPaint = Paint().apply {
            isAntiAlias = true
            color = if (examType == "TYT") Color.rgb(3, 105, 161) else Color.rgb(180, 83, 9)
            textSize = 7.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        drawCenteredText(canvas, examType, examBadgeRect.centerX(), examBadgeRect.centerY() + 2.5f, examTextPaint)

        // Ders ve Konu
        val dersAdi = DersTaksonomisi.getDersAdi(soru.examType, soru.subjectId)
        val konuAdi = soru.topic.ifBlank { "Genel" }
        val headerText = "$dersAdi • $konuAdi"

        val subjectPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(51, 65, 85)
            textSize = 8f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val maxHeaderWidth = width - 80f
        canvas.drawText(clipText(headerText, subjectPaint, maxHeaderWidth), x + 60f, cursorY + 11.5f, subjectPaint)

        // Rozetler (Sağdan Sola: Karekod, Hata Tipi ve Zorluk)
        var extraInfoX = x + width
        if (config.includeQuestionQrCodes) {
            val qrSize = 16f
            val qrX = extraInfoX - qrSize
            val qrY = cursorY
            OmrMatrixCodeCodec.drawQuestionIdMatrixCode(canvas, soru.id, qrX, qrY, qrSize)
            extraInfoX -= (qrSize + 4f)
        }
        if (config.includeMistakeType && soru.mistakeType != null) {
            val mistakeText = soru.mistakeType.displayName
            val mPaint = Paint().apply {
                isAntiAlias = true
                textSize = 7f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                color = Color.rgb(194, 65, 12)
            }
            val mWidth = mPaint.measureText(mistakeText) + 10f
            val mRect = RectF(extraInfoX - mWidth, cursorY, extraInfoX, cursorY + 16f)
            val mBg = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.FILL
                color = Color.rgb(255, 237, 213)
            }
            canvas.drawRoundRect(mRect, 3f, 3f, mBg)
            drawCenteredText(canvas, mistakeText, mRect.centerX(), mRect.centerY() + 2.5f, mPaint)
            extraInfoX -= (mWidth + 4f)
        }

        if (config.includeDifficulty && !soru.difficulty.isNullOrBlank()) {
            val diffText = soru.difficulty.trim()
            val dPaint = Paint().apply {
                isAntiAlias = true
                textSize = 6.5f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                color = Color.rgb(71, 85, 105)
            }
            val dWidth = dPaint.measureText(diffText) + 8f
            if (extraInfoX - dWidth > x + 130f) {
                val dRect = RectF(extraInfoX - dWidth, cursorY, extraInfoX, cursorY + 16f)
                val dBg = Paint().apply {
                    isAntiAlias = true
                    style = Paint.Style.FILL
                    color = Color.rgb(241, 245, 249)
                }
                canvas.drawRoundRect(dRect, 3f, 3f, dBg)
                drawCenteredText(canvas, diffText, dRect.centerX(), dRect.centerY() + 2.5f, dPaint)
                extraInfoX -= (dWidth + 4f)
            }
        }

        // Ayırıcı çizgi
        val linePaint = Paint().apply { color = Color.rgb(226, 232, 240); strokeWidth = 0.8f }
        canvas.drawLine(x, cursorY + 20f, x + width, cursorY + 20f, linePaint)
        cursorY += 24f

        // Kaynak Bilgisi (Yayın / Deneme / Soru No)
        val kaynak = listOfNotNull(soru.kaynakYayin?.takeIf { it.isNotBlank() }, soru.kaynakDeneme?.takeIf { it.isNotBlank() }).joinToString(" - ")
        if (config.includeSourceInfo && kaynak.isNotBlank()) {
            val kPaint = Paint().apply {
                isAntiAlias = true
                textSize = 6.8f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
                color = Color.rgb(100, 116, 139)
            }
            canvas.drawText(clipText("📖 $kaynak", kPaint, width - 10f), x + 4f, cursorY + 8f, kPaint)
            cursorY += 14f
        }

        // B. ÖĞRENCİ NOTU BANNERI
        if (config.includeStudentNote && !soru.note.isNullOrBlank()) {
            val noteRect = RectF(x, cursorY, x + width, cursorY + 18f)
            val noteBg = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.FILL
                color = Color.rgb(254, 252, 232)
            }
            canvas.drawRoundRect(noteRect, 3f, 3f, noteBg)
            val noteBorder = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.STROKE
                color = Color.rgb(254, 240, 138)
                strokeWidth = 0.6f
            }
            canvas.drawRoundRect(noteRect, 3f, 3f, noteBorder)

            val nPaint = Paint().apply {
                isAntiAlias = true
                color = Color.rgb(133, 77, 14)
                textSize = 7.5f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
            }
            canvas.drawText("📝 Not: ${clipText(soru.note, nPaint, width - 14f)}", x + 6f, cursorY + 12f, nPaint)
            cursorY += 22f
        }

        // C. SORU İÇERİĞİ (ölçüm kaydındaki değerlerle birebir: kayma olamaz)
        // 1. Üstte soru metni (varsa)
        if (olcum.metinSatirlari.isNotEmpty()) {
            val qTextPaint = Paint().apply {
                isAntiAlias = true
                color = Color.rgb(30, 41, 59)
                textSize = 8f
                typeface = Typeface.SANS_SERIF
            }
            val gosterilecek = PdfSoruYerlesim.MetinAkisi.dilimle(olcum.metinSatirlari, satirAraligi)
            cursorY += olcum.metinIlkBosluk
            cursorY = PdfSoruYerlesim.MetinAkisi.ciz(canvas, gosterilecek, qTextPaint, x + 4f, cursorY, olcum.satirYuksekligi)
            cursorY += olcum.metinSonraBosluk
        }

        // 2. Altta soru görseli (varsa)
        if (bitmap != null && olcum.bitmapRatio > 0f) {
            val gorselH = if (olcum.gorselYukseklik > 0f) {
                olcum.gorselYukseklik
            } else {
                (olcum.icerikYukseklik - olcum.metinBlokYuksekligi - 6f).coerceAtLeast(1f)
            }
            val hedefGenislik = gorselH / olcum.bitmapRatio
            val offsetX = x + max(0f, (width - hedefGenislik) / 2f)
            val destRect = RectF(offsetX, cursorY, offsetX + hedefGenislik, cursorY + gorselH)
            canvas.drawBitmap(bitmap, null, destRect, null)
            cursorY += gorselH + 6f
        } else if (olcum.metinSatirlari.isEmpty()) {
            val placeholderRect = RectF(x, cursorY, x + width, cursorY + 40f)
            val pBgPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL; color = Color.rgb(248, 250, 252) }
            canvas.drawRoundRect(placeholderRect, 4f, 4f, pBgPaint)
            val pTextPaint = Paint().apply {
                isAntiAlias = true
                color = Color.rgb(148, 163, 184)
                textSize = 8f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
            }
            drawCenteredText(canvas, "📷 [Soru Görseli]", placeholderRect.centerX(), placeholderRect.centerY() + 3f, pTextPaint)
            cursorY += 46f
        }

        // D. ŞABLONA GÖRE ÇÖZÜM VEYA ÇÖZÜM ALANI
        when (config.templateMode) {
            PdfTemplateMode.COZUMLU_FASIKUL -> {
                // Doğru Cevap Rozeti
                if (!soru.correctAnswer.isNullOrBlank()) {
                    val ansText = "✓ Doğru Cevap: ${soru.correctAnswer.uppercase()}"
                    val ansPaint = Paint().apply {
                        isAntiAlias = true
                        color = Color.rgb(21, 128, 61)
                        textSize = 8f
                        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    }
                    val ansRect = RectF(x, cursorY, x + ansPaint.measureText(ansText) + 12f, cursorY + 16f)
                    val ansBg = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL; color = Color.rgb(240, 253, 244) }
                    canvas.drawRoundRect(ansRect, 3f, 3f, ansBg)
                    canvas.drawText(ansText, x + 6f, cursorY + 11.5f, ansPaint)
                    cursorY += 20f
                }

                // AI Çözüm Kutusu (yükseklik ölçe: satırlar kesin sığar)
                if (config.includeAiSolution && !soru.aiExplanation.isNullOrBlank()) {
                    val aiSatirlar = aiMetinSatirlari(soru.aiExplanation!!, width)
                    val aiBoxHeight = aiKutuYuksekligi(aiSatirlar.size)
                    val aiRect = RectF(x, cursorY, x + width, cursorY + aiBoxHeight)
                    val aiBg = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL; color = Color.rgb(240, 249, 255) }
                    canvas.drawRoundRect(aiRect, 4f, 4f, aiBg)
                    val aiBorder = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE; color = Color.rgb(186, 230, 253); strokeWidth = 0.8f }
                    canvas.drawRoundRect(aiRect, 4f, 4f, aiBorder)

                    val aiTitlePaint = Paint().apply {
                        isAntiAlias = true
                        color = Color.rgb(3, 105, 161)
                        textSize = 7.5f
                        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    }
                    canvas.drawText("💡 Yapay Zeka Çözüm Adımları & İpuçları:", x + 6f, cursorY + 11f, aiTitlePaint)

                    val aiTextPaint = Paint().apply {
                        isAntiAlias = true
                        color = Color.rgb(30, 41, 59)
                        textSize = 7.2f
                        typeface = Typeface.SANS_SERIF
                    }
                    PdfSoruYerlesim.MetinAkisi.ciz(canvas, aiSatirlar, aiTextPaint, x + 6f, cursorY + 22f, 10f)
                    cursorY += aiBoxHeight + 6f
                }

                if (config.workspaceType != WorkspaceType.NONE) {
                    val wsRect = RectF(x, cursorY, x + width, cursorY + config.workspaceType.heightPt)
                    drawSolutionWorkspace(canvas, wsRect, "✍️ Çözüm İçin Boş Alan", config.targetMedium == PdfTargetMedium.DIGITAL_TABLET)
                    cursorY += config.workspaceType.heightPt
                }
            }
            PdfTemplateMode.OGRETMEN_BULTENI -> {
                val wsHeight = maxOf(config.workspaceType.heightPt, 110f)
                val wsRect = RectF(x, cursorY, x + width, cursorY + wsHeight)
                drawSolutionWorkspace(canvas, wsRect, "👨‍🏫 Öğretmen Değerlendirme & Çözüm Alanı", config.targetMedium == PdfTargetMedium.DIGITAL_TABLET)
                cursorY += wsHeight
            }
            else -> {
                if (olcum.cozumYukseklik > 0f) {
                    val wsRect = RectF(x, cursorY, x + width, cursorY + config.workspaceType.heightPt)
                    drawSolutionWorkspace(canvas, wsRect, "✍️ Çözüm İçin Boş Alan", config.targetMedium == PdfTargetMedium.DIGITAL_TABLET)
                    cursorY += config.workspaceType.heightPt
                }
            }
        }
    }

    /**
     * Metinin başka sayfaya devamı: kompakt başlık bandı (18f) + ilk boşluk (8f,
     * yerleşim sabitine dahil) + sarmalı satır akışı. Yüksekliği pl ile birebir.
     */
    private fun drawMetinDevami(
        canvas: Canvas,
        oge: PdfSoruYerlesim.Oge.MetinDevami,
        soruNo: Int
    ) {
        val x = oge.x
        val ustY = oge.y

        // Devam başlığı bandı (0..18f)
        val badgeRect = RectF(x, ustY + 1f, x + 24f, ustY + 15f)
        val badgePaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = Color.rgb(30, 41, 59)
        }
        canvas.drawRoundRect(badgeRect, 3f, 3f, badgePaint)
        val badgeTextPaint = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            textSize = 9f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        drawCenteredText(canvas, "$soruNo", badgeRect.centerX(), badgeRect.centerY() + 3f, badgeTextPaint)

        val devamTextPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(100, 116, 139)
            textSize = 8f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText("Soru $soruNo — devam", x + 28f, ustY + 11f, devamTextPaint)

        val devLinePaint = Paint().apply { color = Color.rgb(226, 232, 240); strokeWidth = 0.8f }
        canvas.drawLine(x, ustY + 17f, x + oge.genislik, ustY + 17f, devLinePaint)

        // Satır akışı: ilk boşluk 8f yerleşim sabitine dahil; baseline = ustY + DEVAM_BASLIK_YUKSEKLIGI
        val metinPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(30, 41, 59)
            textSize = 8f
            typeface = Typeface.SANS_SERIF
        }
        PdfSoruYerlesim.MetinAkisi.ciz(
            canvas,
            oge.satirlar,
            metinPaint,
            x + 4f,
            ustY + PdfSoruYerlesim.DEVAM_BASLIK_YUKSEKLIGI,
            oge.satirYuksekligi
        )
    }

    private fun drawSolutionWorkspace(canvas: Canvas, rect: RectF, label: String, isTabletMode: Boolean = false) {
        val bgPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = if (isTabletMode) Color.rgb(248, 250, 252) else Color.rgb(253, 253, 254)
        }
        canvas.drawRoundRect(rect, 4f, 4f, bgPaint)

        if (isTabletMode) {
            // Dijital Tablet Stylus Çizimi için Hafif Noktalı Izgara (Dot Grid)
            val dotPaint = Paint().apply {
                isAntiAlias = true
                color = Color.rgb(203, 213, 225)
                strokeWidth = 1.2f
                style = Paint.Style.STROKE
            }
            var dotX = rect.left + 14f
            while (dotX < rect.right - 10f) {
                var dotY = rect.top + 20f
                while (dotY < rect.bottom - 6f) {
                    canvas.drawPoint(dotX, dotY, dotPaint)
                    dotY += 14f
                }
                dotX += 14f
            }
        } else {
            // Kağıt Baskı için Çizgili Alan
            val gridPaint = Paint().apply {
                isAntiAlias = true
                color = Color.rgb(226, 232, 240)
                strokeWidth = 0.5f
            }
            var lineY = rect.top + 18f
            while (lineY < rect.bottom - 4f) {
                canvas.drawLine(rect.left + 6f, lineY, rect.right - 6f, lineY, gridPaint)
                lineY += 15f
            }
        }

        val borderPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            color = if (isTabletMode) Color.rgb(186, 230, 253) else Color.rgb(203, 213, 225)
            strokeWidth = 0.8f
            if (!isTabletMode) {
                pathEffect = DashPathEffect(floatArrayOf(4f, 4f), 0f)
            }
        }
        canvas.drawRoundRect(rect, 4f, 4f, borderPaint)

        val labelPaint = Paint().apply {
            isAntiAlias = true
            color = if (isTabletMode) Color.rgb(2, 132, 199) else Color.rgb(148, 163, 184)
            textSize = 7f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD_ITALIC)
        }
        val finalLabel = if (isTabletMode) "✍️ Tablet Kalemiyle Çözüm Alanı (Noktalı Grid)" else label
        canvas.drawText(finalLabel, rect.left + 8f, rect.top + 11f, labelPaint)
    }

    private fun drawPageRunningHeaderFooter(
        canvas: Canvas,
        pageIndex: Int,
        targetTitle: String,
        isLastPage: Boolean,
        isSingleCol: Boolean,
        marginSide: Float = MARGIN_LEFT,
        drawVerticalDivider: Boolean = !isSingleCol
    ) {
        val linePaint = Paint().apply {
            color = Color.rgb(203, 213, 225)
            strokeWidth = 0.6f
        }
        val textPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(100, 116, 139)
            textSize = 7.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        // Header
        canvas.drawLine(marginSide, MARGIN_TOP - 6f, PAGE_WIDTH - marginSide, MARGIN_TOP - 6f, linePaint)
        canvas.drawText("SORU DEFTERİM", marginSide, MARGIN_TOP - 11f, textPaint)
        drawCenteredText(canvas, targetTitle.uppercase(Locale("tr")), PAGE_WIDTH / 2f, MARGIN_TOP - 11f, textPaint)
        val rightHeaderText = "ÇALIŞMA BELGESİ"
        canvas.drawText(rightHeaderText, PAGE_WIDTH - marginSide - textPaint.measureText(rightHeaderText), MARGIN_TOP - 11f, textPaint)

        // Orta dikey ayırıcı çizgi (Yalnızca 2 sütunlu modda ve tam genişlikli soru yoksa)
        if (drawVerticalDivider) {
            val dividerPaint = Paint().apply {
                color = Color.rgb(226, 232, 240)
                strokeWidth = 0.8f
            }
            canvas.drawLine(PAGE_WIDTH / 2f, MARGIN_TOP, PAGE_WIDTH / 2f, PAGE_HEIGHT - MARGIN_BOTTOM, dividerPaint)
        }

        // Footer
        val footerY = PAGE_HEIGHT - MARGIN_BOTTOM + 8f
        canvas.drawLine(marginSide, footerY, PAGE_WIDTH - marginSide, footerY, linePaint)

        val footerTextPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(148, 163, 184)
            textSize = 7.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        canvas.drawText("Soru Defterim ile Başarıya", marginSide, footerY + 13f, footerTextPaint)
        drawCenteredText(canvas, "- Sayfa $pageIndex -", PAGE_WIDTH / 2f, footerY + 13f, textPaint)

        val rightFooterText = if (isLastPage) "Çalışma Tamamlandı." else "Diğer sayfaya geçiniz ➔"
        canvas.drawText(rightFooterText, PAGE_WIDTH - MARGIN_RIGHT - footerTextPaint.measureText(rightFooterText), footerY + 13f, footerTextPaint)
    }

    private fun drawEmptyNotice(canvas: Canvas, targetTitle: String) {
        drawPageRunningHeaderFooter(canvas, 1, targetTitle, true, true)
        val textPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(100, 116, 139)
            textSize = 12f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        drawCenteredText(canvas, "Bu belge için soru seçilmedi.", PAGE_WIDTH / 2f, 400f, textPaint)
        textPaint.textSize = 9.5f
        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        drawCenteredText(canvas, "Kütüphaneden dilediğiniz soruları seçip 'PDF Çıktısı Al' butonuna tıklayınız.", PAGE_WIDTH / 2f, 420f, textPaint)
    }

    // ==========================================
    // 3. BÖLÜM: ARKA KAPAK & CEVAP ANAHTARI
    // ==========================================

    /**
     * Kamera ile taranabilir tam sayfa A4 Optik Cevap Formu.
     *
     * [OmrTemplate] koordinatları ve Fiducial geometriyle %100 uyumludur:
     * - 4 Köşe Fiducial: 40x40 pt (14x14 mm) saf siyah dolu kareler (sol-üst: 25..65, sağ-üst: 530..570, sol-alt: 25..65/777..817, sağ-alt: 530..570/777..817)
     * - Sağ-Üst Başlık: OmrMatrixCodeCodec.drawMatrixCode ile config.testId matris kodu + okunaklı "KOD: ${config.testId}" metni
     * - Sol-Üst Başlık: Deneme Başlığı, Tarih, Kitapçık Türü [A] [B] baloncukları
     * - 4 Sütun x 30 Satır = 120 Soru: (A) (B) (C) (D) (E) optik baloncukları
     * - Zamanlama Rayları: Her satırın solunda 4 x 1.5 mm (11.34 x 4.25 pt) zamanlama rayları
     */
    fun drawPrintableOpticalFormPage(
        canvas: Canvas,
        sorular: List<Soru>,
        config: PdfExportConfig
    ) {
        val solidBlackPaint = Paint().apply {
            isAntiAlias = false
            style = Paint.Style.FILL
            color = Color.BLACK
        }
        val strokeBlackPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            color = Color.BLACK
            strokeWidth = 0.8f
        }
        val textBlackPaint = Paint().apply {
            isAntiAlias = true
            color = Color.BLACK
        }

        // 1. 4 KÖŞE FIDUCIAL KARELERİ (40x40 pt = 14x14 mm)
        // Sol-Üst: 25..65, 25..65
        canvas.drawRect(25f, 25f, 65f, 65f, solidBlackPaint)
        // Sağ-Üst: 530..570, 25..65
        canvas.drawRect(530f, 25f, 570f, 65f, solidBlackPaint)
        // Sol-Alt: 25..65, 777..817
        canvas.drawRect(25f, 777f, 65f, 817f, solidBlackPaint)
        // Sağ-Alt: 530..570, 777..817
        canvas.drawRect(530f, 777f, 570f, 817f, solidBlackPaint)

        // 2. SAĞ ÜST: TEST ID MATRİS KODU VE OKUNAKLI KOD METNİ
        val matrixRect = OmrMatrixCodeCodec.OPTICAL_FORM_MATRIX_RECT_PT
        OmrMatrixCodeCodec.drawMatrixCode(canvas, matrixRect, config.testId)

        textBlackPaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textBlackPaint.textSize = 8f
        drawCenteredText(canvas, "KOD: ${config.testId}", matrixRect.centerX(), matrixRect.bottom + 12f, textBlackPaint)

        // 3. SOL ÜST: BAŞLIK, TARİH, KİTAPÇIK TÜRÜ [A] [B] BALONCUKLARI
        val headerLeftX = 75f
        var headerY = 40f

        textBlackPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textBlackPaint.textSize = 7.5f
        textBlackPaint.color = Color.rgb(71, 85, 105)
        canvas.drawText("T.C. • SORU DEFTERİM OPTİK CEVAP FORMU", headerLeftX, headerY, textBlackPaint)
        headerY += 16f

        textBlackPaint.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        textBlackPaint.textSize = 13f
        textBlackPaint.color = Color.BLACK
        canvas.drawText(clipText(config.title, textBlackPaint, 340f), headerLeftX, headerY, textBlackPaint)
        headerY += 15f

        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale("tr"))
        val tarihStr = dateFormat.format(Date())
        textBlackPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        textBlackPaint.textSize = 8.5f
        textBlackPaint.color = Color.rgb(30, 41, 59)
        canvas.drawText("TARİH: $tarihStr", headerLeftX, headerY, textBlackPaint)

        val studentStr = "ÖĞRENCİ: ${config.studentName ?: "................................................"}"
        canvas.drawText(studentStr, headerLeftX + 110f, headerY, textBlackPaint)
        headerY += 18f

        // Kitapçık Türü [A] [B]
        textBlackPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textBlackPaint.textSize = 8.5f
        canvas.drawText("KİTAPÇIK TÜRÜ:", headerLeftX, headerY + 3f, textBlackPaint)

        val bookletOptions = listOf("A", "B")
        var bookletBubbleX = headerLeftX + 90f
        val bookletBubbleRadius = 6.5f

        for (bOpt in bookletOptions) {
            val isSelected = config.bookletType.equals(bOpt, ignoreCase = true)
            if (isSelected) {
                canvas.drawCircle(bookletBubbleX, headerY, bookletBubbleRadius, solidBlackPaint)
                val selTextPaint = Paint().apply {
                    isAntiAlias = true
                    color = Color.WHITE
                    textSize = 7.5f
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                }
                drawCenteredText(canvas, bOpt, bookletBubbleX, headerY + 2.5f, selTextPaint)
            } else {
                canvas.drawCircle(bookletBubbleX, headerY, bookletBubbleRadius, strokeBlackPaint)
                val unselTextPaint = Paint().apply {
                    isAntiAlias = true
                    color = Color.BLACK
                    textSize = 7.5f
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                }
                drawCenteredText(canvas, bOpt, bookletBubbleX, headerY + 2.5f, unselTextPaint)
            }
            bookletBubbleX += 22f
        }

        val instructionPaint = Paint().apply {
            isAntiAlias = true
            textSize = 6.8f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
            color = Color.rgb(100, 116, 139)
        }
        canvas.drawText("Kurşun kalemle taşmayacak şekilde doldurunuz. 4 köşe referans karesini katlamayınız/karalamayınız.", headerLeftX, headerY + 18f, instructionPaint)

        val dividerPaint = Paint().apply {
            color = Color.rgb(203, 213, 225)
            strokeWidth = 0.8f
        }
        canvas.drawLine(45f, 175f, 550f, 175f, dividerPaint)

        // 4. 4 SÜTUN X 30 SATIR = 120 SORULUK BALONCUK GRID'İ VE ZAMANLAMA RAYLARI
        // Timing tracks: 4 x 1.5 mm = 11.34 x 4.25 pt
        val trackW = 11.34f
        val trackH = 4.25f

        // Baloncuk yarıçapı
        val bubbleRadiusPt = 5.68f

        val bubbleCirclePaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
            color = Color.rgb(30, 41, 59)
        }
        val bubbleLetterPaint = Paint().apply {
            isAntiAlias = true
            textSize = 6.2f
            color = Color.rgb(51, 65, 85)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val qNumPaint = Paint().apply {
            isAntiAlias = true
            textSize = 7.2f
            color = Color.rgb(30, 41, 59)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val colHeaderPaint = Paint().apply {
            isAntiAlias = true
            textSize = 7.5f
            color = Color.rgb(71, 85, 105)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        // Sütun Başlıkları: NO, A, B, C, D, E
        for (col in 0 until OmrTemplate.COLUMNS) {
            val sampleQ = col * OmrTemplate.ROWS_PER_COLUMN + 1
            val (trackX, _) = OmrTemplate.warpToPt(OmrTemplate.getTimingTrackCenter(sampleQ).first, 0f)
            val bubbleMap = OmrTemplate.getBubbleCenters(sampleQ)
            val headerYPos = 192f

            drawCenteredText(canvas, "NO", trackX + 16f, headerYPos, colHeaderPaint)

            for (opt in OmrTemplate.OPTIONS) {
                val optWarpX = bubbleMap[opt]?.first ?: 0f
                val (optPtX, _) = OmrTemplate.warpToPt(optWarpX, 0f)
                drawCenteredText(canvas, opt, optPtX, headerYPos, colHeaderPaint)
            }
        }

        // 120 Soru için satırlar
        for (q in 1..OmrTemplate.MAX_QUESTIONS) {
            val (timingWarpX, timingWarpY) = OmrTemplate.getTimingTrackCenter(q)
            val (timingPtX, timingPtY) = OmrTemplate.warpToPt(timingWarpX, timingWarpY)
            val trackRect = RectF(
                timingPtX - trackW / 2f,
                timingPtY - trackH / 2f,
                timingPtX + trackW / 2f,
                timingPtY + trackH / 2f
            )
            canvas.drawRect(trackRect, solidBlackPaint)

            val numStr = "$q."
            val numX = timingPtX + trackW / 2f + 4f
            canvas.drawText(numStr, numX, timingPtY + 2.6f, qNumPaint)

            val bubbleMap = OmrTemplate.getBubbleCenters(q)
            for (opt in OmrTemplate.OPTIONS) {
                val warpPos = bubbleMap[opt] ?: continue
                val (bPtX, bPtY) = OmrTemplate.warpToPt(warpPos.first, warpPos.second)

                canvas.drawCircle(bPtX, bPtY, bubbleRadiusPt, bubbleCirclePaint)
                drawCenteredText(canvas, opt, bPtX, bPtY + 2.2f, bubbleLetterPaint)
            }
        }

        // Alt Bilgilendirme
        val bottomInfoPaint = Paint().apply {
            isAntiAlias = true
            textSize = 7f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            color = Color.rgb(100, 116, 139)
        }
        drawCenteredText(canvas, "Optik form kamera okuyucu tarafından otomatik algılanır • Soru Defterim OMR Motoru", PAGE_WIDTH / 2f, 770f, bottomInfoPaint)
    }

    private fun drawBackCoverPage(canvas: Canvas, sorular: List<Soru>, targetTitle: String) {
        val strokePaint = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE; color = Color.BLACK }
        val fillPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL; color = Color.BLACK }
        val textPaint = Paint().apply { isAntiAlias = true; color = Color.BLACK }

        strokePaint.strokeWidth = 2.2f
        canvas.drawRect(25f, 25f, 570f, 817f, strokePaint)
        strokePaint.strokeWidth = 0.8f
        canvas.drawRect(28.5f, 28.5f, 566.5f, 813.5f, strokePaint)

        val bannerRect = RectF(45f, 42f, 550f, 88f)
        fillPaint.color = Color.rgb(24, 30, 42)
        canvas.drawRoundRect(bannerRect, 6f, 6f, fillPaint)

        val bannerTextPaint = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            textSize = 14f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        drawCenteredText(canvas, "CEVAP ANAHTARI VE KONU DAĞILIMI", 297.5f, 68f, bannerTextPaint)

        // 1. KISIM: OPTİK CEVAP FORMU
        val optikBox = RectF(45f, 100f, 550f, 435f)
        strokePaint.strokeWidth = 1f
        strokePaint.color = Color.rgb(71, 85, 105)
        canvas.drawRoundRect(optikBox, 5f, 5f, strokePaint)

        val optikHeader = RectF(45f, 100f, 550f, 124f)
        fillPaint.color = Color.rgb(226, 232, 240)
        canvas.drawRoundRect(optikHeader, 5f, 5f, fillPaint)
        canvas.drawLine(45f, 124f, 550f, 124f, strokePaint)

        textPaint.textSize = 9.5f
        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textPaint.color = Color.rgb(30, 41, 59)
        drawCenteredText(canvas, "OPTİK CEVAP FORMU (CEVAP ANAHTARI)", 297.5f, 116f, textPaint)

        drawOpticalAnswerSheet(canvas, sorular, optikBox)

        // 2. KISIM: KONU DAĞILIM TABLOSU
        val matrisBox = RectF(45f, 450f, 550f, 755f)
        strokePaint.strokeWidth = 1f
        strokePaint.color = Color.rgb(71, 85, 105)
        canvas.drawRoundRect(matrisBox, 5f, 5f, strokePaint)

        val matrisHeader = RectF(45f, 450f, 550f, 474f)
        fillPaint.color = Color.rgb(226, 232, 240)
        canvas.drawRoundRect(matrisHeader, 5f, 5f, fillPaint)
        canvas.drawLine(45f, 474f, 550f, 474f, strokePaint)

        drawCenteredText(canvas, "KONU DAĞILIM VE KAZANIM ANALİZ TABLOSU", 297.5f, 466f, textPaint)
        drawSubjectDistributionMatrix(canvas, sorular, matrisBox)

        val bottomPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(51, 65, 85)
            textSize = 10f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        drawCenteredText(canvas, "🎯 Soru Defterim ile Hatalarını Güce Dönüştür, Hedefine Ulaş!", 297.5f, 785f, bottomPaint)
    }

    private fun drawCompactAnswerKeyPage(canvas: Canvas, sorular: List<Soru>, targetTitle: String) {
        drawPageRunningHeaderFooter(canvas, 999, targetTitle, true, true)
        val textPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(30, 41, 59)
            textSize = 14f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        drawCenteredText(canvas, "CEVAP ANAHTARI", PAGE_WIDTH / 2f, 100f, textPaint)

        val optikBox = RectF(MARGIN_LEFT, 130f, PAGE_WIDTH - MARGIN_RIGHT, 720f)
        drawOpticalAnswerSheet(canvas, sorular, optikBox)
    }

    private fun drawOpticalAnswerSheet(canvas: Canvas, sorular: List<Soru>, container: RectF) {
        if (sorular.isEmpty()) {
            val emptyPaint = Paint().apply {
                isAntiAlias = true
                color = Color.rgb(148, 163, 184)
                textSize = 9f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
            }
            drawCenteredText(canvas, "Seçili soru bulunamadı.", container.centerX(), container.centerY(), emptyPaint)
            return
        }

        val totalQ = sorular.size
        val columns = when {
            totalQ <= 20 -> 2
            totalQ <= 40 -> 2
            else -> 3
        }
        val questionsPerCol = (totalQ + columns - 1) / columns
        val colWidth = (container.width() - 20f) / columns

        val options = listOf("A", "B", "C", "D", "E")
        val circleRadius = 5.2f
        val bubbleSpacing = 15.5f

        val numberPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(30, 41, 59)
            textSize = 7.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val unselectedCirclePaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
            color = Color.rgb(71, 85, 105)
        }
        val selectedCirclePaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = Color.rgb(15, 23, 42)
        }
        val unselectedLetterPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(71, 85, 105)
            textSize = 6f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val selectedLetterPaint = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            textSize = 6f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        val rowHeight = min(14.5f, (container.height() - 36f) / max(1, questionsPerCol))

        for (qIdx in sorular.indices) {
            val colIndex = qIdx / questionsPerCol
            val rowIndex = qIdx % questionsPerCol

            val soru = sorular[qIdx]
            val soruNo = qIdx + 1
            val correctOpt = soru.correctAnswer?.trim()?.uppercase()

            val rowX = container.left + 14f + colIndex * colWidth
            val rowY = container.top + 34f + rowIndex * rowHeight

            canvas.drawText("$soruNo.", rowX, rowY + 3.5f, numberPaint)

            val bubbleStartX = rowX + 22f
            for (optIdx in options.indices) {
                val optLetter = options[optIdx]
                val cx = bubbleStartX + optIdx * bubbleSpacing
                val cy = rowY

                val isCorrect = optLetter == correctOpt
                if (isCorrect) {
                    canvas.drawCircle(cx, cy, circleRadius, selectedCirclePaint)
                    drawCenteredText(canvas, optLetter, cx, cy + 2.2f, selectedLetterPaint)
                } else {
                    canvas.drawCircle(cx, cy, circleRadius, unselectedCirclePaint)
                    drawCenteredText(canvas, optLetter, cx, cy + 2.2f, unselectedLetterPaint)
                }
            }
        }
    }

    private fun drawSubjectDistributionMatrix(canvas: Canvas, sorular: List<Soru>, container: RectF) {
        val tableTop = container.top + 24f
        val rowHeight = 16f
        val strokePaint = Paint().apply {
            color = Color.rgb(203, 213, 225)
            strokeWidth = 0.6f
        }
        val headerTextPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(30, 41, 59)
            textSize = 8f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val cellTextPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(51, 65, 85)
            textSize = 7.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val altRowPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = Color.rgb(248, 250, 252)
        }

        val col0X = container.left
        val col1X = col0X + 50f
        val col2X = col1X + 115f
        val col3X = col2X + 240f
        val col4X = container.right

        val headerRect = RectF(col0X, tableTop, col4X, tableTop + rowHeight)
        val hBgPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL; color = Color.rgb(241, 245, 249) }
        canvas.drawRect(headerRect, hBgPaint)
        canvas.drawLine(col0X, tableTop + rowHeight, col4X, tableTop + rowHeight, strokePaint)

        canvas.drawText("Soru No", col0X + 8f, tableTop + 11.5f, headerTextPaint)
        canvas.drawText("Ders", col1X + 8f, tableTop + 11.5f, headerTextPaint)
        canvas.drawText("Konu", col2X + 8f, tableTop + 11.5f, headerTextPaint)
        canvas.drawText("Doğru Cevap", col3X + 8f, tableTop + 11.5f, headerTextPaint)

        canvas.drawLine(col1X, tableTop, col1X, tableTop + rowHeight, strokePaint)
        canvas.drawLine(col2X, tableTop, col2X, tableTop + rowHeight, strokePaint)
        canvas.drawLine(col3X, tableTop, col3X, tableTop + rowHeight, strokePaint)

        if (sorular.isEmpty()) {
            drawCenteredText(canvas, "Soru verisi yok", container.centerX(), tableTop + 35f, cellTextPaint)
            return
        }

        var currentY = tableTop + rowHeight
        for (idx in sorular.indices) {
            if (currentY + rowHeight > container.bottom - 4f) break
            val soru = sorular[idx]
            val soruNo = idx + 1
            val isAlt = idx % 2 == 1

            if (isAlt) {
                canvas.drawRect(col0X, currentY, col4X, currentY + rowHeight, altRowPaint)
            }

            val dersAdi = DersTaksonomisi.getDersAdi(soru.examType, soru.subjectId)
            val konuAdi = soru.topic.ifBlank { "-" }
            val cevap = soru.correctAnswer?.uppercase()?.trim() ?: "-"

            canvas.drawText("$soruNo", col0X + 12f, currentY + 11f, cellTextPaint)
            canvas.drawText(clipText(dersAdi, cellTextPaint, 100f), col1X + 8f, currentY + 11f, cellTextPaint)
            canvas.drawText(clipText(konuAdi, cellTextPaint, 225f), col2X + 8f, currentY + 11f, cellTextPaint)
            canvas.drawText(cevap, col3X + 18f, currentY + 11f, cellTextPaint)

            canvas.drawLine(col0X, currentY + rowHeight, col4X, currentY + rowHeight, strokePaint)
            canvas.drawLine(col1X, currentY, col1X, currentY + rowHeight, strokePaint)
            canvas.drawLine(col2X, currentY, col2X, currentY + rowHeight, strokePaint)
            canvas.drawLine(col3X, currentY, col3X, currentY + rowHeight, strokePaint)

            currentY += rowHeight
        }
    }

    // ==========================================
    // 4. BÖLÜM: YARDIMCI VE BİÇİMLENDİRME METOTLARI
    // ==========================================

    private fun loadQuestionBitmap(
        context: Context,
        soruId: Long,
        imagePath: String?,
        maxWidth: Int,
        maxHeight: Int,
        includeStylusDrawing: Boolean
    ): Bitmap? {
        val file = gorselDosyasi(context, imagePath) ?: return null
        return try {
            val baseBitmap = decodeSampledBitmap(file, maxWidth, maxHeight) ?: return null

            if (!includeStylusDrawing) {
                return baseBitmap
            }

            // Stylus çizimlerini entegre et
            val scratchpadFile = File(context.filesDir, "scratchpads/soru_${soruId}.json")
            if (!scratchpadFile.exists()) {
                return baseBitmap
            }

            try {
                val jsonText = scratchpadFile.readText()
                val json = Json { ignoreUnknownKeys = true }
                val drawingData = json.decodeFromString(QuestionDrawingData.serializer(), jsonText)
                if (drawingData.strokes.isEmpty()) {
                    return baseBitmap
                }

                val mutableBitmap = baseBitmap.copy(Bitmap.Config.ARGB_8888, true)
                val canvas = Canvas(mutableBitmap)
                val strokePaint = Paint().apply {
                    isAntiAlias = true
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }

                for (stroke in drawingData.strokes) {
                    if (stroke.points.isEmpty()) continue
                    strokePaint.color = stroke.colorArgb.toInt()
                    strokePaint.strokeWidth = stroke.baseWidth.coerceAtLeast(1.5f)

                    val path = Path()
                    path.moveTo(stroke.points[0].x, stroke.points[0].y)
                    for (i in 1 until stroke.points.size) {
                        val p = stroke.points[i]
                        path.lineTo(p.x, p.y)
                    }
                    canvas.drawPath(path, strokePaint)
                }

                if (!baseBitmap.isRecycled && baseBitmap != mutableBitmap) {
                    baseBitmap.recycle()
                }
                mutableBitmap
            } catch (_: Exception) {
                baseBitmap
            }
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    private fun decodeSampledBitmap(file: File, reqWidth: Int, reqHeight: Int): Bitmap? {
        if (!file.exists() || !file.isFile) return null
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, options)
            if (options.outWidth <= 0 || options.outHeight <= 0) return null

            var inSampleSize = 1
            while ((options.outWidth / (inSampleSize * 2)) >= reqWidth &&
                (options.outHeight / (inSampleSize * 2)) >= reqHeight
            ) {
                inSampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    private fun drawCenteredText(canvas: Canvas, text: String, cx: Float, cy: Float, paint: Paint) {
        val fontMetrics = paint.fontMetrics
        val y = if (fontMetrics != null) {
            cy - (fontMetrics.ascent + fontMetrics.descent) / 2f
        } else {
            cy
        }
        val textWidth = paint.measureText(text)
        canvas.drawText(text, cx - textWidth / 2f, y, paint)
    }

    private fun drawWrappedText(
        canvas: Canvas,
        text: String,
        x: Float,
        startY: Float,
        maxWidth: Float,
        paint: Paint,
        lineHeight: Float
    ): Float {
        var currentY = startY
        val words = text.split(Regex("\\s+"))
        var currentLine = StringBuilder()

        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(testLine) <= maxWidth) {
                currentLine = StringBuilder(testLine)
            } else {
                if (currentLine.isNotEmpty()) {
                    canvas.drawText(currentLine.toString(), x, currentY, paint)
                    currentY += lineHeight
                }
                currentLine = StringBuilder(word)
            }
        }
        if (currentLine.isNotEmpty()) {
            canvas.drawText(currentLine.toString(), x, currentY, paint)
            currentY += lineHeight
        }
        return currentY
    }

    private fun clipText(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        val charCount = paint.breakText(text, true, maxWidth - 8f, null)
        return text.substring(0, charCount) + "..."
    }
}

/**
 * Soru başlık şeridinde mini vektörel 2D matris kodu çizimi için pratik uzantı fonksiyonu.
 */
fun OmrMatrixCodeCodec.drawQuestionIdMatrixCode(
    canvas: Canvas,
    soruId: Long,
    x: Float,
    y: Float,
    size: Float = COMPACT_HEADER_QR_SIZE_PT,
    darkColor: Int = Color.rgb(30, 41, 59)
) {
    drawQuestionMatrixCode(canvas, RectF(x, y, x + size, y + size), soruId, darkColor)
}
