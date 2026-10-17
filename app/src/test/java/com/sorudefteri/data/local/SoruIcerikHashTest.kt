package com.sorudefteri.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Akıllı içe aktarma (dedup/merge) için içerik parmak izi testleri.
 *
 * Amaç: hash'in DETERMİNİSTİK olmasını garantiye al. Aynı soru farklı
 * yedeklerde / farklı beyaz boşluk ve büyük-küçük harf varyantlarında
 * aynı parmak izini üretmeli; içerik değişirse değişmelidir.
 */
class SoruIcerikHashTest {

    private fun hesapla(
        examType: String = "TYT",
        subjectId: String = "matematik",
        topic: String = "Türev",
        questionText: String? = "2x + 3 = 11 denkleminin çözümü kaçtır?",
        ocrMetni: String? = null,
        kaynakYayin: String? = "Karekök",
        kaynakDeneme: String? = null,
        kaynakYil: Int? = null,
        kaynakSoruNo: Int? = null
    ): String = SoruIcerikHash.hesapla(
        examType = examType,
        subjectId = subjectId,
        topic = topic,
        questionText = questionText,
        ocrMetni = ocrMetni,
        kaynakYayin = kaynakYayin,
        kaynakDeneme = kaynakDeneme,
        kaynakYil = kaynakYil,
        kaynakSoruNo = kaynakSoruNo
    )

    @Test
    fun ayniIcerik_ayniHashUretir() {
        val birinci = hesapla()
        val ikinci = hesapla()
        assertEquals(birinci, ikinci)
    }

    @Test
    fun buyukKucukHarfVeBoslukFarklari_hashiDegistirmez() {
        val standart = hesapla(questionText = "2x + 3 = 11 denkleminin çözümü kaçtır?")
        val bozukFormat = hesapla(questionText = "2X  +  3 = 11  DENKLEMİNİN  ÇÖZÜMÜ  KAÇTIR?   ")
        assertEquals(standart, bozukFormat)
    }

    @Test
    fun basVeSondaTire_hashiDegistirmez() {
        val standart = hesapla(questionText = "denklemin çözümü nedir")
        val tireli = hesapla(questionText = "--- denklemin çözümü nedir ---")
        assertEquals(standart, tireli)
    }

    @Test
    fun turkceKucukHarf_Ii_ve_Iyi_farkliHaleGetirir() {
        // "İ" (İngilizce locale'de i olur; Türkçe locale'de i) ve "I" (Türkçe'de ı)
        val ikisi = SoruIcerikHash.normalize("İSTANBUL IĞDIR")
        assertEquals("istanbul ığdır", ikisi)
    }

    @Test
    fun metinDegisince_hashDegisir() {
        val birinci = hesapla(questionText = "2x + 3 = 11 denkleminin çözümü kaçtır?")
        val ikinci = hesapla(questionText = "2x + 5 = 11 denkleminin çözümü kaçtır?")
        assertNotEquals(birinci, ikinci)
    }

    @Test
    fun konuyaGore_hashDegisir() {
        val birinci = hesapla(topic = "Türev")
        val ikinci = hesapla(topic = "İntegral")
        assertNotEquals(birinci, ikinci)
    }

    @Test
    fun kaynakBilgisiEklenince_hashDegisir() {
        val kaynakli = hesapla(kaynakSoruNo = 42)
        val kaynaksiz = hesapla(kaynakSoruNo = null)
        assertNotEquals(kaynakli, kaynaksiz)
    }

    @Test
    fun questionText_yoksa_ocrMetniKullanilir() {
        val ocrYolu = hesapla(
            questionText = null,
            ocrMetni = "2x + 3 = 11 denkleminin çözümü kaçtır?"
        )
        val metinYolu = hesapla(
            questionText = "2x + 3 = 11 denkleminin çözümü kaçtır?",
            ocrMetni = null
        )
        // OCR metni questionText ile aynı içeriği taşıyorsa aynı parmak izini üretir
        assertEquals(metinYolu, ocrYolu)
    }

    @Test
    fun tamamenBosIcerik_bosHashDondurur_tanimlanamaz() {
        val hash = hesapla(
            questionText = null,
            ocrMetni = "   ",
            kaynakYayin = null,
            kaynakDeneme = null,
            kaynakYil = null,
            kaynakSoruNo = null
        )
        assertEquals("", hash)
    }

    @Test
    fun hashSHA256FormatindaVe64Karakter() {
        val hash = hesapla()
        assertEquals(64, hash.length)
        assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun domainModeldenHesaplama_parametreliSurumleAyni() {
        val domain = com.sorudefteri.domain.model.Soru(
            imagePath = "gorsel.jpg",
            examType = "TYT",
            subjectId = "matematik",
            topic = "Türev",
            questionText = "2x + 3 = 11 denkleminin çözümü kaçtır?",
            kaynakYayin = "Karekök"
        )
        val domainIle = SoruIcerikHash.hesapla(domain)
        val parametreli = hesapla()
        assertEquals(parametreli, domainIle)
    }

    @Test
    fun gorselVeTimestampAlanlariHashiEtkilemez() {
        val domain = com.sorudefteri.domain.model.Soru(
            imagePath = "gorsel.jpg",
            examType = "TYT",
            subjectId = "matematik",
            topic = "Türev",
            questionText = "2x + 3 = 11 denkleminin çözümü kaçtır?",
            isFavorite = true,
            reviewStage = 3
        )
        val farkliDurum = domain.copy(
            imagePath = "baska_gorsel.png",
            isFavorite = false,
            reviewStage = 0
        )
        // Görsel yolu ve kullanıcı durumu parmak izinde yer almaz
        assertEquals(SoruIcerikHash.hesapla(domain), SoruIcerikHash.hesapla(farkliDurum))
        assertFalse(SoruIcerikHash.hesapla(domain).isBlank())
    }
}
