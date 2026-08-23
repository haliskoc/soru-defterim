package com.sorudefteri.domain

import com.sorudefteri.domain.model.Alan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * YKS Taksonomisi, TYT/AYT dersleri, alan eşleştirmeleri ve konu filtrelerinin birim testi.
 */
class DersTaksonomisiTest {

    @Test
    fun tytDersleri_eksiksizVeGecerli() {
        val tytDersler = DersTaksonomisi.TYT_DERSLER
        assertTrue("TYT dersleri boş olamaz", tytDersler.isNotEmpty())
        assertEquals(10, tytDersler.size)

        val dersIdleri = tytDersler.map { it.id }
        assertTrue(dersIdleri.contains("tyt_turkce"))
        assertTrue(dersIdleri.contains("tyt_matematik"))
        assertTrue(dersIdleri.contains("tyt_geometri"))
        assertTrue(dersIdleri.contains("tyt_fizik"))
        assertTrue(dersIdleri.contains("tyt_kimya"))
        assertTrue(dersIdleri.contains("tyt_biyoloji"))
        assertTrue(dersIdleri.contains("tyt_tarih"))
        assertTrue(dersIdleri.contains("tyt_cografya"))
        assertTrue(dersIdleri.contains("tyt_felsefe"))
        assertTrue(dersIdleri.contains("tyt_din"))
    }

    @Test
    fun aytSayisalDersleri_dogruFiltreleniyor() {
        val aytSayisal = DersTaksonomisi.getAytDersler(Alan.SAYISAL)
        val idler = aytSayisal.map { it.id }

        assertEquals(5, aytSayisal.size)
        assertTrue(idler.contains("ayt_matematik"))
        assertTrue(idler.contains("ayt_geometri"))
        assertTrue(idler.contains("ayt_fizik"))
        assertTrue(idler.contains("ayt_kimya"))
        assertTrue(idler.contains("ayt_biyoloji"))
        assertFalse("Sayısal alanda Edebiyat olmamalı", idler.contains("ayt_edebiyat"))
    }

    @Test
    fun aytEsitAgirlikDersleri_dogruFiltreleniyor() {
        val aytEa = DersTaksonomisi.getAytDersler(Alan.ESIT_AGIRLIK)
        val idler = aytEa.map { it.id }

        assertEquals(5, aytEa.size)
        assertTrue(idler.contains("ayt_matematik"))
        assertTrue(idler.contains("ayt_geometri"))
        assertTrue(idler.contains("ayt_edebiyat"))
        assertTrue(idler.contains("ayt_tarih_1"))
        assertTrue(idler.contains("ayt_cografya_1"))
        assertFalse("Eşit Ağırlıkta Fizik olmamalı", idler.contains("ayt_fizik"))
    }

    @Test
    fun aytSozelDersleri_dogruFiltreleniyor() {
        val aytSozel = DersTaksonomisi.getAytDersler(Alan.SOZEL)
        val idler = aytSozel.map { it.id }

        assertEquals(7, aytSozel.size)
        assertTrue(idler.contains("ayt_edebiyat"))
        assertTrue(idler.contains("ayt_tarih_1"))
        assertTrue(idler.contains("ayt_cografya_1"))
        assertTrue(idler.contains("ayt_tarih_2"))
        assertTrue(idler.contains("ayt_cografya_2"))
        assertTrue(idler.contains("ayt_felsefe_grubu"))
        assertTrue(idler.contains("ayt_din"))
        assertFalse("Sözelde Matematik olmamalı", idler.contains("ayt_matematik"))
    }

    @Test
    fun aytDilDersleri_dogruFiltreleniyor() {
        val aytDil = DersTaksonomisi.getAytDersler(Alan.DIL)
        val idler = aytDil.map { it.id }

        assertEquals(5, aytDil.size)
        assertTrue(idler.contains("ydt_ingilizce"))
        assertTrue(idler.contains("ydt_almanca"))
        assertTrue(idler.contains("ydt_fransizca"))
    }

    @Test
    fun dersKonulari_bosDegilVeGecerli() {
        val matKonular = DersTaksonomisi.getKonular("tyt_matematik")
        assertTrue("TYT Matematik konuları boş olamaz", matKonular.isNotEmpty())
        assertTrue("TYT Matematik 'Temel Kavramlar' içermeli", matKonular.contains("Temel Kavramlar"))
        assertTrue("TYT Matematik 'Problemler' içermeli", matKonular.contains("Problemler"))

        val geoKonular = DersTaksonomisi.getKonular("ayt_geometri")
        assertTrue(geoKonular.contains("Analitik Geometri"))
        assertTrue(geoKonular.contains("Çember ve Daire"))

        val bilinmeyenKonular = DersTaksonomisi.getKonular("gecersiz_ders_id")
        assertEquals(listOf("Genel"), bilinmeyenKonular)
    }

    @Test
    fun getDersAdi_dogruEslestiriyor() {
        assertEquals("TYT Matematik", DersTaksonomisi.getDersAdi("tyt_matematik"))
        assertEquals("AYT Fizik", DersTaksonomisi.getDersAdi("ayt_fizik"))
        assertEquals("AYT Edebiyat", DersTaksonomisi.getDersAdi("ayt_edebiyat"))
        assertEquals("Bilinmeyen Ders", DersTaksonomisi.getDersAdi("gecersiz_ders"))
    }
}
