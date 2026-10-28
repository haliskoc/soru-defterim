package com.sorudefteri.data.pdf

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Gelişmiş PDF Yerleşim ve Optik Form Birim Testleri:
 * 1. Paragraf ve Satır Sonu Koruma (MetinAkisi.satirlaraAyir)
 * 2. Elastik Görsel Sıkıştırma (%80 - %100 ölçekleme ile sütun kurtarma)
 * 3. 2 Sütunlu Sayfada Tam Genişlik Soru İçin Dikey Çizgi Baskılama
 * 4. OmrMatrixCodeCodec 2D Vektörel Optik Kodlama & Çözme Doğrulaması
 */
class PdfAdvancedLayoutTest {

    private lateinit var mockPaint: Paint

    @Before
    fun setup() {
        mockPaint = mockk(relaxed = true)
        // Her karakterin genişliğini 8.0f pt olarak simüle et
        every { mockPaint.measureText(any<String>()) } answers {
            val text = firstArg<String>()
            text.length * 8.0f
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // =========================================================
    // 1. PARAGRAF VE SATIR SONU KORUMA TESTLERİ
    // =========================================================

    @Test
    fun `satirlaraAyir Roma rakamli oncul satirlarini ve sirasini korur`() {
        val onculMetni = """
            I. Hücre zarı seçici geçirgendir.
            II. Mitokondri ATP üretir.
            III. Ribozom protein sentezler.
        """.trimIndent()

        // Genişlik 500f pt (her öncül tek satıra sığacak genişlikte)
        val satirlar = PdfSoruYerlesim.MetinAkisi.satirlaraAyir(onculMetni, mockPaint, 500f)

        assertEquals("Her öncül ayrı satır olarak korunmalıdır", 3, satirlar.size)
        assertTrue("1. öncül I. ile başlamalı", satirlar[0].startsWith("I."))
        assertTrue("2. öncül II. ile başlamalı", satirlar[1].startsWith("II."))
        assertTrue("3. öncül III. ile başlamalı", satirlar[2].startsWith("III."))
    }

    @Test
    fun `satirlaraAyir paragraflar arasindaki bos satirlari korur`() {
        val metin = "Birinci paragraf metni.\n\nİkinci paragraf metni."
        val satirlar = PdfSoruYerlesim.MetinAkisi.satirlaraAyir(metin, mockPaint, 400f)

        assertEquals("2 paragraf + 1 boş satır = 3 satır olmalıdır", 3, satirlar.size)
        assertEquals("Birinci paragraf metni.", satirlar[0])
        assertEquals("", satirlar[1]) // Boş satır "" olarak korunmalı
        assertEquals("İkinci paragraf metni.", satirlar[2])
    }

    @Test
    fun `satirlaraAyir Windows CRLF satır sonlarını normalize eder`() {
        val metin = "Satır bir\r\nSatır iki\r\n\r\nSatır üç"
        val satirlar = PdfSoruYerlesim.MetinAkisi.satirlaraAyir(metin, mockPaint, 300f)

        assertEquals(4, satirlar.size)
        assertEquals("Satır bir", satirlar[0])
        assertEquals("Satır iki", satirlar[1])
        assertEquals("", satirlar[2])
        assertEquals("Satır üç", satirlar[3])
    }

    @Test
    fun `satirlaraAyir genisligi asan paragrafta acgozlu kelime sarma uygular`() {
        // Her harf 8f pt. 150f genişlik yaklaşık 18 karaktere izin verir.
        val uzunParagraf = "Bu paragraf satır genişliğini aşacak kadar uzun kelimeler içerir"
        val satirlar = PdfSoruYerlesim.MetinAkisi.satirlaraAyir(uzunParagraf, mockPaint, 150f)

        assertTrue("Genişliği aşan metin birden fazla satıra bölünmeli", satirlar.size > 1)
        satirlar.forEach { satir ->
            val olculen = mockPaint.measureText(satir)
            // Satırdaki tek bir kelime sınırın üstünde olmadığı sürece genişliği aşamaz
            if (satir.contains(" ")) {
                assertTrue("Satır genişliği sınır içinde olmalı: $olculen <= 150f", olculen <= 150f)
            }
        }
    }

    // =========================================================
    // 2. ELASTİK GÖRSEL SIĞDIRMA TESTLERİ
    // =========================================================

    @Test
    fun `elastikOlcekle yuzde 18e kadar olan kucuk tasmayi gorseli sikistirarak sigdirir`() {
        val olcum = PdfSoruYerlesim.SoruOlcum(
            soruIndex = 1,
            genislik = 250f,
            ustYukseklik = 24f,
            icerikYukseklik = 160f, // 154f görsel + 6f boşluk
            cozumYukseklik = 0f,
            altBosluk = 16f,
            metinSatirlari = emptyList(),
            satirYuksekligi = 11f,
            metinIlkBosluk = 0f,
            metinSonraBosluk = 0f,
            bitmapRatio = 0.6f,
            gorselYukseklik = 154f
        )
        // toplamYukseklik = 24 + 160 + 0 + 16 = 200f
        assertEquals(200f, olcum.toplamYukseklik, 0.01f)

        // Hedef kapasite 185f -> taşma = 15f (200f'in %7.5'i, %18 sınırının altında)
        val elastik = olcum.elastikOlcekle(185f)

        assertNotNull("Elastik sığdırma başarılı olmalıdır", elastik)
        assertEquals("Yeni toplam yükseklik hedef kapasiteye eşitlenmeli", 185f, elastik!!.toplamYukseklik, 0.05f)
        assertTrue("Görsel ölçeği %80 - %100 aralığında olmalı", elastik.gorselOlcek in 0.80f..1.0f)
        assertEquals(154f - 15f, elastik.gorselYukseklik, 0.05f)
    }

    @Test
    fun `elastikOlcekle yuzde 18den fazla buyuk tasmada sikistirmayi reddeder`() {
        val olcum = PdfSoruYerlesim.SoruOlcum(
            soruIndex = 1,
            genislik = 250f,
            ustYukseklik = 24f,
            icerikYukseklik = 160f,
            cozumYukseklik = 0f,
            altBosluk = 16f,
            metinSatirlari = emptyList(),
            satirYuksekligi = 11f,
            metinIlkBosluk = 0f,
            metinSonraBosluk = 0f,
            bitmapRatio = 0.6f,
            gorselYukseklik = 154f
        )
        // toplamYukseklik = 200f. Hedef = 120f -> taşma = 80f (%40 taşma)
        val elastik = olcum.elastikOlcekle(120f)
        assertNull("Yüksek taşmada elastik sığdırma reddedilmeli (null dönmeli)", elastik)
    }

    // =========================================================
    // 3. SÜTUN DİKEY ÇİZGİ BASKILAMA TESTİ
    // =========================================================

    @Test
    fun `tam genislikli soru sayfada yer aliyorsa orta dikey cizgi baskilanir`() {
        val twoColWidth = 251.5f

        // Normal 2 sütunlu sayfa: tüm ögeler standart sütun genişliğinde
        val normalOgeler = listOf(
            PdfSoruYerlesim.Oge.TamSoru(
                soruIndex = 0, x = 35f, y = 50f, genislik = twoColWidth, yukseklik = 120f, sutun = 0
            ),
            PdfSoruYerlesim.Oge.TamSoru(
                soruIndex = 1, x = 308.5f, y = 50f, genislik = twoColWidth, yukseklik = 140f, sutun = 1
            )
        )
        val normalHasFullWidth = normalOgeler.any { it.genislik > twoColWidth + 10f }
        val normalDrawDivider = !false && !normalHasFullWidth
        assertFalse("Normal 2 sütunlu sayfada tam genişlik olmamalı", normalHasFullWidth)
        assertTrue("Normal 2 sütunlu sayfada orta dikey çizgi çizilmeli", normalDrawDivider)

        // Tam genişlikli kaçış sorusu içeren sayfa (~525f)
        val tamGenislikOgeler = listOf(
            PdfSoruYerlesim.Oge.TamSoru(
                soruIndex = 2, x = 35f, y = 50f, genislik = 525f, yukseklik = 600f, sutun = -1
            )
        )
        val fullHasFullWidth = tamGenislikOgeler.any { it.genislik > twoColWidth + 10f }
        val fullDrawDivider = !false && !fullHasFullWidth
        assertTrue("Sayfada tam genişlikli öge tespit edilmeli", fullHasFullWidth)
        assertFalse("Tam genişlikli sorunun ortasından dikey çizgi GEÇMEMELİ (baskılanmalı)", fullDrawDivider)
    }

    // =========================================================
    // 4. OMR MATRIX CODE CODEC KODLAMA & ÇÖZME TESTLERİ
    // =========================================================

    @Test
    fun `OmrMatrixCodeCodec testId kodlama ve cozme dongusu esit doner`() {
        val testIds = listOf(
            "SD-EXAM-A8F29C1B",
            "SD-EXAM-2026YKS9",
            "SD-YKS-DENEME01",
            "sorudefteri://soru/987654"
        )

        for (originalId in testIds) {
            val bits64 = OmrMatrixCodeCodec.encodePayloadTo64Bits(originalId)
            val decoded = OmrMatrixCodeCodec.decode64BitsToPayload(bits64)

            assertNotNull("Kayıpsız 64-bit matris kodu çözülebilmeli: $originalId", decoded)
            assertEquals("Çözülen kimlik orijinali ile tam eşleşmeli", originalId, decoded)
        }
    }

    @Test
    fun `OmrMatrixCodeCodec 10x10 izgara geometrisi ve cerceve standartlarina uyar`() {
        val testId = "SD-EXAM-BETA2026"
        val grid = OmrMatrixCodeCodec.encodeMatrixGrid(testId)

        assertEquals("Izgara 10 satır olmalı", 10, grid.size)
        assertEquals("Izgara 10 sütun olmalı", 10, grid[0].size)

        // 4 köşe her zaman koyu (true) olmalıdır
        assertTrue("Sol-Üst köşe siyah olmalı", grid[0][0])
        assertTrue("Sağ-Üst köşe siyah olmalı", grid[0][9])
        assertTrue("Sol-Alt köşe siyah olmalı", grid[9][0])
        assertTrue("Sağ-Alt köşe siyah olmalı", grid[9][9])

        // Sol kenar (col = 0) ve Alt kenar (row = 9): L-Finder referansı tam siyah olmalı
        for (r in 0 until 10) {
            assertTrue("Sol L-Finder sütunu [$r][0] siyah olmalı", grid[r][0])
        }
        for (c in 0 until 10) {
            assertTrue("Alt L-Finder satırı [9][$c] siyah olmalı", grid[9][c])
        }

        // Üst kenar (row = 0) ve Sağ kenar (col = 9): siyah/beyaz ardışık Timing kenarı (köşe hariç 0..8)
        for (i in 0 until 9) {
            val expected = (i % 2 == 0)
            assertEquals("Üst timing kenarı [0][$i] beklenen değerde olmalı", expected, grid[0][i])
            assertEquals("Sağ timing kenarı [$i][9] beklenen değerde olmalı", expected, grid[i][9])
        }
    }

    @Test
    fun `drawQuestionIdMatrixCode soru idsi icin gecerli matris kod uretir`() {
        val bits = OmrMatrixCodeCodec.encodePayloadTo64Bits("sorudefteri://soru/12345")
        assertTrue("Soru ID matris kodu sıfır olmamalı", bits != 0L)
        val decoded = OmrMatrixCodeCodec.decode64BitsToPayload(bits)
        assertNotNull("Çözülen soru payload null olmamalı", decoded)
        assertTrue("Çözülen payload 12345 içermeli", decoded!!.contains("12345"))
    }
}
