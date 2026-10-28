package com.sorudefteri.data.sync

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Güvenilir eşleşmiş cihaz kaydı (Telefon ↔ Tablet Yan Yana Otomatik Eşleşme Hafızası).
 */
@Serializable
data class TrustedDevice(
    val cihazKodu: String,
    val cihazAdi: String,
    val lastSyncedAt: Long = System.currentTimeMillis()
)

/**
 * Bir kez PIN onayı veya başarılı eşleşme tamamlandığında karşı cihazı "Güvenilir Cihaz"
 * olarak saklayan, iki cihaz tekrar yan yana geldiğinde PIN sormadan (Zero-Click Auto-Handshake)
 * otomatik bağlanmayı yöneten yerel tercih deposu.
 */
@Singleton
class TrustedDeviceStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val PREFS_NAME = "soru_defteri_trusted_devices"
        private const val KEY_TRUSTED_DEVICES = "trusted_devices_json"
        private const val KEY_AUTO_PROXIMITY_SYNC = "auto_proximity_sync_enabled"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val prefs: SharedPreferences? by lazy {
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        } catch (_: Exception) {
            null
        }
    }

    private val _trustedDevices = MutableStateFlow<List<TrustedDevice>>(emptyList())
    val trustedDevices: StateFlow<List<TrustedDevice>> = _trustedDevices.asStateFlow()

    private val _autoProximitySyncEnabled = MutableStateFlow(true)
    val autoProximitySyncEnabled: StateFlow<Boolean> = _autoProximitySyncEnabled.asStateFlow()
    val autoProximitySyncFlow: StateFlow<Boolean> get() = autoProximitySyncEnabled

    init {
        yukle()
    }

    private fun yukle() {
        val p = prefs ?: return
        _autoProximitySyncEnabled.value = try {
            p.getBoolean(KEY_AUTO_PROXIMITY_SYNC, true)
        } catch (_: Exception) {
            true
        }
        val raw = try {
            p.getString(KEY_TRUSTED_DEVICES, null)
        } catch (_: Exception) {
            null
        }
        if (!raw.isNullOrBlank()) {
            _trustedDevices.value = try {
                json.decodeFromString<List<TrustedDevice>>(raw)
                    .sortedByDescending { it.lastSyncedAt }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    fun isAutoProximitySyncEnabled(): Boolean = _autoProximitySyncEnabled.value

    fun setAutoProximitySyncEnabled(enabled: Boolean) {
        _autoProximitySyncEnabled.value = enabled
        try {
            prefs?.edit()?.putBoolean(KEY_AUTO_PROXIMITY_SYNC, enabled)?.apply()
        } catch (_: Exception) {}
    }

    fun getTrustedDevices(): List<TrustedDevice> = _trustedDevices.value

    /**
     * Verilen cihazKodu veya Nearby endpointName ("Soru Defterim • Model • 1234")
     * güvenilir cihaz listesinde kayıtlı mı kontrol eder.
     */
    fun isTrusted(cihazKoduOrEndpointName: String): Boolean {
        val sorgu = cihazKoduOrEndpointName.trim()
        if (sorgu.isEmpty()) return false
        val mevcutlar = _trustedDevices.value
        if (mevcutlar.isEmpty()) return false

        val endpointSon4 = if (sorgu.contains("•")) {
            sorgu.substringAfterLast("•").trim()
        } else {
            ""
        }

        return mevcutlar.any { cihaz ->
            val kayitliKod = cihaz.cihazKodu.trim()
            val kayitliSon4 = kayitliKod.takeLast(4)
            when {
                kayitliKod.isNotEmpty() && kayitliKod.equals(sorgu, ignoreCase = true) -> true
                cihaz.cihazAdi.isNotEmpty() && cihaz.cihazAdi.equals(sorgu, ignoreCase = true) -> true
                kayitliSon4.length == 4 && endpointSon4.length == 4 &&
                    kayitliSon4.equals(endpointSon4, ignoreCase = true) -> true
                kayitliSon4.length == 4 && sorgu.endsWith("• $kayitliSon4", ignoreCase = true) -> true
                else -> false
            }
        }
    }

    fun markTrusted(cihazAdiVeyaKodu: String, pinVeyaKod: String = "") {
        addTrustedDevice(cihazKodu = pinVeyaKod, cihazAdi = cihazAdiVeyaKodu)
    }

    /**
     * Başarılı eşleşme veya PIN onayı sonrasında cihazı güvenilir listesine ekler/günceller.
     */
    fun addTrustedDevice(
        cihazKodu: String,
        cihazAdi: String,
        syncedAt: Long = System.currentTimeMillis()
    ) {
        val temizAd = cihazAdi.trim()
        val cikarilanKod = if (cihazKodu.isNotBlank()) {
            cihazKodu.trim()
        } else if (temizAd.contains("•")) {
            temizAd.substringAfterLast("•").trim()
        } else {
            temizAd
        }
        if (cikarilanKod.isEmpty() && temizAd.isEmpty()) return

        val yeniKayit = TrustedDevice(
            cihazKodu = cikarilanKod,
            cihazAdi = temizAd.ifEmpty { "Güvenilir Cihaz ($cikarilanKod)" },
            lastSyncedAt = syncedAt
        )

        val guncelListe = _trustedDevices.value
            .filterNot { mevcut ->
                (mevcut.cihazKodu.isNotEmpty() && mevcut.cihazKodu.equals(cikarilanKod, ignoreCase = true)) ||
                    (cikarilanKod.length >= 4 && mevcut.cihazKodu.takeLast(4).equals(cikarilanKod.takeLast(4), ignoreCase = true))
            }
            .plus(yeniKayit)
            .sortedByDescending { it.lastSyncedAt }

        _trustedDevices.value = guncelListe
        kaydet(guncelListe)
    }

    /**
     * Güvenilir cihazı listeden çıkarır ("Unut").
     */
    fun removeTrustedDevice(cihazKodu: String) {
        val hedef = cihazKodu.trim()
        if (hedef.isEmpty()) return
        val guncelListe = _trustedDevices.value.filterNot {
            it.cihazKodu.equals(hedef, ignoreCase = true) ||
                it.cihazAdi.equals(hedef, ignoreCase = true)
        }
        _trustedDevices.value = guncelListe
        kaydet(guncelListe)
    }

    fun clearAll() {
        _trustedDevices.value = emptyList()
        try {
            prefs?.edit()?.remove(KEY_TRUSTED_DEVICES)?.apply()
        } catch (_: Exception) {}
    }

    private fun kaydet(liste: List<TrustedDevice>) {
        try {
            val encoded = json.encodeToString(liste)
            prefs?.edit()?.putString(KEY_TRUSTED_DEVICES, encoded)?.apply()
        } catch (_: Exception) {}
    }
}
