package com.sorudefteri.ui.istatistik.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.FileProvider
import com.sorudefteri.domain.model.Badge
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * Instagram & WhatsApp Story 9:16 Başarı Kartı Üreticisi (Faz 5.4).
 *
 * 1080x1920 px dikey (9:16) formatında öğrencinin seri, başarı oranı, seviye,
 * rozetler ve hedefini içeren modern gradient arkaplanlı paylaşılabilir görsel üretir.
 */
object StoryCardGenerator {

    const val STORY_WIDTH = 1080
    const val STORY_HEIGHT = 1920

    /**
     * 1080x1920 piksel boyutunda 9:16 oranlı hikaye görseli (Bitmap) oluşturur.
     */
    fun generateStoryBitmap(
        context: Context,
        currentStreak: Int,
        successRate: Float,
        levelTitle: String,
        levelNumber: Int,
        totalXp: Int,
        unlockedBadges: Set<String> = emptySet(),
        studentTarget: String = "Hedef: Cerrahpaşa Tıp",
        netIncrease: String? = null,
        bitmapProvider: ((Int, Int) -> Bitmap)? = null
    ): Bitmap {
        val bitmap = bitmapProvider?.invoke(STORY_WIDTH, STORY_HEIGHT)
            ?: Bitmap.createBitmap(STORY_WIDTH, STORY_HEIGHT, Bitmap.Config.ARGB_8888)

        val canvas = Canvas(bitmap)

        // 1. Modern Dinamik Gradient Arka Plan (Deep Purple / Indigo -> Sunset Coral)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, STORY_WIDTH.toFloat(), STORY_HEIGHT.toFloat(),
                intArrayOf(
                    0xFF120826.toInt(), // Deep Indigo / Dark Purple
                    0xFF28114E.toInt(), // Midnight Violet
                    0xFF541355.toInt(), // Royal Purple
                    0xFF8E1B5D.toInt(), // Magenta
                    0xFFE64A5E.toInt()  // Sunset Coral
                ),
                floatArrayOf(0.0f, 0.25f, 0.55f, 0.80f, 1.0f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, STORY_WIDTH.toFloat(), STORY_HEIGHT.toFloat(), bgPaint)

        // 2. Ambient Işıltılar (Glow Orbs)
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        glowPaint.color = 0x22FF6090.toInt()
        canvas.drawCircle(920f, 320f, 280f, glowPaint)

        glowPaint.color = 0x1A7C4DFF.toInt()
        canvas.drawCircle(140f, 1380f, 320f, glowPaint)

        // 3. Aday & Uygulama Başlığı
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 38f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("🎯 Soru Defterim • YKS Hazırlık", STORY_WIDTH / 2f, 180f, titlePaint)

        // 4. Öğrenci Hedef Kapsülü (Pill Badge)
        val targetText = if (studentTarget.startsWith("Hedef", ignoreCase = true)) studentTarget else "Hedef: $studentTarget"
        val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x33FFFFFF
            style = Paint.Style.FILL
        }
        val pillStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x66FFFFFF
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }
        val targetTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFE082.toInt() // Soft Gold
            textSize = 32f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val textWidth = targetTextPaint.measureText(targetText)
        val pillLeft = (STORY_WIDTH - textWidth) / 2f - 40f
        val pillRight = (STORY_WIDTH + textWidth) / 2f + 40f
        val pillRect = RectF(pillLeft, 220f, pillRight, 290f)
        canvas.drawRoundRect(pillRect, 35f, 35f, pillPaint)
        canvas.drawRoundRect(pillRect, 35f, 35f, pillStrokePaint)
        canvas.drawText(targetText, STORY_WIDTH / 2f, 266f, targetTextPaint)

        // 5. Büyük KPI Kartları (Glassmorphism stili)
        val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x2AFFFFFF
            style = Paint.Style.FILL
        }
        val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x4DFFFFFF
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }

        val cardLeft = 80f
        val cardRight = STORY_WIDTH - 80f

        // KPI Kart 1: Günlük Seri
        val card1Rect = RectF(cardLeft, 370f, cardRight, 560f)
        drawKpiCard(
            canvas = canvas,
            rect = card1Rect,
            bgPaint = cardBgPaint,
            borderPaint = cardBorderPaint,
            icon = "🔥",
            title = "$currentStreak Günlük Seri!",
            titleColor = 0xFFFFB74D.toInt(), // Flame Orange
            subtitle = "Hedefe giden yolda disiplinli ve kesintisiz çalışma temposu"
        )

        // KPI Kart 2: Başarı Oranı & Net Artışı
        val card2Rect = RectF(cardLeft, 600f, cardRight, 790f)
        val rateTitle = "%${successRate.toInt()} Başarı Oranı" +
            if (!netIncrease.isNullOrBlank()) " • $netIncrease" else " & Net Artışı"
        drawKpiCard(
            canvas = canvas,
            rect = card2Rect,
            bgPaint = cardBgPaint,
            borderPaint = cardBorderPaint,
            icon = "📈",
            title = rateTitle,
            titleColor = 0xFF81C784.toInt(), // Light Green
            subtitle = "Hatalı sorular analiz edildi, doğrulara dönüştürüldü"
        )

        // KPI Kart 3: Kullanıcı Seviyesi & XP
        val card3Rect = RectF(cardLeft, 830f, cardRight, 1020f)
        drawKpiCard(
            canvas = canvas,
            rect = card3Rect,
            bgPaint = cardBgPaint,
            borderPaint = cardBorderPaint,
            icon = "🏆",
            title = "$levelTitle (Seviye $levelNumber) • $totalXp XP",
            titleColor = 0xFFFFD54F.toInt(), // Warm Gold
            subtitle = "Soru Defterim tecrübe ve soru avcılığı kademesi"
        )

        // 6. Rozetler Şeridi (Unlocked Badges Strip)
        val badgeBoxRect = RectF(cardLeft, 1070f, cardRight, 1270f)
        canvas.drawRoundRect(badgeBoxRect, 32f, 32f, cardBgPaint)
        canvas.drawRoundRect(badgeBoxRect, 32f, 32f, cardBorderPaint)

        val badgeHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFE0E0E0.toInt()
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("🏅 KAZANILAN ROZETLER", cardLeft + 36f, 1120f, badgeHeaderPaint)

        if (unlockedBadges.isNotEmpty()) {
            drawBadgesStrip(canvas, cardLeft + 36f, 1160f, unlockedBadges)
        } else {
            val emptyBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFB0BEC5.toInt()
                textSize = 28f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            }
            canvas.drawText("🚀 Henüz açılan rozet yok • Soru çözmeye devam et!", cardLeft + 36f, 1200f, emptyBadgePaint)
        }

        // 7. Motivasyon Sloganı Bölümü
        val quoteMarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x55FFFFFF
            textSize = 100f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("“", STORY_WIDTH / 2f, 1370f, quoteMarkPaint)

        val sloganPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 46f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Her çözülmeyen soru,", STORY_WIDTH / 2f, 1460f, sloganPaint)
        canvas.drawText("sınavda kazanılan bir net!", STORY_WIDTH / 2f, 1524f, sloganPaint)

        val sloganSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFE0E0E0.toInt()
            textSize = 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Hatalarından ders çıkar, gerçek sınavda fark yarat.", STORY_WIDTH / 2f, 1590f, sloganSubPaint)

        // 8. Alt Bilgi / Marka & Web Adresi
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xAAFFFFFF.toInt()
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("📱 Soru Defterim • Kişisel YKS Koçun & Hata Defterin", STORY_WIDTH / 2f, 1800f, footerPaint)

        return bitmap
    }

    private fun drawKpiCard(
        canvas: Canvas,
        rect: RectF,
        bgPaint: Paint,
        borderPaint: Paint,
        icon: String,
        title: String,
        titleColor: Int,
        subtitle: String
    ) {
        canvas.drawRoundRect(rect, 32f, 32f, bgPaint)
        canvas.drawRoundRect(rect, 32f, 32f, borderPaint)

        // Emoji İkon
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 58f
        }
        canvas.drawText(icon, rect.left + 36f, rect.top + 78f, iconPaint)

        // Başlık
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = titleColor
            textSize = 46f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(title, rect.left + 116f, rect.top + 78f, titlePaint)

        // Alt Açıklama Metni
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFECEFF1.toInt()
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        canvas.drawText(subtitle, rect.left + 36f, rect.top + 138f, subPaint)
    }

    private fun drawBadgesStrip(canvas: Canvas, startX: Float, startY: Float, unlockedBadgeIds: Set<String>) {
        var currentX = startX
        val badgePillBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x4DFFFFFF
            style = Paint.Style.FILL
        }
        val badgePillBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x80FFFFFF.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val badgesToShow = unlockedBadgeIds.take(4)
        for (badgeId in badgesToShow) {
            val badge = Badge.fromId(badgeId)
            val title = badge?.title ?: badgeId
            val displayText = "⭐ $title"
            val textW = badgeTextPaint.measureText(displayText)
            val pillW = textW + 36f
            val pillH = 54f

            if (currentX + pillW > STORY_WIDTH - 120f) break

            val pillRect = RectF(currentX, startY, currentX + pillW, startY + pillH)
            canvas.drawRoundRect(pillRect, 27f, 27f, badgePillBg)
            canvas.drawRoundRect(pillRect, 27f, 27f, badgePillBorder)
            canvas.drawText(displayText, currentX + 18f, startY + 37f, badgeTextPaint)

            currentX += pillW + 18f
        }
    }

    /**
     * Üretilen Bitmap'i geçici PNG dosyasına kaydedip doğrudan Instagram Story veya
     * sistem Paylaşım Seçicisi (WhatsApp, Telegram vb.) ile paylaşır.
     */
    fun shareToStory(context: Context, storyBitmap: Bitmap) {
        try {
            val sharedDir = File(context.cacheDir, "shared").apply { if (!exists()) mkdirs() }
            val storyFile = File(sharedDir, "soru_defterim_story_${System.currentTimeMillis()}.png")

            FileOutputStream(storyFile).use { fos ->
                storyBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                fos.flush()
            }

            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                storyFile
            )

            // 1. Instagram Stories Intent Denemesi
            val instagramIntent = Intent("com.instagram.share.ADD_TO_STORY").apply {
                setDataAndType(contentUri, "image/png")
                putExtra("interactive_asset_uri", contentUri)
                putExtra("source_application", context.packageName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val hasInstagram = try {
                val resolved = instagramIntent.resolveActivity(context.packageManager)
                resolved != null
            } catch (_: Exception) {
                false
            }

            if (hasInstagram) {
                try {
                    context.startActivity(instagramIntent)
                    return
                } catch (_: ActivityNotFoundException) {
                    // Fallback to standard share
                }
            }

            // 2. Standart Paylaşım Seçicisi (WhatsApp Durum, Telegram, Galeri vb.)
            val fallbackIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(
                    Intent.EXTRA_TEXT,
                    "🎯 Soru Defterim ile YKS Hazırlık Başarı Kartım! #SoruDefterim #YKS"
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(fallbackIntent, "Hikayede Paylaş (Instagram / WhatsApp / Telegram)")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
