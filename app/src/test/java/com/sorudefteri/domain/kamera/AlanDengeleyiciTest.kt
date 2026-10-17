package com.sorudefteri.domain.kamera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AlanDengeleyici (yumuşatma + kilit/kayıp) durum makinesi testleri.
 */
class AlanDengeleyiciTest {

    @Test
    fun `aday gelmedigi surece ARANIYOR`() {
        val dengeleyici = AlanDengeleyici()
        val durum = dengeleyici.isle(null, 0L)
        assertEquals(TaramaDurumu.ARANIYOR, durum.durum)
        assertNull(durum.alan)
    }

    @Test
    fun `yeni aday BULUNDU sonramis KILITLI olur`() {
        val dengeleyici = AlanDengeleyici(kilitSureMs = 900L)
        val aday = KutuOrani(0.2, 0.2, 0.3, 0.2)

        val ilk = dengeleyici.isle(aday, 0L)
        assertEquals(TaramaDurumu.BULUNDU, ilk.durum)

        // Aynı bölge (IoU=1): kararlılık
        var zaman = 0L
        var son = ilk
        for (i in 1..10) {
            zaman += 100L
            son = dengeleyici.isle(aday, zaman)
        }
        assertEquals(TaramaDurumu.KILITLI, son.durum)
    }

    @Test
    fun `EMA katsayisi ilk kademede geg terrası`() {
        val dengeleyici = AlanDengeleyici(kilitSureMs = Long.MAX_VALUE)
        val aday1 = KutuOrani(0.0, 0.0, 1.0, 1.0)
        var zaman = 0L
        val il = dengeleyici.isle(aday1, zaman)
        assertEquals(0.0, il.alan!!.sol, 1e-9)

        zaman += 100L
        val aday2 = KutuOrani(0.2, 0.2, 0.4, 0.4)
        val ikinci = dengeleyici.isle(aday2, zaman)
        // 0 + 0.35*(0.2-0) = 0.07
        assertEquals(0.07, ikinci.alan!!.sol, 1e-9)
        assertEquals(0.07, ikinci.alan.ust, 1e-9)
    }

    @Test
    fun `kayıp gecmistesi 1200ms sonra ARANIYOR`() {
        val dengeleyici = AlanDengeleyici(kilitSureMs = Long.MAX_VALUE, kayipSureMs = 1200L)
        val aday = KutuOrani(0.2, 0.2, 0.3, 0.2)

        var zaman = 0L
        dengeleyici.isle(aday, zaman)
        for (i in 1..5) {
            zaman += 100L
            dengeleyici.isle(aday, zaman)
        }
        val kayipEtmiyor = dengeleyici.isle(null, zaman + 1100L)
        assertTrue(kayipEtmiyor.durum != TaramaDurumu.ARANIYOR)  // hysteresis
        val kayipZamani = dengeleyici.isle(null, zaman + 1300L)
        assertEquals(TaramaDurumu.ARANIYOR, kayipZamani.durum)
        assertNull(kayipZamani.alan)
    }

    @Test
    fun `yeni tarayici reset sifirla ARANIYOR`() {
        val dengeleyici = AlanDengeleyici(kilitSureMs = Long.MAX_VALUE)
        val aday = KutuOrani(0.2, 0.2, 0.3, 0.2)
        dengeleyici.isle(aday, 0L)
        dengeleyici.sifirla()
        val durum = dengeleyici.isle(null, 100L)
        assertEquals(TaramaDurumu.ARANIYOR, durum.durum)
        assertNull(durum.alan)
    }
}
