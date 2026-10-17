package com.sorudefteri.data.pdf

import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.max
import kotlin.math.min

/**
 * A4 soru yerleşim motoru (ölçüm-sonra-yerleştir / measure-then-pack).
 *
 * Eski akışın sorunları:
 * - Yükseklik tahmini (satır sayısı tahmini + 200 pt / 160 pt sabit üst sınır)
 *   ile çizim sarması farklı hesaplar yürütüyordu: plan küçük, çizim büyük
 *   çıktığında soru metni altındaki çözüm alanına taşıyordu (kayma/çakışma).
 * - Sıralama + tam rastgele karıştırma biçimsiz boşluklar ve "bazı dar / bazı
 *   ferah" algısı yaratıyordu.
 *
 * Yeni mimari:
 * - [MetinAkisi] satır sarmayı hem ölçümde hem çizimde TEK algoritmayla yapar →
 *   pl plan yüksekliği == çizilen yükseklik (kayma kaynağı biter).
 * - [yerlesimPlanla] her soruyu ölçülmüş yüksekliğiyle sırayla sütunlara koyar
 *   (Next-Fit paginasyonu; soru sırası hiç bozulmaz): mevcut sütun → 2. sütun →
 *   yeni sayfa → tam genişlik sayfa → metin için sayfadan sayfaya satır bölme.
 * - Tam genişlik sayfası yalnızca TEK soru taşır (kitap tipi geniş soru sayfası).
 * - Sütun kalan boşluğu bloklar arasına üst sınırlı ek boşlukla dağıtılır
 *   (dengeli görünüm); hiçbir zaman kapasiteyi aşmaz.
 */
object PdfSoruYerlesim {

    // =========================================================
    // 1) TEK KAYNAKLI METİN AKIŞI (ölçüm + çizim aynı kod)
    // =========================================================
    object MetinAkisi {

        /**
         * Greedy kelime sarma: kelimeyi bir satırda tutamıyorsak satırı kapat.
         * Tek başına genişlikten uzun kelime kendi satırında kalır (eski çizimle
         * aynı davranış). Bozuk/boş metin boş liste döner.
         */
        fun satirlaraAyir(text: String, paint: Paint, genislik: Float): List<String> {
            val satirlar = mutableListOf<String>()
            var mevcut: String? = null
            for (kelime in text.split(Regex("\\s+"))) {
                if (kelime.isBlank()) continue
                val deneme = if (mevcut == null) kelime else "$mevcut $kelime"
                if (mevcut != null && paint.measureText(deneme) > genislik) {
                    satirlar += mevcut!!
                    mevcut = kelime
                } else {
                    mevcut = deneme
                }
            }
            if (mevcut != null) satirlar += mevcut!!
            return satirlar
        }

        /** Satır sarmalanmış metnin dikey yüksekliği (yalnızca satır boşlukları). */
        fun yukseklik(satirlar: List<String>, satirYuksekligi: Float): Float =
            satirlar.size * satirYuksekligi

        /**
         * Satırları çizer ve yeni Y (bir sonraki satırın taban çizgisi) döner.
         * [startY] ilk satırın baseline'ıdır (eski davranışla aynı).
         */
        fun ciz(
            canvas: Canvas,
            satirlar: List<String>,
            paint: Paint,
            x: Float,
            startY: Float,
            satirYuksekligi: Float
        ): Float {
            var y = startY
            for (satir in satirlar) {
                canvas.drawText(satir, x, y, paint)
                y += satirYuksekligi
            }
            return y
        }

        /** İçin belirtilmiş alt aralıkta satır dilimi (bölünmüş parçalarda). */
        fun dilimle(satirlar: List<String>, aralik: IntRange?): List<String> {
            if (aralik == null) return satirlar
            if (satirlar.isEmpty() || aralik.isEmpty()) return emptyList()
            val from = aralik.first.coerceIn(0, satirlar.size)
            val until = (aralik.last + 1).coerceIn(0, satirlar.size)
            return if (until <= from) emptyList() else satirlar.subList(from, until).toList()
        }
    }

    // =========================================================
    // 2) ÖLÇÜM KAYDI (bir genişlik için soru bloğu ölçümü)
    // =========================================================

    /**
     * Bir sorunun belirli görsel genişlikte ölçülmüş blok bilgisi.
     * Çizim bu kayıttaki değerleri doğrudan kullanır; bir daha hesaplamaz,
     * böylece plan ile çizim arasında asla fark oluşamaz.
     */
    data class SoruOlcum(
        val soruIndex: Int,
        val genislik: Float,
        /** Başlık barı + kaynak + not bölgesi (içerikten önce). */
        val ustYukseklik: Float,
        /** İçerik akış yüksekliği (metin: ilk + satırlar + son boşluk; bitmap: çerçeve + son boşluk). */
        val icerikYukseklik: Float,
        /** Çözüm bölümü (doğru cevap rozeti + AI kutusu + kalem alanı). */
        val cozumYukseklik: Float,
        /** Bloklar arası taban boşluk. */
        val altBosluk: Float,
        /** Soru metni sarmalı satırları (bitmap/placeholder'da boş). */
        val metinSatirlari: List<String>,
        val satirYuksekligi: Float,
        val metinIlkBosluk: Float,
        val metinSonraBosluk: Float,
        /** Bitmap içerik varsa yükseklik/genişlik oranı; yoksa 0f. */
        val bitmapRatio: Float
    ) {
        val toplamYukseklik: Float
            get() = ustYukseklik + icerikYukseklik + cozumYukseklik + altBosluk

        /** Metin içeriği var mı (bölme olanak sağlar)? */
        val metinBolunebilir: Boolean
            get() = metinSatirlari.isNotEmpty() && satirYuksekligi > 0f && bitmapRatio <= 0f

        /** Metin satırları DIŞINDAKİ tüm sabit yükseklik (baş kısmı + dolgular + çözüm + alt boşluk). */
        val metinDisiSabitYukseklik: Float
            get() = if (metinBolunebilir) {
                (toplamYukseklik - metinSatirlari.size * satirYuksekligi).coerceAtLeast(0f)
            } else toplamYukseklik
    }

    /** Planlayıcı girdisi: sorunun sütun genişliği ve tam genişlik ölçümleri. */
    data class Girdi(
        val sutunMetriki: SoruOlcum,
        val tamGenislikMetriki: SoruOlcum
    )

    /** Geometri: A4 sayfası sütun yerleşimi sabitleri. */
    data class Geometri(
        val sutunXlar: List<Float>,
        val sutunGenislik: Float,
        val tamGenislik: Float,
        val icerikUstY: Float,
        val kapasite: Float
    )

    // =========================================================
    // 3) PLAN ELEMANLARI
    // =========================================================

    /** Bir sayfadaki yerleşim ögeleri. Çizim bu koordinatlara sadık kalır. */
    sealed class Oge {
        abstract val soruIndex: Int
        abstract val x: Float
        abstract val y: Float
        abstract val genislik: Float
        abstract val yukseklik: Float

        /** Süre boyunca tam yerleşmiş soru bloğu. */
        data class TamSoru(
            override val soruIndex: Int,
            override val x: Float,
            override val y: Float,
            override val genislik: Float,
            override val yukseklik: Float,
            /** Sütun dizini; tam genişlik sayfada −1. */
            val sutun: Int,
            /** Metin bölünmüş ilk parça ise 0 until k aralığı, yoksa null. */
            val satirAraligi: IntRange? = null
        ) : Oge()

        /** Metin satırları sonraki sayfaya meşru devam parçası (kitap tipi akış). */
        data class MetinDevami(
            override val soruIndex: Int,
            override val x: Float,
            override val y: Float,
            override val genislik: Float,
            override val yukseklik: Float,
            val satirlar: List<String>,
            val satirYuksekligi: Float
        ) : Oge()
    }

    /** Sayfa planı. */
    data class SayfaPlani(val ogeler: List<Oge>)

    /**
     * Devam bandı toplam yüksekliği: 18f başlık çizgisi + 8f metin ilk boşluğu.
     * Çizim ([drawMetinDevami]) aynı ritmi kullanır → plan ile çizim birebir eşit.
     */
    const val DEVAM_BASLIK_YUKSEKLIGI = 26f

    /** Sütun kalan boşluğun bloklar arası dağıtım üst sınırı. */
    const val MAKS_EK_BOSLUK = 14f

    // =========================================================
    // 4) YERLEŞİM PLANLAYICISI
    // =========================================================

    /**
     * Sıralamayı BOZMADAN her soruyu ölçüsüne göre yerleştirir:
     * 1) mevcut sütuna sığar → koy;
     * 2) sığmaz ve henüz 1. sütundayken 2. sütuna sığar → ikinci sütuna geçir;
     * 3) sığmaz → yeni sayfa aç (sütun düzeni);
     * 4) sütun kapasitesinde hiçbir düzenle sığmıyorsa → tam genişlik sayfa;
     * 5) tam genişlikçesine de taşacaksa → metin satırlarını sayfa sayfa böl.
     *
     * İki sütundan sonra 1. sütuna geri dönmek yasaktır (soru sırası görsel
     * olarak korunur; numaralandırma ve cevap anahtarı ile mutabık kalır).
     */
    fun yerlesimPlanla(girdiler: List<Girdi>, geometri: Geometri): List<SayfaPlani> {
        val hamSayfalar = mutableListOf<List<Oge>>()
        var sayfaOgeleri = mutableListOf<Oge>()
        val sutunKullanim = FloatArray(geometri.sutunXlar.size)
        var aktifSutun = 0

        fun sayfaDoldu() {
            if (sayfaOgeleri.isNotEmpty()) {
                hamSayfalar += sayfaOgeleri.toList()
            }
            sayfaOgeleri = mutableListOf()
            sutunKullanim.fill(0f)
            aktifSutun = 0
        }

        fun tamKutuYerles(acim: SoruOlcum, sutun: Int) {
            val x = if (sutun < 0) geometri.sutunXlar.first() else geometri.sutunXlar[sutun.coerceIn(0, geometri.sutunXlar.size - 1)]
            val genislik = if (sutun < 0) geometri.tamGenislik else geometri.sutunGenislik
            val tabanIdx = if (sutun < 0) 0 else sutun
            sayfaOgeleri += Oge.TamSoru(
                soruIndex = acim.soruIndex,
                x = x,
                y = geometri.icerikUstY + (if (sutun < 0) 0f else sutunKullanim[tabanIdx]),
                genislik = genislik,
                yukseklik = acim.toplamYukseklik,
                sutun = sutun
            )
            if (sutun >= 0) sutunKullanim[tabanIdx] += acim.toplamYukseklik
        }

        fun sigeDene(acim: SoruOlcum, sutun: Int): Boolean =
            sutun >= 0 && sutun < sutunKullanim.size &&
                sutunKullanim[sutun] + acim.toplamYukseklik <= geometri.kapasite + 0.01f

        fun metniBol(f: SoruOlcum) {
            val satirlar = f.metinSatirlari
            val lh = f.satirYuksekligi
            val sabit = f.metinDisiSabitYukseklik

            // İLK PARÇA — tam genişlik sayfada; baş+sabit+ilk k satır+çözüm+alt boşluk.
            sayfaDoldu()
            val ilkSatirYeri = ((geometri.kapasite - sabit) / lh).toInt().coerceAtLeast(1)
            val ilkSayisi = min(satirlar.size, ilkSatirYeri)
            sayfaOgeleri += Oge.TamSoru(
                soruIndex = f.soruIndex,
                x = geometri.sutunXlar.first(),
                y = geometri.icerikUstY,
                genislik = geometri.tamGenislik,
                yukseklik = sabit + ilkSayisi * lh,
                sutun = -1,
                satirAraligi = 0 until ilkSayisi
            )

            // DEVAM PARÇALARI — devam başlığı + satırlar; her biri kendi sayfasında.
            var kalan = satirlar.size - ilkSayisi
            var baslangic = ilkSayisi
            val devamKapasite = geometri.kapasite - DEVAM_BASLIK_YUKSEKLIGI
            val devamSatirTercihi = (devamKapasite / lh).toInt().coerceAtLeast(1)
            while (kalan > 0) {
                sayfaDoldu()
                val goster = min(kalan, devamSatirTercihi)
                sayfaOgeleri += Oge.MetinDevami(
                    soruIndex = f.soruIndex,
                    x = geometri.sutunXlar.first(),
                    y = geometri.icerikUstY,
                    genislik = geometri.tamGenislik,
                    yukseklik = DEVAM_BASLIK_YUKSEKLIGI + goster * lh,
                    satirlar = satirlar.subList(baslangic, baslangic + goster).toList(),
                    satirYuksekligi = lh
                )
                kalan -= goster
                baslangic += goster
            }
            sayfaDoldu() // son parça sayfası kapanır; sonraki soru yeni sayfada sütun düzenine döner
        }

        for (girdi in girdiler) {
            val m = girdi.sutunMetriki
            val f = girdi.tamGenislikMetriki

            // 1) MEVCUT SÜTUNA SIĞAR:
            if (sigeDene(m, aktifSutun)) {
                tamKutuYerles(m, aktifSutun)
                continue
            }
            // 2) İKİNCİ SÜTUNA GEÇİŞ (yalnızca 1. sütundan 2.'ye; geri dönüş yasak):
            if (aktifSutun == 0 && sutunKullanim.size > 1 && sigeDene(m, 1)) {
                aktifSutun = 1
                tamKutuYerles(m, 1)
                continue
            }
            // 3) YENİ SAYFA (sütun düzeni devam eder):
            if (m.toplamYukseklik <= geometri.kapasite) {
                sayfaDoldu()
                tamKutuYerles(m, 0)
                continue
            }
            // 4) TAM GENİŞLİK SAYFA (geniş/uzun soruyu rahat gösterir):
            if (f.toplamYukseklik <= geometri.kapasite) {
                sayfaDoldu()
                tamKutuYerles(f, -1)
                sayfaDoldu() // tam genişlik sayfa TEK soru taşır; sonraki soru yeni sayfada
                continue
            }
            // 5) METİN BÖLME:
            if (f.metinBolunebilir) {
                metniBol(f)
                continue
            }
            // 6) İSTİSNA (dev blok + metin dışı): kapasiteye sabitle (taşma kesin önlenir).
            sayfaDoldu()
            sayfaOgeleri += Oge.TamSoru(
                soruIndex = f.soruIndex,
                x = geometri.sutunXlar.first(),
                y = geometri.icerikUstY,
                genislik = geometri.tamGenislik,
                yukseklik = minOf(f.toplamYukseklik, geometri.kapasite),
                sutun = -1
            )
            sayfaDoldu()
        }

        if (sayfaOgeleri.isNotEmpty()) {
            hamSayfalar += sayfaOgeleri.toList()
        }

        return homojenlestir(hamSayfalar, geometri)
    }

    /**
     * Sütun içi dikey boşluğu bloklar arasına bölüştürür: sütunda kalan boşluk −
     * üst sınırla ([MAKS_EK_BOSLUK]) her blok arasına kendi payı kadar eklenir.
     * Böylece sayfa sonu sert görünmez, sayfa içi ritim homojen olur; kapasite
     * asla aşılmaz (ek toplamı ≤ kalan boşluk).
     */
    private fun homojenlestir(hamSayfalar: List<List<Oge>>, geometri: Geometri): List<SayfaPlani> {
        return hamSayfalar.map { ogeler ->
            val sutunGruplari = ogeler.filterIsInstance<Oge.TamSoru>()
                .filter { it.sutun >= 0 }
                .groupBy { it.sutun }
            val yeniOgeler = ogeler.map { oge ->
                if (oge is Oge.TamSoru && oge.sutun >= 0) {
                    val grup = sutunGruplari[oge.sutun] ?: return@map oge
                    if (grup.size < 2) return@map oge
                    val kullanilan = grup.sumOf { it.yukseklik.toDouble() }.toFloat()
                    val kalan = (geometri.kapasite - kullanilan).coerceAtLeast(0f)
                    val ek = min(MAKS_EK_BOSLUK, kalan / (grup.size - 1))
                    if (ek <= 0f) return@map oge
                    val sira = grup.indexOf(oge)
                    val oncesi = (0 until sira).sumOf { ek.toDouble() }.toFloat()
                    oge.copy(y = oge.y + oncesi)
                } else {
                    oge
                }
            }
            SayfaPlani(yeniOgeler)
        }
    }
}
