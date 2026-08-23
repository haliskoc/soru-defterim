package com.sorudefteri.domain.model

import androidx.compose.ui.graphics.Color

/**
 * Soruda yapılan hata türü sınıflandırması
 */
enum class HataTipi(
    val displayName: String,
    val code: String,
    val description: String,
    val defaultColor: Color
) {
    BILGI_EKSIKLIGI(
        displayName = "Bilgi Eksikliği",
        code = "BILGI_EKSIKLIGI",
        description = "Tanım, kural veya teorik bilginin eksik olması",
        defaultColor = Color(0xFF1E88E5) // Canlı Mavi
    ),
    DIKKAT_HATASI(
        displayName = "Dikkat Hatası",
        code = "DIKKAT_HATASI",
        description = "Soruyu yanlış okuma, şıkkı kaydırma veya veriyi atlama",
        defaultColor = Color(0xFFFB8C00) // Canlı Turuncu
    ),
    FORMUL_HATASI(
        displayName = "Formül Hatası",
        code = "FORMUL_HATASI",
        description = "Yanlış formül uygulama veya formülü hatırlayamama",
        defaultColor = Color(0xFF8E24AA) // Mor
    ),
    ISLEM_HATASI(
        displayName = "İşlem Hatası",
        code = "ISLEM_HATASI",
        description = "Dört işlem, işaret, sadeleştirme veya parantez hatası",
        defaultColor = Color(0xFFE53935) // Kırmızı
    ),
    SURE_YETMEDI(
        displayName = "Süre Yetmedi",
        code = "SURE_YETMEDI",
        description = "Zaman yönetimi nedeniyle soruyu yetiştirememe",
        defaultColor = Color(0xFFF4511E) // Koyu Turuncu
    ),
    SORU_KOKU(
        displayName = "Soru Kökü Hatası",
        code = "SORU_KOKU",
        description = "Soru kökünü (değildir, olamaz, kesinlikle) yanlış anlama",
        defaultColor = Color(0xFF00897B) // Teal
    ),
    KONU_EKSIGI(
        displayName = "Konu Eksiği",
        code = "KONU_EKSIGI",
        description = "Konu kazanımının veya kuralların tam bilinmemesi",
        defaultColor = Color(0xFF6D4C41) // Kahverengi
    ),
    SORUYU_ANLAMAMA(
        displayName = "Soruyu Anlamama",
        code = "SORUYU_ANLAMAMA",
        description = "Yeni nesil sorunun kurgusunu kavrayamama",
        defaultColor = Color(0xFF3949AB) // İndigo
    ),
    DIGER(
        displayName = "Diğer",
        code = "DIGER",
        description = "Yöntem hatası veya diğer nedenler",
        defaultColor = Color(0xFF757575) // Nötr Gri
    );

    companion object {
        fun fromString(value: String?): HataTipi? {
            if (value.isNullOrBlank()) return null
            val clean = value.trim()
            return entries.firstOrNull {
                it.name.equals(clean, ignoreCase = true) ||
                it.displayName.equals(clean, ignoreCase = true) ||
                it.code.equals(clean, ignoreCase = true)
            }
        }

        fun fromName(name: String?): HataTipi? = fromString(name)

        fun toDisplayName(name: String?): String {
            return fromString(name)?.displayName ?: (name ?: "Belirtilmemiş")
        }
    }
}

