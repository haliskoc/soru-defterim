package com.sorudefteri.math

import com.sorudefteri.data.ai.AiJsonParser
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

    @Test
    fun turkceOndalikVirgul_temizleniyor() {
        val raw = "Enerji verimi (≈ 9{,}3 kcal/g), karbonhidrat (4{,}1 kcal/g) ve protein (4{,}3 kcal/g) şeklindedir."
        val formatted = formatMathSymbols(raw)

        assertTrue("9,3 olmalı, {,} kalmamalı", formatted.contains("9,3 kcal/g"))
        assertTrue("4,1 olmalı, {,} kalmamalı", formatted.contains("4,1 kcal/g"))
        assertTrue("4,3 olmalı, {,} kalmamalı", formatted.contains("4,3 kcal/g"))
        assertFalse("Süslü parantezli virgül kalmamalı", formatted.contains("{,}"))
    }

    @Test
    fun kimyaBiyolojiTepkimeOklari_veEnzimler_duzgunGosteriliyor() {
        // Ok üstü enzim ve katalizör gösterimi
        val raw1 = """Nişasta + H_2O \xrightarrow{\text{Amilaz}} Maltoz + Dekstrin"""
        val formatted1 = formatMathSymbols(raw1)
        assertTrue("Ok ve enzim düzgün gösterilmeli", formatted1.contains("───[Amilaz]───>"))

        val raw2 = """A + B \xrightarrow[\text{ısı}]{\text{Katalizör}} C"""
        val formatted2 = formatMathSymbols(raw2)
        assertTrue("Üst ve alt etiketli ok gösterilmeli", formatted2.contains("───[Katalizör / ısı]───>"))

        val raw3 = """Glikoz \overset{\text{Heksokinaz}}{\longrightarrow} Glikoz-6-P"""
        val formatted3 = formatMathSymbols(raw3)
        assertTrue("overset enzimi oka eklenmeli", formatted3.contains("───[Heksokinaz]───>"))

        val raw4 = """N_2 + 3H_2 \xrightleftharpoons[\text{Basınç}]{\text{Fe}} 2NH_3"""
        val formatted4 = formatMathSymbols(raw4)
        assertTrue("Denge oku ve katalizör gösterilmeli", formatted4.contains("⇌ [Fe / Basınç] ⇌"))

        val raw5 = """\ce{Maltoz ->[Maltaz] 2 Glikoz}"""
        val formatted5 = formatMathSymbols(raw5)
        assertTrue("mhchem enzimli ok gösterilmeli", formatted5.contains("───[Maltaz]───>"))
    }

    @Test
    fun soyagaciVeGenetikSembolleri_duzgunDonusturuluyor() {
        val raw = """
            Sağlıklı dişi (\bigcirc) ve hasta erkek (\blacksquare) çaprazlanır.
            Genotipler: X^H X^h ile X^h Y, ayrıca babanın X^{h}'yi aktarması.
            Kan grupları: I^A I^B ve i^0 genleri bulunur.
            Taşıyıcı dişi (\odot), normal erkek (\square) olarak gösterilir.
        """.trimIndent()

        val formatted = formatMathSymbols(raw)

        assertTrue("Dişi sembolü ○ olmalı", formatted.contains("○"))
        assertFalse("\\bigcirc kalmamalı", formatted.contains("\\bigcirc"))
        assertTrue("Hasta erkek sembolü ■ olmalı", formatted.contains("■"))
        assertFalse("\\blacksquare kalmamalı", formatted.contains("\\blacksquare"))
        assertTrue("Sağlıklı erkek sembolü □ olmalı", formatted.contains("□"))
        assertTrue("Taşıyıcı sembolü ⊙ olmalı", formatted.contains("⊙"))

        // Genotip üst simgeleri
        assertTrue("X^H üst simgeli Xᴴ olmalı", formatted.contains("Xᴴ"))
        assertTrue("X^h üst simgeli Xʰ olmalı", formatted.contains("Xʰ"))
        assertTrue("X^{h}'yi ifadesi Xʰ'yi olmalı", formatted.contains("Xʰ'yi"))
        assertTrue("I^A üst simgeli Iᴬ olmalı", formatted.contains("Iᴬ"))
        assertTrue("I^B üst simgeli Iᴮ olmalı", formatted.contains("Iᴮ"))
        assertTrue("i^0 üst simgeli i⁰ olmalı", formatted.contains("i⁰"))
    }

    @Test
    fun fizikVeOptikSembolleri_duzgunDonusturuluyor() {
        val raw = "Işık akısı \\Phi'dir ve dalga boyu \\lambda = 5000\\AA'dür. Açı \\theta = 45\\degree ve direnç R = 10\\Omega'dur."
        val formatted = formatMathSymbols(raw)

        assertTrue("\\Phi sembolü Φ olmalı", formatted.contains("Φ'dir"))
        assertFalse("\\Phi kalmamalı", formatted.contains("\\Phi"))
        assertTrue("\\lambda sembolü λ olmalı", formatted.contains("λ = 5000"))
        assertTrue("Ångström Å olmalı", formatted.contains("5000Å"))
        assertTrue("Derece ° olmalı", formatted.contains("45°"))
        assertTrue("Ohm Ω olmalı", formatted.contains("10Ω"))
    }

    @Test
    fun literalNewlineKacislari_gercekSatirSonunaCevriliyor() {
        val raw = "Birinci satır.\\n\\nİkinci satır.\\nI. Öncül\\nII. Öncül"
        val formatted = formatMathSymbols(raw)

        assertFalse("Literal \\n kalmamalı", formatted.contains("\\n"))
        assertTrue("Gerçek newline içermeli", formatted.contains("\n\nİkinci satır.\nI. Öncül"))
    }

    @Test
    fun bilimselVeGrekSembolleri_duzgunDonusturuluyor() {
        val raw = """
            Tüm harfler: \pi \alpha \beta \gamma \delta \epsilon \varepsilon \zeta \eta \theta \vartheta \iota \kappa \lambda \mu \nu \xi \omicron \rho \varrho \sigma \varsigma \tau \upsilon \phi \varphi \chi \psi \omega
            Büyük harfler: \Gamma \Delta \Theta \Lambda \Xi \Pi \Sigma \Upsilon \Phi \Psi \Omega
            Cinsiyet: \male ve \female, \mars ve \venus
            Mantık: p \land q \implies r \lor \neg s \therefore p \because q
            Fizik ve Matematik: a \gg b, c \ll d, A \supset B, f\prime(x), \ell boyutu, \hbar Planck
        """.trimIndent()

        val formatted = formatMathSymbols(raw)

        assertTrue(formatted.contains("π α β γ δ ε ε ζ η θ θ ι κ λ μ ν ξ ο ρ ρ σ σ τ υ ϕ ϕ χ ψ ω"))
        assertTrue(formatted.contains("Γ Δ Θ Λ Ξ Π Σ Υ Φ Ψ Ω"))
        assertTrue(formatted.contains("♂ ve ♀, ♂ ve ♀"))
        assertTrue(formatted.contains("p ∧ q ⇒ r ∨ ¬ s ∴ p ∵ q"))
        assertTrue(formatted.contains("a ≫ b, c ≪ d, A ⊃ B, f′(x), ℓ boyutu, ℏ Planck"))
    }

    @Test
    fun kesirVeLimitDonusumleri_icIceVeDfracDestekler() {
        // Dfrac, cfrac, frac ve iç içe kesirler
        val fracText = """\frac{1}{2} + \dfrac{3x + 1}{4} + \cfrac{a}{b} ve \frac{\frac{1}{2}}{3}"""
        val fracFormatted = formatMathSymbols(fracText)

        assertTrue("Standart frac çözülmeli", fracFormatted.contains("(1 / 2)"))
        assertTrue("dfrac çözülmeli", fracFormatted.contains("(3x + 1 / 4)"))
        assertTrue("cfrac çözülmeli", fracFormatted.contains("(a / b)"))
        assertTrue("İç içe kesir çözülmeli", fracFormatted.contains("((1 / 2) / 3)"))

        // Limit ifadesi
        val limitText = """\lim_{x \to 0} \frac{\sin x}{x} = 1 ve \lim_{h \to 0} \frac{f(x+h) - f(x)}{h} = f'(x)"""
        val limitFormatted = formatMathSymbols(limitText)

        assertTrue("Limit okunabilir biçime dönüşmeli", limitFormatted.contains("lim(x → 0)"))
        assertTrue("h limit ifadesi okunabilir olmalı", limitFormatted.contains("lim(h → 0)"))
    }

    @Test
    fun lhopitalVeEsitsizlikSembolleri_duzgunDonusturuluyor() {
        val raw = """
            Limit \frac{0}{0} belirsizliği verdiğinden L'Hôpital uygulanır.
            x \neq 3 için x \le 5 ve y \ge 10 koşulları sağlanır.
        """.trimIndent()
        val formatted = formatMathSymbols(raw)

        assertTrue("0/0 kesri parantezli sadeleşmeli", formatted.contains("(0 / 0)"))
        assertTrue("neq eşitsizlik sembolü ≠ olmalı", formatted.contains("x ≠ 3"))
        assertTrue("le küçük eşit ≤ olmalı", formatted.contains("x ≤ 5"))
        assertTrue("ge büyük eşit ≥ olmalı", formatted.contains("y ≥ 10"))
    }

    @Test
    fun rightarrowVeIghtarrowKurtarilmasi_okSemboluneDonusuyor() {
        val rawStandard = "f(x) \\rightarrow 1 ve x \\to 0 ve A \\longrightarrow B"
        val formattedStandard = formatMathSymbols(rawStandard)
        assertTrue("\\rightarrow ok sembolüne dönüşmeli", formattedStandard.contains("f(x) → 1"))
        assertTrue("\\to ok sembolüne dönüşmeli", formattedStandard.contains("x → 0"))
        assertTrue("\\longrightarrow uzun ok sembolüne dönüşmeli", formattedStandard.contains("A ⟶ B"))

        // Bozulmuş ightarrow ve ongtrightarrow kurtarma
        val rawCorrupted = "f(x) ightarrow 1 ve f(x) \\ightarrow 1 ve A ongtrightarrow B"
        val formattedCorrupted = formatMathSymbols(rawCorrupted)
        assertTrue("Bozulmuş ightarrow → sembolüne dönüşmeli", formattedCorrupted.contains("f(x) → 1"))
        assertTrue("Bozulmuş \\ightarrow → sembolüne dönüşmeli", formattedCorrupted.contains("f(x) → 1"))
        assertFalse("Metinde ightarrow kalmamalı", formattedCorrupted.contains("ightarrow"))
    }

    @Test
    fun rightVeIghtKalintilari_belirsizlikVeParantezlerTemizleniyor() {
        // Standart \left[ \frac{0}{0} \right]
        val raw1 = "\\left[ \\frac{0}{0} \\right] belirsizliği"
        val formatted1 = formatMathSymbols(raw1)
        assertTrue("KaTeX 0/0 belirsizliği [0/0] olmalı", formatted1.contains("[0/0] belirsizliği"))

        // Bozulmuş [(0 / 0)ight] belirsizliği
        val raw2 = "[(0 / 0)ight] belirsizliği"
        val formatted2 = formatMathSymbols(raw2)
        assertTrue("Bozulmuş [(0 / 0)ight] [0/0] olmalı", formatted2.contains("[0/0] belirsizliği"))

        // Bozulmuş [(0/0)ight] ve [0/0 ight]
        val raw3 = "[(0/0)ight] veya [ 0/0 ight ]"
        val formatted3 = formatMathSymbols(raw3)
        assertTrue("Bozulmuş [(0/0)ight] [0/0] olmalı", formatted3.contains("[0/0]"))

        // Bozulmuş \right artıkları ight], ight)
        val raw4 = "[ (a / b) ight] ve ( x ight)"
        val formatted4 = formatMathSymbols(raw4)
        assertFalse("ight] kalmamalı", formatted4.contains("ight]"))
        assertFalse("ight) kalmamalı", formatted4.contains("ight)"))
        assertTrue("Köşeli parantez korunmalı", formatted4.contains("]"))
        assertTrue("Yay parantez korunmalı", formatted4.contains(")"))
    }

    @Test
    fun neqVeEqSifir_esitDegildirOlarakRenderEdiliyor() {
        // Standart \neq 0 ve \ne 0
        val raw1 = "x \\neq 0 ve a \\ne 0 ve \\neq 0"
        val formatted1 = formatMathSymbols(raw1)
        assertTrue("x \\neq 0 -> x ≠ 0 olmalı", formatted1.contains("x ≠ 0"))
        assertTrue("a \\ne 0 -> a ≠ 0 olmalı", formatted1.contains("a ≠ 0"))
        assertTrue("\\neq 0 -> ≠ 0 olmalı", formatted1.contains("≠ 0"))

        // Bozulmuş eq 0 ve neq 0 (ters eğik çizgisiz)
        val raw2 = "x eq 0 ve y neq 0 ve eq 0"
        val formatted2 = formatMathSymbols(raw2)
        assertTrue("Bozulmuş x eq 0 -> x ≠ 0 olmalı", formatted2.contains("x ≠ 0"))
        assertTrue("Bozulmuş y neq 0 -> y ≠ 0 olmalı", formatted2.contains("y ≠ 0"))
        assertTrue("Bozulmuş eq 0 -> ≠ 0 olmalı", formatted2.contains("≠ 0"))
    }

    @Test
    fun normalizeSafeEscapes_latexKomutlariniBozmazVeSatirSonlariniDonusturur() {
        val raw = "1. Satır\\n2. Satır\\n\\nx \\neq 0 iken f(x) \\rightarrow 1, [(0 / 0)\\right] ve \\frac{a}{b}.\\nI. Öncül\\nII. Öncül"
        val normalized = AiJsonParser.normalizeSafeEscapes(raw)

        // Gerçek satır sonları
        assertTrue("Gerçek newline içermeli", normalized.contains("1. Satır\n2. Satır\n\n"))
        assertTrue("Öncül öncesi newline korunmalı", normalized.contains("\nI. Öncül\nII. Öncül"))

        // LaTeX komutları ASLA bozulmamalı
        assertTrue("\\neq 0 bozulmamalı", normalized.contains("\\neq 0"))
        assertFalse("eq 0 olarak bölünmemeli", normalized.contains("\neq 0"))
        assertTrue("\\rightarrow bozulmamalı", normalized.contains("\\rightarrow"))
        assertFalse("ightarrow olmamalı", Regex("""(?<!r)ightarrow""").containsMatchIn(normalized))
        assertTrue("\\right bozulmamalı", normalized.contains("\\right"))
        assertTrue("\\frac{a}{b} bozulmamalı", normalized.contains("\\frac{a}{b}"))
    }

    @Test
    fun formatMathSymbols_dfacTfacTfracVeCokluKacislariCozumler() {
        val raw = """\\\\frac{1}{2} + \dfac{3}{4} + \tfac{5}{6} + \tfrac{7}{8}"""
        val formatted = formatMathSymbols(raw)

        assertTrue("(1 / 2) çözülmeli", formatted.contains("(1 / 2)"))
        assertTrue("(3 / 4) çözülmeli", formatted.contains("(3 / 4)"))
        assertTrue("(5 / 6) çözülmeli", formatted.contains("(5 / 6)"))
        assertTrue("(7 / 8) çözülmeli", formatted.contains("(7 / 8)"))
        assertFalse("dfac kalmamalı", formatted.contains("dfac"))
        assertFalse("tfac kalmamalı", formatted.contains("tfac"))
        assertFalse("tfrac kalmamalı", formatted.contains("tfrac"))
    }
}
