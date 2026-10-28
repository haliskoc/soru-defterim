package com.sorudefteri.ui.ortak.math

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * KaTeX için HTML şablonu oluşturan, LaTeX ve Markdown içeriklerini
 * yerel KaTeX motoru için hazırlayan yardımcı motor.
 */
object KaTeXHtmlGenerator {

    val MATH_DELIMITER_REGEX = Regex("""(\$\$[\s\S]*?\$\$|\\\[[\s\S]*?\\\]|\\\([\s\S]*?\\\)|\$(?:\\.|[^$\\])+\$)""")

    val LATEX_TRIGGER_KEYWORDS = listOf(
        "\\frac", "\\dfrac", "\\cfrac", "\\tfrac", "\\dfac", "\\sqrt", "\\int", "\\iint", "\\iiint", "\\oint",
        "\\sum", "\\prod", "\\lim", "\\begin", "\\pmatrix", "\\bmatrix", "\\matrix",
        "\\ce", "\\vec", "\\overrightarrow", "\\alpha", "\\beta", "\\gamma",
        "\\theta", "\\lambda", "\\mu", "\\sigma", "\\omega", "\\Delta",
        "\\partial", "\\nabla", "\\infty", "\\times", "\\cdot", "\\pm", "\\mp",
        "\\leq", "\\geq", "\\neq", "\\approx", "\\equiv", "\\to",
        "\\implies", "\\iff", "\\rightarrow", "\\leftarrow", "\\cancel", "\\binom",
        "\\overset", "\\underset", "\\xrightarrow", "\\xleftarrow", "\\left", "\\right"
    )

    private val TURKISH_NATURAL_WORDS = listOf(
        "ve", "veya", "ile", "için", "olan", "olur", "bulunur", "olarak",
        "ifadesinde", "belirsizliği", "kuralı", "adım", "çözüm", "sonuç",
        "soru", "cevap", "şeklinde", "türevi", "fonksiyonu", "noktası",
        "değeri", "burada", "göre", "eşit", "olduğundan", "bu", "bir",
        "doğru", "yanlış", "şıkkı", "seçeneği"
    )

    /**
     * Çift veya daha fazla kaçışlı ters eğik çizgileri (\\frac, \\\dfrac vb.) tek kaçışlı forma indirir.
     */
    private val MULTI_ESCAPED_SLASH_REGEX = Regex("""\\{2,}(?=[a-zA-Z])""")

    private val HTML_ENTITY_REGEX_MAP = mapOf(
        "&lt;" to "<",
        "&gt;" to ">",
        "&quot;" to "\"",
        "&#39;" to "'"
    )

    private val ISOLATED_NEWLINE_REGEX = Regex(
        """(?<!\\)\\n(?!(?:eq|e(?![a-zA-Z])|eg(?![a-zA-Z])|abla|otin|u(?![a-zA-Z])|ot(?![a-zA-Z])|atural|i(?![a-zA-Z])|exists|geq|leq|subseteq|supseteq|parallel|sim|cong|mid|earrow|warrow)(?![a-zA-Z]))"""
    )

    /**
     * AI/harici chatbot çıktılarındaki kaçış ve HTML kalıntılarını KaTeX öncesi temizler:
     * - &lt;/&gt;/&quot; -> gerçek karakter
     * - \dfac veya dfac -> \dfrac (yaygın model/OCR yazım hatası)
     * - \\frac, \\\dfrac -> \frac, \dfrac (çift/çoklu kaçış indirgeme)
     * - izole \n literalleri -> gerçek satır sonu
     */
    fun sanitizeAiSolutionEscapes(raw: String): String {
        var text = raw
        // Önce HTML entity'leri çöz
        HTML_ENTITY_REGEX_MAP.forEach { (entity, ch) ->
            text = text.replace(entity, ch)
        }
        // &amp; en son çözülmeli (&lt; içindeki & bozulmasın)
        text = text.replace("&amp;", "&")
        // Olası "dfac" veya "\dfac" yazım hatalarını standart \dfrac komutuna çevir
        text = Regex("""(?<![a-zA-Z])\\*dfac(?![a-zA-Z])""").replace(text, "\\dfrac")
        // Çift veya çoklu kaçışlı LaTeX komutlarını teke indir (\\dfrac -> \dfrac, \\\frac -> \frac)
        text = MULTI_ESCAPED_SLASH_REGEX.replace(text) { "\\" }
        // \r\n literalleri ve izole \r temizliği
        text = text.replace("\r\n", "\n").replace("\r", "")
        text = text.replace("\\r\\n", "\n")
        text = text.replace(Regex("""\\r(?![a-zA-Z])"""), "")
        // Bozuk ok komutlarını onar (ightarrow -> \rightarrow, ongtarrow -> \longrightarrow)
        text = text.replace(Regex("""(?<![a-zA-Z\\])ightarrow\b"""), "\\rightarrow")
        text = text.replace(Regex("""(?<![a-zA-Z\\])ongrightarrow\b"""), "\\longrightarrow")
        text = text.replace(Regex("""(?<![a-zA-Z\\])ightleftharpoons\b"""), "\\rightleftharpoons")
        // L'Hôpital yazımlarını normalize et
        text = text.replace(Regex("""L['’]H[\^oô]pital|L['’]Hospital""", RegexOption.IGNORE_CASE), "L'Hôpital")
        // İzole \n -> gerçek satır sonu
        text = ISOLATED_NEWLINE_REGEX.replace(text, "\n")
        return text
    }
    fun colorToHex(color: Color): String {
        val argb = color.toArgb()
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return String.format(Locale.ROOT, "#%02x%02x%02x", r, g, b)
    }

    /**
     * Bir metnin karışık Türkçe metin mi yoksa tek başına bir matematik formülü mü olduğunu belirler.
     */
    fun isPureFormula(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return false
        if (trimmed.contains("\n") && !trimmed.startsWith("\\begin")) return false

        val lower = trimmed.lowercase(Locale("tr"))
        val hasTurkishWords = TURKISH_NATURAL_WORDS.any { word ->
            Regex("""(?:\b|[^a-zA-ZçğıöşüÇĞİÖŞÜ])$word(?:\b|[^a-zA-ZçğıöşüÇĞİÖŞÜ])""", RegexOption.IGNORE_CASE).containsMatchIn(lower)
        }
        if (hasTurkishWords) return false

        return trimmed.startsWith("\\") ||
                Regex("""^[a-zA-Z0-9\(\)\s\+\-\*\/=.,_^{}\[\]\\|]+$""").matches(trimmed)
    }

    /**
     * Düz metin kesirlerini ((4 / 3) -> \frac{4}{3}), L'Hôpital [0/0] belirsizliğini,
     * limit ve integralsel ifadeleri KaTeX sınır belirteçleriyle sararak zenginleştirir.
     */
    fun normalizeAndWrapMath(raw: String, defaultDisplayMode: Boolean = false): String {
        val parts = MATH_DELIMITER_REGEX.split(raw)
        val matches = MATH_DELIMITER_REGEX.findAll(raw).map { it.value }.toList()

        val sb = StringBuilder()
        var matchIndex = 0

        for (part in parts) {
            var processedPart = part

            // 1. Türkçe ondalık virgül düzeltmesi (9{,}3 -> 9,3)
            processedPart = processedPart.replace(Regex("""(\d+)\{,\}(\d+)"""), "$1,$2")
                .replace("{,}", ",")

            // 2. Sınır belirteçsiz LaTeX formül ve komutlarını (\lim, \frac, \sqrt, \int vb.) tespit edip $...$ içine al
            processedPart = wrapUnenclosedLatex(processedPart)

            // 3. L'Hôpital [0/0] belirsizliği (standart ve bozulmuş varyasyonlar)
            processedPart = processedPart.replace(Regex("""\\?\[\s*\(?\s*0\s*/\s*0\s*\)?\s*(?:\\?right|ight)?\s*\\?\]""")) { """${'$'}\left[\frac{0}{0}\right]${'$'}""" }
            processedPart = processedPart.replace(Regex("""(?<=\s|^)0\s*/\s*0(?=[\s.,;)]|$)""")) { """${'$'}\left[\frac{0}{0}\right]${'$'}""" }

            // 4a. İki parantezli bölme: (x + 1) / (x - 1) -> $\frac{x + 1}{x - 1}$
            processedPart = processedPart.replace(Regex("""(?<![a-zA-Z0-9\\])\(\s*([a-zA-Z0-9\+\-\*\.\s]+)\s*\)\s*/\s*\(\s*([a-zA-Z0-9\+\-\*\.\s]+)\s*\)""")) { m ->
                val num = m.groupValues[1].trim()
                val den = m.groupValues[2].trim()
                "${'$'}\\frac{$num}{$den}${'$'}"
            }

            // 4b. Parantezli pay ve yalın payda: (x + 1) / 2 -> $\frac{x + 1}{2}$
            processedPart = processedPart.replace(Regex("""(?<![a-zA-Z0-9\\])\(\s*([a-zA-Z0-9\+\-\*\.\s]+)\s*\)\s*/\s*([a-zA-Z0-9]+)(?![a-zA-Z0-9/])""")) { m ->
                val num = m.groupValues[1].trim()
                val den = m.groupValues[2].trim()
                "${'$'}\\frac{$num}{$den}${'$'}"
            }

            // 4c. Parantezli kesirler: (4 / 3), (a / b), (3x + 1 / 2) -> $\frac{...}{...}$
            processedPart = processedPart.replace(Regex("""(?<![a-zA-Z0-9\\])\(\s*([a-zA-Z0-9\+\-\*\.]+)\s*/\s*([a-zA-Z0-9\+\-\*\.]+)\s*\)""")) { m ->
                val num = m.groupValues[1].trim()
                val den = m.groupValues[2].trim()
                "${'$'}\\frac{$num}{$den}${'$'}"
            }

            // 4d. Standart yalın sayı bölme işlemleri (örn: 4/3 veya 1/2, tarih veya URL olmayanlar)
            processedPart = processedPart.replace(Regex("""(?<![a-zA-Z0-9/\\])(\d{1,4})\s*/\s*(\d{1,4})(?![a-zA-Z0-9/])""")) { m ->
                "${'$'}\\frac{${m.groupValues[1]}}{${m.groupValues[2]}}${'$'}"
            }

            sb.append(processedPart)

            if (matchIndex < matches.size) {
                var mathMatch = matches[matchIndex]
                // Satır içi limit veya integrallere \displaystyle ekleyerek dikey dizgi güzelliğini sağla
                if (mathMatch.startsWith("$") && !mathMatch.startsWith("$$")) {
                    if (mathMatch.contains("\\lim") && !mathMatch.contains("\\displaystyle")) {
                        mathMatch = mathMatch.replace("\\lim", "\\displaystyle \\lim")
                    }
                    if (mathMatch.contains("\\int") && !mathMatch.contains("\\displaystyle")) {
                        mathMatch = mathMatch.replace("\\int", "\\displaystyle \\int")
                    }
                }
                sb.append(mathMatch)
                matchIndex++
            }
        }

        return sb.toString()
    }

    /**
     * Düz metin içerisindeki \dfrac, \frac, \cfrac, \tfrac veya \dfac ifadelerini,
     * iç içe süslü parantezler ({...}{...}) derinliği ne olursa olsun tam olarak
     * tespit edip KaTeX $...$ sınır belirteçleriyle sarar.
     */
    fun wrapUnenclosedFractions(input: String): String {
        val fracCmdRegex = Regex("""(?<![a-zA-Z\$\\])\\(?:frac|dfrac|cfrac|tfrac|dfac)\b""")
        val sb = StringBuilder()
        var i = 0

        while (i < input.length) {
            val match = fracCmdRegex.find(input, i)
            if (match == null) {
                sb.append(input.substring(i))
                break
            }

            val cmdStart = match.range.first
            val cmdEnd = match.range.last + 1

            sb.append(input.substring(i, cmdStart))

            val firstArg = extractBalancedBrace(input, cmdEnd)
            if (firstArg != null) {
                val secondArg = extractBalancedBrace(input, firstArg.second)
                if (secondArg != null) {
                    val fullFrac = input.substring(cmdStart, secondArg.second)
                    val cmdName = match.value
                    // Bozuk \dfac yazım hatasını düzelt, orijinal \frac veya \dfrac'ı koru
                    val normalizedFrac = if (cmdName == "\\dfac") {
                        "\\frac" + fullFrac.substring(cmdName.length)
                    } else fullFrac

                    val alreadyInMath = isInsideDelimiters(input, cmdStart)
                    if (alreadyInMath) {
                        sb.append(normalizedFrac)
                    } else {
                        sb.append("\$$normalizedFrac\$")
                    }
                    i = secondArg.second
                    continue
                }
            }

            // Süslü parantezsiz tekil token eşleşmesi (Örn: \frac 1 2 veya \dfrac a b)
            val singleTokenRegex = Regex("""^\\(?:frac|dfrac|cfrac|tfrac|dfac)\s+([0-9a-zA-Z])\s+([0-9a-zA-Z])""")
            val singleMatch = singleTokenRegex.find(input.substring(cmdStart))
            if (singleMatch != null) {
                val fullFrac = "\\frac{${singleMatch.groupValues[1]}}{${singleMatch.groupValues[2]}}"
                val alreadyInMath = isInsideDelimiters(input, cmdStart)
                if (alreadyInMath) {
                    sb.append(fullFrac)
                } else {
                    sb.append("\$$fullFrac\$")
                }
                i = cmdStart + singleMatch.range.last + 1
                continue
            }

            sb.append(match.value)
            i = cmdEnd
        }

        return sb.toString()
    }

    private fun extractBalancedBrace(text: String, startIdx: Int): Pair<String, Int>? {
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

    private fun isInsideDelimiters(text: String, targetIdx: Int): Boolean {
        var count = 0
        var i = 0
        while (i < targetIdx) {
            if (text[i] == '$' && (i == 0 || text[i - 1] != '\\')) {
                count++
            }
            i++
        }
        return count % 2 == 1
    }

    private fun wrapUnenclosedLatex(text: String): String {
        var s = text

        // \lim_{...} ve devamındaki kesir/ifade
        s = s.replace(Regex("""(?<!\$)\\lim(?:_\{[^}]+\}|_[0-9a-zA-Z\\]+)?(?:\s*(?:\\(?:frac|dfrac|cfrac|tfrac|dfac)\{(?:[^{}]|\{[^{}]*\})+\}\{(?:[^{}]|\{[^{}]*\})+\}|[a-zA-Z0-9\(\)\+\-\*\/\^_{}\\]+))*(?!\$)""")) { m ->
            "${'$'}\\displaystyle ${m.value.trim()}${'$'}"
        }

        // \dfrac, \frac, \cfrac, \tfrac, \dfac kesirlerini dengeli parantezle tespit edip $...$ ile sar
        s = wrapUnenclosedFractions(s)

        // \sqrt{...} veya \sqrt[...]
        s = s.replace(Regex("""(?<!\$)\\sqrt(?:\[[^\]]+\])?\{[^{}]+\}(?!\$)""")) { m ->
            "${'$'}${m.value}${'$'}"
        }

        // \int ve sınırları
        s = s.replace(Regex("""(?<!\$)\\int(?:_\{[^}]+\}|_[0-9a-zA-Z]+)?(?:\^\{[^}]+\}|\^[0-9a-zA-Z]+)?(?:\s+[^\n$,]+)?(?!\$)""")) { m ->
            "${'$'}\\displaystyle ${m.value.trim()}${'$'}"
        }

        // \vec{...} ve \overrightarrow{...}
        s = s.replace(Regex("""(?<!\$)\\(?:vec|overrightarrow)\{[^{}]+\}(?!\$)""")) { m ->
            "${'$'}${m.value}${'$'}"
        }

        // \ce{...} kimyasal tepkimeler
        s = s.replace(Regex("""(?<!\$)\\ce\{[^{}]+\}(?!\$)""")) { m ->
            "${'$'}${m.value}${'$'}"
        }

        // \begin{ortam}...\end{ortam} blokları (pmatrix, cases, aligned vb.)
        s = s.replace(
            Regex(
                """(?<!\$)\\begin\{[a-zA-Z*]+\}[\s\S]*?\\end\{[a-zA-Z*]+\}(?!\$)"""
            )
        ) { m ->
            "${'$'}${'$'}${m.value}${'$'}${'$'}"
        }

        // 1. Kümeler ve uzay tanımları: a, b, c \in \mathbb{R} veya x \in [a, b]
        s = s.replace(Regex("""(?<!\$)([a-zA-Z0-9,\s]+?)\s*\\in\s*(\\mathbb\{[A-Z]\}|\[[^\]]+\]|\([^\)]+\)|\\{0,1}[A-Z])(?!\$)""")) { m ->
            "${'$'}${m.value.trim()}${'$'}"
        }

        // 2. Eşit değil, yaklaşık, denklik, yönlendirme ve karşılaştırma: c \neq 0, x \le 5, h \to 0 vb.
        s = s.replace(Regex("""(?<!\$)([a-zA-Z0-9\(\)\'\^]+?)\s*(\\(?:neq|ne|approx|equiv|sim|propto|le|ge|leq|geq|to|rightarrow|longrightarrow|implies|iff))\s*([a-zA-Z0-9\(\)\'\^\\\{\}]+)(?!\$)""")) { m ->
            "${'$'}${m.value.trim()}${'$'}"
        }

        // 3. Çarpma (\cdot, \times) ve işlem adımları: 6 \cdot f'(1+6h) veya \frac{8}{3} \cdot f'(1)
        s = s.replace(Regex("""(?<!\$)([a-zA-Z0-9\(\)\'\^\\\{\}]+?)\s*(\\(?:cdot|times|pm|mp|div))\s*([a-zA-Z0-9\(\)\'\^\\\{\}]+)(?!\$)""")) { m ->
            "${'$'}${m.value.trim()}${'$'}"
        }

        // 4. Matematiksel eşitlik satırları (Örn: "Payın türevi: 6 \cdot f'(1+6h) - (-2) \cdot f'(1-2h) = 6 \cdot f'(1+6h) + 2 \cdot f'(1-2h)")
        s = s.lines().joinToString("\n") { line ->
            val trimmedLine = line.trim()
            if (trimmedLine.contains("$")) {
                line
            } else if (!trimmedLine.startsWith("#") && trimmedLine.contains("=") &&
                (trimmedLine.contains("\\cdot") || trimmedLine.contains("\\frac") || trimmedLine.contains("'") || trimmedLine.contains("^") || trimmedLine.contains("_"))) {
                if (trimmedLine.contains(":") && !trimmedLine.startsWith("http")) {
                    val label = trimmedLine.substringBefore(":") + ":"
                    val expr = trimmedLine.substringAfter(":").trim()
                    if (expr.contains("=") && !expr.contains("$") && (expr.contains("\\") || expr.contains("+") || expr.contains("-") || expr.contains("*"))) {
                        "$label ${'$'}\\displaystyle $expr${'$'}"
                    } else {
                        line
                    }
                } else if (!Regex("""[a-zçğıöşü]{3,}""").containsMatchIn(trimmedLine) && (trimmedLine.contains("\\") || trimmedLine.contains("+") || trimmedLine.contains("-") || trimmedLine.contains("*"))) {
                    "${'$'}\\displaystyle $trimmedLine${'$'}"
                } else {
                    line
                }
            } else {
                line
            }
        }

        // 5. İndisler ve alt simgeler (F_s, F_k, F_{net}, a_n, c_1, c_2 vb. - sadece $ ve { dışındakiler)
        s = s.replace(Regex("""(?<![a-zA-Z0-9\$\\\{])([Ff])_(s|k|net|statik|kinetik|sürtünme)\b""")) { m ->
            if (isInsideDelimiters(s, m.range.first)) m.value else "${'$'}${m.groupValues[1]}_{${m.groupValues[2]}}${'$'}"
        }
        s = s.replace(Regex("""(?<![a-zA-Z0-9\$\\\{])([a-zA-Z])_([0-9]+|[a-z])(?![a-zA-Z0-9\$\\\}])""")) { m ->
            if (isInsideDelimiters(s, m.range.first)) m.value else "${'$'}${m.groupValues[1]}_{${m.groupValues[2]}}${'$'}"
        }

        // 6. Üsler (c_3^2, a_n^2, x^2 vb. - sadece $ ve { dışındakiler)
        s = s.replace(Regex("""(?<![a-zA-Z0-9\$\\\{])([a-zA-Z0-9\)\}]+)\^([0-9]+|[a-z])(?![a-zA-Z0-9\$\\\}])""")) { m ->
            if (isInsideDelimiters(s, m.range.first)) m.value else "${'$'}${m.groupValues[1]}^{${m.groupValues[2]}}${'$'}"
        }

        // 7. Çarpma yıldızları: c_1 * c_5 -> c_1 \cdot c_5
        s = s.replace(Regex("""(?<=\b[a-zA-Z0-9_])\s*\*\s*(?=[a-zA-Z0-9_]\b)""")) { " \\cdot " }

        return s
    }

    /**
     * Ham metni analiz edip, LaTeX formülü ise KaTeX sınır belirteçleriyle sarar;
     * Markdown içeriyorsa formülleri bozmadan HTML etiketlerine dönüştürür.
     */
    fun preprocessContent(rawText: String, defaultDisplayMode: Boolean = false): String {
        val sanitized = sanitizeAiSolutionEscapes(rawText)
        val clean = sanitized.trim()
        if (clean.isBlank()) return ""

        // Eğer içerikte hiç $ veya \[ veya \( yoksa ve tamamen saf bir LaTeX formülü ise:
        val hasExplicitDelimiters = clean.contains("$") || clean.contains("\\[") || clean.contains("\\(")
        val looksLikeFormula = LATEX_TRIGGER_KEYWORDS.any { clean.contains(it) } ||
                clean.startsWith("\\") ||
                (clean.contains("^") && clean.contains("_"))

        if (!hasExplicitDelimiters && looksLikeFormula && isPureFormula(clean)) {
            val dollar = "${'$'}"
            return if (defaultDisplayMode) "$dollar$dollar$clean$dollar$dollar" else "$dollar$clean$dollar"
        }

        // Karışık metin, markdown veya sınır belirteçsiz formülleri normalize et ve sar
        val normalized = normalizeAndWrapMath(clean, defaultDisplayMode)

        // Markdown + Math hibrit ayrıştırma
        return convertMarkdownWithMathPreserved(normalized)
    }

    /**
     * Matematik bloklarını ($$...$$, $...$, \[...\], \(...\)) koruyarak
     * Markdown kalın, italik, liste ve başlık yapılarını HTML'e dönüştürür.
     * Cümle içindeki formüllerin satır satır parçalanmasını (orphan p etiketlerini) önler.
     */
    fun convertMarkdownWithMathPreserved(raw: String): String {
        val matches = MATH_DELIMITER_REGEX.findAll(raw).map { it.value }.toList()
        if (matches.isEmpty()) {
            return convertFullMarkdownToHtml(raw)
        }

        val tokenPrefix = "___KATEX_TOKEN_"
        val tokenSuffix = "___"
        var tokenized = raw

        matches.forEachIndexed { index, match ->
            tokenized = tokenized.replaceFirst(match, "$tokenPrefix$index$tokenSuffix")
        }

        var html = convertFullMarkdownToHtml(tokenized)

        matches.forEachIndexed { index, match ->
            html = html.replace("$tokenPrefix$index$tokenSuffix", match)
        }

        return html
    }

    private fun convertFullMarkdownToHtml(text: String): String {
        if (text.isEmpty()) return ""

        var s = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

        // Başlıklar
        s = s.replace(Regex("""(?m)^###\s+(.+)$"""), "<h3>$1</h3>")
        s = s.replace(Regex("""(?m)^##\s+(.+)$"""), "<h2>$1</h2>")
        s = s.replace(Regex("""(?m)^#\s+(.+)$"""), "<h1>$1</h1>")

        // Kalın: **metin**
        s = s.replace(Regex("""\*\*(.+?)\*\*"""), "<strong>$1</strong>")

        // İtalik: *metin*
        s = s.replace(Regex("""(?<!\*)\*([^*]+?)\*(?!\*)"""), "<em>$1</em>")

        // Üstü çizili: ~~metin~~
        s = s.replace(Regex("""~~(.+?)~~"""), "<del>$1</del>")

        // Madde işaretleri (- veya •) ve paragraflar
        val lines = s.lines()
        val result = StringBuilder()
        var inList = false
        val currentParagraph = StringBuilder()

        fun flushParagraph() {
            if (currentParagraph.isNotBlank()) {
                result.append("<p>").append(currentParagraph.toString().trim()).append("</p>")
                currentParagraph.clear()
            }
        }

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("<h") && (trimmed.endsWith("</h3>") || trimmed.endsWith("</h2>") || trimmed.endsWith("</h1>"))) {
                flushParagraph()
                if (inList) { result.append("</ul>"); inList = false }
                result.append(trimmed)
            } else if (trimmed.startsWith("- ") || trimmed.startsWith("• ") || trimmed.startsWith("* ")) {
                flushParagraph()
                if (!inList) {
                    result.append("<ul>")
                    inList = true
                }
                val item = trimmed.substring(2).trim()
                result.append("<li>").append(item).append("</li>")
            } else if (trimmed.isEmpty()) {
                flushParagraph()
                if (inList) {
                    result.append("</ul>")
                    inList = false
                }
            } else {
                if (inList) {
                    result.append("</ul>")
                    inList = false
                }
                if (currentParagraph.isNotEmpty()) {
                    currentParagraph.append("<br/>")
                }
                currentParagraph.append(trimmed)
            }
        }

        flushParagraph()
        if (inList) {
            result.append("</ul>")
        }

        return result.toString()
    }

    /**
     * Yerel KaTeX ve mhchem JS/CSS dosyalarını referans alan eksiksiz HTML dokümanı üretir.
     */
    fun generateHtml(
        content: String,
        isDarkTheme: Boolean = false,
        textColorHex: String = if (isDarkTheme) "#E6E1E5" else "#1C1B1F",
        backgroundColorHex: String = "transparent",
        fontSizeSp: Float = 15f,
        displayMode: Boolean = false,
        // DÜZELTME: Compose'un sistem yazı tipi ölçeği. WebView textZoom=100
        // olduğu için sistem ölçeğini KENDİSİ uygulamıyor; Compose Text ise
        // uyguluyor. Büyütülmüş yazıda formüller komşu metinden küçük kalıp
        // hizalamayı bozuyordu. Ölçek burada HTML'e uygulanıyor.
        fontScale: Float = 1f,
        // PERF: Birleştirilmiş AI çözüm gövdesi zaten preprocess edilmiş
        // HTML içeriyorsa (bkz. generateCombinedHtml) ikinci kez
        // preprocessContent çalıştırılmaz — hem CPU hem çift-escape riski biter.
        skipPreprocess: Boolean = false,
        // PERF: Birleştirilmiş kart stilleri (tek WebView'de bölüm kartları).
        extraCss: String = ""
    ): String {
        val preprocessed = if (skipPreprocess) content else preprocessContent(content, displayMode)
        val fontSizePx: Float = (fontSizeSp * fontScale.coerceIn(0.85f, 2.0f))

        return """
            <!DOCTYPE html>
            <html lang="tr">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <link rel="stylesheet" href="file:///android_asset/katex/katex.min.css">
                <script src="file:///android_asset/katex/katex.min.js"></script>
                <script src="file:///android_asset/katex/contrib/mhchem.min.js"></script>
                <script src="file:///android_asset/katex/contrib/auto-render.min.js"></script>
                <style>
                    * {
                        box-sizing: border-box;
                        -webkit-tap-highlight-color: transparent;
                    }
                    body {
                        margin: 0;
                        /* DÜZELTME: Eski değer 4px 6px 28px 6px idi ve #math-content
                           de 20px padding-bottom ekliyordu, üstüne Kotlin tarafında da
                           +28.dp vardı -> her formül bloğunda ~76px boşluk. Aynı çözüm
                           içindeki DÜZ metinli kartlarda bu boşluk 0 olduğu için
                           bölümler hizalanmıyor, "metin kayıyor" izlenimi doğuyordu. */
                        padding: 3px 4px 4px 4px;
                        background-color: $backgroundColorHex;
                        color: $textColorHex;
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                        /* DÜZELTME: Sistem yazı tipi ölçeği (fontScale) burada
                           uygulanıyor. WebView textZoom=100 ile sistem ölçeğini
                           yok sayıyor, Compose Text ise uyguluyordu; büyütülmüş
                           yazıda formüller yanındaki normal metinden küçük kalıyor
                           ve hizalama bozuluyordu. */
                        font-size: ${fontSizePx}px;
                        line-height: 1.55;
                        word-break: break-word;
                        /* DÜZELTME: overflow-x: auto KALDIRILDI. Bu WebView
                           NonTouchableWebView (onTouchEvent = false) olduğu için
                           yatay kaydırma YAPILAMAZ; auto sadece geniş formülü
                           sessizce kırpıyordu. Artık geniş içerik JS ile
                           ölçeğe uyduruluyor (fitToWidth). */
                        overflow-x: visible;
                        overflow-y: visible;
                    }
                    #math-content {
                        padding-bottom: 0;
                        transform-origin: left top;
                    }
                    .katex-display {
                        margin: 0.5em 0;
                        padding: 4px 0;
                        /* DÜZELTME: auto -> visible, aynı dokunulmazlık nedeniyle. */
                        overflow-x: visible;
                        overflow-y: visible;
                    }
                    .katex {
                        /* DÜZELTME: 1.18em -> 1.05em. KaTeX varsayılanı zaten
                           büyük; 1.18 ile uzun formüller panel genişliğini aşıyor
                           ve (kaydırılamayan WebView'de) kırpılıyordu. */
                        font-size: 1.05em;
                        text-rendering: optimizeLegibility;
                        color: $textColorHex;
                    }
                    .katex .frac-line {
                        border-bottom-color: $textColorHex;
                        border-bottom-width: 1.5px;
                    }
                    .katex-html {
                        /* DÜZELTME: white-space: normal KALDIRILDI. Bu kural
                           KaTeX'in kendi "no-wrap" davranışını eziyor ve formülü
                           satır ortasından bölüyordu; böylece payda/pay üsleri
                           (baseline) hizası bozulup formül "kaymış" gibi
                           görünüyordu. KaTeX'in varsayılanı korunuyor; geniş
                           formüller artık fitToWidth() ile ölçekleniyor. */
                    }
                    p {
                        margin: 0.25em 0;
                    }
                    ul, ol {
                        margin: 0.25em 0;
                        padding-left: 20px;
                    }
                    li {
                        margin-bottom: 0.2em;
                        line-height: 1.5;
                    }
                    strong {
                        font-weight: 700;
                        color: $textColorHex;
                    }
                    em {
                        font-style: italic;
                    }
                    h1, h2, h3 {
                        margin: 0.4em 0 0.2em 0;
                        font-size: 1.1em;
                        font-weight: 700;
                        color: $textColorHex;
                    }
                    $extraCss
                </style>
            </head>
            <body>
                <div id="math-content">$preprocessed</div>
                <script>
                    document.addEventListener("DOMContentLoaded", function() {
                        if (typeof renderMathInElement === "function") {
                            renderMathInElement(document.getElementById("math-content"), {
                                delimiters: [
                                    {left: "$$", right: "$$", display: true},
                                    {left: "\\[", right: "\\]", display: true},
                                    {left: "\\(", right: "\\)", display: false},
                                    {left: "$", right: "$", display: false}
                                ],
                                throwOnError: false
                            });
                        }
                        
                        function notifyHeight() {
                            if (window.AndroidBridge && typeof window.AndroidBridge.onHeightChange === "function") {
                                var m = document.getElementById("math-content");
                                if (!m) return;
                                // DÜZELTME: getBoundingClientRect() CSS dönüşümlerini
                                // (scale) YANSITIR. fitToWidth() bloğu küçülttüğünde
                                // buradaki değer zaten GÖRSEL gerçek yüksekliktir.
                                // Eskiden body.scrollHeight (dönüşümsüz) kullanılıyordu
                                // ve ölçekli blokta çok fazla yer ayrılıyordu.
                                var h = m.getBoundingClientRect().height;
                                // body'nin kendi dikey padding'i (3px üst + 4px alt)
                                // ve güvenlik payı eklendi.
                                window.AndroidBridge.onHeightChange(Math.ceil(h) + 10);
                            }
                        }

                        // DÜZELTME: Bu fonksiyon DOMContentLoaded callback'i içinde
                        // tanımlandığı için global DEĞİL; Android tarafından
                        // evaluateJavascript("window.notifyHeight()") ile çağrılamıyordu
                        // (sessizce hiçbir şey olmuyordu). Açıkça global'e bağlıyoruz.
                        window.notifyHeight = notifyHeight;

                        /* Geniş formülleri panel genişliğine sığdır.
                           WebView dokunulmaz olduğu için yatay kaydırma imkânsız;
                           taşan kısım kalıcı olarak kırpılıyordu (formülün yarısı
                           hiç görünmüyordu). Bunun yerine ölçekliyoruz. */
                        function fitToWidth() {
                            var m = document.getElementById("math-content");
                            var b = document.body;
                            if (!m || !b) return;
                            m.style.transform = "none";
                            var style = window.getComputedStyle(b);
                            var avail = b.clientWidth
                                - (parseFloat(style.paddingLeft) || 0)
                                - (parseFloat(style.paddingRight) || 0);
                            var w = m.scrollWidth;
                            if (avail > 0 && w > avail) {
                                var ratio = avail / w;
                                // Aşırı küçültmeyi engelle (okunabilirlik)
                                if (ratio > 0.4) {
                                    m.style.transform = "scale(" + ratio + ")";
                                }
                            }
                        }
                        window.fitToWidth = fitToWidth;

                        if (window.ResizeObserver) {
                            try {
                                var ro = new ResizeObserver(function() {
                                    fitToWidth();
                                    notifyHeight();
                                });
                                if (document.body) ro.observe(document.body);
                                var m = document.getElementById("math-content");
                                if (m) ro.observe(m);
                            } catch(e) {}
                        }

                        // DÜZELTME: 0/30/100/250/500 ms'de gelen 5 ayrı ölçüm
                        // zamanlayıcısı kaldırıldı. KaTeX webfont'ları yüklendiğinde
                        // boyut değişiyor ve her ölçüm üstteki ortalanmış (Center)
                        // kapları yerinden oynatıyordu. Artık yalnızca:
                        //   1) ilk ölçüm, 2) font'lar hazır olduğunda, 3) ResizeObserver
                        // Yeterli ve daha stabil.
                        fitToWidth();
                        notifyHeight();
                        if (document.fonts && document.fonts.ready) {
                            document.fonts.ready.then(function() {
                                fitToWidth();
                                notifyHeight();
                            });
                        }
                        window.addEventListener("resize", function() {
                            fitToWidth();
                            notifyHeight();
                        });
                    });
                </script>
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * PERF: AI çözüm bölümlerini TEK WebView'de renderlamak için birleştirilmiş
     * gövde üretir. Her bölüm [MathMarkdownText] kartındaki renk/başlık
     * karşılığıyla HTML'e gömülür; sonuç [generateHtml] içine
     * `skipPreprocess=true` ile verilir (bölümler burada zaten preprocess edilir).
     *
     * Bölüm tipi -> kart stili eşlemesi:
     * - CORRECT_ANSWER (yeşil), FORMULAS (mor), STEPS (nötr),
     *   MISTAKES (kırmızı), TIPS (turuncu), GENERAL (sade).
     */
    fun generateCombinedHtml(
        sections: List<Triple<Int, String, String>>,
        displayMode: Boolean = false
    ): String {
        if (sections.isEmpty()) return ""
        val sb = StringBuilder()
        for ((typeOrdinal, title, content) in sections) {
            val preprocessed = preprocessContent(content, displayMode)
            if (preprocessed.isBlank() && title.isBlank()) continue
            val cardClass = when (typeOrdinal) {
                0 -> "katex-card katex-card-answer"
                1 -> "katex-card katex-card-formulas"
                2 -> "katex-card katex-card-steps"
                3 -> "katex-card katex-card-mistakes"
                4 -> "katex-card katex-card-tips"
                else -> "katex-card katex-card-general"
            }
            val safeTitle = title
                .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            sb.append("<div class=\"$cardClass\">")
            if (safeTitle.isNotBlank()) {
                sb.append("<div class=\"katex-card-title\">").append(safeTitle).append("</div>")
            }
            sb.append("<div class=\"katex-card-body\">").append(preprocessed).append("</div>")
            sb.append("</div>")
        }
        return sb.toString()
    }

    /**
     * PERF: Birleştirilmiş kart stilleri. [generateHtml] içine
     * `extraCss` olarak verilir. Renkler MathMarkdownText kartlarıyla birebir
     * eşleşir (yeşil/mor/nötr/kırmızı/turuncu).
     */
    fun combinedCardsCss(textColorHex: String): String = """
        .katex-card { border-radius: 14px; padding: 14px; margin: 0 0 12px 0; }
        .katex-card:last-child { margin-bottom: 0; }
        .katex-card-title { font-size: 1.1em; font-weight: 700; margin: 0 0 8px 0; }
        .katex-card-body { color: $textColorHex; }
        .katex-card-answer { background: rgba(46,125,50,0.12); border: 1px solid rgba(46,125,50,0.35); }
        .katex-card-answer .katex-card-title { color: #2E7D32; }
        .katex-card-formulas { background: rgba(98,0,238,0.08); border: 1px solid rgba(98,0,238,0.25); }
        .katex-card-steps { background: rgba(128,128,128,0.10); border: 1px solid rgba(128,128,128,0.30); }
        .katex-card-mistakes { background: rgba(211,47,47,0.08); border: 1px solid rgba(211,47,47,0.28); }
        .katex-card-mistakes .katex-card-title { color: #D32F2F; }
        .katex-card-tips { background: rgba(245,124,0,0.10); border: 1px solid rgba(245,124,0,0.30); }
        .katex-card-tips .katex-card-title { color: #F57C00; }
        .katex-card-general { background: transparent; border: none; padding: 4px 0; }
    """.trimIndent()
}

/**
 * Android WebView ve yerel KaTeX motoru (APK içi assets/katex) ile çalışan,
 * internet bağlantısı gerektirmeyen, sıfır gecikmeli, ÖSYM dizgisi kalitesinde
 * vektörel matematik ve kimya formül görüntüleyicisi.
 *
 * WebView bulunmayan ortamlarda otomatik olarak [fallback] veya [formatMathText]'e düşer.
 */
/**
 * Dokunma olaylarını ana Compose kaydırma konteynerine (verticalScroll) aktaran
 * ve webview tıklama kilitlenmelerini önleyen özel WebView sınıfı.
 */
private class NonTouchableWebView(context: Context) : WebView(context) {
    override fun onTouchEvent(event: android.view.MotionEvent?): Boolean {
        return false
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun KaTeXMathView(
    content: String,
    modifier: Modifier = Modifier,
    fontSizeSp: Float = 15f,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    backgroundColor: Color = Color.Transparent,
    isDarkTheme: Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f,
    displayMode: Boolean = false,
    fallback: (@Composable () -> Unit)? = null,
    // PERF: Birleştirilmiş kart gövdesi verilirse content ikinci kez
    // preprocess edilmez; tek WebView'de bölüm kartları renderlanır.
    combinedBodyHtml: String? = null,
    combinedCss: String = ""
) {
    if (content.isBlank()) return

    var hasWebViewError by remember { mutableStateOf(false) }
    // JavaScript'ten gelen YÜKSEKLİK ÖLÇÜMÜ. Artık bir ÜST SINIR değil, sadece
    // BİR ALT SINIR (taban yükseklik) olarak kullanılır; bkz. Box aşağıda.
    var contentHeightDp by remember { mutableStateOf<Dp?>(null) }
    val density = LocalDensity.current

    if (hasWebViewError) {
        if (fallback != null) {
            fallback()
        } else {
            androidx.compose.material3.Text(
                text = formatMathText(content),
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                modifier = modifier
            )
        }
        return
    }

    val textColorHex = remember(textColor) { KaTeXHtmlGenerator.colorToHex(textColor) }
    val bgColorHex = remember(backgroundColor) {
        if (backgroundColor == Color.Transparent) "transparent" else KaTeXHtmlGenerator.colorToHex(backgroundColor)
    }

    // Genişlik değişimini izlemek için sayaç. update bloğu bunu karşılaştırıp
    // sayfayı yeniden yüklemeden JS ölçümünü tazeler.
    // (lastRemeasuredToken bilinçli olarak Compose state DEĞİLDİR: update bloğunda
    //  state okumak gereksiz yeniden bileşim döngüsü riski yaratır.)
    var remeasureToken by remember { mutableIntStateOf(0) }
    val lastRemeasuredToken = remember { AtomicInteger(-1) }

    // Metin satır sayısı GENİŞLİĞE BAĞLIDIR. Tabletin %45'lik sağ paneli
    // döndürüldüğünde/ekran boyutu değiştiğinde satır sayısı değişir ve eski
    // yükseklik ölçümü yanlış kalır. Genişliği izleyip yeniden ölçüm tetikliyoruz.
    //
    // DÜZELTME: Önceden burada `contentHeightDp = null` yapılıyordu; bu blok önce
    // 24.dp'a ÇÖKÜP sonra yeniden büyüyor, yani genişlik değişiminde ek bir
    // görünür sıçrama üretiyordu. Artık mevcut yükseklik korunuyor, sadece JS'e
    // tazeleme isteği gönderiliyor. Sıfırlama, sayfa yeniden yüklendiğinde
    // yapılır (update bloğu).
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
    val measuredWidth = maxWidth
    LaunchedEffect(measuredWidth) {
        remeasureToken++
    }

    val html = remember(content, combinedBodyHtml, isDarkTheme, textColorHex, bgColorHex, fontSizeSp, displayMode, density.fontScale, combinedCss) {
        if (combinedBodyHtml != null) {
            KaTeXHtmlGenerator.generateHtml(
                content = combinedBodyHtml,
                isDarkTheme = isDarkTheme,
                textColorHex = textColorHex,
                backgroundColorHex = bgColorHex,
                fontSizeSp = fontSizeSp,
                displayMode = displayMode,
                fontScale = density.fontScale,
                skipPreprocess = true,
                extraCss = combinedCss
            )
        } else {
            KaTeXHtmlGenerator.generateHtml(
                content = content,
                isDarkTheme = isDarkTheme,
                textColorHex = textColorHex,
                backgroundColorHex = bgColorHex,
                fontSizeSp = fontSizeSp,
                displayMode = displayMode,
                fontScale = density.fontScale
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                // KRİTİK DÜZELTME: `Modifier.height()` hem minimum hem MAKSİMUM
                // yüksekliği sabitler ve WebView kendi sınırları içinde KIRPAR.
                // JS ölçümü gecikmeli/eksik olduğunda (ilk kare, font yüklenmeden
                // önce, dar panelde) metin cümle ortasından yatay olarak kesiliyordu.
                // Artık ölçüm yalnızca taban yüksekliktir; WebView kendi WRAP_CONTENT
                // ölçümüyle büyüyebilir, taşarsa asla kırpılmaz.
                if (contentHeightDp != null) Modifier.heightIn(min = contentHeightDp!!)
                else Modifier.defaultMinSize(minHeight = if (displayMode) 40.dp else 24.dp)
            )
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            factory = { context ->
                try {
                    NonTouchableWebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        isVerticalScrollBarEnabled = false
                        isHorizontalScrollBarEnabled = false
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            allowFileAccess = true
                            cacheMode = WebSettings.LOAD_DEFAULT
                            textZoom = 100
                        }
                        addJavascriptInterface(
                            object {
                                @JavascriptInterface
                                fun onHeightChange(heightPx: Float) {
                                    post {
                                        val calculatedDp =
                                            (heightPx / context.resources.displayMetrics.density).dp
                                        if (calculatedDp > 0.dp) {
                                            // DÜZELTME: Rapor edilen yükseklik zaten CSS
                                            // body'sinin padding'ini İÇERİR; ayrıca +28.dp
                                            // ekleniyordu (çift sayım). Daha önemlisi:
                                            // JS birden fazla kez rapor ediyor (ilk
                                            // ölçüm, font'lar yüklendikten sonra,
                                            // ResizeObserver). Değer her seferinde
                                            // koşulsuz yazılınca blok birkaç kez
                                            // büyüyüp küçülüyor ve üstteki ortalanmış
                                            // kapta metin GÖRÜNÜR ŞEKİLDE KAYIYORDU.
                                            // Artık bu sayfa yüklemesi boyunca en büyük
                                            // değer tutulur (monotonik); sıfırlama yalnızca
                                            // sayfa yeniden yüklenince yapılır.
                                            val mevcut = contentHeightDp ?: 0.dp
                                            contentHeightDp = maxOf(mevcut, calculatedDp)
                                        }
                                    }
                                }
                            },
                            "AndroidBridge"
                        )
                    }
                } catch (e: Throwable) {
                    hasWebViewError = true
                    WebView(context) // Fallback dummy to satisfy factory
                }
            },
            update = { webView ->
                if (!hasWebViewError) {
                    try {
                        if (webView.tag != html) {
                            webView.tag = html
                            // Sayfa yeniden yükleniyor: monotonik ölçüm tabanını
                            // sıfırla, yoksa ESKİ (daha uzun) yükseklik kalıcı olur.
                            contentHeightDp = null
                            webView.loadDataWithBaseURL(
                                "file:///android_asset/katex/",
                                html,
                                "text/html",
                                "UTF-8",
                                null
                            )
                        } else if (remeasureToken != lastRemeasuredToken.get()) {
                            // Sayfa zaten yüklüyse (ör. panel genişliği değişti) yeniden
                            // yüklemek yerine JS ölçümünü doğrudan tazele. measure probe
                            // dizisi 500 ms'de bitip kapanıyordu ve genişlik değişiminden
                            // sonra taze yükseklik hiç gelmediği için metin kırpılıyordu.
                            lastRemeasuredToken.set(remeasureToken)
                            // Önce genişliğe sığdır, sonra yüksekliği bildir.
                            // Sıralama önemli: fitToWidth yüksekliği değiştirir.
                            webView.evaluateJavascript(
                                "if(window.fitToWidth){window.fitToWidth();}" +
                                    "if(window.notifyHeight){window.notifyHeight();}",
                                null
                            )
                        }
                    } catch (e: Throwable) {
                        hasWebViewError = true
                    }
                }
            },
            onRelease = { webView ->
                // Bellek sızıntısı koruması: sayfa değişince WebView + JS context serbest kalır.
                try {
                    webView.stopLoading()
                    webView.loadUrl("about:blank")
                    webView.clearHistory()
                    webView.removeAllViews()
                    webView.destroy()
                } catch (_: Throwable) {}
            }
        )
    }
    } // BoxWithConstraints — genişlik değişimini izleyerek JS ölçümünü tazeler
}
