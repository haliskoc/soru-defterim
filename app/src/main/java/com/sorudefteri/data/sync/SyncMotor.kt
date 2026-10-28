package com.sorudefteri.data.sync

/**
 * Eşleşme karar motoru (SAF — Android bağımlılığı yok, unit testli).
 *
 * Anahtar: `evrenselId` (hiç değişmez). Eşitse `updatedAt` büyük olan kazanır
 * (son-yazan-kazanır); saat farkı toleransı içinde ve içerik aynıysa dokunulmaz.
 */
object SyncMotor {

    /** İki cihaz saati arası kabul edilen fark (ms). */
    const val SAAT_TOLERANS_MS = 60_000L

    enum class Karar { EKLE, GUNCELLE, ATLA }

    data class YerelKayit(
        val evrenselId: String,
        val updatedAt: Long,
        val contentHash: String,
        val isFavorite: Boolean = false,
        val createdAt: Long = 0L
    )

    data class UzakKayit(
        val evrenselId: String,
        val updatedAt: Long,
        val contentHash: String
    )

    data class Karsilastirma(
        val karar: Karar,
        /** true: uzak daha yeni ama yerel de değişmiş (özette "çakışan" sayacına). */
        val cakisma: Boolean = false
    )

    /**
     * @param yerel null = bizde yok.
     * @param bazUpdatedAt Baz özetinden beri yerelde değiştiyse çakışma sayılır
     *   (çağıran, tur-başı özetindeki değeri verir; null = izlenmiyor).
     */
    fun karar(yerel: YerelKayit?, uzak: UzakKayit, bazUpdatedAt: Long? = null): Karsilastirma {
        if (yerel == null) return Karsilastirma(Karar.EKLE)
        val fark = uzak.updatedAt - yerel.updatedAt
        if (fark <= 0) return Karsilastirma(Karar.ATLA)
        if (fark <= SAAT_TOLERANS_MS && uzak.contentHash == yerel.contentHash) {
            return Karsilastirma(Karar.ATLA)
        }
        val cakisma = bazUpdatedAt != null && yerel.updatedAt > bazUpdatedAt
        return Karsilastirma(Karar.GUNCELLE, cakisma)
    }

    /** GUNCELLE yolunda alan birleştirme: favori OR, createdAt min korunur. */
    fun birlestirFavori(yerelFav: Boolean, uzakFav: Boolean): Boolean = yerelFav || uzakFav

    fun birlestirCreatedAt(yerel: Long, uzak: Long): Long = minOf(yerel, uzak)
}
