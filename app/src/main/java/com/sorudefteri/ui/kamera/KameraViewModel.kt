package com.sorudefteri.ui.kamera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sorudefteri.data.local.AyarlarDataStore
import com.sorudefteri.domain.kamera.AlanDengeleyici
import com.sorudefteri.domain.kamera.KirpmaHesaplayici
import com.sorudefteri.domain.kamera.Kutu
import com.sorudefteri.domain.kamera.KutuOrani
import com.sorudefteri.domain.kamera.SoruAlgilayici
import com.sorudefteri.domain.kamera.TarananSatir
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
 *   MetinAnalizci → [onAnalizSonuc] → SoruAlgilayici(.secMetinli) → AlanDengeleyici → [_taramaBilgi]
 *
 * Performans: [yuksekDogrulukAkisi] kapalıysa eski hafif yol ([SoruAlgilayici.sec])
 * çalışır; açıkken metinli + ROI yolu. Yük yalnız kamera ekranı açıkken biner
 * (ViewModel ekranla ölür, analiz durur).
 */
@HiltViewModel
class KameraViewModel @Inject constructor(
    private val ayarlarDataStore: AyarlarDataStore
) : ViewModel() {

    /** "Uzun Pozlama": çek tuşuna basınca birden çok karenin akıllıca birleştirilmesi. */
    val uzunPozlamaAkisi: StateFlow<Boolean> = ayarlarDataStore.kameraUzunPozlamaAkisi.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AyarlarDataStore.DEFAULT_KAMERA_UZUN_POZLAMA
    )

    /** "Otomatik Çekim": soru KILITLI'ya geçince fotoğrafın otomatik çekilmesi (yalnız TEKLİ). */
    val otomatikCekimAkisi: StateFlow<Boolean> = ayarlarDataStore.kameraOtomatikCekimAkisi.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AyarlarDataStore.DEFAULT_KAMERA_OTOMATIK_CEKIM
    )

    /** "Otomatik Soru Algılama": TEKLİ modda soru alanının aranması (kapalıysa analiz durur, statik kılavuz). */
    val otomatikAlgilaAkisi: StateFlow<Boolean> = ayarlarDataStore.kameraOtomatikAlgilaAkisi.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AyarlarDataStore.DEFAULT_KAMERA_OTOMATIK_ALGILA
    )

    /** "Yüksek Doğruluk": 1280x720 + 300ms + metinli tespit + deklanşör refine. */
    val yuksekDogrulukAkisi: StateFlow<Boolean> = ayarlarDataStore.kameraYuksekDogrulukAkisi.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AyarlarDataStore.DEFAULT_KAMERA_YUKSEK_DOGRULUK
    )

    /** "Otomatik Performans": düşük donanımda yüksek doğruluğu otomatik kapatır. */
    val perfOtomatikAkisi: StateFlow<Boolean> = ayarlarDataStore.kameraPerfOtomatikAkisi.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AyarlarDataStore.DEFAULT_KAMERA_PERF_OTOMATIK
    )

    fun setUzunPozlama(acik: Boolean) {
        viewModelScope.launch { ayarlarDataStore.setKameraUzunPozlama(acik) }
    }

    fun setOtomatikCekim(acik: Boolean) {
        viewModelScope.launch { ayarlarDataStore.setKameraOtomatikCekim(acik) }
    }

    fun setOtomatikAlgila(acik: Boolean) {
        viewModelScope.launch { ayarlarDataStore.setKameraOtomatikAlgila(acik) }
        if (!acik) taramayiSifirla()
    }

    fun setYuksekDogruluk(acik: Boolean) {
        viewModelScope.launch { ayarlarDataStore.setKameraYuksekDogruluk(acik) }
        taramayiSifirla()
    }

    fun setPerfOtomatik(acik: Boolean) {
        viewModelScope.launch { ayarlarDataStore.setKameraPerfOtomatik(acik) }
    }

    /** Ekranın tarama bilgisi (alan_normalized + durum + analiz boyutları). */
    data class TaramaBilgi(
        val alan: KutuOrani? = null,
        val durum: TaramaDurumu = TaramaDurumu.ARANIYOR,
        val analizGen: Int = 0,
        val analizYuk: Int = 0
    )

    private val dengeleyici = AlanDengeleyici()

    /** Yüksek doğruluk yolu için canlı ROI (analiz px). Screen'den beslenir. */
    @Volatile
    var yuksekRoi: Kutu? = null

    /** Yüksek doğruluk yolu açık mı? (kontrolcüyle aynı kaynaktan beslenir). */
    @Volatile
    var yuksekAktif: Boolean = false

    /** Üst üste analiz hatası sayacı (model-hazır uyarısı için). */
    private var ustUsteHata = 0

    private val _taramaBilgi = MutableStateFlow(TaramaBilgi())
    val taramaBilgi: StateFlow<TaramaBilgi> = _taramaBilgi.asStateFlow()

    private val _modelUyari = MutableStateFlow(false)
    /** true: ML Kit art arda başarısız → model iniyor olabilir (UI uyarısı). */
    val modelUyari: StateFlow<Boolean> = _modelUyari.asStateFlow()

    /**
     * MetinAnalizci çağrısı. [satirlar] ML Kit upright koordinatlarında metinli satırlardır;
     * [analizGen]/[analizYuk] dik çözümlenmiş analiz görüntüsü boyutları.
     */
    fun onAnalizSonuc(satirlar: List<TarananSatir>, analizGen: Int, analizYuk: Int) {
        ustUsteHata = 0
        if (_modelUyari.value) _modelUyari.value = false
        val aday = try {
            if (yuksekAktif) {
                SoruAlgilayici.secMetinli(satirlar, analizGen, analizYuk, roi = yuksekRoi)
            } else {
                SoruAlgilayici.sec(satirlar.map { it.kutu }, analizGen, analizYuk)
            }
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

    /** Analiz başarısızlığı (model iniyor / tanınamadı olabilir). */
    fun onAnalizHata() {
        ustUsteHata++
        if (ustUsteHata >= 3) _modelUyari.value = true
    }

    /** "Yeniden tara" ya da mod/lens değişimi. */
    fun taramayiSifirla() {
        dengeleyici.sifirla()
        _taramaBilgi.value = _taramaBilgi.value.copy(alan = null, durum = TaramaDurumu.ARANIYOR)
    }
}
