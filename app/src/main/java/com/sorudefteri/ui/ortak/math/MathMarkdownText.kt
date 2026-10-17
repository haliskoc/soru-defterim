package com.sorudefteri.ui.ortak.math

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * LaTeX ve KaTeX ifadelerini tespit eden yardımcı nesne.
 */
object MathDisplayHelper {
    private val MATH_TRIGGER_KEYWORDS = listOf(
        "\\frac", "\\dfrac", "\\cfrac", "\\tfrac", "\\dfac", "dfac",
        "\\sqrt", "\\int", "\\iint", "\\iiint", "\\oint",
        "\\sum", "\\prod", "\\lim", "\\begin", "\\pmatrix", "\\bmatrix", "\\matrix",
        "\\ce", "\\vec", "\\overrightarrow", "\\alpha", "\\beta", "\\gamma",
        "\\theta", "\\lambda", "\\mu", "\\sigma", "\\omega", "\\Delta", "\\infty",
        "\\times", "\\cdot", "\\pm", "\\mp", "\\leq", "\\geq", "\\neq",
        "\\approx", "\\equiv", "\\to", "\\implies", "\\iff", "\\rightarrow",
        "\\partial", "\\nabla", "\\cancel", "\\binom", "\\overset", "\\underset",
        "\\xrightarrow", "\\xleftarrow", "\\left", "\\right"
    )

    private val FRACTION_PATTERN = Regex("""(?:\(\s*[a-zA-Z0-9\+\-\*\.\s]+\s*\)\s*/\s*(?:\(\s*[a-zA-Z0-9\+\-\*\.\s]+\s*\)|[a-zA-Z0-9]+)|\(\s*[a-zA-Z0-9\+\-\*\.]+\s*/\s*[a-zA-Z0-9\+\-\*\.]+\s*\)|\[\s*0\s*/\s*0\s*\]|(?<![a-zA-Z0-9/\\])\d{1,4}\s*/\s*\d{1,4}(?![a-zA-Z0-9/])|(?<![a-zA-Z])\\*dfac\b)""")

    fun hasMathOrLatex(text: String): Boolean {
        if (text.isBlank()) return false
        if (text.contains("$") || text.contains("\\[") || text.contains("\\(")) return true
        if (MATH_TRIGGER_KEYWORDS.any { text.contains(it, ignoreCase = true) }) return true
        if (FRACTION_PATTERN.containsMatchIn(text)) return true
        if (Regex("""[a-zA-Z0-9]\s*[\^_]\{?[0-9a-zA-Z\+\-]+\}?""").containsMatchIn(text)) return true
        return false
    }
}

/**
 * İçerikte matematiksel veya kimyasal formül (KaTeX) varsa [KaTeXMathView] ile
 * vektörel ve pay-payda kesirli renderlayan, yoksa yerel Compose [RenderFormattedLines]
 * kullanan akıllı hibrit görüntüleyici.
 */
@Composable
fun SmartMathContent(
    content: String,
    modifier: Modifier = Modifier,
    fontSizeSp: Float = 15f,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    backgroundColor: Color = Color.Transparent,
    displayMode: Boolean = false
) {
    if (content.isBlank()) return

    val hasMath = remember(content) { MathDisplayHelper.hasMathOrLatex(content) }

    if (hasMath) {
        KaTeXMathView(
            content = content,
            modifier = modifier,
            fontSizeSp = fontSizeSp,
            textColor = textColor,
            backgroundColor = backgroundColor,
            displayMode = displayMode,
            fallback = {
                RenderFormattedLines(content, textColor)
            }
        )
    } else {
        RenderFormattedLines(content, textColor)
    }
}

/**
 * Yapay Zeka çıktılarında yer alan LaTeX formülleri, üslü/köklü/kesirli ifadeler,
 * integraller, toplam sembolleri ve Markdown formatını göze hitap eden zengin
 * kartlar ve tipografi ile renderlayan bileşen.
 */
@Composable
fun MathMarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    fontSizeSp: Float = 15f,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    if (text.isBlank()) return

    val sections = remember(text) { parseAiExplanationSections(text) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        sections.forEach { section ->
            when (section.type) {
                SectionType.CORRECT_ANSWER -> CorrectAnswerCard(section, fontSizeSp, textColor)
                SectionType.FORMULAS -> FormulasCard(section, fontSizeSp, textColor)
                SectionType.STEPS -> StepsCard(section, fontSizeSp, textColor)
                SectionType.MISTAKES -> MistakesCard(section, fontSizeSp, textColor)
                SectionType.TIPS -> TipsCard(section, fontSizeSp, textColor)
                SectionType.GENERAL -> GeneralTextCard(section, fontSizeSp, textColor)
            }
        }
    }
}

private enum class SectionType {
    CORRECT_ANSWER,
    FORMULAS,
    STEPS,
    MISTAKES,
    TIPS,
    GENERAL
}

private data class AiSection(
    val type: SectionType,
    val title: String,
    val content: String,
    val icon: ImageVector? = null
)

/**
 * Ham AI çıktısını veya veritabanındaki JSON / markdown biçimli metinleri temizler.
 * Eğer metin bir JSON objesi içeriyorsa (örn. benzer soru çıktısı, harici AI cevabı veya
 * raw JSON payload), JSON alanlarını (solution, correctAnswer, steps, tips vb.) ayıklar
 * ve okunabilir zengin Markdown formatına dönüştürür.
 */
private fun sanitizeOrExtractFromJson(rawText: String): String {
    val trimmed = rawText.trim()
    if (trimmed.isBlank()) return ""

    // 1. Markdown code block içindeki JSON'u çıkar (```json ... ``` veya ``` ... ```)
    var candidate = trimmed
    val codeBlockMatch = Regex("""```(?:json)?\s*(\{[\s\S]*?\})\s*```""", RegexOption.IGNORE_CASE).find(candidate)
    if (codeBlockMatch != null) {
        candidate = codeBlockMatch.groupValues[1].trim()
    }

    // 2. JSON objesi başlangıç/bitiş kontrolü
    val firstBrace = candidate.indexOf('{')
    val lastBrace = candidate.lastIndexOf('}')
    if (firstBrace != -1 && lastBrace > firstBrace) {
        val jsonCandidate = candidate.substring(firstBrace, lastBrace + 1).trim()
        try {
            val jsonObject = org.json.JSONObject(jsonCandidate)
            val sb = StringBuilder()

            // Doğru Cevap
            val answer = when {
                jsonObject.has("correctAnswer") -> jsonObject.optString("correctAnswer")
                jsonObject.has("dogru_cevap") -> jsonObject.optString("dogru_cevap")
                jsonObject.has("dogruCevap") -> jsonObject.optString("dogruCevap")
                jsonObject.has("cevap") -> jsonObject.optString("cevap")
                else -> null
            }
            if (!answer.isNullOrBlank()) {
                sb.append("🎯 **Doğru Cevap:** ").append(answer.trim()).append("\n\n")
            }

            // Formüller / Temel Kavramlar
            val formulas = when {
                jsonObject.has("formulas") -> jsonObject.opt("formulas")
                jsonObject.has("formuller") -> jsonObject.opt("formuller")
                jsonObject.has("kavramlar") -> jsonObject.opt("kavramlar")
                jsonObject.has("concepts") -> jsonObject.opt("concepts")
                else -> null
            }
            if (formulas != null) {
                sb.append("📚 **Temel Kavramlar & Formüller:**\n")
                when (formulas) {
                    is org.json.JSONArray -> {
                        for (i in 0 until formulas.length()) {
                            sb.append("• ").append(formulas.optString(i)).append("\n")
                        }
                    }
                    else -> sb.append(formulas.toString()).append("\n")
                }
                sb.append("\n")
            }

            // Çözüm Adımları / Çözüm
            val solution = when {
                jsonObject.has("solution") -> jsonObject.opt("solution")
                jsonObject.has("cozum") -> jsonObject.opt("cozum")
                jsonObject.has("explanation") -> jsonObject.opt("explanation")
                jsonObject.has("aciklama") -> jsonObject.opt("aciklama")
                jsonObject.has("steps") -> jsonObject.opt("steps")
                jsonObject.has("adimlar") -> jsonObject.opt("adimlar")
                else -> null
            }
            if (solution != null) {
                sb.append("📝 **Adım Adım Ayrıntılı Çözüm:**\n")
                when (solution) {
                    is org.json.JSONArray -> {
                        for (i in 0 until solution.length()) {
                            val step = solution.optString(i)
                            if (Regex("""^\d+[\.\)]""").containsMatchIn(step)) {
                                sb.append(step).append("\n")
                            } else {
                                sb.append("${i + 1}. ").append(step).append("\n")
                            }
                        }
                    }
                    else -> sb.append(solution.toString()).append("\n")
                }
                sb.append("\n")
            }

            // Hata & Çeldirici Analizi
            val mistakes = when {
                jsonObject.has("mistakes") -> jsonObject.opt("mistakes")
                jsonObject.has("hatalar") -> jsonObject.opt("hatalar")
                jsonObject.has("celdirici") -> jsonObject.opt("celdirici")
                jsonObject.has("distractorAnalysis") -> jsonObject.opt("distractorAnalysis")
                else -> null
            }
            if (mistakes != null) {
                sb.append("🔍 **Hata & Çeldirici Analizi:**\n")
                when (mistakes) {
                    is org.json.JSONArray -> {
                        for (i in 0 until mistakes.length()) {
                            sb.append("• ").append(mistakes.optString(i)).append("\n")
                        }
                    }
                    else -> sb.append(mistakes.toString()).append("\n")
                }
                sb.append("\n")
            }

            // Püf Noktaları
            val tips = when {
                jsonObject.has("tips") -> jsonObject.opt("tips")
                jsonObject.has("ipuclari") -> jsonObject.opt("ipuclari")
                jsonObject.has("puf_noktalari") -> jsonObject.opt("puf_noktalari")
                jsonObject.has("tactics") -> jsonObject.opt("tactics")
                else -> null
            }
            if (tips != null) {
                sb.append("💡 **Püf Noktaları & Sınav Taktikleri:**\n")
                when (tips) {
                    is org.json.JSONArray -> {
                        for (i in 0 until tips.length()) {
                            sb.append("• ").append(tips.optString(i)).append("\n")
                        }
                    }
                    else -> sb.append(tips.toString()).append("\n")
                }
                sb.append("\n")
            }

            val extracted = sb.toString().trim()
            if (extracted.isNotBlank()) {
                return extracted
            }
        } catch (_: Exception) {
            // JSON ayrıştırma başarısız olursa Regex tabanlı fallback
            val solutionRegex = Regex(""""(?:solution|cozum|explanation|aciklama)"\s*:\s*"((?:[^"\\]|\\.)*)"""")
            val solMatch = solutionRegex.find(jsonCandidate)
            if (solMatch != null) {
                val rawSol = solMatch.groupValues[1]
                    .replace("\\n", "\n")
                    .replace("\\t", "\t")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                val ansMatch = Regex(""""(?:correctAnswer|dogru_cevap|dogruCevap|cevap)"\s*:\s*"([^"]*)"""").find(jsonCandidate)
                val ansStr = ansMatch?.groupValues?.get(1)?.trim()
                return if (!ansStr.isNullOrBlank()) {
                    "🎯 **Doğru Cevap:** $ansStr\n\n📝 **Adım Adım Ayrıntılı Çözüm:**\n$rawSol"
                } else {
                    "📝 **Adım Adım Ayrıntılı Çözüm:**\n$rawSol"
                }
            }
        }
    }

    return trimmed
}

/**
 * AI metnini yapılandırılmış bölümlere (Doğru Cevap, Formüller, Adımlar, Hatalar, İpuçları) ayırır.
 */
private fun parseAiExplanationSections(rawText: String): List<AiSection> {
    // 0. Varsa ham JSON bloğunu ayıkla ve temiz markdown metnine dönüştür
    val extractedText = sanitizeOrExtractFromJson(rawText)
    // 1. Literal \n kaçışlarını güvenle gerçek satır sonuna çevir (LaTeX komutlarını koru)
    val cleanText = normalizeSafeEscapes(extractedText).trim()
    val lines = cleanText.lines()
    val sections = mutableListOf<AiSection>()

    var currentType = SectionType.GENERAL
    var currentTitle = ""
    val currentContentLines = mutableListOf<String>()

    fun commitCurrentSection() {
        val content = currentContentLines.joinToString("\n").trim()
        if (content.isNotBlank() || currentTitle.isNotBlank()) {
            sections.add(
                AiSection(
                    type = currentType,
                    title = currentTitle,
                    content = content
                )
            )
        }
        currentContentLines.clear()
        currentTitle = ""
    }

    for (line in lines) {
        val trimmed = line.trim()
        // Başlıktaki olası markdown '#' karakterlerini temizle
        val normalizedHeaderCandidate = trimmed.trimStart('#', ' ', '\t')
        when {
            normalizedHeaderCandidate.contains("🎯") ||
            normalizedHeaderCandidate.contains("Aşama 4", ignoreCase = true) ||
            (normalizedHeaderCandidate.contains("Doğru Cevap", ignoreCase = true) && (trimmed.startsWith("#") || trimmed.startsWith("**") || trimmed.startsWith("🎯"))) -> {
                commitCurrentSection()
                currentType = SectionType.CORRECT_ANSWER
                currentTitle = "🎯 Doğru Cevap"
                val afterTitle = trimmed.substringAfter(":", "").trim().trimStart('*').trimEnd('*').trim()
                if (afterTitle.isNotBlank()) {
                    currentContentLines.add(afterTitle)
                }
            }
            normalizedHeaderCandidate.contains("📚") ||
            normalizedHeaderCandidate.contains("Aşama 1", ignoreCase = true) ||
            normalizedHeaderCandidate.contains("Temel Kavram", ignoreCase = true) ||
            normalizedHeaderCandidate.contains("Konu Kavramı", ignoreCase = true) ||
            (normalizedHeaderCandidate.contains("Formül", ignoreCase = true) && (trimmed.startsWith("#") || trimmed.startsWith("**") || trimmed.startsWith("📚") || trimmed.startsWith("🧩"))) -> {
                commitCurrentSection()
                currentType = SectionType.FORMULAS
                currentTitle = "📚 Temel Kavramlar & Formüller"
            }
            normalizedHeaderCandidate.contains("📝") ||
            normalizedHeaderCandidate.contains("Aşama 2", ignoreCase = true) ||
            normalizedHeaderCandidate.contains("İlk Adım", ignoreCase = true) ||
            normalizedHeaderCandidate.contains("Adım Adım", ignoreCase = true) ||
            (normalizedHeaderCandidate.contains("Ayrıntılı Çözüm", ignoreCase = true) && (trimmed.startsWith("#") || trimmed.startsWith("**") || trimmed.startsWith("📝") || trimmed.startsWith("🚀"))) -> {
                commitCurrentSection()
                currentType = SectionType.STEPS
                currentTitle = "📝 Adım Adım Ayrıntılı Çözüm"
            }
            normalizedHeaderCandidate.contains("🔍") ||
            normalizedHeaderCandidate.contains("Aşama 3", ignoreCase = true) ||
            normalizedHeaderCandidate.contains("Çeldirici Tuzağı", ignoreCase = true) ||
            normalizedHeaderCandidate.contains("Çeldirici Analizi", ignoreCase = true) ||
            (normalizedHeaderCandidate.contains("Hata & Çeldirici", ignoreCase = true) && (trimmed.startsWith("#") || trimmed.startsWith("**") || trimmed.startsWith("🔍") || trimmed.startsWith("⚠️"))) -> {
                commitCurrentSection()
                currentType = SectionType.MISTAKES
                currentTitle = "🔍 Hata & Çeldirici Analizi"
            }
            normalizedHeaderCandidate.contains("💡") ||
            normalizedHeaderCandidate.contains("Püf Noktaları", ignoreCase = true) ||
            normalizedHeaderCandidate.contains("Sınav Taktikleri", ignoreCase = true) ||
            (normalizedHeaderCandidate.contains("İpuçları", ignoreCase = true) && (trimmed.startsWith("#") || trimmed.startsWith("**") || trimmed.startsWith("💡"))) -> {
                commitCurrentSection()
                currentType = SectionType.TIPS
                currentTitle = "💡 Püf Noktaları & Sınav Taktikleri"
            }
            else -> {
                currentContentLines.add(line)
            }
        }
    }
    commitCurrentSection()

    return if (sections.isEmpty()) {
        listOf(AiSection(SectionType.GENERAL, "", cleanText))
    } else {
        sections
    }
}

/**
 * 🎯 Doğru Cevap Kartı
 */
@Composable
private fun CorrectAnswerCard(
    section: AiSection,
    fontSizeSp: Float = 15f,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val answerContent = section.content.ifBlank { section.title }
    val hasMath = remember(answerContent) { MathDisplayHelper.hasMathOrLatex(answerContent) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF2E7D32).copy(alpha = 0.12f),
        border = BorderStroke(1.dp, Color(0xFF2E7D32).copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            // DÜZELTME: CenterVertically -> Top. Yanındaki 32.dp rozet ile
            // KaTeXMathView aynı satırdaydı; KaTeX yüksekliğini yüklendikten
            // sonra bildirdiği için rozet aşağı kayıyordu.
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xFF2E7D32),
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Doğru Cevap & Sonuç",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32)
                )
                if (hasMath) {
                    KaTeXMathView(
                        content = answerContent,
                        fontSizeSp = fontSizeSp + 1f,
                        textColor = textColor,
                        fallback = {
                            Text(
                                text = formatMathText(answerContent),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = textColor
                            )
                        }
                    )
                } else {
                    Text(
                        text = formatMathText(answerContent),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = textColor
                    )
                }
            }
        }
    }
}

/**
 * 📚 Temel Kavramlar & Formüller Kartı
 */
@Composable
private fun FormulasCard(
    section: AiSection,
    fontSizeSp: Float = 15f,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Functions,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Temel Kavramlar & Formüller",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            SmartMathContent(content = section.content, fontSizeSp = fontSizeSp, textColor = textColor)
        }
    }
}

/**
 * 📝 Adım Adım Çözüm Kartı
 */
@Composable
private fun StepsCard(
    section: AiSection,
    fontSizeSp: Float = 15f,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Adım Adım Ayrıntılı Çözüm",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            SmartMathContent(content = section.content, fontSizeSp = fontSizeSp, textColor = textColor)
        }
    }
}

/**
 * 💡 Püf Noktaları Kartı
 */
@Composable
private fun TipsCard(
    section: AiSection,
    fontSizeSp: Float = 15f,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFF57C00).copy(alpha = 0.10f),
        border = BorderStroke(1.dp, Color(0xFFF57C00).copy(alpha = 0.30f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = Color(0xFFF57C00),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Püf Noktaları & Sınav Taktikleri",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF57C00)
                )
            }

            SmartMathContent(content = section.content, fontSizeSp = fontSizeSp, textColor = textColor)
        }
    }
}

/**
 * 🔍 Hata & Çeldirici Analizi Kartı
 */
@Composable
private fun MistakesCard(
    section: AiSection,
    fontSizeSp: Float = 15f,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFD32F2F).copy(alpha = 0.08f),
        border = BorderStroke(1.dp, Color(0xFFD32F2F).copy(alpha = 0.28f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFD32F2F),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = section.title.ifBlank { "Hata & Çeldirici Analizi" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFD32F2F)
                )
            }

            SmartMathContent(content = section.content, fontSizeSp = fontSizeSp, textColor = textColor)
        }
    }
}

/**
 * Genel Metin Kartı
 */
@Composable
private fun GeneralTextCard(
    section: AiSection,
    fontSizeSp: Float = 15f,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val verticalPadding = if (section.title.isNotBlank()) 4.dp else 0.dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = verticalPadding),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (section.title.isNotBlank()) {
            val cleanTitle = section.title.replace(Regex("""^#{1,6}\s*"""), "").trim()
            Text(
                text = cleanTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }
        SmartMathContent(
            content = section.content,
            fontSizeSp = fontSizeSp,
            textColor = textColor
        )
    }
}

/**
 * Satırları analiz edip alt başlık, sıralı liste, madde imi veya temiz paragraf olarak biçimlendirir.
 * Gereksiz madde imi (•) yığınını engeller, başlıkları şık bir alt başlık rozeti olarak render eder.
 */
@Composable
private fun RenderFormattedLines(
    content: String,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val normalized = normalizeSafeEscapes(content)
    val lines = normalized.lines()

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        lines.forEach { line ->
            val rawTrimmed = line.trim()
            if (rawTrimmed.isNotBlank()) {
                // Başlıktaki Markdown '#' karakterlerini temizle
                val cleanLine = rawTrimmed.replace(Regex("""^#{1,6}\s*"""), "").trim()
                if (cleanLine.isBlank()) return@forEach

                // 1. Madde imi tespiti (-, •, · veya tek * ardından boşluk - ** hariç!)
                var isRawBullet = false
                var bulletText = "•"
                var candidateText = cleanLine

                val bulletMatch = Regex("""^([-•·]|\*(?!\*))\s+(.*)$""").find(cleanLine)
                if (bulletMatch != null) {
                    isRawBullet = true
                    candidateText = bulletMatch.groupValues[2].trim()
                }

                // 2. Sıralı madde tespiti (1., 2., I., II., A., B., 1), a))
                var isOrderedList = false
                val orderMatch = Regex("""^((?:\d{1,2}|[IVXLCDM]+|[A-Ea-e])[\.\)])\s+(.*)$""").find(candidateText)
                if (orderMatch != null) {
                    isOrderedList = true
                    bulletText = orderMatch.groupValues[1]
                    candidateText = orderMatch.groupValues[2].trim()
                }

                // Ön ek temizliği (başta yanlışlıkla kalan nokta kalıntıları)
                candidateText = candidateText.removePrefix(".").trim()

                // 3. Akıllı Alt Başlık (SubHeader) Tespiti:
                // Bir satır şu durumlarda madde imi (•) DEĞİL, şık bir alt başlık / rozet olmalıdır:
                // - Satır iki nokta üst üste ile bitiyorsa (örn: "Türevin Limit Tanımı:", "**Türevin Limit Tanımı:**", "- Başlık:")
                //   ve makul bir başlık uzunluğundaysa (< 90 karakter)
                // - Veya satır "Kavram:", "Tanım:", "Formül:", "Adım \d+:", "Aşama \d+:" gibi başlık kalıbı içeriyorsa
                val stripped = candidateText
                    .removePrefix("**").removeSuffix("**")
                    .removePrefix("*").removeSuffix("*")
                    .trim()

                val endsWithColon = stripped.endsWith(":") || candidateText.endsWith(":**") || candidateText.endsWith(":*")
                val isShortTitle = stripped.length in 3..90
                val hasHeaderKeyword = stripped.startsWith("Aşama ", ignoreCase = true) ||
                        stripped.startsWith("Adım ", ignoreCase = true) ||
                        stripped.startsWith("Kavram", ignoreCase = true) ||
                        stripped.startsWith("Formül", ignoreCase = true) ||
                        stripped.startsWith("Özdeşlik", ignoreCase = true) ||
                        stripped.startsWith("Tanım", ignoreCase = true) ||
                        stripped.startsWith("Taktik", ignoreCase = true) ||
                        stripped.startsWith("Tuzak", ignoreCase = true) ||
                        stripped.startsWith("Hatırlatma", ignoreCase = true) ||
                        stripped.startsWith("Eşleştirme", ignoreCase = true) ||
                        stripped.startsWith("Sonuç", ignoreCase = true) ||
                        stripped.startsWith("Strateji", ignoreCase = true) ||
                        stripped.startsWith("Önemli", ignoreCase = true) ||
                        stripped.startsWith("Uyarı", ignoreCase = true)

                val isSubHeader = (endsWithColon && isShortTitle) || (hasHeaderKeyword && endsWithColon)

                if (isSubHeader) {
                    // Alt başlık: Madde imini kaldır, solunda primary bar olan şık alt başlık yap
                    val titleClean = candidateText.replace("**", "").replace("*", "").trim()
                    val formatted = formatMathText(titleClean)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 3.5.dp, height = 15.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = formatted,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else if (isOrderedList) {
                    val formatted = formatMathText(candidateText)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = bulletText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 1.dp)
                        )
                        Text(
                            text = formatted,
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 22.sp,
                            color = textColor,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else if (isRawBullet) {
                    val formatted = formatMathText(candidateText)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 6.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 1.dp)
                        )
                        Text(
                            text = formatted,
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 22.sp,
                            color = textColor,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    val formatted = formatMathText(candidateText)
                    Text(
                        text = formatted,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 22.sp,
                        color = textColor,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

/**
 * LaTeX sembollerini, üslü sayıları, alt indisleri, integralleri, kesirleri
 * ve toplam sembollerini temiz Unicode Matematik formatına dönüştürür.
 */
private val SAFE_NEWLINE_REGEX = Regex(
    """(?<!\\)\\n(?!(?:eq|e(?![a-zA-Z])|eg(?![a-zA-Z])|abla|otin|u(?![a-zA-Z])|ot(?![a-zA-Z])|atural|i(?![a-zA-Z])|exists|geq|leq|subseteq|supseteq|parallel|sim|cong|mid|earrow|warrow)(?![a-zA-Z]))"""
)

/**
 * Metindeki izole edilmiş escape karakterlerini (\n, \r) güvenli biçimde dönüştürür.
 * LaTeX komutlarını (\rightarrow, \right, \rho, \rangle, \neq, \nu, \nabla, \notin, \neg, \frac, \to vb.)
 * ASLA bozmaz.
 */
fun normalizeSafeEscapes(str: String): String {
    var text = str.replace("\r\n", "\n").replace("\r", "")
    text = text.replace("\\r\\n", "\n")
    text = text.replace(Regex("""\\r(?![a-zA-Z])"""), "")
    text = SAFE_NEWLINE_REGEX.replace(text, "\n")
    text = text.replace(Regex("""(?<=\s|^|[a-zA-Z0-9$])\\?ightarrow(?![a-zA-Z])"""), "\\rightarrow")
    text = text.replace(Regex("""(?<=\s|^|[a-zA-Z0-9$])\\?ongrightarrow(?![a-zA-Z])"""), "\\longrightarrow")
    text = text.replace(Regex("""(?<=\s|^|[a-zA-Z0-9$])\\?ightleftharpoons(?![a-zA-Z])"""), "\\rightleftharpoons")
    return text
}

private val THINK_REGEX = Regex("""<(think|thought)>[\s\S]*?</\1>""")
private val LEFT_REGEX = Regex("""\\left(?![a-zA-Z])\s*""")
private val RIGHT_REGEX = Regex("""\\right(?![a-zA-Z])\s*""")
private val TEXT_CMD_REGEX = Regex("""\\(text|mathbf|mathit|mathrm|textbf|textit|mathbb|bold|operatorname)\{([^}]+)\}""")
private val FRAC_REGEX = Regex("""\\?(?:frac|dfrac|cfrac)\{([^}]+)\}\{([^}]+)\}""")
private val SQRT_REGEX = Regex("""\\sqrt\{([^}]+)\}""")
private val N_SQRT_REGEX = Regex("""\\sqrt\[([^\]]+)\]\{([^}]+)\}""")

private val ARROW_REPLACEMENTS = listOf(
    Regex("""[ \t]*\\implies(?![a-zA-Z])[ \t]*""") to " ⇒ ",
    Regex("""[ \t]*\\impliedby(?![a-zA-Z])[ \t]*""") to " ⇐ ",
    Regex("""[ \t]*\\iff(?![a-zA-Z])[ \t]*""") to " ⇔ ",
    Regex("""[ \t]*\\?(?:longrightarrow|ongrightarrow)(?![a-zA-Z])[ \t]*""") to " ⟶ ",
    Regex("""[ \t]*\\longleftarrow(?![a-zA-Z])[ \t]*""") to " ⟵ ",
    Regex("""[ \t]*\\?(?:rightleftharpoons|leftrightharpoons|ightleftharpoons)(?![a-zA-Z])[ \t]*""") to " ⇌ ",
    Regex("""[ \t]*\\leftrightarrows(?![a-zA-Z])[ \t]*""") to " ⇄ ",
    Regex("""[ \t]*(?:\\(?:to|rightarrow)|\\?ightarrow)(?![a-zA-Z])[ \t]*""") to " → ",
    Regex("""[ \t]*\\leftarrow(?![a-zA-Z])[ \t]*""") to " ← ",
    Regex("""[ \t]*\\Rightarrow(?![a-zA-Z])[ \t]*""") to " ⇒ ",
    Regex("""[ \t]*\\Leftarrow(?![a-zA-Z])[ \t]*""") to " ⇐ ",
    Regex("""[ \t]*\\Leftrightarrow(?![a-zA-Z])[ \t]*""") to " ⇔ ",
    Regex("""[ \t]*\\leftrightarrow(?![a-zA-Z])[ \t]*""") to " ↔ "
)

private val REGEX_REPLACEMENTS = listOf(
    Regex("""\\(leq|le)(?![a-zA-Z])""") to "≤",
    Regex("""\\(geq|ge)(?![a-zA-Z])""") to "≥",
    Regex("""\\(neq|ne)(?![a-zA-Z])""") to "≠",
    Regex("""(?<=\s|^|[a-zA-Z0-9\)])\\?neq(?![a-zA-Z])""") to "≠",
    Regex("""(?<=\s|^|[a-zA-Z0-9\)])eq\s*0(?![a-zA-Z])""") to "≠ 0",
    Regex("""\\(approx)(?![a-zA-Z])""") to "≈",
    Regex("""\\(sim)(?![a-zA-Z])""") to "∼",
    Regex("""\\(equiv)(?![a-zA-Z])""") to "≡",
    Regex("""\\(cong)(?![a-zA-Z])""") to "≅",
    Regex("""\\(propto)(?![a-zA-Z])""") to "∝",
    Regex("""\\(times)(?![a-zA-Z])""") to "×",
    Regex("""\\(cdot)(?![a-zA-Z])""") to "·",
    Regex("""\\(div)(?![a-zA-Z])""") to "÷",
    Regex("""\\(pm)(?![a-zA-Z])""") to "±",
    Regex("""\\(mp)(?![a-zA-Z])""") to "∓",
    Regex("""\\(infty)(?![a-zA-Z])""") to "∞",
    Regex("""\\(degree|circ)(?![a-zA-Z])""") to "°",
    Regex("""\\(int)(?![a-zA-Z])""") to "∫",
    Regex("""\\(iint)(?![a-zA-Z])""") to "∬",
    Regex("""\\(iiint)(?![a-zA-Z])""") to "∭",
    Regex("""\\(oint)(?![a-zA-Z])""") to "∮",
    Regex("""\\(sum)(?![a-zA-Z])""") to "∑",
    Regex("""\\(prod)(?![a-zA-Z])""") to "∏",
    Regex("""\\(lim)(?![a-zA-Z])""") to "lim",
    Regex("""\\(alpha)(?![a-zA-Z])""") to "α",
    Regex("""\\(beta)(?![a-zA-Z])""") to "β",
    Regex("""\\(gamma)(?![a-zA-Z])""") to "γ",
    Regex("""\\(Gamma)(?![a-zA-Z])""") to "Γ",
    Regex("""\\(delta)(?![a-zA-Z])""") to "δ",
    Regex("""\\(Delta)(?![a-zA-Z])""") to "Δ",
    Regex("""\\(epsilon|varepsilon)(?![a-zA-Z])""") to "ε",
    Regex("""\\(zeta)(?![a-zA-Z])""") to "ζ",
    Regex("""\\(eta)(?![a-zA-Z])""") to "η",
    Regex("""\\(theta|vartheta)(?![a-zA-Z])""") to "θ",
    Regex("""\\(Theta)(?![a-zA-Z])""") to "Θ",
    Regex("""\\(iota)(?![a-zA-Z])""") to "ι",
    Regex("""\\(kappa)(?![a-zA-Z])""") to "κ",
    Regex("""\\(lambda)(?![a-zA-Z])""") to "λ",
    Regex("""\\(Lambda)(?![a-zA-Z])""") to "Λ",
    Regex("""\\(mu)(?![a-zA-Z])""") to "μ",
    Regex("""\\(nu)(?![a-zA-Z])""") to "ν",
    Regex("""\\(xi)(?![a-zA-Z])""") to "ξ",
    Regex("""\\(Xi)(?![a-zA-Z])""") to "Ξ",
    Regex("""\\(omicron)(?![a-zA-Z])""") to "ο",
    Regex("""\\(pi|varpi)(?![a-zA-Z])""") to "π",
    Regex("""\\(Pi)(?![a-zA-Z])""") to "Π",
    Regex("""\\(rho|varrho)(?![a-zA-Z])""") to "ρ",
    Regex("""\\(sigma|varsigma)(?![a-zA-Z])""") to "σ",
    Regex("""\\(Sigma)(?![a-zA-Z])""") to "Σ",
    Regex("""\\(tau)(?![a-zA-Z])""") to "τ",
    Regex("""\\(upsilon)(?![a-zA-Z])""") to "υ",
    Regex("""\\(Upsilon)(?![a-zA-Z])""") to "Υ",
    Regex("""\\(phi|varphi)(?![a-zA-Z])""") to "ϕ",
    Regex("""\\(Phi)(?![a-zA-Z])""") to "Φ",
    Regex("""\\(chi)(?![a-zA-Z])""") to "χ",
    Regex("""\\(psi)(?![a-zA-Z])""") to "ψ",
    Regex("""\\(Psi)(?![a-zA-Z])""") to "Ψ",
    Regex("""\\(omega)(?![a-zA-Z])""") to "ω",
    Regex("""\\(Omega)(?![a-zA-Z])""") to "Ω",
    // LLM kaynaklı olası büyük Yunan harfi kaçışları (Latin karşılıkları)
    Regex("""\\Alpha(?![a-zA-Z])""") to "A",
    Regex("""\\Beta(?![a-zA-Z])""") to "B",
    Regex("""\\Epsilon(?![a-zA-Z])""") to "E",
    Regex("""\\Zeta(?![a-zA-Z])""") to "Z",
    Regex("""\\Eta(?![a-zA-Z])""") to "H",
    Regex("""\\Iota(?![a-zA-Z])""") to "I",
    Regex("""\\Kappa(?![a-zA-Z])""") to "K",
    Regex("""\\Mu(?![a-zA-Z])""") to "M",
    Regex("""\\Nu(?![a-zA-Z])""") to "N",
    Regex("""\\Omicron(?![a-zA-Z])""") to "O",
    Regex("""\\Rho(?![a-zA-Z])""") to "P",
    Regex("""\\Tau(?![a-zA-Z])""") to "T",
    Regex("""\\Chi(?![a-zA-Z])""") to "X",
    // Biyoloji Cinsiyet Simgeleri
    Regex("""\\(male|mars)(?![a-zA-Z])""") to "♂",
    Regex("""\\(female|venus)(?![a-zA-Z])""") to "♀",
    // Mantık ve Matematiksel Çıkarım
    Regex("""\\(therefore)(?![a-zA-Z])""") to "∴",
    Regex("""\\(because)(?![a-zA-Z])""") to "∵",
    Regex("""\\(land|wedge)(?![a-zA-Z])""") to "∧",
    Regex("""\\(lor|vee)(?![a-zA-Z])""") to "∨",
    Regex("""\\(neg|lnot)(?![a-zA-Z])""") to "¬",
    Regex("""\\(oplus)(?![a-zA-Z])""") to "⊕",
    Regex("""\\(otimes)(?![a-zA-Z])""") to "⊗",
    // Karşılaştırma, Kümeler ve Bağıntılar
    Regex("""\\(gg)(?![a-zA-Z])""") to "≫",
    Regex("""\\(ll)(?![a-zA-Z])""") to "≪",
    Regex("""\\(supset)(?![a-zA-Z])""") to "⊃",
    Regex("""\\(supseteq)(?![a-zA-Z])""") to "⊇",
    Regex("""\\(setminus)(?![a-zA-Z])""") to "∖",
    // Fizik ve Matematik Diğer Semboller
    Regex("""\\(ell)(?![a-zA-Z])""") to "ℓ",
    Regex("""\\(prime)(?![a-zA-Z])""") to "′",
    Regex("""\\(partial)(?![a-zA-Z])""") to "∂",
    Regex("""\\(nabla)(?![a-zA-Z])""") to "∇",
    Regex("""\\(hbar)(?![a-zA-Z])""") to "ℏ",
    Regex("""\\(parallel)(?![a-zA-Z])""") to "∥",
    Regex("""\\(perp)(?![a-zA-Z])""") to "⊥",
    Regex("""\\(angle|measuredangle)(?![a-zA-Z])""") to "∠",
    Regex("""\\AA(?![a-zA-Z])""") to "Å",
    Regex("""\\(notin)(?![a-zA-Z])""") to "∉",
    Regex("""\\(in)(?![a-zA-Z])""") to "∈",
    Regex("""\\(subset)(?![a-zA-Z])""") to "⊂",
    Regex("""\\(subseteq)(?![a-zA-Z])""") to "⊆",
    Regex("""\\(cup)(?![a-zA-Z])""") to "∪",
    Regex("""\\(cap)(?![a-zA-Z])""") to "∩",
    Regex("""\\(emptyset)(?![a-zA-Z])""") to "∅",
    Regex("""\\(forall)(?![a-zA-Z])""") to "∀",
    Regex("""\\(exists)(?![a-zA-Z])""") to "∃",
    Regex("""\\uparrow(?![a-zA-Z])""") to "↑",
    Regex("""\\downarrow(?![a-zA-Z])""") to "↓"
)

private val EXPONENT_BRACE_REGEX = Regex("""\^\{([^}]+)\}""")
private val SINGLE_EXPONENT_REGEX = Regex("""\^([0-9a-zA-Z\+\-\*]+)""")
private val SUBSCRIPT_BRACE_REGEX = Regex("""_\{([^}]+)\}""")
private val SINGLE_SUBSCRIPT_REGEX = Regex("""_([0-9a-zA-Z\+\-]+)""")

private val SUPERSCRIPT_MAP = mapOf(
    '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
    '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
    '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾',
    'a' to 'ᵃ', 'b' to 'ᵇ', 'c' to 'ᶜ', 'd' to 'ᵈ', 'e' to 'ᵉ',
    'f' to 'ᶠ', 'g' to 'ᵍ', 'h' to 'ʰ', 'i' to 'ⁱ', 'j' to 'ʲ',
    'k' to 'ᵏ', 'l' to 'ˡ', 'm' to 'ᵐ', 'n' to 'ⁿ', 'o' to 'ᵒ',
    'p' to 'ᵖ', 'r' to 'ʳ', 's' to 'ˢ', 't' to 'ᵗ', 'u' to 'ᵘ',
    'v' to 'ᵛ', 'w' to 'ʷ', 'x' to 'ˣ', 'y' to 'ʸ', 'z' to 'ᶻ',
    // Büyük harf üst simgeler (Genetik & İyon yükleri: Xᴴ, Xᴿ, Iᴬ, Iᴮ, vb.)
    'A' to 'ᴬ', 'B' to 'ᴮ', 'C' to 'ᶜ', 'D' to 'ᴰ', 'E' to 'ᴱ',
    'F' to 'ᶠ', 'G' to 'ᴳ', 'H' to 'ᴴ', 'I' to 'ᴵ', 'J' to 'ᴶ',
    'K' to 'ᴷ', 'L' to 'ᴸ', 'M' to 'ᴹ', 'N' to 'ᴺ', 'O' to 'ᴼ',
    'P' to 'ᴾ', 'R' to 'ᴿ', 'S' to 'ˢ', 'T' to 'ᵀ', 'U' to 'ᵁ',
    'V' to 'ⱽ', 'W' to 'ᵂ', 'X' to 'ˣ', 'Y' to 'ʸ', 'Z' to 'ᶻ'
)

private val SUBSCRIPT_MAP = mapOf(
    '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
    '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
    '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎',
    'a' to 'ₐ', 'e' to 'ₑ', 'h' to 'ₕ', 'i' to 'ᵢ', 'j' to 'ⱼ',
    'k' to 'ₖ', 'l' to 'ˡ', 'm' to 'ₘ', 'n' to 'ₙ', 'o' to 'ₒ',
    'p' to 'ₚ', 'r' to 'ᵣ', 's' to 'ₛ', 't' to 'ₜ', 'u' to 'ᵤ',
    'v' to 'ᵥ', 'x' to 'ₓ'
)

/**
 * Giriş metnindeki LaTeX ve matematiksel sembolleri UTF-8 Unicode matematik sembollerine dönüştürür.
 */
fun formatMathSymbols(raw: String): String {
    var text = raw

    // 0. Newline kaçışlarını, çoklu ters eğik çizgileri ve AI Düşünce (<think>...</think>) etiketlerini temizle
    text = Regex("""(?<![a-zA-Z])\\*dfac(?![a-zA-Z])""").replace(text, "\\dfrac")
    text = Regex("""\\{2,}(?=[a-zA-Z])""").replace(text, "\\")
    // LaTeX komutları (\nu, \neq, \nabla, \notin, \neg, \rho vb.) ile çakışmayan gerçek \n ve \r kaçışlarını dönüştür
    text = Regex("""\\n(?!(?:u|eq|e|abla|otin|ot|atural|eg|i)(?![a-zA-Z]))""").replace(text, "\n")
        .replace("\r", "")
        .replace(Regex("""\\r(?![a-zA-Z])"""), "")
    text = text.replace(THINK_REGEX, "")

    // 1. Türkçe Ondalık Virgül (Örn: 9{,}3 -> 9,3 veya 4{,}1 -> 4,1)
    text = Regex("""(\d+)\{,\}(\d+)""").replace(text, "$1,$2")
    text = Regex("""(\d+)\{\.\}(\d+)""").replace(text, "$1.$2")
    text = Regex("""(?<=\d)\{,\}(?=\d)""").replace(text, ",")
    text = text.replace("{,}", ",")

    // 2. LaTeX Bloklarını, Parantez Kaçışlarını ve Sınır Belirteçlerini Temizle
    text = text.replace("\\left.", "").replace("\\right.", "")
    text = text.replace("$$", "").replace("$", "")
    text = text.replace("\\[", "").replace("\\]", "")
    text = text.replace("\\(", "").replace("\\)", "")
    text = text.replace(LEFT_REGEX, "")
    text = text.replace(RIGHT_REGEX, "")

    // Bozulmuş \right kaynaklı "ight" veya "[(0 / 0)ight]" artıklarını düzelt
    text = Regex("""\[\s*(?:\(?\s*0\s*/\s*0\s*\)?|\\?(?:frac|dfrac|cfrac)\{0\}\{0\})\s*(?:\\?right|ight)?\s*\]""").replace(text, "[0/0]")
    text = Regex("""(?<=\s|^|[a-zA-Z0-9\(\[\{\+\-\*/])\\?ight(?=[\]\)\}\s,\.])""").replace(text, "")
    text = Regex("""\\?ight([\]\)\}\.\,])""").replace(text, "$1")

    // 2.1. LaTeX Metin ve Yazı Tipi Sarmalayıcılarını Çöz (\text{...}, \mathbf{...}) - Reaksiyon oklarından ve formüllerden ÖNCE!
    while (TEXT_CMD_REGEX.containsMatchIn(text)) {
        text = TEXT_CMD_REGEX.replace(text) { it.groupValues[2] }
    }

    // 3. Sadeleştirme / İptal (Cancel): \cancel{x} -> ~~x~~
    text = Regex("""\\(?:cancel|bcancel|xcancel)\{([^}]+)\}""").replace(text, "~~$1~~")

    // 4. Kombinasyon: \binom{n}{k} -> C(n, k)
    text = Regex("""\\binom\{([^}]+)\}\{([^}]+)\}""").replace(text, "C($1, $2)")

    // 5. Vektörler ve Yönlü Doğrular: \vec{F} -> F⃗, \overrightarrow{AB} -> AB→
    text = Regex("""\\vec\{([A-Za-z])\}""").replace(text, "$1\u20D7")
    text = Regex("""\\overrightarrow\{([^}]+)\}""").replace(text, "$1→")

    // 6. Kimyasal Fiziksel Haller: _{k}, _{(suda)}, _{(aq)} (Genel alt indisten ÖNCE çalışmalı!)
    text = Regex("""_\{?\s*\(?\s*(?:\\text\{)?(k|s|g|aq|suda)(?:\})?\s*\)?\s*\}?""").replace(text) {
        "(${it.groupValues[1]})"
    }

    // 7. Kimyasal & Biyokimyasal Reaksiyon Okları (Ok üstü enzim, sıcaklık, katalizör gösterimi)
    text = Regex("""\\xrightarrow(?:\[([^\]]*)\])?\{([^}]*)\}""").replace(text) { match ->
        val below = match.groupValues[1].trim()
        val above = match.groupValues[2].trim()
        val label = listOf(above, below).filter { it.isNotBlank() }.joinToString(" / ")
        if (label.isNotBlank()) " ───[$label]───> " else " → "
    }
    text = Regex("""\\xleftarrow(?:\[([^\]]*)\])?\{([^}]*)\}""").replace(text) { match ->
        val below = match.groupValues[1].trim()
        val above = match.groupValues[2].trim()
        val label = listOf(above, below).filter { it.isNotBlank() }.joinToString(" / ")
        if (label.isNotBlank()) " <───[$label]─── " else " ← "
    }
    text = Regex("""\\x(?:rightleftharpoons|leftrightharpoons)(?:\[([^\]]*)\])?\{([^}]*)\}""").replace(text) { match ->
        val below = match.groupValues[1].trim()
        val above = match.groupValues[2].trim()
        val label = listOf(above, below).filter { it.isNotBlank() }.joinToString(" / ")
        if (label.isNotBlank()) " ⇌ [$label] ⇌ " else " ⇌ "
    }
    text = Regex("""\\(?:overset|stackrel)\{([^}]+)\}\{\\?(?:rightarrow|longrightarrow|to)\}""").replace(text) {
        " ───[${it.groupValues[1].trim()}]───> "
    }
    text = Regex("""\\(?:overset|stackrel)\{([^}]+)\}\{\\?(?:rightleftharpoons|leftrightharpoons)\}""").replace(text) {
        " ⇌ [${it.groupValues[1].trim()}] ⇌ "
    }
    text = Regex("""\\underset\{([^}]+)\}\{\\?(?:rightarrow|longrightarrow|to)\}""").replace(text) {
        " ───[${it.groupValues[1].trim()}]───> "
    }

    // 8. mhchem \ce{...} Çözümleyici
    text = Regex("""\\ce\{([^}]+)\}""").replace(text) { match ->
        var inner = match.groupValues[1]
        inner = Regex("""->\[([^\]]*)\](?:\[([^\]]*)\])?""").replace(inner) { m ->
            val l = listOf(m.groupValues[1], m.groupValues[2]).filter { s -> s.isNotBlank() }.joinToString(" / ")
            if (l.isNotBlank()) " ───[$l]───> " else " → "
        }
        inner = Regex("""<=>\[([^\]]*)\](?:\[([^\]]*)\])?""").replace(inner) { m ->
            val l = listOf(m.groupValues[1], m.groupValues[2]).filter { s -> s.isNotBlank() }.joinToString(" / ")
            if (l.isNotBlank()) " ⇌ [$l] ⇌ " else " ⇌ "
        }
        inner.replace("<=>", " ⇌ ").replace("->", " → ").replace("<-", " ← ")
            .replace(Regex("""(?<=[A-Za-z0-9\)])\s*\^"""), " ↑")
            .replace(Regex("""(?<=[A-Za-z0-9\)])\s*v"""), " ↓")
    }

    // 9. LaTeX Metin ve Yazı Tipi Sarmalayıcılarını Çöz: \text{...}, \mathbf{...}, vb.
    while (TEXT_CMD_REGEX.containsMatchIn(text)) {
        text = TEXT_CMD_REGEX.replace(text) { it.groupValues[2] }
    }

    // 10. LaTeX Kesirleri: \frac{a}{b}, \dfrac{a}{b}, \cfrac{a}{b}, \tfrac{a}{b}, \dfac{a}{b} -> (a / b) (iç içe ve boşluklu kesirleri de çözer)
    text = convertFractionsToReadable(text)

    // 10.1. Kesir sadeleştirmesi sonrası [0/0] kalıplarını güvene al
    text = Regex("""\[\s*\(?\s*0\s*/\s*0\s*\)?\s*\]""").replace(text, "[0/0]")

    // 11. LaTeX Kökleri: \sqrt{x} -> √(x), \sqrt[n]{x} -> ⁿ√(x)
    text = SQRT_REGEX.replace(text) { "√(${it.groupValues[1]})" }
    text = N_SQRT_REGEX.replace(text) { "${toSuperscript(it.groupValues[1])}√(${it.groupValues[2]})" }

    // 12. Mantıksal ve Yön Okları
    for ((pattern, replacement) in ARROW_REPLACEMENTS) {
        text = text.replace(pattern, replacement)
    }

    // 12.1. LaTeX Limit İfadeleri: \lim_{x → a} -> lim(x → a)
    text = Regex("""\\lim_\{([^}]+)\}""").replace(text) { match ->
        "lim(${match.groupValues[1]})"
    }
    text = Regex("""\\lim_([0-9a-zA-Z]+)""").replace(text) { match ->
        "lim(${match.groupValues[1]})"
    }

    // 13. Soyağacı (Kalıtım) ve Geometrik Şekiller
    text = text.replace(Regex("""\\(square|Box)(?![a-zA-Z])"""), "□")
    text = text.replace(Regex("""\\(blacksquare)(?![a-zA-Z])"""), "■")
    text = text.replace(Regex("""\\(bigcirc|Circle)(?![a-zA-Z])"""), "○")
    text = text.replace(Regex("""\\(bullet|CIRCLE|blackcircle)(?![a-zA-Z])"""), "●")
    text = text.replace(Regex("""\\(odot|bigodot)(?![a-zA-Z])"""), "⊙")
    text = text.replace(Regex("""\\(triangle|bigtriangleup)(?![a-zA-Z])"""), "△")
    text = text.replace(Regex("""\\(blacktriangle)(?![a-zA-Z])"""), "▲")
    text = text.replace(Regex("""\\(lozenge|diamond)(?![a-zA-Z])"""), "◇")
    text = text.replace(Regex("""\\(blacklozenge)(?![a-zA-Z])"""), "◆")
    text = text.replace(Regex("""\\(ast|star)(?![a-zA-Z])"""), "★")

    // 14. Kelime Sınırı Güvenliğine Sahip LaTeX Sembolleri (Greek, Math, Units)
    for ((pattern, replacement) in REGEX_REPLACEMENTS) {
        text = text.replace(pattern, replacement)
    }

    // 15. Sayı Kümeleri (Blackboard Bold)
    text = text.replace(Regex("""\\mathbb\{R\}"""), "ℝ")
    text = text.replace(Regex("""\\mathbb\{Z\}"""), "ℤ")
    text = text.replace(Regex("""\\mathbb\{N\}"""), "ℕ")
    text = text.replace(Regex("""\\mathbb\{Q\}"""), "ℚ")
    text = text.replace(Regex("""\\mathbb\{C\}"""), "ℂ")

    // 16. Üslü İfadeler (Genetik X^h, X^H, X^{h}'yi ve matematik)
    text = EXPONENT_BRACE_REGEX.replace(text) { match ->
        toSuperscript(match.groupValues[1])
    }
    text = SINGLE_EXPONENT_REGEX.replace(text) { match ->
        toSuperscript(match.groupValues[1])
    }

    // 17. Alt İndisler (Kimya ve matematik indisleri)
    text = SUBSCRIPT_BRACE_REGEX.replace(text) { match ->
        toSubscript(match.groupValues[1])
    }
    text = SINGLE_SUBSCRIPT_REGEX.replace(text) { match ->
        toSubscript(match.groupValues[1])
    }

    // 18. LaTeX Boşluk Komutları
    text = text.replace(Regex("""\\(quad|qquad)"""), "  ")
    text = text.replace(Regex("""\\([,;:!])"""), " ")

    return text
}

/**
 * LaTeX kesir komutlarını (\dfrac, \frac, \cfrac, \tfrac, \dfac) iç içe küme parantezleri
 * veya tekil argümanları destekleyecek şekilde okunaklı "(pay / payda)" formatına dönüştürür.
 * Ekranda hiçbir zaman çiğ "dfac" veya "\dfrac" metni bırakmaz.
 */
private fun convertFractionsToReadable(raw: String): String {
    var text = raw
    val fracCmdRegex = Regex("""\\?(?:frac|dfrac|cfrac|tfrac|dfac)\b""")

    var iterations = 0
    while (iterations < 25 && fracCmdRegex.containsMatchIn(text)) {
        val match = fracCmdRegex.find(text) ?: break
        val cmdStart = match.range.first
        val cmdEnd = match.range.last + 1

        val firstArg = extractBalancedBraceForFallback(text, cmdEnd)
        if (firstArg != null) {
            val secondArg = extractBalancedBraceForFallback(text, firstArg.second)
            if (secondArg != null) {
                val num = firstArg.first.removeSurrounding("{", "}").trim()
                val den = secondArg.first.removeSurrounding("{", "}").trim()
                val replacement = "($num / $den)"
                text = text.substring(0, cmdStart) + replacement + text.substring(secondArg.second)
                iterations++
                continue
            }
        }

        // Süslü parantezsiz tekil token eşleşmesi (Örn: \frac 1 2 veya \dfrac a b)
        val singleTokenRegex = Regex("""^\\?(?:frac|dfrac|cfrac|tfrac|dfac)\s+([0-9a-zA-Z])\s+([0-9a-zA-Z])""")
        val singleMatch = singleTokenRegex.find(text.substring(cmdStart))
        if (singleMatch != null) {
            val replacement = "(${singleMatch.groupValues[1]} / ${singleMatch.groupValues[2]})"
            text = text.substring(0, cmdStart) + replacement + text.substring(cmdStart + singleMatch.range.last + 1)
            iterations++
            continue
        }

        // Argümansız arta kalan komut adını sil (ham yazı olarak ekranda kalmasın)
        text = text.substring(0, cmdStart) + text.substring(cmdEnd)
        iterations++
    }

    // Her ihtimale karşı arta kalan yalın \dfrac veya dfac sözcüklerini temizle
    text = text.replace(Regex("""\\?(?:frac|dfrac|cfrac|tfrac|dfac)\b"""), "")

    return text
}

private fun extractBalancedBraceForFallback(text: String, startIdx: Int): Pair<String, Int>? {
    var idx = startIdx
    while (idx < text.length && text[idx].isWhitespace()) idx++
    if (idx >= text.length || text[idx] != '{') return null
    val openIdx = idx
    var depth = 1
    idx++
    while (idx < text.length && depth > 0) {
        if (text[idx] == '\\' && idx + 1 < text.length) {
            idx += 2
            continue
        }
        if (text[idx] == '{') depth++
        else if (text[idx] == '}') depth--
        idx++
    }
    return if (depth == 0) Pair(text.substring(openIdx, idx), idx) else null
}

/**
 * LaTeX sembollerini ve Markdown kalın/italik/üstüçizili yapılarını temiz AnnotatedString formatına dönüştürür.
 */
fun formatMathText(raw: String): AnnotatedString {
    val mathFormatted = formatMathSymbols(raw)
    return parseMarkdownToAnnotatedString(mathFormatted)
}

/**
 * Karakterleri Unicode Üslü (Superscript) karakterlere çevirir.
 */
private fun toSuperscript(input: String): String {
    return input.map { SUPERSCRIPT_MAP[it] ?: it }.joinToString("")
}

/**
 * Karakterleri Unicode Alt İndis (Subscript) karakterlere çevirir.
 */
private fun toSubscript(input: String): String {
    return input.map { SUBSCRIPT_MAP[it] ?: it }.joinToString("")
}

/**
 * Markdown kalın (**...**), italik (*...*) ve üstü çizili (~~...~~) işaretlerini AnnotatedString'e dönüştürür.
 */
private fun parseMarkdownToAnnotatedString(text: String): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        val length = text.length

        while (cursor < length) {
            // Strikethrough: ~~...~~
            if (cursor + 1 < length && text[cursor] == '~' && text[cursor + 1] == '~') {
                val endStrike = text.indexOf("~~", cursor + 2)
                if (endStrike != -1) {
                    val strikeContent = text.substring(cursor + 2, endStrike)
                    pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                    append(strikeContent)
                    pop()
                    cursor = endStrike + 2
                    continue
                }
            }

            // Bold: **...**
            if (cursor + 1 < length && text[cursor] == '*' && text[cursor + 1] == '*') {
                val endBold = text.indexOf("**", cursor + 2)
                if (endBold != -1) {
                    val boldContent = text.substring(cursor + 2, endBold)
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(boldContent)
                    pop()
                    cursor = endBold + 2
                    continue
                }
            }

            // Italic: *...*
            if (text[cursor] == '*') {
                val endItalic = text.indexOf('*', cursor + 1)
                if (endItalic != -1) {
                    val italicContent = text.substring(cursor + 1, endItalic)
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(italicContent)
                    pop()
                    cursor = endItalic + 1
                    continue
                }
            }

            append(text[cursor])
            cursor++
        }
    }
}

