package com.sorudefteri.ui.kamera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sorudefteri.data.local.AyarlarDataStore
import com.sorudefteri.domain.kamera.AlanDengeleyici
import com.sorudefteri.domain.kamera.KirpmaHesaplayici
import com.sorudefteri.domain.kamera.Kutu
import com.sorudefteri.domain.kamera.KutuOrani
import com.sorudefteri.domain.kamera.SoruAlgilayici
import com.sorudefteri.domain.kamera.TaramaDurumu
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Kamera ekranı ViewModel'i: ayarlar (DataStore kalıcı) + tekli mod tarama durumu.
 *
 * Tarama akışı (kamera executor iş parçacığından çağırılabilir, StateFlow güvenli):
 *   MetinAnalizci → [onAnalizSonuc] → SoruAlgilayici → AlanDengeleyici → [_taramaBilgi]
 */
@HiltViewModel
class KameraViewModel @Inject constructor(
    private val ayarlarDataStore: AyarlarDataStore
) : ViewModel() {

    /** "Uzun Pozlama": çek tuşuna basınca birden çok karenin akıllıca birleştirilmesi. */
    val uzunPozlamaAkisi: StateFlow<Boolean> = ayarlarDataStore.kameraUzunPozlamaAkisi.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AyarlarDataStore.DEFAULT_KAMERA_UZUN_POZLAMA
    )

    /** "Otomatik Çekim": soru KILITLI'ya geçince fotoğrafın otomatik çekilmesi (yalnız TEKLİ). */
    val otomatikCekimAkisi: StateFlow<Boolean> = ayarlarDataStore.kameraOtomatikCekimAkisi.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AyarlarDataStore.DEFAULT_KAMERA_OTOMATIK_CEKIM
    )

    fun setUzunPozlama(acik: Boolean) {
        viewModelScope.launch { ayarlarDataStore.setKameraUzunPozlama(acik) }
    }

    fun setOtomatikCekim(acik: Boolean) {
        viewModelScope.launch { ayarlarDataStore.setKameraOtomatikCekim(acik) }
    }

    /** Ekranın tarama bilgisi (alan_normalized + durum + analiz boyutları). */
    data class TaramaBilgi(
        val alan: KutuOrani? = null,
        val durum: TaramaDurumu = TaramaDurumu.ARANIYOR,
        val analizGen: Int = 0,
        val analizYuk: Int = 0
    )

    private val dengeleyici = AlanDengeleyici()

    private val _taramaBilgi = MutableStateFlow(TaramaBilgi())
    val taramaBilgi: StateFlow<TaramaBilgi> = _taramaBilgi.asStateFlow()

    /**
     * MetinAnalizci çağrısı. [satirlar] ML Kit upright koordinatlarında satır kutularıdır;
     * [analizGen]/[analizYuk] dik çözümlenmiş analiz görüntüsü boyutları.
     */
    fun onAnalizSonuc(satirlar: List<Kutu>, analizGen: Int, analizYuk: Int) {
        val aday = try {
            SoruAlgilayici.sec(satirlar, analizGen, analizYuk)
        } catch (t: Throwable) {
            null
        }
        val oran = aday?.let { KirpmaHesaplayici.kutuyuOrana(it, analizGen, analizYuk) }
        val sonuc = dengeleyici.isle(oran, System.currentTimeMillis())
        _taramaBilgi.value = TaramaBilgi(
            alan = sonuc.alan,
            durum = sonuc.durum,
            analizGen = analizGen,
            analizYuk = analizYuk
        )
    }

    /** "Yeniden tara" ya da mod/lens değişimi. */
    fun taramayiSifirla() {
        dengeleyici.sifirla()
        _taramaBilgi.value = _taramaBilgi.value.copy(alan = null, durum = TaramaDurumu.ARANIYOR)
    }
}
