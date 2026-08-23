package com.sorudefteri.math

import com.sorudefteri.ui.ortak.math.formatMathSymbols
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LaTeX sembolleri, matematiksel ifadeler (üslü sayılar, integraller, kesirler) ve markdown biçimlendirme birim testi.
 */
class MathMarkdownParserTest {

    @Test
    fun latexMatematikSembolleri_unicodeDonusumuYapiliyor() {
        val rawText = """
            Fonksiyonun türevi f'(x) = 3x^2 + 2x - 5 olarak bulunur.
            Limit değeri \infty iken \pm 1 aralığındadır.
            Toplam sembolü \sum ve integral \int_0^1 f(x)dx işlemi uygulanır.
            Çarpım \times ve bölme \div sembolleri içerir.
        """.trimIndent()

        val formatted = formatMathSymbols(rawText)

        // Üslü sayı kontrolü (x^2 -> x²)
        assertTrue("x^2 üslü x² karakterine dönüşmeli", formatted.contains("x²"))

        // Özel matematik sembolleri
        assertTrue("\\infty sonsuzluk (∞) sembolüne dönüşmeli", formatted.contains("∞"))
        assertTrue("\\pm artı-eksi (±) sembolüne dönüşmeli", formatted.contains("±"))
        assertTrue("\\sum toplam (∑) sembolüne dönüşmeli", formatted.contains("∑"))
        assertTrue("\\int integral (∫) sembolüne dönüşmeli", formatted.contains("∫"))
        assertTrue("\\times çarpı (×) sembolüne dönüşmeli", formatted.contains("×"))
        assertTrue("\\div bölü (÷) sembolüne dönüşmeli", formatted.contains("÷"))
    }

    @Test
    fun latexKesirVeKok_okunabilirFormataCevriliyor() {
        val rawText = "Sonuç: \\frac{3x + 1}{2} ve \\sqrt{16} = 4 olur."
        val formatted = formatMathSymbols(rawText)

        assertTrue("Kesir (3x + 1 / 2) biçiminde sadeleşmeli", formatted.contains("(3x + 1 / 2)"))
        assertTrue("Karekök √(16) biçiminde sadeleşmeli", formatted.contains("√(16)"))
    }

    @Test
    fun deneJpg_latexHatalariTamamenDuzeltildi() {
        val rawText = """
            ax = b \implies x_1 = \frac{b}{a}
            Soruda çözüm kümesi \left[ \frac{a}{b}, \frac{2a}{b} \right) olarak verilmiştir.
            a = b \text{ veya } a = -b
            -c = 2a \implies \mathbf{c = -2a}
            (\text{alt sınır} < \text{üst sınır})
            <think>burası gizli düşünce</think>
            x \le 5 ve y \ge 10
        """.trimIndent()

        val formatted = formatMathSymbols(rawText)

        // 1. \left[ ve \right) bozulup ≤ft[ olmamalı!
        assertFalse("≤ft olmamalı", formatted.contains("≤ft"))
        assertFalse("\\left olmamalı", formatted.contains("\\left"))
        assertFalse("\\right olmamalı", formatted.contains("\\right"))
        assertTrue("Köşeli parantez korunmalı", formatted.contains("["))
        assertTrue("Yay parantez korunmalı", formatted.contains(")"))

        // 2. \implies ok sembolüne dönüşmeli
        assertTrue("\\implies ⇒ sembolüne dönüşmeli", formatted.contains("⇒"))
        assertFalse("\\implies kalmamalı", formatted.contains("\\implies"))

        // 3. \text{ veya } ve \mathbf{c = -2a} sarmalayıcıları çözülmeli
        assertTrue("veya çözülmeli", formatted.contains("veya"))
        assertTrue("c = -2a çözülmeli", formatted.contains("c = -2a"))
        assertTrue("alt sınır < üst sınır çözülmeli", formatted.contains("alt sınır < üst sınır"))
        assertFalse("\\text kalmamalı", formatted.contains("\\text"))
        assertFalse("\\mathbf kalmamalı", formatted.contains("\\mathbf"))

        // 4. <think> etiketleri temizlenmeli
        assertFalse("Düşünce etiketi temizlenmeli", formatted.contains("burası gizli düşünce"))

        // 5. \le ve \ge doğru eşleşmeli
        assertTrue("x ≤ 5 olmalı", formatted.contains("x ≤ 5"))
        assertTrue("y ≥ 10 olmalı", formatted.contains("y ≥ 10"))
    }
}
