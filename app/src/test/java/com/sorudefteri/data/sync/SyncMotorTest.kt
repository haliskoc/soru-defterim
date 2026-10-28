package com.sorudefteri.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SyncMotor karar matrisi testleri (olasılık #1, #2, #17, #18).
 */
class SyncMotorTest {

    private fun yerel(e: String, u: Long, h: String = "h1") =
        SyncMotor.YerelKayit(e, u, h)

    private fun uzak(e: String, u: Long, h: String = "h1") =
        SyncMotor.UzakKayit(e, u, h)

    @Test
    fun `bizde yoksa EKLE`() {
        val k = SyncMotor.karar(null, uzak("e1", 1000))
        assertEquals(SyncMotor.Karar.EKLE, k.karar)
    }

    @Test
    fun `uzak eskiyse ATLA`() {
        val k = SyncMotor.karar(yerel("e1", 2000), uzak("e1", 1000))
        assertEquals(SyncMotor.Karar.ATLA, k.karar)
    }

    @Test
    fun `uzak yeniyse GUNCELLE`() {
        val k = SyncMotor.karar(yerel("e1", 1000, "hX"), uzak("e1", 5000, "hY"))
        assertEquals(SyncMotor.Karar.GUNCELLE, k.karar)
        assertFalse(k.cakisma)
    }

    @Test
    fun `tolerans ici ve ayni icerikse ATLA`() {
        val k = SyncMotor.karar(yerel("e1", 1000, "hX"), uzak("e1", 1000 + 30_000, "hX"))
        assertEquals(SyncMotor.Karar.ATLA, k.karar)
    }

    @Test
    fun `tolerans ici ama icerik farkliysa GUNCELLE`() {
        val k = SyncMotor.karar(yerel("e1", 1000, "hX"), uzak("e1", 1000 + 30_000, "hY"))
        assertEquals(SyncMotor.Karar.GUNCELLE, k.karar)
    }

    @Test
    fun `tur icinde yerelde degismisse cakisma`() {
        val k = SyncMotor.karar(
            yerel("e1", 3000, "hX"), uzak("e1", 9000, "hY"), bazUpdatedAt = 1000
        )
        assertEquals(SyncMotor.Karar.GUNCELLE, k.karar)
        assertTrue(k.cakisma)
    }

    @Test
    fun `favori OR birlesir`() {
        assertTrue(SyncMotor.birlestirFavori(true, false))
        assertTrue(SyncMotor.birlestirFavori(false, true))
        assertFalse(SyncMotor.birlestirFavori(false, false))
    }

    @Test
    fun `createdAt min korunur`() {
        assertEquals(100L, SyncMotor.birlestirCreatedAt(100, 200))
        assertEquals(100L, SyncMotor.birlestirCreatedAt(200, 100))
    }
}

/**
 * SyncProtokol chunk + encode/decode testleri (olasılık #8).
 */
class SyncProtokolTest {

    @Test
    fun `hello kodla coz`() {
        val mesaj = SyncMesaj.Hello(SyncHello(cihazAdi = "Test", soruSayisi = 5))
        val coz = SyncProtokol.coz(SyncProtokol.kodla(mesaj)) as SyncMesaj.Hello
        assertEquals("Test", coz.icerik.cihazAdi)
        assertEquals(5, coz.icerik.soruSayisi)
    }

    @Test
    fun `ozet chunk birlesim`() {
        val tum = (0 until 850).map { OzetSatir("e$it", it.toLong(), "h$it") }
        val chunklar = SyncProtokol.ozetChunkla(tum)
        assertTrue(chunklar.size > 1)
        val birlesim = ChunkBirlesim()
        var sonuc: List<OzetSatir>? = null
        for (c in chunklar) {
            // Her chunk BYTES limitinin altında olmalı
            assertTrue(SyncProtokol.bytesAl(SyncProtokol.kodla(SyncMesaj.Ozet(c))).size < 1024 * 1024)
            sonuc = birlesim.ekle(c)
        }
        assertNotNull(sonuc)
        assertEquals(850, sonuc!!.size)
    }

    @Test
    fun `bos ozet tek paket`() {
        val chunklar = SyncProtokol.ozetChunkla(emptyList())
        assertEquals(1, chunklar.size)
    }

    @Test
    fun `bilinmeyen tip hata doner`() {
        val mesaj = SyncProtokol.coz("""{"tip":"XXX","govde":"{}"}""")
        assertTrue(mesaj is SyncMesaj.Hata)
    }

    @Test
    fun `sonuc toplama`() {
        val toplam = SyncSonuc(eklenen = 1) + SyncSonuc(guncellenen = 2, cakisan = 1)
        assertEquals(1, toplam.eklenen)
        assertEquals(2, toplam.guncellenen)
        assertEquals(1, toplam.cakisan)
    }
}

/**
 * İki SyncDeposu örneğini sahte (in-memory) bir taşıma üzerinden yan yana koşturup
 * her iki tarafın da Faz.Bitti'ye ulaştığını, görsellerin ve soruların çift yönlü
 * aktarıldığını doğrulayan entegrasyon testi.
 */
class SyncDeposuEntegrasyonTest {

    private class BellekIciDao(baslangic: List<com.sorudefteri.data.local.entity.SoruEntity> = emptyList()) {
        val kayitlar = java.util.concurrent.CopyOnWriteArrayList(baslangic)
        var sonId = 100L

        fun olusturMock(): com.sorudefteri.data.local.dao.SoruDao {
            val dao = io.mockk.mockk<com.sorudefteri.data.local.dao.SoruDao>(relaxed = true)
            io.mockk.coEvery { dao.getEslestirmeOzeti() } answers {
                kayitlar.map {
                    com.sorudefteri.data.local.dao.EslestirmeOzetTuple(
                        evrenselId = it.evrenselId,
                        updatedAt = it.updatedAt,
                        contentHash = it.contentHash,
                        isDeleted = it.isDeleted
                    )
                }
            }
            io.mockk.coEvery { dao.getByEvrenselIdler(any()) } answers {
                val idler = firstArg<List<String>>().toSet()
                kayitlar.filter { it.evrenselId in idler }
            }
            io.mockk.coEvery { dao.getByEvrenselId(any()) } answers {
                val id = firstArg<String>()
                kayitlar.firstOrNull { it.evrenselId == id }
            }
            io.mockk.coEvery { dao.insertSoru(any()) } answers {
                val yeni = firstArg<com.sorudefteri.data.local.entity.SoruEntity>().copy(id = ++sonId)
                kayitlar.removeAll { it.evrenselId == yeni.evrenselId }
                kayitlar.add(yeni)
                yeni.id
            }
            io.mockk.coEvery { dao.updateEntityFull(any()) } answers {
                val guncel = firstArg<com.sorudefteri.data.local.entity.SoruEntity>()
                val idx = kayitlar.indexOfFirst { it.id == guncel.id || it.evrenselId == guncel.evrenselId }
                if (idx >= 0) kayitlar[idx] = guncel else kayitlar.add(guncel)
            }
            return dao
        }
    }

    private fun ornekEntity(evrenselId: String, konu: String, updatedAt: Long, gorselYol: String = ""): com.sorudefteri.data.local.entity.SoruEntity {
        val taslak = com.sorudefteri.domain.model.Soru(
            id = 1L,
            evrenselId = evrenselId,
            imagePath = gorselYol,
            examType = "TYT",
            subjectId = "matematik",
            topic = konu,
            questionText = "Soru metni $evrenselId",
            createdAt = 1_000L,
            updatedAt = updatedAt
        )
        val hash = com.sorudefteri.data.local.SoruIcerikHash.hesapla(taslak)
        return com.sorudefteri.data.local.entity.SoruEntity(
            id = 1L,
            evrenselId = evrenselId,
            imagePath = gorselYol,
            examType = "TYT",
            subjectId = "matematik",
            topic = konu,
            questionText = "Soru metni $evrenselId",
            createdAt = 1_000L,
            updatedAt = updatedAt,
            contentHash = hash,
            isDeleted = 0
        )
    }

    @Test
    fun `iki cihaz in-memory transport ile cift yonlu eslesir ve Faz Bittiye ulasir`() = kotlinx.coroutines.runBlocking {
        val geciciKlasor = java.nio.file.Files.createTempDirectory("sync_test").toFile()
        try {
            val ornekGorselA = java.io.File(geciciKlasor, "ornek_a.webp").apply { writeBytes(ByteArray(128) { 7 }) }
            val ornekGorselB = java.io.File(geciciKlasor, "ornek_b.webp").apply { writeBytes(ByteArray(256) { 9 }) }

            val daoA = BellekIciDao(
                listOf(
                    ornekEntity("uuid-a1", "Fonksiyonlar", 10_000L, ornekGorselA.absolutePath),
                    ornekEntity("uuid-ortak", "Polinomlar (A eski)", 5_000L)
                )
            )
            val daoB = BellekIciDao(
                listOf(
                    ornekEntity("uuid-b1", "Türev", 12_000L, ornekGorselB.absolutePath),
                    ornekEntity("uuid-ortak", "Polinomlar (B yeni)", 20_000L)
                )
            )

            val context = io.mockk.mockk<android.content.Context>(relaxed = true)
            io.mockk.every { context.cacheDir } returns geciciKlasor
            io.mockk.every { context.filesDir } returns geciciKlasor

            val eslestirmeA = io.mockk.mockk<CihazEslestirme>(relaxed = true)
            val eslestirmeB = io.mockk.mockk<CihazEslestirme>(relaxed = true)

            var baglantiA: ((String, Boolean) -> Unit)? = null
            var baglantiB: ((String, Boolean) -> Unit)? = null
            var mesajAlB: ((String, SyncMesaj) -> Unit)? = null
            var mesajAlA: ((String, SyncMesaj) -> Unit)? = null
            var dosyaAlB: ((Long, java.io.File?) -> Unit)? = null
            var dosyaAlA: ((Long, java.io.File?) -> Unit)? = null

            io.mockk.every { eslestirmeA.baglantiKuruldu = any() } answers { baglantiA = firstArg() }
            io.mockk.every { eslestirmeB.baglantiKuruldu = any() } answers { baglantiB = firstArg() }
            io.mockk.every { eslestirmeA.mesajDinleyici = any() } answers { mesajAlA = firstArg() }
            io.mockk.every { eslestirmeB.mesajDinleyici = any() } answers { mesajAlB = firstArg() }
            io.mockk.every { eslestirmeA.dosyaDinleyici = any() } answers { dosyaAlA = firstArg() }
            io.mockk.every { eslestirmeB.dosyaDinleyici = any() } answers { dosyaAlB = firstArg() }

            io.mockk.every { eslestirmeA.cihazAdi() } returns "Soru Defterim • Telefon • 1111"
            io.mockk.every { eslestirmeB.cihazAdi() } returns "Soru Defterim • Tablet • 2222"
            io.mockk.every { eslestirmeA.kendiKodum() } returns "aaaa1111"
            io.mockk.every { eslestirmeB.kendiKodum() } returns "bbbb2222"

            val payloadSayac = java.util.concurrent.atomic.AtomicLong(5000L)

            // A -> B mesaj ve dosya yönlendirme
            io.mockk.every { eslestirmeA.mesajGonder(any(), any()) } answers {
                val m = secondArg<SyncMesaj>()
                mesajAlB?.invoke("epA", m)
            }
            io.mockk.every { eslestirmeA.ozetGonder(any(), any()) } answers {
                val tum = secondArg<List<OzetSatir>>()
                SyncProtokol.ozetChunkla(tum).forEach { mesajAlB?.invoke("epA", SyncMesaj.Ozet(it)) }
                mesajAlB?.invoke("epA", SyncMesaj.OzetBitti)
            }
            io.mockk.every { eslestirmeA.dosyaGonder(any(), any()) } answers {
                val kaynak = secondArg<java.io.File>()
                val pid = payloadSayac.incrementAndGet()
                val kopya = java.io.File(geciciKlasor, "recv_b_$pid.webp")
                kaynak.copyTo(kopya, overwrite = true)
                dosyaAlB?.invoke(pid, kopya)
                pid
            }

            // B -> A mesaj ve dosya yönlendirme
            io.mockk.every { eslestirmeB.mesajGonder(any(), any()) } answers {
                val m = secondArg<SyncMesaj>()
                mesajAlA?.invoke("epB", m)
            }
            io.mockk.every { eslestirmeB.ozetGonder(any(), any()) } answers {
                val tum = secondArg<List<OzetSatir>>()
                SyncProtokol.ozetChunkla(tum).forEach { mesajAlA?.invoke("epB", SyncMesaj.Ozet(it)) }
                mesajAlA?.invoke("epB", SyncMesaj.OzetBitti)
            }
            io.mockk.every { eslestirmeB.dosyaGonder(any(), any()) } answers {
                val kaynak = secondArg<java.io.File>()
                val pid = payloadSayac.incrementAndGet()
                val kopya = java.io.File(geciciKlasor, "recv_a_$pid.webp")
                kaynak.copyTo(kopya, overwrite = true)
                dosyaAlA?.invoke(pid, kopya)
                pid
            }

            val gorselDepo = io.mockk.mockk<com.sorudefteri.data.images.GorselDepo>(relaxed = true)
            io.mockk.coEvery { gorselDepo.kaydetDosyadan(any(), any()) } answers {
                val f = firstArg<java.io.File>()
                val kalici = java.io.File(geciciKlasor, "kalici_${ System.nanoTime() }.webp")
                f.copyTo(kalici, overwrite = true)
                kalici.absolutePath
            }

            val repo = io.mockk.mockk<com.sorudefteri.domain.repository.SoruRepository>(relaxed = true)
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

            val depoA = SyncDeposu(context, eslestirmeA, daoA.olusturMock(), repo, gorselDepo, json)
            val depoB = SyncDeposu(context, eslestirmeB, daoB.olusturMock(), repo, gorselDepo, json)

            // A bağlantıyı başlatan (baslatan=true), B kabul eden (baslatan=false)
            baglantiA!!.invoke("epB", true)
            baglantiB!!.invoke("epA", false)

            // Her iki deponun da Faz.Bitti durumuna ulaşmasını bekle (en fazla 10 sn)
            val baslangic = System.currentTimeMillis()
            while (System.currentTimeMillis() - baslangic < 10_000L) {
                if (depoA.faz.value is SyncDeposu.Faz.Bitti && depoB.faz.value is SyncDeposu.Faz.Bitti) {
                    break
                }
                kotlinx.coroutines.delay(50)
            }

            assertTrue("Depo A Faz.Bitti olmaliydi ama ${depoA.faz.value}", depoA.faz.value is SyncDeposu.Faz.Bitti)
            assertTrue("Depo B Faz.Bitti olmaliydi ama ${depoB.faz.value}", depoB.faz.value is SyncDeposu.Faz.Bitti)

            val bittiA = depoA.faz.value as SyncDeposu.Faz.Bitti
            val bittiB = depoB.faz.value as SyncDeposu.Faz.Bitti

            // A: uuid-b1 eklendi (1), uuid-ortak güncellendi (1), görselsiz = 0
            assertEquals(1, bittiA.sonuc.eklenen)
            assertEquals(1, bittiA.sonuc.guncellenen)
            assertEquals(0, bittiA.sonuc.gorselsiz)

            // B: uuid-a1 eklendi (1), görselsiz = 0
            assertEquals(1, bittiB.sonuc.eklenen)
            assertEquals(0, bittiB.sonuc.gorselsiz)

            // Her iki veritabanında da 3'er soru olmalı ve uuid-ortak yeni konuda olmalı
            assertEquals(3, daoA.kayitlar.size)
            assertEquals(3, daoB.kayitlar.size)
            assertEquals("Polinomlar (B yeni)", daoA.kayitlar.first { it.evrenselId == "uuid-ortak" }.topic)
            assertTrue(daoA.kayitlar.none { it.contentHash.isBlank() })
            assertTrue(daoB.kayitlar.none { it.contentHash.isBlank() })

            depoA.durdur()
            depoB.durdur()
        } finally {
            geciciKlasor.deleteRecursively()
        }
    }
}
