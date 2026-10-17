package com.sorudefteri.ui.ortak.drawing

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View

/**
 * Stylus üzerinde çift tıklama yapıldığında tetiklenecek eylemler.
 */
enum class StylusDoubleTapAction(
    val title: String,
    val description: String
) {
    PEN_ERASER_TOGGLE("Kalem ↔ Silgi", "Kalem ve silgi araçları arasında geçiş yapar"),
    UNDO("Geri Al", "Son çizimi veya eylemi geri alır"),
    COLOR_PALETTE("Hızlı Renk / Araç Seçici", "Renk ve araç paletini açıp kapatır"),
    COACH_HINT("Koç Modu İpucu Aç", "Yapay zeka koç ipucunu görüntüler");

    companion object {
        fun fromString(value: String?): StylusDoubleTapAction {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: PEN_ERASER_TOGGLE
        }
    }
}

/**
 * Lenovo Yoga Tab (TB-710FU) & Lenovo Tab Pen Pro (Kapasitif çift tıklama sensörlü stylus)
 * donanım kontrolcüsü.
 *
 * Özellikler:
 * - TB-710FU donanım profili denetimi
 * - <300ms aralık ve mesafe eşikli çift dokunma (double-tap) algılama algoritması
 * - Stylus ikincil buton / sensör durum değişimi kontrolü
 * - Haptic Tick (anlık tıklama hissi) titreşim yönetimi
 */
class LenovoStylusHardwareController(
    private val context: Context? = null,
    var doubleTapTimeoutMs: Long = DEFAULT_DOUBLE_TAP_TIMEOUT_MS,
    var maxDistanceThresholdPx: Float = DEFAULT_MAX_DISTANCE_PX,
    var isHapticFeedbackEnabled: Boolean = true,
    var isPalmRejectionEnabled: Boolean = true
) {
    companion object {
        const val HARDWARE_MODEL_TB710FU = "TB-710FU"
        const val DEFAULT_DOUBLE_TAP_TIMEOUT_MS = 300L
        const val DEFAULT_MAX_DISTANCE_PX = 50f
    }

    private var lastTapTimeMs: Long = 0L
    private var lastTapX: Float = 0f
    private var lastTapY: Float = 0f
    private var lastButtonState: Int = 0

    /**
     * Cihazın Lenovo Yoga Tab (TB-710FU serisi) olup olmadığını tespit eder.
     */
    fun isLenovoYogaTab(): Boolean {
        val model = Build.MODEL.orEmpty().uppercase()
        val device = Build.DEVICE.orEmpty().uppercase()
        val product = Build.PRODUCT.orEmpty().uppercase()
        val brand = Build.BRAND.orEmpty().uppercase()
        val manufacturer = Build.MANUFACTURER.orEmpty().uppercase()

        return model.contains("TB-710") || model.contains("TB710") ||
               device.contains("TB-710") || device.contains("TB710") ||
               product.contains("TB710") ||
               ((brand.contains("LENOVO") || manufacturer.contains("LENOVO")) && model.contains("YOGA"))
    }

    /**
     * Çift tıklama algılama algoritması.
     * İki dokunuş arasındaki süre <300ms ve mesafe eşik değeri (50px) altındaysa true döner.
     */
    fun onStylusTap(
        eventTimeMs: Long = System.currentTimeMillis(),
        x: Float = 0f,
        y: Float = 0f
    ): Boolean {
        if (lastTapTimeMs == 0L) {
            lastTapTimeMs = eventTimeMs
            lastTapX = x
            lastTapY = y
            return false
        }

        val interval = eventTimeMs - lastTapTimeMs
        val dx = x - lastTapX
        val dy = y - lastTapY
        val distance = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()

        val isDoubleTap = (interval in 1..doubleTapTimeoutMs) && (distance <= maxDistanceThresholdPx)

        if (isDoubleTap) {
            lastTapTimeMs = 0L // Başarılı çift dokunuş sonrası sıfırla
            return true
        } else {
            lastTapTimeMs = eventTimeMs
            lastTapX = x
            lastTapY = y
            return false
        }
    }

    /**
     * Lenovo Tab Pen Pro kapasitif dokunmatik sensöründen gelen doğrudan çift dokunuş olayı.
     */
    fun onSensorDoubleTap(): Boolean {
        return true
    }

    /**
     * Bir pointer'ın kalem (stylus) olup olmadığını doğrular.
     */
    fun isStylusToolType(toolType: Int): Boolean {
        return toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER
    }

    /**
     * MotionEvent girişini işler. Stylus butonu veya hızlı çift tıklama algılanırsa
     * onDoubleTap çağrılır ve true döner.
     */
    fun processMotionEvent(event: MotionEvent, onDoubleTap: () -> Unit): Boolean {
        val toolType = event.getToolType(0)
        if (isPalmRejectionEnabled && !isStylusToolType(toolType) && event.pointerCount == 1) {
            return false
        }

        // 1. Donanımsal stylus butonu veya kapasitif sensör buton durumu kontrolü
        val currentButtonState = event.buttonState
        val buttonChanged = (currentButtonState != lastButtonState)
        val isButtonPressed = (currentButtonState and MotionEvent.BUTTON_STYLUS_PRIMARY != 0) ||
                             (currentButtonState and MotionEvent.BUTTON_SECONDARY != 0)

        if (buttonChanged && isButtonPressed) {
            lastButtonState = currentButtonState
            onDoubleTap()
            return true
        }
        lastButtonState = currentButtonState

        // 2. ACTION_UP zamanlamalı çift dokunma kontrolü
        if (event.actionMasked == MotionEvent.ACTION_UP && isStylusToolType(toolType)) {
            val isDoubleTap = onStylusTap(event.eventTime, event.x, event.y)
            if (isDoubleTap) {
                onDoubleTap()
                return true
            }
        }

        return false
    }

    /**
     * Stylus etkileşimi ve çift tıklamalarda anlık Haptic Tick hissi verir.
     */
    fun performHapticTick(view: View? = null) {
        if (!isHapticFeedbackEnabled) return

        var hapticHandled = false
        try {
            if (view != null) {
                hapticHandled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                } else {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }
            }
        } catch (_: Exception) {}

        if (!hapticHandled && context != null) {
            try {
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    manager?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }

                vibrator?.let {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        it.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                    } else {
                        @Suppress("DEPRECATION")
                        it.vibrate(15L)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Seçili çift tıklama eylemini çalıştırır ve haptik titreşimi tetikler.
     */
    fun executeDoubleTapAction(
        action: StylusDoubleTapAction,
        onTogglePenEraser: () -> Unit,
        onUndo: () -> Unit,
        onOpenColorPalette: () -> Unit,
        onShowCoachHint: () -> Unit,
        view: View? = null
    ) {
        performHapticTick(view)
        when (action) {
            StylusDoubleTapAction.PEN_ERASER_TOGGLE -> onTogglePenEraser()
            StylusDoubleTapAction.UNDO -> onUndo()
            StylusDoubleTapAction.COLOR_PALETTE -> onOpenColorPalette()
            StylusDoubleTapAction.COACH_HINT -> onShowCoachHint()
        }
    }

    /**
     * Durum geçmişini temizler.
     */
    fun reset() {
        lastTapTimeMs = 0L
        lastTapX = 0f
        lastTapY = 0f
        lastButtonState = 0
    }
}
