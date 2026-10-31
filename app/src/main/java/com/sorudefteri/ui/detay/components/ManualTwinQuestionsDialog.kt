package com.sorudefteri.ui.detay.components

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import com.sorudefteri.domain.model.TwinQuestionsResult
import com.sorudefteri.ui.ortak.math.CombinedMathMarkdownText
import kotlinx.coroutines.delay

/**
 * Harici AI chatbotlarından (Gemini Web, ChatGPT, Claude) kopyalanan
 * 3 Soruluk Soru İkizi (Kardeş Test) JSON çıktısını yapıştırma ve ayrıştırma dialogu.
 */
@Composable
fun ManualTwinQuestionsDialog(
    onDismiss: () -> Unit,
    onStartTest: (TwinQuestionsResult) -> Unit,
    onSaveToLibrary: (TwinQuestionsResult) -> Unit
) {
    val context = LocalContext.current
    var rawInputText by rememberSaveable { mutableStateOf("") }
    var parsedResult by remember { mutableStateOf<TwinQuestionsResult?>(null) }
    var parseError by remember { mutableStateOf<String?>(null) }

    // Kullanıcı yazarken dialog boyutlarının zıplamaması için 350ms debounce
    var debouncedText by remember { mutableStateOf(rawInputText) }
    LaunchedEffect(rawInputText) {
        delay(350)
        debouncedText = rawInputText
    }

    LaunchedEffect(debouncedText) {
        val trimmed = debouncedText.trim()
        if (trimmed.isBlank()) {
            parsedResult = null
            parseError = null
            return@LaunchedEffect
        }

        val result = AiJsonParser.parseTwinQuestionsResult(trimmed)
        result.onSuccess { parsed ->
            if (parsed.questions.isNotEmpty()) {
                parsedResult = parsed
                parseError = null
            } else {
                parsedResult = null
                parseError = "JSON içinde soru bulunamadı."
            }
        }.onFailure { err ->
            parsedResult = null
            parseError = err.message ?: "3 soruluk JSON formatı okunamadı. Lütfen chatbotun verdiği tam JSON çıktısını yapıştırın."
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
                Column {
                    Text(
                        text = "3 Kardeş Soru Yapıştır",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Soru İkizi Test Paketi",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
                    text = "Gemini Web veya ChatGPT'nin ürettiği 3 soruluk JSON kod bloğunu buraya yapıştırın. Sorular, şıklar, çözümler ve tuzak analizleri otomatik olarak ayrıştırılır.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = rawInputText,
                    onValueChange = { rawInputText = it },
                    label = { Text("Chatbot JSON Çıktısı") },
                    placeholder = { Text("{\"originalTopic\": \"...\", \"questions\": [...]}") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 130.dp, max = 220.dp),
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 8
                )

                // Ayrıştırma Başarılı Önizleme
                if (parsedResult != null) {
                    val res = parsedResult!!
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "3 Soru Başarıyla Ayrıştırıldı!",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            if (res.originalTopic.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Kazanım: ${res.originalTopic}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // 3 Sorunun Kısa Listesi
                            res.questions.forEachIndexed { idx, q ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${idx + 1}. Kardeş Soru",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "Doğru: ${q.correctAnswer} (${q.options.size} Şık)",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))
                                        CombinedMathMarkdownText(
                                            text = q.questionText.take(120) + if (q.questionText.length > 120) "..." else "",
                                            textColor = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        if (!q.trapWarning.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Default.Warning,
                                                    contentDescription = null,
                                                    tint = Color(0xFFF57C00),
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "Tuzak Uyarısı Mevcut",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color(0xFFF57C00),
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }
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
            if (parsedResult != null) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Seçenek a: Mini Testi Başlat
                    Button(
                        onClick = {
                            parsedResult?.let { res ->
                                onDismiss()
                                onStartTest(res)
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Mini Testi Başlat", fontWeight = FontWeight.Bold)
                    }

                    // Seçenek b: Kütüphaneme Soru Olarak Kaydet
                    FilledTonalButton(
                        onClick = {
                            parsedResult?.let { res ->
                                onDismiss()
                                onSaveToLibrary(res)
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Kütüphaneme 3 Soru Olarak Kaydet")
                    }
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Kapat")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}
