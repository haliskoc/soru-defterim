package com.sorudefteri.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Google ML Kit Text Recognition kullanarak Türkçe ve Latin karakterli metinleri
 * görsellerden (Bitmap, Uri, File, ByteArray) asenkron olarak çıkaran servis.
 */
@Singleton
class OcrHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val recognizer: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Bitmap nesnesinden metin ayıklar.
     */
    suspend fun recognizeText(bitmap: Bitmap): Result<String> = withContext(Dispatchers.Default) {
        suspendCancellableCoroutine { continuation ->
            try {
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                recognizer.process(inputImage)
                    .addOnSuccessListener { visionText ->
                        val cleaned = cleanOcrArtifacts(visionText.text)
                        continuation.resume(Result.success(cleaned))
                    }
                    .addOnFailureListener { exception ->
                        continuation.resume(
                            Result.failure(
                                Exception("Görselden metin okunamadı: ${exception.localizedMessage}", exception)
                            )
                        )
                    }
                    .addOnCanceledListener {
                        continuation.cancel()
                    }
            } catch (e: Exception) {
                continuation.resume(Result.failure(e))
            }
        }
    }

    /**
     * Android Uri (content:// veya file://) üzerinden metin ayıklar.
     */
    suspend fun recognizeText(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            try {
                val inputImage = InputImage.fromFilePath(context, uri)
                recognizer.process(inputImage)
                    .addOnSuccessListener { visionText ->
                        val cleaned = cleanOcrArtifacts(visionText.text)
                        continuation.resume(Result.success(cleaned))
                    }
                    .addOnFailureListener { exception ->
                        continuation.resume(
                            Result.failure(
                                Exception("Uri üzerinden metin okunamadı: ${exception.localizedMessage}", exception)
                            )
                        )
                    }
                    .addOnCanceledListener {
                        continuation.cancel()
                    }
            } catch (e: Exception) {
                continuation.resume(Result.failure(e))
            }
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
     * File nesnesinden metin ayıklar.
     */
    suspend fun recognizeText(file: File): Result<String> = withContext(Dispatchers.IO) {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            ?: return@withContext Result.failure(
                IllegalArgumentException("Görsel dosyası bitmap formatına dönüştürülemedi: ${file.name}")
            )
        recognizeText(bitmap)
    }

    /**
     * Bellekteki byte dizisinden metin ayıklar.
     */
    suspend fun recognizeText(imageBytes: ByteArray): Result<String> = withContext(Dispatchers.Default) {
        if (imageBytes.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("OCR için görsel verisi boş."))
        }
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            ?: return@withContext Result.failure(
                IllegalArgumentException("Görsel byte dizisi çözümlenemedi.")
            )
        recognizeText(bitmap)
    }

    /**
     * OCR çıktısındaki yaygın boşluk ve satır sonu bozukluklarını yüzeysel temizler.
     */
    fun cleanOcrArtifacts(rawText: String): String {
        if (rawText.isBlank()) return ""

        return rawText
            // Satır içi birden fazla boşlukları tek boşluğa indir
            .replace(Regex("[ \\t]+"), " ")
            // 3 veya daha fazla ardışık satır sonlarını 2 satır sonuna indir
            .replace(Regex("\\n{3,}"), "\n\n")
            .lines()
            .joinToString("\n") { it.trim() }
            .trim()
    }

    /**
     * Kaynakları serbest bırakır.
     */
    fun close() {
        try {
            recognizer.close()
        } catch (_: Exception) {
        }
    }
}
