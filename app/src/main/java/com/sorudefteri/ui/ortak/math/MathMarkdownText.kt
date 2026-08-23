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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Numbers
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Yapay Zeka çıktılarında yer alan LaTeX formülleri, üslü/köklü/kesirli ifadeler,
 * integraller, toplam sembolleri ve Markdown formatını göze hitap eden zengin
 * kartlar ve tipografi ile renderlayan bileşen.
 */
@Composable
fun MathMarkdownText(
    text: String,
    modifier: Modifier = Modifier
) {
    if (text.isBlank()) return

    val sections = remember(text) { parseAiExplanationSections(text) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        sections.forEach { section ->
            when (section.type) {
                SectionType.CORRECT_ANSWER -> CorrectAnswerCard(section)
                SectionType.FORMULAS -> FormulasCard(section)
                SectionType.STEPS -> StepsCard(section)
                SectionType.TIPS -> TipsCard(section)
                SectionType.GENERAL -> GeneralTextCard(section)
            }
        }
    }
}

private enum class SectionType {
    CORRECT_ANSWER,
    FORMULAS,
    STEPS,
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
 * AI metnini yapılandırılmış bölümlere (Doğru Cevap, Formüller, Adımlar, İpuçları) ayırır.
 */
private fun parseAiExplanationSections(rawText: String): List<AiSection> {
    val cleanText = rawText.trim()
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
        when {
            trimmed.contains("🎯") || trimmed.contains("Doğru Cevap", ignoreCase = true) && trimmed.startsWith("#") || (trimmed.startsWith("**Doğru Cevap") || trimmed.startsWith("🎯 **Doğru Cevap")) -> {
                commitCurrentSection()
                currentType = SectionType.CORRECT_ANSWER
                currentTitle = "🎯 Doğru Cevap"
                val afterTitle = trimmed.substringAfter(":", "").trim()
                if (afterTitle.isNotBlank()) {
                    currentContentLines.add(afterTitle)
                }
            }
            trimmed.contains("📚") || trimmed.contains("Temel Kavramlar", ignoreCase = true) || trimmed.contains("Formüller", ignoreCase = true) -> {
                commitCurrentSection()
                currentType = SectionType.FORMULAS
                currentTitle = "📚 Temel Kavramlar & Formüller"
            }
            trimmed.contains("📝") || trimmed.contains("Adım Adım", ignoreCase = true) || trimmed.contains("Ayrıntılı Çözüm", ignoreCase = true) -> {
                commitCurrentSection()
                currentType = SectionType.STEPS
                currentTitle = "📝 Adım Adım Çözüm"
            }
            trimmed.contains("💡") || trimmed.contains("Püf Noktaları", ignoreCase = true) || trimmed.contains("Sınav Taktikleri", ignoreCase = true) || trimmed.contains("İpuçları", ignoreCase = true) -> {
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
private fun CorrectAnswerCard(section: AiSection) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF2E7D32).copy(alpha = 0.12f),
        border = BorderStroke(1.dp, Color(0xFF2E7D32).copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
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

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Doğru Cevap & Sonuç",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32)
                )
                Text(
                    text = formatMathText(section.content.ifBlank { section.title }),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * 📚 Temel Kavramlar & Formüller Kartı
 */
@Composable
private fun FormulasCard(section: AiSection) {
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

            RenderFormattedLines(section.content)
        }
    }
}

/**
 * 📝 Adım Adım Çözüm Kartı
 */
@Composable
private fun StepsCard(section: AiSection) {
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
                    imageVector = Icons.Default.Numbers,
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

            RenderFormattedLines(section.content)
        }
    }
}

/**
 * 💡 Püf Noktaları Kartı
 */
@Composable
private fun TipsCard(section: AiSection) {
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

            RenderFormattedLines(section.content)
        }
    }
}

/**
 * Genel Metin Kartı
 */
@Composable
private fun GeneralTextCard(section: AiSection) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (section.title.isNotBlank()) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        RenderFormattedLines(section.content)
    }
}

/**
 * Satırları analiz edip liste, başlık, formül veya düz metin olarak biçimlendirir.
 */
@Composable
private fun RenderFormattedLines(content: String) {
    val lines = content.lines()

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        lines.forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isNotBlank()) {
                val isListItem = trimmed.startsWith("-") || trimmed.startsWith("•") || trimmed.startsWith("*")
                val isNumberedItem = trimmed.firstOrNull()?.isDigit() == true && (trimmed.getOrNull(1) == '.' || trimmed.getOrNull(2) == '.')

                val formatted = formatMathText(trimmed.removePrefix("-").removePrefix("•").removePrefix("*").trim())

                if (isListItem || isNumberedItem) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (isNumberedItem) trimmed.substringBefore(".").trim() + "." else "•",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 1.dp)
                        )
                        Text(
                            text = formatted,
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 22.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    Text(
                        text = formatted,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

/**
 * LaTeX sembollerini, üslü sayıları, alt indisleri, integralleri,
 * toplam sembollerini ve Markdown kalın/italik yapılarını temiz Unicode Matematik formatına dönüştürür.
 */
fun formatMathText(raw: String): AnnotatedString {
    var text = raw

    // 1. LaTeX bloklarını ve inline sembolleri temizle/dönüştür
    text = text.replace("$$", "").replace("$", "")
    text = text.replace("\\[", "").replace("\\]", "")
    text = text.replace("\\(", "").replace("\\)", "")

    // 2. LaTeX Kesirleri: \frac{a}{b} -> (a / b)
    val fracRegex = Regex("""\\frac\{([^}]+)\}\{([^}]+)\}""")
    text = fracRegex.replace(text) { match ->
        val num = match.groupValues[1]
        val den = match.groupValues[2]
        "($num / $den)"
    }

    // 3. LaTeX Kökleri: \sqrt{x} -> √(x), \sqrt[n]{x} -> ⁿ√(x)
    val sqrtRegex = Regex("""\\sqrt\{([^}]+)\}""")
    text = sqrtRegex.replace(text) { "√(${it.groupValues[1]})" }
    val nSqrtRegex = Regex("""\\sqrt\[([^\]]+)\]\{([^}]+)\}""")
    text = nSqrtRegex.replace(text) { "${toSuperscript(it.groupValues[1])}√(${it.groupValues[2]})" }

    // 4. LaTeX Sembolleri ve Matematiksel Operatörler
    val replacements = mapOf(
        "\\int" to "∫",
        "\\iint" to "∬",
        "\\iiint" to "∭",
        "\\oint" to "∮",
        "\\sum" to "∑",
        "\\prod" to "∏",
        "\\lim" to "lim",
        "\\infty" to "∞",
        "\\pm" to "±",
        "\\mp" to "∓",
        "\\times" to "×",
        "\\cdot" to "·",
        "\\div" to "÷",
        "\\leq" to "≤",
        "\\le" to "≤",
        "\\geq" to "≥",
        "\\ge" to "≥",
        "\\neq" to "≠",
        "\\ne" to "≠",
        "\\approx" to "≈",
        "\\sim" to "∼",
        "\\equiv" to "≡",
        "\\alpha" to "α",
        "\\beta" to "β",
        "\\gamma" to "γ",
        "\\Gamma" to "Γ",
        "\\delta" to "δ",
        "\\Delta" to "Δ",
        "\\epsilon" to "ε",
        "\\zeta" to "ζ",
        "\\eta" to "η",
        "\\theta" to "θ",
        "\\Theta" to "Θ",
        "\\lambda" to "λ",
        "\\Lambda" to "Λ",
        "\\mu" to "μ",
        "\\nu" to "ν",
        "\\xi" to "ξ",
        "\\pi" to "π",
        "\\Pi" to "Π",
        "\\rho" to "ρ",
        "\\sigma" to "σ",
        "\\Sigma" to "Σ",
        "\\tau" to "τ",
        "\\phi" to "ϕ",
        "\\Phi" to "Φ",
        "\\chi" to "χ",
        "\\psi" to "ψ",
        "\\omega" to "ω",
        "\\Omega" to "Ω",
        "\\degree" to "°",
        "\\circ" to "°",
        "\\partial" to "∂",
        "\\nabla" to "∇",
        "\\in" to "∈",
        "\\notin" to "∉",
        "\\subset" to "⊂",
        "\\subseteq" to "⊆",
        "\\cup" to "∪",
        "\\cap" to "∩",
        "\\emptyset" to "∅",
        "\\forall" to "∀",
        "\\exists" to "∃",
        "\\rightarrow" to "→",
        "\\to" to "→",
        "\\leftarrow" to "←",
        "\\Rightarrow" to "⇒",
        "\\Leftarrow" to "⇐",
        "\\Leftrightarrow" to "⇔",
        "\\leftrightarrow" to "↔"
    )

    for ((latex, unicode) in replacements) {
        text = text.replace(latex, unicode)
    }

    // 5. Üslü İfadeler: x^{2} veya x^2 veya x^+ veya x^-
    val exponentBraceRegex = Regex("""\^\{([^}]+)\}""")
    text = exponentBraceRegex.replace(text) { match ->
        toSuperscript(match.groupValues[1])
    }

    val singleExponentRegex = Regex("""\^([0-9a-zA-Z\+\-\*]+)""")
    text = singleExponentRegex.replace(text) { match ->
        toSuperscript(match.groupValues[1])
    }

    // 6. Alt İndisler: x_{1} veya x_1 veya x_n
    val subscriptBraceRegex = Regex("""_\{([^}]+)\}""")
    text = subscriptBraceRegex.replace(text) { match ->
        toSubscript(match.groupValues[1])
    }

    val singleSubscriptRegex = Regex("""_([0-9a-zA-Z\+\-]+)""")
    text = singleSubscriptRegex.replace(text) { match ->
        toSubscript(match.groupValues[1])
    }

    // 7. Markdown Kalın (**text**) ve İtalik (*text*) çözümleme
    return parseMarkdownToAnnotatedString(text)
}

/**
 * Karakterleri Unicode Üslü (Superscript) karakterlere çevirir.
 */
private fun toSuperscript(input: String): String {
    val map = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
        '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
        '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾',
        'a' to 'ᵃ', 'b' to 'ᵇ', 'c' to 'ᶜ', 'd' to 'ᵈ', 'e' to 'ᵉ',
        'f' to 'ᶠ', 'g' to 'ᵍ', 'h' to 'ʰ', 'i' to 'ⁱ', 'j' to 'ʲ',
        'k' to 'ᵏ', 'l' to 'ˡ', 'm' to 'ᵐ', 'n' to 'ⁿ', 'o' to 'ᵒ',
        'p' to 'ᵖ', 'r' to 'ʳ', 's' to 'ˢ', 't' to 'ᵗ', 'u' to 'ᵘ',
        'v' to 'ᵛ', 'w' to 'ʷ', 'x' to 'ˣ', 'y' to 'ʸ', 'z' to 'ᶻ'
    )
    return input.map { map[it] ?: it }.joinToString("")
}

/**
 * Karakterleri Unicode Alt İndis (Subscript) karakterlere çevirir.
 */
private fun toSubscript(input: String): String {
    val map = mapOf(
        '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
        '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
        '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎',
        'a' to 'ₐ', 'e' to 'ₑ', 'h' to 'ₕ', 'i' to 'ᵢ', 'j' to 'ⱼ',
        'k' to 'ₖ', 'l' to 'ˡ', 'm' to 'ₘ', 'n' to 'ₙ', 'o' to 'ₒ',
        'p' to 'ₚ', 'r' to 'ᵣ', 's' to 'ₛ', 't' to 'ₜ', 'u' to 'ᵤ',
        'v' to 'ᵥ', 'x' to 'ₓ'
    )
    return input.map { map[it] ?: it }.joinToString("")
}

/**
 * Markdown kalın (**...**) ve italik (*...*) işaretlerini AnnotatedString'e dönüştürür.
 */
private fun parseMarkdownToAnnotatedString(text: String): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        val length = text.length

        while (cursor < length) {
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
