package com.sorudefteri.data.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import com.sorudefteri.domain.model.HataTipi
import com.sorudefteri.domain.model.Soru
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.OutputStream

class PdfExportConfigTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var mockContext: Context
    private lateinit var mockPdfDocument: PdfDocument
    private lateinit var mockPage: PdfDocument.Page
    private lateinit var mockCanvas: Canvas
    private lateinit var cacheDir: File

    @Before
    fun setup() {
        cacheDir = tempFolder.newFolder("cache")
        mockContext = mockk(relaxed = true)
        every { mockContext.cacheDir } returns cacheDir
        every { mockContext.packageName } returns "com.sorudefteri"

        mockPdfDocument = mockk(relaxed = true)
        mockPage = mockk(relaxed = true)
        mockCanvas = mockk(relaxed = true)

        every { mockPage.canvas } returns mockCanvas
        every { mockPdfDocument.startPage(any()) } returns mockPage
        every { mockPdfDocument.writeTo(any()) } answers {
            val out = firstArg<OutputStream>()
            out.write("%PDF-1.4\n%EOF\n".toByteArray(Charsets.UTF_8))
        }
    }

    @Test
    fun `default config should have OSYM_DENEME template and sensible defaults`() {
        val config = PdfExportConfig()

        assertEquals(PdfTemplateMode.OSYM_DENEME, config.templateMode)
        assertEquals(PdfTargetMedium.PRINT_PAPER, config.targetMedium)
        assertEquals(PdfDensity.BALANCED, config.density)
        assertEquals(2, config.columnsCount)
        assertTrue(config.includeAnswerKey)
        assertTrue(config.includeSourceInfo)
        assertTrue(config.includeDifficulty)
        assertTrue(config.includeMistakeType)
        assertEquals(WorkspaceType.STANDARD, config.workspaceType)

        // DÜZELTİLEN VARSAYILANLAR: includeAiSolution, includeStudentNote ve
        // includeStylusDrawing `false` idi. Kullanıcı AI çözümünü kaydetmiş olsa
        // bile PDF'e hiç girmiyordu; "çözümlerim yedeğe aktarılmıyor" şeklinde
        // bildiriliyordu. Artık varsayılan olarak AÇIK; kullanıcı dialog'dan kapatabilir.
        assertTrue(
            "AI çözümü varsayılan olarak PDF'e dahil edilmeli",
            config.includeAiSolution
        )
        assertTrue(
            "Öğrenci notu varsayılan olarak PDF'e dahil edilmeli",
            config.includeStudentNote
        )
        assertTrue(
            "Kalem çizimleri varsayılan olarak PDF'e dahil edilmeli",
            config.includeStylusDrawing
        )
    }

    @Test
    fun `template modes should have correct titles and descriptions`() {
        val modes = PdfTemplateMode.values()
        assertEquals(4, modes.size)

        val osym = PdfTemplateMode.OSYM_DENEME
        assertEquals("ÖSYM Deneme Kitapçığı", osym.title)

        val fasikul = PdfTemplateMode.COZUMLU_FASIKUL
        assertEquals("Açıklamalı Çözüm Fasikülü", fasikul.title)

        val ogretmen = PdfTemplateMode.OGRETMEN_BULTENI
        assertEquals("Öğretmen / Etüt Bülteni", ogretmen.title)

        val eko = PdfTemplateMode.EKO_KOMPAKT
        assertEquals("Eko / Kompakt Soru Listesi", eko.title)
    }

    @Test
    fun `generatePdfWithConfig should generate booklet with OSYM_DENEME config`() {
        val config = PdfExportConfig(
            title = "Matematik Deneme",
            templateMode = PdfTemplateMode.OSYM_DENEME,
            columnsCount = 2,
            includeAnswerKey = true
        )

        val sampleSorular = listOf(
            Soru(
                id = 1L,
                imagePath = "",
                correctAnswer = "B",
                examType = "TYT",
                subjectId = "matematik",
                topic = "Üslü Sayılar",
                mistakeType = HataTipi.DIKKAT_HATASI,
                questionText = "2^x = 16 olduğuna göre x kaçtır?"
            )
        )

        val generatedFile = PdfDenemeKitapcikGenerator.generatePdfWithConfig(
            context = mockContext,
            sorular = sampleSorular,
            config = config,
            documentProvider = { mockPdfDocument }
        )

        assertNotNull(generatedFile)
        assertTrue(generatedFile.exists())
        assertTrue(generatedFile.name.endsWith(".pdf"))
        verify(atLeast = 3) { mockPdfDocument.startPage(any()) }
        verify(atLeast = 3) { mockPdfDocument.finishPage(any()) }
        verify(exactly = 1) { mockPdfDocument.writeTo(any()) }
        verify(exactly = 1) { mockPdfDocument.close() }
    }

    @Test
    fun `generatePdfWithConfig should generate booklet with COZUMLU_FASIKUL config and single column`() {
        val config = PdfExportConfig(
            title = "Çözümlü Matematik",
            templateMode = PdfTemplateMode.COZUMLU_FASIKUL,
            columnsCount = 1,
            includeAiSolution = true,
            includeStudentNote = true,
            includeMistakeType = true,
            includeAnswerKey = true
        )

        val sampleSorular = listOf(
            Soru(
                id = 10L,
                imagePath = "",
                correctAnswer = "D",
                examType = "AYT",
                subjectId = "matematik",
                topic = "Türev",
                mistakeType = HataTipi.BILGI_EKSIKLIGI,
                questionText = "f(x) = x^3 - 3x fonksiyonunun yerel ekstremum noktaları nelerdir?",
                aiExplanation = "Türev alınır: f'(x) = 3x^2 - 3. Kökler x = 1 ve x = -1.",
                note = "İşaret tablosuna dikkat et!"
            )
        )

        val generatedFile = PdfDenemeKitapcikGenerator.generatePdfWithConfig(
            context = mockContext,
            sorular = sampleSorular,
            config = config,
            documentProvider = { mockPdfDocument }
        )

        assertNotNull(generatedFile)
        assertTrue(generatedFile.exists())
        verify(atLeast = 2) { mockPdfDocument.startPage(any()) }
        verify(atLeast = 2) { mockPdfDocument.finishPage(any()) }
        verify(exactly = 1) { mockPdfDocument.writeTo(any()) }
        verify(exactly = 1) { mockPdfDocument.close() }
    }

    @Test
    fun `generatePdfWithConfig should generate booklet with OGRETMEN_BULTENI and large workspace`() {
        val config = PdfExportConfig(
            title = "Hocaya Sorulacaklar",
            templateMode = PdfTemplateMode.OGRETMEN_BULTENI,
            columnsCount = 1,
            includeMistakeType = true,
            workspaceType = WorkspaceType.LARGE
        )

        val sampleSorular = listOf(
            Soru(
                id = 20L,
                imagePath = "",
                correctAnswer = "A",
                examType = "TYT",
                subjectId = "fizik",
                topic = "Optik",
                mistakeType = HataTipi.SORUYU_ANLAMAMA,
                questionText = "Düzlem aynada görüntü oluşumu hakkında soru."
            )
        )

        val generatedFile = PdfDenemeKitapcikGenerator.generatePdfWithConfig(
            context = mockContext,
            sorular = sampleSorular,
            config = config,
            documentProvider = { mockPdfDocument }
        )

        assertNotNull(generatedFile)
        assertTrue(generatedFile.exists())
        verify(atLeast = 3) { mockPdfDocument.startPage(any()) }
        verify(atLeast = 3) { mockPdfDocument.finishPage(any()) }
        verify(exactly = 1) { mockPdfDocument.close() }
    }

    @Test
    fun `generatePdfWithConfig should generate booklet with EKO_KOMPAKT config`() {
        val config = PdfExportConfig(
            title = "Hızlı Tekrar Testi",
            templateMode = PdfTemplateMode.EKO_KOMPAKT,
            columnsCount = 2,
            includeAiSolution = false,
            includeStudentNote = false,
            includeMistakeType = false,
            includeAnswerKey = true,
            workspaceType = WorkspaceType.NONE
        )

        val sampleSorular = (1..6).map { i ->
            Soru(
                id = i.toLong(),
                imagePath = "",
                correctAnswer = "A",
                examType = "TYT",
                subjectId = "turkce",
                topic = "Paragraf",
                questionText = "Paragraf sorusu $i metni."
            )
        }

        val generatedFile = PdfDenemeKitapcikGenerator.generatePdfWithConfig(
            context = mockContext,
            sorular = sampleSorular,
            config = config,
            documentProvider = { mockPdfDocument }
        )

        assertNotNull(generatedFile)
        assertTrue(generatedFile.exists())
        verify(atLeast = 2) { mockPdfDocument.startPage(any()) }
        verify(atLeast = 2) { mockPdfDocument.finishPage(any()) }
        verify(exactly = 1) { mockPdfDocument.close() }
    }
}
