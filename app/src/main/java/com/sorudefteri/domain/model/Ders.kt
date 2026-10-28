package com.sorudefteri.domain.model

import androidx.compose.runtime.Immutable

/**
 * Ders modeli (TYT / AYT)
 *
 * @property id Dersin benzersiz kimliği (örn: "matematik", "turkce", "fizik", "ayt_fizik")
 * @property ad Dersin kullanıcıya gösterilen adı (örn: "Matematik", "Türkçe")
 * @property soruSayisi İlgili sınav oturumundaki soru sayısı (opsiyonel)
 * @property examType Sınav türü ("TYT" veya "AYT")
 * @property jsonKey Konular JSON dosyasındaki ders anahtarı
 */
@Immutable
data class Ders(
    val id: String,
    val ad: String,
    val soruSayisi: Int? = null,
    val examType: String = "TYT",
    val jsonKey: String = id.removePrefix("ayt_").removePrefix("tyt_")
) {
    val name: String get() = ad
}
