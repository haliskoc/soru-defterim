package com.sorudefteri.domain.kamera

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * "Ölç-sonra-kırp" motorunun SAF çekirdeği.
 *
 * KameraX PreviewView FILL_CENTER (varsayılan ScaleType) varsayımıyla:
 *   s      = max(gorselGen/gorunumGen, gorselYuk/gorunumYuk)  (dolgu ölçeği)
 *   dW,dH  = gorselGen/s, gorselYuk/s                        (görüntünün görünümdeki px boyutu)
 *   vOff   = (gorunum - dW,dH) / 2                           (merkezleme kayması)
 *
 * Görüntü→Görünüm:  xV = vOff + xI / s
 * Görünüm→Görüntü:  xI = (xV - vOff) * s
 *
 * Ön kamera: önizleme X ekseninde YANSITILMIŞ gösterilir ama JPEG yansıtılmaz.
 * Bu yüzden aynalı durumda X dönüşümleri çevrilir (yansıtma DOĞRU tarafı hesaplar).
 *
 * Tüm fonksiyonlar saf + Double tabanlıdır; birim testlerle doğrulanır.
 */
object KirpmaHesaplayici {

    /** Dolgu sıfır: her görüntü pikselinin görünümde kapladığı px (s ≥ 1). */
    fun dolguOlcegi(gorselGen: Int, gorselYuk: Int, gorunumGen: Int, gorunumYuk: Int): Double {
        if (gorselGen <= 0 || gorselYuk <= 0 || gorunumGen <= 0 || gorunumYuk <= 0) return 1.0
        return max(gorselGen.toDouble() / gorunumGen, gorselYuk.toDouble() / gorunumYuk)
    }

    private data class Ogun(val olcek: Double, val vOffX: Double, val vOffY: Double, val gorselGen: Int, val gorselYuk: Int)

    private fun ogun(gorselGen: Int, gorselYuk: Int, gorunumGen: Int, gorunumYuk: Int): Ogun? {
        if (gorselGen <= 0 || gorselYuk <= 0 || gorunumGen <= 0 || gorunumYuk <= 0) return null
        val olcek = dolguOlcegi(gorselGen, gorselYuk, gorunumGen, gorunumYuk)
        val dW = gorselGen / olcek
        val dH = gorselYuk / olcek
        return Ogun(olcek, (gorunumGen - dW) / 2.0, (gorunumYuk - dH) / 2.0, gorselGen, gorselYuk)
    }

    /**
     * [gorselGen x gorselYuk] görüntü btümtisindeki KUTUOrani'ni, FILL_CENTER
     * gösterimdeki görünüm px kutusuna çevirir (overlay çizimi için).
     */
    fun gorselOraniGorunumPxne(
        oran: KutuOrani,
        gorselGen: Int,
        gorselYuk: Int,
        gorunumGen: Int,
        gorunumYuk: Int,
        aynaliMi: Boolean
    ): Kutu {
        val o = ogun(gorselGen, gorselYuk, gorunumGen, gorunumYuk)
            ?: return Kutu(0, 0, gorunumGen.coerceAtLeast(1), gorunumYuk.coerceAtLeast(1))
        fun xV(imgFrac: Double): Double = o.vOffX + imgFrac * o.gorselGen / o.olcek
        fun yV(imgFrac: Double): Double = o.vOffY + imgFrac * o.gorselYuk / o.olcek
        val xSolKutuda = xV(oran.sol)
        val xSagKutuda = xV(oran.sag)
        val yUstKutuda = yV(oran.ust)
        val yAltKutuda = yV(oran.alt)
        val solV: Double
        val sagV: Double
        if (aynaliMi) {
            solV = gorunumGen - xSagKutuda
            sagV = gorunumGen - xSolKutuda
        } else {
            solV = xSolKutuda
            sagV = xSagKutuda
        }
        val sol = solV.roundToInt().coerceIn(0, gorunumGen)
        val sag = sagV.roundToInt().coerceIn(sol, gorunumGen)
        val ust = yUstKutuda.roundToInt().coerceIn(0, gorunumYuk)
        val alt = yAltKutuda.roundToInt().coerceIn(ust, gorunumYuk)
        return Kutu(sol, ust, (sag - sol).coerceAtLeast(1), (alt - ust).coerceAtLeast(1))
    }

    /**
     * Görünüm px kutusunu (overlay'de çizilen/kılavuz çerçeve) görüntü ORANI'na
     * çevirir (kırpma hedefi olarak kullanılır).
     */
    fun gorunumPxiGorselOranina(
        kutu: Kutu,
        gorselGen: Int,
        gorselYuk: Int,
        gorunumGen: Int,
        gorunumYuk: Int,
        aynaliMi: Boolean
    ): KutuOrani {
        val o = ogun(gorselGen, gorselYuk, gorunumGen, gorunumYuk) ?: return KutuOrani(0.0, 0.0, 1.0, 1.0)
        fun imgFracX(xV: Double): Double = (xV - o.vOffX) * o.olcek / o.gorselGen
        fun imgFracY(yV: Double): Double = (yV - o.vOffY) * o.olcek / o.gorselYuk
        val solFark = imgFracX(kutu.sol.toDouble())
        val sagFark = imgFracX(kutu.sag.toDouble())
        val ustFark = imgFracY(kutu.ust.toDouble())
        val altFark = imgFracY(kutu.alt.toDouble())
        val gSon: Double
        val gSonSag: Double
        if (aynaliMi) {
            gSon = 1.0 - sagFark
            gSonSag = 1.0 - solFark
        } else {
            gSon = solFark
            gSonSag = sagFark
        }
        return KutuOrani(gSon, ustFark, gSonSag - gSon, altFark - ustFark).kisnala()
    }

    /** Görüntü ORANI → görüntü px KUTU (kırpma için). */
    fun oraniKutuya(oran: KutuOrani, gorselGen: Int, gorselYuk: Int): Kutu {
        val g = gorselGen.coerceAtLeast(1)
        val h = gorselYuk.coerceAtLeast(1)
        val sol = (oran.sol.coerceIn(0.0, 1.0) * g).roundToInt().coerceIn(0, g - 1)
        val ust = (oran.ust.coerceIn(0.0, 1.0) * h).roundToInt().coerceIn(0, h - 1)
        val gen = (oran.genislik * g).roundToInt().coerceIn(1, g - sol)
        val yuk = (oran.yukseklik * h).roundToInt().coerceIn(1, h - ust)
        return Kutu(sol, ust, gen, yuk)
    }

    /** Görüntü px KUTU → görüntü ORANI (normalizasyon). */
    fun kutuyuOrana(kutu: Kutu, gorselGen: Int, gorselYuk: Int): KutuOrani {
        if (gorselGen <= 0 || gorselYuk <= 0) return KutuOrani(0.0, 0.0, 0.0, 0.0)
        return KutuOrani(
            kutu.sol / gorselGen.toDouble(),
            kutu.ust / gorselYuk.toDouble(),
            kutu.genislik / gorselGen.toDouble(),
            kutu.yukseklik / gorselYuk.toDouble()
        )
    }

    /**
     * Çok küçük/adیمی alanlar için: kutunun ürün kenarlarından en az [minOran] pay
     * almasını garanti eder (merkez korunur). Bengi kurallar sıkardı; bu yüzden güvenli.
     */
    fun minimumBoyutOrani(oran: KutuOrani, minOran: Double = 0.15): KutuOrani {
        val g = oran.genislik.coerceAtLeast(minOran).coerceAtMost(1.0)
        val y = oran.yukseklik.coerceAtLeast(minOran).coerceAtMost(1.0)
        if (g == oran.genislik && y == oran.yukseklik && oran.sol >= 0.0 && oran.ust >= 0.0 &&
            oran.sag <= 1.0 && oran.alt <= 1.0
        ) {
            return oran
        }
        val sol = oran.merkezX - g / 2.0
        val ust = oran.merkezY - y / 2.0
        return KutuOrani(sol, ust, g, y).kisnala()
    }

    /**
     * Statik kılavuz çerçevesi (eski Compose çerçeve matematiğinin aynısı, görüntü px cinsinden):
     * fillMaxHeight(0.75) + aspectRatio(oranGen/oranYuk), 24dp/96dp kenar payları arka planda neden oluyorsa uyumlu.
     */
    fun statikCerceve(
        gorunumGen: Int,
        gorunumYuk: Int,
        ustAlanPx: Int,
        altAlanPx: Int,
        yanalAlanPx: Int,
        oranGen: Int = 3,
        oranYuk: Int = 4,
        dolguKatsayisi: Double = 0.75
    ): Kutu {
        if (gorunumGen <= 0 || gorunumYuk <= 0) return Kutu(0, 0, 1, 1)
        val kullanilabilirYuk = (gorunumYuk - ustAlanPx - altAlanPx).coerceAtLeast(1)
        var h = (kullanilabilirYuk * dolguKatsayisi).roundToInt().coerceAtLeast(1)
        var w = (h * oranGen.toDouble() / oranYuk).roundToInt().coerceAtLeast(1)
        val enCokGen = (gorunumGen - yanalAlanPx * 2).coerceAtLeast(1)
        if (w > enCokGen) {
            w = enCokGen
            h = ((w * oranYuk.toDouble() / oranGen)).roundToInt().coerceAtLeast(1)
        }
        val sol = ((gorunumGen - w) / 2).coerceAtLeast(0)
        val ust = ustAlanPx + ((kullanilabilirYuk - h) / 2).coerceAtLeast(0)
        return Kutu(sol, ust, min(w, gorunumGen - sol), min(h, gorunumYuk - ust))
    }
}
