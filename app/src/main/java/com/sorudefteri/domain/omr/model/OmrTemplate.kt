package com.sorudefteri.domain.omr.model

import android.graphics.PointF
import android.graphics.RectF

/**
 * Normalize edilmiş 1200 x 1700 piksel (A4 595x842 pt oranında)
 * Akıllı Optik Cevap Formu şablon koordinatları ve geometrik sabitleri.
 *
 * 4 Köşe Fiducial Merkezleri (A4 pt uzayında):
 * - Sol-Üst: (45f, 45f) pt   -> Warp uzayında (0f, 0f)
 * - Sağ-Üst: (550f, 45f) pt  -> Warp uzayında (1200f, 0f)
 * - Sağ-Alt: (550f, 797f) pt -> Warp uzayında (1200f, 1700f)
 * - Sol-Alt: (45f, 797f) pt  -> Warp uzayında (0f, 1700f)
 */
object OmrTemplate {

    const val WARP_WIDTH = 1200
    const val WARP_HEIGHT = 1700

    const val A4_WIDTH_PT = 595f
    const val A4_HEIGHT_PT = 842f

    // A4 (595x842 pt) üzerinde 4 köşe referans karesi (Fiducial) merkezleri
    val FIDUCIAL_TOP_LEFT_PT = PointF(45f, 45f)
    val FIDUCIAL_TOP_RIGHT_PT = PointF(550f, 45f)
    val FIDUCIAL_BOTTOM_RIGHT_PT = PointF(550f, 797f)
    val FIDUCIAL_BOTTOM_LEFT_PT = PointF(45f, 797f)

    // 4 sütun × 30 satır = 120 soru kapasitesi
    const val COLUMNS = 4
    const val ROWS_PER_COLUMN = 30
    const val MAX_QUESTIONS = COLUMNS * ROWS_PER_COLUMN // 120

    val OPTIONS: List<String> = listOf("A", "B", "C", "D", "E")

    // Warp (1200 x 1700) uzayında baloncuk ızgarası parametreleri
    const val GRID_LEFT_X = 55f
    const val COLUMN_WIDTH = 280f
    const val FIRST_BUBBLE_OFFSET_X = 68f
    const val BUBBLE_STEP_X = 40f

    const val GRID_TOP_Y = 360f
    const val ROW_STEP_Y = 42f
    const val BUBBLE_RADIUS = 13.5f

    // Üst başlıkta Test Kimliği ("SD-EXAM-..." / QR / Metin) okuma bölgesi (1200x1700 uzayında)
    val HEADER_CODE_ROI = RectF(60f, 30f, 1140f, 320f)

    /**
     * Belirtilen soru numarası (1..120) için A, B, C, D, E şıklarının
     * 1200x1700 normalize warp uzayındaki (cx, cy) merkez koordinatlarını döner.
     */
    fun getBubbleCenters(questionNumber: Int): Map<String, Pair<Float, Float>> {
        require(questionNumber in 1..MAX_QUESTIONS) {
            "Soru numarası 1..$MAX_QUESTIONS aralığında olmalıdır (Verilen: $questionNumber)"
        }
        val zeroBased = questionNumber - 1
        val colIndex = zeroBased / ROWS_PER_COLUMN
        val rowIndex = zeroBased % ROWS_PER_COLUMN

        val colStartX = GRID_LEFT_X + colIndex * COLUMN_WIDTH
        val cy = GRID_TOP_Y + rowIndex * ROW_STEP_Y

        val map = LinkedHashMap<String, Pair<Float, Float>>(OPTIONS.size)
        for (optIndex in OPTIONS.indices) {
            val cx = colStartX + FIRST_BUBBLE_OFFSET_X + optIndex * BUBBLE_STEP_X
            map[OPTIONS[optIndex]] = Pair(cx, cy)
        }
        return map
    }

    /**
     * Belirtilen soru satırının solundaki zamanlama rayı (timing track) merkezini döner.
     */
    fun getTimingTrackCenter(questionNumber: Int): Pair<Float, Float> {
        val zeroBased = (questionNumber - 1).coerceIn(0, MAX_QUESTIONS - 1)
        val colIndex = zeroBased / ROWS_PER_COLUMN
        val rowIndex = zeroBased % ROWS_PER_COLUMN
        val cx = GRID_LEFT_X + colIndex * COLUMN_WIDTH + 10f
        val cy = GRID_TOP_Y + rowIndex * ROW_STEP_Y
        return Pair(cx, cy)
    }

    /**
     * A4 pt koordinatını (45..550, 45..797 fiducial çerçevesi içinde) 1200x1700 warp uzayına çevirir.
     */
    fun ptToWarp(ptX: Float, ptY: Float): Pair<Float, Float> {
        val spanX = FIDUCIAL_TOP_RIGHT_PT.x - FIDUCIAL_TOP_LEFT_PT.x
        val spanY = FIDUCIAL_BOTTOM_LEFT_PT.y - FIDUCIAL_TOP_LEFT_PT.y
        val wx = ((ptX - FIDUCIAL_TOP_LEFT_PT.x) / spanX) * WARP_WIDTH
        val wy = ((ptY - FIDUCIAL_TOP_LEFT_PT.y) / spanY) * WARP_HEIGHT
        return Pair(wx, wy)
    }

    /**
     * 1200x1700 warp uzayındaki koordinatı A4 pt koordinatına çevirir.
     */
    fun warpToPt(warpX: Float, warpY: Float): Pair<Float, Float> {
        val spanX = FIDUCIAL_TOP_RIGHT_PT.x - FIDUCIAL_TOP_LEFT_PT.x
        val spanY = FIDUCIAL_BOTTOM_LEFT_PT.y - FIDUCIAL_TOP_LEFT_PT.y
        val ptX = FIDUCIAL_TOP_LEFT_PT.x + (warpX / WARP_WIDTH) * spanX
        val ptY = FIDUCIAL_TOP_LEFT_PT.y + (warpY / WARP_HEIGHT) * spanY
        return Pair(ptX, ptY)
    }

    /**
     * Sınav türüne göre varsayılan toplam soru sayısını döner.
     */
    fun defaultQuestionCount(sinavTuru: String): Int {
        return when (sinavTuru.uppercase().trim()) {
            "TYT" -> 120
            "AYT" -> 80
            "LGS" -> 90
            "KPSS" -> 120
            "YDT" -> 80
            else -> 120
        }
    }

    /**
     * Sınav türü ve soru numarasına göre ders adı ve SoruEkleme subjectId bilgisini döner.
     * @return Pair(dersAdi, subjectId)
     */
    fun resolveSubjectForQuestion(sinavTuru: String, questionNumber: Int): Pair<String, String> {
        return when (sinavTuru.uppercase().trim()) {
            "TYT" -> when (questionNumber) {
                in 1..40 -> "Türkçe" to "turkce"
                in 41..45 -> "Sosyal Bilimler" to "tarih"
                in 46..50 -> "Sosyal Bilimler" to "cografya"
                in 51..55 -> "Sosyal Bilimler" to "felsefe"
                in 56..60 -> "Sosyal Bilimler" to "din_kulturu"
                in 61..90 -> "Matematik" to "matematik"
                in 91..100 -> "Matematik" to "geometri"
                in 101..107 -> "Fen Bilimleri" to "fizik"
                in 108..114 -> "Fen Bilimleri" to "kimya"
                in 115..120 -> "Fen Bilimleri" to "biyoloji"
                else -> "Türkçe" to "turkce"
            }
            "AYT" -> when (questionNumber) {
                in 1..30 -> "Matematik" to "ayt_matematik"
                in 31..40 -> "Matematik" to "ayt_geometri"
                in 41..54 -> "Fen Bilimleri" to "ayt_fizik"
                in 55..67 -> "Fen Bilimleri" to "ayt_kimya"
                in 68..80 -> "Fen Bilimleri" to "ayt_biyoloji"
                else -> "Matematik" to "ayt_matematik"
            }
            "LGS" -> when (questionNumber) {
                in 1..20 -> "Türkçe" to "lgs_turkce"
                in 21..30 -> "T.C. İnkılap Tarihi" to "lgs_inkilap"
                in 31..40 -> "Din Kültürü" to "lgs_din"
                in 41..50 -> "İngilizce" to "lgs_ingilizce"
                in 51..70 -> "Matematik" to "lgs_matematik"
                in 71..90 -> "Fen Bilimleri" to "lgs_fen"
                else -> "Matematik" to "lgs_matematik"
            }
            "KPSS" -> when (questionNumber) {
                in 1..30 -> "Türkçe" to "kpss_turkce"
                in 31..60 -> "Matematik" to "kpss_matematik"
                in 61..87 -> "Tarih" to "kpss_tarih"
                in 88..105 -> "Coğrafya" to "kpss_cografya"
                in 106..120 -> "Vatandaşlık" to "kpss_vatandaslik"
                else -> "Türkçe" to "kpss_turkce"
            }
            else -> "Matematik" to "matematik"
        }
    }
}
