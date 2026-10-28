package com.sorudefteri.data.sync

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Yakın Cihaz Eşleşmesi, Güvenilir Cihaz Hafızası ve Deneme Sınavı Paket Mesajı Birim Testleri.
 */
class TrustedDeviceSyncTest {

    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor
    private val fakeStorage = mutableMapOf<String, Any?>()

    @Before
    fun setUp() {
        fakeStorage.clear()
        mockEditor = mockk(relaxed = true)
        every { mockEditor.putString(any(), any()) } answers {
            val key = firstArg<String>()
            val value = secondArg<String>()
            fakeStorage[key] = value
            mockEditor
        }
        every { mockEditor.putBoolean(any(), any()) } answers {
            val key = firstArg<String>()
            val value = secondArg<Boolean>()
            fakeStorage[key] = value
            mockEditor
        }
        every { mockEditor.remove(any()) } answers {
            val key = firstArg<String>()
            fakeStorage.remove(key)
            mockEditor
        }
        every { mockEditor.apply() } returns Unit

        mockPrefs = mockk(relaxed = true)
        every { mockPrefs.edit() } returns mockEditor
        every { mockPrefs.getString(any(), any()) } answers {
            val key = firstArg<String>()
            val defaultVal = secondArg<String?>()
            (fakeStorage[key] as? String) ?: defaultVal
        }
        every { mockPrefs.getBoolean(any(), any()) } answers {
            val key = firstArg<String>()
            val defaultVal = secondArg<Boolean>()
            (fakeStorage[key] as? Boolean) ?: defaultVal
        }

        mockContext = mockk(relaxed = true)
        every { mockContext.getSharedPreferences(any(), any()) } returns mockPrefs
    }

    // =========================================================================
    // 1. PROTOKOL & DENEME PAKET MESAJI TESTLERİ
    // =========================================================================

    @Test
    fun `DenemePaketMesaj kodlanip cozuldugunde tum veriler korunur`() {
        val testItem = DenemeSyncItem(
            testId = "SD-EXAM-99",
            baslik = "Örnek TYT Türkiye Geneli",
            yayinEvi = "3D Yayınları",
            sinavTuru = "TYT",
            tarih = 1700000000000L,
            sureDakika = 165,
            toplamSoru = 120,
            toplamDogru = 95,
            toplamYanlis = 15,
            toplamBos = 10,
            toplamNet = 91.25,
            dersNetleriJson = """[{"dersAdi":"Türkçe","dogruSayisi":35,"yanlisSayisi":5,"bosSayisi":0,"net":33.75}]""",
            cevapAnahtariJson = """{"1":"A","2":"B"}""",
            ogrenciCevaplariJson = """{"1":"A","2":"C"}""",
            notlar = "Süre yönetimi başarılıydı",
            isDeleted = 0
        )
        val denemePaket = DenemeSyncPaket(
            denemeler = listOf(testItem)
        )
        val mesaj = SyncMesaj.DenemePaketMesaj(denemePaket)

        val kodlanmis = SyncProtokol.kodla(mesaj)
        val cozulmus = SyncProtokol.coz(kodlanmis)

        assertTrue("Çözülen mesaj DenemePaketMesaj olmalıdır", cozulmus is SyncMesaj.DenemePaketMesaj)
        val cozulmusPaket = (cozulmus as SyncMesaj.DenemePaketMesaj).icerik
        assertEquals(1, cozulmusPaket.denemeler.size)

        val gelenItem = cozulmusPaket.denemeler.first()
        assertEquals("SD-EXAM-99", gelenItem.testId)
        assertEquals("Örnek TYT Türkiye Geneli", gelenItem.baslik)
        assertEquals("3D Yayınları", gelenItem.yayinEvi)
        assertEquals(91.25, gelenItem.toplamNet, 0.001)
    }

    // =========================================================================
    // 2. TRUSTED DEVICE STORE GÜVENİLİR CİHAZ YÖNETİMİ
    // =========================================================================

    @Test
    fun `markTrusted ile eklenen cihaz listelenir ve isTrusted ile dogrulanir`() {
        val store = TrustedDeviceStore(mockContext)

        store.markTrusted(
            cihazAdiVeyaKodu = "Galaxy Tab S9 • 4821",
            pinVeyaKod = "4821"
        )

        val list = store.trustedDevices.value
        assertEquals(1, list.size)
        assertEquals("4821", list.first().cihazKodu)
        assertTrue(list.first().cihazAdi.contains("Galaxy Tab S9"))

        // PIN / kod ile kontrol
        assertTrue("Cihaz koduyla tanınmalıdır", store.isTrusted("4821"))
        assertTrue("Cihaz adıyla tanınmalıdır", store.isTrusted("Galaxy Tab S9 • 4821"))
        assertFalse("Bilinmeyen cihaz reddedilmelidir", store.isTrusted("9999"))
    }

    @Test
    fun `removeTrustedDevice guvenilir cihaz hafizasindan siler`() {
        val store = TrustedDeviceStore(mockContext)

        store.markTrusted("iPad Pro • 1234", "1234")
        assertEquals(1, store.trustedDevices.value.size)

        store.removeTrustedDevice("1234")
        assertTrue("Liste boşalmalıdır", store.trustedDevices.value.isEmpty())
        assertFalse("Artık güvenilir olmamalıdır", store.isTrusted("1234"))
    }

    @Test
    fun `clearAll tum guvenilir cihazlari temizler`() {
        val store = TrustedDeviceStore(mockContext)

        store.markTrusted("Cihaz A • 1111", "1111")
        store.markTrusted("Cihaz B • 2222", "2222")
        assertEquals(2, store.trustedDevices.value.size)

        store.clearAll()
        assertEquals(0, store.trustedDevices.value.size)
    }

    @Test
    fun `autoProximitySyncEnabled StateFlow ve ayar durumu degistirilebilir`() {
        val store = TrustedDeviceStore(mockContext)

        // Varsayılan true
        assertTrue(store.isAutoProximitySyncEnabled())
        assertTrue(store.autoProximitySyncEnabled.value)

        // Kapat
        store.setAutoProximitySyncEnabled(false)
        assertFalse(store.isAutoProximitySyncEnabled())
        assertFalse(store.autoProximitySyncEnabled.value)

        // Aç
        store.setAutoProximitySyncEnabled(true)
        assertTrue(store.isAutoProximitySyncEnabled())
        assertTrue(store.autoProximitySyncEnabled.value)
    }
}
