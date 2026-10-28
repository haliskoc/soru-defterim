package com.sorudefteri.ui.kamera

import androidx.camera.core.CameraSelector
import com.sorudefteri.domain.kamera.Kutu

/**
 * Çözümleyici ekran-etkileşim kumandası.
 *
 * `ImageAnalysis` bağlaması ekranda kalır; maliyetli ML Kit işi TEKLİ mod dışında
 * durdurulur ([acik]) ve ön/arka kamera geçişinde koordinat yansıtma bilgisi
 * ([aynaliMi]) buradan güncellenir. AnalysisExecutor iş parçacığından erişildiği
 * için alanlar `@Volatile`.
 *
 * Pil disiplini: [yuksekDogruluk] kapalıysa analiz 450ms aralıkla çalışır;
 * [kilavuzRoi] yalnız yüksek doğrulukta arama alanını kısıtlar (ROI).
 */
class AnalizKontrolcu {
    /** Çözümleyici aktif mi? (TEKLİ soru modunda `true`) */
    @Volatile
    var acik: Boolean = true

    /** Ön kamera ise `true`: önizleme yansıtılır ama çekilen kare yansıtılmaz. */
    @Volatile
    var aynaliMi: Boolean = false

    /** Yüksek doğruluk aktif mi? (1280x720 + 300ms + metinli tespit + refine) */
    @Volatile
    var yuksekDogruluk: Boolean = false

    /** Kılavuz ROI (analiz px): yalnız bu bölgedeki satırlar aranır. null = tüm kadraj. */
    @Volatile
    var kilavuzRoi: Kutu? = null

    /**
     * PERF: Soru kilitlendiğinde veya ayar alt çubuğu açıkken `true` yapılır;
     * OCR durur (kilit-sonrası birleştirme ile CPU yarışması biter).
     */
    @Volatile
    var duraklatildi: Boolean = false

    fun guncelle(lensFacing: Int) {
        aynaliMi = lensFacing == CameraSelector.LENS_FACING_FRONT
    }
}
