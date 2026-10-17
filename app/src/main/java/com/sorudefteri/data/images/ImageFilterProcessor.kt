package com.sorudefteri.data.images

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import kotlin.math.roundToInt

/**
 * Desteklenen görsel filtre tipleri.
 */
enum class ImageFilterType(
    val title: String,
    val description: String
) {
    ORIGINAL(
        title = "Orijinal",
        description = "Değişiklik yapmadan saklar"
    ),
    SCANNER_BW(
        title = "Fotokopi",
        description = "Sarı ışık ve gölgeleri siler, metni yüksek kontrastla netleştirir"
    ),
    NEGATIVE_DARK(
        title = "Negatif (Gece)",
        description = "OLED ve gece çalışması için koyu kağıt ve beyaz metin"
    ),
    MAGIC_COLOR(
        title = "Canlı Belge",
        description = "Belge kontrastını artırırken renkli çizim ve kalem izlerini korur"
    )
}

/**
 * Sınav kağıtları, test kitapları ve soru fotoğrafları için optimize edilmiş
 * görsel filtre işlemcisi.
 */
object ImageFilterProcessor {

    /**
     * Orijinal filtre matrisi (Birim matris).
     */
    val MATRIX_ORIGINAL = floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )

    /**
     * Fotokopi / Tarayıcı Yüksek Kontrast matrisi:
     * - Sarı masa lambası ışığını ve gölgeleri eler.
     * - Grayscale parlaklık ağırlıklarıyla metin siyahlaşır, kağıt bembeyaz olur.
     */
    val MATRIX_SCANNER_BW = floatArrayOf(
        0.598f, 1.174f, 0.228f, 0f, -100f,
        0.598f, 1.174f, 0.228f, 0f, -100f,
        0.598f, 1.174f, 0.228f, 0f, -100f,
        0f,     0f,     0f,     1f,    0f
    )

    /**
     * Negatif / Gece Modu matrisi:
     * - Beyaz/sarı kağıdı derin siyaha / koyu griye dönüştürür.
     * - Siyah/koyu metinleri parlak beyaza çevirir.
     * - OLED ekran tasarrufu ve gece göz yorgunluğunu önlemek için idealdir.
     */
    val MATRIX_NEGATIVE_DARK = floatArrayOf(
        -0.5382f, -1.0566f, -0.2052f, 0f, 310f,
        -0.5382f, -1.0566f, -0.2052f, 0f, 310f,
        -0.5382f, -1.0566f, -0.2052f, 0f, 310f,
        0f,        0f,        0f,     1f,   0f
    )

    /**
     * Belge Renk İyileştirme matrisi:
     * - Sarı ışık eğilimini mavi kanalı destekleyerek dengeler.
     * - Kağıt zeminini aydınlatırken renkli kalem (kırmızı, mavi vb.) çizimlerini canlı tutar.
     */
    val MATRIX_MAGIC_COLOR = floatArrayOf(
        1.30f, 0.00f, 0.00f, 0f, -25f,
        0.00f, 1.32f, 0.00f, 0f, -25f,
        0.00f, 0.00f, 1.45f, 0f, -20f,
        0.00f, 0.00f, 0.00f, 1f,   0f
    )

    /**
     * Filtre tipine karşılık gelen 20 elemanlı 4x5 ColorMatrix dizisini döner.
     */
    fun getColorMatrixArray(filterType: ImageFilterType): FloatArray {
        return when (filterType) {
            ImageFilterType.ORIGINAL -> MATRIX_ORIGINAL
            ImageFilterType.SCANNER_BW -> MATRIX_SCANNER_BW
            ImageFilterType.NEGATIVE_DARK -> MATRIX_NEGATIVE_DARK
            ImageFilterType.MAGIC_COLOR -> MATRIX_MAGIC_COLOR
        }
    }

    /**
     * Filtre tipine karşılık gelen android.graphics.ColorMatrix nesnesini döner.
     */
    fun getColorMatrix(filterType: ImageFilterType): ColorMatrix {
        return ColorMatrix(getColorMatrixArray(filterType))
    }

    /**
     * Tek bir pikselin renk bileşenlerini (0..255) verilen filtre matrisine göre dönüştürür.
     * Birim testlerde Android grafik bağımlılığı olmadan matematiksel doğrulamayı sağlar.
     * @return [R, G, B, A] dizisi (değerler 0..255 aralığında)
     */
    fun transformPixel(r: Int, g: Int, b: Int, a: Int = 255, filterType: ImageFilterType): IntArray {
        val m = getColorMatrixArray(filterType)
        val rOut = (m[0] * r + m[1] * g + m[2] * b + m[3] * a + m[4]).roundToInt().coerceIn(0, 255)
        val gOut = (m[5] * r + m[6] * g + m[7] * b + m[8] * a + m[9]).roundToInt().coerceIn(0, 255)
        val bOut = (m[10] * r + m[11] * g + m[12] * b + m[13] * a + m[14]).roundToInt().coerceIn(0, 255)
        val aOut = (m[15] * r + m[16] * g + m[17] * b + m[18] * a + m[19]).roundToInt().coerceIn(0, 255)
        return intArrayOf(rOut, gOut, bOut, aOut)
    }

    /**
     * Verilen Bitmap üzerine seçilen filtreyi donanım hızlandırmalı Canvas ve ColorMatrix ile uygular.
     * OOM veya geçersiz boyut durumunda güvenli bir şekilde orijinal görseli korur.
     */
    fun applyFilter(source: Bitmap, filterType: ImageFilterType): Bitmap {
        if (filterType == ImageFilterType.ORIGINAL) {
            return source
        }

        if (source.isRecycled || source.width <= 0 || source.height <= 0) {
            return source
        }

        return try {
            val width = source.width
            val height = source.height

            // HARDWARE bitmap kontrolü: Canvas çizimi için yazılım konfigürasyonu gerekir
            val softwareSource = if (source.config == Bitmap.Config.HARDWARE) {
                source.copy(Bitmap.Config.ARGB_8888, false) ?: source
            } else {
                source
            }

            val targetConfig = if (softwareSource.config == Bitmap.Config.HARDWARE) {
                Bitmap.Config.ARGB_8888
            } else {
                softwareSource.config ?: Bitmap.Config.ARGB_8888
            }

            val result = Bitmap.createBitmap(width, height, targetConfig)
            val canvas = Canvas(result)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(getColorMatrixArray(filterType))
            }
            canvas.drawBitmap(softwareSource, 0f, 0f, paint)

            // Geçici dönüştürülen kaynak varsa temizle
            if (softwareSource !== source && !softwareSource.isRecycled) {
                try { softwareSource.recycle() } catch (_: Exception) {}
            }

            result
        } catch (oom: OutOfMemoryError) {
            oom.printStackTrace()
            source
        } catch (e: Exception) {
            e.printStackTrace()
            source
        }
    }
}
