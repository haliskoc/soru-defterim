package com.sorudefteri.domain.model

/**
 * YKS Alanları. AYT ders listesini belirler.
 */
enum class Alan(
    val displayName: String,
    val description: String
) {
    SAYISAL(
        displayName = "Sayısal",
        description = "Matematik, Fizik, Kimya, Biyoloji"
    ),
    ESIT_AGIRLIK(
        displayName = "Eşit Ağırlık",
        description = "Matematik, Türk Dili ve Edebiyatı, Tarih-1, Coğrafya-1"
    ),
    SOZEL(
        displayName = "Sözel",
        description = "Türk Dili ve Edebiyatı, Tarih-1/2, Coğrafya-1/2, Felsefe Grubu, Din"
    ),
    DIL(
        displayName = "Dil (YDT)",
        description = "YDT İngilizce, Almanca, Fransızca"
    );

    companion object {
        fun fromString(value: String?): Alan {
            if (value.isNullOrBlank()) return SAYISAL
            return entries.firstOrNull {
                it.name.equals(value, ignoreCase = true) ||
                it.displayName.equals(value, ignoreCase = true)
            } ?: SAYISAL
        }
    }
}
