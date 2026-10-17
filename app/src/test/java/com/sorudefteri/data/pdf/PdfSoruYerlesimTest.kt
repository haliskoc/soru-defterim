package com.sorudefteri.data.pdf

import android.graphics.Paint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PdfSoruYerlesim] birim testleri:
 * sütun akışı, tam genişlik kaçışı, metin satır bölme, homojenleştirme ve
 * kapasite garantisi (hiçbir öge sütun kapasitesini aşamaz → kayma imkansız).
 */
class PdfSoruYerlesimTest {

    private val geometri = PdfSoruYerlesim.Geometri(
        sutunXlar = listOf(35f, 35f + 251.5f + 22f),
        sutunGenislik = 251.5f,
        tamGenislik = 525f,
        icerikUstY = 58f,
        kapasite = 700f
    )

    private fun blok(index: Int, toplamYukseklik: Float): PdfSoruYerlesim.Girdi {
        val sutun = PdfSoruYerlesim.SoruOlcum(
            soruIndex = index,
            genislik = geometri.sutunGenislik,
            ustYukseklik = 0f,
            icerikYukseklik = toplamYukseklik,
            cozumYukseklik = 0f,
            altBosluk = 0f,
            metinSatirlari = emptyList(),
            satirYuksekligi = 10f,
            metinIlkBosluk = 0f,
            metinSonraBosluk = 0f,
            bitmapRatio = 0f
        )
        return PdfSoruYerlesim.Girdi(sutun, sutun)
    }

    private fun ikiGenislikliBlok(
        index: Int,
        sutunToplam: Float,
        tamToplam: Float,
        tamMetinSatirlari: List<String> = emptyList(),
        tamSatirYuksekligi: Float = 10f,
        bitmapRatio: Float = 0f
    ): PdfSoruYerlesim.Girdi {
        val sutun = PdfSoruYerlesim.SoruOlcum(
            soruIndex = index,
            genislik = geometri.sutunGenislik,
            ustYukseklik = 24f,
            icerikYukseklik = sutunToplam - 24f,
            cozumYukseklik = 0f,
            altBosluk = 8f,
            metinSatirlari = emptyList(),
            satirYuksekligi = tamSatirYuksekligi,
            metinIlkBosluk = 8f,
            metinSonraBosluk = 6f,
            bitmapRatio = bitmapRatio
        )
        val tam = PdfSoruYerlesim.SoruOlcum(
            soruIndex = index,
            genislik = geometri.tamGenislik,
            ustYukseklik = 24f,
            icerikYukseklik = tamToplam - 24f,
            cozumYukseklik = 0f,
            altBosluk = 8f,
            metinSatirlari = tamMetinSatirlari,
            satirYuksekligi = tamSatirYuksekligi,
            metinIlkBosluk = 8f,
            metinSonraBosluk = 6f,
            bitmapRatio = if (tamMetinSatirlari.isNotEmpty()) 0f else bitmapRatio
        )
        return PdfSoruYerlesim.Girdi(sutun, tam)
    }

    // ---------- 1) Kapasite garantisi + sıra korunması ----------

    @Test
    fun `planlanan her oge sutun kapasitesini asamaz ve soru sirasi korunur`() {
        val girdiler = (0 until 40).map { blok(it, toplamYukseklik = 100f) }
        val plan = PdfSoruYerlesim.yerlesimPlanla(girdiler, geometri)

        val hepsi = plan.flatMap { it.ogeler }
        assertEquals(40, hepsi.size)

        // Görsel dizide soru sırası artan (numaralandırma/cevap anahtarı mutabakatı).
        val siralar = hepsi.map { it.soruIndex }
        assertEquals("Sıra korunasına kalındı", siralar.sorted(), siralar)

        // Her öge kendi sütununun kapasitesini aşmaz.
        hepsi.filterIsInstance<PdfSoruYerlesim.Oge.TamSoru>()
            .filter { it.sutun >= 0 }
            .forEach { oge ->
                val alt = oge.y + oge.yukseklik
                assertTrue(
                    "Taşma! alt=$alt, sınır=${geometri.icerikUstY + geometri.kapasite}",
                    alt <= geometri.icerikUstY + geometri.kapasite + 0.01f
                )
            }
    }

    @Test
    fun `sütun dolunca ikinci sutuna gecip sonra yeni sayfa acar`() {
        val girdiler = (0 until 16).map { blok(it, toplamYukseklik = 110f) } // 6 soru/sütun (660)
        val plan = PdfSoruYerlesim.yerlesimPlanla(girdiler, geometri)

        assertEquals(2, plan.size)
        val ilkSayfa = plan.first().ogeler.filterIsInstance<PdfSoruYerlesim.Oge.TamSoru>()
        assertEquals(12, ilkSayfa.size)
        assertEquals(6, ilkSayfa.count { it.sutun == 0 })
        assertEquals(6, ilkSayfa.count { it.sutun == 1 })
        assertEquals(4, plan.last().ogeler.size)
        cakismaTaramasi(plan)
    }

    @Test
    fun `sutuna hicbir duzenle sigmayan blok tam genislik sayfaya kacar ve tek basina kalir`() {
        // Sütun ölçümü: 900 (taşar), tam genişlik ölçümü: 650 (sığar) → tam genişlik kaçışı.
        val sutunOlcum = PdfSoruYerlesim.SoruOlcum(
            soruIndex = 0, genislik = geometri.sutunGenislik,
            ustYukseklik = 24f, icerikYukseklik = 900f - 24f, cozumYukseklik = 0f, altBosluk = 8f,
            metinSatirlari = emptyList(), satirYuksekligi = 10f,
            metinIlkBosluk = 8f, metinSonraBosluk = 6f, bitmapRatio = 0.4f
        )
        val tamOlcum = PdfSoruYerlesim.SoruOlcum(
            soruIndex = 0, genislik = geometri.tamGenislik,
            ustYukseklik = 24f, icerikYukseklik = 650f - 24f, cozumYukseklik = 0f, altBosluk = 8f,
            metinSatirlari = emptyList(), satirYuksekligi = 10f,
            metinIlkBosluk = 8f, metinSonraBosluk = 6f, bitmapRatio = 0.4f
        )
        val girdiler = listOf(
            PdfSoruYerlesim.Girdi(sutunOlcum, tamOlcum),
            blok(index = 1, toplamYukseklik = 100f)
        )
        val plan = PdfSoruYerlesim.yerlesimPlanla(girdiler, geometri)

        val tamSayfaIndex = plan.indexOfFirst { sayfa ->
            sayfa.ogeler.any { it is PdfSoruYerlesim.Oge.TamSoru && it.sutun == -1 }
        }
        assertTrue("Tam genişlik sayfa bulunamadı", tamSayfaIndex >= 0)
        val tamSayfa = plan[tamSayfaIndex]
        assertEquals("Tam genişlik sayfa yalnız tek öge taşır", 1, tamSayfa.ogeler.size)
        val tamOge = tamSayfa.ogeler.first() as PdfSoruYerlesim.Oge.TamSoru
        assertEquals(0, tamOge.soruIndex)
        assertEquals(geometri.tamGenislik, tamOge.genislik, 0.01f)

        // Devamındaki soru YENİ sayfada, sütun düzeninde:
        val sonrakiSayfa = plan.getOrNull(tamSayfaIndex + 1)
        assertTrue(sonrakiSayfa != null)
        val sonrakiIlk = sonrakiSayfa!!.ogeler.first() as PdfSoruYerlesim.Oge.TamSoru
        assertEquals(1, sonrakiIlk.soruIndex)
        assertEquals(0, sonrakiIlk.sutun)
    }

    // ---------- 2) Metin satır bölme (kitap tipi devam) ----------

    @Test
    fun `uzun oranli metin sayfa sayfa bolunur ve tum satirlar korunur`() {
        val satirlar = (0 until 150).map { "satir $it" }
        val girdiler = listOf(
            ikiGenislikliBlok(
                index = 0,
                sutunToplam = 3000f,
                tamToplam = 1540f, // sabit 40f + 150 satır × 10f
                tamMetinSatirlari = satirlar,
                tamSatirYuksekligi = 10f
            )
        )
        val plan = PdfSoruYerlesim.yerlesimPlanla(girdiler, geometri)

        val ilkParca = plan[0].ogeler.first() as PdfSoruYerlesim.Oge.TamSoru
        val ilkAralik = ilkParca.satirAraligi
        assertTrue("İlk parça aralığı yok", ilkAralik != null)
        assertTrue("İlk parça tüm satırları almamalı", ilkAralik!!.last < satirlar.size - 1)

        val devamlar = plan.flatMap { it.ogeler }.filterIsInstance<PdfSoruYerlesim.Oge.MetinDevami>()
        assertTrue("Devam parçası yok", devamlar.isNotEmpty())

        // TÜM satırlar plandadır (hiçbir metin kaybolmaz):
        val toplanan = ilkAralik.last - ilkAralik.first + 1 + devamlar.sumOf { it.satirlar.size }
        assertEquals(satirlar.size, toplanan)

        // Devam satırları soru metninin kesintisiz kuyruğudur:
        assertEquals(
            satirlar.drop(ilkAralik.last - ilkAralik.first + 1),
            devamlar.flatMap { it.satirlar }
        )

        // Her sayfa kapasiteyi aşmaz:
        plan.flatMap { it.ogeler }.forEach { oge ->
            assertTrue(
                "Devam taşması: ${oge.yukseklik} > ${geometri.kapasite}",
                oge.yukseklik <= geometri.kapasite + 0.01f
            )
        }
    }

    // ---------- 3) Homojenleştirme (sıkışık/ferah ritim dengesi) ----------

    @Test
    fun `homojenlestirme kalan boslugu sinirli ek boslukla dagitir ve kapasiteyi asmaz`() {
        val girdiler = (0 until 3).map { blok(it, toplamYukseklik = 150f) } // 450 / 700
        val plan = PdfSoruYerlesim.yerlesimPlanla(girdiler, geometri)
        val ogeler = plan.first().ogeler.filterIsInstance<PdfSoruYerlesim.Oge.TamSoru>()

        assertEquals(3, ogeler.size)
        val ekLimit = PdfSoruYerlesim.MAKS_EK_BOSLUK
        val ek1 = ogeler[1].y - geometri.icerikUstY - 150f
        val ek2 = ogeler[2].y - geometri.icerikUstY - 300f
        assertTrue("Ölçek dağıtılmadı", ek1 > 0f)
        assertTrue("Ek boşluk üst sınırı aşıldı", ek1 <= ekLimit + 0.01f)
        assertTrue("Birikmiş ek boşluk üst sınırı aşıldı", ek2 <= 2 * ekLimit + 0.01f)

        val sonAlt = ogeler.last().y + ogeler.last().yukseklik
        assertTrue(
            "Homojenleştirme kapasiteyi aştı: $sonAlt",
            sonAlt <= geometri.icerikUstY + geometri.kapasite + 0.01f
        )
    }

    // ---------- 4) MetinAkisi ----------

    @Test
    fun `satirlaraAyir bos metni bos liste doner ve tek kelimeyi tek satira koyar`() {
        val paint = Paint()
        // Mocked ortamda measureText 0 döner; davranış deterministir (tek satır).
        assertEquals(0, PdfSoruYerlesim.MetinAkisi.satirlaraAyir("", paint, 100f).size)
        assertEquals(0, PdfSoruYerlesim.MetinAkisi.satirlaraAyir("   ", paint, 100f).size)
        assertEquals("kelime", PdfSoruYerlesim.MetinAkisi.satirlaraAyir("kelime", paint, 100f).first())
    }

    @Test
    fun `dilimle aralik disinda kalanlari keser`() {
        val liste = listOf("a", "b", "c", "d", "e")
        assertEquals(listOf("b", "c"), PdfSoruYerlesim.MetinAkisi.dilimle(liste, 1 until 3))
        assertEquals(liste, PdfSoruYerlesim.MetinAkisi.dilimle(liste, null))
        assertTrue(PdfSoruYerlesim.MetinAkisi.dilimle(emptyList(), 0 until 3).isEmpty())
    }

    // ---------- 5) SoruOlcum hesapları ----------

    @Test
    fun `SoruOlcum toplamYukseklik hesabı doğru`() {
        val olcum = PdfSoruYerlesim.SoruOlcum(
            soruIndex = 3, genislik = 251.5f,
            ustYukseklik = 24f, icerikYukseklik = 8f + 40 * 11f + 6f,
            cozumYukseklik = 85f, altBosluk = 18f,
            metinSatirlari = List(40) { "satir" }, satirYuksekligi = 11f,
            metinIlkBosluk = 8f, metinSonraBosluk = 6f, bitmapRatio = 0f
        )
        val beklenen = 24f + (8f + 40 * 11f + 6f) + 85f + 18f
        assertEquals(beklenen, olcum.toplamYukseklik, 0.01f)
        // Bölünemez sabit kısım = toplam − satır bölümü:
        assertEquals(beklenen - 40 * 11f, olcum.metinDisiSabitYukseklik, 0.01f)
    }

    @Test
    fun `bitmap icerikli blok bolunemez ve sabit yukseklik tum bloktur`() {
        val olcum = PdfSoruYerlesim.SoruOlcum(
            soruIndex = 0, genislik = 250f,
            ustYukseklik = 24f, icerikYukseklik = 100f, cozumYukseklik = 85f, altBosluk = 18f,
            metinSatirlari = emptyList(), satirYuksekligi = 11f,
            metinIlkBosluk = 8f, metinSonraBosluk = 6f, bitmapRatio = 0.8f
        )
        assertFalse(olcum.metinBolunebilir)
        assertEquals(24f + 100f + 85f + 18f, olcum.metinDisiSabitYukseklik, 0.01f)
        assertEquals(227f, olcum.toplamYukseklik, 0.01f)
    }

    // ---------- Yardımcı taramalar ----------

    /** Sayfa içinde aynı sütundaki ögeler üst üste binmez. */
    private fun cakismaTaramasi(plan: List<PdfSoruYerlesim.SayfaPlani>) {
        for (sayfa in plan) {
            val tamlar = sayfa.ogeler.filterIsInstance<PdfSoruYerlesim.Oge.TamSoru>()
            val gruplar = tamlar.filter { it.sutun >= 0 }.groupBy { it.sutun }
            for ((_, grup) in gruplar) {
                val sirali = grup.sortedBy { it.y }
                for (i in 1 until sirali.size) {
                    val oncekiAlt = sirali[i - 1].y + sirali[i - 1].yukseklik
                    assertTrue(
                        "Çakışma! önceki alt=$oncekiAlt, sonraki üst=${sirali[i].y}",
                        sirali[i].y + 0.01f >= oncekiAlt
                    )
                }
            }
        }
    }
}
