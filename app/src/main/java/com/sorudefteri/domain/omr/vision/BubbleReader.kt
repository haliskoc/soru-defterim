package com.sorudefteri.domain.omr.vision

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.sorudefteri.data.pdf.OmrMatrixCodeCodec
import com.sorudefteri.domain.omr.model.OmrTemplate
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min

/**
 * Düzleştirilmiş (1200 x 1700) optik form [Bitmap]'i üzerinde:
 * 1. Bağıl kontrast algoritmasıyla A-E baloncuklarını okur,
 * 2. Çekirdek/halka (core/ring) oranıyla silgi izini (smudge) gerçek kurşun kalem dolgusundan ayırt eder,
 * 3. Birden fazla şık işaretlenmişse `"MULTI"` döner,
 * 4. Üst başlıktaki 2D matris koddan veya ML Kit TextRecognition ile `"SD-EXAM-..."` kimliğini çözümler.
 */
object BubbleReader {

    const val MULTI_MARK = "MULTI"

    // 2D Binary Matrix Kod alanı (1200x1700 warp uzayında sağ üst kutu: 10 satır x 8 sütun bit matrisi)
    const val MATRIX_LEFT = 948f
    const val MATRIX_TOP = 64f
    const val MATRIX_CELL_SIZE = 18f
    const val MATRIX_COLS = 8
    const val MATRIX_ROWS = 10
    private const val MATRIX_MAGIC_HEADER = 0x5A // 'Z' senkron baytı

    private val TEST_ID_REGEX = Regex("""SD-(?:EXAM|YKS)-[A-Z0-9]{4,12}""", RegexOption.IGNORE_CASE)

    data class BubbleMetrics(
        val option: String,
        val coreDarkness: Float,
        val ringDarkness: Float,
        val effectiveScore: Float
    )

    /**
     * Düzleştirilmiş 1200x1700 [warpedBitmap] üzerindeki tüm soruları (1..[questionCount]) okur.
     * @return Map<SoruNo, OkunanŞık?> (`null` = Boş, `"MULTI"` = Çift İşaret, `"A".."E"` = Seçilen Şık)
     */
    fun readAllQuestions(
        warpedBitmap: Bitmap,
        questionCount: Int = OmrTemplate.MAX_QUESTIONS
    ): Map<Int, String?> {
        val count = questionCount.coerceIn(1, OmrTemplate.MAX_QUESTIONS)
        val results = LinkedHashMap<Int, String?>(count)
        for (q in 1..count) {
            val centers = OmrTemplate.getBubbleCenters(q)
            results[q] = readRow(warpedBitmap, centers, OmrTemplate.BUBBLE_RADIUS)
        }
        return results
    }

    /**
     * Tek bir soru satırındaki A, B, C, D, E baloncuklarını analiz eder.
     *
     * - Bağıl kontrast: `effectiveScore >= medianDarkness * 1.65f` ve minimum karanlık eşiği.
     * - Silgi izi (smudge) filtresi: Çekirdek (iç %65 yarıçap) karanlığı, dış halka karanlığına göre
     *   cılız kalıyorsa veya homojen dolgu yoksa silgi izi olarak bastırılır.
     * - Birden fazla şık eşiği geçerse `"MULTI"` döner.
     */
    fun readRow(
        bitmap: Bitmap,
        centers: Map<String, Pair<Float, Float>>,
        radius: Float = OmrTemplate.BUBBLE_RADIUS
    ): String? {
        if (centers.isEmpty()) return null

        val metricsList = centers.map { (option, center) ->
            measureBubble(bitmap, option, center.first, center.second, radius)
        }

        val sortedScores = metricsList.map { it.effectiveScore }.sorted()
        val medianDarkness = sortedScores[sortedScores.size / 2].coerceAtLeast(1f)

        // Bağıl kontrast eşiği: satır medyanının en az 1.65 katı ve mutlak minimum 52f karanlık
        val relativeThreshold = max(medianDarkness * 1.65f, 52f)

        val filledOptions = metricsList.filter { m ->
            // Silgi izi ayrımı: çekirdek karanlığı yeterince güçlü olmalı ve sadece dış çember çizgisi olmamalı
            val coreToRingRatio = m.coreDarkness / m.ringDarkness.coerceAtLeast(15f)
            val isNotSmudge = m.coreDarkness >= 56f && coreToRingRatio >= 0.82f
            m.effectiveScore >= relativeThreshold && isNotSmudge
        }

        return when {
            filledOptions.isEmpty() -> null
            filledOptions.size == 1 -> filledOptions.first().option
            else -> {
                // Eğer en koyu şık, ikinci en koyu şıktan çok belirgin şekilde (%45+) daha koyuysa
                // (ikincisi silgi izi kalıntısıysa) en koyu olanı kabul et; aksi halde çift işaret ("MULTI")
                val topTwo = filledOptions.sortedByDescending { it.effectiveScore }
                val first = topTwo[0]
                val second = topTwo[1]
                if (first.coreDarkness >= second.coreDarkness * 1.48f && second.coreDarkness < 95f) {
                    first.option
                } else {
                    MULTI_MARK
                }
            }
        }
    }

    /**
     * Bir baloncuğun iç çekirdek (core: 0..0.68r) ve dış halka (ring: 0.68r..1.05r) karanlığını ölçer.
     */
    fun measureBubble(
        bitmap: Bitmap,
        option: String,
        cx: Float,
        cy: Float,
        radius: Float
    ): BubbleMetrics {
        val coreRadius = radius * 0.68f
        val coreDarkness = calculateDarkness(bitmap, cx, cy, coreRadius)
        val ringDarkness = calculateRingDarkness(bitmap, cx, cy, coreRadius, radius * 1.05f)

        // Çekirdek dolgusuna %78, halka dolgusuna %22 ağırlık ver (baskı çemberi yanıltmasın)
        val effectiveScore = coreDarkness * 0.78f + ringDarkness * 0.22f
        return BubbleMetrics(
            option = option,
            coreDarkness = coreDarkness,
            ringDarkness = ringDarkness,
            effectiveScore = effectiveScore
        )
    }

    /**
     * Belirtilen dairesel alan içindeki ortalama piksel karanlığını (0 = beyaz, 255 = siyah) hesaplar.
     */
    fun calculateDarkness(bitmap: Bitmap, cx: Float, cy: Float, r: Float): Float {
        var totalDarkness = 0f
        var count = 0
        val rInt = r.toInt().coerceAtLeast(1)
        val rSquare = r * r
        val maxW = bitmap.width - 1
        val maxH = bitmap.height - 1

        for (dy in -rInt..rInt) {
            for (dx in -rInt..rInt) {
                if (dx * dx + dy * dy <= rSquare) {
                    val px = (cx + dx).toInt().coerceIn(0, maxW)
                    val py = (cy + dy).toInt().coerceIn(0, maxH)
                    val color = bitmap.getPixel(px, py)
                    val rCol = (color shr 16) and 0xFF
                    val gCol = (color shr 8) and 0xFF
                    val bCol = color and 0xFF
                    val luminance = 0.299f * rCol + 0.587f * gCol + 0.114f * bCol
                    totalDarkness += (255f - luminance)
                    count++
                }
            }
        }
        return if (count > 0) totalDarkness / count else 0f
    }

    private fun calculateRingDarkness(
        bitmap: Bitmap,
        cx: Float,
        cy: Float,
        innerR: Float,
        outerR: Float
    ): Float {
        var totalDarkness = 0f
        var count = 0
        val rInt = outerR.toInt().coerceAtLeast(1)
        val innerSq = innerR * innerR
        val outerSq = outerR * outerR
        val maxW = bitmap.width - 1
        val maxH = bitmap.height - 1

        for (dy in -rInt..rInt) {
            for (dx in -rInt..rInt) {
                val distSq = (dx * dx + dy * dy).toFloat()
                if (distSq > innerSq && distSq <= outerSq) {
                    val px = (cx + dx).toInt().coerceIn(0, maxW)
                    val py = (cy + dy).toInt().coerceIn(0, maxH)
                    val color = bitmap.getPixel(px, py)
                    val rCol = (color shr 16) and 0xFF
                    val gCol = (color shr 8) and 0xFF
                    val bCol = color and 0xFF
                    val luminance = 0.299f * rCol + 0.587f * gCol + 0.114f * bCol
                    totalDarkness += (255f - luminance)
                    count++
                }
            }
        }
        return if (count > 0) totalDarkness / count else 0f
    }

    /**
     * Üst kısımdaki 2D matris kod alanından veya ML Kit TextRecognition ile
     * `"SD-EXAM-..."` / `"SD-YKS-..."` test kimliğini okur.
     */
    suspend fun extractTestId(warpedBitmap: Bitmap): String? {
        // 1. Önce 10x10 CRC8 korumalı 2D Vektörel Optik Matris Kodunu kontrol et
        val codecId = OmrMatrixCodeCodec.decodeMatrixCode(warpedBitmap, OmrTemplate.HEADER_CODE_ROI)
        if (!codecId.isNullOrBlank()) {
            return codecId
        }

        // 2. Alternatif 10x8 Binary Matrix kodunu kontrol et
        val matrixId = decode2DMatrixTestId(warpedBitmap)
        if (!matrixId.isNullOrBlank()) {
            return matrixId
        }

        // 3. ML Kit TextRecognition ile üst başlık bölgesinde "SD-EXAM-..." metnini ara
        return recognizeTestIdWithMlKit(warpedBitmap)
    }

    /**
     * 1200x1700 şablonun sağ üst köşesindeki 2D Binary Matrix (10 satır x 8 bit) kodunu çözer.
     * Satır 0: MATRIX_MAGIC_HEADER (0x5A)
     * Satır 1..8: 8 karakterlik ASCII test kodu eki (örn: "2026X8F9" -> "SD-EXAM-2026X8F9")
     * Satır 9: XOR sağlama baytı (checksum)
     */
    fun decode2DMatrixTestId(warpedBitmap: Bitmap): String? {
        if (warpedBitmap.width < OmrTemplate.WARP_WIDTH || warpedBitmap.height < 300) return null

        val bytes = IntArray(MATRIX_ROWS)
        for (row in 0 until MATRIX_ROWS) {
            var byteVal = 0
            val cy = MATRIX_TOP + (row + 0.5f) * MATRIX_CELL_SIZE
            for (col in 0 until MATRIX_COLS) {
                val cx = MATRIX_LEFT + (col + 0.5f) * MATRIX_CELL_SIZE
                val darkness = calculateDarkness(warpedBitmap, cx, cy, MATRIX_CELL_SIZE * 0.32f)
                val bit = if (darkness >= 115f) 1 else 0
                byteVal = (byteVal shl 1) or bit
            }
            bytes[row] = byteVal
        }

        if (bytes[0] != MATRIX_MAGIC_HEADER) return null

        var xorChecksum = MATRIX_MAGIC_HEADER
        val sb = StringBuilder(8)
        for (i in 1..8) {
            val b = bytes[i]
            xorChecksum = xorChecksum xor b
            val ch = b.toChar()
            if (ch.isLetterOrDigit() || ch == '-') {
                sb.append(ch)
            } else {
                return null
            }
        }

        if ((xorChecksum and 0xFF) != (bytes[9] and 0xFF)) return null
        val suffix = sb.toString().uppercase()
        return if (suffix.isNotBlank()) "SD-EXAM-$suffix" else null
    }

    /**
     * Bir testId'nin ("SD-EXAM-2026X8F9" vb.) 8 karakterlik ekini 2D matris bit dizisi olarak üretir.
     */
    fun encode2DMatrixBytes(testId: String): IntArray {
        val cleanSuffix = testId
            .uppercase()
            .removePrefix("SD-EXAM-")
            .removePrefix("SD-YKS-")
            .filter { it.isLetterOrDigit() }
            .take(8)
            .padEnd(8, '0')

        val out = IntArray(MATRIX_ROWS)
        out[0] = MATRIX_MAGIC_HEADER
        var xorChecksum = MATRIX_MAGIC_HEADER
        for (i in 0 until 8) {
            val code = cleanSuffix[i].code and 0xFF
            out[i + 1] = code
            xorChecksum = xorChecksum xor code
        }
        out[9] = xorChecksum and 0xFF
        return out
    }

    private suspend fun recognizeTestIdWithMlKit(warpedBitmap: Bitmap): String? {
        return try {
            val roi = OmrTemplate.HEADER_CODE_ROI
            val left = roi.left.toInt().coerceIn(0, warpedBitmap.width - 1)
            val top = roi.top.toInt().coerceIn(0, warpedBitmap.height - 1)
            val width = min(roi.width().toInt(), warpedBitmap.width - left).coerceAtLeast(1)
            val height = min(roi.height().toInt(), warpedBitmap.height - top).coerceAtLeast(1)

            val headerBitmap = Bitmap.createBitmap(warpedBitmap, left, top, width, height)
            val result = suspendCancellableCoroutine<String?> { cont ->
                val image = InputImage.fromBitmap(headerBitmap, 0)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        val raw = visionText.text
                        val match = TEST_ID_REGEX.find(raw)?.value?.uppercase()
                        if (cont.isActive) cont.resume(match)
                    }
                    .addOnFailureListener {
                        if (cont.isActive) cont.resume(null)
                    }
            }
            if (headerBitmap !== warpedBitmap && !headerBitmap.isRecycled) {
                headerBitmap.recycle()
            }
            result
        } catch (_: Exception) {
            null
        }
    }
}
