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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.OutputStream

class PdfDenemeKitapcikGeneratorTest {

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
    fun `generatePdfKitapcik should handle empty question list gracefully without crashing`() {
        val emptyList = emptyList<Soru>()

        val generatedFile = PdfDenemeKitapcikGenerator.generatePdfKitapcik(
            context = mockContext,
            sorular = emptyList,
            targetTitle = "YKS Derece",
            documentProvider = { mockPdfDocument }
        )

        assertNotNull(generatedFile)
        assertTrue("PDF dosyası oluşturulmuş olmalı", generatedFile.exists())
        assertTrue("Dosya .pdf uzantılı olmalı", generatedFile.name.endsWith(".pdf"))
        assertTrue("Dosya boyutu 0'dan büyük olmalı", generatedFile.length() > 0)

        // Kapak, boş sayfa ve arka kapak çizilmeli
        verify(atLeast = 3) { mockPdfDocument.startPage(any()) }
        verify(atLeast = 3) { mockPdfDocument.finishPage(any()) }
        verify(exactly = 1) { mockPdfDocument.writeTo(any()) }
        verify(exactly = 1) { mockPdfDocument.close() }
    }

    @Test
    fun `generatePdfKitapcik should generate booklet with sample questions successfully`() {
        val sampleSorular = listOf(
            Soru(
                id = 1L,
                imagePath = "/non/existent/image1.jpg",
                correctAnswer = "C",
                examType = "TYT",
                subjectId = "matematik",
                topic = "Fonksiyonlar",
                mistakeType = HataTipi.BILGI_EKSIKLIGI,
                questionText = "f(x) = 2x + 5 olduğuna göre f(3) değeri kaçtır?"
            ),
            Soru(
                id = 2L,
                imagePath = "",
                correctAnswer = "A",
                examType = "AYT",
                subjectId = "ayt_fizik",
                topic = "Kuvvet ve Hareket",
                mistakeType = HataTipi.DIKKAT_HATASI,
                ocrMetni = "Sürtünmesiz yatay düzlemde 2 kg kütleli cisme 10 N kuvvet uygulanıyor."
            ),
            Soru(
                id = 3L,
                imagePath = "/non/existent/image3.jpg",
                correctAnswer = null,
                examType = "TYT",
                subjectId = "turkce",
                topic = "Paragrafta Anlam",
                questionText = "Bu parçadan aşağıdaki yargılardan hangisine ulaşılamaz?"
            )
        )

        val generatedFile = PdfDenemeKitapcikGenerator.generatePdfKitapcik(
            context = mockContext,
            sorular = sampleSorular,
            targetTitle = "Tıp Fakültesi Hedefim",
            documentProvider = { mockPdfDocument }
        )

        assertNotNull(generatedFile)
        assertTrue(generatedFile.exists())
        assertTrue(generatedFile.name.startsWith("Deneme_Kitapcigi_"))
        assertTrue(generatedFile.name.endsWith(".pdf"))

        // Kapak + Test sayfası + Arka kapak
        verify(atLeast = 3) { mockPdfDocument.startPage(any()) }
        verify(atLeast = 3) { mockPdfDocument.finishPage(any()) }
        verify(exactly = 1) { mockPdfDocument.writeTo(any()) }
        verify(exactly = 1) { mockPdfDocument.close() }
    }

    @Test
    fun `generatePdfKitapcik should handle large number of questions spanning multiple pages`() {
        val manyQuestions = (1..30).map { i ->
            Soru(
                id = i.toLong(),
                imagePath = "",
                correctAnswer = listOf("A", "B", "C", "D", "E")[i % 5],
                examType = if (i % 2 == 0) "TYT" else "AYT",
                subjectId = if (i % 2 == 0) "matematik" else "ayt_biyoloji",
                topic = "Konu $i",
                questionText = "Soru metni $i: Bu bir test sorusudur ve iki sütunlu düzende akışı test eder."
            )
        }

        val generatedFile = PdfDenemeKitapcikGenerator.generatePdfKitapcik(
            context = mockContext,
            sorular = manyQuestions,
            targetTitle = "YKS 2026 Derece",
            documentProvider = { mockPdfDocument }
        )

        assertNotNull(generatedFile)
        assertTrue(generatedFile.exists())
        // 30 soru en az 2 test sayfası gerektirir; kapak + test sayfaları + arka kapak >= 4 sayfa
        verify(atLeast = 4) { mockPdfDocument.startPage(any()) }
        verify(atLeast = 4) { mockPdfDocument.finishPage(any()) }
        verify(exactly = 1) { mockPdfDocument.close() }
    }

    @Test
    fun `shareOrPrintPdf should handle non-existent file without throwing unhandled exception`() {
        val nonExistentFile = File(cacheDir, "does_not_exist.pdf")
        // Should not crash
        PdfDenemeKitapcikGenerator.shareOrPrintPdf(mockContext, nonExistentFile)
    }
}
