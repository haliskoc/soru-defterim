package com.sorudefteri.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.sorudefteri.domain.model.DenemeDersNeti
import com.sorudefteri.domain.model.DenemeSinavi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Deneme Sınavları Tablosu (Room)
 */
@Entity(
    tableName = "denemeler",
    indices = [
        Index(value = ["sinavTuru"]),
        Index(value = ["tarih"]),
        Index(value = ["createdAt"])
    ]
)
data class DenemeSinaviEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val baslik: String,
    val sinavTuru: String,
    val tarih: Long,
    val toplamNet: Double,
    val dersNetleriJson: String,
    val notlar: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toDomain(json: Json): DenemeSinavi {
        val list = try {
            json.decodeFromString<List<DenemeDersNeti>>(dersNetleriJson)
        } catch (_: Exception) {
            emptyList()
        }
        return DenemeSinavi(
            id = id,
            baslik = baslik,
            sinavTuru = sinavTuru,
            tarih = tarih,
            toplamNet = toplamNet,
            dersNetleri = list,
            notlar = notlar,
            createdAt = createdAt
        )
    }
}

fun DenemeSinavi.toEntity(json: Json): DenemeSinaviEntity {
    return DenemeSinaviEntity(
        id = id,
        baslik = baslik,
        sinavTuru = sinavTuru,
        tarih = tarih,
        toplamNet = toplamNet,
        dersNetleriJson = json.encodeToString(dersNetleri),
        notlar = notlar,
        createdAt = createdAt
    )
}
