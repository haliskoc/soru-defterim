package com.sorudefteri.domain.omr.vision

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import com.sorudefteri.domain.omr.model.OmrTemplate

/**
 * Android Skia [Matrix.setPolyToPoly] kullanarak 4 köşesi bilinen optik kağıdı
 * 1200 x 1700 piksel standart şablon düzlemine dönüştüren Perspektif (Homografi) Motoru.
 */
object PerspectiveWarper {

    /**
     * Kaynak görüntüdeki 4 referans köşesini [outWidth] x [outHeight] boyutunda düzleştirilmiş
     * (perspektifi giderilmiş) yeni bir [Bitmap]'e dönüştürür.
     *
     * @param source Kamera veya galeriden gelen kaynak fotoğraf
     * @param corners Sırasıyla: [0] Sol-Üst, [1] Sağ-Üst, [2] Sağ-Alt, [3] Sol-Alt
     */
    fun warp(
        source: Bitmap,
        corners: List<PointF>,
        outWidth: Int = OmrTemplate.WARP_WIDTH,
        outHeight: Int = OmrTemplate.WARP_HEIGHT
    ): Bitmap {
        require(corners.size == 4) { "Perspektif düzeltme için 4 köşe noktası zorunludur." }

        val src = floatArrayOf(
            corners[0].x, corners[0].y, // Sol-Üst
            corners[1].x, corners[1].y, // Sağ-Üst
            corners[2].x, corners[2].y, // Sağ-Alt
            corners[3].x, corners[3].y  // Sol-Alt
        )
        val dst = floatArrayOf(
            0f, 0f,
            outWidth.toFloat(), 0f,
            outWidth.toFloat(), outHeight.toFloat(),
            0f, outHeight.toFloat()
        )

        val matrix = Matrix()
        val success = matrix.setPolyToPoly(src, 0, dst, 0, 4)

        val output = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.WHITE)

        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        if (success) {
            canvas.drawBitmap(source, matrix, paint)
        } else {
            // Dejenere dörtgen durumunda güvenli ölçekleme fallback'i
            val scaled = Bitmap.createScaledBitmap(source, outWidth, outHeight, true)
            canvas.drawBitmap(scaled, 0f, 0f, paint)
            if (scaled !== source && !scaled.isRecycled) {
                scaled.recycle()
            }
        }
        return output
    }
}
