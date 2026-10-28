package com.sorudefteri.data.pdf

/**
 * PDF Çıktı Şablon Türleri.
 */
enum class PdfTemplateMode(val title: String, val description: String) {
    OSYM_DENEME(
        title = "ÖSYM Deneme Kitapçığı",
        description = "Resmi 2 sütunlu sınav düzeni, kapak sayfası, optik cevap formu ve konu dağılım matrisi."
    ),
    COZUMLU_FASIKUL(
        title = "Açıklamalı Çözüm Fasikülü",
        description = "Soru görseli + yapay zeka adımlı çözümü, doğru cevap ve konu açıklamaları."
    ),
    OGRETMEN_BULTENI(
        title = "Öğretmen / Etüt Bülteni",
        description = "Öğrencinin hata notu + öğretmenin kalemle çözmesi için geniş kareli çözüm alanı."
    ),
    EKO_KOMPAKT(
        title = "Eko / Kompakt Soru Listesi",
        description = "Mürekkep ve kağıt tasarruflu, sayfa başına maksimum soru yerleştiren çalışma yaprağı."
    )
}

/**
 * PDF Çıktı Kullanım Amacı / Hedef Ortamı.
 */
enum class PdfTargetMedium(val title: String, val description: String) {
    PRINT_PAPER(
        title = "Yazıcı / Kağıt Baskı Modu",
        description = "Mürekkep tasarruflu, yüksek kontrastlı, siyah/beyaz dostu temiz baskı."
    ),
    DIGITAL_TABLET(
        title = "Tablet / Dijital Çalışma Modu",
        description = "Stylus (tablet kalemi) ile çözüme uygun, ferah, modern renkler ve geniş karalama alanları."
    )
}

/**
 * Sayfa İçi Soru Yoğunluğu / Sığdırma Stratejisi.
 */
enum class PdfDensity(val title: String, val description: String, val marginPt: Float, val columnGapPt: Float) {
    MAX_COMPACT(
        title = "Maksimum Soru (Ultra Tasarruf)",
        description = "Sayfa başına 6-10 soru. Dar kenar boşlukları ve sıfır gereksiz boşluk.",
        marginPt = 22f,
        columnGapPt = 14f
    ),
    BALANCED(
        title = "Dengeli (Standart ÖSYM)",
        description = "Sayfa başına 4-6 soru. Klasik resmi sınav mizanpajı.",
        marginPt = 35f,
        columnGapPt = 22f
    ),
    SPACIOUS(
        title = "Ferah (Geniş Çözüm Alanı)",
        description = "Sayfa başına 2-4 soru. Tablette rahatça çizim ve not alma imkanı.",
        marginPt = 40f,
        columnGapPt = 26f
    )
}

/**
 * Soru altı çözüm alanı (karalama / öğretmen alanı) büyüklüğü.
 */
enum class WorkspaceType(val title: String, val heightPt: Float) {
    NONE("Boşluksuz (0 pt)", 0f),
    COMPACT("Kompakt (50 pt)", 50f),
    STANDARD("Standart (85 pt)", 85f),
    LARGE("Geniş / Öğretmen Alanı (140 pt)", 140f)
}

/**
 * Kütüphaneden PDF çıktısı üretirken kullanılan tüm yapılandırma ayarları.
 */
data class PdfExportConfig(
    val title: String = "Soru Defterim Özel Çalışma Fasikülü",
    val subtitle: String? = null,
    val studentName: String? = null,
    val templateMode: PdfTemplateMode = PdfTemplateMode.OSYM_DENEME,
    val targetMedium: PdfTargetMedium = PdfTargetMedium.PRINT_PAPER,
    val density: PdfDensity = PdfDensity.BALANCED,
    val columnsCount: Int = 2, // 1 (Geniş / Tek Sütun) veya 2 (ÖSYM / Çift Sütun)
    val includeCoverPage: Boolean = true,
    val includeBackCover: Boolean = true,
    val includeAnswerKey: Boolean = true,
    // Varsayılanlar DÜZELTİLDİ: includeAiSolution ve includeStudentNote `false`
    // idi. Kullanıcı AI çözümünü kaydetmiş olsa bile PDF'e hiç girmiyordu ve
    // soru "PDF aktarımında çözümüm gelmiyor" şeklinde bildiriliyordu.
    // Üçünü de varsayılan açık yapıyoruz; kullanıcı dialog'dan kapatabilir.
    val includeAiSolution: Boolean = true,
    val includeStudentNote: Boolean = true,
    val includeMistakeType: Boolean = true,
    val includeStylusDrawing: Boolean = true,
    val includeSourceInfo: Boolean = true,
    val includeDifficulty: Boolean = true,
    val workspaceType: WorkspaceType = WorkspaceType.STANDARD,
    val includePrintableOpticalForm: Boolean = true,
    val includeQuestionQrCodes: Boolean = true,
    val testId: String = "SD-EXAM-" + java.util.UUID.randomUUID().toString().take(8).uppercase(),
    val bookletType: String = "A"
)
