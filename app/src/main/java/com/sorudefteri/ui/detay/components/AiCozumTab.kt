package com.sorudefteri.ui.detay.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sorudefteri.ui.ortak.math.CombinedMathMarkdownText
import kotlinx.coroutines.launch

@Composable
fun AiCozumTab(
    aiExplanation: String?,
    isAiLoading: Boolean,
    aiError: String?,
    isApiKeyMissing: Boolean,
    isQueuedForRetry: Boolean = false,
    onGenerateSolution: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onManualSolution: () -> Unit = {},
    onExternalAiShare: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (aiExplanation.isNullOrBlank() && !isAiLoading) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Yapay Zeka Çözüm Asistanı",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Sorunun adım adım çözümünü, ilgili formülleri ve yapılan hatanın analizini yapay zekadan öğrenin.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    if (isApiKeyMissing) {
                        Button(
                            onClick = onNavigateToSettings,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("API Anahtarı Tanımla")
                        }
                    } else {
                        Button(
                            onClick = onGenerateSolution,
                            enabled = !isAiLoading,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("AI Çözümünü Getir")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    FilledTonalButton(
                        onClick = onExternalAiShare,
                        enabled = !isAiLoading,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Harici AI ile Çöz & Paylaş (Ücretsiz)")
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = onManualSolution,
                        enabled = !isAiLoading,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Manuel Çözüm Gir (Harici AI)")
                    }

                    if (isQueuedForRetry) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "📡 Çevrimdışı kuyruk aktif — internet gelince otomatik denenecek.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    aiError?.let { err ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (!isApiKeyMissing) {
                            OutlinedButton(
                                onClick = onGenerateSolution,
                                enabled = !isAiLoading,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Tekrar Dene")
                            }
                        }
                    }
                }
            }
        } else if (isAiLoading) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(52.dp),
                            strokeWidth = 3.5.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "⚡ AI Çözümü Arka Planda Hazırlanıyor...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "⚡ AI çözümü arka planda güvenle üretiliyor. Ekrandan çıksanız veya uygulamayı kapatsanız dahi çözüm kaydedilecek ve bildirim alacaksınız.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(text = "💡", fontSize = 16.sp)
                            Text(
                                text = "Soru analiz ediliyor, LaTeX matematik formatı ve pedagojik açıklamalar derleniyor.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else {
            // Çözüm Kartı
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Adım Adım AI Çözümü",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row {
                            IconButton(onClick = onManualSolution, enabled = !isAiLoading) {
                                Icon(Icons.Default.Edit, contentDescription = "Manuel Düzenle")
                            }
                            IconButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("AI Çözüm", aiExplanation)
                                clipboard.setPrimaryClip(clip)
                                scope.launch { snackbarHostState.showSnackbar("Çözüm panoya kopyalandı") }
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Kopyala")
                            }

                            IconButton(onClick = onGenerateSolution, enabled = !isAiLoading) {
                                Icon(Icons.Default.Refresh, contentDescription = "Yeniden Çöz")
                            }

                            IconButton(onClick = onExternalAiShare, enabled = !isAiLoading) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = "Harici AI ile Çöz / Paylaş", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        SnackbarHost(hostState = snackbarHostState)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val rawExplanation = aiExplanation ?: ""
                    val parsedTiers = remember(rawExplanation) { parseCoachTiers(rawExplanation) }
                    var isCoachMode by remember(rawExplanation) { mutableStateOf(parsedTiers != null && parsedTiers.size >= 2) }
                    var unlockedTier by remember(rawExplanation) { androidx.compose.runtime.mutableIntStateOf(1) }

                    // Mod Seçici: Kademeli Koç Modu vs Tam Çözüm
                    if (parsedTiers != null && parsedTiers.size >= 2) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            androidx.compose.material3.FilterChip(
                                selected = isCoachMode,
                                onClick = { isCoachMode = true },
                                label = { Text("🔒 Kademeli Koç Modu") },
                                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                            androidx.compose.material3.FilterChip(
                                selected = !isCoachMode,
                                onClick = { isCoachMode = false },
                                label = { Text("📖 Tam Çözüm") },
                                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    if (isCoachMode && parsedTiers != null) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            parsedTiers.forEach { tier ->
                                if (tier.tierNumber <= unlockedTier) {
                                    UnlockedTierCard(tier = tier)
                                } else {
                                    LockedTierCard(
                                        tier = tier,
                                        isNextToUnlock = tier.tierNumber == unlockedTier + 1,
                                        onUnlock = { unlockedTier = tier.tierNumber }
                                    )
                                }
                            }
                        }
                    } else {
                        CombinedMathMarkdownText(
                            text = rawExplanation,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                    }

                    // Yeniden denemede oluşan hata - cached çözüm varken bile göster
                    aiError?.let { err ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Yeniden çözüm hatası: $err",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (isApiKeyMissing) {
                            Button(
                                onClick = onNavigateToSettings,
                                enabled = !isAiLoading,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("API Anahtarı Tanımla")
                            }
                        } else {
                            FilledTonalButton(
                                onClick = onGenerateSolution,
                                enabled = !isAiLoading,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Tekrar Dene")
                            }
                        }
                    }
                }
            }
        }
    }
}

data class CoachTier(
    val tierNumber: Int,
    val title: String,
    val subtitle: String,
    val content: String,
    val iconEmoji: String
)

fun parseCoachTiers(rawText: String): List<CoachTier>? {
    val clean = rawText.trim()
    val tierHeaderRegex = Regex("""(?m)^#{1,4}\s*(?:🧩|🚀|⚠️|🎯)?\s*(?:Aşama\s*([1-4])|([1-4])\.\s*Aşama)\s*:\s*(.+)$""")
    val matches = tierHeaderRegex.findAll(clean).toList()
    if (matches.size >= 2) {
        val tiers = mutableListOf<CoachTier>()
        for (i in matches.indices) {
            val currentMatch = matches[i]
            val tierNum = (currentMatch.groupValues[1].ifBlank { currentMatch.groupValues[2] }).toIntOrNull() ?: (i + 1)
            val title = currentMatch.groupValues[3].trim()
            val startIdx = currentMatch.range.last + 1
            val endIdx = if (i < matches.size - 1) matches[i + 1].range.first else clean.length
            val content = clean.substring(startIdx, endIdx).trim()
            val (subtitle, emoji) = when (tierNum) {
                1 -> "Konu Kavramı ve Temel Formül" to "🧩"
                2 -> "İlk Adım Stratejisi" to "🚀"
                3 -> "En Büyük Çeldirici Tuzağı" to "⚠️"
                else -> "Tam Çözüm ve Doğru Yanıt" to "🎯"
            }
            tiers.add(CoachTier(tierNum, title.ifBlank { subtitle }, subtitle, content, emoji))
        }
        return tiers
    }

    // Alternatif olarak ### başlıklarına göre ayır
    val lines = clean.lines()
    val sectionStarts = lines.indices.filter { idx ->
        val l = lines[idx].trim()
        l.contains("Aşama 1", ignoreCase = true) ||
        l.contains("Aşama 2", ignoreCase = true) ||
        l.contains("Aşama 3", ignoreCase = true) ||
        l.contains("Aşama 4", ignoreCase = true)
    }
    if (sectionStarts.size >= 2) {
        val tiers = mutableListOf<CoachTier>()
        for (i in sectionStarts.indices) {
            val startLine = sectionStarts[i]
            val endLine = if (i < sectionStarts.size - 1) sectionStarts[i + 1] else lines.size
            val headerLine = lines[startLine].trim().replace("#", "").trim()
            val content = lines.subList(startLine + 1, endLine).joinToString("\n").trim()
            val tierNum = i + 1
            val (subtitle, emoji) = when (tierNum) {
                1 -> "Konu Kavramı ve Temel Formül" to "🧩"
                2 -> "İlk Adım Stratejisi" to "🚀"
                3 -> "En Büyük Çeldirici Tuzağı" to "⚠️"
                else -> "Tam Çözüm ve Doğru Yanıt" to "🎯"
            }
            tiers.add(CoachTier(tierNum, headerLine, subtitle, content, emoji))
        }
        return tiers
    }

    return null
}

@Composable
private fun UnlockedTierCard(
    tier: CoachTier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(
            1.dp,
            if (tier.tierNumber == 4) Color(0xFF2E7D32).copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (tier.tierNumber == 4) Color(0xFF2E7D32).copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = tier.iconEmoji,
                            fontSize = 16.sp
                        )
                    }
                }
                Column {
                    Text(
                        text = "Aşama ${tier.tierNumber}: ${tier.subtitle}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (tier.tierNumber == 4) Color(0xFF2E7D32)
                        else MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            CombinedMathMarkdownText(
                text = tier.content,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun LockedTierCard(
    tier: CoachTier,
    isNextToUnlock: Boolean,
    onUnlock: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "${tier.iconEmoji} Aşama ${tier.tierNumber}: ${tier.subtitle}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Öğrenmeyi pekiştirmek için ipuçlarını sırayla aç.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            if (isNextToUnlock) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onUnlock,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        imageVector = Icons.Default.LockOpen,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Aşama ${tier.tierNumber} İpucunu Aç")
                }
            }
        }
    }
}
