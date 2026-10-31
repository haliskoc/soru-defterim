package com.sorudefteri.data.ai

import com.sorudefteri.domain.model.AiErrorType
import com.sorudefteri.domain.model.AiException
import com.sorudefteri.domain.model.QuestionMetadata
import com.sorudefteri.domain.model.SimilarQuestionResult
import com.sorudefteri.domain.model.TwinQuestion
import com.sorudefteri.domain.model.TwinQuestionsResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `cleanAndParseJson should preserve newlines before text and words`() {
        val raw = """
            {
              "questionText": "Perde düzlemine paralel konumdadır.\n\nBu durumda; ışık akısı \Phi'dir.\n\nBuna göre;\nI. Düzlem ayna yaklaştırılırsa E artar.\nII. Düzlem ayna kaldırılırsa \Phi azalır.",
              "options": {
                "A": "Yalnız I",
                "B": "Yalnız II",
                "C": "I ve II",
                "D": "I ve III",
                "E": "I, II ve III"
              },
              "correctAnswer": "C",
              "solution": "Aydınlanma şiddeti E = I/d^2 artar."
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
        assertTrue("Gerçek newline karakteri içermeli", question!!.questionText.contains("\nI. Düzlem ayna"))
        assertFalse("Çiğ \\n literal string kalmamalı", question.questionText.contains("\\nI."))
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

    @Test
    fun `cleanAndParseJson should preserve latex commands with single backslash without corruption`() {
        val raw = """
            {
              "questionText": "Limit x \rightarrow 0 iken f(x) \rightarrow 1 ve x \neq 0 koşulu ile \frac{a}{b} ve [(0 / 0)\right] belirsizliği incelenir. Ayrıca \to, \beta ve \theta değerleri verilsin.",
              "options": {
                "A": "\frac{1}{2}",
                "B": "x \neq 0",
                "C": "\rightarrow \infty",
                "D": "\right)",
                "E": "0"
              },
              "correctAnswer": "A",
              "solution": "x \neq 0 olduğundan payda sıfır olamaz. \frac{a}{b} sadeleşir ve limit \rightarrow 1 olur."
            }
        """.trimIndent()

        val result = AiJsonParser.cleanAndParseJson(
            rawText = raw,
            serializer = SimilarQuestionResult.serializer(),
            fallbackParser = { AiJsonParser.parseSimilarQuestionFallback(it) }
        )

        assertTrue(result.isSuccess)
        val q = result.getOrNull()
        assertNotNull(q)

        // 1. \rightarrow 'ightarrow' olmamalı, \rightarrow korunmalı
        assertTrue("\\rightarrow korunmalı", q!!.questionText.contains("\\rightarrow"))
        assertFalse("ightarrow olmamalı", Regex("""(?<!r)ightarrow""").containsMatchIn(q.questionText))

        // 2. \right 'ight' olmamalı, \right korunmalı
        assertTrue("\\right korunmalı", q.questionText.contains("\\right"))
        assertFalse("ight] tek başına kalmamalı", q.questionText.contains("0)ight]"))

        // 3. \neq 0 'eq 0' olmamalı, satır başına kaymamalı
        assertTrue("\\neq 0 korunmalı", q.questionText.contains("\\neq 0"))
        assertFalse("eq 0 olarak bölünmemeli", q.questionText.contains("\neq 0"))

        // 4. \frac{a}{b} form feed'e kurban gitmemeli
        assertTrue("\\frac{a}{b} korunmalı", q.questionText.contains("\\frac{a}{b}"))
        assertTrue("A şıkkında \\frac korunmalı", q.options["A"]!!.contains("\\frac{1}{2}"))

        // 5. \to, \beta, \theta korunmalı
        assertTrue("\\to korunmalı", q.questionText.contains("\\to"))
        assertTrue("\\beta korunmalı", q.questionText.contains("\\beta"))
        assertTrue("\\theta korunmalı", q.questionText.contains("\\theta"))
    }

    @Test
    fun `cleanAndParseJson should handle double backslash LaTeX commands cleanly`() {
        val raw = """
            {
              "questionText": "Limit x \\rightarrow 0 iken f(x) \\rightarrow 1, x \\neq 0 ve \\frac{a}{b}",
              "options": {
                "A": "\\frac{1}{2}",
                "B": "x \\neq 0",
                "C": "\\rightarrow 0",
                "D": "[(0 / 0)\\right]",
                "E": "0"
              },
              "correctAnswer": "B",
              "solution": "x \\neq 0 olmalıdır."
            }
        """.trimIndent()

        val result = AiJsonParser.cleanAndParseJson(
            rawText = raw,
            serializer = SimilarQuestionResult.serializer(),
            fallbackParser = { AiJsonParser.parseSimilarQuestionFallback(it) }
        )

        assertTrue(result.isSuccess)
        val q = result.getOrNull()
        assertNotNull(q)
        assertTrue(q!!.questionText.contains("\\rightarrow"))
        assertTrue(q.questionText.contains("\\neq 0"))
        assertTrue(q.questionText.contains("\\frac{a}{b}"))
        assertTrue(q.options["D"]!!.contains("\\right"))
    }

    @Test
    fun `normalizeSafeEscapes should never corrupt LaTeX commands starting with r, n, f, t, b`() {
        val sample = "x \\neq 0 iken f(x) \\rightarrow 1, [(0 / 0)\\right] ve \\frac{a}{b}. \\nu, \\nabla, \\notin, \\neg, \\rho, \\to, \\beta"
        val normalized = AiJsonParser.normalizeSafeEscapes(sample)

        assertTrue(normalized.contains("\\rightarrow"))
        assertFalse(Regex("""(?<!r)ightarrow""").containsMatchIn(normalized))
        assertTrue(normalized.contains("\\right"))
        assertTrue(normalized.contains("\\neq 0"))
        assertTrue(normalized.contains("\\frac{a}{b}"))
        assertTrue(normalized.contains("\\nu"))
        assertTrue(normalized.contains("\\nabla"))
        assertTrue(normalized.contains("\\notin"))
        assertTrue(normalized.contains("\\neg"))
        assertTrue(normalized.contains("\\rho"))
        assertTrue(normalized.contains("\\to"))
        assertTrue(normalized.contains("\\beta"))
    }

    @Test
    fun `sanitizeLatexEscapesInJson should properly escape single backslashes and ignore double backslashes`() {
        val singleEscaped = """{"text": "x \rightarrow 0 ve x \neq 0 ve \frac{1}{2} ve \alpha"}"""
        val sanitizedSingle = AiJsonParser.sanitizeLatexEscapesInJson(singleEscaped)
        assertTrue(sanitizedSingle.contains("\\\\rightarrow"))
        assertTrue(sanitizedSingle.contains("\\\\neq"))
        assertTrue(sanitizedSingle.contains("\\\\frac"))
        assertTrue(sanitizedSingle.contains("\\\\alpha"))

        val doubleEscaped = """{"text": "x \\rightarrow 0 ve x \\neq 0 ve \\frac{1}{2} ve \\alpha"}"""
        val sanitizedDouble = AiJsonParser.sanitizeLatexEscapesInJson(doubleEscaped)
        assertFalse(sanitizedDouble.contains("\\\\\\rightarrow"))
        assertFalse(sanitizedDouble.contains("\\\\\\neq"))
        assertFalse(sanitizedDouble.contains("\\\\\\frac"))
        assertFalse(sanitizedDouble.contains("\\\\\\alpha"))
    }

    @Test
    fun `fallbackParser should safely preserve latex commands`() {
        val brokenJsonWithLatex = """
            {
              "questionText": "x \neq 0 ise limit \rightarrow 1 ve [(0 / 0)\right] vardır.",
              "A": "\frac{1}{2}",
              "B": "\frac{2}{3}",
              "C": "\neq 0",
              "D": "\rightarrow \infty",
              "E": "Tanımsız",
              "correctAnswer": "A",
              "solution": "x \neq 0 kuralı uygulanır."
            }
        """.trimIndent()

        val parsed = AiJsonParser.parseSimilarQuestionFallback(brokenJsonWithLatex)
        assertNotNull(parsed)
        assertTrue(parsed!!.questionText.contains("\\neq 0"))
        assertTrue(parsed.questionText.contains("\\rightarrow"))
        assertTrue(parsed.questionText.contains("\\right"))
        assertTrue(parsed.options["A"]!!.contains("\\frac{1}{2}"))
    }

    @Test
    fun `parseTwinQuestionsResult should parse valid JSON wrapped in markdown fences`() {
        val raw = """
            Tabii ki, işte 3 kardeş soru:
            ```json
            {
              "originalTopic": "İkinci Dereceden Denklemler",
              "questions": [
                {
                  "id": 1,
                  "questionText": "x^2 - 5x + 6 = 0 denkleminin kökleri çarpımı kaçtır?",
                  "options": {
                    "A": "6",
                    "B": "5",
                    "C": "-5",
                    "D": "-6",
                    "E": "1"
                  },
                  "correctAnswer": "A",
                  "solution": "Kökler çarpımı c/a = 6/1 = 6'dır.",
                  "trapWarning": "Kökler toplamı (-b/a) ile karıştırmayın."
                },
                {
                  "id": 2,
                  "questionText": "Bir tarlanın alanı (x^2 - 7x + 12) m² ise x'in alabileceği değerler...",
                  "options": {
                    "A": "3 ve 4",
                    "B": "2 ve 6",
                    "C": "-3 ve -4",
                    "D": "1 ve 12",
                    "E": "5 ve 7"
                  },
                  "correctAnswer": "A",
                  "solution": "(x-3)(x-4)=0 kökler 3 ve 4.",
                  "trapWarning": "Negatif çarpanları pozitif kökle karıştırmayın."
                },
                {
                  "id": 3,
                  "questionText": "Kökleri 2 ve -3 olan ikinci dereceden denklem hangisidir?",
                  "options": {
                    "A": "x^2 + x - 6 = 0",
                    "B": "x^2 - x - 6 = 0",
                    "C": "x^2 + 5x - 6 = 0",
                    "D": "x^2 - 5x + 6 = 0",
                    "E": "x^2 + x + 6 = 0"
                  },
                  "correctAnswer": "A",
                  "solution": "x^2 - (x1+x2)x + x1*x2 = x^2 - (-1)x + (-6) = x^2 + x - 6 = 0.",
                  "trapWarning": "Kökler toplamının ters işaretli formüldeki yerini unutmayın."
                }
              ]
            }
            ```
            Başarılar dilerim!
        """.trimIndent()

        val result = AiJsonParser.parseTwinQuestionsResult(raw)
        assertTrue(result.isSuccess)
        val twinResult = result.getOrNull()
        assertNotNull(twinResult)
        assertEquals("İkinci Dereceden Denklemler", twinResult?.originalTopic)
        assertEquals(3, twinResult?.questions?.size)

        val q1 = twinResult?.questions?.get(0)
        assertEquals(1, q1?.id)
        assertEquals("A", q1?.correctAnswer)
        assertEquals("6", q1?.options?.get("A"))
        assertEquals("Kökler toplamı (-b/a) ile karıştırmayın.", q1?.trapWarning)

        val q2 = twinResult?.questions?.get(1)
        assertEquals(2, q2?.id)
        assertEquals("A", q2?.correctAnswer)

        val q3 = twinResult?.questions?.get(2)
        assertEquals(3, q3?.id)
        assertEquals("A", q3?.correctAnswer)
    }

    @Test
    fun `parseTwinQuestionsResult should sanitize unescaped LaTeX backslashes like frac and sqrt`() {
        val raw = """
            {
              "originalTopic": "Köklü Sayılar",
              "questions": [
                {
                  "id": 1,
                  "questionText": "İşlemin sonucu: \frac{\sqrt{18}}{\sqrt{2}} kaçtır?",
                  "options": {
                    "A": "3",
                    "B": "\sqrt{3}",
                    "C": "\frac{3}{2}",
                    "D": "9",
                    "E": "6"
                  },
                  "correctAnswer": "A",
                  "solution": "\sqrt{\frac{18}{2}} = \sqrt{9} = 3",
                  "trapWarning": "Kök içindeki bölmeyi dışarı çıkarmadan sadeleştirin."
                }
              ]
            }
        """.trimIndent()

        val result = AiJsonParser.parseTwinQuestionsResult(raw)
        assertTrue(result.isSuccess)
        val q1 = result.getOrNull()?.questions?.firstOrNull()
        assertNotNull(q1)
        assertEquals("A", q1?.correctAnswer)
        assertTrue(q1!!.questionText.contains("\\frac"))
        assertTrue(q1.questionText.contains("\\sqrt"))
        assertTrue(q1.options["C"]!!.contains("\\frac{3}{2}"))
    }

    @Test
    fun `parseTwinQuestionsResult should repair root array and trailing commas`() {
        val raw = """
            [
              {
                "id": 1,
                "questionText": "Soru 1",
                "options": {
                  "A": "10",
                  "B": "20",
                },
                "correctAnswer": "B",
                "solution": "Çözüm 1",
                "trapWarning": "Tuzak 1",
              },
              {
                "id": 2,
                "questionText": "Soru 2",
                "options": {
                  "A": "30",
                  "B": "40",
                },
                "correctAnswer": "A",
                "solution": "Çözüm 2",
                "trapWarning": "Tuzak 2",
              }
            ]
        """.trimIndent()

        val result = AiJsonParser.parseTwinQuestionsResult(raw)
        assertTrue(result.isSuccess)
        val twinResult = result.getOrNull()
        assertNotNull(twinResult)
        assertEquals(2, twinResult?.questions?.size)
        assertEquals("B", twinResult?.questions?.get(0)?.correctAnswer)
        assertEquals("A", twinResult?.questions?.get(1)?.correctAnswer)
    }

    @Test
    fun `parseTwinQuestionsResult should parse plain text fallback format`() {
        val raw = """
            Konu: Fonksiyon Grafikleri

            1. Soru (Sayı & Değer Varyasyonu):
            f(x) = 2x + 4 fonksiyonunun x eksenini kestiği noktanın apsisi kaçtır?
            A) -2
            B) 2
            C) 4
            D) 0
            E) -4
            Doğru Cevap: A
            Çözüm: 2x + 4 = 0 => x = -2 bulunur.
            Tuzak Uyarısı: y eksenini kestiği nokta (0, 4) ile karıştırılabilir.

            2. Soru (Hikaye & Bağlam Varyasyonu):
            Bir taksimetre açılışta 10 TL, her km için 5 TL yazmaktadır. 30 TL ödeyen müşteri kaç km gitmiştir?
            A) 2
            B) 4
            C) 5
            D) 6
            E) 8
            Doğru Cevap: B
            Çözüm: 10 + 5x = 30 => 5x = 20 => x = 4 km.
            Tuzak Uyarısı: Açılış ücretini toplamdan çıkarmayı unutmayın.
        """.trimIndent()

        val result = AiJsonParser.parseTwinQuestionsResult(raw)
        assertTrue(result.isSuccess)
        val twinResult = result.getOrNull()
        assertNotNull(twinResult)
        assertEquals("Fonksiyon Grafikleri", twinResult?.originalTopic)
        assertEquals(2, twinResult?.questions?.size)
        assertEquals("A", twinResult?.questions?.get(0)?.correctAnswer)
        assertEquals("B", twinResult?.questions?.get(1)?.correctAnswer)
        assertEquals("-2", twinResult?.questions?.get(0)?.options?.get("A"))
        assertEquals("4", twinResult?.questions?.get(1)?.options?.get("B"))
    }
}
