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
}
