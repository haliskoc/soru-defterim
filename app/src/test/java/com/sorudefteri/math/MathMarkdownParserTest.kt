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
}
