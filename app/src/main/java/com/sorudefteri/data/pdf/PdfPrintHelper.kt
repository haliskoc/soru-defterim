package com.sorudefteri.data.pdf

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Android PrintManager üzerinden Wi-Fi / Yerel yazıcılara doğrudan yazdırma,
 * PDF dosyasını İndirilenler klasörüne kaydetme ve paylaşma işlemlerini yönetir.
 */
object PdfPrintHelper {

    private fun showToastSafely(context: Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
        try {
            Toast.makeText(context, message, duration).show()
        } catch (_: Exception) {}
    }

    /**
     * PDF dosyasını sistemin yerel yazdırma yöneticisine (PrintManager) gönderir.
     * Kullanıcı Wi-Fi yazıcısından basabilir veya Android'in dahili "PDF Olarak Kaydet" seçeneğini kullanabilir.
     */
    fun printPdf(context: Context, pdfFile: File, jobName: String = "Soru Defterim Belge"): Boolean {
        return try {
            if (!pdfFile.exists()) {
                showToastSafely(context, "Yazdırılacak PDF dosyası bulunamadı.")
                return false
            }

            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            if (printManager == null) {
                showToastSafely(context, "Cihazda yazdırma servisi bulunamadı.")
                return false
            }

            val adapter = object : PrintDocumentAdapter() {
                override fun onLayout(
                    oldAttributes: PrintAttributes?,
                    newAttributes: PrintAttributes?,
                    cancellationSignal: CancellationSignal?,
                    callback: LayoutResultCallback?,
                    extras: Bundle?
                ) {
                    if (cancellationSignal?.isCanceled == true) {
                        callback?.onLayoutCancelled()
                        return
                    }

                    val info = PrintDocumentInfo.Builder("$jobName.pdf")
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                        .build()

                    callback?.onLayoutFinished(info, newAttributes != oldAttributes)
                }

                override fun onWrite(
                    pages: Array<out PageRange>?,
                    destination: ParcelFileDescriptor?,
                    cancellationSignal: CancellationSignal?,
                    callback: WriteResultCallback?
                ) {
                    if (destination == null) {
                        callback?.onWriteFailed("Hedef dosya tanımlayıcısı null.")
                        return
                    }

                    try {
                        FileInputStream(pdfFile).use { input ->
                            FileOutputStream(destination.fileDescriptor).use { output ->
                                val buffer = ByteArray(8192)
                                var bytesRead: Int
                                while (input.read(buffer).also { bytesRead = it } >= 0) {
                                    if (cancellationSignal?.isCanceled == true) {
                                        callback?.onWriteCancelled()
                                        return
                                    }
                                    output.write(buffer, 0, bytesRead)
                                }
                            }
                        }
                        callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    } catch (e: Exception) {
                        e.printStackTrace()
                        callback?.onWriteFailed(e.localizedMessage ?: "Yazdırma yazma hatası")
                    }
                }
            }

            val printAttributes = PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                .build()

            printManager.print(jobName, adapter, printAttributes)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Yazdırma başlatılamadı: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            false
        }
    }

    /**
     * Oluşturulan PDF dosyasını cihazın genel "Download" (İndirilenler) klasörüne kopyalar.
     */
    fun savePdfToDownloads(
        context: Context,
        pdfFile: File,
        customFileName: String? = null
    ): File? {
        return try {
            if (!pdfFile.exists()) return null

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val safeName = (customFileName?.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                ?: "SoruDefterim_Fasikul") + "_$timestamp.pdf"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("MediaStore kaydı oluşturulamadı.")
                resolver.openOutputStream(uri)?.use { output ->
                    FileInputStream(pdfFile).use { input ->
                        input.copyTo(output)
                    }
                } ?: throw IllegalStateException("Çıktı akışı açılamadı.")

                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)

                showToastSafely(
                    context,
                    "Belge İndirilenler klasörüne kaydedildi:\n$safeName",
                    Toast.LENGTH_LONG
                )
                pdfFile
            } else {
                @Suppress("DEPRECATION")
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()

                val targetFile = File(downloadsDir, safeName)
                pdfFile.copyTo(targetFile, overwrite = true)

                showToastSafely(
                    context,
                    "Belge İndirilenler klasörüne kaydedildi:\n${targetFile.name}",
                    Toast.LENGTH_LONG
                )
                targetFile
            }
        } catch (e: Exception) {
            e.printStackTrace()
            showToastSafely(context, "Kaydedilemedi: ${e.localizedMessage}", Toast.LENGTH_LONG)
            null
        }
    }

    /**
     * PDF dosyasını Android paylaşım menüsüyle (WhatsApp, Drive vb.) paylaşır.
     */
    fun sharePdf(context: Context, pdfFile: File, subject: String = "Soru Defterim Çalışma Fasikülü") {
        try {
            if (!pdfFile.exists()) {
                showToastSafely(context, "Paylaşılacak PDF dosyası bulunamadı.", Toast.LENGTH_SHORT)
                return
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, subject)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Fasikülü Paylaş").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            showToastSafely(context, "Paylaşılırken hata oluştu: ${e.localizedMessage}", Toast.LENGTH_LONG)
        }
    }
}
