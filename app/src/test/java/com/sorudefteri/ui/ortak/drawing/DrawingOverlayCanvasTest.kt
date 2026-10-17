package com.sorudefteri.ui.ortak.drawing

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DrawingOverlayCanvas matematiksel hesaplamaları ve veri modelleri için birim testleri.
 */
class DrawingOverlayCanvasTest {

    @Test
    fun `calculateDynamicStrokeWidth erasers icin taban genisligi 3_5 katina cikarir`() {
        val width = calculateDynamicStrokeWidth(
            baseWidth = 6f,
            pressure = 0.5f,
            tilt = 0f,
            toolType = DrawingToolType.ERASER
        )
        assertEquals(21f, width, 0.01f)
    }

    @Test
    fun `calculateDynamicStrokeWidth highlighter icin taban genisligi 2_8 katina cikarir`() {
        val width = calculateDynamicStrokeWidth(
            baseWidth = 6f,
            pressure = 0.5f,
            tilt = 0f,
            toolType = DrawingToolType.HIGHLIGHTER
        )
        assertEquals(16.8f, width, 0.01f)
    }

    @Test
    fun `calculateDynamicStrokeWidth basinc arttikca genisligi artirir`() {
        val lowPressureWidth = calculateDynamicStrokeWidth(
            baseWidth = 10f,
            pressure = 0.1f,
            tilt = 0f,
            toolType = DrawingToolType.PEN
        )
        val highPressureWidth = calculateDynamicStrokeWidth(
            baseWidth = 10f,
            pressure = 0.9f,
            tilt = 0f,
            toolType = DrawingToolType.PEN
        )
        assertTrue(
            "Yüksek basınç ($highPressureWidth) düşük basınçtan ($lowPressureWidth) daha kalın olmalıdır",
            highPressureWidth > lowPressureWidth
        )
    }

    @Test
    fun `calculateDynamicStrokeWidth kalem yatirildiginda tilt genisligi artirir`() {
        val uprightWidth = calculateDynamicStrokeWidth(
            baseWidth = 10f,
            pressure = 0.5f,
            tilt = 0f, // Dik
            toolType = DrawingToolType.PEN
        )
        val tiltedWidth = calculateDynamicStrokeWidth(
            baseWidth = 10f,
            pressure = 0.5f,
            tilt = 1.2f, // Yatık (Tilt)
            toolType = DrawingToolType.PEN
        )
        assertTrue(
            "Yatık kalem ($tiltedWidth) dik kalemden ($uprightWidth) daha geniş çizgi üretmelidir",
            tiltedWidth > uprightWidth
        )
    }

    @Test
    fun `calculateModulatedColor silgi icin seffaf doner`() {
        val color = calculateModulatedColor(
            baseColor = Color.Red,
            tilt = 0f,
            toolType = DrawingToolType.ERASER
        )
        assertEquals(Color.Transparent, color)
    }

    @Test
    fun `calculateModulatedColor fosforlu icin yari saydam doner`() {
        val color = calculateModulatedColor(
            baseColor = Color.Blue,
            tilt = 0f,
            toolType = DrawingToolType.HIGHLIGHTER
        )
        assertEquals(0.35f, color.alpha, 0.01f)
    }

    @Test
    fun `calculateModulatedColor yatik kalemde golgeleme icin alphayi yumusatir`() {
        val baseColor = Color(0xFF000000)
        val uprightColor = calculateModulatedColor(
            baseColor = baseColor,
            tilt = 0f,
            toolType = DrawingToolType.PEN
        )
        val tiltedColor = calculateModulatedColor(
            baseColor = baseColor,
            tilt = 1.4f,
            toolType = DrawingToolType.PEN
        )
        assertTrue(
            "Yatık kalemdeki saydamlık (${tiltedColor.alpha}) dik kalemden (${uprightColor.alpha}) düşük olmalıdır",
            tiltedColor.alpha < uprightColor.alpha
        )
    }
}
