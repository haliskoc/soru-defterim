package com.sorudefteri.domain.model

/**
 * Kütüphane listeleme sıralama seçenekleri.
 */
enum class SortOrder(val displayName: String) {
    DATE_DESC("En Yeni"),
    DATE_ASC("En Eski"),
    SUBJECT("Derse Göre")
}
