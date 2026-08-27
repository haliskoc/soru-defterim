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
                val cleanPrefix = trimmed.removePrefix("-").removePrefix("•").removePrefix("*").removePrefix(".").trim()
                val numberMatch = Regex("""^(\d{1,2}\.)\s*(.*)$""").find(cleanPrefix)
                val isNumberedItem = numberMatch != null
                val isListItem = trimmed.startsWith("-") || trimmed.startsWith("•") || (trimmed.startsWith("*") && !trimmed.startsWith("**")) || trimmed.startsWith(".") || isNumberedItem

                val rawContent = when {
                    isNumberedItem -> numberMatch!!.groupValues[2]
                    isListItem -> cleanPrefix
                    else -> trimmed
                }.removeSuffix(":*").removeSuffix("*").trim()

                val formatted = formatMathText(rawContent)

                if (isListItem) {
                    val bulletText = if (isNumberedItem) numberMatch!!.groupValues[1] else "•"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp),
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
 * LaTeX sembollerini, üslü sayıları, alt indisleri, integralleri, kesirleri
 * ve toplam sembollerini temiz Unicode Matematik formatına dönüştürür.
 */
private val THINK_REGEX = Regex("""<(think|thought)>[\s\S]*?</\1>""")
private val LEFT_REGEX = Regex("""\\left\s*""")
private val RIGHT_REGEX = Regex("""\\right\s*""")
private val TEXT_CMD_REGEX = Regex("""\\(text|mathbf|mathit|mathrm|textbf|textit|mathbb|bold|operatorname)\{([^}]+)\}""")
private val FRAC_REGEX = Regex("""\\frac\{([^}]+)\}\{([^}]+)\}""")
private val SQRT_REGEX = Regex("""\\sqrt\{([^}]+)\}""")
private val N_SQRT_REGEX = Regex("""\\sqrt\[([^\]]+)\]\{([^}]+)\}""")

private val ARROW_REPLACEMENTS = listOf(
    Regex("""\\implies(?![a-zA-Z])""") to " ⇒ ",
    Regex("""\\impliedby(?![a-zA-Z])""") to " ⇐ ",
    Regex("""\\iff(?![a-zA-Z])""") to " ⇔ ",
    Regex("""\\(to|rightarrow)(?![a-zA-Z])""") to " → ",
    Regex("""\\leftarrow(?![a-zA-Z])""") to " ← ",
    Regex("""\\Rightarrow(?![a-zA-Z])""") to " ⇒ ",
    Regex("""\\Leftarrow(?![a-zA-Z])""") to " ⇐ ",
    Regex("""\\Leftrightarrow(?![a-zA-Z])""") to " ⇔ ",
    Regex("""\\leftrightarrow(?![a-zA-Z])""") to " ↔ "
)

private val REGEX_REPLACEMENTS = listOf(
    Regex("""\\(leq|le)(?![a-zA-Z])""") to "≤",
    Regex("""\\(geq|ge)(?![a-zA-Z])""") to "≥",
    Regex("""\\(neq|ne)(?![a-zA-Z])""") to "≠",
    Regex("""\\(approx)(?![a-zA-Z])""") to "≈",
    Regex("""\\(sim)(?![a-zA-Z])""") to "∼",
    Regex("""\\(equiv)(?![a-zA-Z])""") to "≡",
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
    Regex("""\\(epsilon)(?![a-zA-Z])""") to "ε",
    Regex("""\\(zeta)(?![a-zA-Z])""") to "ζ",
    Regex("""\\(eta)(?![a-zA-Z])""") to "η",
    Regex("""\\(theta)(?![a-zA-Z])""") to "θ",
    Regex("""\\(Theta)(?![a-zA-Z])""") to "Θ",
    Regex("""\\(lambda)(?![a-zA-Z])""") to "λ",
    Regex("""\\(Lambda)(?![a-zA-Z])""") to "Λ",
    Regex("""\\(mu)(?![a-zA-Z])""") to "μ",
    Regex("""\\(nu)(?![a-zA-Z])""") to "ν",
    Regex("""\\(xi)(?![a-zA-Z])""") to "ξ",
    Regex("""\\(pi)(?![a-zA-Z])""") to "π",
    Regex("""\\(Pi)(?![a-zA-Z])""") to "Π",
    Regex("""\\(rho)(?![a-zA-Z])""") to "ρ",
    Regex("""\\(sigma)(?![a-zA-Z])""") to "σ",
    Regex("""\\(Sigma)(?![a-zA-Z])""") to "Σ",
    Regex("""\\(tau)(?![a-zA-Z])""") to "τ",
    Regex("""\\(phi)(?![a-zA-Z])""") to "ϕ",
    Regex("""\\(Phi)(?![a-zA-Z])""") to "Φ",
    Regex("""\\(chi)(?![a-zA-Z])""") to "χ",
    Regex("""\\(psi)(?![a-zA-Z])""") to "ψ",
    Regex("""\\(omega)(?![a-zA-Z])""") to "ω",
    Regex("""\\(Omega)(?![a-zA-Z])""") to "Ω",
    Regex("""\\(partial)(?![a-zA-Z])""") to "∂",
    Regex("""\\(nabla)(?![a-zA-Z])""") to "∇",
    Regex("""\\(notin)(?![a-zA-Z])""") to "∉",
    Regex("""\\(in)(?![a-zA-Z])""") to "∈",
    Regex("""\\(subset)(?![a-zA-Z])""") to "⊂",
    Regex("""\\(subseteq)(?![a-zA-Z])""") to "⊆",
    Regex("""\\(cup)(?![a-zA-Z])""") to "∪",
    Regex("""\\(cap)(?![a-zA-Z])""") to "∩",
    Regex("""\\(emptyset)(?![a-zA-Z])""") to "∅",
    Regex("""\\(forall)(?![a-zA-Z])""") to "∀",
    Regex("""\\(exists)(?![a-zA-Z])""") to "∃"
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
    'v' to 'ᵛ', 'w' to 'ʷ', 'x' to 'ˣ', 'y' to 'ʸ', 'z' to 'ᶻ'
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

    // 0. AI Düşünce (<think>...</think>) etiketlerini temizle
    text = text.replace(THINK_REGEX, "")

    // 1. LaTeX bloklarını, parantez kaçışlarını ve \left / \right belirteçlerini temizle
    text = text.replace("$$", "").replace("$", "")
    text = text.replace("\\[", "").replace("\\]", "")
    text = text.replace("\\(", "").replace("\\)", "")
    text = text.replace(LEFT_REGEX, "")
    text = text.replace(RIGHT_REGEX, "")

    // 2. LaTeX Metin ve Yazı Tipi Sarmalayıcılarını Çöz: \text{...}, \mathbf{...}, vb.
    while (TEXT_CMD_REGEX.containsMatchIn(text)) {
        text = TEXT_CMD_REGEX.replace(text) { it.groupValues[2] }
    }

    // 3. LaTeX Kesirleri: \frac{a}{b} -> (a / b)
    text = FRAC_REGEX.replace(text) { match ->
        val num = match.groupValues[1]
        val den = match.groupValues[2]
        "($num / $den)"
    }

    // 4. LaTeX Kökleri: \sqrt{x} -> √(x), \sqrt[n]{x} -> ⁿ√(x)
    text = SQRT_REGEX.replace(text) { "√(${it.groupValues[1]})" }
    text = N_SQRT_REGEX.replace(text) { "${toSuperscript(it.groupValues[1])}√(${it.groupValues[2]})" }

    // 5. Mantıksal ve Yön Okları
    for ((pattern, replacement) in ARROW_REPLACEMENTS) {
        text = text.replace(pattern, replacement)
    }

    // 6. Kelime Sınırı Güvenliğine Sahip LaTeX Sembolleri
    for ((pattern, replacement) in REGEX_REPLACEMENTS) {
        text = text.replace(pattern, replacement)
    }

    // 7. Üslü İfadeler
    text = EXPONENT_BRACE_REGEX.replace(text) { match ->
        toSuperscript(match.groupValues[1])
    }
    text = SINGLE_EXPONENT_REGEX.replace(text) { match ->
        toSuperscript(match.groupValues[1])
    }

    // 8. Alt İndisler
    text = SUBSCRIPT_BRACE_REGEX.replace(text) { match ->
        toSubscript(match.groupValues[1])
    }
    text = SINGLE_SUBSCRIPT_REGEX.replace(text) { match ->
        toSubscript(match.groupValues[1])
    }

    return text
}

/**
 * LaTeX sembollerini ve Markdown kalın/italik yapılarını temiz AnnotatedString formatına dönüştürür.
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
