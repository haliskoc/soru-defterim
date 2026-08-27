package com.sorudefteri.data.images

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
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
        private const val MAX_BYTES_BEFORE_COMPRESS = 5 * 1024 * 1024L // 5 MB
    }

    /**
     * Galeri veya dosya URI'sinden soru görselini işleyip yerel depolamaya kaydeder.
     * @return Kaydedilen dosyanın mutlak yolu (absolute path)
     */
    suspend fun kaydetSoruGorseli(sourceUri: android.net.Uri): String = withContext(Dispatchers.IO) {
        val fileName = "soru_${UUID.randomUUID()}.jpg"
        saveUriToFile(sourceUri, fileName)
    }

    /**
     * Galeri veya dosya URI'sinden çözüm görselini işleyip yerel depolamaya kaydeder.
     * @return Kaydedilen dosyanın mutlak yolu (absolute path)
     */
    suspend fun kaydetCozumGorseli(sourceUri: android.net.Uri): String = withContext(Dispatchers.IO) {
        val fileName = "cozum_${UUID.randomUUID()}.jpg"
        saveUriToFile(sourceUri, fileName)
    }

    /**
     * Kalıcı depolama dizini (filesDir/images) dışındaki bir dosyayı (örn: cacheDir'den gelen kamera çekimi)
     * kalıcı klasöre kopyalar ve sıkıştırır. Geçici dosya cache'te ise temizler.
     * @return Kalıcı dosyanın mutlak yolu (absolute path)
     */
    suspend fun kaydetDosyadan(sourceFile: File, isSolution: Boolean): String = withContext(Dispatchers.IO) {
        // canonical check: source zaten imagesDir içindeyse doğrudan dön
        try {
            if (sourceFile.exists() && sourceFile.canonicalPath.startsWith(imagesDir.canonicalPath + File.separator)) {
                return@withContext sourceFile.absolutePath
            }
        } catch (_: Exception) {
            if (sourceFile.parentFile?.absolutePath == imagesDir.absolutePath && sourceFile.exists()) {
                return@withContext sourceFile.absolutePath
            }
        }
        val uri = android.net.Uri.fromFile(sourceFile)
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
        try {
            FileOutputStream(targetFile).use { out ->
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                out.flush()
            }
        } finally {
            if (scaledBitmap != bitmap && !scaledBitmap.isRecycled) {
                scaledBitmap.recycle()
            }
        }

        targetFile.absolutePath
    }

    /**
     * URI üzerinden görsel kaydetme (uyumluluk köprüsü).
     */
    suspend fun saveImageFromUri(uri: android.net.Uri, isSolution: Boolean): String {
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
            if (!file.exists() || !file.isFile) return@withContext false
            // canonical check: sadece imagesDir içindeki dosyaları sil (zip-slip / path traversal engeli)
            val canonicalImages = try { imagesDir.canonicalPath } catch (_: Exception) { imagesDir.absolutePath }
            val canonicalFile = try { file.canonicalPath } catch (_: Exception) { file.absolutePath }
            if (!canonicalFile.startsWith(canonicalImages + File.separator) && canonicalFile != canonicalImages) {
                // Güvenlik: imagesDir dışı path istenirse engelle
                return@withContext false
            }
            file.delete()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } catch (e: OutOfMemoryError) {
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
                // deleteRecursively tüm alt dosyaları ve dizinleri siler; ardından dizini yeniden oluştur
                imagesDir.deleteRecursively()
                imagesDir.mkdirs()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            false
        }
    }

    suspend fun clearAllImages(): Boolean = tumGorselleriSil()

    /**
     * Görsel dosyasının bayt dizisini okur (AI Vision sorguları için).
     * 5 MB üzeri dosyalar OOM engellemek için sampled decode + JPEG compress ile küçültülür.
     */
    suspend fun getGorselBytes(path: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            if (!file.exists() || !file.isFile) return@withContext null

            // OOM öncesi kontrol: 5 MB üzeri ise stream yerine sıkıştırılmış bitmap döndür
            if (file.length() > MAX_BYTES_BEFORE_COMPRESS) {
                try {
                    // Önce boyutları öğren
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.absolutePath, bounds)
                    if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                        var inSampleSize = 1
                        // MAX_DIMENSION'a göre power-of-2 sampling
                        while ((bounds.outWidth / inSampleSize) > MAX_DIMENSION || (bounds.outHeight / inSampleSize) > MAX_DIMENSION) {
                            inSampleSize *= 2
                        }
                        // Dosya çok büyükse ek sampling (5 MB oranına göre)
                        val ratio = file.length().toFloat() / MAX_BYTES_BEFORE_COMPRESS
                        if (ratio > 2f) inSampleSize *= 2
                        if (ratio > 4f) inSampleSize *= 2

                        val decodeOpts = BitmapFactory.Options().apply {
                            this.inSampleSize = inSampleSize
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                        }
                        var bmp: Bitmap? = null
                        var scaled: Bitmap? = null
                        try {
                            bmp = BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
                            if (bmp != null) {
                                scaled = scaleBitmapIfNeeded(bmp, MAX_DIMENSION)
                                val baos = ByteArrayOutputStream()
                                scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, baos)
                                val bytes = baos.toByteArray()
                                return@withContext bytes
                            }
                        } catch (oom: OutOfMemoryError) {
                            oom.printStackTrace()
                            // fallback: daha büyük sample ile tekrar dene
                            try {
                                decodeOpts.inSampleSize = inSampleSize * 2
                                bmp = BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
                                if (bmp != null) {
                                    scaled = scaleBitmapIfNeeded(bmp, MAX_DIMENSION)
                                    val baos = ByteArrayOutputStream()
                                    scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, baos)
                                    val bytes = baos.toByteArray()
                                    return@withContext bytes
                                }
                            } catch (_: OutOfMemoryError) {
                                return@withContext null
                            }
                        } finally {
                            if (scaled != null && scaled != bmp && !scaled.isRecycled) scaled.recycle()
                            if (bmp != null && !bmp.isRecycled) {
                                // scaled == bmp ise yukarıda recycle edilmedi, şimdi recycle etme? scaled bmp ise bmp'yi recycle etme çünkü scaled aynı referans
                                if (scaled !== bmp) bmp.recycle()
                                else if (scaled == null) bmp.recycle()
                            }
                        }
                    }
                } catch (e: OutOfMemoryError) {
                    e.printStackTrace()
                    return@withContext null
                } catch (e: Exception) {
                    e.printStackTrace()
                    // fallthrough to stream read
                }
                // fallback: stream ile oku (dosya hala büyükse OOM olabilir, ama elimizden geleni yaptık)
            }

            // Küçük dosyalar veya compress fallback: stream ile oku (readBytes yerine buffered stream)
            try {
                file.inputStream().use { input -> input.readBytes() }
            } catch (oom: OutOfMemoryError) {
                oom.printStackTrace()
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } catch (oom: OutOfMemoryError) {
            oom.printStackTrace()
            null
        }
    }

    /**
     * Görsel dosyasını döner (canonical path kontrolü ile).
     */
    fun getGorselFile(path: String): File? {
        try {
            val file = File(path)
            if (!file.exists() || !file.isFile) return null
            val canonicalImages = try { imagesDir.canonicalPath } catch (_: Exception) { imagesDir.absolutePath }
            val canonicalFile = try { file.canonicalPath } catch (_: Exception) { file.absolutePath }
            // zip-slip benzeri imagesDir dışı path engelle
            if (!canonicalFile.startsWith(canonicalImages + File.separator) && canonicalFile != canonicalImages) {
                return null
            }
            return file
        } catch (_: Exception) {
            return null
        } catch (_: OutOfMemoryError) {
            return null
        }
    }

    private fun saveUriToFile(sourceUri: android.net.Uri, fileName: String): String {
        val targetFile = File(imagesDir, fileName)

        // 1. EXIF rotasyonunu oku (FileDescriptor üzerinden)
        val rotationDegrees = getRotationDegrees(sourceUri)

        // 2. Boyutları öğren ve decode et (FileDescriptor + OOM korumalı)
        val originalBitmap = decodeSampledBitmapFromUri(sourceUri)
            ?: throw IllegalArgumentException("Görsel yüklenemedi: $sourceUri")

        var rotatedBitmap: Bitmap = originalBitmap
        var finalBitmap: Bitmap = originalBitmap

        try {
            // 3. EXIF rotasyonunu uygula
            if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                rotatedBitmap = try {
                    Bitmap.createBitmap(
                        originalBitmap,
                        0,
                        0,
                        originalBitmap.width,
                        originalBitmap.height,
                        matrix,
                        true
                    )
                } catch (oom: OutOfMemoryError) {
                    oom.printStackTrace()
                    originalBitmap
                }
            }

            // 4. Max boyuta ölçekle
            finalBitmap = try {
                scaleBitmapIfNeeded(rotatedBitmap, MAX_DIMENSION)
            } catch (oom: OutOfMemoryError) {
                oom.printStackTrace()
                rotatedBitmap
            }

            // 5. JPEG olarak kaydet
            FileOutputStream(targetFile).use { out ->
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                out.flush()
            }

            return targetFile.absolutePath
        } finally {
            // Bellek Temizliği: Tüm bitmap referanslarını güvenli şekilde recycle et
            if (finalBitmap !== rotatedBitmap && finalBitmap !== originalBitmap && !finalBitmap.isRecycled) {
                try { finalBitmap.recycle() } catch (_: Exception) {}
            }
            if (rotatedBitmap !== originalBitmap && !rotatedBitmap.isRecycled) {
                try { rotatedBitmap.recycle() } catch (_: Exception) {}
            }
            if (!originalBitmap.isRecycled) {
                try { originalBitmap.recycle() } catch (_: Exception) {}
            }
        }
    }

    private fun getRotationDegrees(uri: android.net.Uri): Int {
        // Öncelikli: FileDescriptor üzerinden ExifInterface (tek handle, InputStream yerine)
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                try {
                    val exif = ExifInterface(pfd.fileDescriptor)
                    return when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270
                        else -> 0
                    }
                } catch (_: Exception) {
                    // fallback aşağıda
                } catch (_: OutOfMemoryError) {
                    return 0
                }
            }
        } catch (_: Exception) {
        } catch (_: OutOfMemoryError) {
            return 0
        }

        // Fallback: InputStream (ör. content provider fileDescriptor desteklemiyorsa)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val exif = ExifInterface(input)
                return when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            }
        } catch (_: Exception) {
        } catch (_: OutOfMemoryError) {
        }
        return 0
    }

    private fun decodeSampledBitmapFromUri(uri: android.net.Uri): Bitmap? {
        // FileDescriptor ile tek handle kullanımı (2 kez openInputStream yerine)
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val fd = pfd.fileDescriptor

                // 1) Sadece boyutları oku
                val boundsOptions = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFileDescriptor(fd, null, boundsOptions)

                val originalWidth = boundsOptions.outWidth
                val originalHeight = boundsOptions.outHeight
                if (originalWidth <= 0 || originalHeight <= 0) return null

                // 2) inSampleSize hesabı düzeltildi: her iki boyut da MAX_DIMENSION altında kalana kadar power-of-2 artış
                var inSampleSize = 1
                // power-of-2 olarak en küçük sample'ı bul
                while ((originalWidth / inSampleSize) > MAX_DIMENSION || (originalHeight / inSampleSize) > MAX_DIMENSION) {
                    inSampleSize *= 2
                }
                // Ek güvenlik: çok büyük görsellerde (örn. 8000px) sample'ı bir kademe daha artır
                // mevcut logic maxDim/(sample*2) >= MAX_DIMENSION ile uyumlu ama daha açık

                val decodeOptions = BitmapFactory.Options().apply {
                    this.inSampleSize = inSampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }

                // 3) Asıl bitmap'i decode et - OOM korumalı
                return try {
                    BitmapFactory.decodeFileDescriptor(fd, null, decodeOptions)
                } catch (oom: OutOfMemoryError) {
                    oom.printStackTrace()
                    // OOM'da sample'ı 2 katına çıkararak tekrar dene
                    decodeOptions.inSampleSize = inSampleSize * 2
                    try {
                        BitmapFactory.decodeFileDescriptor(fd, null, decodeOptions)
                    } catch (_: OutOfMemoryError) {
                        null
                    } catch (_: Exception) {
                        null
                    }
                } catch (_: Exception) {
                    null
                }
            }
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            return null
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Fallback: openInputStream yolu (FileDescriptor desteklenmeyen provider için)
        // Not: tek handle yerine 2 stream fallback, ama ana yol zaten FileDescriptor
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var inSampleSize = 1
            while ((bounds.outWidth / inSampleSize) > MAX_DIMENSION || (bounds.outHeight / inSampleSize) > MAX_DIMENSION) {
                inSampleSize *= 2
            }
            val opts = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input, null, opts)
                }
            } catch (oom: OutOfMemoryError) {
                oom.printStackTrace()
                opts.inSampleSize = inSampleSize * 2
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        BitmapFactory.decodeStream(input, null, opts)
                    }
                } catch (_: OutOfMemoryError) { null }
            }
        } catch (_: OutOfMemoryError) {
            null
        } catch (_: Exception) {
            null
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

        return try {
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        } catch (oom: OutOfMemoryError) {
            oom.printStackTrace()
            bitmap
        }
    }
}
