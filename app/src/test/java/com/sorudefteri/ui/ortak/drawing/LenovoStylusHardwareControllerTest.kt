package com.sorudefteri.ui.ortak.drawing

import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * LenovoStylusHardwareController için birim testleri.
 * TB-710FU donanım profili, çift tıklama (<300ms) algoritması ve eylem tetikleme mantığını doğrular.
 */
class LenovoStylusHardwareControllerTest {

    private lateinit var controller: LenovoStylusHardwareController

    @Before
    fun setUp() {
        controller = LenovoStylusHardwareController(
            context = null, // Test modunda headless
            doubleTapTimeoutMs = 300L,
            maxDistanceThresholdPx = 50f,
            isHapticFeedbackEnabled = false,
            isPalmRejectionEnabled = true
        )
    }

    @Test
    fun `ilk tekli dokunma cift tiklama sayilmaz false doner`() {
        val result = controller.onStylusTap(eventTimeMs = 1000L, x = 100f, y = 100f)
        assertFalse("İlk dokunma çift tıklama sayılmamalıdır", result)
    }

    @Test
    fun `300ms altindaki ardil dokunus basariyla cift tiklama olarak algilanir`() {
        // 1. Dokunuş: t = 1000ms
        val tap1 = controller.onStylusTap(eventTimeMs = 1000L, x = 100f, y = 100f)
        assertFalse(tap1)

        // 2. Dokunuş: t = 1180ms (aralık 180ms < 300ms, mesafe 5px < 50px)
        val tap2 = controller.onStylusTap(eventTimeMs = 1180L, x = 103f, y = 104f)
        assertTrue("180ms aralıklı dokunuş çift tıklama olarak tanınmalıdır", tap2)
    }

    @Test
    fun `cift tiklama algilandiktan sonra sonraki dokunus sifirlanmis olmalidir`() {
        // Çift tıklama tamamla
        controller.onStylusTap(eventTimeMs = 1000L, x = 100f, y = 100f)
        val doubleTap = controller.onStylusTap(eventTimeMs = 1200L, x = 100f, y = 100f)
        assertTrue(doubleTap)

        // Üçüncü dokunuş yeni bir seri başlatmalı, çift tıklama olmamalı
        val thirdTap = controller.onStylusTap(eventTimeMs = 1300L, x = 100f, y = 100f)
        assertFalse("Çift tıklama sonrası durum sıfırlanmalıdır", thirdTap)
    }

    @Test
    fun `300ms esigini asan dokunuslar cift tiklama sayilmaz`() {
        controller.onStylusTap(eventTimeMs = 1000L, x = 100f, y = 100f)

        // 350ms sonra ikinci dokunuş (>300ms)
        val lateTap = controller.onStylusTap(eventTimeMs = 1350L, x = 100f, y = 100f)
        assertFalse("300ms üzerindeki aralık çift tıklama sayılmamalıdır", lateTap)

        // Bu geç dokunuştan 150ms sonra gelen yeni dokunuş çift tıklama olmalı
        val quickTap = controller.onStylusTap(eventTimeMs = 1500L, x = 100f, y = 100f)
        assertTrue("Geç dokunuş yeni başlangıç noktası olmalıdır", quickTap)
    }

    @Test
    fun `mesafe esigini (50px) asan dokunuslar cift tiklama sayilmaz`() {
        controller.onStylusTap(eventTimeMs = 1000L, x = 50f, y = 50f)

        // 100ms sonra ama ekranın uzak bir yerinde (dx=150, dy=150 -> distance ~212px)
        val farTap = controller.onStylusTap(eventTimeMs = 1100L, x = 200f, y = 200f)
        assertFalse("Uzak mesafe dokunuşu çift tıklama sayılmamalıdır", farTap)
    }

    @Test
    fun `isStylusToolType yalnizca stylus ve silgi araclarini tanir`() {
        assertTrue(controller.isStylusToolType(MotionEvent.TOOL_TYPE_STYLUS))
        assertTrue(controller.isStylusToolType(MotionEvent.TOOL_TYPE_ERASER))
        assertFalse(controller.isStylusToolType(MotionEvent.TOOL_TYPE_FINGER))
        assertFalse(controller.isStylusToolType(MotionEvent.TOOL_TYPE_MOUSE))
        assertFalse(controller.isStylusToolType(MotionEvent.TOOL_TYPE_UNKNOWN))
    }

    @Test
    fun `executeDoubleTapAction PEN_ERASER_TOGGLE eylemini dogru calistirir`() {
        var penEraserToggled = false
        controller.executeDoubleTapAction(
            action = StylusDoubleTapAction.PEN_ERASER_TOGGLE,
            onTogglePenEraser = { penEraserToggled = true },
            onUndo = {},
            onOpenColorPalette = {},
            onShowCoachHint = {}
        )
        assertTrue("PEN_ERASER_TOGGLE eylemi tetiklenmelidir", penEraserToggled)
    }

    @Test
    fun `executeDoubleTapAction UNDO eylemini dogru calistirir`() {
        var undoCalled = false
        controller.executeDoubleTapAction(
            action = StylusDoubleTapAction.UNDO,
            onTogglePenEraser = {},
            onUndo = { undoCalled = true },
            onOpenColorPalette = {},
            onShowCoachHint = {}
        )
        assertTrue("UNDO eylemi tetiklenmelidir", undoCalled)
    }

    @Test
    fun `executeDoubleTapAction COLOR_PALETTE eylemini dogru calistirir`() {
        var paletteOpened = false
        controller.executeDoubleTapAction(
            action = StylusDoubleTapAction.COLOR_PALETTE,
            onTogglePenEraser = {},
            onUndo = {},
            onOpenColorPalette = { paletteOpened = true },
            onShowCoachHint = {}
        )
        assertTrue("COLOR_PALETTE eylemi tetiklenmelidir", paletteOpened)
    }

    @Test
    fun `executeDoubleTapAction COACH_HINT eylemini dogru calistirir`() {
        var hintOpened = false
        controller.executeDoubleTapAction(
            action = StylusDoubleTapAction.COACH_HINT,
            onTogglePenEraser = {},
            onUndo = {},
            onOpenColorPalette = {},
            onShowCoachHint = { hintOpened = true }
        )
        assertTrue("COACH_HINT eylemi tetiklenmelidir", hintOpened)
    }

    @Test
    fun `StylusDoubleTapAction fromString dönüsümleri basarili calisir`() {
        assertEquals(StylusDoubleTapAction.PEN_ERASER_TOGGLE, StylusDoubleTapAction.fromString("PEN_ERASER_TOGGLE"))
        assertEquals(StylusDoubleTapAction.UNDO, StylusDoubleTapAction.fromString("undo"))
        assertEquals(StylusDoubleTapAction.COLOR_PALETTE, StylusDoubleTapAction.fromString("color_palette"))
        assertEquals(StylusDoubleTapAction.COACH_HINT, StylusDoubleTapAction.fromString("coach_hint"))
        // Gecersiz degerler varsayilana dönmeli
        assertEquals(StylusDoubleTapAction.PEN_ERASER_TOGGLE, StylusDoubleTapAction.fromString(null))
        assertEquals(StylusDoubleTapAction.PEN_ERASER_TOGGLE, StylusDoubleTapAction.fromString("UNKNOWN_ACTION"))
    }

    @Test
    fun `reset metodu tap gecmisini temizler`() {
        controller.onStylusTap(eventTimeMs = 1000L, x = 100f, y = 100f)
        controller.reset()

        // 100ms sonra gelen dokunuş sıfırlanmış olacağı için çift tıklama olmamalıdır
        val tap = controller.onStylusTap(eventTimeMs = 1100L, x = 100f, y = 100f)
        assertFalse("reset sonrası ilk dokunuş çift tıklama olmamalıdır", tap)
    }

    @Test
    fun `onSensorDoubleTap her zaman true doner`() {
        assertTrue(controller.onSensorDoubleTap())
    }
}
