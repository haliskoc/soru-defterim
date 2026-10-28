package com.sorudefteri.data.sync

import android.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Telefon ↔ tablet eşleşme protokolü (Nearby BYTES üstünde JSON).
 *
 * Mesaj tipleri: HELLO → OZET(+chunk) → OZET_BITTI → SIRA_SENDE →
 * SORU(+görsel FILE) → SORU_BITTI → OZET_SONUC. Çift-yön aynı anda YASAK;
 * başlatan önce ALIR sonra GÖNDERİR ([SyncDeposu]).
 */
object SyncProtokol {

    const val PROTO_VERSION = 1
    const val SERVICE_ID = "com.sorudefteri.sync.v1"

    /** BYTES limiti (1 MiB) altında güvenli chunk boyutu. */
    const val CHUNK_BOYUT = 900 * 1024

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    fun kodla(mesaj: SyncMesaj): String = when (mesaj) {
        is SyncMesaj.Hello -> json.encodeToString(SyncKapsul("HELLO", json.encodeToString(mesaj.icerik)))
        is SyncMesaj.Ozet -> json.encodeToString(SyncKapsul("OZET", json.encodeToString(mesaj.icerik)))
        is SyncMesaj.OzetBitti -> json.encodeToString(SyncKapsul("OZET_BITTI", "{}"))
        is SyncMesaj.SiraSende -> json.encodeToString(SyncKapsul("SIRA_SENDE", "{}"))
        is SyncMesaj.Soru -> json.encodeToString(SyncKapsul("SORU", json.encodeToString(mesaj.icerik)))
        is SyncMesaj.SoruBitti -> json.encodeToString(SyncKapsul("SORU_BITTI", json.encodeToString(mesaj.icerik)))
        is SyncMesaj.DenemePaketMesaj -> json.encodeToString(SyncKapsul("DENEME_PAKET", json.encodeToString(mesaj.icerik)))
        is SyncMesaj.OzetSonuc -> json.encodeToString(SyncKapsul("OZET_SONUC", json.encodeToString(mesaj.icerik)))
        is SyncMesaj.Hata -> json.encodeToString(SyncKapsul("HATA", json.encodeToString(mesaj.icerik)))
    }

    fun coz(metin: String): SyncMesaj {
        val kapsul = json.decodeFromString<SyncKapsul>(metin)
        return when (kapsul.tip) {
            "HELLO" -> SyncMesaj.Hello(json.decodeFromString(kapsul.govde))
            "OZET" -> SyncMesaj.Ozet(json.decodeFromString(kapsul.govde))
            "OZET_BITTI" -> SyncMesaj.OzetBitti
            "SIRA_SENDE" -> SyncMesaj.SiraSende
            "SORU" -> SyncMesaj.Soru(json.decodeFromString(kapsul.govde))
            "SORU_BITTI" -> SyncMesaj.SoruBitti(json.decodeFromString(kapsul.govde))
            "DENEME_PAKET" -> SyncMesaj.DenemePaketMesaj(json.decodeFromString(kapsul.govde))
            "OZET_SONUC" -> SyncMesaj.OzetSonuc(json.decodeFromString(kapsul.govde))
            "HATA" -> SyncMesaj.Hata(json.decodeFromString(kapsul.govde))
            else -> SyncMesaj.Hata(SyncHata("BILINMEYEN_TIP", "Bilinmeyen mesaj: ${kapsul.tip}"))
        }
    }

    /** Büyük listeyi chunk'lara böler (alıcı [ChunkBirlesim] ile toplar). */
    fun ozetChunkla(tum: List<OzetSatir>): List<OzetPaket> {
        if (tum.isEmpty()) return listOf(OzetPaket(0, 1, emptyList()))
        val parcalar = tum.chunked(400)
        return parcalar.mapIndexed { i, p -> OzetPaket(i, parcalar.size, p) }
    }

    fun bytesAl(metin: String): ByteArray = metin.toByteArray(Charsets.UTF_8)

    fun metinAl(bytes: ByteArray): String = bytes.toString(Charsets.UTF_8)

    fun dosyaB64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    fun b64Coz(s: String): ByteArray = Base64.decode(s, Base64.DEFAULT)
}

@Serializable
private data class SyncKapsul(val tip: String, val govde: String)

sealed interface SyncMesaj {
    data class Hello(val icerik: SyncHello) : SyncMesaj
    data class Ozet(val icerik: OzetPaket) : SyncMesaj
    data object OzetBitti : SyncMesaj
    data object SiraSende : SyncMesaj
    data class Soru(val icerik: SoruPaket) : SyncMesaj
    data class SoruBitti(val icerik: SoruBittiPaket) : SyncMesaj
    data class DenemePaketMesaj(val icerik: DenemeSyncPaket) : SyncMesaj
    data class OzetSonuc(val icerik: SyncSonuc) : SyncMesaj
    data class Hata(val icerik: SyncHata) : SyncMesaj
}

@Serializable
data class DenemeSyncItem(
    val testId: String = "",
    val baslik: String = "",
    val yayinEvi: String = "",
    val sinavTuru: String = "TYT",
    val tarih: Long = 0L,
    val sureDakika: Int = 165,
    val toplamSoru: Int = 0,
    val toplamDogru: Int = 0,
    val toplamYanlis: Int = 0,
    val toplamBos: Int = 0,
    val toplamNet: Double = 0.0,
    val dersNetleriJson: String = "[]",
    val cevapAnahtariJson: String? = null,
    val ogrenciCevaplariJson: String? = null,
    val notlar: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val isDeleted: Int = 0
)

@Serializable
data class DenemeSyncPaket(
    val denemeler: List<DenemeSyncItem> = emptyList()
)

@Serializable
data class SyncHello(
    val proto: Int = SyncProtokol.PROTO_VERSION,
    val cihazAdi: String = "",
    val cihazKodu: String = "",
    val soruSayisi: Int = 0,
    val appVersion: String = ""
)

/** e=evrenselId, u=updatedAt, h=contentHash, d=isDeleted (kısa anahtarlar = küçük payload). */
@Serializable
data class OzetSatir(val e: String, val u: Long = 0L, val h: String = "", val d: Int = 0)

@Serializable
data class OzetPaket(val paket: Int = 0, val toplam: Int = 1, val liste: List<OzetSatir> = emptyList())

@Serializable
data class GorselBilgi(val tur: String = "soru", val dosyaId: String = "", val bayt: Int = 0)

@Serializable
data class SoruPaket(
    val paket: Int = 0,
    val toplam: Int = 1,
    val soru: String = "",
    val gorseller: List<GorselBilgi> = emptyList()
)

@Serializable
data class SoruBittiPaket(val gonderilen: Int = 0, val atlanan: Int = 0)

@Serializable
data class SyncSonuc(
    val eklenen: Int = 0,
    val guncellenen: Int = 0,
    val guncel: Int = 0,
    val cakisan: Int = 0,
    val gorselsiz: Int = 0,
    val eslenenDeneme: Int = 0
) {
    operator fun plus(diger: SyncSonuc) = SyncSonuc(
        eklenen + diger.eklenen,
        guncellenen + diger.guncellenen,
        guncel + diger.guncel,
        cakisan + diger.cakisan,
        gorselsiz + diger.gorselsiz,
        eslenenDeneme + diger.eslenenDeneme
    )
}

@Serializable
data class SyncHata(val kod: String = "", val mesaj: String = "")

/** Chunk'lı OZET alıcısı: sırayla toplar, tamamlanınca listeyi verir. */
class ChunkBirlesim {
    private val parcalar = sortedMapOf<Int, List<OzetSatir>>()
    private var beklenen = -1

    fun ekle(paket: OzetPaket): List<OzetSatir>? {
        beklenen = paket.toplam
        parcalar[paket.paket] = paket.liste
        if (beklenen <= 0 || parcalar.size < beklenen) return null
        return (0 until beklenen).flatMap { parcalar[it].orEmpty() }
    }

    fun sifirla() {
        parcalar.clear()
        beklenen = -1
    }
}
