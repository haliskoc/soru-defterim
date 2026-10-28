package com.sorudefteri.ui.eslesme

import androidx.lifecycle.ViewModel
import com.sorudefteri.data.sync.CihazEslestirme
import com.sorudefteri.data.sync.SyncDeposu
import com.sorudefteri.data.sync.TrustedDevice
import com.sorudefteri.data.sync.TrustedDeviceStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Yakın-cihaz eşleşme ekranı ViewModel'i.
 * Taşıma [CihazEslestirme]'de, karar [SyncDeposu]'nda; burası yalnız UI durumu.
 */
@HiltViewModel
class CihazEslemeViewModel @Inject constructor(
    val eslestirme: CihazEslestirme,
    val depo: SyncDeposu,
    private val trustedDeviceStore: TrustedDeviceStore? = null
) : ViewModel() {

    val durum = eslestirme.durum
    val bulunanlar = eslestirme.bulunanlar
    val ilerleme = eslestirme.ilerleme
    val faz = depo.faz

    val trustedDevices: StateFlow<List<TrustedDevice>> =
        trustedDeviceStore?.trustedDevices ?: MutableStateFlow(emptyList())

    val autoProximitySyncEnabled: StateFlow<Boolean> =
        trustedDeviceStore?.autoProximitySyncEnabled ?: MutableStateFlow(true)

    fun setAutoProximitySyncEnabled(enabled: Boolean) {
        trustedDeviceStore?.setAutoProximitySyncEnabled(enabled)
    }

    fun removeTrustedDevice(cihazKodu: String) {
        trustedDeviceStore?.removeTrustedDevice(cihazKodu)
    }

    fun isTrustedDevice(adVeyaKod: String): Boolean {
        return trustedDeviceStore?.isTrusted(adVeyaKod) == true
    }

    fun baslat() {
        // Zaten yayında veya bağlıysa rotasyonda bağlantıyı sıfırlama
        val mevcutDurum = eslestirme.durum.value
        if (mevcutDurum is CihazEslestirme.BaglantiDurumu.Bagli ||
            mevcutDurum is CihazEslestirme.BaglantiDurumu.PinBekliyor ||
            mevcutDurum is CihazEslestirme.BaglantiDurumu.Yayinda
        ) {
            return
        }
        depo.sifirla()
        eslestirme.baslat()
    }

    fun yenidenTara() {
        eslestirme.durdur()
        depo.sifirla()
        eslestirme.baslat()
    }

    fun baglan(endpointId: String, ad: String) {
        eslestirme.baglan(endpointId, ad)
    }

    /**
     * PIN onaylandı. Gerçek senkronizasyon onConnectionResult(isSuccess) geldiğinde
     * CihazEslestirme.baglantiKuruldu callback'i üzerinden deterministik rolle başlar.
     */
    fun pinOnayla(endpointId: String) {
        eslestirme.pinOnayla(endpointId)
    }

    fun pinReddet(endpointId: String) {
        eslestirme.pinReddet(endpointId)
    }

    fun baglantiyiKes() {
        depo.sifirla()
        eslestirme.baglantiyiKes()
    }

    override fun onCleared() {
        super.onCleared()
        // PERF: Ekran tamamen kapandığında (back-stack'ten çıktığında) BLE/Wi-Fi aramasını bitir.
        eslestirme.durdur()
        depo.durdur()
    }
}
