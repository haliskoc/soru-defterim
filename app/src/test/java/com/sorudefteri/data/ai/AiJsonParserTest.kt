package com.sorudefteri.data.ai

import com.sorudefteri.domain.model.AiErrorType
import com.sorudefteri.domain.model.AiException
import com.sorudefteri.domain.model.QuestionMetadata
import com.sorudefteri.domain.model.SimilarQuestionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiJsonParserTest {

    @Test
    fun `extractJsonPayload should strip markdown fences and outer conversational text`() {
        val raw = """
            Tabii ki! İşte ürettiğim benzer soru:
            ```json
            {
              "questionText": "f(x) = x^2 fonksiyonunun türevi nedir?",
              "options": {
                "A": "2x",
                "B": "x",
                "C": "x^2",
                "D": "2",
                "E": "0"
              },
              "correctAnswer": "A",
              "solution": "Türev kuralı: d/dx(x^n) = n*x^(n-1). Buradan 2x bulunur."
            }
            ```
            Umarım faydalı olur, başarılar!
        """.trimIndent()

        val extracted = AiJsonParser.extractJsonPayload(raw)
        assertTrue(extracted.startsWith("{"))
        assertTrue(extracted.endsWith("}"))
    }

    @Test
    fun `cleanAndParseJson should decode valid SimilarQuestionResult with markdown fences`() {
        val raw = """
            ```json
            {
              "questionText": "Aşağıdakilerden hangisi bir asal sayıdır?",
              "options": {
                "A": "91",
                "B": "51",
                "C": "97",
                "D": "57",
                "E": "87"
              },
              "correctAnswer": "C",
              "solution": "97 bir asal sayıdır. 91=7x13, 51=3x17 vb."
            }
            ```
        """.trimIndent()

        val result = AiJsonParser.cleanAndParseJson(
            rawText = raw,
            serializer = SimilarQuestionResult.serializer(),
            fallbackParser = { AiJsonParser.parseSimilarQuestionFallback(it) }
        )

        assertTrue(result.isSuccess)
        val question = result.getOrNull()
        assertNotNull(question)
        assertEquals("Aşağıdakilerden hangisi bir asal sayıdır?", question?.questionText)
        assertEquals("C", question?.correctAnswer)
        assertEquals("97", question?.options?.get("C"))
    }

    @Test
    fun `cleanAndParseJson should sanitize single backslash LaTeX escapes in JSON`() {
        val raw = """
            {
              "questionText": "Limit hesabı: \lim_{x \to 0} \frac{\sin x}{x}",
              "options": {
                "A": "1",
                "B": "0",
                "C": "\infty",
                "D": "-1",
                "E": "Tanımsız"
              },
              "correctAnswer": "A",
              "solution": "Standart trigonometrik limit teoreminden 1'dir."
            }
        """.trimIndent()

        val result = AiJsonParser.cleanAndParseJson(
            rawText = raw,
            serializer = SimilarQuestionResult.serializer(),
            fallbackParser = { AiJsonParser.parseSimilarQuestionFallback(it) }
        )

        assertTrue(result.isSuccess)
        val question = result.getOrNull()
        assertNotNull(question)
        assertEquals("A", question?.correctAnswer)
    }

    @Test
    fun `cleanAndParseJson should fallback to Regex parsing when JSON is slightly broken`() {
        val brokenJson = """
            İşte soru:
            {
              "questionText": "2x + 6 = 14 ise x kaçtır?",
              "options": {
                "A": "4",
                "B": "5",
                "C": "6",
                "D": "7",
                "E": "8", // trailing comma
              },
              "correctAnswer": "A",
              "solution": "2x = 8 => x = 4"
            }
        """.trimIndent()

        val result = AiJsonParser.cleanAndParseJson(
            rawText = brokenJson,
            serializer = SimilarQuestionResult.serializer(),
            fallbackParser = { AiJsonParser.parseSimilarQuestionFallback(it) }
        )

        assertTrue(result.isSuccess)
        val question = result.getOrNull()
        assertNotNull(question)
        assertEquals("2x + 6 = 14 ise x kaçtır?", question?.questionText)
        assertEquals("4", question?.options?.get("A"))
        assertEquals("A", question?.correctAnswer)
    }

    @Test
    fun `parseQuestionMetadataFallback should extract metadata correctly`() {
        val text = """
            İnceleme sonucu:
            {
              "examType": "AYT",
              "subjectId": "ayt_matematik",
              "topic": "İntegral",
              "correctAnswer": "D"
            }
        """.trimIndent()

        val metadata = AiJsonParser.parseQuestionMetadataFallback(text)
        assertNotNull(metadata)
        assertEquals("AYT", metadata?.examType)
        assertEquals("ayt_matematik", metadata?.subjectId)
        assertEquals("İntegral", metadata?.topic)
        assertEquals("D", metadata?.correctAnswer)
    }

    @Test
    fun `cleanAndParseJson should return AiException with PARSING_FAILED on unparseable garbage text`() {
        val garbage = "Üzgünüm, soruyu çözemedim veya format oluştulamadı."

        val result = AiJsonParser.cleanAndParseJson(
            rawText = garbage,
            serializer = SimilarQuestionResult.serializer(),
            fallbackParser = { AiJsonParser.parseSimilarQuestionFallback(it) }
        )

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is AiException)
        assertEquals(AiErrorType.PARSING_FAILED, (exception as AiException).errorType)
    }
}
