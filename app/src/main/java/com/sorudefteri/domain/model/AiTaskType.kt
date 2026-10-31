package com.sorudefteri.domain.model

/**
 * AI görev türleri. Her görev için ayrı sağlayıcı/model yönlendirmesi yapılabilir.
 */
enum class AiTaskType(val displayName: String, val description: String, val requiresVision: Boolean) {
    COZUM("Soru Çözümü", "Adım adım çözüm ve koç modu", true),
    CHAT("Sokratik Sohbet", "Soru üzerine çok turlu AI hoca", false),
    ETIKETLEME("Otomatik Etiketleme", "Görselden ders/konu/şık tespiti", true),
    BENZER_SORU("Benzer Soru Üretimi", "Kazanımı koruyarak yeni soru", false),
    OCR_TEMIZLEME("OCR Temizleme", "Bozuk metin ve LaTeX düzeltme", false)
}

/** Bir görev için tercih edilen sağlayıcı + model. */
data class AiTaskRoute(val provider: AiProviderType, val model: String)
