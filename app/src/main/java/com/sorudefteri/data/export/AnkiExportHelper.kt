package com.sorudefteri.data.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.sorudefteri.domain.DersTaksonomisi
import com.sorudefteri.domain.model.Soru
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Anki & Flashcard TSV/CSV ve APKG Deste Dışa Aktarım Yardımcısı (Faz 5.3).
 *
 * Anki kart yapısı:
 * - Ön Yüz (Front): Sınav türü, ders, konu başlığı, soru metni/OCR, soru görseli (<img src="...">)
 * - Arka Yüz (Back): Doğru cevap, yapay zeka çözümü, öğrenci notu, zorluk derecesi, hata tipi
 * - Etiketler (Tags): Sınav türü, ders, Leitner tekrar aşaması (LeitnerStage_X), hata kategorisi
 */
object AnkiExportHelper {

    private const val BUFFER_SIZE = 32 * 1024

    /**
     * Verilen soruları Anki uyumlu TSV/CSV metin dosyasına aktarır.
     * Anki'nin doğrudan içe aktarabilmesi için `#separator:tab`, `#html:true` ve `#tags column:3`
     * direktiflerini içerir.
     *
     * @param context Android Context
     * @param sorular Dışa aktarılacak sorular
     * @return Oluşturulan dosya
     */
    fun exportQuestionsToAnki(context: Context, sorular: List<Soru>): File {
        val sharedDir = File(context.cacheDir, "shared").apply { if (!exists()) mkdirs() }
        val exportFile = File(sharedDir, "anki_deste_${System.currentTimeMillis()}.csv")

        val deckContent = buildAnkiDeckContent(sorular)
        FileOutputStream(exportFile).use { fos ->
            OutputStreamWriter(fos, StandardCharsets.UTF_8).use { writer ->
                writer.write(deckContent)
                writer.flush()
            }
        }
        return exportFile
    }

    /**
     * Verilen soruları ve bunlara ait görselleri tek bir ZIP paketi (.zip / anki flashcard pack)
     * halinde dışa aktarır. Arşiv içerisinde Anki TSV dosyası ('deck.tsv') ve tüm soru görselleri yer alır.
     *
     * @param context Android Context
     * @param sorular Dışa aktarılacak sorular
     * @return Oluşturulan ZIP dosyası
     */
    fun exportQuestionsToAnkiPackage(context: Context, sorular: List<Soru>): File {
        val sharedDir = File(context.cacheDir, "shared").apply { if (!exists()) mkdirs() }
        val packageFile = File(sharedDir, "anki_destesi_paketi_${System.currentTimeMillis()}.zip")

        FileOutputStream(packageFile).use { fos ->
            ZipOutputStream(BufferedOutputStream(fos)).use { zipOut ->
                val buffer = ByteArray(BUFFER_SIZE)

                // 1. Görselleri ZIP kök dizinine ekle (Anki media import kuralı)
                for ((index, soru) in sorular.withIndex()) {
                    val imageFile = File(soru.imagePath)
                    if (imageFile.exists() && imageFile.isFile) {
                        val imageName = getAnkiImageFileName(soru, index)
                        zipOut.putNextEntry(ZipEntry(imageName))
                        FileInputStream(imageFile).use { fis ->
                            var read: Int
                            while (fis.read(buffer).also { read = it } > 0) {
                                zipOut.write(buffer, 0, read)
                            }
                        }
                        zipOut.closeEntry()
                    }
                }

                // 2. Anki TSV dosyasını ZIP içine ekle
                val tsvContent = buildAnkiDeckContent(sorular)
                zipOut.putNextEntry(ZipEntry("deck.tsv"))
                val writer = OutputStreamWriter(zipOut, StandardCharsets.UTF_8)
                writer.write(tsvContent)
                writer.flush()
                zipOut.closeEntry()
            }
        }
        return packageFile
    }

    /**
     * Dışa aktarılan deste dosyasını Android Share Intent (FileProvider) ile paylaşır.
     *
     * @param context Android Context
     * @param file Paylaşılacak dosya
     */
    fun shareExportedDeck(context: Context, file: File) {
        if (!file.exists()) return

        val contentUri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val mimeType = when {
            file.name.endsWith(".zip", ignoreCase = true) -> "application/zip"
            file.name.endsWith(".tsv", ignoreCase = true) -> "text/tab-separated-values"
            else -> "text/csv"
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, "Soru Defterim Anki Destesi")
            putExtra(
                Intent.EXTRA_TEXT,
                "Soru Defterim'den dışa aktarılan ${file.name} Anki destesi. Anki uygulamasından 'İçe Aktar' seçeneğiyle ekleyebilirsiniz."
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(shareIntent, "Anki Deste Dışa Aktar (.csv)")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    /**
     * Verilen sorular listesinden Anki TSV/CSV metin içeriğini oluşturur.
     */
    fun buildAnkiDeckContent(sorular: List<Soru>): String {
        val sb = StringBuilder()
        // Anki TSV Direktifleri
        sb.append("#separator:tab\n")
        sb.append("#html:true\n")
        sb.append("#tags column:3\n")

        for ((index, soru) in sorular.withIndex()) {
            val imageRefName = if (soru.imagePath.isNotBlank()) getAnkiImageFileName(soru, index) else null
            val front = sanitizeTsvField(buildFrontHtml(soru, imageRefName))
            val back = sanitizeTsvField(buildBackHtml(soru))
            val tags = sanitizeTsvField(buildTagsString(soru))

            sb.append(front)
                .append("\t")
                .append(back)
                .append("\t")
                .append(tags)
                .append("\n")
        }
        return sb.toString()
    }

    /**
     * Kart Ön Yüzü (Front):
     * Ders, Konu, Soru Metni veya OCR, Görsel etiketi
     */
    fun buildFrontHtml(soru: Soru, imageRefName: String?): String {
        val dersAdi = DersTaksonomisi.getDersAdi(soru.examType, soru.subjectId)
        val sb = StringBuilder()

        sb.append("<div style=\"font-family: sans-serif; font-size: 15px; color: #1e1e1e;\">")
        sb.append("<div style=\"font-weight: bold; color: #1565c0; font-size: 16px; margin-bottom: 8px; border-bottom: 1px solid #e0e0e0; padding-bottom: 4px;\">")
        sb.append("[${soru.examType} - ${escapeHtml(dersAdi)}] ${escapeHtml(soru.topic)}")
        sb.append("</div>")

        val questionContent = when {
            !soru.questionText.isNullOrBlank() -> soru.questionText
            !soru.ocrMetni.isNullOrBlank() -> soru.ocrMetni
            else -> null
        }

        if (!questionContent.isNullOrBlank()) {
            sb.append("<div style=\"margin: 10px 0; line-height: 1.5;\">")
            sb.append(escapeHtml(questionContent).replace("\n", "<br/>"))
            sb.append("</div>")
        }

        if (!imageRefName.isNullOrBlank()) {
            sb.append("<div style=\"margin-top: 12px; text-align: center;\">")
            sb.append("<img src=\"${imageRefName}\" style=\"max-width: 100%; height: auto; border-radius: 8px; border: 1px solid #ddd;\"/>")
            sb.append("</div>")
        }

        sb.append("</div>")
        return sb.toString()
    }

    /**
     * Kart Arka Yüzü (Back):
     * Doğru Cevap, AI Çözümü, Öğrenci Notu, Zorluk ve Hata Tipi
     */
    fun buildBackHtml(soru: Soru): String {
        val sb = StringBuilder()
        sb.append("<div style=\"font-family: sans-serif; font-size: 15px; color: #1e1e1e;\">")

        // Doğru Cevap
        val answer = soru.correctAnswer?.trim()?.ifBlank { "Belirtilmemiş" } ?: "Belirtilmemiş"
        sb.append("<div style=\"font-size: 18px; font-weight: bold; color: #2e7d32; margin-bottom: 10px;\">")
        sb.append("🎯 Doğru Cevap: ${escapeHtml(answer)}")
        sb.append("</div>")

        if (!soru.studentAnswer.isNullOrBlank()) {
            sb.append("<div style=\"font-size: 14px; color: #d32f2f; margin-bottom: 8px;\">")
            sb.append("❌ Verilen Cevap: ${escapeHtml(soru.studentAnswer!!)}")
            sb.append("</div>")
        }

        // AI Çözüm ve Açıklama
        if (!soru.aiExplanation.isNullOrBlank()) {
            sb.append("<hr style=\"border: none; border-top: 1px dashed #ccc; margin: 10px 0;\"/>")
            sb.append("<div style=\"background-color: #f8f9fa; padding: 10px; border-radius: 6px; border-left: 4px solid #1976d2;\">")
            sb.append("<b style=\"color: #1976d2;\">💡 Yapay Zeka Çözümü:</b><br/>")
            sb.append("<div style=\"margin-top: 6px; line-height: 1.5;\">")
            sb.append(escapeHtml(soru.aiExplanation!!).replace("\n", "<br/>"))
            sb.append("</div></div>")
        }

        // Öğrenci Notu
        if (!soru.note.isNullOrBlank()) {
            sb.append("<div style=\"margin-top: 10px; padding: 8px; background-color: #fffde7; border-radius: 6px; border-left: 4px solid #fbc02d;\">")
            sb.append("<b>📝 Kişisel Not:</b> ${escapeHtml(soru.note!!).replace("\n", "<br/>")}")
            sb.append("</div>")
        }

        // Zorluk ve Hata Tipi Bilgisi
        val zorluk = soru.difficulty?.ifBlank { "Orta" } ?: "Orta"
        val hataTipiAdi = soru.mistakeType?.displayName ?: "Genel"
        sb.append("<div style=\"margin-top: 12px; font-size: 12px; color: #757575;\">")
        sb.append("📊 Zorluk: <b>${escapeHtml(zorluk)}</b> &nbsp;•&nbsp; Hata Tipi: <b>${escapeHtml(hataTipiAdi)}</b>")
        sb.append("</div>")

        sb.append("</div>")
        return sb.toString()
    }

    /**
     * Anki Etiketleri (Tags):
     * Boşlukla ayrılmış, Türkçe karakter uyumlu etiket dizesi
     */
    fun buildTagsString(soru: Soru): String {
        val dersAdi = DersTaksonomisi.getDersAdi(soru.examType, soru.subjectId)
        val tags = mutableListOf<String>()

        // 1. Sınav Türü (TYT / AYT)
        tags.add(sanitizeTag(soru.examType))

        // 2. Ders
        if (dersAdi.isNotBlank()) {
            tags.add(sanitizeTag(dersAdi))
        }

        // 3. Konu
        if (soru.topic.isNotBlank()) {
            tags.add(sanitizeTag(soru.topic))
        }

        // 4. Leitner Tekrar Kademesi
        tags.add("LeitnerStage_${soru.reviewStage}")

        // 5. Hata Tipi
        soru.mistakeType?.let {
            tags.add("Hata_${sanitizeTag(it.name)}")
        }

        return tags.filter { it.isNotBlank() }.joinToString(" ")
    }

    fun getAnkiImageFileName(soru: Soru, fallbackIndex: Int): String {
        val baseId = if (soru.id != 0L) soru.id else (fallbackIndex + 1).toLong()
        val originalExt = File(soru.imagePath).extension.ifBlank { "jpg" }
        return "soru_${baseId}.${originalExt}"
    }

    private fun sanitizeTag(input: String): String {
        return input.trim()
            .replace("\\s+".toRegex(), "_")
            .replace("[^a-zA-Z0-9_çğıöşüÇĞİÖŞÜ-]".toRegex(), "")
    }

    private fun sanitizeTsvField(content: String): String {
        // TSV içinde tab (\t) veya ham satır sonu (\r\n, \n) ayrıştırıcıyı bozmamalı
        return content
            .replace("\t", " ")
            .replace("\r\n", "<br/>")
            .replace("\n", "<br/>")
            .replace("\r", "")
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}
