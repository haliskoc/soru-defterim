package com.sorudefteri.data.ocr

import android.content.Context
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class OcrHelperTest {

    private lateinit var context: Context
    private lateinit var ocrHelper: OcrHelper

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        ocrHelper = OcrHelper(context)
    }

    @Test
    fun `cleanOcrArtifacts should normalize excessive whitespaces and newlines`() {
        val raw = "Soru    1:    \n\n\n\n   f(x) = x^2   +   2x   \n\n\n   A) 1    B) 2"
        val cleaned = ocrHelper.cleanOcrArtifacts(raw)

        val expected = "Soru 1:\n\nf(x) = x^2 + 2x\n\nA) 1 B) 2"
        assertEquals(expected, cleaned)
    }

    @Test
    fun `cleanOcrArtifacts should return empty string for blank input`() {
        assertEquals("", ocrHelper.cleanOcrArtifacts("   \n\n  \t  "))
    }
}
