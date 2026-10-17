package com.sorudefteri.ui.ortak

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.ui.graphics.Color
import androidx.core.content.FileProvider
import java.io.File

/**
 * Desteklenen harici AI Chatbot modelleri
 */
enum class AiBotType(
    val id: String,
    val displayName: String,
    val description: String,
    val badge: String,
    val color: Color,
    val packageName: String,
    val webUrl: String,
    val isGeneralShare: Boolean = false
) {
    GEMINI(
        id = "gemini",
        displayName = "Google Gemini",
        description = "Görsel tanıma & zengin analiz",
        badge = "Önerilen",
        color = Color(0xFF1A73E8),
        packageName = "com.google.android.apps.bard",
        webUrl = "https://gemini.google.com/"
    ),
    CHATGPT(
        id = "chatgpt",
        displayName = "ChatGPT",
        description = "GPT-4o & adım adım mantık",
        badge = "Popüler",
        color = Color(0xFF10A37F),
        packageName = "com.openai.chatgpt",
        webUrl = "https://chatgpt.com/"
    ),
    CLAUDE(
        id = "claude",
        displayName = "Claude",
        description = "Ayrıntılı pedagojik anlatım",
        badge = "Sonnet",
        color = Color(0xFFCC785C),
        packageName = "com.anthropic.claude",
        webUrl = "https://claude.ai/"
    ),
    DEEPSEEK(
        id = "deepseek",
        displayName = "DeepSeek",
        description = "Derin matematik & mantık muhakemesi",
        badge = "R1 / V3",
        color = Color(0xFF4D6BFE),
        packageName = "com.deepseek.chat",
        webUrl = "https://chat.deepseek.com/"
    ),
    QWEN(
        id = "qwen",
        displayName = "Qwen",
        description = "Sayısal soru çözme uzmanı",
        badge = "Qwen 2.5",
        color = Color(0xFF6C5CE7),
        packageName = "com.alibaba.ailabs.tg",
        webUrl = "https://chat.qwen.ai/"
    ),
    KIMI(
        id = "kimi",
        displayName = "Kimi",
        description = "Uzun bağlam & hızlı analiz",
        badge = "Moonshot",
        color = Color(0xFF00B894),
        packageName = "com.moonshot.kimi",
        webUrl = "https://kimi.moonshot.cn/"
    ),
    GENERAL(
        id = "general",
        displayName = "Diğer / Sistem",
        description = "WhatsApp, Telegram, Notlar vb.",
        badge = "Sistem",
        color = Color(0xFF5C6BC0),
        packageName = "",
        webUrl = "",
        isGeneralShare = true
    );
}

/**
 * Harici AI chatbot uygulamalarına soru görseli ve prompt aktarım yöneticisi
 */
object AiChatbotShareHelper {

    /**
     * Cihazda hedeflenen paketin yüklü olup olmadığını denetler.
     */
    fun isAppInstalled(context: Context, packageName: String): Boolean {
        if (packageName.isBlank()) return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Prompt metnini panoya kopyalar.
     */
    fun copyPromptToClipboard(context: Context, promptText: String, showToast: Boolean = true) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Soru Promptu", promptText)
        clipboard.setPrimaryClip(clip)

        if (showToast && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, "✅ Prompt panoya kopyalandı!", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Chatbot uygulamasını veya web sürümünü açar. Promptu panoya kopyalar.
     */
    fun openChatbot(context: Context, bot: AiBotType, promptText: String) {
        copyPromptToClipboard(context, promptText, showToast = true)

        if (bot.isGeneralShare) {
            shareText(context, promptText)
            return
        }

        try {
            val pm = context.packageManager
            val launchIntent = if (bot.packageName.isNotBlank()) pm.getLaunchIntentForPackage(bot.packageName) else null
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                Toast.makeText(context, "${bot.displayName} açılıyor (Prompt panoda hazır) 📋", Toast.LENGTH_SHORT).show()
                context.startActivity(launchIntent)
            } else {
                openWebUrl(context, bot.webUrl, bot.displayName)
            }
        } catch (e: Exception) {
            openWebUrl(context, bot.webUrl, bot.displayName)
        }
    }

    /**
     * Soru görseli ve prompt metnini doğrudan AI uygulamasına veya sistem paylaşımına gönderir.
     */
    fun shareWithImageAndPrompt(
        context: Context,
        bot: AiBotType,
        imagePath: String?,
        promptText: String
    ) {
        copyPromptToClipboard(context, promptText, showToast = false)

        val file = imagePath?.let { File(it) }
        val hasValidImage = file != null && file.exists()

        val contentUri: Uri? = if (hasValidImage) {
            try {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file!!
                )
            } catch (e: Exception) {
                null
            }
        } else null

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, promptText)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            if (contentUri != null) {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, contentUri)
            } else {
                type = "text/plain"
            }
        }

        // Eğer hedef uygulama yüklüyse doğrudan ona gönder
        if (!bot.isGeneralShare && bot.packageName.isNotBlank() && isAppInstalled(context, bot.packageName)) {
            try {
                shareIntent.setPackage(bot.packageName)
                if (contentUri != null) {
                    context.grantUriPermission(bot.packageName, contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(shareIntent)
                return
            } catch (e: Exception) {
                // Hata durumunda intent package temizlenip fallback yapılır
                shareIntent.setPackage(null)
            }
        }

        // Sistem paylaşım menüsü
        val chooserTitle = if (bot.isGeneralShare) "Soruyu Paylaş" else "${bot.displayName} ile Paylaş"
        val chooser = Intent.createChooser(shareIntent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    private fun shareText(context: Context, text: String) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(sendIntent, "Promptu Paylaş")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    private fun openWebUrl(context: Context, url: String, botName: String) {
        if (url.isBlank()) return
        try {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            Toast.makeText(context, "$botName web sitesi açılıyor (Prompt panoda hazır) 📋", Toast.LENGTH_SHORT).show()
            context.startActivity(webIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Tarayıcı açılamadı: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}
