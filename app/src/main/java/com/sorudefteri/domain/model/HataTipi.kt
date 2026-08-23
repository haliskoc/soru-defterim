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
    ISLEM_HATASI(
        displayName = "İşlem Hatası",
        code = "ISLEM_HATASI",
        description = "Dört işlem, işaret, sadeleştirme veya parantez hatası",
        defaultColor = Color(0xFFEF5350) // Kırmızı
    ),
    KONU_EKSIGI(
        displayName = "Konu Eksiği",
        code = "KONU_EKSIGI",
        description = "Konu kazanımının veya kuralların tam bilinmemesi",
        defaultColor = Color(0xFFAB47BC) // Mor
    ),
    BILGI_EKSIKLIGI(
        displayName = "Bilgi Eksikliği",
        code = "BILGI_EKSIKLIGI",
        description = "Formül, tanım veya kuralın hatırlanamaması",
        defaultColor = Color(0xFF42A5F5) // Mavi
    ),
    DIKKAT_HATASI(
        displayName = "Dikkat Hatası",
        code = "DIKKAT_HATASI",
        description = "Soruyu yanlış okuma, şıkkı kaydırma veya veriyi atlama",
        defaultColor = Color(0xFFFFA726) // Turuncu
    ),
    SORUYU_ANLAMAMA(
        displayName = "Soruyu Anlamama",
        code = "SORUYU_ANLAMAMA",
        description = "Yeni nesil sorunun kurgusunu kavrayamama",
        defaultColor = Color(0xFF26A69A) // Teal
    ),
    DIGER(
        displayName = "Diğer",
        code = "DIGER",
        description = "Süre yetersizliği, yöntem hatası veya diğer nedenler",
        defaultColor = Color(0xFF78909C) // Gri-Mavi
    );

    companion object {
        fun fromString(value: String?): HataTipi? {
            if (value.isNullOrBlank()) return null
            return entries.firstOrNull {
                it.name.equals(value, ignoreCase = true) ||
                it.displayName.equals(value, ignoreCase = true) ||
                it.code.equals(value, ignoreCase = true)
            }
        }

        fun fromName(name: String?): HataTipi? = fromString(name)

        fun toDisplayName(name: String?): String {
            return fromString(name)?.displayName ?: (name ?: "Belirtilmemiş")
        }
    }
}
