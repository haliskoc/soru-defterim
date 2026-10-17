package com.sorudefteri.ui.ortak

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.sorudefteri.domain.DersTaksonomisi
import com.sorudefteri.domain.model.Soru
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/**
 * "Hocam Bakar Mısınız?" — Akıllı WhatsApp Soru Kartı Üreteci ve Paylaşımcısı
 *
 * Soru fotoğrafı, Ders/Konu, Öğrenci Cevabı, Doğru Cevap ve Öğrenci Notunu
 * yüksek çözünürlüklü tek bir şık görsel kartta birleştirir ve WhatsApp üzerinden paylaşır.
 */
object TeacherShareHelper {

    const val DEFAULT_WHATSAPP_GREETING =
        "Hocam merhaba, Soru Defterim uygulamasından bir soruyu sizinle paylaşıyorum. Vaktiniz olduğunda bakabilir misiniz? Teşekkürler!"

    private val WHATSAPP_PACKAGES = listOf(
        "com.whatsapp",
        "com.whatsapp.w4b"
    )

    /**
     * Paylaşım mesaj metnini oluşturur
     */
    fun buildShareMessage(
        greeting: String = DEFAULT_WHATSAPP_GREETING,
        dersAdi: String,
        topic: String,
        examType: String,
        studentAnswer: String? = null,
        correctAnswer: String? = null,
        note: String? = null
    ): String {
        val sb = StringBuilder()
        sb.append(greeting.trim()).append("\n\n")
        sb.append("📚 *Ders:* ").append(dersAdi).append(" (").append(examType).append(")\n")
        if (topic.isNotBlank()) {
            sb.append("📖 *Konu:* ").append(topic).append("\n")
        }
        if (!studentAnswer.isNullOrBlank()) {
            sb.append("🔴 *Benim Cevabım:* ").append(studentAnswer).append("\n")
        }
        if (!correctAnswer.isNullOrBlank()) {
            sb.append("🟢 *Doğru Cevap:* ").append(correctAnswer).append("\n")
        }
        if (!note.isNullOrBlank()) {
            sb.append("\n💬 *Sorum / Notum:* \"").append(note.trim()).append("\"\n")
        }
        sb.append("\n_Soru Defterim • YKS Hazırlık_")
        return sb.toString()
    }

    /**
     * Paylaşım Intent verilerini tutan saf veri sınıfı (JVM test edilebilir)
     */
    data class TeacherSharePayload(
        val action: String = Intent.ACTION_SEND,
        val type: String = "image/jpeg",
        val streamUri: Uri,
        val text: String,
        val targetPackage: String? = null,
        val flags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
    )

    fun buildSharePayload(
        imageUri: Uri,
        greetingText: String = DEFAULT_WHATSAPP_GREETING,
        targetPackage: String? = null
    ): TeacherSharePayload {
        return TeacherSharePayload(
            action = Intent.ACTION_SEND,
            type = "image/jpeg",
            streamUri = imageUri,
            text = greetingText,
            targetPackage = targetPackage,
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    }

    /**
     * Paylaşım Intent'i üretir
     */
    fun createTeacherShareIntent(
        imageUri: Uri,
        greetingText: String = DEFAULT_WHATSAPP_GREETING,
        targetPackage: String? = null
    ): Intent {
        val payload = buildSharePayload(imageUri, greetingText, targetPackage)
        val intent = Intent(payload.action).apply {
            type = payload.type
            putExtra(Intent.EXTRA_STREAM, payload.streamUri)
            putExtra(Intent.EXTRA_TEXT, payload.text)
            addFlags(payload.flags)
        }
        if (!payload.targetPackage.isNullOrBlank()) {
            intent.setPackage(payload.targetPackage)
        }
        return intent
    }

    /**
     * Cihazda WhatsApp veya WhatsApp Business yüklü mü kontrol eder
     */
    fun isWhatsAppInstalled(context: Context): Boolean {
        val pm = context.packageManager
        return WHATSAPP_PACKAGES.any { pkg ->
            try {
                pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES)
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * Yüklü olan ilk WhatsApp paket adını döner (veya null)
     */
    fun getInstalledWhatsAppPackage(context: Context): String? {
        val pm = context.packageManager
        return WHATSAPP_PACKAGES.firstOrNull { pkg ->
            try {
                pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES)
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * Kompozit Yüksek Çözünürlüklü Markalı Soru Kartı Görseli (Bitmap) Üretir
     */
    fun generateQuestionCardBitmap(
        context: Context,
        soru: Soru,
        studentAnswer: String? = null,
        customNote: String? = null
    ): Bitmap {
        val dersAdi = DersTaksonomisi.getDersAdi(soru.examType, soru.subjectId)
        val topic = soru.topic
        val examType = soru.examType
        val effectiveStudentAnswer = studentAnswer ?: soru.studentAnswer ?: "—"
        val effectiveCorrectAnswer = soru.correctAnswer ?: "—"
        val effectiveNote = if (!customNote.isNullOrBlank()) {
            customNote.trim()
        } else {
            soru.note?.trim() ?: "Hocam bu sorunun çözüm mantığında takıldım, yardımcı olabilir misiniz?"
        }

        // Kart Genişliği ve Dinamik Yükseklik Planlaması
        val cardWidth = 1080
        val padding = 48
        val contentWidth = cardWidth - (padding * 2)

        // Soru görselini yükle ve boyutlandır
        val originalQuestionBitmap: Bitmap? = try {
            val file = File(soru.imagePath)
            if (file.exists()) {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, opts)
                val inSample = max(1, opts.outWidth / contentWidth)
                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = inSample }
                BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
            } else null
        } catch (e: Exception) {
            null
        }

        val questionImageHeight = originalQuestionBitmap?.let { bmp ->
            val scale = contentWidth.toFloat() / bmp.width.toFloat()
            (bmp.height * scale).toInt().coerceAtMost(1200)
        } ?: 300

        // Statik Text Ölçümleri (Not kutusu için StaticLayout)
        val noteTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.rgb(30, 41, 59) // Slate 800
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        val noteLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(effectiveNote, 0, effectiveNote.length, noteTextPaint, contentWidth - 40)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.25f)
                .setIncludePad(false)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(effectiveNote, noteTextPaint, contentWidth - 40, Layout.Alignment.ALIGN_NORMAL, 1.25f, 0f, false)
        }

        // Toplam Yükseklik Hesabı
        val headerHeight = 180
        val imageContainerHeight = questionImageHeight + 32
        val answerRowHeight = 130
        val noteBoxHeight = noteLayout.height + 90
        val footerHeight = 100
        val totalHeight = padding + headerHeight + imageContainerHeight + answerRowHeight + noteBoxHeight + footerHeight + padding

        // Bitmap ve Canvas Oluştur
        val bitmap = Bitmap.createBitmap(cardWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Arka Plan (Temiz Açık Gri / Beyaz Gradyan Etkisi)
        val bgPaint = Paint().apply { color = AndroidColor.rgb(248, 250, 252) } // #F8FAFC
        canvas.drawRect(0f, 0f, cardWidth.toFloat(), totalHeight.toFloat(), bgPaint)

        // Beyaz İç Kart Gövdesi
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.WHITE
            setShadowLayer(16f, 0f, 8f, AndroidColor.argb(40, 0, 0, 0))
        }
        val cardRect = RectF(
            (padding / 2).toFloat(),
            (padding / 2).toFloat(),
            (cardWidth - padding / 2).toFloat(),
            (totalHeight - padding / 2).toFloat()
        )
        canvas.drawRoundRect(cardRect, 32f, 32f, cardPaint)

        var currentY = padding.toFloat() + 20f

        // 2. ÜST BAŞLIK (Branding + Ders/Konu Etiketi)
        val brandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.rgb(37, 99, 235) // Primary Blue
            textSize = 38f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("Soru Defterim • Hocama Sor", padding.toFloat(), currentY + 32f, brandPaint)

        val datePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.rgb(148, 163, 184)
            textSize = 28f
        }
        val dateStr = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())
        val dateWidth = datePaint.measureText(dateStr)
        canvas.drawText(dateStr, (cardWidth - padding).toFloat() - dateWidth, currentY + 32f, datePaint)

        currentY += 60f

        // Ders / Konu / Sınav Türü Başlığı
        val tagBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.rgb(238, 242, 255) // Soft Indigo Container
        }
        val tagRect = RectF(padding.toFloat(), currentY, (cardWidth - padding).toFloat(), currentY + 70f)
        canvas.drawRoundRect(tagRect, 16f, 16f, tagBgPaint)

        val tagTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.rgb(67, 56, 202)
            textSize = 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val topicDisplay = if (topic.isNotBlank()) " • $topic" else ""
        val tagTitle = "[$examType] $dersAdi$topicDisplay"
        val truncatedTagTitle = if (tagTextPaint.measureText(tagTitle) > contentWidth - 32) {
            tagTitle.take(45) + "..."
        } else tagTitle
        canvas.drawText(truncatedTagTitle, (padding + 20).toFloat(), currentY + 46f, tagTextPaint)

        currentY += 90f

        // 3. SORU GÖRSELİ (Framed Container)
        val imgBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.rgb(226, 232, 240)
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        val imgContainerRect = RectF(
            padding.toFloat(),
            currentY,
            (cardWidth - padding).toFloat(),
            currentY + questionImageHeight.toFloat()
        )
        canvas.drawRoundRect(imgContainerRect, 20f, 20f, imgBorderPaint)

        if (originalQuestionBitmap != null) {
            val srcRect = Rect(0, 0, originalQuestionBitmap.width, originalQuestionBitmap.height)
            val dstRect = RectF(
                imgContainerRect.left + 4f,
                imgContainerRect.top + 4f,
                imgContainerRect.right - 4f,
                imgContainerRect.bottom - 4f
            )
            canvas.drawBitmap(originalQuestionBitmap, srcRect, dstRect, Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            val placeholderPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.rgb(100, 116, 139)
                textSize = 32f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("Soru Görseli", (cardWidth / 2).toFloat(), currentY + (questionImageHeight / 2f), placeholderPaint)
        }

        currentY += questionImageHeight + 30f

        // 4. CEVAP ROZETLERİ (Benim Cevabım & Doğru Cevap Yan Yana)
        val pillWidth = (contentWidth - 24) / 2f
        val pillHeight = 85f

        // 4.1. Benim Cevabım (Kırmızı/Turuncu Vurgulu)
        val studentPillBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.rgb(254, 226, 226) // Soft Red Container
        }
        val studentPillBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.rgb(239, 68, 68) // Red
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }
        val studentPillRect = RectF(padding.toFloat(), currentY, padding.toFloat() + pillWidth, currentY + pillHeight)
        canvas.drawRoundRect(studentPillRect, 18f, 18f, studentPillBg)
        canvas.drawRoundRect(studentPillRect, 18f, 18f, studentPillBorder)

        val studentTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.rgb(185, 28, 28)
            textSize = 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Benim Cevabım: $effectiveStudentAnswer", studentPillRect.centerX(), studentPillRect.centerY() + 10f, studentTextPaint)

        // 4.2. Doğru Cevap (Yeşil Vurgulu)
        val correctPillBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.rgb(220, 252, 231) // Soft Green Container
        }
        val correctPillBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.rgb(34, 197, 94) // Green
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }
        val correctPillRect = RectF(padding.toFloat() + pillWidth + 24f, currentY, (cardWidth - padding).toFloat(), currentY + pillHeight)
        canvas.drawRoundRect(correctPillRect, 18f, 18f, correctPillBg)
        canvas.drawRoundRect(correctPillRect, 18f, 18f, correctPillBorder)

        val correctTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.rgb(21, 128, 61)
            textSize = 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Doğru Cevap: $effectiveCorrectAnswer", correctPillRect.centerX(), correctPillRect.centerY() + 10f, correctTextPaint)

        currentY += pillHeight + 28f

        // 5. ÖĞRENCİ NOTU KUTUSU (Speech Bubble / Quote Card)
        val noteBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.rgb(241, 245, 249) // Slate 100
        }
        val noteBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.rgb(2, 132, 199) // Sky Blue Left Accent Bar
        }
        val noteBoxRect = RectF(padding.toFloat(), currentY, (cardWidth - padding).toFloat(), currentY + noteLayout.height + 70f)
        canvas.drawRoundRect(noteBoxRect, 16f, 16f, noteBgPaint)
        // Sol vurgulu çizgi
        canvas.drawRoundRect(RectF(padding.toFloat(), currentY, padding.toFloat() + 12f, currentY + noteLayout.height + 70f), 8f, 8f, noteBarPaint)

        val noteHeaderPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.rgb(2, 132, 199)
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("ÖĞRENCİ NOTU & SORUSU:", padding.toFloat() + 24f, currentY + 34f, noteHeaderPaint)

        canvas.save()
        canvas.translate(padding.toFloat() + 24f, currentY + 50f)
        noteLayout.draw(canvas)
        canvas.restore()

        currentY += noteLayout.height + 95f

        // 6. ALT BİLGİ (Footer)
        val dividerPaint = Paint().apply {
            color = AndroidColor.rgb(226, 232, 240)
            strokeWidth = 2f
        }
        canvas.drawLine(padding.toFloat(), currentY, (cardWidth - padding).toFloat(), currentY, dividerPaint)
        currentY += 35f

        val footerTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.rgb(148, 163, 184)
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Soru Defterim • YKS Hazırlık", (cardWidth / 2).toFloat(), currentY, footerTextPaint)

        return bitmap
    }

    /**
     * Üretilen kart görselini geçici önbelleğe kaydeder
     */
    fun saveBitmapToCache(context: Context, bitmap: Bitmap, soruId: Long): File {
        val cacheFolder = File(context.cacheDir, "teacher_share").apply { mkdirs() }
        val targetFile = File(cacheFolder, "soru_kart_${soruId}_${System.currentTimeMillis()}.jpg")
        FileOutputStream(targetFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, fos)
            fos.flush()
        }
        return targetFile
    }

    /**
     * Soru kartını arka planda render eder ve WhatsApp (veya Intent Chooser) ile paylaşır
     */
    suspend fun shareToTeacher(
        context: Context,
        soru: Soru,
        studentAnswer: String? = null,
        customNote: String? = null
    ) = withContext(Dispatchers.IO) {
        try {
            val bitmap = generateQuestionCardBitmap(
                context = context,
                soru = soru,
                studentAnswer = studentAnswer,
                customNote = customNote
            )

            val file = saveBitmapToCache(context, bitmap, soru.id)
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareText = buildShareMessage(
                greeting = DEFAULT_WHATSAPP_GREETING,
                dersAdi = DersTaksonomisi.getDersAdi(soru.examType, soru.subjectId),
                topic = soru.topic,
                examType = soru.examType,
                studentAnswer = studentAnswer ?: soru.studentAnswer,
                correctAnswer = soru.correctAnswer,
                note = customNote ?: soru.note
            )

            val isInstalled = isWhatsAppInstalled(context)
            val whatsappPkg = getInstalledWhatsAppPackage(context)

            val shareIntent = createTeacherShareIntent(
                imageUri = uri,
                greetingText = shareText,
                targetPackage = if (isInstalled) whatsappPkg else null
            )

            withContext(Dispatchers.Main) {
                if (isInstalled && whatsappPkg != null) {
                    try {
                        context.startActivity(shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (e: Exception) {
                        val chooser = Intent.createChooser(createTeacherShareIntent(uri, shareText), "Hocana Sor (WhatsApp / Paylaş)")
                        context.startActivity(chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                } else {
                    val chooser = Intent.createChooser(createTeacherShareIntent(uri, shareText), "Hocana Sor (WhatsApp / Paylaş)")
                    context.startActivity(chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Soru kartı oluşturulurken hata oluştu: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/**
 * Öğrencinin hocaya göndermeden önce notunu ve cevabını girmesini sağlayan modern diyalog
 */
@Composable
fun TeacherShareDialog(
    soru: Soru,
    studentAnswer: String? = null,
    onDismiss: () -> Unit,
    onSend: (note: String) -> Unit
) {
    var noteText by remember {
        mutableStateOf(
            soru.note ?: "Hocam bu sorunun mantığını tam anlayamadım, vaktiniz olduğunda bakabilir misiniz?"
        )
    }
    var isSending by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isSending) onDismiss() },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "👨‍🏫", fontSize = 24.sp)
                Column {
                    Text(
                        text = "Hocama Sor (WhatsApp)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Markalı akıllı soru kartı oluşturulacak",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Soru ve Cevap Özeti
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "${soru.examType} • ${DersTaksonomisi.getDersAdi(soru.examType, soru.subjectId)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (soru.topic.isNotBlank()) {
                            Text(text = soru.topic, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Senin Cevabın: ${studentAnswer ?: soru.studentAnswer ?: "Boş"}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEF5350)
                            )
                            Text(
                                text = "Doğru Cevap: ${soru.correctAnswer ?: "—"}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF43A047)
                            )
                        }
                    }
                }

                // Not Giriş Alanı
                Text(
                    text = "Hocana iletmek istediğin not:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    placeholder = {
                        Text("Örn: Hocam sağ el kuralında indüksiyon akımının yönünü ters buluyorum, bakar mısınız?")
                    },
                    shape = RoundedCornerShape(12.dp),
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isSending = true
                    onSend(noteText)
                },
                enabled = !isSending,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF25D366) // WhatsApp Yeşil
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Kart Hazırlanıyor...")
                } else {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("WhatsApp ile Gönder", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !isSending,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("İptal")
            }
        }
    )
}
