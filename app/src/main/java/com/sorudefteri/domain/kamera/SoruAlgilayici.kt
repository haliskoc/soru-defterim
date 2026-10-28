package com.sorudefteri.domain.kamera

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Tekli Soru modunun AKILLI TARAMA seçicisi (SAF).
 *
 * ML Kit metin analizi çıktısındaki SATIR kutularını kümeleyen, yalnızca
 * TEK bir soru bloğu seçen algoritma:
 *  1. Çok küçük (+ çok geniş) satırlar elenir.
 *  2. Satırlar dikey boşluk + yatay örtüşmeye göre kümelere bölünür
 *     (aynı soru+cıkları ardışık ve hizalıdır).
 *  3. Merkez bandındaki, en büyük alanlı küme (satır sayısı önceliğiyle) seçilir.
 *  4. Sonuç, sabit oranlarda dolgu ile sınırlar içinde yeni kutu olarak döner.
 *
 * Metinli yol ([secMetinli]): ML Kit satır metni + [SoruIsaretleri] ile
 * içerik-farkındalıklı sınır çizer; ROI ([roi]) yalnız kılavuz çevresinde arar.
 */
object SoruAlgilayici {

    fun sec(
        kutular: List<Kutu>,
        alanGen: Int,
        alanYuk: Int,
        minYukseklikOran: Double = 0.008,
        maxYukseklikOran: Double = 0.20,
        boslukKatSayisi: Double = 1.6,
        yatayOrtusEmbani: Double = 0.25,
        merkezSolSiniri: Double = 0.12,
        merkezSagSiniri: Double = 0.88,
        minAlanOran: Double = 0.02,
        dolguYatayOran: Double = 0.03,
        dolguDikeyOran: Double = 0.05
    ): Kutu? {
        if (kutular.isEmpty() || alanGen <= 0 || alanYuk <= 0) return null

        val filtre = kutular.filter {
            it.genislik > 0 && it.yukseklik > 0 &&
                it.yukseklik >= alanYuk * minYukseklikOran &&
                it.yukseklik <= alanYuk * maxYukseklikOran
        }
        if (filtre.isEmpty()) return null

        // 1) Dikey yakınlık + yatay örtüşme ile kümeleme
        //    Boşluk karşılaştırması SATIR-SATIR (son satır) yapılır; kümenin toplam
        //    yüksekliği kullanılsaydı ayrı soru blokları bir kümeye yapışardı.
        val sirali = filtre.sortedBy { it.ust }
        val kumeler = ArrayList<MutableList<Kutu>>()
        for (satir in sirali) {
            val sonKume = kumeler.lastOrNull()
            if (sonKume == null) {
                kumeler.add(mutableListOf(satir))
                continue
            }
            val sonSatir = sonKume.last()
            val birlesim = sonKume.reduce { a, b -> a.birlestir(b) }
            val bosluk = satir.ust - sonSatir.alt
            val izinliBosluk = boslukKatSayisi * max(sonSatir.yukseklik, satir.yukseklik).coerceAtLeast(1)
            val ortus = min(satir.sag, birlesim.sag) - max(satir.sol, birlesim.sol)
            val ortusFark = ortus / min(satir.genislik, birlesim.genislik).coerceAtLeast(1).toDouble()
            if (bosluk > izinliBosluk || ortusFark < yatayOrtusEmbani) {
                kumeler.add(mutableListOf(satir))
            } else {
                sonKume.add(satir)
            }
        }
        if (kumeler.isEmpty()) return null

        // 2) Puanlama: merkez bandı + alan + satır sayısı, tie: merkeze yakınlık
        var enIyiKume: MutableList<Kutu>? = null
        var enIyiSkor = Double.NEGATIVE_INFINITY
        var enIyiMesafe = Double.MAX_VALUE
        val toplamAlan = alanGen.toDouble() * alanYuk
        for (kume in kumeler) {
            val birlesim = kume.reduce { a, b -> a.birlestir(b) }
            if (birlesim.genislik <= 0 || birlesim.yukseklik <= 0) continue
            val cx = birlesim.merkezX / alanGen.toDouble()
            if (cx < merkezSolSiniri || cx > merkezSagSiniri) continue
            if (birlesim.alan < toplamAlan * minAlanOran) continue
            val skor = birlesim.alan.toDouble() * (1.0 + 0.1 * min(kume.size, 8))
            val mesafe = abs(birlesim.merkezX - alanGen / 2.0) + abs(birlesim.merkezY - alanYuk / 2.0)
            if (skor > enIyiSkor + 1e-9 ||
                (abs(skor - enIyiSkor) <= 1e-3 && mesafe < enIyiMesafe)
            ) {
                enIyiSkor = skor
                enIyiMesafe = mesafe
                enIyiKume = kume
            }
        }
        val secimKume = enIyiKume ?: return null

        // 3) Dolgu ile genişletilmiş kutu → sınırda kırp
        val birlesim = secimKume.reduce { a, b -> a.birlestir(b) }
        val padX = (alanGen * dolguYatayOran).roundToInt()
        val padY = (alanYuk * dolguDikeyOran).roundToInt()
        val solN = (birlesim.sol - padX).coerceIn(0, alanGen - 1)
        val ustN = (birlesim.ust - padY).coerceIn(0, alanYuk - 1)
        val sagN = (birlesim.sag + padX).coerceIn(solN + 1, alanGen)
        val altN = (birlesim.alt + padY).coerceIn(ustN + 1, alanYuk)
        return Kutu(solN, ustN, sagN - solN, altN - ustN)
    }

    /**
     * İçerik-farkındalıklı tek soru seçimi (SAF, yüksek doğruluk yolu).
     *
     * [sec] ile aynı greedy kümeleme; farklar:
     *  - Gevşek eşikler (şekil/formül boşluğunda kopmama): boşluk 2.2×, örtüşme 0.15.
     *  - [roi] dışı satırlar elenir (kılavuz çevresi arama).
     *  - Birden çok soru başı içeren küme başlardan bölünür (komşu soru ayrışır).
     *  - Puanlama: soru başı ×(1+0.6), şık satırı başına ×(1+0.15), bölüm-sonu ×0.3.
     */
    fun secMetinli(
        satirlar: List<TarananSatir>,
        alanGen: Int,
        alanYuk: Int,
        roi: Kutu? = null,
        minYukseklikOran: Double = 0.008,
        maxYukseklikOran: Double = 0.20,
        boslukKatSayisi: Double = 2.2,
        yatayOrtusEmbani: Double = 0.15,
        merkezSolSiniri: Double = 0.12,
        merkezSagSiniri: Double = 0.88,
        minAlanOran: Double = 0.02,
        dolguYatayOran: Double = 0.03,
        dolguDikeyOran: Double = 0.08
    ): Kutu? {
        if (satirlar.isEmpty() || alanGen <= 0 || alanYuk <= 0) return null

        val filtre = satirlar.filter {
            val k = it.kutu
            k.genislik > 0 && k.yukseklik > 0 &&
                k.yukseklik >= alanYuk * minYukseklikOran &&
                k.yukseklik <= alanYuk * maxYukseklikOran &&
                (roi == null || merkezIcinde(k, roi))
        }
        if (filtre.isEmpty()) return null

        val sirali = filtre.sortedBy { it.kutu.ust }
        val kumeler = ArrayList<MutableList<TarananSatir>>()
        for (satir in sirali) {
            val sonKume = kumeler.lastOrNull()
            if (sonKume == null) {
                kumeler.add(mutableListOf(satir))
                continue
            }
            val sonSatir = sonKume.last().kutu
            val birlesim = sonKume.map { it.kutu }.reduce { a, b -> a.birlestir(b) }
            val bosluk = satir.kutu.ust - sonSatir.alt
            val izinliBosluk = boslukKatSayisi * max(sonSatir.yukseklik, satir.kutu.yukseklik).coerceAtLeast(1)
            val ortus = min(satir.kutu.sag, birlesim.sag) - max(satir.kutu.sol, birlesim.sol)
            val ortusFark = ortus / min(satir.kutu.genislik, birlesim.genislik).coerceAtLeast(1).toDouble()
            if (bosluk > izinliBosluk || ortusFark < yatayOrtusEmbani) {
                kumeler.add(mutableListOf(satir))
            } else {
                sonKume.add(satir)
            }
        }

        // Çok soru başı → başlardan böl (öndeki başlıksız kuyruk önceki soruya aittir)
        val adaylar = kumeler.flatMap { bol(it) }
        if (adaylar.isEmpty()) return null

        var enIyiKume: List<TarananSatir>? = null
        var enIyiSkor = Double.NEGATIVE_INFINITY
        var enIyiMesafe = Double.MAX_VALUE
        val cozumDisiVar = adaylar.any { kume -> kume.none { SoruIsaretleri.bolumSonu(it.metin) } }
        val toplamAlan = alanGen.toDouble() * alanYuk
        for (kume in adaylar) {
            val birlesim = kume.map { it.kutu }.reduce { a, b -> a.birlestir(b) }
            if (birlesim.genislik <= 0 || birlesim.yukseklik <= 0) continue
            val cx = birlesim.merkezX / alanGen.toDouble()
            if (cx < merkezSolSiniri || cx > merkezSagSiniri) continue
            if (birlesim.alan < toplamAlan * minAlanOran) continue
            val cozum = kume.any { SoruIsaretleri.bolumSonu(it.metin) }
            if (cozum && cozumDisiVar) continue
            var skor = birlesim.alan.toDouble() * (1.0 + 0.1 * min(kume.size, 8))
            val basiSay = kume.count { SoruIsaretleri.soruBasi(it.metin) }
            val sikSay = kume.count { SoruIsaretleri.sik(it.metin) }
            if (basiSay > 0) skor *= 1.0 + 0.6 * min(basiSay, 2)
            skor *= 1.0 + 0.15 * min(sikSay, 4)
            val mesafe = abs(birlesim.merkezX - alanGen / 2.0) + abs(birlesim.merkezY - alanYuk / 2.0)
            if (skor > enIyiSkor + 1e-9 ||
                (abs(skor - enIyiSkor) <= 1e-3 && mesafe < enIyiMesafe)
            ) {
                enIyiSkor = skor
                enIyiMesafe = mesafe
                enIyiKume = kume
            }
        }
        val secimKume = enIyiKume ?: return null

        val birlesim = secimKume.map { it.kutu }.reduce { a, b -> a.birlestir(b) }
        val padX = (alanGen * dolguYatayOran).roundToInt()
        val padY = (alanYuk * dolguDikeyOran).roundToInt()
        val solN = (birlesim.sol - padX).coerceIn(0, alanGen - 1)
        val ustN = (birlesim.ust - padY).coerceIn(0, alanYuk - 1)
        val sagN = (birlesim.sag + padX).coerceIn(solN + 1, alanGen)
        val altN = (birlesim.alt + padY).coerceIn(ustN + 1, alanYuk)
        return Kutu(solN, ustN, sagN - solN, altN - ustN)
    }

    private fun merkezIcinde(k: Kutu, roi: Kutu): Boolean =
        k.merkezX in roi.sol..roi.sag && k.merkezY in roi.ust..roi.alt

    private fun bol(kume: List<TarananSatir>): List<List<TarananSatir>> {
        val baslar = kume.indices.filter { SoruIsaretleri.soruBasi(kume[it].metin) }
        if (baslar.size <= 1) return listOf(kume)
        return baslar.mapIndexed { i, bas ->
            val son = if (i + 1 < baslar.size) baslar[i + 1] else kume.size
            kume.subList(bas, son)
        }
    }
}
