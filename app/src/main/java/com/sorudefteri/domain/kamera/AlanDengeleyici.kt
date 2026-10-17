package com.sorudefteri.domain.kamera

import kotlin.math.max
import kotlin.math.min

/**
 * Tarama alanı YUMUŞATICI + KİLİTLEME durumu makinesi (SAF).
 *
 * Soru algılayıcı her analiz karesinde küçük sıçramalar yapabilir. Bu sınıf:
 *  - Aday bölgeyi EMA (üstel yumuşatma) ile yumuşatır → çerçeve titremez ve
 *    kamera hareket ettikçe alan soruyla birlikte akar,
 *  - Alan ~[kilitSureMs] boyunca yeterince benzer kalırsa KILITLI olur,
 *  - Aday kaybolursa [kayipSureMs] tolerans verir; sonra ARANIYOR'a düşer.
 *
 * Saf Kotlin'dir; testlerde gerçekçi sahte zaman damgalarıyla kullanılır.
 */
class AlanDengeleyici(
    /** EMA katsayısı (0 = eski değer hiç değişmez, 1 = anlık değer). */
    private val yumusatma: Double = 0.35,
    /** Milisaniye: bu süre boyunca bölge yeterince benzer kalırsa KILITLI. */
    private val kilitSureMs: Long = 900L,
    /** Milisaniye: aday göründüğü son zamandan sonra bu süre aranırsa duraklar. */
    private val kayipSureMs: Long = 1200L,
    /** IoU eşiği: iki kare aynı bölge sayılır. */
    private val benzerlikEsik: Double = 0.72
) {

    data class Durum(
        val alan: KutuOrani?,
        val durum: TaramaDurumu
    )

    private var yumusak: KutuOrani? = null
    private var guncelDurum: TaramaDurumu = TaramaDurumu.ARANIYOR
    private var sonGecerliZaman: Long = Long.MIN_VALUE
    private var stabilBaslangic: Long = Long.MIN_VALUE

    /** Analiz karesi başında çağrılır; aday yoksa null geçilir. */
    fun isle(aday: KutuOrani?, zamanMs: Long): Durum {
        if (stabilBaslangic == Long.MIN_VALUE) stabilBaslangic = zamanMs

        if (aday != null) {
            sonGecerliZaman = zamanMs
            val benzer = yumusak != null && iou(yumusak!!, aday) >= benzerlikEsik
            if (!benzer) stabilBaslangic = zamanMs
            yumusak = if (yumusak == null) aday else yumusakla(yumusak!!, aday)
            guncelDurum = if (zamanMs - stabilBaslangic >= kilitSureMs) {
                TaramaDurumu.KILITLI
            } else {
                TaramaDurumu.BULUNDU
            }
        } else {
            val kayip = sonGecerliZaman != Long.MIN_VALUE &&
                (zamanMs - sonGecerliZaman) >= kayipSureMs
            if (kayip || yumusak == null) {
                yumusak = null
                guncelDurum = TaramaDurumu.ARANIYOR
                stabilBaslangic = zamanMs
            }
            // Kısa kayıpta son kutu korunur (histerezis)
        }
        return Durum(yumusak?.kisnala(), guncelDurum)
    }

    /** Kullanıcı "Yeniden tara" derse ya da mod/lens değişirse. */
    fun sifirla() {
        yumusak = null
        guncelDurum = TaramaDurumu.ARANIYOR
        sonGecerliZaman = Long.MIN_VALUE
        stabilBaslangic = Long.MIN_VALUE
    }

    private fun yumusakla(mevcut: KutuOrani, aday: KutuOrani): KutuOrani {
        val a = yumusatma
        fun mc(eski: Double, yeni: Double) = eski + a * (yeni - eski)
        return KutuOrani(
            sol = mc(mevcut.sol, aday.sol),
            ust = mc(mevcut.ust, aday.ust),
            genislik = mc(mevcut.genislik, aday.genislik),
            yukseklik = mc(mevcut.yukseklik, aday.yukseklik)
        )
    }

    private fun iou(a: KutuOrani, b: KutuOrani): Double {
        val ortusSol = max(a.sol, b.sol)
        val ortusUst = max(a.ust, b.ust)
        val ortusSag = min(a.sag, b.sag)
        val ortusAlt = min(a.alt, b.alt)
        val ortusGen = (ortusSag - ortusSol).coerceAtLeast(0.0)
        val ortusYuk = (ortusAlt - ortusUst).coerceAtLeast(0.0)
        val ortusAlan = ortusGen * ortusYuk
        val toplamAlan = a.genislik * a.yukseklik + b.genislik * b.yukseklik - ortusAlan
        if (toplamAlan <= 0.0) return 0.0
        return ortusAlan / toplamAlan
    }
}
