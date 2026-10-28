package com.sorudefteri.data.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

/**
 * 0 MB harici bağımlılıksız, deterministik 2D Vektörel Optik Matris / Karekod
 * Kodlayıcı (Encoder) ve Çözücü (Decoder).
 *
 * Matris Yapısı (10x10 Hücre):
 * - Dış Çerçeve (10x10 sınır):
 *   - Sol kenar (col = 0) ve Alt kenar (row = 9): Tam siyah L-Finder Pattern (referans kenarları).
 *   - Üst kenar (row = 0, col = 1..8) ve Sağ kenar (col = 9, row = 1..8): Siyah-Beyaz ardışık Timing Border.
 *   - 4 köşe hücresi (`(0,0)`, `(0,9)`, `(9,0)`, `(9,9)`): Sabit siyah referans hücreleri.
 * - İç Veri Matrisi (8x8 = 64 bit):
 *   - Bit 63..62 (2 bit): Payload Modu
 *     - `0` (`00`): `"SD-EXAM-"` önekli sınav kimliği (9 karaktere kadar 6-bit kodlu gövde)
 *     - `1` (`01`): `"SD-YKS-"` önekli sınav kimliği (9 karaktere kadar 6-bit kodlu gövde)
 *     - `2` (`10`): `"sorudefteri://soru/"` önekli soru kimliği (`soruId`)
 *     - `3` (`11`): Ham 6-bit alfa-sayısal metin (9 karaktere kadar)
 *   - Bit 61..8 (54 bit): 9 adet 6-bitlik karakter (`0-9`, `A-Z`, `a-z`, `-`)
 *   - Bit 7..0 (8 bit): CRC-8 hata denetim toplamı (Polinom: `0x07`, Başlangıç: `0xA5`)
 */
object OmrMatrixCodeCodec {

    const val GRID_SIZE = 10
    const val DATA_GRID_SIZE = 8

    /** Soru başlığında veya bağımsız kutuda standart 18x18mm (~50x50pt) matris kod boyutu. */
    const val QUESTION_QR_SIZE_PT = 50f

    /** Soru başlık barı içi kompakt matris kod boyutu (pt). */
    const val COMPACT_HEADER_QR_SIZE_PT = 18f

    /**
     * A4 (595x842 pt) yazdırılabilir optik form sayfasında `testId` matris kodunun
     * çizildiği varsayılan dikdörtgen (pt).
     *
     * Fiducial çerçevesi (`45..550 pt` X, `45..797 pt` Y) içinde sağ üst başlık bölgesine karşılık gelir.
     */
    val OPTICAL_FORM_MATRIX_RECT_PT = RectF(432.17f, 67.12f, 507.92f, 146.74f)

    /**
     * 1200x1700 normalize edilmiş (warp) optik form bitmap'inde `testId` matris kodunun
     * birebir karşılık geldiği ROI alanı (`OmrTemplate.HEADER_CODE_ROI` içinde yer alır).
     */
    val OPTICAL_FORM_MATRIX_ROI_WARP = RectF(920f, 50f, 1100f, 230f)

    private const val MODE_SD_EXAM = 0
    private const val MODE_SD_YKS = 1
    private const val MODE_QUESTION = 2
    private const val MODE_RAW = 3

    // 6-bit karakter tablosu (0 = dolgu/boş, 1..63 = 63 görünür karakter)
    private const val ALPHABET = "\u00000123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-"

    /**
     * Verilen [payload] dizgesini PDF veya ekran [canvas] üzerindeki [rect] alanına
     * 10x10 keskin vektörel 2D optik matris kodu olarak çizer.
     */
    fun drawMatrixCode(
        canvas: Canvas,
        rect: RectF,
        payload: String,
        darkColor: Int = Color.BLACK
    ) {
        val rectW = if (rect.width() > 0f) rect.width() else (rect.right - rect.left)
        val rectH = if (rect.height() > 0f) rect.height() else (rect.bottom - rect.top)
        if (rectW <= 0f || rectH <= 0f) return

        val bgPaint = Paint().apply {
            isAntiAlias = false
            style = Paint.Style.FILL
            color = Color.WHITE
        }
        canvas.drawRect(rect, bgPaint)

        val grid = encodeMatrixGrid(payload)
        val cellW = rectW / GRID_SIZE.toFloat()
        val cellH = rectH / GRID_SIZE.toFloat()

        val darkPaint = Paint().apply {
            isAntiAlias = false
            style = Paint.Style.FILL
            color = darkColor
        }

        for (r in 0 until GRID_SIZE) {
            val top = rect.top + r * cellH
            val bottom = rect.top + (r + 1) * cellH
            for (c in 0 until GRID_SIZE) {
                if (grid[r][c]) {
                    val left = rect.left + c * cellW
                    val right = rect.left + (c + 1) * cellW
                    canvas.drawRect(left, top, right, bottom, darkPaint)
                }
            }
        }
    }

    /**
     * Her soru başlığının sağ üst köşesine veya belirtilen [rect] alanına
     * soru kimlik kodunu (`soruId: Long` -> `"sorudefteri://soru/$soruId"`) çizer.
     */
    fun drawQuestionMatrixCode(
        canvas: Canvas,
        rect: RectF,
        soruId: Long,
        darkColor: Int = Color.rgb(30, 41, 59)
    ) {
        val safeId = if (soruId >= 0L) soruId.toString() else kotlin.math.abs(soruId).toString()
        drawMatrixCode(
            canvas = canvas,
            rect = rect,
            payload = "sorudefteri://soru/$safeId",
            darkColor = darkColor
        )
    }

    /**
     * Perspektifi düzeltilmiş (ör. 1200x1700) optik form veya soru bitmap'indeki [roi]
     * alanından 10x10 matris kodunu okuyup 8x8 iç bitleri ve CRC8'i doğrulayarak
     * orijinal `testId` (örn. `"SD-EXAM-A8F29C1B"`) veya soru kimliğini 1 milisaniyede çözer.
     */
    fun decodeMatrixCode(bitmap: Bitmap, roi: RectF = OPTICAL_FORM_MATRIX_ROI_WARP): String? {
        if (bitmap.width <= 0 || bitmap.height <= 0) return null

        val clampedRoi = RectF(
            roi.left.coerceIn(0f, (bitmap.width - 1).toFloat()),
            roi.top.coerceIn(0f, (bitmap.height - 1).toFloat()),
            roi.right.coerceIn(1f, bitmap.width.toFloat()),
            roi.bottom.coerceIn(1f, bitmap.height.toFloat())
        )
        if (clampedRoi.width() < GRID_SIZE || clampedRoi.height() < GRID_SIZE) return null

        // 1. Eğer geniş bir başlık ROI'si (örn. OmrTemplate.HEADER_CODE_ROI) verildiyse
        // önce standart 1200x1700 optik form matris koordinatını dene
        if (clampedRoi.width() > clampedRoi.height() * 1.35f &&
            clampedRoi.contains(OPTICAL_FORM_MATRIX_ROI_WARP)
        ) {
            decodeFromExactGridRect(bitmap, OPTICAL_FORM_MATRIX_ROI_WARP)?.let { return it }
        }

        // 2. Doğrudan verilen ROI'yi 10x10 ızgara olarak çözmeyi dene
        decodeFromExactGridRect(bitmap, clampedRoi)?.let { return it }

        // 3. ROI etrafında beyaz boşluk (quiet zone) veya hafif kayma varsa
        // koyu piksellerin sıkı sınırlayıcı kutusunu (tight bounding box) bulup çöz
        val tightRect = findTightDarkBoundingBox(bitmap, clampedRoi)
        if (tightRect != null && tightRect != clampedRoi) {
            decodeFromExactGridRect(bitmap, tightRect)?.let { return it }
        }

        return null
    }

    /**
     * [payload] bilgisini 10x10 boolean matrise (dış çerçeve + 8x8 veri/CRC8) dönüştürür.
     */
    fun encodeMatrixGrid(payload: String): Array<BooleanArray> {
        val bits64 = encodePayloadTo64Bits(payload)
        val grid = Array(GRID_SIZE) { BooleanArray(GRID_SIZE) }

        // Dış Çerçeve: L-Finder (sol ve alt tam dolu) + Timing (üst ve sağ ardışık)
        for (i in 0 until GRID_SIZE) {
            grid[i][0] = true                      // Sol L-Finder kenarı
            grid[GRID_SIZE - 1][i] = true          // Alt L-Finder kenarı
            grid[0][i] = (i % 2 == 0)              // Üst Timing kenarı
            grid[i][GRID_SIZE - 1] = (i % 2 == 0)  // Sağ Timing kenarı
        }
        // 4 köşe her zaman koyu
        grid[0][0] = true
        grid[0][GRID_SIZE - 1] = true
        grid[GRID_SIZE - 1][0] = true
        grid[GRID_SIZE - 1][GRID_SIZE - 1] = true

        // İç 8x8 = 64 bit (MSB -> LSB)
        for (r in 0 until DATA_GRID_SIZE) {
            for (c in 0 until DATA_GRID_SIZE) {
                val bitIndex = r * DATA_GRID_SIZE + c
                val isDark = ((bits64 ushr (63 - bitIndex)) and 1L) == 1L
                grid[r + 1][c + 1] = isDark
            }
        }
        return grid
    }

    /**
     * [payload] dizgesini 56 bit (2 bit mod + 9x6 bit karakter) + 8 bit CRC8 = 64 bit `Long` değere kodlar.
     */
    fun encodePayloadTo64Bits(payload: String): Long {
        val clean = extractCorePayload(payload.trim())
        val (mode, body) = when {
            clean.startsWith("SD-EXAM-", ignoreCase = true) -> {
                MODE_SD_EXAM to clean.substring(8).uppercase()
            }
            clean.startsWith("SD-YKS-", ignoreCase = true) -> {
                MODE_SD_YKS to clean.substring(7).uppercase()
            }
            clean.startsWith("sorudefteri://soru/", ignoreCase = true) -> {
                MODE_QUESTION to clean.substringAfterLast("/")
            }
            clean.startsWith("SD-Q-", ignoreCase = true) -> {
                MODE_QUESTION to clean.substring(5)
            }
            clean.length == 8 && clean.all { it.isLetterOrDigit() } -> {
                // 8 karakterlik saf sınav kodu (örn. "A8F29C1B") -> SD-EXAM-A8F29C1B
                MODE_SD_EXAM to clean.uppercase()
            }
            else -> {
                MODE_RAW to clean
            }
        }

        var data56 = (mode.toLong() and 0x03L)
        val trimmedBody = body.take(9)
        for (i in 0 until 9) {
            val ch = if (i < trimmedBody.length) trimmedBody[i] else '\u0000'
            val code6 = charTo6Bit(ch)
            data56 = (data56 shl 6) or (code6.toLong() and 0x3FL)
        }

        val crc = crc8(data56) and 0xFF
        return (data56 shl 8) or crc.toLong()
    }

    /**
     * 64 bitlik iç matris verisini CRC-8 doğrulamasından geçirerek orijinal `testId` / `payload`
     * dizgesine çözer. CRC-8 eşleşmezse veya veri boşsa `null` döner.
     */
    fun decode64BitsToPayload(bits64: Long): String? {
        if (bits64 == 0L || bits64 == -1L) return null

        val data56 = bits64 ushr 8
        val expectedCrc = (bits64 and 0xFFL).toInt()
        val calculatedCrc = crc8(data56)
        if (expectedCrc != calculatedCrc) return null

        val mode = ((data56 ushr 54) and 0x03L).toInt()
        val chars = CharArray(9)
        var length = 0
        for (i in 0 until 9) {
            val shift = (8 - i) * 6
            val code6 = ((data56 ushr shift) and 0x3FL).toInt()
            if (code6 == 0) break
            chars[length++] = sixBitToChar(code6)
        }
        if (length == 0) return null
        val body = String(chars, 0, length)

        return when (mode) {
            MODE_SD_EXAM -> "SD-EXAM-$body"
            MODE_SD_YKS -> "SD-YKS-$body"
            MODE_QUESTION -> "sorudefteri://soru/$body"
            else -> body
        }
    }

    /**
     * 56 bitlik veri (7 byte) için deterministik CRC-8 hesaplar (Polinom `0x07`, Init `0xA5`).
     */
    fun crc8(data56Bits: Long): Int {
        var crc = 0xA5
        for (byteIndex in 6 downTo 0) {
            val b = ((data56Bits ushr (byteIndex * 8)) and 0xFFL).toInt()
            crc = crc xor b
            for (bit in 0 until 8) {
                crc = if ((crc and 0x80) != 0) {
                    ((crc shl 1) xor 0x07) and 0xFF
                } else {
                    (crc shl 1) and 0xFF
                }
            }
        }
        return crc and 0xFF
    }

    private fun decodeFromExactGridRect(bitmap: Bitmap, rect: RectF): String? {
        val cellW = rect.width() / GRID_SIZE.toFloat()
        val cellH = rect.height() / GRID_SIZE.toFloat()
        if (cellW < 1f || cellH < 1f) return null

        val darkness = Array(GRID_SIZE) { FloatArray(GRID_SIZE) }
        var minDark = 255f
        var maxDark = 0f

        val sampleRx = max(1, (cellW * 0.25f).toInt())
        val sampleRy = max(1, (cellH * 0.25f).toInt())

        for (r in 0 until GRID_SIZE) {
            val cy = rect.top + (r + 0.5f) * cellH
            for (c in 0 until GRID_SIZE) {
                val cx = rect.left + (c + 0.5f) * cellW
                val d = sampleAreaDarkness(bitmap, cx, cy, sampleRx, sampleRy)
                darkness[r][c] = d
                if (d < minDark) minDark = d
                if (d > maxDark) maxDark = d
            }
        }

        // Yeterli kontrast yoksa (boş kağıt alanı) erken çık
        if (maxDark - minDark < 35f) return null
        val threshold = (minDark + maxDark) * 0.5f

        // L-Finder sol ve alt kenar doğrulaması (en az 16/20 hücre koyu olmalı)
        var finderHits = 0
        for (i in 0 until GRID_SIZE) {
            if (darkness[i][0] >= threshold) finderHits++
            if (darkness[GRID_SIZE - 1][i] >= threshold) finderHits++
        }
        if (finderHits < 15) return null

        // İç 8x8 bitleri oku
        var bits64 = 0L
        for (r in 0 until DATA_GRID_SIZE) {
            for (c in 0 until DATA_GRID_SIZE) {
                val isDark = darkness[r + 1][c + 1] >= threshold
                bits64 = (bits64 shl 1) or (if (isDark) 1L else 0L)
            }
        }

        return decode64BitsToPayload(bits64)
    }

    private fun sampleAreaDarkness(
        bitmap: Bitmap,
        cx: Float,
        cy: Float,
        rx: Int,
        ry: Int
    ): Float {
        val centerX = cx.toInt().coerceIn(0, bitmap.width - 1)
        val centerY = cy.toInt().coerceIn(0, bitmap.height - 1)
        val x0 = (centerX - rx / 2).coerceIn(0, bitmap.width - 1)
        val x1 = (centerX + rx / 2).coerceIn(0, bitmap.width - 1)
        val y0 = (centerY - ry / 2).coerceIn(0, bitmap.height - 1)
        val y1 = (centerY + ry / 2).coerceIn(0, bitmap.height - 1)

        var sum = 0f
        var count = 0
        for (y in y0..y1) {
            for (x in x0..x1) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val lum = 0.299f * r + 0.587f * g + 0.114f * b
                sum += (255f - lum)
                count++
            }
        }
        return if (count > 0) sum / count else 0f
    }

    private fun findTightDarkBoundingBox(bitmap: Bitmap, searchRoi: RectF): RectF? {
        val left = searchRoi.left.toInt().coerceIn(0, bitmap.width - 1)
        val top = searchRoi.top.toInt().coerceIn(0, bitmap.height - 1)
        val right = searchRoi.right.toInt().coerceIn(left + 1, bitmap.width)
        val bottom = searchRoi.bottom.toInt().coerceIn(top + 1, bitmap.height)

        var minX = right
        var minY = bottom
        var maxX = left
        var maxY = top
        var found = false

        for (y in top until bottom) {
            for (x in left until right) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val lum = 0.299f * r + 0.587f * g + 0.114f * b
                if (lum < 128f) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                    found = true
                }
            }
        }

        if (!found) return null
        val w = (maxX - minX + 1).toFloat()
        val h = (maxY - minY + 1).toFloat()
        if (w < GRID_SIZE || h < GRID_SIZE) return null
        return RectF(minX.toFloat(), minY.toFloat(), (maxX + 1).toFloat(), (maxY + 1).toFloat())
    }

    private fun extractCorePayload(raw: String): String {
        if (raw.startsWith("sorudefteri://omr/scan", ignoreCase = true)) {
            val idParam = raw.substringAfter("id=", "").substringBefore("&")
            if (idParam.isNotBlank()) return idParam
        }
        return raw
    }

    private fun charTo6Bit(ch: Char): Int {
        if (ch == '\u0000') return 0
        val idx = ALPHABET.indexOf(ch)
        if (idx > 0) return idx
        val upperIdx = ALPHABET.indexOf(ch.uppercaseChar())
        return if (upperIdx > 0) upperIdx else 63 // '-'
    }

    private fun sixBitToChar(code: Int): Char {
        return ALPHABET.getOrElse(code.coerceIn(0, ALPHABET.length - 1)) { '-' }
    }
}
