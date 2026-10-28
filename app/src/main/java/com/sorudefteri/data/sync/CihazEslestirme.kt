package com.sorudefteri.data.sync

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Nearby Connections sarmalayıcı: telefon ↔ tablet eşleşme taşıması.
 *
 * Strateji: P2P_POINT_TO_POINT (2 cihaz, en yüksek bant). BLE keşif +
 * Wi-Fi aktarım, uçtan uca şifreli, internetsiz. Çift-yön aynı anda YOK —
 * [SyncDeposu] SIRA_SENDE jetonuyla sıralar; burası yalnız taşır.
 *
 * Yaşam döngüsü: ekran açılırken [baslat], kapanırken [durdur]
 * (stopAllEndpoints). Yük yalnız eşleşme ekranı açıkken biner.
 */
@Singleton
class CihazEslestirme @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trustedDeviceStore: TrustedDeviceStore? = null
) {

    companion object {
        private const val TAG = "CihazEslestirme"
    }

    sealed interface BaglantiDurumu {
        data object Kapali : BaglantiDurumu
        data object Yayinda : BaglantiDurumu
        data class PinBekliyor(val endpointId: String, val endpointAdi: String, val pin: String) : BaglantiDurumu
        data class Bagli(val endpointId: String, val endpointAdi: String) : BaglantiDurumu
        data class Hata(val mesaj: String) : BaglantiDurumu
    }

    data class BulunanCihaz(val endpointId: String, val ad: String)

    private val istemci: ConnectionsClient by lazy { Nearby.getConnectionsClient(context) }
    private val kapsam = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _durum = MutableStateFlow<BaglantiDurumu>(BaglantiDurumu.Kapali)
    val durum: StateFlow<BaglantiDurumu> = _durum.asStateFlow()

    private val _bulunanlar = MutableStateFlow<List<BulunanCihaz>>(emptyList())
    val bulunanlar: StateFlow<List<BulunanCihaz>> = _bulunanlar.asStateFlow()

    private val _ilerleme = MutableStateFlow<Pair<Long, Long>?>(null)
    /** (alınan bayt, toplam bayt) — dosya aktarım ilerlemesi. */
    val ilerleme: StateFlow<Pair<Long, Long>?> = _ilerleme.asStateFlow()

    /** Gelen BYTES mesajları (SyncDeposu tüketir). */
    var mesajDinleyici: ((String, SyncMesaj) -> Unit)? = null

    /** Gelen FILE tamamlanınca (payloadId → geçici dosya). */
    var dosyaDinleyici: ((Long, File?) -> Unit)? = null

    /** Bağlantı kurulduğunda: (endpointId, baslatanMi). */
    var baglantiKuruldu: ((String, Boolean) -> Unit)? = null

    private val gelenBaglanti = ConcurrentHashMap<String, Boolean>()
    private val endpointAdlari = ConcurrentHashMap<String, String>()
    private val gelenDosyalar = ConcurrentHashMap<Long, Payload.File>()

    private val cihazKodu: String by lazy {
        val id = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
        } catch (_: Exception) { "" }
        if (id.length >= 4) id else UUID.randomUUID().toString().replace("-", "").take(16)
    }

    private val baglantiGeriCagirma = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            if (info.isIncomingConnection && _durum.value is BaglantiDurumu.Bagli) {
                // Olasılık #9: meşgulken ikinci bağlantıyı reddet
                try { istemci.rejectConnection(endpointId) } catch (_: Exception) {}
                return
            }
            gelenBaglanti[endpointId] = info.isIncomingConnection
            endpointAdlari[endpointId] = info.endpointName

            // Güvenilir Cihaz & Yan Yana Otomatik Eşleşme: PIN diyaloğu beklemeden otomatik kabul et (Zero-Click Auto-Handshake)
            if (trustedDeviceStore?.isAutoProximitySyncEnabled() == true &&
                trustedDeviceStore.isTrusted(info.endpointName)
            ) {
                Log.i(TAG, "Güvenilir cihaz algılandı (${info.endpointName}) — otomatik kabul ediliyor")
                kabulEt(endpointId)
                return
            }

            _durum.value = BaglantiDurumu.PinBekliyor(endpointId, info.endpointName, info.authenticationDigits)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                val ad = endpointAdlari[endpointId]
                    ?: (_durum.value as? BaglantiDurumu.PinBekliyor)?.endpointAdi
                    ?: endpointId
                _durum.value = BaglantiDurumu.Bagli(endpointId, ad)
                try { istemci.stopDiscovery() } catch (_: Exception) {}
                try { istemci.stopAdvertising() } catch (_: Exception) {}
                val isIncoming = gelenBaglanti[endpointId] ?: true
                val baslatanBenMiyim = !isIncoming
                baglantiKuruldu?.invoke(endpointId, baslatanBenMiyim)
            } else {
                val kod = result.status.statusCode
                Log.e(TAG, "onConnectionResult başarısız: kod=$kod mesaj=${result.status.statusMessage}")
                _durum.value = BaglantiDurumu.Hata("Bağlantı kurulamadı (Kod: $kod)")
            }
        }

        override fun onDisconnected(endpointId: String) {
            gelenBaglanti.remove(endpointId)
            if (_durum.value is BaglantiDurumu.Bagli) {
                _durum.value = BaglantiDurumu.Hata("Bağlantı koptu — yeniden eşleşerek devam edebilirsiniz")
            }
        }
    }

    private val kesifGeriCagirma = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            // Olasılık #10: kendi reklamımızı görürsek yoksay (ad içinde cihaz kodu son 4 hane)
            val son4 = cihazKodu.takeLast(4)
            if (son4.isNotEmpty() && info.endpointName.endsWith("• $son4")) return
            endpointAdlari[endpointId] = info.endpointName
            val mevcut = _bulunanlar.value
            if (mevcut.none { it.endpointId == endpointId }) {
                _bulunanlar.value = mevcut + BulunanCihaz(endpointId, info.endpointName)
            }
            // Güvenilir Cihaz & Yan Yana Otomatik Eşleşme: keşfedildiği anda otomatik bağlantı başlat
            if (trustedDeviceStore?.isAutoProximitySyncEnabled() == true &&
                trustedDeviceStore.isTrusted(info.endpointName) &&
                _durum.value is BaglantiDurumu.Yayinda
            ) {
                Log.i(TAG, "Güvenilir cihaz keşfedildi (${info.endpointName}) — otomatik bağlanılıyor")
                baglan(endpointId, info.endpointName)
            }
        }

        override fun onEndpointLost(endpointId: String) {
            _bulunanlar.value = _bulunanlar.value.filterNot { it.endpointId == endpointId }
        }
    }

    private val yukGeriCagirma = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> {
                    val metin = payload.asBytes()?.let { SyncProtokol.metinAl(it) } ?: return
                    try {
                        val mesaj = SyncProtokol.coz(metin)
                        mesajDinleyici?.invoke(endpointId, mesaj)
                    } catch (e: Exception) {
                        Log.e(TAG, "Mesaj çözülemedi", e)
                    }
                }
                Payload.Type.FILE -> {
                    // onPayloadReceived ilk baytta çağrılır; dosya henüz tamamlanmadı.
                    // Yalnızca referansı sakla, okumayı onPayloadTransferUpdate(SUCCESS)'te yap.
                    payload.asFile()?.let { pf ->
                        gelenDosyalar[payload.id] = pf
                    }
                }
                else -> Unit
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            when (update.status) {
                PayloadTransferUpdate.Status.IN_PROGRESS -> {
                    if (update.totalBytes > 0) {
                        _ilerleme.value = update.bytesTransferred to update.totalBytes
                    }
                }
                PayloadTransferUpdate.Status.SUCCESS -> {
                    _ilerleme.value = null
                    val pf = gelenDosyalar.remove(update.payloadId) ?: return
                    kapsam.launch {
                        val kopyalanan = dosyaCikar(update.payloadId, pf)
                        dosyaDinleyici?.invoke(update.payloadId, kopyalanan)
                    }
                }
                PayloadTransferUpdate.Status.FAILURE, PayloadTransferUpdate.Status.CANCELED -> {
                    _ilerleme.value = null
                    val pf = gelenDosyalar.remove(update.payloadId)
                    try { pf?.asParcelFileDescriptor()?.close() } catch (_: Exception) {}
                    if (pf != null) {
                        dosyaDinleyici?.invoke(update.payloadId, null)
                    }
                }
            }
        }
    }

    /**
     * Gelen Payload.File'ı güvenli şekilde cacheDir içine kopyalar.
     * 19.x AAR'larında asJavaFile() alıcıda null dönebildiği için önce asUri(),
     * ardından asParcelFileDescriptor(), en son asJavaFile() denenir.
     */
    private fun dosyaCikar(payloadId: Long, pf: Payload.File): File? {
        val hedef = File(context.cacheDir, "nearby_recv_$payloadId.webp")
        try {
            pf.asUri()?.let { uri ->
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(hedef).use { out -> input.copyTo(out) }
                }
                if (hedef.exists() && hedef.length() > 0) return hedef
            }
        } catch (e: Exception) {
            Log.w(TAG, "asUri ile okuma başarısız: $payloadId", e)
        }
        try {
            pf.asParcelFileDescriptor()?.use { pfd ->
                FileInputStream(pfd.fileDescriptor).use { input ->
                    FileOutputStream(hedef).use { out -> input.copyTo(out) }
                }
                if (hedef.exists() && hedef.length() > 0) return hedef
            }
        } catch (e: Exception) {
            Log.w(TAG, "asParcelFileDescriptor ile okuma başarısız: $payloadId", e)
        }
        try {
            @Suppress("DEPRECATION")
            val jf = pf.asJavaFile()
            if (jf != null && jf.exists() && jf.length() > 0) {
                jf.copyTo(hedef, overwrite = true)
                try { jf.delete() } catch (_: Exception) {}
                return hedef
            }
        } catch (e: Exception) {
            Log.w(TAG, "asJavaFile ile okuma başarısız: $payloadId", e)
        }
        return null
    }

    fun cihazAdi(): String {
        val model = (Build.MODEL ?: "Cihaz").take(14)
        return "Soru Defterim • $model • ${cihazKodu.takeLast(4)}"
    }

    fun kendiKodum(): String = cihazKodu

    private fun hataKoduAcikla(e: Exception): String {
        val kod = (e as? ApiException)?.statusCode
        return if (kod != null) "Kod $kod (${e.localizedMessage ?: ""})" else (e.localizedMessage ?: "Bilinmeyen hata")
    }

    /** Eşleşme ekranı açıldı: advertise + discover aynı anda. */
    fun baslat() {
        _bulunanlar.value = emptyList()
        _durum.value = BaglantiDurumu.Yayinda
        try {
            istemci.stopAdvertising()
            istemci.stopDiscovery()
        } catch (_: Exception) {}

        try {
            istemci.startAdvertising(
                cihazAdi(), SyncProtokol.SERVICE_ID, baglantiGeriCagirma,
                AdvertisingOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build()
            ).addOnFailureListener { e ->
                Log.e(TAG, "startAdvertising başarısız: ${hataKoduAcikla(e)}", e)
                // STATUS_ALREADY_ADVERTISING (8001) ise hata durumuna düşürme
                val kod = (e as? ApiException)?.statusCode
                if (kod != 8001) {
                    _durum.value = BaglantiDurumu.Hata("Yayın başlatılamadı: ${hataKoduAcikla(e)}")
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "startAdvertising istisna", t)
            _durum.value = BaglantiDurumu.Hata("Play Hizmetleri gerekli: ${t.localizedMessage}")
            return
        }
        try {
            istemci.startDiscovery(
                SyncProtokol.SERVICE_ID, kesifGeriCagirma,
                DiscoveryOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build()
            ).addOnFailureListener { e ->
                Log.e(TAG, "startDiscovery başarısız: ${hataKoduAcikla(e)}", e)
                val kod = (e as? ApiException)?.statusCode
                if (kod != 8002) {
                    _durum.value = BaglantiDurumu.Hata("Tarama başlatılamadı: ${hataKoduAcikla(e)}")
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "startDiscovery istisna", t)
            _durum.value = BaglantiDurumu.Hata("Tarama başlatılamadı: ${t.localizedMessage}")
        }
    }

    /** Seçilen cihaza bağlan (PIN karşıda gösterilir, güvenilir cihazsa otomatik onaylanır). */
    fun baglan(endpointId: String, ad: String = endpointAdlari[endpointId] ?: endpointId) {
        endpointAdlari[endpointId] = ad
        try {
            istemci.requestConnection(cihazAdi(), endpointId, baglantiGeriCagirma)
                .addOnFailureListener { e ->
                    Log.e(TAG, "requestConnection başarısız: ${hataKoduAcikla(e)}", e)
                    // STATUS_ALREADY_CONNECTED_TO_ENDPOINT (8003) veya STATUS_CONNECTION_REJECTED değilse uyar
                    val kod = (e as? ApiException)?.statusCode
                    if (kod != 8003) {
                        _durum.value = BaglantiDurumu.Hata("Bağlantı isteği başarısız (${hataKoduAcikla(e)})")
                    }
                }
        } catch (t: Throwable) {
            _durum.value = BaglantiDurumu.Hata("Bağlantı isteği başarısız: ${t.localizedMessage}")
        }
    }

    /** PIN onaylandı (iki taraf da onaylar) veya güvenilir cihaz otomatik kabul edildi. */
    fun pinOnayla(endpointId: String) {
        val ad = endpointAdlari[endpointId]
            ?: (_durum.value as? BaglantiDurumu.PinBekliyor)?.endpointAdi
            ?: ""
        if (ad.isNotBlank()) {
            trustedDeviceStore?.addTrustedDevice(cihazKodu = "", cihazAdi = ad)
        }
        try {
            istemci.acceptConnection(endpointId, yukGeriCagirma)
                .addOnFailureListener { e ->
                    Log.e(TAG, "acceptConnection başarısız: ${hataKoduAcikla(e)}", e)
                    _durum.value = BaglantiDurumu.Hata("Onay başarısız (${hataKoduAcikla(e)})")
                }
        } catch (t: Throwable) {
            _durum.value = BaglantiDurumu.Hata("Onay başarısız: ${t.localizedMessage}")
        }
    }

    /** Güvenilir cihaz bağlantısını PIN diyaloğu beklemeden otomatik kabul eder. */
    fun kabulEt(endpointId: String) = pinOnayla(endpointId)

    /** PIN reddedildi. */
    fun pinReddet(endpointId: String) {
        try { istemci.rejectConnection(endpointId) } catch (_: Exception) {}
        gelenBaglanti.remove(endpointId)
        _durum.value = BaglantiDurumu.Yayinda
    }

    /** Bağlantıyı kesip keşfi yeniden başlatarak listeye dön. */
    fun baglantiyiKes() {
        val bagli = (_durum.value as? BaglantiDurumu.Bagli)?.endpointId
        try { if (bagli != null) istemci.disconnectFromEndpoint(bagli) } catch (_: Exception) {}
        gelenBaglanti.clear()
        baslat()
    }

    fun mesajGonder(endpointId: String, mesaj: SyncMesaj) {
        try {
            val metin = SyncProtokol.kodla(mesaj)
            istemci.sendPayload(endpointId, Payload.fromBytes(SyncProtokol.bytesAl(metin)))
                .addOnFailureListener { e ->
                    Log.e(TAG, "sendPayload(BYTES) başarısız: ${hataKoduAcikla(e)}", e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "mesajGonder istisna", e)
        }
    }

    /** Büyük özetleri chunk'la gönderir. */
    fun ozetGonder(endpointId: String, tum: List<OzetSatir>) {
        SyncProtokol.ozetChunkla(tum).forEach { mesajGonder(endpointId, SyncMesaj.Ozet(it)) }
        mesajGonder(endpointId, SyncMesaj.OzetBitti)
    }

    fun dosyaGonder(endpointId: String, dosya: File): Long {
        return try {
            val yuk = Payload.fromFile(dosya)
            istemci.sendPayload(endpointId, yuk)
                .addOnFailureListener { e ->
                    Log.e(TAG, "sendPayload(FILE) başarısız: ${hataKoduAcikla(e)}", e)
                }
            yuk.id
        } catch (e: Exception) {
            Log.e(TAG, "dosyaGonder istisna", e)
            -1L
        }
    }

    /** Ekran kapandı: her şeyi durdur (pil disiplini). */
    fun durdur() {
        try { istemci.stopAllEndpoints() } catch (_: Exception) {}
        try { istemci.stopAdvertising() } catch (_: Exception) {}
        try { istemci.stopDiscovery() } catch (_: Exception) {}
        gelenBaglanti.clear()
        endpointAdlari.clear()
        gelenDosyalar.values.forEach { pf ->
            try { pf.asParcelFileDescriptor()?.close() } catch (_: Exception) {}
        }
        gelenDosyalar.clear()
        _ilerleme.value = null
        _durum.value = BaglantiDurumu.Kapali
        _bulunanlar.value = emptyList()
    }
}
