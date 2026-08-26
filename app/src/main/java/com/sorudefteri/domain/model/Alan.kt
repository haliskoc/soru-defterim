package com.sorudefteri.domain.model

/**
 * YKS, LGS ve KPSS Hazırlık Alanları (Tracks).
 * Kullanıcının ders listesini, AYT/sınav filtrelerini ve konu kataloğunu belirler.
 */
enum class Alan(
    val displayName: String,
    val description: String
) {
    SAYISAL(
        displayName = "Sayısal",
        description = "Matematik, Geometri, Fizik, Kimya, Biyoloji"
    ),
    ESIT_AGIRLIK(
        displayName = "Eşit Ağırlık",
        description = "Matematik, Geometri, Türk Dili ve Edebiyatı, Tarih-1, Coğrafya-1"
    ),
    SOZEL(
        displayName = "Sözel",
        description = "Türk Dili ve Edebiyatı, Tarih-1/2, Coğrafya-1/2, Felsefe Grubu, Din Kültürü"
    ),
    YDT(
        displayName = "YDT (Dil)",
        description = "Yabancı Dil Testi (İngilizce Dilbilgisi, Kelime Bilgisi, Okuma-Anlama, Çeviri)"
    ),
    LGS(
        displayName = "LGS (8. Sınıf)",
        description = "Türkçe, Matematik, Fen Bilimleri, T.C. İnkılap Tarihi, Din Kültürü, Yabancı Dil"
    ),
    KPSS(
        displayName = "KPSS (Genel Yetenek & Genel Kültür)",
        description = "Genel Yetenek (Türkçe, Matematik), Genel Kültür (Tarih, Coğrafya, Vatandaşlık, Güncel)"
    );

    companion object {
        /**
         * Geriye dönük uyumluluk için DIL alias'ı
         */
        val DIL: Alan get() = YDT

        fun fromString(value: String?): Alan {
            if (value.isNullOrBlank()) return SAYISAL
            val normalized = value.trim().uppercase()
            if (normalized == "DIL" || normalized == "YDT") return YDT
            return entries.firstOrNull {
                it.name.equals(normalized, ignoreCase = true) ||
                it.displayName.equals(value.trim(), ignoreCase = true)
            } ?: SAYISAL
        }
    }
}
