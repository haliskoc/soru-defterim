package com.sorudefteri.data.ai

import com.sorudefteri.domain.model.AiErrorType
import com.sorudefteri.domain.model.AiException
import com.sorudefteri.domain.model.QuestionMetadata
import com.sorudefteri.domain.model.SimilarQuestionResult
import com.sorudefteri.domain.model.TwinQuestion
import com.sorudefteri.domain.model.TwinQuestionsResult
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
        if (obj is TwinQuestionsResult) {
            val processedQuestions = obj.questions.map { q ->
                val normalizedOptions = q.options.mapValues { (_, v) ->
                    cleanEscapedNewlines(v)
                }
                q.copy(
                    questionText = cleanEscapedNewlines(q.questionText),
                    options = normalizedOptions,
                    solution = cleanEscapedNewlines(q.solution),
                    trapWarning = q.trapWarning?.let { cleanEscapedNewlines(it) }
                )
            }
            @Suppress("UNCHECKED_CAST")
            return obj.copy(
                originalTopic = obj.originalTopic.trim(),
                questions = processedQuestions
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
                """(?m)(?:^|\n)\s*(?:\*\*)?(?:Çözüm|Ayrıntılı\s*Çözüm|Adım\s*Adım\s*Çözüm|Detaylı\s*Çözüm|Açıklama|Solution)\s*(?:\*\*)?\s*[:：]?\s*([\s\S]*?)(?=(?:\r?\n\s*(?:\*\*)?(?:Tuzak|Tuzak\s*Uyarısı|Tuzak\s*Analizi|Trap\s*Warning))|\Z)""",
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
                """(?m)(?:^|\n)\s*(?:[-*•]\s*)?(?:\*\*)?(?:\[|\()?([A-Ea-e])(?:(?:\]|\))|[\.\:\-])\s*(?:\*\*)?\s*(.+?)(?=(?:\r?\n\s*(?:[-*•]\s*)?(?:\*\*)?(?:\[|\()?[A-Ea-e](?:(?:\]|\))|[\.\:\-])\s*)|(?:\r?\n\s*(?:\*\*)?(?:Doğru\s*Cevap|Cevap|Doğru\s*Seçenek|Doğru\s*Şık|Yanıt|Correct\s*Answer|Çözüm|Açıklama|Solution|Tuzak))|\Z)""",
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

    /**
     * LLM'lerin ürettiği bozuk JSON yapılarını onarır:
     * - Fazlalık trailing comma'ları (, } veya , ]) temizler
     * - Eğer kök nesne dizi ([...]) ise {"questions": [...]} ile sarar
     * - Alternatif alan isimlerini (twinQuestions, sorular -> questions) standartlaştırır
     * - Kapanmayan tırnak ve eksik kapanış parantezlerini (}, ]) tamamlar
     */
    fun repairJsonStructure(jsonStr: String): String {
        if (jsonStr.isBlank()) return ""
        var text = jsonStr.trim()

        // 1. Dizi olarak döndüyse nesneye sar
        if (text.startsWith("[") && text.endsWith("]")) {
            text = "{\"questions\": $text}"
        } else if (text.startsWith("[") && !text.endsWith("}") && !text.endsWith("]")) {
            text = "{\"questions\": $text"
        }

        // 2. Alternatif anahtar isimlerini eşle
        text = text.replace(Regex(""""(?:twinQuestions|sorular|kardesSorular)"\s*:""", RegexOption.IGNORE_CASE), "\"questions\":")
        text = text.replace(Regex(""""(?:konu|dersKonusu|kazanim)"\s*:""", RegexOption.IGNORE_CASE), "\"originalTopic\":")

        // 3. Trailing commas (virgülden sonra doğrudan kapanan süslü veya köşeli parantez)
        text = text.replace(Regex(""",\s*([}\]])"""), "$1")

        // 4. Kapanmayan parantezleri ve köşeli parantezleri tamamla
        var openBraces = 0
        var openBrackets = 0
        var inString = false
        var isEscaped = false

        for (c in text) {
            if (isEscaped) {
                isEscaped = false
                continue
            }
            if (c == '\\') {
                isEscaped = true
                continue
            }
            if (c == '"') {
                inString = !inString
                continue
            }
            if (!inString) {
                when (c) {
                    '{' -> openBraces++
                    '}' -> if (openBraces > 0) openBraces--
                    '[' -> openBrackets++
                    ']' -> if (openBrackets > 0) openBrackets--
                }
            }
        }

        if (inString) {
            text += "\""
        }
        if (openBrackets > 0 || openBraces > 0) {
            val sb = StringBuilder(text)
            repeat(openBrackets) { sb.append("]") }
            repeat(openBraces) { sb.append("}") }
            text = sb.toString()
        }

        text = text.replace(Regex(""",\s*([}\]])"""), "$1")
        return text
    }

    /**
     * JSON metni içindeki dengeli süslü parantezli ({ ... }) en üst seviye nesneleri ayıklar.
     * İç içe nesneleri (örn: options: { A: ... }) doğru derinlikle takip eder.
     */
    fun extractTopLevelJsonObjects(text: String): List<String> {
        val objects = mutableListOf<String>()
        var depth = 0
        var startIndex = -1
        var inString = false
        var isEscaped = false

        for (i in text.indices) {
            val c = text[i]

            if (isEscaped) {
                isEscaped = false
                continue
            }

            if (c == '\\') {
                isEscaped = true
                continue
            }

            if (c == '"') {
                inString = !inString
                continue
            }

            if (!inString) {
                if (c == '{') {
                    if (depth == 0) {
                        startIndex = i
                    }
                    depth++
                } else if (c == '}') {
                    depth--
                    if (depth == 0 && startIndex != -1) {
                        objects.add(text.substring(startIndex, i + 1))
                        startIndex = -1
                    } else if (depth < 0) {
                        depth = 0
                    }
                }
            }
        }
        return objects
    }

    /**
     * Ham metin veya JSON dizisinden soru nesnelerini ayıklar.
     */
    fun extractQuestionObjects(text: String): List<String> {
        val questionsArrayMatch = Regex(""""(?:questions|twinQuestions|sorular|kardesSorular)"\s*:\s*\[""", RegexOption.IGNORE_CASE).find(text)
        val searchSubstr = if (questionsArrayMatch != null) {
            text.substring(questionsArrayMatch.range.last)
        } else {
            text
        }
        val allObjs = extractTopLevelJsonObjects(searchSubstr)
        return allObjs.filter { objStr ->
            objStr.contains("questionText", ignoreCase = true) ||
            objStr.contains("options", ignoreCase = true) ||
            objStr.contains("correctAnswer", ignoreCase = true)
        }
    }

    /**
     * Düz metin halindeki çoklu soru çıktılarını ayrıştırır (Örn: 1. Soru: ... A) ... 2. Soru: ...).
     */
    fun parseTwinQuestionsPlainText(rawText: String): TwinQuestionsResult? {
        val topicRegex = Regex("""(?m)(?:^|\n)\s*(?:\*\*)?(?:Konu|Topic|Kazanım|originalTopic)\s*(?:\*\*)?\s*[:：]?\s*([^\n\r]+)""", RegexOption.IGNORE_CASE)
        val topic = topicRegex.find(rawText)?.groupValues?.get(1)?.trim().orEmpty()

        val questionSplitRegex = Regex("""(?m)(?:^|\n)\s*(?:\*\*)?(?:[1-3][\.\)]\s*Soru|Soru\s*[1-3]|Kardeş\s*Soru\s*[1-3]|#+\s*[1-3][\.\)])\s*[:：]?""", RegexOption.IGNORE_CASE)
        val matches = questionSplitRegex.findAll(rawText).toList()
        if (matches.isEmpty()) return null

        val questions = mutableListOf<TwinQuestion>()
        for (i in matches.indices) {
            val start = matches[i].range.first
            val end = if (i + 1 < matches.size) matches[i + 1].range.first else rawText.length
            val section = rawText.substring(start, end).trim()

            val singleResult = parseSimilarQuestionFallback(section)
            if (singleResult != null && (singleResult.questionText.isNotBlank() || singleResult.options.isNotEmpty())) {
                val trapRegex = Regex("""(?:\*\*)?(?:Tuzak\s*Uyarısı|Tuzak\s*Analizi|Tuzak|Kavram\s*Tuzağı|Trap\s*Warning)\s*(?:\*\*)?\s*[:：]?\s*([^\n\r]+(?:\n(?!\s*(?:[A-E][\.\)]|Çözüm|Doğru\s*Cevap))[^\n\r]+)*)""", RegexOption.IGNORE_CASE)
                val trapWarning = trapRegex.find(section)?.groupValues?.get(1)?.trim()

                questions.add(
                    TwinQuestion(
                        id = i + 1,
                        questionText = singleResult.questionText,
                        options = singleResult.options,
                        correctAnswer = singleResult.correctAnswer,
                        solution = singleResult.solution,
                        trapWarning = trapWarning
                    )
                )
            }
        }

        if (questions.isNotEmpty()) {
            return TwinQuestionsResult(originalTopic = topic, questions = questions)
        }
        return null
    }

    /**
     * Regex ve yapısal analiz tabanlı TwinQuestionsResult kurtarma fallback'i.
     */
    fun parseTwinQuestionsFallback(rawText: String): TwinQuestionsResult? {
        if (rawText.isBlank()) return null

        val topicRegex = Regex(""""?(?:originalTopic|topic|konu|kazanim)"?\s*:\s*["']((?:[^"'\\]|\\.)*)["']""", RegexOption.IGNORE_CASE)
        val originalTopic = topicRegex.find(rawText)?.groupValues?.get(1)?.let { normalizeSafeEscapes(it) }?.trim().orEmpty()

        // 1. JSON nesnesi ayıklama yöntemi
        val questionObjs = extractQuestionObjects(rawText)
        if (questionObjs.isNotEmpty()) {
            val questions = mutableListOf<TwinQuestion>()
            for ((index, objStr) in questionObjs.withIndex()) {
                val qId = Regex(""""?id"?\s*:\s*(\d+)""", RegexOption.IGNORE_CASE)
                    .find(objStr)?.groupValues?.get(1)?.toIntOrNull() ?: (index + 1)

                val similar = parseSimilarQuestionFallback(objStr)
                val trapRegex = Regex(""""?(?:trapWarning|tuzak|tuzakUyarisi|tuzakAnalizi)"?\s*:\s*["']((?:[^"'\\]|\\.)*)["']""", RegexOption.IGNORE_CASE)
                val trapWarning = trapRegex.find(objStr)?.groupValues?.get(1)?.let { normalizeSafeEscapes(it) }?.trim()

                if (similar != null && (similar.questionText.isNotBlank() || similar.options.isNotEmpty())) {
                    questions.add(
                        TwinQuestion(
                            id = qId,
                            questionText = similar.questionText,
                            options = similar.options,
                            correctAnswer = similar.correctAnswer,
                            solution = similar.solution,
                            trapWarning = trapWarning
                        )
                    )
                }
            }
            if (questions.isNotEmpty()) {
                return TwinQuestionsResult(originalTopic = originalTopic, questions = questions)
            }
        }

        // 2. Düz metin ayrıştırma yöntemi
        val plainTextResult = parseTwinQuestionsPlainText(rawText)
        if (plainTextResult != null && plainTextResult.questions.isNotEmpty()) {
            return if (originalTopic.isNotBlank() && plainTextResult.originalTopic.isBlank()) {
                plainTextResult.copy(originalTopic = originalTopic)
            } else {
                plainTextResult
            }
        }

        return null
    }

    /**
     * Yanlış Soru İkizi (3 Soruluk Kardeş Test) için AI JSON veya metin yanıtını ayrıştırır.
     * Markdown kod bloklarını (```json ... ```), kaçışsız LaTeX komutlarını (\frac, \sqrt),
     * eksik kapanış parantezlerini, trailing virgülleri ve serbest metin çıktılarını onararak
     * dayanıklı (resilient) biçimde TwinQuestionsResult nesnesine dönüştürür.
     */
    fun parseTwinQuestionsResult(rawJson: String): Result<TwinQuestionsResult> {
        if (rawJson.isBlank()) {
            return Result.failure(
                AiException(
                    errorType = AiErrorType.PARSING_FAILED,
                    userFriendlyMessage = "AI yanıtı boş geldi."
                )
            )
        }

        val cleanCandidate = extractJsonPayload(rawJson)

        // 1. Standart cleanAndParseJson denemesi (LaTeX sanitization + standart decode)
        val standardResult = cleanAndParseJson(
            rawText = rawJson,
            serializer = TwinQuestionsResult.serializer(),
            fallbackParser = { parseTwinQuestionsFallback(it) }
        )
        if (standardResult.isSuccess && standardResult.getOrNull()?.questions?.isNotEmpty() == true) {
            return standardResult
        }

        // 2. Yapısal onarım (repairJsonStructure: trailing comma, array sarma, eksik parantez) sonrası deneme
        val repaired = repairJsonStructure(cleanCandidate)
        val repairedResult = cleanAndParseJson(
            rawText = repaired,
            serializer = TwinQuestionsResult.serializer(),
            fallbackParser = { parseTwinQuestionsFallback(it) }
        )
        if (repairedResult.isSuccess && repairedResult.getOrNull()?.questions?.isNotEmpty() == true) {
            return repairedResult
        }

        // 3. Doğrudan fallback parser (Regex / Parantez dengeleyici / Düz metin)
        val fallbackResult = parseTwinQuestionsFallback(rawJson)
            ?: parseTwinQuestionsFallback(cleanCandidate)
            ?: parseTwinQuestionsFallback(repaired)

        if (fallbackResult != null && fallbackResult.questions.isNotEmpty()) {
            return Result.success(postProcessDecoded(fallbackResult))
        }

        return standardResult.exceptionOrNull()?.let { Result.failure(it) }
            ?: Result.failure(
                AiException(
                    errorType = AiErrorType.PARSING_FAILED,
                    userFriendlyMessage = "3'lü kardeş soru JSON çıktısı çözümlenemedi. Ham yanıt: ${rawJson.take(150)}"
                )
            )
    }
}
