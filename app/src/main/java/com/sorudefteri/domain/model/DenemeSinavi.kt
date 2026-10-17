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
 * Deneme Sınavı Ajandası Domain Modeli (3.4)
 */
data class DenemeSinavi(
    val id: Long = 0L,
    val baslik: String,
    val sinavTuru: String, // "TYT", "AYT", "LGS", "KPSS"
    val tarih: Long = System.currentTimeMillis(),
    val toplamNet: Double,
    val dersNetleri: List<DenemeDersNeti> = emptyList(),
    val notlar: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
