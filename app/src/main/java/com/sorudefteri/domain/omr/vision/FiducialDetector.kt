package com.sorudefteri.domain.omr.vision

import android.graphics.Bitmap
import android.graphics.PointF
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Kamera Tabanlı OMR (Optik Okuma) için 4 Köşe Referans Karesi (Fiducial Marker) Algılayıcı.
 *
 * Hem [Bitmap] hem de 8-bit grayscale [ByteArray] (CameraX ImageProxy Y-plane)
 * üzerinde sıfır harici kütüphane bağımlılığıyla çalışır.
 */
object FiducialDetector {

    /**
     * 4 köşe tespit sonucu.
     * @param corners Sırasıyla: [0] Sol-Üst, [1] Sağ-Üst, [2] Sağ-Alt, [3] Sol-Alt
     * @param isLocked Dört köşe de yüksek güvenle bulunduysa ve dörtgen geometrisi geçerliyse true
     * @param confidence 0.0f .. 1.0f arası ortalama tespit güven skoru
     * @param cornerLocked Her bir köşenin (TL, TR, BR, BL) ayrı ayrı kilitlenme durumu
     */
    data class DetectionResult(
        val corners: List<PointF>,
        val isLocked: Boolean,
        val confidence: Float,
        val cornerLocked: List<Boolean> = listOf(isLocked, isLocked, isLocked, isLocked)
    )

    private data class CandidateCorner(
        val point: PointF,
        val score: Float,
        val found: Boolean
    )

    /**
     * Bitmap üzerinde 4 köşe referans karesini (Fiducial) arar.
     */
    fun detect(bitmap: Bitmap): DetectionResult {
        val width = bitmap.width
        val height = bitmap.height
        if (width < 64 || height < 64) {
            return fallbackResult(width, height)
        }

        // Hızlı analiz için çalışma boyutu (maksimum ~600px genişlik)
        val scale = if (width > 600) 600f / width.toFloat() else 1f
        val workW = (width * scale).toInt().coerceAtLeast(64)
        val workH = (height * scale).toInt().coerceAtLeast(64)

        val scaledBitmap = if (scale < 0.99f) {
            Bitmap.createScaledBitmap(bitmap, workW, workH, true)
        } else {
            bitmap
        }

        val pixels = IntArray(workW * workH)
        scaledBitmap.getPixels(pixels, 0, workW, 0, 0, workW, workH)
        if (scaledBitmap !== bitmap && !scaledBitmap.isRecycled) {
            scaledBitmap.recycle()
        }

        val luma = ByteArray(workW * workH)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val y = ((r * 77 + g * 150 + b * 29) shr 8).coerceIn(0, 255)
            luma[i] = y.toByte()
        }

        val rawResult = detectFromLuma(luma, workW, workH, workW)
        val invScale = 1f / scale
        val mappedCorners = rawResult.corners.map { pt ->
            PointF(
                (pt.x * invScale).coerceIn(0f, width - 1f),
                (pt.y * invScale).coerceIn(0f, height - 1f)
            )
        }
        return rawResult.copy(corners = mappedCorners)
    }

    /**
     * CameraX ImageAnalysis Y-plane (8-bit grayscale) verisi üzerinde 4 köşe arar.
     * Sonuç koordinatları görüntü düzleminde (rotationDegrees uygulanmışsa döndürülmüş uzayda) döner.
     */
    fun detectFromYPlane(
        yPlane: ByteArray,
        width: Int,
        height: Int,
        rowStride: Int = width,
        rotationDegrees: Int = 0
    ): DetectionResult {
        if (width < 64 || height < 64 || yPlane.isEmpty()) {
            return fallbackResult(width.coerceAtLeast(1), height.coerceAtLeast(1))
        }

        // Eğer rotasyon varsa veya rowStride != width ise kompakt alt-örneklenmiş (downsampled) buffer hazırla
        val step = max(1, min(width, height) / 360)
        val sampledW = width / step
        val sampledH = height / step
        val rot90 = ((rotationDegrees % 360) + 360) % 360
        val outW = if (rot90 == 90 || rot90 == 270) sampledH else sampledW
        val outH = if (rot90 == 90 || rot90 == 270) sampledW else sampledH

        val normalized = ByteArray(outW * outH)
        for (sy in 0 until sampledH) {
            val srcY = (sy * step).coerceIn(0, height - 1)
            val rowOffset = srcY * rowStride
            for (sx in 0 until sampledW) {
                val srcX = (sx * step).coerceIn(0, width - 1)
                val idx = rowOffset + srcX
                val lum = if (idx in yPlane.indices) yPlane[idx] else 0.toByte()

                val (dx, dy) = when (rot90) {
                    90 -> (sampledH - 1 - sy) to sx
                    180 -> (sampledW - 1 - sx) to (sampledH - 1 - sy)
                    270 -> sy to (sampledW - 1 - sx)
                    else -> sx to sy
                }
                if (dx in 0 until outW && dy in 0 until outH) {
                    normalized[dy * outW + dx] = lum
                }
            }
        }

        val res = detectFromLuma(normalized, outW, outH, outW)
        // Normalize koordinatlara (0f..1f) veya orijinal döndürülmüş piksel uzayına ölçekle
        val fullRotW = if (rot90 == 90 || rot90 == 270) height else width
        val fullRotH = if (rot90 == 90 || rot90 == 270) width else height
        val scaleX = fullRotW.toFloat() / outW.toFloat()
        val scaleY = fullRotH.toFloat() / outH.toFloat()

        return res.copy(
            corners = res.corners.map { PointF(it.x * scaleX, ptY(it.y * scaleY, fullRotH)) }
        )
    }

    private fun ptY(y: Float, maxH: Int): Float = y.coerceIn(0f, (maxH - 1).coerceAtLeast(1).toFloat())

    /**
     * Fotoğraf çekildiğinde 4 köşe tam kilitlenmemiş olsa bile, bulunan köşeleri koruyup
     * bulunamayanları kılavuz çerçeve (fallback) köşeleriyle tamamlayarak kesintisiz okuma sağlar.
     */
    fun detectWithFallback(bitmap: Bitmap): DetectionResult {
        val detected = detect(bitmap)
        if (detected.isLocked) return detected

        val fallback = getGuideFrameCorners(bitmap.width, bitmap.height)
        val merged = (0 until 4).map { idx ->
            if (detected.cornerLocked.getOrElse(idx) { false }) {
                detected.corners[idx]
            } else {
                fallback[idx]
            }
        }
        val valid = isValidQuad(merged, bitmap.width, bitmap.height)
        return DetectionResult(
            corners = if (valid) merged else fallback,
            isLocked = detected.isLocked,
            confidence = detected.confidence.coerceAtLeast(0.45f),
            cornerLocked = detected.cornerLocked
        )
    }

    /**
     * Kullanıcı kağıdı kılavuz çerçeveye hizaladığında kullanılan varsayılan 4 köşe.
     */
    fun getGuideFrameCorners(width: Int, height: Int): List<PointF> {
        val padX = width * 0.06f
        val padY = height * 0.06f
        return listOf(
            PointF(padX, padY),                         // Sol-Üst
            PointF(width - padX, padY),                 // Sağ-Üst
            PointF(width - padX, height - padY),        // Sağ-Alt
            PointF(padX, height - padY)                 // Sol-Alt
        )
    }

    private fun fallbackResult(width: Int, height: Int): DetectionResult {
        return DetectionResult(
            corners = getGuideFrameCorners(width, height),
            isLocked = false,
            confidence = 0f,
            cornerLocked = listOf(false, false, false, false)
        )
    }

    private fun detectFromLuma(
        luma: ByteArray,
        width: Int,
        height: Int,
        stride: Int
    ): DetectionResult {
        val quadW = (width * 0.25f).toInt().coerceAtLeast(16)
        val quadH = (height * 0.25f).toInt().coerceAtLeast(16)

        val fallback = getGuideFrameCorners(width, height)

        // 4 Kadranda (Sol-Üst, Sağ-Üst, Sağ-Alt, Sol-Alt) adaptif siyah kare araması
        val tl = findFiducialInQuadrant(luma, stride, 0, 0, quadW, quadH, fallback[0])
        val tr = findFiducialInQuadrant(luma, stride, width - quadW, 0, width, quadH, fallback[1])
        val br = findFiducialInQuadrant(luma, stride, width - quadW, height - quadH, width, height, fallback[2])
        val bl = findFiducialInQuadrant(luma, stride, 0, height - quadH, quadW, height, fallback[3])

        val candidates = listOf(tl, tr, br, bl)
        val corners = candidates.map { it.point }
        val lockedFlags = candidates.map { it.found }
        val avgConfidence = candidates.map { it.score }.average().toFloat().coerceIn(0f, 1f)

        val allFound = lockedFlags.all { it }
        val quadValid = isValidQuad(corners, width, height)

        return DetectionResult(
            corners = if (quadValid) corners else fallback,
            isLocked = allFound && quadValid && avgConfidence >= 0.58f,
            confidence = if (quadValid) avgConfidence else avgConfidence * 0.5f,
            cornerLocked = lockedFlags
        )
    }

    /**
     * Belirtilen kadran içinde adaptif karanlık eşiklemesiyle en belirgin siyah referans karesinin
     * ağırlık merkezini (PointF) bulur.
     */
    private fun findFiducialInQuadrant(
        luma: ByteArray,
        stride: Int,
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        fallbackPoint: PointF
    ): CandidateCorner {
        val qW = (endX - startX).coerceAtLeast(1)
        val qH = (endY - startY).coerceAtLeast(1)

        var sumLum = 0L
        var minLum = 255
        var maxLum = 0
        var sampleCount = 0

        for (y in startY until endY step 2) {
            val rowOff = y * stride
            for (x in startX until endX step 2) {
                val v = luma[rowOff + x].toInt() and 0xFF
                sumLum += v
                if (v < minLum) minLum = v
                if (v > maxLum) maxLum = v
                sampleCount++
            }
        }

        if (sampleCount == 0 || (maxLum - minLum) < 28) {
            return CandidateCorner(fallbackPoint, 0.1f, false)
        }

        val meanLum = (sumLum / sampleCount).toInt()
        // Adaptif karanlık eşiği: kadranın en koyu noktası ile ortalaması arasında
        val darkThreshold = (minLum + ((meanLum - minLum) * 0.42f).toInt()).coerceIn(30, 145)

        // Beklenen referans karesi pencere yarıçapı
        val winRadius = (min(qW, qH) * 0.09f).toInt().coerceIn(3, 18)
        val step = max(1, winRadius / 2)

        var bestCx = -1
        var bestCy = -1
        var bestDarkRatio = 0f
        var bestContrast = 0f

        val margin = winRadius + 1
        for (cy in (startY + margin) until (endY - margin) step step) {
            for (cx in (startX + margin) until (endX - margin) step step) {
                var darkPixels = 0
                var totalPixels = 0
                var coreLumSum = 0

                for (dy in -winRadius..winRadius) {
                    val rOff = (cy + dy) * stride
                    for (dx in -winRadius..winRadius) {
                        val lum = luma[rOff + cx + dx].toInt() and 0xFF
                        coreLumSum += lum
                        if (lum <= darkThreshold) {
                            darkPixels++
                        }
                        totalPixels++
                    }
                }

                val darkRatio = darkPixels.toFloat() / totalPixels.toFloat()
                if (darkRatio >= 0.62f) {
                    val coreMean = coreLumSum.toFloat() / totalPixels.toFloat()
                    val contrast = (meanLum - coreMean).coerceAtLeast(0f)
                    val combined = darkRatio * (contrast / 128f)
                    val bestCombined = bestDarkRatio * (bestContrast / 128f)
                    if (combined > bestCombined) {
                        bestDarkRatio = darkRatio
                        bestContrast = contrast
                        bestCx = cx
                        bestCy = cy
                    }
                }
            }
        }

        if (bestCx < 0 || bestCy < 0 || bestContrast < 22f) {
            return CandidateCorner(fallbackPoint, 0.15f, false)
        }

        // Bulunan en güçlü aday etrafında ağırlık merkezi (centroid) hesabı
        val refineRadius = (winRadius * 2).coerceAtLeast(6)
        val rx0 = (bestCx - refineRadius).coerceAtLeast(startX)
        val rx1 = (bestCx + refineRadius).coerceAtMost(endX - 1)
        val ry0 = (bestCy - refineRadius).coerceAtLeast(startY)
        val ry1 = (bestCy + refineRadius).coerceAtMost(endY - 1)

        var weightedX = 0f
        var weightedY = 0f
        var totalWeight = 0f

        for (y in ry0..ry1) {
            val rOff = y * stride
            for (x in rx0..rx1) {
                val lum = luma[rOff + x].toInt() and 0xFF
                if (lum <= darkThreshold) {
                    val weight = (darkThreshold - lum + 10).toFloat()
                    weightedX += x * weight
                    weightedY += y * weight
                    totalWeight += weight
                }
            }
        }

        val finalX = if (totalWeight > 0f) weightedX / totalWeight else bestCx.toFloat()
        val finalY = if (totalWeight > 0f) weightedY / totalWeight else bestCy.toFloat()
        val confidence = ((bestDarkRatio * 0.6f) + ((bestContrast / 160f).coerceAtMost(1f) * 0.4f))
            .coerceIn(0f, 1f)

        return CandidateCorner(
            point = PointF(finalX, finalY),
            score = confidence,
            found = confidence >= 0.52f
        )
    }

    /**
     * Dört köşenin dışbükey (convex) bir dörtgen oluşturduğunu ve geçerli A4/optik form
     * en-boy oranında olduğunu doğrular.
     */
    fun isValidQuad(corners: List<PointF>, imageWidth: Int, imageHeight: Int): Boolean {
        if (corners.size != 4) return false
        val tl = corners[0]
        val tr = corners[1]
        val br = corners[2]
        val bl = corners[3]

        // Köşelerin kadran sıralaması tutarlı olmalı
        if (tr.x <= tl.x || br.x <= bl.x) return false
        if (bl.y <= tl.y || br.y <= tr.y) return false

        val topW = hypot((tr.x - tl.x).toDouble(), (tr.y - tl.y).toDouble()).toFloat()
        val bottomW = hypot((br.x - bl.x).toDouble(), (br.y - bl.y).toDouble()).toFloat()
        val leftH = hypot((bl.x - tl.x).toDouble(), (bl.y - tl.y).toDouble()).toFloat()
        val rightH = hypot((br.x - tr.x).toDouble(), (br.y - tr.y).toDouble()).toFloat()

        val minSpanW = imageWidth * 0.35f
        val minSpanH = imageHeight * 0.35f
        if (topW < minSpanW || bottomW < minSpanW || leftH < minSpanH || rightH < minSpanH) {
            return false
        }

        // Perspektif bozulması aşırı olmamalı (karşılıklı kenarlar birbirinin 2.5 katını aşmamalı)
        val widthRatio = max(topW, bottomW) / min(topW, bottomW).coerceAtLeast(1f)
        val heightRatio = max(leftH, rightH) / min(leftH, rightH).coerceAtLeast(1f)
        if (widthRatio > 2.4f || heightRatio > 2.4f) return false

        // Dışbükeylik (Convexity) çapraz çarpım kontrolü
        fun crossProductZ(a: PointF, b: PointF, c: PointF): Float {
            return (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
        }

        val c1 = crossProductZ(tl, tr, br)
        val c2 = crossProductZ(tr, br, bl)
        val c3 = crossProductZ(br, bl, tl)
        val c4 = crossProductZ(bl, tl, tr)

        val allPositive = c1 > 0f && c2 > 0f && c3 > 0f && c4 > 0f
        val allNegative = c1 < 0f && c2 < 0f && c3 < 0f && c4 < 0f

        return allPositive || allNegative
    }
}
