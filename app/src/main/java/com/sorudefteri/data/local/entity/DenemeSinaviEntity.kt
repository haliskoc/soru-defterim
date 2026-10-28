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
        Index(value = ["testId"]),
        Index(value = ["sinavTuru"]),
        Index(value = ["tarih"]),
        Index(value = ["createdAt"])
    ]
)
data class DenemeSinaviEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val testId: String = "",
    val baslik: String,
    val yayinEvi: String = "",
    val sinavTuru: String,
    val tarih: Long,
    val sureDakika: Int = 165,
    val toplamSoru: Int = 0,
    val toplamDogru: Int = 0,
    val toplamYanlis: Int = 0,
    val toplamBos: Int = 0,
    val toplamNet: Double,
    val dersNetleriJson: String,
    val cevapAnahtariJson: String? = null,
    val ogrenciCevaplariJson: String? = null,
    val notlar: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Int = 0
) {
    fun toDomain(json: Json): DenemeSinavi {
        val list = try {
            json.decodeFromString<List<DenemeDersNeti>>(dersNetleriJson)
        } catch (_: Exception) {
            emptyList()
        }
        return DenemeSinavi(
            id = id,
            testId = testId,
            baslik = baslik,
            yayinEvi = yayinEvi,
            sinavTuru = sinavTuru,
            tarih = tarih,
            sureDakika = sureDakika,
            toplamSoru = toplamSoru,
            toplamDogru = toplamDogru,
            toplamYanlis = toplamYanlis,
            toplamBos = toplamBos,
            toplamNet = toplamNet,
            dersNetleri = list,
            cevapAnahtariJson = cevapAnahtariJson,
            ogrenciCevaplariJson = ogrenciCevaplariJson,
            notlar = notlar,
            createdAt = createdAt,
            updatedAt = updatedAt,
            isDeleted = isDeleted
        )
    }
}

fun DenemeSinavi.toEntity(json: Json): DenemeSinaviEntity {
    return DenemeSinaviEntity(
        id = id,
        testId = testId,
        baslik = baslik,
        yayinEvi = yayinEvi,
        sinavTuru = sinavTuru,
        tarih = tarih,
        sureDakika = sureDakika,
        toplamSoru = toplamSoru,
        toplamDogru = toplamDogru,
        toplamYanlis = toplamYanlis,
        toplamBos = toplamBos,
        toplamNet = toplamNet,
        dersNetleriJson = json.encodeToString(dersNetleri),
        cevapAnahtariJson = cevapAnahtariJson,
        ogrenciCevaplariJson = ogrenciCevaplariJson,
        notlar = notlar,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isDeleted = isDeleted
    )
}
