package com.sorudefteri.ui.ortak.math

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PERF: Birleştirilmiş KaTeX kart gövdesi testleri.
 * MathMarkdownText bölüm başına WebView üretiyordu; CombinedMathMarkdownText
 * tek HTML gövdesi üretmeli.
 */
class CombinedMathHtmlTest {

    @Test
    fun generateCombinedHtml_bolumleriTekGovdedeKartlaraSorar() {
        val sections = listOf(
            Triple(0, "🎯 Doğru Cevap", "Cevap \$\\frac{1}{2}\$"),
            Triple(2, "📝 Adım Adım Ayrıntılı Çözüm", "Adım 1: \$x+1\$"),
            Triple(4, "💡 Püf Noktaları", "Düz metin ipucu")
        )
        val body = KaTeXHtmlGenerator.generateCombinedHtml(sections)
        assertTrue(body.contains("katex-card-answer"))
        assertTrue(body.contains("katex-card-steps"))
        assertTrue(body.contains("katex-card-tips"))
        assertTrue(body.contains("Doğru Cevap"))
    }

    @Test
    fun generateCombinedHtml_bosListeBosDoner() {
        assertTrue(KaTeXHtmlGenerator.generateCombinedHtml(emptyList()).isEmpty())
    }

    @Test
    fun generateHtml_skipPreprocess_ikinciKezIslemez() {
        val raw = "Düz metin \$x\$"
        val normal = KaTeXHtmlGenerator.generateHtml(raw)
        val skipped = KaTeXHtmlGenerator.generateHtml(
            content = "<p>HAZIR</p>",
            skipPreprocess = true
        )
        assertTrue(normal.contains("math-content"))
        assertTrue(skipped.contains("<p>HAZIR</p>"))
        assertFalse(skipped.contains(raw))
    }

    @Test
    fun combinedCardsCss_tumKartStilleriniIcerir() {
        val css = KaTeXHtmlGenerator.combinedCardsCss("#1C1B1F")
        assertTrue(css.contains("katex-card-answer"))
        assertTrue(css.contains("katex-card-formulas"))
        assertTrue(css.contains("katex-card-steps"))
        assertTrue(css.contains("katex-card-mistakes"))
        assertTrue(css.contains("katex-card-tips"))
        assertTrue(css.contains("#1C1B1F"))
    }
}
