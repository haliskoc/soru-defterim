package com.sorudefteri.domain.model

import kotlinx.serialization.Serializable

/**
 * Deneme sınavındaki bir dersin net detayları
 */
@Serializable
data class DenemeDersNeti(
    val dersAdi: String,
    val dogruSayisi: Int,
    val yanlisSayisi: Int,
    val bosSayisi: Int,
    val net: Double
)

/**
 * Deneme Sınavı Ajandası ve Optik Eşleme Domain Modeli
 */
data class DenemeSinavi(
    val id: Long = 0L,
    val testId: String = "",
    val baslik: String,
    val yayinEvi: String = "",
    val sinavTuru: String, // "TYT", "AYT", "LGS", "KPSS"
    val tarih: Long = System.currentTimeMillis(),
    val sureDakika: Int = 165,
    val toplamSoru: Int = 0,
    val toplamDogru: Int = 0,
    val toplamYanlis: Int = 0,
    val toplamBos: Int = 0,
    val toplamNet: Double,
    val dersNetleri: List<DenemeDersNeti> = emptyList(),
    val cevapAnahtariJson: String? = null,
    val ogrenciCevaplariJson: String? = null,
    val notlar: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Int = 0
)
