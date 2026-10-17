package com.sorudefteri.ui.detay.components

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import kotlinx.coroutines.delay
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sorudefteri.data.ai.AiJsonParser
import com.sorudefteri.domain.model.SimilarQuestionResult
import com.sorudefteri.ui.ortak.math.MathMarkdownText

@Composable
fun ManualSimilarQuestionDialog(
    onDismiss: () -> Unit,
    onSaveSimilarQuestion: (SimilarQuestionResult) -> Unit
) {
    val context = LocalContext.current
    var rawInputText by rememberSaveable { mutableStateOf("") }
    var parsedResult by remember { mutableStateOf<SimilarQuestionResult?>(null) }
    var parseError by remember { mutableStateOf<String?>(null) }

    // DÜZELTME: Parse HER tuş vuruşunda tetikleniyordu. Önizleme kartı
    // (içinde MathMarkdownText -> KaTeX WebView var) her tuşta belirip
    // kaybolduğu için platformun SİNİRLANDIRDIĞI AlertDialog penceresi sürekli
    // yeniden boyutlanıyor, başlık ve düğmeler zıplıyordu. 350 ms debounce ile
    // yalnızca kullanıcı durduğunda parse ediliyor.
    var debouncedText by remember { mutableStateOf(rawInputText) }
    LaunchedEffect(rawInputText) {
        delay(350)
        debouncedText = rawInputText
    }

    // Girdi değiştikçe otomatik parse dene
    LaunchedEffect(debouncedText) {
        val trimmed = debouncedText.trim()
        if (trimmed.isBlank()) {
            parsedResult = null
            parseError = null
            return@LaunchedEffect
        }

        val result = AiJsonParser.cleanAndParseJson(
            rawText = trimmed,
            serializer = SimilarQuestionResult.serializer(),
            fallbackParser = { AiJsonParser.parseSimilarQuestionFallback(it) }
        )

        result.onSuccess { parsed ->
            if (parsed.questionText.isNotBlank() || parsed.options.isNotEmpty()) {
                parsedResult = parsed
                parseError = null
            } else {
                parsedResult = null
                parseError = "Soru metni veya şıklar eksik görünüyor."
            }
        }.onFailure {
            parsedResult = null
            parseError = "JSON veya metin formatı okunamadı. Lütfen chatbotun ürettiği tam metni yapıştırın."
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Benzer Soru Yapıştır",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                FilledTonalButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = clipboard.primaryClip
                        if (clip != null && clip.itemCount > 0) {
                            val pasteText = clip.getItemAt(0).text?.toString() ?: ""
                            if (pasteText.isNotBlank()) {
                                rawInputText = pasteText
                            }
                        }
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Yapıştır")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Gemini, ChatGPT, DeepSeek vb. chatbotun verdiği yanıtı veya JSON çıktısını buraya yapıştırın. Soru metni, şıklar ve çözüm otomatik olarak ayrıştırılacaktır.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = rawInputText,
                    onValueChange = { rawInputText = it },
                    label = { Text("Chatbot Yanıtı (JSON veya Metin)") },
                    placeholder = { Text("{\"questionText\": \"...\", \"options\": {\"A\": \"...\"}}") },
                    modifier = Modifier
                        .fillMaxWidth()
                        // DÜZELTME: Sabit 160.dp + maxLines 15 -> 15 satır ~300 dp
                        // gerektiriyordu, dolayısıyla alan HER ZAMAN kendi içinde
                        // kaydırma çubuğu taşıyor, dialog'un dış kaydırmasıyla
                        // iç içe geçiyordu. Aralık verildi: kısa metinde küçük,
                        // uzun metinde büyür ama sınırlı.
                        .heightIn(min = 140.dp, max = 260.dp),
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 8
                )

                // Durum Bildirimi ve Önizleme
                if (parsedResult != null) {
                    val res = parsedResult!!
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        // Geç KaTeX yükseklik bildiriminin dialog'u yeniden
                        // boyutlandırmaması için taban yükseklik sabitleniyor.
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 96.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Ayrıştırıldı (${res.options.size} Şık + Doğru Cevap: ${res.correctAnswer})",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            val isDark = isSystemInDarkTheme()
                            Text(
                                text = "Soru Önizleme:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                            MathMarkdownText(
                                text = res.questionText.take(180) + if (res.questionText.length > 180) "..." else "",
                                textColor = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (res.options.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                res.options.forEach { (key, optText) ->
                                    Text(
                                        text = "$key) ${optText.take(60)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (key == res.correctAnswer) MaterialTheme.colorScheme.primary else if (isDark) Color(0xFFE0E2EC) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                } else if (parseError != null && rawInputText.isNotBlank()) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = parseError!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    parsedResult?.let {
                        onSaveSimilarQuestion(it)
                        onDismiss()
                    }
                },
                enabled = parsedResult != null,
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Uygulamaya Yükle")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(10.dp)) {
                Text("İptal")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}
