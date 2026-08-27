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

    /**
     * LaTeX kaçış karakterlerini (ör: \alpha, \frac, \int vb.) JSON'un kırılmaması için çift ters eğik çizgiye çevirir.
     */
    fun sanitizeLatexEscapesInJson(jsonStr: String): String {
        // Standart JSON kaçışları: \", \\, \/, \b, \f, \n, \r, \t, \uXXXX
        // LaTeX komutları \frac, \to, \neq vb. için \f, \t, \n, \b, \r tek başına valide sayılır ama
        // \f + letter (frac), \t+letter (to), \n+letter (neq), \b+letter (binom), \r+letter (rightarrow) ise LaTeX'tir ve kaçışlanmalı
        val regex = Regex("""\\(?!(?:["\\/]|b(?![a-zA-Z])|f(?![a-zA-Z])|n(?![a-zA-Z])|r(?![a-zA-Z])|t(?![a-zA-Z])|u[0-9a-fA-F]{4}))""")
        return jsonStr.replace(regex, "\\\\")
    }

    /**
     * Genel JSON çözümleme metodu (Önce temizleme, sonra sanitize, ardından opsiyonel fallback).
     */
    fun <T> cleanAndParseJson(
        rawText: String,
        serializer: KSerializer<T>,
        fallbackParser: ((String) -> T?)? = null
    ): Result<T> {
        val cleanCandidate = extractJsonPayload(rawText)

        // 1. Standart decode denemesi
        try {
            val decoded = json.decodeFromString(serializer, cleanCandidate)
            return Result.success(decoded)
        } catch (_: Exception) {
        }

        // 2. LaTeX kaçışlarını sanitize ederek tekrar dene
        try {
            val sanitized = sanitizeLatexEscapesInJson(cleanCandidate)
            val decoded = json.decodeFromString(serializer, sanitized)
            return Result.success(decoded)
        } catch (_: Exception) {
        }

        // 3. Fallback parser (Regex tabanlı) - önce ham text (tam çözüm içerir), sonra temizlenmiş candidate
        if (fallbackParser != null) {
            try {
                val fallbackResult = fallbackParser(rawText)
                if (fallbackResult != null) {
                    return Result.success(fallbackResult)
                }
            } catch (_: Exception) {
            }
            try {
                val fallbackResult2 = fallbackParser(cleanCandidate)
                if (fallbackResult2 != null) {
                    return Result.success(fallbackResult2)
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
     * SimilarQuestionResult için Regex tabanlı kurtarma fallback'i.
     */
    fun parseSimilarQuestionFallback(rawText: String): SimilarQuestionResult? {
        try {
            // Soru metni
            val questionTextRegex = Regex(""""(?:questionText|question|soru|soruMetni)"\s*:\s*"((?:[^"\\]|\\.)*)"""", RegexOption.IGNORE_CASE)
            val qMatch = questionTextRegex.find(rawText)
            val questionText = qMatch?.groupValues?.get(1)?.replace("\\n", "\n")?.replace("\\\"", "\"")?.trim().orEmpty()

            // Şıklar (A, B, C, D, E)
            val options = mutableMapOf<String, String>()
            val optLetters = listOf("A", "B", "C", "D", "E")
            for (letter in optLetters) {
                val optRegex = Regex(""""$letter"\s*:\s*"((?:[^"\\]|\\.)*)"""", RegexOption.IGNORE_CASE)
                val match = optRegex.find(rawText)
                if (match != null) {
                    options[letter] = match.groupValues[1].replace("\\n", "\n").replace("\\\"", "\"").trim()
                }
            }

            // Doğru cevap
            val ansRegex = Regex(""""(?:correctAnswer|dogruCevap|cevap)"\s*:\s*"([A-Ea-e])"""", RegexOption.IGNORE_CASE)
            val ansMatch = ansRegex.find(rawText)
            val correctAnswer = ansMatch?.groupValues?.get(1)?.uppercase() ?: "A"

            // Çözüm
            val solRegex = Regex(""""(?:solution|cozum|açıklama|aciklama)"\s*:\s*"((?:[^"\\]|\\.)*)"""", RegexOption.IGNORE_CASE)
            val solMatch = solRegex.find(rawText)
            val solution = solMatch?.groupValues?.get(1)?.replace("\\n", "\n")?.replace("\\\"", "\"")?.trim().orEmpty()

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
