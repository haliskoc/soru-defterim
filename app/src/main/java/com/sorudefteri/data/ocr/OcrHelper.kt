package com.sorudefteri.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.FileNotFoundException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.math.max

/**
 * Google ML Kit Text Recognition kullanarak Türkçe ve Latin karakterli metinleri
 * görsellerden (Bitmap, Uri, File, ByteArray) asenkron olarak çıkaran servis.
 * Lifecycle: Application onTerminate veya ViewModel onCleared'da [close] çağrılmalı.
 */
@Singleton
class OcrHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    @Volatile
    private var _recognizer: TextRecognizer? = null

    @Volatile
    private var isClosed = false

    private fun getRecognizer(): TextRecognizer {
        synchronized(this) {
            if (_recognizer == null || isClosed) {
                _recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                isClosed = false
            }
            return _recognizer!!
        }
    }

    /**
     * Bitmap nesnesinden metin ayıklar. rotationDegrees EXIF'ten alınmalı (0 yerine).
     */
    suspend fun recognizeText(bitmap: Bitmap, rotationDegrees: Int = 0): Result<String> = withContext(Dispatchers.Default) {
        try {
            withTimeout(15000L) {
                suspendCancellableCoroutine { continuation ->
                    try {
                        val inputImage = InputImage.fromBitmap(bitmap, rotationDegrees)
                        val rec = getRecognizer()
                        rec.process(inputImage)
                            .addOnSuccessListener { visionText ->
                                val cleaned = cleanOcrArtifacts(visionText.text)
                                if (continuation.isActive) continuation.resume(Result.success(cleaned))
                            }
                            .addOnFailureListener { exception ->
                                if (continuation.isActive) continuation.resume(
                                    Result.failure(
                                        Exception("Görselden metin okunamadı: ${exception.localizedMessage}", exception)
                                    )
                                )
                            }
                            .addOnCanceledListener {
                                if (continuation.isActive) continuation.cancel()
                            }
                    } catch (e: Exception) {
                        if (continuation.isActive) continuation.resume(Result.failure(e))
                    } catch (oom: OutOfMemoryError) {
                        if (continuation.isActive) continuation.resume(Result.failure(Exception("OCR OOM: ${oom.message}", oom)))
                    }
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Result.failure(Exception("OCR zaman aşımına uğradı (15s). Lütfen daha küçük bir görseli deneyin.", e))
        } catch (e: Exception) {
            Result.failure(e)
        } catch (oom: OutOfMemoryError) {
            Result.failure(Exception("OCR OOM", oom))
        }
    }

    /**
     * Android Uri (content:// veya file://) üzerinden metin ayıklar.
     */
    suspend fun recognizeText(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            withTimeout(15000L) {
                suspendCancellableCoroutine { continuation ->
                    try {
                        val inputImage = InputImage.fromFilePath(context, uri)
                        getRecognizer().process(inputImage)
                            .addOnSuccessListener { visionText ->
                                val cleaned = cleanOcrArtifacts(visionText.text)
                                if (continuation.isActive) continuation.resume(Result.success(cleaned))
                            }
                            .addOnFailureListener { exception ->
                                if (continuation.isActive) continuation.resume(
                                    Result.failure(
                                        Exception("Uri üzerinden metin okunamadı: ${exception.localizedMessage}", exception)
                                    )
                                )
                            }
                            .addOnCanceledListener {
                                if (continuation.isActive) continuation.cancel()
                            }
                    } catch (e: Exception) {
                        if (continuation.isActive) continuation.resume(Result.failure(e))
                    } catch (oom: OutOfMemoryError) {
                        if (continuation.isActive) continuation.resume(Result.failure(Exception("OCR OOM", oom)))
                    }
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Result.failure(Exception("OCR zaman aşımına uğradı (15s).", e))
        } catch (e: Exception) {
            Result.failure(e)
        } catch (oom: OutOfMemoryError) {
            Result.failure(Exception("OCR OOM", oom))
        }
    }

    /**
     * Disk üzerindeki dosya yolundan metin ayıklar.
     */
    suspend fun recognizeText(filePath: String): Result<String> = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists() || !file.canRead()) {
            return@withContext Result.failure(
                FileNotFoundException("OCR için hedef dosya bulunamadı veya okunamıyor: $filePath")
            )
        }
        recognizeText(file)
    }

    /**
     * File nesnesinden metin ayıklar. Sampling + rotation düzeltmeli.
     */
    suspend fun recognizeText(file: File): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Sampling: büyük görsellerde OOM engellemek için inSampleSize ile decode
            val sampledBitmap = decodeSampledBitmapForOcr(file)
                ?: return@withContext Result.failure(
                    IllegalArgumentException("Görsel dosyası bitmap formatına dönüştürülemedi: ${file.name}")
                )
            // Rotation: 0 yerine EXIF rotasyonu kullan (GorselDepo rotasyonu)
            val rotation = getRotationDegreesForFile(file)
            try {
                recognizeText(sampledBitmap, rotation)
            } finally {
                if (!sampledBitmap.isRecycled) {
                    try { sampledBitmap.recycle() } catch (_: Exception) {}
                }
            }
        } catch (oom: OutOfMemoryError) {
            Result.failure(Exception("OCR OOM: Görsel çok büyük", oom))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Bellekteki byte dizisinden metin ayıklar. Sampling ekli.
     */
    suspend fun recognizeText(imageBytes: ByteArray): Result<String> = withContext(Dispatchers.Default) {
        if (imageBytes.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("OCR için görsel verisi boş."))
        }
        try {
            // Sampling: byte array çok büyükse sampling ile decode
            val bitmap = decodeSampledBitmapFromBytes(imageBytes)
                ?: return@withContext Result.failure(
                    IllegalArgumentException("Görsel byte dizisi çözümlenemedi.")
                )
            try {
                recognizeText(bitmap, 0)
            } finally {
                if (!bitmap.isRecycled) {
                    try { bitmap.recycle() } catch (_: Exception) {}
                }
            }
        } catch (oom: OutOfMemoryError) {
            Result.failure(Exception("OCR OOM", oom))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * OCR çıktısındaki yaygın boşluk ve satır sonu bozukluklarını yüzeysel temizler.
     */
    fun cleanOcrArtifacts(rawText: String): String {
        if (rawText.isBlank()) return ""

        return rawText
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .lines()
            .joinToString("\n") { it.trim() }
            .trim()
    }

    /**
     * Sampling ile bitmap decode (OCR için max 1800px)
     */
    private fun decodeSampledBitmapForOcr(file: File): Bitmap? {
        return try {
            val maxDim = 1800
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                // fallback: doğrudan decode
                return try { BitmapFactory.decodeFile(file.absolutePath) } catch (_: OutOfMemoryError) { null }
            }
            var inSampleSize = 1
            while ((bounds.outWidth / inSampleSize) > maxDim || (bounds.outHeight / inSampleSize) > maxDim) {
                inSampleSize *= 2
            }
            // Ek OOM koruması: dosya boyutu >5MB ise ek sample
            if (file.length() > 5 * 1024 * 1024) inSampleSize *= 2
            val opts = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            try {
                BitmapFactory.decodeFile(file.absolutePath, opts)
            } catch (oom: OutOfMemoryError) {
                oom.printStackTrace()
                opts.inSampleSize = inSampleSize * 2
                try { BitmapFactory.decodeFile(file.absolutePath, opts) } catch (_: OutOfMemoryError) { null }
            }
        } catch (_: OutOfMemoryError) { null } catch (_: Exception) { null }
    }

    private fun decodeSampledBitmapFromBytes(bytes: ByteArray): Bitmap? {
        return try {
            val maxDim = 1800
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return try { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) } catch (_: OutOfMemoryError) { null }
            }
            var inSampleSize = 1
            while ((bounds.outWidth / inSampleSize) > maxDim || (bounds.outHeight / inSampleSize) > maxDim) {
                inSampleSize *= 2
            }
            // 5MB üzeri byte array için ek sample
            if (bytes.size > 5 * 1024 * 1024) inSampleSize *= 2
            val opts = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            try {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            } catch (oom: OutOfMemoryError) {
                oom.printStackTrace()
                opts.inSampleSize = inSampleSize * 2
                try { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) } catch (_: OutOfMemoryError) { null }
            }
        } catch (_: OutOfMemoryError) { null } catch (_: Exception) { null }
    }

    private fun getRotationDegreesForFile(file: File): Int {
        return try {
            val exif = ExifInterface(file.absolutePath)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (_: Exception) { 0 } catch (_: OutOfMemoryError) { 0 }
    }

    private fun getRotationDegreesForUri(uri: Uri): Int {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val exif = ExifInterface(pfd.fileDescriptor)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (_: Exception) { 0 } catch (_: OutOfMemoryError) { 0 }
    }

    /**
     * Kaynakları serbest bırakır. ViewModel onCleared veya Application onTerminate'de çağrılmalı.
     */
    fun close() {
        synchronized(this) {
            if (isClosed) return
            try {
                _recognizer?.close()
            } catch (_: Exception) {
            } catch (_: OutOfMemoryError) {}
            _recognizer = null
            isClosed = true
        }
    }
}
