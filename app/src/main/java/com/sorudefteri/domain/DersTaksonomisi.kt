package com.sorudefteri.domain

import com.sorudefteri.domain.model.Alan
import com.sorudefteri.domain.model.Ders

/**
 * YKS Ders Taksonomisi (TYT ve AYT ders listeleri ve ad eşleştirmeleri)
 */
object DersTaksonomisi {

    /**
     * Tüm alanlar için ortak olan 9 TYT Dersi
     */
    val tytDersleri: List<Ders> = listOf(
        Ders(id = "turkce", ad = "Türkçe", soruSayisi = 40, examType = "TYT", jsonKey = "turkce"),
        Ders(id = "matematik", ad = "Matematik", soruSayisi = 40, examType = "TYT", jsonKey = "matematik"),
        Ders(id = "fizik", ad = "Fizik", soruSayisi = 7, examType = "TYT", jsonKey = "fizik"),
        Ders(id = "kimya", ad = "Kimya", soruSayisi = 7, examType = "TYT", jsonKey = "kimya"),
        Ders(id = "biyoloji", ad = "Biyoloji", soruSayisi = 6, examType = "TYT", jsonKey = "biyoloji"),
        Ders(id = "tarih", ad = "Tarih", soruSayisi = 5, examType = "TYT", jsonKey = "tarih"),
        Ders(id = "cografya", ad = "Coğrafya", soruSayisi = 5, examType = "TYT", jsonKey = "cografya"),
        Ders(id = "felsefe", ad = "Felsefe", soruSayisi = 5, examType = "TYT", jsonKey = "felsefe"),
        Ders(id = "din_kulturu", ad = "Din Kültürü ve Ahlak Bilgisi", soruSayisi = 5, examType = "TYT", jsonKey = "din_kulturu")
    )

    val TYT_DERSLER: List<Ders> get() = tytDersleri

    /**
     * Sayısal Alanı AYT Dersleri
     */
    val aytSayisalDersleri: List<Ders> = listOf(
        Ders(id = "ayt_matematik", ad = "Matematik", soruSayisi = 40, examType = "AYT", jsonKey = "matematik"),
        Ders(id = "ayt_fizik", ad = "Fizik", soruSayisi = 14, examType = "AYT", jsonKey = "fizik"),
        Ders(id = "ayt_kimya", ad = "Kimya", soruSayisi = 13, examType = "AYT", jsonKey = "kimya"),
        Ders(id = "ayt_biyoloji", ad = "Biyoloji", soruSayisi = 13, examType = "AYT", jsonKey = "biyoloji")
    )

    /**
     * Eşit Ağırlık Alanı AYT Dersleri
     */
    val aytEsitAgirlikDersleri: List<Ders> = listOf(
        Ders(id = "ayt_matematik", ad = "Matematik", soruSayisi = 40, examType = "AYT", jsonKey = "matematik"),
        Ders(id = "ayt_edebiyat", ad = "Türk Dili ve Edebiyatı", soruSayisi = 24, examType = "AYT", jsonKey = "edebiyat"),
        Ders(id = "ayt_tarih1", ad = "Tarih-1", soruSayisi = 10, examType = "AYT", jsonKey = "tarih1"),
        Ders(id = "ayt_cografya1", ad = "Coğrafya-1", soruSayisi = 6, examType = "AYT", jsonKey = "cografya1")
    )

    /**
     * Sözel Alanı AYT Dersleri
     */
    val aytSozelDersleri: List<Ders> = listOf(
        Ders(id = "ayt_edebiyat", ad = "Türk Dili ve Edebiyatı", soruSayisi = 24, examType = "AYT", jsonKey = "edebiyat"),
        Ders(id = "ayt_tarih1", ad = "Tarih-1", soruSayisi = 10, examType = "AYT", jsonKey = "tarih1"),
        Ders(id = "ayt_cografya1", ad = "Coğrafya-1", soruSayisi = 6, examType = "AYT", jsonKey = "cografya1"),
        Ders(id = "ayt_tarih2", ad = "Tarih-2", soruSayisi = 11, examType = "AYT", jsonKey = "tarih2"),
        Ders(id = "ayt_cografya2", ad = "Coğrafya-2", soruSayisi = 11, examType = "AYT", jsonKey = "cografya2"),
        Ders(id = "ayt_felsefe_grubu", ad = "Felsefe Grubu", soruSayisi = 12, examType = "AYT", jsonKey = "felsefe_grubu"),
        Ders(id = "ayt_din", ad = "Din Kültürü ve Ahlak Bilgisi", soruSayisi = 6, examType = "AYT", jsonKey = "din_kulturu")
    )

    /**
     * Dil (YDT) Alanı AYT/YDT Dersleri
     */
    val aytDilDersleri: List<Ders> = listOf(
        Ders(id = "ydt_ingilizce", ad = "YDT İngilizce", soruSayisi = 80, examType = "AYT", jsonKey = "ydt_ingilizce"),
        Ders(id = "ydt_almanca", ad = "YDT Almanca", soruSayisi = 80, examType = "AYT", jsonKey = "ydt_almanca"),
        Ders(id = "ydt_fransizca", ad = "YDT Fransızca", soruSayisi = 80, examType = "AYT", jsonKey = "ydt_fransizca")
    )

    /**
     * Tüm bilinen derslerin ID -> Ad sözlüğü
     */
    private val tumDersAdlari: Map<String, String> = mapOf(
        "turkce" to "Türkçe",
        "matematik" to "Matematik",
        "fizik" to "Fizik",
        "kimya" to "Kimya",
        "biyoloji" to "Biyoloji",
        "tarih" to "Tarih",
        "cografya" to "Coğrafya",
        "felsefe" to "Felsefe",
        "din_kulturu" to "Din Kültürü ve Ahlak Bilgisi",
        "din" to "Din Kültürü ve Ahlak Bilgisi",
        "ayt_matematik" to "Matematik",
        "ayt_fizik" to "Fizik",
        "ayt_kimya" to "Kimya",
        "ayt_biyoloji" to "Biyoloji",
        "ayt_edebiyat" to "Türk Dili ve Edebiyatı",
        "edebiyat" to "Türk Dili ve Edebiyatı",
        "ayt_tarih1" to "Tarih-1",
        "tarih_1" to "Tarih-1",
        "ayt_cografya1" to "Coğrafya-1",
        "cografya_1" to "Coğrafya-1",
        "ayt_tarih2" to "Tarih-2",
        "tarih_2" to "Tarih-2",
        "ayt_cografya2" to "Coğrafya-2",
        "cografya_2" to "Coğrafya-2",
        "ayt_felsefe_grubu" to "Felsefe Grubu",
        "felsefe_grubu" to "Felsefe Grubu",
        "ayt_din" to "Din Kültürü ve Ahlak Bilgisi",
        "ydt_ingilizce" to "YDT İngilizce",
        "ydt_almanca" to "YDT Almanca",
        "ydt_fransizca" to "YDT Fransızca"
    )

    /**
     * Seçilen alana göre AYT derslerini döner
     */
    fun getAytDersleri(alan: Alan): List<Ders> {
        return when (alan) {
            Alan.SAYISAL -> aytSayisalDersleri
            Alan.ESIT_AGIRLIK -> aytEsitAgirlikDersleri
            Alan.SOZEL -> aytSozelDersleri
            Alan.DIL -> aytDilDersleri
        }
    }

    fun getAytDersler(alan: Alan): List<Ders> = getAytDersleri(alan)

    /**
     * Sınav türü ve alana göre ders listesini döner
     */
    fun getDersler(examType: String, alan: Alan = Alan.SAYISAL): List<Ders> {
        return if (examType.equals("TYT", ignoreCase = true)) {
            tytDersleri
        } else {
            getAytDersleri(alan)
        }
    }

    /**
     * Ders kimliğinden ve sınav türünden kullanıcı dostu ders adını döner
     */
    fun getDersAdi(examType: String = "TYT", subjectId: String): String {
        val normalizedSubjectId = subjectId.lowercase().trim()
        val allDersler = tytDersleri + aytSayisalDersleri + aytEsitAgirlikDersleri + aytSozelDersleri + aytDilDersleri
        return allDersler.find { it.id.equals(normalizedSubjectId, ignoreCase = true) }?.ad
            ?: tumDersAdlari[normalizedSubjectId]
            ?: tumDersAdlari[normalizedSubjectId.removePrefix("ayt_").removePrefix("tyt_")]
            ?: subjectId.replace("ayt_", "AYT ")
                .replace("tyt_", "TYT ")
                .replace('_', ' ')
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    fun getDersAdi(subjectId: String): String = getDersAdi("TYT", subjectId)

    fun getDersName(subjectId: String): String = getDersAdi("TYT", subjectId)
}
