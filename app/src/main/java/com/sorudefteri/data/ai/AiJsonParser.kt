package com.sorudefteri.data.ai

import com.sorudefteri.domain.model.AiErrorType
import com.sorudefteri.domain.model.AiException
import com.sorudefteri.domain.model.QuestionMetadata
import com.sorudefteri.domain.model.SimilarQuestionResult
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * AI tarafından üretilen metinlerden JSON verilerini ayıklayan, markdown kod bloklarını temizleyen
 * ve bozuk/eksik JSON çıktılarında Regex fallback ile veriyi kurtaran yardımcı sınıf.
 */
object AiJsonParser {

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        coerceInputValues = true
        prettyPrint = false
    }

    /**
     * Markdown kod bloklarını (```json ... ``` veya ``` ... ```) ve JSON öncesi/sonrası
     * sohbet metinlerini temizleyerek geçerli JSON bloğunu ayıklar.
     */
    fun extractJsonPayload(rawText: String): String {
        if (rawText.isBlank()) return ""

        var text = rawText.trim()

        // Markdown code blocks temizliği (```json ... ``` veya ``` ... ```)
        val codeBlockRegex = Regex("""```(?:json)?\s*([\s\S]*?)\s*```""", RegexOption.IGNORE_CASE)
        val codeBlockMatch = codeBlockRegex.find(text)
        if (codeBlockMatch != null) {
            text = codeBlockMatch.groupValues[1].trim()
        }

        // İlk { veya [ ile son } veya ] arasını bul
        val firstBrace = text.indexOf('{')
        val firstBracket = text.indexOf('[')

        val startIndex = when {
            firstBrace != -1 && firstBracket != -1 -> minOf(firstBrace, firstBracket)
            firstBrace != -1 -> firstBrace
            firstBracket != -1 -> firstBracket
            else -> -1
        }

        val lastBrace = text.lastIndexOf('}')
        val lastBracket = text.lastIndexOf(']')

        val endIndex = maxOf(lastBrace, lastBracket)

        return if (startIndex != -1 && endIndex != -1 && endIndex >= startIndex) {
            text.substring(startIndex, endIndex + 1).trim()
        } else {
            text
        }
    }

    // LaTeX komutları b, f, n, r, t harfleriyle başlayanlar (JSON kaçışlarıyla çakışanlar)
    private val LATEX_COLLISION_REGEX = Regex(
        """(?<!\\)\\(?=(?:[rRfFbB][a-zA-Z]|times|to(?![a-zA-Z])|tau|theta|vartheta|text|tan|tanh|textbf|textit|triangle|top|tilde|therefore|tag|tfrac|tg|cotg|neq|ne(?![a-zA-Z])|neg(?![a-zA-Z])|nabla|notin|nu(?![a-zA-Z])|not(?![a-zA-Z])|natural|ni(?![a-zA-Z])|nexists|ngeq|nleq|nsubseteq|nsupseteq|nparallel|nsim|ncong|nmid|nearrow|nwarrow))"""
    )

    private val GENERAL_LATEX_REGEX = Regex(
        """(?<!\\)\\(?!(?:["\\/bfnrt]|u[0-9a-fA-F]{4}))"""
    )

    private val SAFE_NEWLINE_REGEX = Regex(
        """(?<!\\)\\n(?!(?:eq|e(?![a-zA-Z])|eg(?![a-zA-Z])|abla|otin|u(?![a-zA-Z])|ot(?![a-zA-Z])|atural|i(?![a-zA-Z])|exists|geq|leq|subseteq|supseteq|parallel|sim|cong|mid|earrow|warrow)(?![a-zA-Z]))"""
    )

    /**
     * LaTeX kaçış karakterlerini (ör: \alpha, \frac, \int vb.) JSON'un kırılmaması için çift ters eğik çizgiye çevirir.
     * Standart JSON kaçışlarını (\n, \t, \r, \", \\) bozmaz; \nI. veya \nyargı... gibi satır sonlarını korur.
     */
    fun sanitizeLatexEscapesInJson(jsonStr: String): String {
        // 1. Önce JSON çakışması olan bilinen LaTeX komutlarını kaçışla (\neq -> \\neq, \rightarrow -> \\rightarrow, \frac -> \\frac, vb.)
        var text = LATEX_COLLISION_REGEX.replace(jsonStr) { "\\\\" }

        // 2. Standart JSON kaçışları dışındaki tüm tek ters eğik çizgileri kaçışla (\alpha -> \\alpha, \Phi -> \\Phi vb.)
        text = GENERAL_LATEX_REGEX.replace(text) { "\\\\" }

        return text
    }

    /**
     * Metindeki izole edilmiş escape karakterlerini (\n, \r) güvenli biçimde dönüştürür.
     * LaTeX komutlarını (\rightarrow, \right, \rho, \rangle, \neq, \nu, \nabla, \notin, \neg, \frac, \to vb.)
     * ASLA bozmaz.
     */
    fun normalizeSafeEscapes(str: String): String {
        var text = str.replace("\r\n", "\n").replace("\r", "")
        text = text.replace("\\r\\n", "\n")
        // Harfle devam etmeyen izole \r'leri temizle (\rightarrow, \right vb. LaTeX komutlarını korur)
        text = text.replace(Regex("""\\r(?![a-zA-Z])"""), "")
        // LaTeX komutu olmayan izole \n kaçışlarını gerçek satır sonuna çevir
        text = SAFE_NEWLINE_REGEX.replace(text, "\n")
        return text
    }

    /**
     * Çözümlenen nesnelerdeki literal \n kaçışlarını güvenle normalize eder.
     */
    private fun cleanEscapedNewlines(str: String): String {
        return normalizeSafeEscapes(str).trim()
    }

    private fun <T> postProcessDecoded(obj: T): T {
        if (obj is SimilarQuestionResult) {
            val normalizedOptions = obj.options.mapValues { (_, v) ->
                cleanEscapedNewlines(v)
            }
            @Suppress("UNCHECKED_CAST")
            return obj.copy(
                questionText = cleanEscapedNewlines(obj.questionText),
                options = normalizedOptions,
                solution = cleanEscapedNewlines(obj.solution)
            ) as T
        }
        return obj
    }

    /**
     * Genel JSON çözümleme metodu (Önce sanitize, ardından temiz candidate ve opsiyonel fallback).
     */
    fun <T> cleanAndParseJson(
        rawText: String,
        serializer: KSerializer<T>,
        fallbackParser: ((String) -> T?)? = null
    ): Result<T> {
        val cleanCandidate = extractJsonPayload(rawText)

        // 1. LaTeX kaçışlarını sanitize ederek dene (tek kaçışlı \rightarrow, \neq, \frac vb. sessiz JSON bozulmalarını önler)
        try {
            val sanitized = sanitizeLatexEscapesInJson(cleanCandidate)
            val decoded = json.decodeFromString(serializer, sanitized)
            return Result.success(postProcessDecoded(decoded))
        } catch (_: Exception) {
        }

        // 2. Standart decode denemesi (sanitize edilmemiş temiz payload ile dene)
        try {
            val decoded = json.decodeFromString(serializer, cleanCandidate)
            return Result.success(postProcessDecoded(decoded))
        } catch (_: Exception) {
        }

        // 3. Fallback parser (Regex tabanlı) - önce ham text (tam çözüm içerir), sonra temizlenmiş candidate
        if (fallbackParser != null) {
            try {
                val fallbackResult = fallbackParser(rawText)
                if (fallbackResult != null) {
                    return Result.success(postProcessDecoded(fallbackResult))
                }
            } catch (_: Exception) {
            }
            try {
                val fallbackResult2 = fallbackParser(cleanCandidate)
                if (fallbackResult2 != null) {
                    return Result.success(postProcessDecoded(fallbackResult2))
                }
            } catch (_: Exception) {
            }
        }

        return Result.failure(
            AiException(
                errorType = AiErrorType.PARSING_FAILED,
                userFriendlyMessage = "AI yanıtı beklenen JSON formatında çözümlenemedi. Ham yanıt: ${rawText.take(150)}"
            )
        )
    }

    /**
     * SimilarQuestionResult için hem esnek JSON hem de düz metin (plain text) Regex kurtarma parser'ı.
     * Kullanıcı ChatGPT, Gemini, Claude vb. chatbotlardan JSON VEYA normal düz metin ("Soru: ... A) ... Çözüm: ...")
     * yapıştırdığında tüm bileşenleri eksiksiz ayıklar.
     */
    fun parseSimilarQuestionFallback(rawText: String): SimilarQuestionResult? {
        // 1. Önce esnek JSON regex ile ayrıştırmayı dene
        val jsonResult = parseSimilarQuestionJsonRegex(rawText)
        if (jsonResult != null && (jsonResult.questionText.isNotBlank() || jsonResult.options.isNotEmpty())) {
            return jsonResult
        }

        // 2. Düz metin (plain text / serbest format) ile ayrıştırmayı dene
        val plainTextResult = parseSimilarQuestionPlainText(rawText)
        if (plainTextResult != null && (plainTextResult.questionText.isNotBlank() || plainTextResult.options.isNotEmpty())) {
            return plainTextResult
        }

        return null
    }

    /**
     * Bozuk veya tırnaksız/eksik JSON çıktılarından Regex ile SimilarQuestionResult ayıklar.
     */
    fun parseSimilarQuestionJsonRegex(rawText: String): SimilarQuestionResult? {
        try {
            // Soru metni (tırnaklı veya tırnaksız anahtarlar)
            val questionTextRegex = Regex(
                """"?(?:questionText|question|soru|soruMetni)"?\s*:\s*["']((?:[^"'\\]|\\.)*)["']""",
                RegexOption.IGNORE_CASE
            )
            val qMatch = questionTextRegex.find(rawText)
            val questionText = qMatch?.groupValues?.get(1)?.let { normalizeSafeEscapes(it) }?.replace("\\\"", "\"")?.trim().orEmpty()

            // Şıklar (A, B, C, D, E)
            val options = mutableMapOf<String, String>()
            val optLetters = listOf("A", "B", "C", "D", "E")
            for (letter in optLetters) {
                val optRegex = Regex(
                    """"?$letter"?\s*:\s*["']((?:[^"'\\]|\\.)*)["']""",
                    RegexOption.IGNORE_CASE
                )
                val match = optRegex.find(rawText)
                if (match != null) {
                    val optVal = normalizeSafeEscapes(match.groupValues[1]).replace("\\\"", "\"").trim()
                    if (optVal.isNotBlank()) {
                        options[letter] = optVal
                    }
                }
            }

            // Doğru cevap
            val ansRegex = Regex(
                """"?(?:correctAnswer|dogruCevap|cevap)"?\s*:\s*["']?([A-Ea-e])["']?""",
                RegexOption.IGNORE_CASE
            )
            val ansMatch = ansRegex.find(rawText)
            val correctAnswer = ansMatch?.groupValues?.get(1)?.uppercase() ?: "A"

            // Çözüm
            val solRegex = Regex(
                """"?(?:solution|cozum|açıklama|aciklama)"?\s*:\s*["']((?:[^"'\\]|\\.)*)["']""",
                RegexOption.IGNORE_CASE
            )
            val solMatch = solRegex.find(rawText)
            val solution = solMatch?.groupValues?.get(1)?.let { normalizeSafeEscapes(it) }?.replace("\\\"", "\"")?.trim().orEmpty()

            if (questionText.isNotBlank() || options.isNotEmpty()) {
                return SimilarQuestionResult(
                    questionText = questionText,
                    options = options,
                    correctAnswer = correctAnswer,
                    solution = solution
                )
            }
        } catch (_: Exception) {
        }
        return null
    }

    /**
     * Harici yapay zekalardan kopyalanan serbest biçimli düz metinleri
     * (Örn: "Soru: ... A) ... B) ... Doğru Cevap: ... Çözüm: ...") ayrıştırır.
     */
    fun parseSimilarQuestionPlainText(rawText: String): SimilarQuestionResult? {
        if (rawText.isBlank()) return null
        try {
            var text = normalizeSafeEscapes(rawText).trim()

            // Markdown kod bloklarını temizle (varsa)
            val codeBlockRegex = Regex("""```(?:json|[a-zA-Z]*)?\s*([\s\S]*?)\s*```""", RegexOption.IGNORE_CASE)
            val codeBlockMatch = codeBlockRegex.find(text)
            if (codeBlockMatch != null) {
                text = codeBlockMatch.groupValues[1].trim()
            }

            // 1. Doğru Cevap Ayıklama
            val ansRegex = Regex(
                """(?:\*\*)?(?:Doğru\s*Cevap|Doğru\s*Seçenek|Doğru\s*Şık|Cevap|Yanıt|Correct\s*Answer)\s*(?:\*\*)?\s*[:：=]?\s*(?:\*\*)?(?:\[|\()?([A-Ea-e])(?:\]|\))?\b""",
                RegexOption.IGNORE_CASE
            )
            var correctAnswer = ansRegex.find(text)?.groupValues?.get(1)?.uppercase()

            // 2. Çözüm Ayıklama (Çözüm / Açıklama / Solution etiketinden sonrası)
            val solRegex = Regex(
                """(?m)(?:^|\n)\s*(?:\*\*)?(?:Çözüm|Ayrıntılı\s*Çözüm|Adım\s*Adım\s*Çözüm|Detaylı\s*Çözüm|Açıklama|Solution)\s*(?:\*\*)?\s*[:：]?\s*([\s\S]*)""",
                RegexOption.IGNORE_CASE
            )
            val solMatch = solRegex.find(text)
            var solution = solMatch?.groupValues?.get(1)?.trim().orEmpty()

            // Eğer çözüm içinde de "Doğru Cevap: B" varsa ve correctAnswer henüz bulunamadıysa yakala
            if (correctAnswer == null) {
                correctAnswer = ansRegex.find(solution)?.groupValues?.get(1)?.uppercase()
            }

            // 3. Şıkları (A, B, C, D, E) Ayıklama
            val options = mutableMapOf<String, String>()

            // Çok satırlı şık formatı: A) ... veya - A) ... veya **A)** ... veya A. ... veya A - ...
            val multiLineOptRegex = Regex(
                """(?m)(?:^|\n)\s*(?:[-*•]\s*)?(?:\*\*)?(?:\[|\()?([A-Ea-e])(?:\]|\))?[\.\:\-]?\s*(?:\*\*)?\s*(.+?)(?=(?:\r?\n\s*(?:[-*•]\s*)?(?:\*\*)?(?:\[|\()?[A-Ea-e](?:\]|\))?[\.\:\-])|(?:\r?\n\s*(?:\*\*)?(?:Doğru\s*Cevap|Cevap|Doğru\s*Seçenek|Doğru\s*Şık|Yanıt|Correct\s*Answer|Çözüm|Açıklama|Solution))|\Z)""",
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
            )
            val matches = multiLineOptRegex.findAll(text).toList()

            // En az 2 şık eşleştiyse çok satırlı şıkları kabul et
            if (matches.size >= 2) {
                for (m in matches) {
                    val key = m.groupValues[1].uppercase()
                    var optText = m.groupValues[2].trim()
                    // Şık metni sonundaki olası "Doğru Cevap" veya "Çözüm" artıklarını temizle
                    optText = optText.replace(Regex("""(?i)\s*(?:\*\*)?(?:Doğru\s*Cevap|Cevap|Çözüm|Açıklama).*$"""), "").trim()
                    if (optText.isNotBlank()) {
                        options[key] = optText
                    }
                }
            }

            // Eğer çok satırlı eşleşme bulunamadıysa tek satırda yan yana şıkları dene (A) 10 B) 20 C) 30 ...)
            if (options.size < 2) {
                val inlineRegex = Regex(
                    """(?:^|[\s,;])(?:\*\*)?(?:\[|\()?([A-Ea-e])(?:\]|\))?[\.\:\-]?\s*(?:\*\*)?\s*([^\n\r]+?)(?=(?:\s+(?:\*\*)?(?:\[|\()?[A-Ea-e](?:\]|\))?[\.\:\-])|\s*(?:Doğru\s*Cevap|Cevap|Çözüm|Açıklama)|\Z)""",
                    RegexOption.IGNORE_CASE
                )
                val inlineMatches = inlineRegex.findAll(text).toList()
                for (m in inlineMatches) {
                    val key = m.groupValues[1].uppercase()
                    val optText = m.groupValues[2].trim()
                    if (optText.isNotBlank() && !options.containsKey(key)) {
                        options[key] = optText
                    }
                }
            }

            // 4. Soru Metnini Ayıklama
            // Soru metni: Şıklar başlamadan veya Çözüm/Cevap etiketinden önceki metindir.
            var questionEndIndex = text.length
            if (matches.isNotEmpty()) {
                questionEndIndex = minOf(questionEndIndex, matches.first().range.first)
            }
            if (solMatch != null) {
                questionEndIndex = minOf(questionEndIndex, solMatch.range.first)
            }
            val ansMatchAll = ansRegex.find(text)
            if (ansMatchAll != null && ansMatchAll.range.first < questionEndIndex) {
                // Eğer cevap soru metninden önce değilse soru sonunu cevap öncesi al
                val beforeAns = text.substring(0, ansMatchAll.range.first)
                if (beforeAns.isNotBlank() && matches.isEmpty()) {
                    questionEndIndex = minOf(questionEndIndex, ansMatchAll.range.first)
                }
            }

            var questionCandidate = if (questionEndIndex > 0 && questionEndIndex <= text.length) {
                text.substring(0, questionEndIndex).trim()
            } else {
                text
            }

            // "Soru:", "**Soru:**", "Soru Metni:" gibi etiketleri baştan temizle
            val soruMarkerRegex = Regex(
                """(?m)(?:^|\n)\s*(?:\*\*)?(?:Soru|Soru\s*Metni|Benzer\s*Soru|Örnek\s*Soru)\s*(?:\*\*)?\s*[:：]?\s*""",
                RegexOption.IGNORE_CASE
            )
            val markerMatch = soruMarkerRegex.find(questionCandidate)
            var questionText = if (markerMatch != null) {
                questionCandidate.substring(markerMatch.range.last + 1).trim()
            } else {
                questionCandidate
            }

            // Chatbot giriş tebrik/selamlama kalıplarını temizle ("Tabii ki! İşte benzer soru:", "Elbette...")
            questionText = questionText.replace(Regex("""(?i)^(?:Tabii(?: ki)?|Elbette|İşte|Harika|Peki)[^\n]*[.:!]\s*"""), "")
            questionText = questionText.replace(Regex("""(?i)^Here is a similar question[^\n]*[.:!]\s*"""), "")
            questionText = questionText.replace(Regex("""(?i)^Aşağıda(?:ki)?[^\n]*soru[^\n]*[.:!]\s*"""), "")
            // Soru metni sonundaki "Seçenekler:" veya "**Şıklar:**" başlığını temizle
            questionText = questionText.replace(Regex("""(?i)\s*(?:\*\*)?(?:Seçenekler|Şıklar|Options)\s*(?:\*\*)?\s*[:：]?\s*$"""), "")
            questionText = questionText.trim()

            // Doğru cevap bulunamadıysa şıklardan ilkini veya A şıkkını varsay
            if (correctAnswer == null) {
                correctAnswer = options.keys.firstOrNull() ?: "A"
            }

            // 5. Doğrulama: Düz metin ayrıştırmasında en az 2 şık veya şık+soru bulunmalıdır.
            // Sadece hata/özür mesajı ("Üzgünüm...") içeren metinler soru olarak kabul edilmez.
            if (questionText.isNotBlank() && (options.size >= 2 || (options.isNotEmpty() && correctAnswer.isNotBlank()))) {
                return SimilarQuestionResult(
                    questionText = questionText,
                    options = options,
                    correctAnswer = correctAnswer,
                    solution = solution
                )
            }
        } catch (_: Exception) {
        }
        return null
    }

    /**
     * QuestionMetadata için Regex tabanlı kurtarma fallback'i.
     */
    fun parseQuestionMetadataFallback(rawText: String): QuestionMetadata? {
        try {
            val examRegex = Regex(""""(?:examType|sinavTuru|exam)"\s*:\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
            val examType = examRegex.find(rawText)?.groupValues?.get(1)?.uppercase() ?: "TYT"

            val subjectRegex = Regex(""""(?:subjectId|ders|dersId)"\s*:\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
            val subjectId = subjectRegex.find(rawText)?.groupValues?.get(1)?.lowercase() ?: "matematik"

            val topicRegex = Regex(""""(?:topic|konu|unite)"\s*:\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
            val topic = topicRegex.find(rawText)?.groupValues?.get(1)?.trim().orEmpty()

            val ansRegex = Regex(""""(?:correctAnswer|dogruCevap|cevap)"\s*:\s*"([A-Ea-e])"""", RegexOption.IGNORE_CASE)
            val correctAnswer = ansRegex.find(rawText)?.groupValues?.get(1)?.uppercase()

            return QuestionMetadata(
                examType = if (examType.contains("AYT")) "AYT" else "TYT",
                subjectId = subjectId,
                topic = topic,
                correctAnswer = correctAnswer
            )
        } catch (_: Exception) {
        }
        return null
    }
}
