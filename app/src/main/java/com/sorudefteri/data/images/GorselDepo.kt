package com.sorudefteri.data.images

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Uygulama içi soru ve çözüm görsellerini yöneten yerel dosya deposu.
 * Görselleri dahili depolamada (internal storage) filesDir/images/ dizininde saklar,
 * maksimum 1600px boyutuna ölçeklendirir ve %85 JPEG kalitesiyle sıkıştırır.
 */
@Singleton
class GorselDepo @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val imagesDir: File by lazy {
        File(context.filesDir, "images").apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    companion object {
        private const val MAX_DIMENSION = 1600
        private const val JPEG_QUALITY = 85
    }

    /**
     * Galeri veya dosya URI'sinden soru görselini işleyip yerel depolamaya kaydeder.
     * @return Kaydedilen dosyanın mutlak yolu (absolute path)
     */
    suspend fun kaydetSoruGorseli(sourceUri: Uri): String = withContext(Dispatchers.IO) {
        val fileName = "soru_${UUID.randomUUID()}.jpg"
        saveUriToFile(sourceUri, fileName)
    }

    /**
     * Galeri veya dosya URI'sinden çözüm görselini işleyip yerel depolamaya kaydeder.
     * @return Kaydedilen dosyanın mutlak yolu (absolute path)
     */
    suspend fun kaydetCozumGorseli(sourceUri: Uri): String = withContext(Dispatchers.IO) {
        val fileName = "cozum_${UUID.randomUUID()}.jpg"
        saveUriToFile(sourceUri, fileName)
    }

    /**
     * Kalıcı depolama dizini (filesDir/images) dışındaki bir dosyayı (örn: cacheDir'den gelen kamera çekimi)
     * kalıcı klasöre kopyalar ve sıkıştırır. Geçici dosya cache'te ise temizler.
     * @return Kalıcı dosyanın mutlak yolu (absolute path)
     */
    suspend fun kaydetDosyadan(sourceFile: File, isSolution: Boolean): String = withContext(Dispatchers.IO) {
        if (sourceFile.parentFile?.absolutePath == imagesDir.absolutePath && sourceFile.exists()) {
            return@withContext sourceFile.absolutePath
        }
        val uri = Uri.fromFile(sourceFile)
        val permanentPath = if (isSolution) kaydetCozumGorseli(uri) else kaydetSoruGorseli(uri)
        // Eğer kaynak dosya cache dizinindeyse temizle
        try {
            if (sourceFile.absolutePath.contains(context.cacheDir.absolutePath) && sourceFile.exists()) {
                sourceFile.delete()
            }
        } catch (_: Exception) {}
        permanentPath
    }

    fun getImagesDirectory(): File = imagesDir

    /**
     * Kamera vb. kaynaktan gelen Bitmap'i işleyip yerel depolamaya kaydeder.
     * @param isSolution true ise "cozum_", false ise "soru_" önekiyle kaydedilir.
     * @return Kaydedilen dosyanın mutlak yolu (absolute path)
     */
    suspend fun kaydetBitmap(bitmap: Bitmap, isSolution: Boolean): String = withContext(Dispatchers.IO) {
        val prefix = if (isSolution) "cozum" else "soru"
        val fileName = "${prefix}_${UUID.randomUUID()}.jpg"
        val targetFile = File(imagesDir, fileName)

        val scaledBitmap = scaleBitmapIfNeeded(bitmap, MAX_DIMENSION)
        FileOutputStream(targetFile).use { out ->
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.flush()
        }

        if (scaledBitmap != bitmap) {
            scaledBitmap.recycle()
        }

        targetFile.absolutePath
    }

    /**
     * URI üzerinden görsel kaydetme (uyumluluk köprüsü).
     */
    suspend fun saveImageFromUri(uri: Uri, isSolution: Boolean): String {
        return if (isSolution) kaydetCozumGorseli(uri) else kaydetSoruGorseli(uri)
    }

    /**
     * Bitmap kaydetme (uyumluluk köprüsü).
     */
    suspend fun saveBitmap(bitmap: Bitmap, isSolution: Boolean): String {
        return kaydetBitmap(bitmap, isSolution)
    }

    /**
     * Belirtilen dosya yolundaki görseli siler.
     */
    suspend fun gorselSil(path: String?): Boolean = withContext(Dispatchers.IO) {
        if (path.isNullOrBlank()) return@withContext false
        try {
            val file = File(path)
            if (file.exists()) {
                file.delete()
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun deleteImage(path: String?): Boolean = gorselSil(path)

    /**
     * `images/` dizinindeki tüm görselleri siler.
     */
    suspend fun tumGorselleriSil(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (imagesDir.exists()) {
                val files = imagesDir.listFiles()
                files?.forEach { it.delete() }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun clearAllImages(): Boolean = tumGorselleriSil()

    /**
     * Görsel dosyasının bayt dizisini okur (AI Vision sorguları için).
     */
    suspend fun getGorselBytes(path: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            if (file.exists() && file.isFile) {
                file.readBytes()
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Görsel dosyasını döner.
     */
    fun getGorselFile(path: String): File? {
        val file = File(path)
        return if (file.exists() && file.isFile) file else null
    }

    private fun saveUriToFile(sourceUri: Uri, fileName: String): String {
        val targetFile = File(imagesDir, fileName)

        // 1. EXIF rotasyonunu oku
        val rotationDegrees = getRotationDegrees(sourceUri)

        // 2. Boyutları öğren ve decode et
        val originalBitmap = decodeSampledBitmapFromUri(sourceUri)
            ?: throw IllegalArgumentException("Görsel yüklenemedi: $sourceUri")

        // 3. EXIF rotasyonunu uygula
        val rotatedBitmap = if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            val transformed = Bitmap.createBitmap(
                originalBitmap,
                0,
                0,
                originalBitmap.width,
                originalBitmap.height,
                matrix,
                true
            )
            if (transformed != originalBitmap) {
                originalBitmap.recycle()
            }
            transformed
        } else {
            originalBitmap
        }

        // 4. Max boyuta ölçekle
        val finalBitmap = scaleBitmapIfNeeded(rotatedBitmap, MAX_DIMENSION)

        // 5. JPEG olarak kaydet
        FileOutputStream(targetFile).use { out ->
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.flush()
        }

        if (finalBitmap != rotatedBitmap) {
            finalBitmap.recycle()
        }
        if (rotatedBitmap != originalBitmap) {
            rotatedBitmap.recycle()
        }

        return targetFile.absolutePath
    }

    private fun getRotationDegrees(uri: Uri): Int {
        var inputStream: InputStream? = null
        return try {
            inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream != null) {
                val exif = ExifInterface(inputStream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } else 0
        } catch (e: Exception) {
            0
        } finally {
            inputStream?.close()
        }
    }

    private fun decodeSampledBitmapFromUri(uri: Uri): Bitmap? {
        // Önce boyutları oku
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream, null, options)
        }

        val originalWidth = options.outWidth
        val originalHeight = options.outHeight
        if (originalWidth <= 0 || originalHeight <= 0) return null

        // inSampleSize hesapla
        var inSampleSize = 1
        val maxDim = max(originalWidth, originalHeight)
        while ((maxDim / inSampleSize) > MAX_DIMENSION * 2) {
            inSampleSize *= 2
        }

        // Asıl bitmap'i decode et
        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        return context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream, null, decodeOptions)
        }
    }

    private fun scaleBitmapIfNeeded(bitmap: Bitmap, maxDim: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val currentMax = max(width, height)

        if (currentMax <= maxDim) {
            return bitmap
        }

        val ratio = maxDim.toFloat() / currentMax.toFloat()
        val newWidth = (width * ratio).roundToInt()
        val newHeight = (height * ratio).roundToInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}
