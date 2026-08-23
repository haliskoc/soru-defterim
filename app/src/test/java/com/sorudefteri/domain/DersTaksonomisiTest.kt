package com.sorudefteri.domain

import com.sorudefteri.domain.model.Alan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * YKS Taksonomisi, TYT/AYT dersleri ve alan eşleştirmelerinin birim testi.
 */
class DersTaksonomisiTest {

    @Test
    fun tytDersleri_eksiksizVeGecerli() {
        val tytDersler = DersTaksonomisi.TYT_DERSLER
        assertTrue("TYT dersleri boş olamaz", tytDersler.isNotEmpty())
        assertEquals(9, tytDersler.size)

        val dersIdleri = tytDersler.map { it.id }
        assertTrue(dersIdleri.contains("turkce"))
        assertTrue(dersIdleri.contains("matematik"))
        assertTrue(dersIdleri.contains("fizik"))
        assertTrue(dersIdleri.contains("kimya"))
        assertTrue(dersIdleri.contains("biyoloji"))
        assertTrue(dersIdleri.contains("tarih"))
        assertTrue(dersIdleri.contains("cografya"))
        assertTrue(dersIdleri.contains("felsefe"))
        assertTrue(dersIdleri.contains("din_kulturu"))
    }

    @Test
    fun aytSayisalDersleri_dogruFiltreleniyor() {
        val aytSayisal = DersTaksonomisi.getAytDersler(Alan.SAYISAL)
        val idler = aytSayisal.map { it.id }

        assertEquals(4, aytSayisal.size)
        assertTrue(idler.contains("ayt_matematik"))
        assertTrue(idler.contains("ayt_fizik"))
        assertTrue(idler.contains("ayt_kimya"))
        assertTrue(idler.contains("ayt_biyoloji"))
        assertFalse("Sayısal alanda Edebiyat olmamalı", idler.contains("ayt_edebiyat"))
    }

    @Test
    fun aytEsitAgirlikDersleri_dogruFiltreleniyor() {
        val aytEa = DersTaksonomisi.getAytDersler(Alan.ESIT_AGIRLIK)
        val idler = aytEa.map { it.id }

        assertEquals(4, aytEa.size)
        assertTrue(idler.contains("ayt_matematik"))
        assertTrue(idler.contains("ayt_edebiyat"))
        assertTrue(idler.contains("ayt_tarih1"))
        assertTrue(idler.contains("ayt_cografya1"))
        assertFalse("Eşit Ağırlıkta Fizik olmamalı", idler.contains("ayt_fizik"))
    }

    @Test
    fun aytSozelDersleri_dogruFiltreleniyor() {
        val aytSozel = DersTaksonomisi.getAytDersler(Alan.SOZEL)
        val idler = aytSozel.map { it.id }

        assertEquals(7, aytSozel.size)
        assertTrue(idler.contains("ayt_edebiyat"))
        assertTrue(idler.contains("ayt_tarih1"))
        assertTrue(idler.contains("ayt_cografya1"))
        assertTrue(idler.contains("ayt_tarih2"))
        assertTrue(idler.contains("ayt_cografya2"))
        assertTrue(idler.contains("ayt_felsefe_grubu"))
        assertTrue(idler.contains("ayt_din"))
        assertFalse("Sözelde Matematik olmamalı", idler.contains("ayt_matematik"))
    }

    @Test
    fun aytDilDersleri_dogruFiltreleniyor() {
        val aytDil = DersTaksonomisi.getAytDersler(Alan.DIL)
        val idler = aytDil.map { it.id }

        assertEquals(3, aytDil.size)
        assertTrue(idler.contains("ydt_ingilizce"))
        assertTrue(idler.contains("ydt_almanca"))
        assertTrue(idler.contains("ydt_fransizca"))
    }

    @Test
    fun getDersAdi_dogruEslestiriyor() {
        assertEquals("Matematik", DersTaksonomisi.getDersAdi("matematik"))
        assertEquals("Fizik", DersTaksonomisi.getDersAdi("ayt_fizik"))
        assertEquals("Türk Dili ve Edebiyatı", DersTaksonomisi.getDersAdi("ayt_edebiyat"))
        assertEquals("Din Kültürü ve Ahlak Bilgisi", DersTaksonomisi.getDersAdi("din_kulturu"))
    }

    @Test
    fun getDersler_alanVeSinavTipineGoreCalisiyor() {
        val tytDersler = DersTaksonomisi.getDersler("TYT", Alan.SAYISAL)
        assertEquals(9, tytDersler.size)

        val aytSayisal = DersTaksonomisi.getDersler("AYT", Alan.SAYISAL)
        assertEquals(4, aytSayisal.size)

        val aytSozel = DersTaksonomisi.getDersler("AYT", Alan.SOZEL)
        assertEquals(7, aytSozel.size)
    }
}
