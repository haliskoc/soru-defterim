package com.sorudefteri.data.images

import android.graphics.Bitmap
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageFilterProcessorTest {

    @Test
    fun `all filter types have valid titles and non-empty descriptions`() {
        for (type in ImageFilterType.values()) {
            assertTrue("Title should not be blank for $type", type.title.isNotBlank())
            assertTrue("Description should not be blank for $type", type.description.isNotBlank())
        }
    }

    @Test
    fun `all filter matrix arrays must have exactly 20 elements (4x5)`() {
        for (type in ImageFilterType.values()) {
            val matrix = ImageFilterProcessor.getColorMatrixArray(type)
            assertEquals("Matrix for $type must have exactly 20 elements", 20, matrix.size)
        }
    }

    @Test
    fun `ORIGINAL filter should be identity matrix and preserve pixel colors`() {
        val originalMatrix = ImageFilterProcessor.getColorMatrixArray(ImageFilterType.ORIGINAL)
        val expectedIdentity = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
        assertArrayEquals(expectedIdentity, originalMatrix, 0.0001f)

        val samplePixel = ImageFilterProcessor.transformPixel(123, 45, 200, 255, ImageFilterType.ORIGINAL)
        assertEquals(123, samplePixel[0])
        assertEquals(45, samplePixel[1])
        assertEquals(200, samplePixel[2])
        assertEquals(255, samplePixel[3])
    }

    @Test
    fun `SCANNER_BW filter should remove yellow table lamp cast and bleach paper to white`() {
        // Yellow lamp cast on paper: high red/green, lower blue
        val yellowPaper = ImageFilterProcessor.transformPixel(240, 220, 160, 255, ImageFilterType.SCANNER_BW)

        // Result should be pure white (255, 255, 255)
        assertEquals(255, yellowPaper[0])
        assertEquals(255, yellowPaper[1])
        assertEquals(255, yellowPaper[2])
    }

    @Test
    fun `SCANNER_BW filter should keep dark question text crisp black`() {
        // Dark printed text or pen: low values
        val darkText = ImageFilterProcessor.transformPixel(35, 35, 35, 255, ImageFilterType.SCANNER_BW)

        // Result should be pure black (0, 0, 0)
        assertEquals(0, darkText[0])
        assertEquals(0, darkText[1])
        assertEquals(0, darkText[2])
    }

    @Test
    fun `SCANNER_BW filter should output equal RGB values (grayscale)`() {
        val arbitraryPixel = ImageFilterProcessor.transformPixel(180, 120, 90, 255, ImageFilterType.SCANNER_BW)
        assertEquals(arbitraryPixel[0], arbitraryPixel[1])
        assertEquals(arbitraryPixel[1], arbitraryPixel[2])
    }

    @Test
    fun `NEGATIVE_DARK filter should invert white paper to black for OLED night studying`() {
        // White or light paper
        val whitePaper = ImageFilterProcessor.transformPixel(250, 250, 250, 255, ImageFilterType.NEGATIVE_DARK)

        // Should invert to pure black
        assertEquals(0, whitePaper[0])
        assertEquals(0, whitePaper[1])
        assertEquals(0, whitePaper[2])
    }

    @Test
    fun `NEGATIVE_DARK filter should invert dark text to bright high contrast white`() {
        // Dark text
        val darkText = ImageFilterProcessor.transformPixel(30, 30, 30, 255, ImageFilterType.NEGATIVE_DARK)

        // Should invert to high contrast white / near 255
        assertTrue("Text should become bright white in negative mode", darkText[0] >= 240)
        assertEquals(darkText[0], darkText[1])
        assertEquals(darkText[1], darkText[2])
    }

    @Test
    fun `MAGIC_COLOR filter should enhance contrast while maintaining vibrant colors`() {
        // Red pen correction
        val redPen = ImageFilterProcessor.transformPixel(220, 30, 30, 255, ImageFilterType.MAGIC_COLOR)
        assertTrue("Red channel should remain prominent", redPen[0] > 240)
        assertTrue("Green channel should be suppressed", redPen[1] < 50)
        assertTrue("Blue channel should be suppressed", redPen[2] < 50)

        // Blue pen line
        val bluePen = ImageFilterProcessor.transformPixel(30, 40, 220, 255, ImageFilterType.MAGIC_COLOR)
        assertTrue("Blue channel should remain prominent", bluePen[2] > 240)
        assertTrue("Red channel should be suppressed", bluePen[0] < 50)

        // Yellow-cast paper background should be lifted towards white
        val yellowPaper = ImageFilterProcessor.transformPixel(230, 215, 160, 255, ImageFilterType.MAGIC_COLOR)
        assertEquals(255, yellowPaper[0])
        assertEquals(255, yellowPaper[1])
        assertTrue("Blue channel on paper should be boosted to reduce yellow tint", yellowPaper[2] > 200)
    }

    @Test
    fun `applyFilter with ORIGINAL returns source instance directly`() {
        val mockBmp = mockk<Bitmap>()
        val result = ImageFilterProcessor.applyFilter(mockBmp, ImageFilterType.ORIGINAL)
        assertSame(mockBmp, result)
    }

    @Test
    fun `applyFilter with recycled bitmap returns source safely without crash`() {
        val mockBmp = mockk<Bitmap> {
            every { isRecycled } returns true
        }
        val result = ImageFilterProcessor.applyFilter(mockBmp, ImageFilterType.SCANNER_BW)
        assertSame(mockBmp, result)
    }

    @Test
    fun `applyFilter with invalid dimension returns source safely without crash`() {
        val mockBmp = mockk<Bitmap> {
            every { isRecycled } returns false
            every { width } returns 0
            every { height } returns 100
        }
        val result = ImageFilterProcessor.applyFilter(mockBmp, ImageFilterType.SCANNER_BW)
        assertSame(mockBmp, result)
    }
}
