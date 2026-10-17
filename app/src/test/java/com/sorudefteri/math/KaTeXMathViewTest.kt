package com.sorudefteri.math

import androidx.compose.ui.graphics.Color
import com.sorudefteri.ui.ortak.math.KaTeXHtmlGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Yerel KaTeX Matematik & Kimya motoru (HTML üretici, LaTeX & Markdown parser,
 * tema renkleri ve çevrimdışı asset referansları) birim testleri.
 */
class KaTeXMathViewTest {

    @Test
    fun generateHtml_yerelAssetleriDogruYukluyor() {
        val formula = """\int_{0}^{1} x^2 \, dx = \frac{1}{3}"""
        val html = KaTeXHtmlGenerator.generateHtml(
            content = formula,
            isDarkTheme = false
        )

        // Yerel CSS ve JS asset yollarının tam doğrulanması
        assertTrue("katex.min.css asset yolu bulunmalı", html.contains("file:///android_asset/katex/katex.min.css"))
        assertTrue("katex.min.js asset yolu bulunmalı", html.contains("file:///android_asset/katex/katex.min.js"))
        assertTrue("mhchem.min.js kimya kütüphanesi bulunmalı", html.contains("file:///android_asset/katex/contrib/mhchem.min.js"))
        assertTrue("auto-render.min.js otomatik ayrıştırıcı bulunmalı", html.contains("file:///android_asset/katex/contrib/auto-render.min.js"))
        assertTrue("math-content container elementi bulunmalı", html.contains("id=\"math-content\""))
        assertTrue("renderMathInElement fonksiyon çağrısı bulunmalı", html.contains("renderMathInElement"))
    }

    @Test
    fun preprocessContent_isaretsizFormulleriKaTeXBelirteciyleSarar() {
        val raw = """\frac{-b \pm \sqrt{b^2 - 4ac}}{2a}"""
        val processedDisplay = KaTeXHtmlGenerator.preprocessContent(raw, defaultDisplayMode = true)
        val processedInline = KaTeXHtmlGenerator.preprocessContent(raw, defaultDisplayMode = false)

        // Display modda çift dolar ($$...$$), inline modda tek dolar ($...$) kullanılmalı
        assertTrue("Display modda \$\$ ile sarılmalı", processedDisplay.startsWith("\$\$") && processedDisplay.endsWith("\$\$"))
        assertTrue("Display mod ham formülü içermeli", processedDisplay.contains(raw))
        assertTrue("Inline mod \$ ile sarılmalı", processedInline.startsWith("\$") && processedInline.endsWith("\$"))
        assertTrue("Inline mod ham formülü içermeli", processedInline.contains(raw))
        // Display çift dolar, inline tek dolar olmalı (farklı uzunluk)
        assertTrue("Display inline'dan uzun olmalı (çift dolar)", processedDisplay.length == processedInline.length + 2)
    }

    @Test
    fun preprocessContent_ciftKacisliLatexTekeIndirgenir() {
        val doubleEscaped = """\\frac{1}{2} + \\to + \\neq"""
        val processed = KaTeXHtmlGenerator.preprocessContent(doubleEscaped)
        assertTrue("Çift kaçışlı frac teke indirgenmeli", processed.contains("""\frac{1}{2}"""))
        assertTrue("Ham çift kaçış kalmamalı", !processed.contains("""\\frac"""))
    }

    @Test
    fun preprocessContent_htmlEntityVeLiteralNCozumlenir() {
        // Math bloğu içindeki &gt; ham '>' olarak korunmalı (KaTeX'e ham gider)
        val withEntities = "x küçük 5 ve \$y > 3\$"
        val processedEntities = KaTeXHtmlGenerator.preprocessContent(withEntities)
        assertTrue("Math bloğu korunmalı", processedEntities.contains("\$y > 3\$"))

        // AI'dan &lt; olarak gelen metin math dışında HTML-escape'li kalabilir ama çifte kaçmamalı
        val withLtEntity = "x &lt; 5 ve \$y > 3\$"
        val processedLt = KaTeXHtmlGenerator.preprocessContent(withLtEntity)
        assertTrue("Çifte HTML kaçış olmamalı", !processedLt.contains("&amp;lt;"))
        assertTrue("Math bloğu ham korunmalı", processedLt.contains("\$y > 3\$"))

        val withLiteralN = "Adım 1\\nAdım 2 \\frac{1}{2}"
        val processedN = KaTeXHtmlGenerator.preprocessContent(withLiteralN)
        assertTrue("İzole literal \\n satıra dönmeli", processedN.contains("Adım 1") && processedN.contains("Adım 2"))
        assertTrue("LaTeX frac korunmalı", processedN.contains("""\frac{1}{2}"""))
    }

    @Test
    fun preprocessContent_mevcutSinirBelirtecleriniBozmaz() {
        val rawWithDollar = "Kuadratik denklem: \$ax^2 + bx + c = 0\$ ve diskriminant \$\\Delta = b^2 - 4ac\$"
        val processed = KaTeXHtmlGenerator.preprocessContent(rawWithDollar)

        assertTrue("\$ax^2 + bx + c = 0\$ korunmalı", processed.contains("\$ax^2 + bx + c = 0\$"))
        assertTrue("\$\\Delta = b^2 - 4ac\$ korunmalı", processed.contains("\$\\Delta = b^2 - 4ac\$"))
    }

    @Test
    fun generateHtml_kimyasalTepkimeleriVeMhchemDestekler() {
        val chemistry = """\ce{2H2 + O2 -> 2H2O} ve \ce{Fe^{2+} + Cu -> Fe + Cu^{2+}}"""
        val html = KaTeXHtmlGenerator.generateHtml(chemistry)

        assertTrue("Kimyasal tepkime içeriği korunmalı", html.contains("""\ce{2H2 + O2 -> 2H2O}"""))
        assertTrue("mhchem scripti yüklenmeli", html.contains("mhchem.min.js"))
    }

    @Test
    fun generateHtml_matrisVeIntegralIfadeleriniDestekler() {
        val matrix = """
            \begin{pmatrix}
            a & b \\
            c & d
            \end{pmatrix}
        """.trimIndent()

        val html = KaTeXHtmlGenerator.generateHtml(matrix, displayMode = true)
        assertTrue("Matris pmatrix bloğu korunmalı", html.contains("\\begin{pmatrix}"))
        assertTrue("Matris pmatrix kapanış bloğu korunmalı", html.contains("\\end{pmatrix}"))
    }

    @Test
    fun generateHtml_karanlikVeAydinlikTemaRenkleri() {
        val darkHtml = KaTeXHtmlGenerator.generateHtml(
            content = "\$x = 5\$",
            isDarkTheme = true
        )
        assertTrue("Karanlık tema açık renk yazı içermeli", darkHtml.contains("#E6E1E5") || darkHtml.contains("#e6e1e5"))

        val lightHtml = KaTeXHtmlGenerator.generateHtml(
            content = "\$x = 5\$",
            isDarkTheme = false
        )
        assertTrue("Aydınlık tema koyu renk yazı içermeli", lightHtml.contains("#1C1B1F") || lightHtml.contains("#1c1b1f"))
    }

    @Test
    fun convertMarkdownWithMathPreserved_markdownEtiketleriniDonusturur() {
        val markdownText = "🎯 **Doğru Cevap:** A\n\n📚 **Temel Kavramlar:**\n- Kökler toplamı: \$x_1 + x_2 = -\\frac{b}{a}\$\n- Kökler çarpımı: \$x_1 \\cdot x_2 = \\frac{c}{a}\$"

        val htmlContent = KaTeXHtmlGenerator.convertMarkdownWithMathPreserved(markdownText)

        // Markdown bold -> <strong>
        assertTrue("<strong>Doğru Cevap:</strong> olmalı", htmlContent.contains("<strong>Doğru Cevap:</strong>"))
        assertTrue("<strong>Temel Kavramlar:</strong> olmalı", htmlContent.contains("<strong>Temel Kavramlar:</strong>"))

        // Markdown list -> <ul><li>
        assertTrue("Liste etiketi <ul> içermeli", htmlContent.contains("<ul>"))
        assertTrue("Madde etiketi <li> içermeli", htmlContent.contains("<li>"))

        // İçindeki matematik formülleri bozulmamalı
        assertTrue("Matematik formülü korunmalı", htmlContent.contains("\$x_1 + x_2 = -\\frac{b}{a}\$"))
        assertTrue("Matematik çarpımı korunmalı", htmlContent.contains("\$x_1 \\cdot x_2 = \\frac{c}{a}\$"))
    }

    @Test
    fun colorToHex_composeColorDonusumuDogru() {
        val red = Color(0xFFFF0000)
        val green = Color(0xFF00FF00)
        val blue = Color(0xFF0000FF)

        assertEquals("#ff0000", KaTeXHtmlGenerator.colorToHex(red))
        assertEquals("#00ff00", KaTeXHtmlGenerator.colorToHex(green))
        assertEquals("#0000ff", KaTeXHtmlGenerator.colorToHex(blue))
    }

    @Test
    fun preprocessContent_vektorVeFizikSembolleri() {
        val vectorText = """\vec{F} = m \cdot \vec{a}"""
        val processed = KaTeXHtmlGenerator.preprocessContent(vectorText, defaultDisplayMode = true)

        assertTrue("Vektör formülü sarılmalı", processed.startsWith("$$") && processed.endsWith("$$"))
        assertTrue("Vektör F korunmalı", processed.contains("""\vec{F}"""))
    }

    @Test
    fun preprocessContent_limitVeLHopitalBelirsizligi() {
        val raw = "x = 2 noktasında \\lim_{x \\to 2} \\frac{x^2 - 4}{x - 2} ifadesinde [0/0] belirsizliği vardır."
        val processed = KaTeXHtmlGenerator.preprocessContent(raw)

        // [0/0] belirsizliği \left[\frac{0}{0}\right] kesrine dönüştürülmeli
        assertTrue("[0/0] kesre dönüştürülmeli", processed.contains("""\left[\frac{0}{0}\right]"""))
        // Limit ifadesi KaTeX içine alınmalı
        assertTrue("Limit KaTeX içine alınmalı", processed.contains("""\lim""") && processed.contains("""\frac{x^2 - 4}{x - 2}"""))
        // Türkçe metinler sağlam kalmalı
        assertTrue("Türkçe kelimeler korunmalı", processed.contains("x = 2 noktasında") && processed.contains("belirsizliği vardır."))
    }

    @Test
    fun preprocessContent_limitLHopitalGenelFormul() {
        val rawLimit = """\lim_{h \to 0} \frac{f(x+ah)-f(x-bh)}{ch}"""
        val processed = KaTeXHtmlGenerator.preprocessContent(rawLimit, defaultDisplayMode = true)

        assertTrue("Saf limit formülü $$ ile sarılmalı", processed.startsWith("$$") && processed.endsWith("$$"))
        assertTrue("Limit ifadesi bozulmadan korunmalı", processed.contains("""\lim_{h \to 0}"""))
        assertTrue("Türev tanımı kesri korunmalı", processed.contains("""\frac{f(x+ah)-f(x-bh)}{ch}"""))
    }

    @Test
    fun preprocessContent_duzMetinBolmeIsleminiGercekKesreDonusturur() {
        // (4 / 3) parantezli kesir
        val p1 = KaTeXHtmlGenerator.preprocessContent("Cevap (4 / 3) olarak bulunur.")
        assertTrue("(4 / 3) dikey kesre dönüştürülmeli", p1.contains("""\frac{4}{3}"""))

        // (a / b) sembolik kesir
        val p2 = KaTeXHtmlGenerator.preprocessContent("Eğim (a / b) değerine eşittir.")
        assertTrue("(a / b) dikey kesre dönüştürülmeli", p2.contains("""\frac{a}{b}"""))

        // Yalın 4/3 bölmesi
        val p3 = KaTeXHtmlGenerator.preprocessContent("Sonuç 4/3 çıkar.")
        assertTrue("4/3 dikey kesre dönüştürülmeli", p3.contains("""\frac{4}{3}"""))

        // İki parantezli (x + 1) / (x - 1)
        val p4 = KaTeXHtmlGenerator.preprocessContent("Fonksiyon (x + 1) / (x - 1) biçimindedir.")
        assertTrue("(x + 1) / (x - 1) dikey kesre dönüştürülmeli", p4.contains("""\frac{x + 1}{x - 1}"""))
    }

    @Test
    fun preprocessContent_karisikMetinVeFormulleriBozmadanSarar() {
        val mixed = "1. Adım: f'(x) türevini alalım. \$\\lim_{x \\to 0} \\frac{\\sin x}{x} = 1\$ kuralını uygularız."
        val processed = KaTeXHtmlGenerator.preprocessContent(mixed)

        assertTrue("1. Adım başlığı korunmalı", processed.contains("1. Adım:"))
        assertTrue("Limit kuralı KaTeX belirteçleriyle korunmalı", processed.contains("""\lim_{x \to 0} \frac{\sin x}{x} = 1"""))
        assertTrue("kuralını uygularız metni korunmalı", processed.contains("kuralını uygularız"))
    }

    @Test
    fun mathDisplayHelper_matematikVeFormulleriDogruTespitEder() {
        // Matematiksel içerikler true dönmeli
        assertTrue(com.sorudefteri.ui.ortak.math.MathDisplayHelper.hasMathOrLatex("""\frac{1}{2}"""))
        assertTrue(com.sorudefteri.ui.ortak.math.MathDisplayHelper.hasMathOrLatex("""\lim_{x \to 0}"""))
        assertTrue(com.sorudefteri.ui.ortak.math.MathDisplayHelper.hasMathOrLatex("""x^2 + y^2 = r^2"""))
        assertTrue(com.sorudefteri.ui.ortak.math.MathDisplayHelper.hasMathOrLatex("""[0/0] belirsizliği"""))
        assertTrue(com.sorudefteri.ui.ortak.math.MathDisplayHelper.hasMathOrLatex("""(4 / 3)"""))
        assertTrue(com.sorudefteri.ui.ortak.math.MathDisplayHelper.hasMathOrLatex("""\ce{H2O}"""))
        assertTrue(com.sorudefteri.ui.ortak.math.MathDisplayHelper.hasMathOrLatex("""\sqrt{16} = 4"""))
        assertTrue(com.sorudefteri.ui.ortak.math.MathDisplayHelper.hasMathOrLatex("""\vec{v}"""))

        // Saf düz Türkçe metin false dönmeli
        assertFalse(com.sorudefteri.ui.ortak.math.MathDisplayHelper.hasMathOrLatex("Bu soruda doğru cevabı bulmak için paragrafı dikkatle okuyunuz."))
    }
}
