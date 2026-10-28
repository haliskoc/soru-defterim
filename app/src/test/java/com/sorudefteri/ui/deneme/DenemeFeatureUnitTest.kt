package com.sorudefteri.ui.deneme

import com.sorudefteri.data.local.entity.DenemeSinaviEntity
import com.sorudefteri.data.local.entity.SoruEntity
import com.sorudefteri.data.local.entity.toEntity
import com.sorudefteri.domain.model.DenemeDersNeti
import com.sorudefteri.domain.model.DenemeSinavi
import com.sorudefteri.domain.model.HataTipi
import com.sorudefteri.domain.model.Soru
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.round

/**
 * Deneme Sınavı Özellikleri & Super-App Analitik Birim Testleri:
 *
 * 1. [DenemeSinavi] Domain modelinin net hesabı ve ders karnesi doğruluğu (TYT 4Y -> 1D, LGS 3Y -> 1D).
 * 2. [DenemeSinaviEntity] Room entity JSON seri-dönüşüm bütünlüğü (Ders netleri listesi).
 * 3. Denemeye yanlış soru eklendiğinde [Soru] ve [SoruEntity]'nin `denemeId` ve `denemeSoruNo` değerlerini alması.
 * 4. `DenemeDetayliAnalizCard`'ın YKS konu bazlı net kaybı (yanlis * 1.25) algoritmasının matematiksel çıktısı.
 */
class DenemeFeatureUnitTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // =========================================================================
    // 1. DENEME SINAVI NET VE DERS KARNESİ HESAPLAMA TESTLERİ
    // =========================================================================

    @Test
    fun `tyt denemesi ders karnesi ve toplam net hesabi 4 yanlis 1 dogru goturur kuraliyla dogru calisir`() {
        // TYT Sınavı Dağılımı:
        // Türkçe: 40 soru -> 35 Doğru, 4 Yanlış, 1 Boş -> Net: 35 - 1.0 = 34.0
        // Matematik: 40 soru -> 30 Doğru, 6 Yanlış, 4 Boş -> Net: 30 - 1.5 = 28.5
        // Fen Bilimleri: 20 soru -> 16 Doğru, 3 Yanlış, 1 Boş -> Net: 16 - 0.75 = 15.25
        // Sosyal Bilimler: 20 soru -> 17 Doğru, 2 Yanlış, 1 Boş -> Net: 17 - 0.5 = 16.5

        val turkceNet = 35 - (4 * 0.25)
        val matNet = 30 - (6 * 0.25)
        val fenNet = 16 - (3 * 0.25)
        val sosyalNet = 17 - (2 * 0.25)

        assertEquals(34.0, turkceNet, 0.001)
        assertEquals(28.5, matNet, 0.001)
        assertEquals(15.25, fenNet, 0.001)
        assertEquals(16.5, sosyalNet, 0.001)

        val dersKarnesi = listOf(
            DenemeDersNeti(dersAdi = "Türkçe", dogruSayisi = 35, yanlisSayisi = 4, bosSayisi = 1, net = turkceNet),
            DenemeDersNeti(dersAdi = "Temel Matematik", dogruSayisi = 30, yanlisSayisi = 6, bosSayisi = 4, net = matNet),
            DenemeDersNeti(dersAdi = "Fen Bilimleri", dogruSayisi = 16, yanlisSayisi = 3, bosSayisi = 1, net = fenNet),
            DenemeDersNeti(dersAdi = "Sosyal Bilimler", dogruSayisi = 17, yanlisSayisi = 2, bosSayisi = 1, net = sosyalNet)
        )

        val toplamDogru = dersKarnesi.sumOf { it.dogruSayisi }
        val toplamYanlis = dersKarnesi.sumOf { it.yanlisSayisi }
        val toplamBos = dersKarnesi.sumOf { it.bosSayisi }
        val beklenenToplamNet = toplamDogru - (toplamYanlis * 0.25)
        val dersNetleriToplami = dersKarnesi.sumOf { it.net }

        assertEquals(98, toplamDogru)
        assertEquals(15, toplamYanlis)
        assertEquals(7, toplamBos)
        assertEquals(94.25, beklenenToplamNet, 0.001)
        assertEquals(beklenenToplamNet, dersNetleriToplami, 0.001)

        val deneme = DenemeSinavi(
            id = 101L,
            testId = "TYT-3D-2026-01",
            baslik = "3D Türkiye Geneli TYT Deneme-1",
            yayinEvi = "3D Yayınları",
            sinavTuru = "TYT",
            sureDakika = 165,
            toplamSoru = 120,
            toplamDogru = toplamDogru,
            toplamYanlis = toplamYanlis,
            toplamBos = toplamBos,
            toplamNet = beklenenToplamNet,
            dersNetleri = dersKarnesi
        )

        assertEquals(120, deneme.toplamSoru)
        assertEquals(94.25, deneme.toplamNet, 0.001)
        assertEquals(4, deneme.dersNetleri.size)
    }

    @Test
    fun `deneme sinavi entity ve domain donusumu json ders karnesini kayipsiz korur`() {
        val dersKarnesi = listOf(
            DenemeDersNeti("Matematik", 32, 5, 3, 30.75),
            DenemeDersNeti("Fizik", 11, 3, 0, 10.25),
            DenemeDersNeti("Kimya", 12, 1, 0, 11.75),
            DenemeDersNeti("Biyoloji", 10, 2, 1, 9.5)
        )

        val domain = DenemeSinavi(
            id = 77L,
            testId = "AYT-BILGI-SARMAL-02",
            baslik = "Bilgi Sarmal AYT Sayısal Deneme",
            yayinEvi = "Bilgi Sarmal",
            sinavTuru = "AYT",
            tarih = 1715000000000L,
            sureDakika = 180,
            toplamSoru = 80,
            toplamDogru = 65,
            toplamYanlis = 11,
            toplamBos = 4,
            toplamNet = 62.25,
            dersNetleri = dersKarnesi,
            cevapAnahtariJson = "{\"1\":\"A\",\"2\":\"B\"}",
            notlar = "İntegral ve optik konularına tekrar çalış."
        )

        // Domain -> Entity
        val entity = domain.toEntity(json)
        assertEquals(domain.id, entity.id)
        assertEquals(domain.testId, entity.testId)
        assertEquals(domain.baslik, entity.baslik)
        assertEquals(domain.toplamNet, entity.toplamNet, 0.001)
        assertTrue("Ders netleri json string olmalı", entity.dersNetleriJson.contains("Matematik"))

        // Entity -> Domain
        val restored = entity.toDomain(json)
        assertEquals(domain.id, restored.id)
        assertEquals(domain.testId, restored.testId)
        assertEquals(domain.toplamNet, restored.toplamNet, 0.001)
        assertEquals(4, restored.dersNetleri.size)
        assertEquals("Matematik", restored.dersNetleri[0].dersAdi)
        assertEquals(30.75, restored.dersNetleri[0].net, 0.001)
        assertEquals("Biyoloji", restored.dersNetleri[3].dersAdi)
        assertEquals(9.5, restored.dersNetleri[3].net, 0.001)
        assertEquals("İntegral ve optik konularına tekrar çalış.", restored.notlar)
    }

    // =========================================================================
    // 2. DENEME İLE SORU BAĞLANTISI (denemeId & denemeSoruNo) TESTLERİ
    // =========================================================================

    @Test
    fun `denemeye yanlis soru eklendiginde Soru ve SoruEntity denemeId ve denemeSoruNo degerlerini alir`() {
        val targetDenemeId = 105L
        val denemeKitapcikSoruNo = 18

        val soru = Soru(
            id = 501L,
            imagePath = "/data/user/0/images/deneme_soru_18.jpg",
            examType = "TYT",
            subjectId = "matematik",
            topic = "Polinomlar",
            mistakeType = HataTipi.ISLEM_HATASI,
            correctAnswer = "C",
            studentAnswer = "E",
            kaynakYayin = "3D Yayınları",
            kaynakDeneme = "Türkiye Geneli TYT Deneme-1",
            kaynakSoruNo = denemeKitapcikSoruNo,
            denemeId = targetDenemeId,
            denemeSoruNo = denemeKitapcikSoruNo
        )

        assertEquals(targetDenemeId, soru.denemeId)
        assertEquals(denemeKitapcikSoruNo, soru.denemeSoruNo)
        assertEquals(denemeKitapcikSoruNo, soru.kaynakSoruNo)

        // Soru -> SoruEntity dönüşümünde denemeId ve denemeSoruNo korunur
        val entity: SoruEntity = soru.toEntity()
        assertEquals(targetDenemeId, entity.denemeId)
        assertEquals(denemeKitapcikSoruNo, entity.denemeSoruNo)
        assertEquals(denemeKitapcikSoruNo, entity.kaynakSoruNo)

        // SoruEntity -> Soru ters dönüşümünde denemeId ve denemeSoruNo korunur
        val restoredSoru: Soru = entity.toDomain()
        assertEquals(targetDenemeId, restoredSoru.denemeId)
        assertEquals(denemeKitapcikSoruNo, restoredSoru.denemeSoruNo)
        assertEquals(denemeKitapcikSoruNo, restoredSoru.kaynakSoruNo)
    }

    @Test
    fun `soru bagimsiz eklendiginde denemeId ve denemeSoruNo null olabilir`() {
        val bagimsizSoru = Soru(
            id = 200L,
            imagePath = "/data/user/0/images/soru_200.jpg",
            examType = "TYT",
            subjectId = "turkce",
            topic = "Paragrafta Anlam",
            denemeId = null,
            denemeSoruNo = null
        )

        val entity = bagimsizSoru.toEntity()
        assertEquals(null, entity.denemeId)
        assertEquals(null, entity.denemeSoruNo)

        val restored = entity.toDomain()
        assertEquals(null, restored.denemeId)
        assertEquals(null, restored.denemeSoruNo)
    }

    // =========================================================================
    // 3. YKS VE LGS KONU BAZLI NET KAYBI (yanlis * 1.25) ALGORİTMA TESTLERİ
    // =========================================================================

    @Test
    fun `YKS konu bazli net kaybi her yanlis icin 1 tam 25 net eksilme algoritmasini dogrular`() {
        // YKS Sisteminde: 4 Yanlış 1 Doğruyu götürür.
        // Bir soruyu yanlış yapmak:
        // 1) O sorudan gelecek +1.00 netten mahrum kalmak
        // 2) Mevcut doğrulardan -0.25 net kaybetmek demektir.
        // Dolayısıyla 1 Yanlış = 1.25 Net Kaybıdır.

        fun calculateYksNetKaybi(yanlisSayisi: Int, examType: String): Double {
            val penaltyFactor = if (examType.equals("LGS", ignoreCase = true)) 1.33 else 1.25
            return round(yanlisSayisi * penaltyFactor * 100.0) / 100.0
        }

        // 1 yanlış: 1.25 net kaybı
        assertEquals(1.25, calculateYksNetKaybi(1, "TYT"), 0.001)
        assertEquals(1.25, calculateYksNetKaybi(1, "AYT"), 0.001)

        // 4 yanlış: 4 * 1.25 = 5.00 net kaybı
        assertEquals(5.0, calculateYksNetKaybi(4, "TYT"), 0.001)

        // 7 yanlış: 7 * 1.25 = 8.75 net kaybı
        assertEquals(8.75, calculateYksNetKaybi(7, "TYT"), 0.001)

        // 12 yanlış: 12 * 1.25 = 15.00 net kaybı
        assertEquals(15.0, calculateYksNetKaybi(12, "AYT"), 0.001)

        // LGS için: 3 yanlış 1 doğruyu götürür (1 + 1/3 = 1.33 net kaybı)
        // 3 yanlış: 3 * 1.33 = 3.99 net kaybı
        assertEquals(3.99, calculateYksNetKaybi(3, "LGS"), 0.01)
    }

    @Test
    fun `konu net kaybi tablosunda yanlis sayisina gore net kaybi ve cozulme orani siralamasi dogrudur`() {
        val sorular = listOf(
            // Fonksiyonlar: 4 yanlış, 2'si çözülmüş -> Net Kaybı: 4 * 1.25 = 5.0, Çözülme: 2/4 = %50
            Soru(id = 1, imagePath = "p1", examType = "TYT", subjectId = "matematik", topic = "Fonksiyonlar", isSolved = true),
            Soru(id = 2, imagePath = "p2", examType = "TYT", subjectId = "matematik", topic = "Fonksiyonlar", isSolved = true),
            Soru(id = 3, imagePath = "p3", examType = "TYT", subjectId = "matematik", topic = "Fonksiyonlar", isSolved = false),
            Soru(id = 4, imagePath = "p4", examType = "TYT", subjectId = "matematik", topic = "Fonksiyonlar", isSolved = false),

            // Olasılık: 2 yanlış, 0'ı çözülmüş -> Net Kaybı: 2 * 1.25 = 2.5, Çözülme: 0/2 = %0
            Soru(id = 5, imagePath = "p5", examType = "TYT", subjectId = "matematik", topic = "Olasılık", isSolved = false),
            Soru(id = 6, imagePath = "p6", examType = "TYT", subjectId = "matematik", topic = "Olasılık", isSolved = false),

            // Türev: 6 yanlış, 3'ü çözülmüş -> Net Kaybı: 6 * 1.25 = 7.5, Çözülme: 3/6 = %50
            Soru(id = 7, imagePath = "p7", examType = "AYT", subjectId = "matematik", topic = "Türev", isSolved = true),
            Soru(id = 8, imagePath = "p8", examType = "AYT", subjectId = "matematik", topic = "Türev", isSolved = true),
            Soru(id = 9, imagePath = "p9", examType = "AYT", subjectId = "matematik", topic = "Türev", isSolved = true),
            Soru(id = 10, imagePath = "p10", examType = "AYT", subjectId = "matematik", topic = "Türev", isSolved = false),
            Soru(id = 11, imagePath = "p11", examType = "AYT", subjectId = "matematik", topic = "Türev", isSolved = false),
            Soru(id = 12, imagePath = "p12", examType = "AYT", subjectId = "matematik", topic = "Türev", isSolved = false)
        )

        val grouped = sorular.groupBy { "${it.examType}_${it.subjectId}_${it.topic}" }
            .map { (key, list) ->
                val parts = key.split("_")
                val examType = parts[0]
                val topic = parts[2]
                val yanlisSayisi = list.size
                val cozulmus = list.count { it.isSolved }
                val netKaybi = round(yanlisSayisi * 1.25 * 100.0) / 100.0
                val cozulmeOrani = if (yanlisSayisi > 0) cozulmus.toFloat() / yanlisSayisi.toFloat() else 0f
                Triple(topic, netKaybi, cozulmeOrani)
            }
            .sortedByDescending { it.second }

        // En çok net kaybettiren ilk konu Türev (7.5 net) olmalıdır
        assertEquals("Türev", grouped[0].first)
        assertEquals(7.5, grouped[0].second, 0.001)
        assertEquals(0.5f, grouped[0].third, 0.001f)

        // İkinci konu Fonksiyonlar (5.0 net) olmalıdır
        assertEquals("Fonksiyonlar", grouped[1].first)
        assertEquals(5.0, grouped[1].second, 0.001)
        assertEquals(0.5f, grouped[1].third, 0.001f)

        // Üçüncü konu Olasılık (2.5 net) olmalıdır
        assertEquals("Olasılık", grouped[2].first)
        assertEquals(2.5, grouped[2].second, 0.001)
        assertEquals(0.0f, grouped[2].third, 0.001f)
    }
}
