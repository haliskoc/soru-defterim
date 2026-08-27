package com.sorudefteri.ui.ortak

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Soruların, çözümlerin ve yedek paketlerinin WhatsApp, Telegram,
 * e-posta ve diğer uygulamalarla (yerel paylaşım) paylaşılmasını sağlayan yardımcı sınıf.
 */
object ShareHelper {

    /**
     * Soru görselini ve varsa AI çözümünü diğer uygulamalarla paylaşır.
     */
    fun shareQuestion(
        context: Context,
        imagePath: String,
        dersAdi: String,
        topic: String,
        correctAnswer: String? = null,
        note: String? = null,
        aiSolution: String? = null
    ) {
        try {
            val file = File(imagePath)
            if (!file.exists()) return

            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareTextBuilder = StringBuilder()
            shareTextBuilder.append("📚 *Soru Defterim - YKS Soru Paylaşımı*\n\n")
            shareTextBuilder.append("📌 *Ders:* $dersAdi\n")
            shareTextBuilder.append("📖 *Konu:* $topic\n")
            if (!correctAnswer.isNullOrBlank()) {
                shareTextBuilder.append("🎯 *Cevap:* $correctAnswer\n")
            }
            if (!note.isNullOrBlank()) {
                shareTextBuilder.append("📝 *Not:* $note\n")
            }

            if (!aiSolution.isNullOrBlank()) {
                shareTextBuilder.append("\n💡 *Yapay Zeka Çözümü:*\n$aiSolution\n")
            }

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_TEXT, shareTextBuilder.toString())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Soruyu Paylaş")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * .sdefteri yedek dosyasını doğrudan yerel paylaşım (Dosyalar, WhatsApp vb.) ile paylaşır - Drive senkron kaldırıldı.
     */
    fun shareBackupFile(context: Context, backupFile: File) {
        try {
            if (!backupFile.exists()) return

            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                backupFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, "Soru Defterim Yedek Paketi")
                putExtra(Intent.EXTRA_TEXT, "Soru Defterim arşiv yedek paketi (.sdefteri) - Yalnızca yerel. Uygulama içinden Ayarlar > İçe Aktar ile yükleyebilirsiniz.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Yedeği Paylaş (Yerel)")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
