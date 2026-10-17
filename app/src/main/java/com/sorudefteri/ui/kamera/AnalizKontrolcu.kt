package com.sorudefteri.ui.kamera

import androidx.camera.core.CameraSelector

/**
 * Çözümleyici ekran-etkileşim kumandası.
 *
 * `ImageAnalysis` bağlaması ekranda kalır; maliyetli ML Kit işi TEKLİ mod dışında
 * durdurulur ([acik]) ve ön/arka kamera geçişinde koordinat yansıtma bilgisi
 * ([aynaliMi]) buradan güncellenir. AnalysisExecutor iş parçacığından erişildiği
 * için alanlar `@Volatile`.
 */
class AnalizKontrolcu {
    /** Çözümleyici aktif mi? (TEKLİ soru modunda `true`) */
    @Volatile
    var acik: Boolean = true

    /** Ön kamera ise `true`: önizleme yansıtılır ama çekilen kare yansıtılmaz. */
    @Volatile
    var aynaliMi: Boolean = false

    fun guncelle(lensFacing: Int) {
        aynaliMi = lensFacing == CameraSelector.LENS_FACING_FRONT
    }
}
