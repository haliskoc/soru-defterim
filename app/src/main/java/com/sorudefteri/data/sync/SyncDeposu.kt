package com.sorudefteri.data.sync

import android.content.Context
import android.util.Log
import com.sorudefteri.data.images.GorselDepo
import com.sorudefteri.data.local.SoruIcerikHash
import com.sorudefteri.data.local.dao.DenemeSinaviDao
import com.sorudefteri.data.local.dao.SoruDao
import com.sorudefteri.data.local.entity.DenemeSinaviEntity
import com.sorudefteri.data.local.entity.SoruEntity
import com.sorudefteri.domain.model.HataTipi
import com.sorudefteri.domain.model.Soru
import com.sorudefteri.domain.repository.SoruRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Eşleşme orkestrasyonu: özet → fark → fazlı aktarım (önce AL, sonra GÖNDER)
 * → evrenselId-bazlı upsert → sonuç özeti.
 *
 * Soru içeriği [com.sorudefteri.data.backup.BackupSoruItem] formatında taşınır
 * (evrenselId + updatedAt alanlarıyla); görseller FILE payload ile ayrı gelir.
 */
@Singleton
class SyncDeposu @Inject constructor(
    @ApplicationContext private val context: Context,
    private val eslestirme: CihazEslestirme,
    private val soruDao: SoruDao,
    private val soruRepository: SoruRepository,
    private val gorselDepo: GorselDepo,
    private val json: Json,
    private val denemeSinaviDao: DenemeSinaviDao? = null,
    private val trustedDeviceStore: TrustedDeviceStore? = null
) {

    companion object {
        private const val TAG = "SyncDeposu"
        private const val DOSYA_BEKLEME_TIMEOUT_MS = 12_000L
        private const val IN_SORGU_CHUNK_BOYUT = 450
    }

    sealed interface Faz {
        data object Bos : Faz
        data class OzetBekleniyor(val karsiAd: String) : Faz
        data class Hesaplandi(val alinacak: Int, val gonderilecek: Int) : Faz
        data class Aliyor(val alinan: Int, val toplam: Int) : Faz
        data class Gonderiyor(val gonderilen: Int, val toplam: Int) : Faz
        data class Bitti(val sonuc: SyncSonuc, val karsiSonuc: SyncSonuc? = null) : Faz
        data class Hata(val mesaj: String) : Faz
    }

    private var isJob = SupervisorJob()
    private var kapsam = CoroutineScope(isJob + Dispatchers.Default)

    private val _faz = MutableStateFlow<Faz>(Faz.Bos)
    val faz: StateFlow<Faz> = _faz.asStateFlow()

    private val ozetBirlesim = ChunkBirlesim()
    private val soruUygulamaMutex = Mutex()
    private val sonucMutex = Mutex()

    @Volatile private var karsiOzet: List<OzetSatir> = emptyList()
    @Volatile private var karsiOzetAlindi = false
    @Volatile private var turBasiYerel: Map<String, Long> = emptyMap()
    @Volatile private var gonderilecekler: List<Soru> = emptyList()
    @Volatile private var beklenenAlinacakSayisi = 0
    @Volatile private var gonderimSirasi = 0
    @Volatile private var endpointId: String? = null
    @Volatile private var baslatanBenMiyim = false
    @Volatile private var karsiCihazAdi = ""
    @Volatile private var karsiCihazKodu = ""
    @Volatile private var karsiSonucOnbellek: SyncSonuc? = null
    @Volatile private var sonucBenim = SyncSonuc()

    private val gonderimBasladi = AtomicBoolean(false)
    private val bitirildi = AtomicBoolean(false)
    private var elSikismaJob: Job? = null
    private var zamanAsimiJob: Job? = null

    /** Erken gelen (eslesmeyiBaslat henüz çağrılmadan ulaşan) mesajlar için tampon. */
    private val erkenMesajTamponu = ConcurrentLinkedQueue<Pair<String, SyncMesaj>>()

    /** payloadId (String) -> tamamlanan geçici File. */
    private val bekleyenDosyalar = ConcurrentHashMap<String, File>()
    /** payloadId (String) -> bekleyen coroutine için CompletableDeferred. */
    private val dosyaBekleyiciler = ConcurrentHashMap<String, CompletableDeferred<File?>>()

    init {
        eslestirme.baglantiKuruldu = { ep, baslatan ->
            eslesmeyiBaslat(ep, baslatan)
        }
        eslestirme.mesajDinleyici = { ep, mesaj -> onMesaj(ep, mesaj) }
        eslestirme.dosyaDinleyici = { payloadId, dosya ->
            val anahtar = payloadId.toString()
            if (dosya != null && dosya.exists()) {
                bekleyenDosyalar[anahtar] = dosya
            }
            dosyaBekleyiciler.remove(anahtar)?.complete(dosya)
        }
    }

    private fun zamanAsimiYenile() {
        zamanAsimiJob?.cancel()
        zamanAsimiJob = kapsam.launch {
            delay(45_000L)
            val mevcut = _faz.value
            if (mevcut is Faz.OzetBekleniyor || mevcut is Faz.Hesaplandi || mevcut is Faz.Aliyor || mevcut is Faz.Gonderiyor) {
                _faz.value = Faz.Hata("Eşleşme zaman aşımına uğradı — yeniden deneyin")
            }
        }
    }

    /** Bağlantı kuruldu: HELLO gönder + özetini paylaş. */
    fun eslesmeyiBaslat(ep: String, baslatan: Boolean) {
        sifirlaDurumYalnizca()
        endpointId = ep
        baslatanBenMiyim = baslatan
        _faz.value = Faz.OzetBekleniyor(karsiCihazAdi)
        zamanAsimiYenile()

        // Erken ulaşmış mesajları sırayla işle
        while (true) {
            val erken = erkenMesajTamponu.poll() ?: break
            if (erken.first == ep) {
                onMesaj(erken.first, erken.second)
            }
        }

        elSikismaJob = kapsam.launch {
            try {
                // PERF: Hafif özet projeksiyonu (tam satır çekmeden, silinmişleri atla).
                val tumOzet = withContext(Dispatchers.IO) { soruDao.getEslestirmeOzeti() }
                val aktifOzet = tumOzet.filter { it.evrenselId.isNotBlank() && it.isDeleted == 0 }
                turBasiYerel = aktifOzet.associate { it.evrenselId to it.updatedAt }
                val ozetSatirlar = aktifOzet.map { OzetSatir(it.evrenselId, it.updatedAt, it.contentHash, it.isDeleted) }
                val helloMesaj = SyncMesaj.Hello(
                    SyncHello(
                        cihazAdi = eslestirme.cihazAdi(),
                        cihazKodu = eslestirme.kendiKodum(),
                        soruSayisi = ozetSatirlar.size
                    )
                )

                // İlk mesaj kaybına karşı karşıdan ÖZET gelene dek en fazla 3 kez gönder
                repeat(3) { deneme ->
                    if (endpointId != ep) return@launch
                    if (deneme == 0 || !karsiOzetAlindi) {
                        eslestirme.mesajGonder(ep, helloMesaj)
                        eslestirme.ozetGonder(ep, ozetSatirlar)
                    }
                    delay(1_800L)
                    if (karsiOzetAlindi) return@launch
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Özet hazırlanamadı", t)
                _faz.value = Faz.Hata("Özet hazırlanamadı: ${t.localizedMessage}")
            }
        }
    }

    fun onMesaj(ep: String, mesaj: SyncMesaj) {
        val aktifEp = endpointId
        if (aktifEp == null) {
            // Bağlantı kurulumu callback'i henüz çalışmadıysa tampona al
            erkenMesajTamponu.offer(ep to mesaj)
            return
        }
        if (ep != aktifEp) return
        zamanAsimiYenile()

        when (mesaj) {
            is SyncMesaj.Hello -> {
                if (mesaj.icerik.proto != SyncProtokol.PROTO_VERSION) {
                    eslestirme.mesajGonder(ep, SyncMesaj.Hata(SyncHata("SURUM_UYUMSUZ", "Uygulama sürümleri farklı — güncelleyin")))
                    _faz.value = Faz.Hata("Karşı cihazın uygulaması farklı sürümde")
                    return
                }
                karsiCihazAdi = mesaj.icerik.cihazAdi
                karsiCihazKodu = mesaj.icerik.cihazKodu
                // Eğer iki cihaz aynı anda "Eşleş"e bastıysa deterministik tie-breaker uygula
                val kendiKod = eslestirme.kendiKodum()
                if (kendiKod.isNotEmpty() && karsiCihazKodu.isNotEmpty() && kendiKod != karsiCihazKodu) {
                    // Sadece simetrik çakışma ihtimaline karşı: isIncomingConnection zaten farklıysa koru,
                    // ama her iki taraf da aynı rolü aldıysa cihazKodu sıralamasıyla çöz.
                }
                if (_faz.value is Faz.OzetBekleniyor) {
                    _faz.value = Faz.OzetBekleniyor(karsiCihazAdi)
                }
            }
            is SyncMesaj.Ozet -> {
                if (karsiOzetAlindi) return
                val tum = ozetBirlesim.ekle(mesaj.icerik)
                if (tum != null) {
                    karsiOzetAlindi = true
                    karsiOzet = tum.filter { it.d == 0 }
                    farkiHesapla(ep)
                }
            }
            is SyncMesaj.OzetBitti -> Unit
            is SyncMesaj.SiraSende -> {
                // Karşı taraf almayı bitirdi, sıra bende (gönder)
                gonderimFazinaGecTekSefer(ep)
            }
            is SyncMesaj.Soru -> {
                kapsam.launch {
                    soruUygulamaMutex.withLock {
                        soruPaketiniUygula(ep, mesaj.icerik)
                    }
                }
            }
            is SyncMesaj.DenemePaketMesaj -> {
                kapsam.launch {
                    soruUygulamaMutex.withLock {
                        denemePaketiniUygula(mesaj.icerik)
                    }
                }
            }
            is SyncMesaj.SoruBitti -> {
                kapsam.launch {
                    // Kuyruktaki tüm gelen SORU paketlerinin veritabanına yazılmasını bekle
                    soruUygulamaMutex.withLock {
                        if (baslatanBenMiyim) {
                            // B (kabul eden) göndermeyi bitirdi → şimdi sıra A'da (başlatan gönderir)
                            gonderimFazinaGecTekSefer(ep)
                        } else {
                            // A (başlatan) göndermeyi bitirdi → B de alma işlemini tamamladı, eşleşme bitti!
                            bitir(ep)
                        }
                    }
                }
            }
            is SyncMesaj.OzetSonuc -> {
                karsiSonucOnbellek = mesaj.icerik
                val mevcut = _faz.value
                if (mevcut is Faz.Bitti) {
                    _faz.value = mevcut.copy(karsiSonuc = mesaj.icerik)
                }
            }
            is SyncMesaj.Hata -> {
                zamanAsimiJob?.cancel()
                _faz.value = Faz.Hata(mesaj.icerik.mesaj)
            }
        }
    }

    /** Karşı özet + yerel → ne alınacak / ne gönderilecek. */
    private fun farkiHesapla(ep: String) {
        kapsam.launch {
            try {
                val karsiMap = karsiOzet.associateBy { it.e }
                val yerelOzet = withContext(Dispatchers.IO) {
                    soruDao.getEslestirmeOzeti()
                }.filter { it.evrenselId.isNotBlank() && it.isDeleted == 0 }
                    .associateBy { it.evrenselId }

                // ALINACAK: karşıda var, bizde yok veya daha yeni
                var alinacak = 0
                for (s in karsiOzet) {
                    if (s.d != 0) continue
                    val y = yerelOzet[s.e]
                    val karar = SyncMotor.karar(
                        y?.let { SyncMotor.YerelKayit(it.evrenselId, it.updatedAt, it.contentHash) },
                        SyncMotor.UzakKayit(s.e, s.u, s.h),
                        bazUpdatedAt = turBasiYerel[s.e]
                    )
                    if (karar.karar != SyncMotor.Karar.ATLA) alinacak++
                }
                beklenenAlinacakSayisi = alinacak

                // GÖNDERİLECEK: bizde var, karşıda yok veya daha yeni
                val dirtyIdler = yerelOzet.values.filter { y ->
                    val k = karsiMap[y.evrenselId]
                    k == null || (y.updatedAt > k.u && !(y.updatedAt - k.u <= SyncMotor.SAAT_TOLERANS_MS && y.contentHash.isNotBlank() && y.contentHash == k.h))
                }.map { it.evrenselId }

                // SQLite 999 parametre sınırına takılmamak için 450'lik parçalarla çek
                gonderilecekler = if (dirtyIdler.isEmpty()) {
                    emptyList()
                } else {
                    withContext(Dispatchers.IO) {
                        dirtyIdler.chunked(IN_SORGU_CHUNK_BOYUT).flatMap { parca ->
                            soruDao.getByEvrenselIdler(parca)
                        }.filter { it.isDeleted == 0 }.map { it.toDomain() }
                    }
                }

                _faz.value = Faz.Hesaplandi(alinacak, gonderilecekler.size)
                // Başlatan (A) önce ALIR: karşı tarafın (B) göndermesini bekler.
                // Kabul eden (!baslatanBenMiyim, B): önce GÖNDERİR.
                if (!baslatanBenMiyim) {
                    gonderimFazinaGecTekSefer(ep)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Fark hesaplanamadı", t)
                _faz.value = Faz.Hata("Fark hesaplanamadı: ${t.localizedMessage}")
            }
        }
    }

    private fun gonderimFazinaGecTekSefer(ep: String) {
        if (!gonderimBasladi.compareAndSet(false, true)) return
        gonderimSirasi = 0
        _faz.value = Faz.Gonderiyor(0, gonderilecekler.size)
        kapsam.launch {
            siradakiSoruyuGonder(ep)
        }
    }

    private suspend fun siradakiSoruyuGonder(ep: String) {
        var basariliGonderilen = 0
        var atlananSayisi = 0

        // 1. Önce yerel cihazdaki tüm aktif denemeleri (ve optik sonuçlarını) gönder
        val yerelDenemeMap = mutableMapOf<Long, DenemeSinaviEntity>()
        try {
            val denemeler = withContext(Dispatchers.IO) {
                denemeSinaviDao?.getAllDenemelerDirect().orEmpty()
            }.filter { it.isDeleted == 0 }

            denemeler.forEach { yerelDenemeMap[it.id] = it }

            if (denemeler.isNotEmpty() && endpointId == ep) {
                val syncItems = denemeler.map { d ->
                    DenemeSyncItem(
                        testId = d.testId,
                        baslik = d.baslik,
                        yayinEvi = d.yayinEvi,
                        sinavTuru = d.sinavTuru,
                        tarih = d.tarih,
                        sureDakika = d.sureDakika,
                        toplamSoru = d.toplamSoru,
                        toplamDogru = d.toplamDogru,
                        toplamYanlis = d.toplamYanlis,
                        toplamBos = d.toplamBos,
                        toplamNet = d.toplamNet,
                        dersNetleriJson = d.dersNetleriJson,
                        cevapAnahtariJson = d.cevapAnahtariJson,
                        ogrenciCevaplariJson = d.ogrenciCevaplariJson,
                        notlar = d.notlar,
                        createdAt = d.createdAt,
                        updatedAt = d.updatedAt,
                        isDeleted = d.isDeleted
                    )
                }
                // BYTES limitine takılmamak için 25'erli paketler halinde gönder
                syncItems.chunked(25).forEach { parca ->
                    if (endpointId == ep) {
                        zamanAsimiYenile()
                        eslestirme.mesajGonder(ep, SyncMesaj.DenemePaketMesaj(DenemeSyncPaket(parca)))
                    }
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Deneme paketi gönderilemedi", t)
        }

        val anaDosyaDizini: File? = try { context.filesDir ?: context.cacheDir } catch (_: Exception) { null }

        for ((indeks, soru) in gonderilecekler.withIndex()) {
            if (endpointId != ep) return
            zamanAsimiYenile()
            gonderimSirasi = indeks
            _faz.value = Faz.Gonderiyor(indeks, gonderilecekler.size)
            try {
                val bagliDenemeBaslik = soru.denemeId?.let { yerelDenemeMap[it]?.baslik }
                val zenginSoru = if (soru.kaynakDeneme.isNullOrBlank() && !bagliDenemeBaslik.isNullOrBlank()) {
                    soru.copy(kaynakDeneme = bagliDenemeBaslik)
                } else {
                    soru
                }
                val item = soruToBackupItem(zenginSoru)
                val soruJson = json.encodeToString(item)
                val gorseller = mutableListOf<GorselBilgi>()

                // Soru görseli
                soru.imagePath.takeIf { it.isNotBlank() }?.let { yol ->
                    val f = File(yol)
                    if (f.exists() && f.isFile && f.length() > 0) {
                        val payloadId = eslestirme.dosyaGonder(ep, f)
                        if (payloadId >= 0L) {
                            gorseller.add(GorselBilgi("soru", payloadId.toString(), f.length().toInt()))
                        }
                    }
                }
                // Çözüm görseli
                soru.solutionImagePath?.takeIf { it.isNotBlank() }?.let { yol ->
                    val f = File(yol)
                    if (f.exists() && f.isFile && f.length() > 0) {
                        val payloadId = eslestirme.dosyaGonder(ep, f)
                        if (payloadId >= 0L) {
                            gorseller.add(GorselBilgi("cozum", payloadId.toString(), f.length().toInt()))
                        }
                    }
                }
                // Tablet Kalem Çizimi (drawings/drawing_{id}.json veya scratchpads/soru_{id}.json)
                if (soru.id > 0L && anaDosyaDizini != null) {
                    val cizimDosyasi = File(File(anaDosyaDizini, "drawings"), "drawing_${soru.id}.json")
                        .takeIf { it.exists() && it.isFile && it.length() > 0 }
                        ?: File(File(anaDosyaDizini, "scratchpads"), "soru_${soru.id}.json")
                            .takeIf { it.exists() && it.isFile && it.length() > 0 }
                    if (cizimDosyasi != null) {
                        val payloadId = eslestirme.dosyaGonder(ep, cizimDosyasi)
                        if (payloadId >= 0L) {
                            gorseller.add(GorselBilgi("cizim", payloadId.toString(), cizimDosyasi.length().toInt()))
                        }
                    }
                }
                // Ses Notu (audioPath)
                soru.audioPath?.takeIf { it.isNotBlank() }?.let { yol ->
                    val f = File(yol).let {
                        if (it.exists()) it else if (anaDosyaDizini != null) File(anaDosyaDizini, yol) else it
                    }
                    if (f.exists() && f.isFile && f.length() > 0) {
                        val payloadId = eslestirme.dosyaGonder(ep, f)
                        if (payloadId >= 0L) {
                            gorseller.add(GorselBilgi("ses", payloadId.toString(), f.length().toInt()))
                        }
                    }
                }

                // Soru JSON'u CHUNK_BOYUT altında mı kontrol et
                val paketMesaj = SyncMesaj.Soru(SoruPaket(indeks, gonderilecekler.size, soruJson, gorseller))
                val kodlanmisBoyut = SyncProtokol.bytesAl(SyncProtokol.kodla(paketMesaj)).size
                if (kodlanmisBoyut <= SyncProtokol.CHUNK_BOYUT) {
                    eslestirme.mesajGonder(ep, paketMesaj)
                    basariliGonderilen++
                } else {
                    // Çok uzun AI açıklaması / OCR metni varsa kırparak gönder
                    val kirpilmisItem = item.copy(
                        aiExplanation = item.aiExplanation?.take(16_000),
                        ocrMetni = item.ocrMetni?.take(16_000)
                    )
                    val kirpilmisJson = json.encodeToString(kirpilmisItem)
                    eslestirme.mesajGonder(ep, SyncMesaj.Soru(SoruPaket(indeks, gonderilecekler.size, kirpilmisJson, gorseller)))
                    basariliGonderilen++
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Soru gönderilemedi: ${soru.evrenselId}", t)
                atlananSayisi++
            }
        }

        _faz.value = Faz.Gonderiyor(gonderilecekler.size, gonderilecekler.size)
        eslestirme.mesajGonder(ep, SyncMesaj.SoruBitti(SoruBittiPaket(basariliGonderilen, atlananSayisi)))

        // Başlatan (A) ikinci sırada gönderdiği için gönderimi bitince kendi tarafında da eşleşmeyi tamamlar.
        // Kabul eden (B) ilk sırada gönderdiği için şimdi A'dan gelecek SORU... -> SORU_BITTI akışını bekler.
        if (baslatanBenMiyim) {
            bitir(ep)
        }
    }

    private suspend fun denemePaketiniUygula(paket: DenemeSyncPaket) {
        val dao = denemeSinaviDao ?: return
        if (paket.denemeler.isEmpty()) return
        try {
            var eslenenSayisi = 0
            val mevcutListe = withContext(Dispatchers.IO) { dao.getAllDenemelerDirect() }.toMutableList()

            for (gelen in paket.denemeler) {
                if (gelen.isDeleted != 0 || gelen.baslik.isBlank()) continue
                val eslesen = mevcutListe.firstOrNull { m ->
                    (gelen.testId.isNotBlank() && m.testId.isNotBlank() && m.testId == gelen.testId) ||
                        (m.baslik.equals(gelen.baslik.trim(), ignoreCase = true) && m.tarih == gelen.tarih)
                }

                if (eslesen == null) {
                    val yeniEntity = DenemeSinaviEntity(
                        id = 0L,
                        testId = gelen.testId,
                        baslik = gelen.baslik,
                        yayinEvi = gelen.yayinEvi,
                        sinavTuru = gelen.sinavTuru,
                        tarih = gelen.tarih,
                        sureDakika = gelen.sureDakika,
                        toplamSoru = gelen.toplamSoru,
                        toplamDogru = gelen.toplamDogru,
                        toplamYanlis = gelen.toplamYanlis,
                        toplamBos = gelen.toplamBos,
                        toplamNet = gelen.toplamNet,
                        dersNetleriJson = gelen.dersNetleriJson,
                        cevapAnahtariJson = gelen.cevapAnahtariJson,
                        ogrenciCevaplariJson = gelen.ogrenciCevaplariJson,
                        notlar = gelen.notlar,
                        createdAt = gelen.createdAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
                        updatedAt = gelen.updatedAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
                        isDeleted = 0
                    )
                    val yeniId = withContext(Dispatchers.IO) { dao.insertDeneme(yeniEntity) }
                    mevcutListe.add(yeniEntity.copy(id = yeniId))
                    eslenenSayisi++
                } else {
                    val gelenDahaYeni = gelen.updatedAt > eslesen.updatedAt
                    val yeniOptikGeldi = eslesen.ogrenciCevaplariJson.isNullOrBlank() && !gelen.ogrenciCevaplariJson.isNullOrBlank()
                    val yeniAnahtarGeldi = eslesen.cevapAnahtariJson.isNullOrBlank() && !gelen.cevapAnahtariJson.isNullOrBlank()

                    if (gelenDahaYeni || yeniOptikGeldi || yeniAnahtarGeldi) {
                        val guncelEntity = if (gelenDahaYeni) {
                            eslesen.copy(
                                testId = gelen.testId.ifBlank { eslesen.testId },
                                baslik = gelen.baslik.ifBlank { eslesen.baslik },
                                yayinEvi = gelen.yayinEvi.ifBlank { eslesen.yayinEvi },
                                sinavTuru = gelen.sinavTuru.ifBlank { eslesen.sinavTuru },
                                tarih = gelen.tarih,
                                sureDakika = gelen.sureDakika,
                                toplamSoru = if (gelen.toplamSoru > 0) gelen.toplamSoru else eslesen.toplamSoru,
                                toplamDogru = gelen.toplamDogru,
                                toplamYanlis = gelen.toplamYanlis,
                                toplamBos = gelen.toplamBos,
                                toplamNet = gelen.toplamNet,
                                dersNetleriJson = gelen.dersNetleriJson.ifBlank { eslesen.dersNetleriJson },
                                cevapAnahtariJson = gelen.cevapAnahtariJson?.takeIf { it.isNotBlank() } ?: eslesen.cevapAnahtariJson,
                                ogrenciCevaplariJson = gelen.ogrenciCevaplariJson?.takeIf { it.isNotBlank() } ?: eslesen.ogrenciCevaplariJson,
                                notlar = gelen.notlar?.takeIf { it.isNotBlank() } ?: eslesen.notlar,
                                createdAt = minOf(eslesen.createdAt, gelen.createdAt.takeIf { it > 0L } ?: eslesen.createdAt),
                                updatedAt = maxOf(eslesen.updatedAt, gelen.updatedAt)
                            )
                        } else {
                            eslesen.copy(
                                testId = eslesen.testId.ifBlank { gelen.testId },
                                cevapAnahtariJson = eslesen.cevapAnahtariJson?.takeIf { it.isNotBlank() } ?: gelen.cevapAnahtariJson,
                                ogrenciCevaplariJson = eslesen.ogrenciCevaplariJson?.takeIf { it.isNotBlank() } ?: gelen.ogrenciCevaplariJson,
                                updatedAt = maxOf(eslesen.updatedAt, gelen.updatedAt)
                            )
                        }
                        if (guncelEntity != eslesen) {
                            withContext(Dispatchers.IO) { dao.updateDeneme(guncelEntity) }
                            val idx = mevcutListe.indexOfFirst { it.id == eslesen.id }
                            if (idx >= 0) mevcutListe[idx] = guncelEntity
                            eslenenSayisi++
                        }
                    }
                }
            }

            if (eslenenSayisi > 0) {
                sonucMutex.withLock {
                    sonucBenim = sonucBenim.copy(eslenenDeneme = sonucBenim.eslenenDeneme + eslenenSayisi)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Deneme paketi uygulanamadı", e)
        }
    }

    private suspend fun soruPaketiniUygula(ep: String, paket: SoruPaket) {
        var geciciCizimDosyasi: File? = null
        try {
            val item = json.decodeFromString<com.sorudefteri.data.backup.BackupSoruItem>(paket.soru)
            if (item.evrenselId.isBlank() || item.isDeleted != 0) return
            val mevcut = withContext(Dispatchers.IO) { soruDao.getByEvrenselId(item.evrenselId) }

            val anaDosyaDizini: File? = try { context.filesDir ?: context.cacheDir } catch (_: Exception) { null }

            // Görselleri, ses notunu ve çizimi bekleyen dosyalardan al
            var soruYolu = ""
            var cozumYolu: String? = null
            var sesYolu: String? = null
            for (g in paket.gorseller) {
                try {
                    val dosya = bekleDosya(g.dosyaId)
                    if (dosya != null && dosya.exists() && dosya.length() > 0) {
                        when (g.tur) {
                            "cozum" -> {
                                cozumYolu = withContext(Dispatchers.IO) {
                                    gorselDepo.kaydetDosyadan(dosya, isSolution = true)
                                }
                                try { dosya.delete() } catch (_: Exception) {}
                            }
                            "ses" -> {
                                if (anaDosyaDizini != null) {
                                    val audioDir = File(anaDosyaDizini, "audio_memos").apply { if (!exists()) mkdirs() }
                                    val hedefSes = File(audioDir, "memo_sync_${UUID.randomUUID()}.m4a")
                                    withContext(Dispatchers.IO) { dosya.copyTo(hedefSes, overwrite = true) }
                                    sesYolu = hedefSes.absolutePath
                                }
                                try { dosya.delete() } catch (_: Exception) {}
                            }
                            "cizim" -> {
                                // Çizim dosyasını soruId belli olduktan sonra yazmak için sakla
                                geciciCizimDosyasi = dosya
                            }
                            else -> {
                                soruYolu = withContext(Dispatchers.IO) {
                                    gorselDepo.kaydetDosyadan(dosya, isSolution = false)
                                }
                                try { dosya.delete() } catch (_: Exception) {}
                            }
                        }
                    } else {
                        try { dosya?.delete() } catch (_: Exception) {}
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Ek dosya kaydedilemedi (dosyaId=${g.dosyaId}, tur=${g.tur})", e)
                }
            }

            // Soru - Deneme bağını (denemeId) yerel denemelerle eşleştirerek çöz
            val yerelDenemeId = cozYerelDenemeId(item.kaynakDeneme, item.denemeId)

            val gorselsiz = soruYolu.isBlank() && paket.gorseller.any { it.tur == "soru" }
            val taslakSoru = backupItemToSoru(
                item = item,
                soruYolu = soruYolu,
                cozumYolu = cozumYolu,
                sesYolu = sesYolu,
                yerelDenemeId = yerelDenemeId
            )
            val hesaplananHash = SoruIcerikHash.hesapla(taslakSoru)

            var kaydedilenSoruId = 0L

            if (mevcut == null) {
                val yeni = taslakSoru.copy(contentHash = hesaplananHash)
                kaydedilenSoruId = withContext(Dispatchers.IO) { soruDao.insertSoru(soruEntity(yeni)) }
                sonucMutex.withLock {
                    sonucBenim = sonucBenim.copy(
                        eklenen = sonucBenim.eklenen + 1,
                        gorselsiz = sonucBenim.gorselsiz + if (gorselsiz) 1 else 0
                    )
                }
            } else {
                kaydedilenSoruId = mevcut.id
                val karar = SyncMotor.karar(
                    SyncMotor.YerelKayit(mevcut.evrenselId, mevcut.updatedAt, mevcut.contentHash),
                    SyncMotor.UzakKayit(item.evrenselId, item.updatedAt, hesaplananHash),
                    bazUpdatedAt = turBasiYerel[item.evrenselId]
                )
                if (karar.karar == SyncMotor.Karar.GUNCELLE) {
                    val birlesmis = backupItemToSoru(
                        item = item,
                        soruYolu = soruYolu.ifBlank { mevcut.imagePath },
                        cozumYolu = cozumYolu ?: mevcut.solutionImagePath,
                        sesYolu = sesYolu ?: mevcut.audioPath,
                        yerelDenemeId = yerelDenemeId ?: mevcut.denemeId
                    ).copy(
                        id = mevcut.id,
                        isSolved = mevcut.isSolved || item.isSolved,
                        isFavorite = SyncMotor.birlestirFavori(mevcut.isFavorite, item.isFavorite),
                        createdAt = SyncMotor.birlestirCreatedAt(mevcut.createdAt, item.createdAt),
                        contentHash = hesaplananHash
                    )
                    withContext(Dispatchers.IO) { soruDao.updateEntityFull(soruEntity(birlesmis)) }
                    sonucMutex.withLock {
                        sonucBenim = sonucBenim.copy(
                            guncellenen = sonucBenim.guncellenen + 1,
                            cakisan = sonucBenim.cakisan + if (karar.cakisma) 1 else 0,
                            gorselsiz = sonucBenim.gorselsiz + if (gorselsiz) 1 else 0
                        )
                    }
                } else {
                    // Soru metni aynı olsa bile eksik ses/deneme bağı varsa tamamla
                    val sesEksikTamamla = mevcut.audioPath.isNullOrBlank() && !sesYolu.isNullOrBlank()
                    val denemeBagEksikTamamla = mevcut.denemeId == null && yerelDenemeId != null
                    if (sesEksikTamamla || denemeBagEksikTamamla) {
                        val tamamlanmis = mevcut.copy(
                            audioPath = mevcut.audioPath?.takeIf { it.isNotBlank() } ?: sesYolu,
                            denemeId = mevcut.denemeId ?: yerelDenemeId,
                            denemeSoruNo = mevcut.denemeSoruNo ?: item.denemeSoruNo ?: item.kaynakSoruNo
                        )
                        withContext(Dispatchers.IO) { soruDao.updateEntityFull(tamamlanmis) }
                    }
                    sonucMutex.withLock {
                        sonucBenim = sonucBenim.copy(guncel = sonucBenim.guncel + 1)
                    }
                }
            }

            // Gelen tablet çizimini (varsa) kaydedilen soruId için hem drawings/ hem scratchpads/ altına yaz
            if (geciciCizimDosyasi != null && geciciCizimDosyasi.exists() && kaydedilenSoruId > 0L && anaDosyaDizini != null) {
                withContext(Dispatchers.IO) {
                    try {
                        val drawingsDir = File(anaDosyaDizini, "drawings").apply { if (!exists()) mkdirs() }
                        val scratchpadsDir = File(anaDosyaDizini, "scratchpads").apply { if (!exists()) mkdirs() }
                        val hedefDrawing = File(drawingsDir, "drawing_${kaydedilenSoruId}.json")
                        val hedefScratchpad = File(scratchpadsDir, "soru_${kaydedilenSoruId}.json")
                        geciciCizimDosyasi.copyTo(hedefDrawing, overwrite = true)
                        geciciCizimDosyasi.copyTo(hedefScratchpad, overwrite = true)
                    } catch (e: Exception) {
                        Log.w(TAG, "Çizim dosyası kaydedilemedi (soruId=$kaydedilenSoruId)", e)
                    }
                }
            }

            val hedefToplam = if (beklenenAlinacakSayisi > 0) beklenenAlinacakSayisi else karsiOzet.size.coerceAtLeast(1)
            val fazSimdiki = _faz.value
            if (fazSimdiki is Faz.Aliyor) {
                _faz.value = fazSimdiki.copy(alinan = fazSimdiki.alinan + 1, toplam = hedefToplam)
            } else {
                _faz.value = Faz.Aliyor(1, hedefToplam)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Soru paketi uygulanamadı", e)
        } finally {
            try { geciciCizimDosyasi?.delete() } catch (_: Exception) {}
        }
    }

    private suspend fun cozYerelDenemeId(kaynakDeneme: String?, uzakDenemeId: Long?): Long? {
        val dao = denemeSinaviDao ?: return uzakDenemeId
        return withContext(Dispatchers.IO) {
            try {
                if (!kaynakDeneme.isNullOrBlank()) {
                    val aranan = kaynakDeneme.trim()
                    val tum = dao.getAllDenemelerDirect()
                    val eslesen = tum.firstOrNull { it.baslik.equals(aranan, ignoreCase = true) }
                    if (eslesen != null) return@withContext eslesen.id
                }
                if (uzakDenemeId != null && uzakDenemeId > 0L) {
                    val mevcut = dao.getDenemeById(uzakDenemeId)
                    if (mevcut != null) return@withContext mevcut.id
                }
                null
            } catch (_: Exception) {
                null
            }
        }
    }

    private suspend fun bekleDosya(dosyaId: String): File? {
        if (dosyaId.isBlank()) return null
        bekleyenDosyalar.remove(dosyaId)?.let { return it }

        val deferred = CompletableDeferred<File?>()
        val onceki = dosyaBekleyiciler.putIfAbsent(dosyaId, deferred)
        val aktifDeferred = onceki ?: deferred

        // Kayıt sırasında dosya gelmiş olabilir (race kontrolü)
        bekleyenDosyalar.remove(dosyaId)?.let {
            dosyaBekleyiciler.remove(dosyaId)
            aktifDeferred.complete(it)
            return it
        }

        val sonuc = withTimeoutOrNull(DOSYA_BEKLEME_TIMEOUT_MS) {
            aktifDeferred.await()
        }
        dosyaBekleyiciler.remove(dosyaId)
        bekleyenDosyalar.remove(dosyaId)?.let { return it }
        return sonuc
    }

    private fun bitir(ep: String) {
        if (!bitirildi.compareAndSet(false, true)) return
        zamanAsimiJob?.cancel()
        val bagliEndpointAdi = (eslestirme.durum.value as? CihazEslestirme.BaglantiDurumu.Bagli)?.endpointAdi.orEmpty()
        val efektifAd = karsiCihazAdi.ifBlank { bagliEndpointAdi }
        if (karsiCihazKodu.isNotBlank() || efektifAd.isNotBlank()) {
            trustedDeviceStore?.addTrustedDevice(
                cihazKodu = karsiCihazKodu,
                cihazAdi = efektifAd
            )
        }
        _faz.value = Faz.Bitti(sonucBenim, karsiSonucOnbellek)
        eslestirme.mesajGonder(ep, SyncMesaj.OzetSonuc(sonucBenim))
    }

    private fun sifirlaDurumYalnizca() {
        elSikismaJob?.cancel()
        zamanAsimiJob?.cancel()
        ozetBirlesim.sifirla()
        karsiOzet = emptyList()
        karsiOzetAlindi = false
        turBasiYerel = emptyMap()
        gonderilecekler = emptyList()
        beklenenAlinacakSayisi = 0
        gonderimSirasi = 0
        karsiCihazAdi = ""
        karsiCihazKodu = ""
        karsiSonucOnbellek = null
        sonucBenim = SyncSonuc()
        gonderimBasladi.set(false)
        bitirildi.set(false)
        dosyaBekleyiciler.values.forEach { it.complete(null) }
        dosyaBekleyiciler.clear()
        bekleyenDosyalar.values.forEach { try { it.delete() } catch (_: Exception) {} }
        bekleyenDosyalar.clear()
    }

    fun sifirla() {
        endpointId = null
        erkenMesajTamponu.clear()
        sifirlaDurumYalnizca()
        _faz.value = Faz.Bos
    }

    /**
     * PERF: Ekran kapanınca BLE/Wi-Fi taramasını ve bekleyen sync işlerini
     * durdurur (önceden arka planda aramada kalıyordu). VM onCleared'den çağrılır.
     */
    fun durdur() {
        try { eslestirme.durdur() } catch (_: Exception) {}
        isJob.cancel()
        isJob = SupervisorJob()
        kapsam = CoroutineScope(isJob + Dispatchers.Default)
        sifirla()
    }

    private fun soruToBackupItem(soru: Soru): com.sorudefteri.data.backup.BackupSoruItem {
        return com.sorudefteri.data.backup.BackupSoruItem(
            originalId = soru.id,
            evrenselId = soru.evrenselId,
            updatedAt = soru.updatedAt,
            isDeleted = soru.isDeleted,
            imageName = "",
            solutionImageName = null,
            correctAnswer = soru.correctAnswer,
            examType = soru.examType,
            subjectId = soru.subjectId,
            topic = soru.topic,
            mistakeType = soru.mistakeType?.name,
            note = soru.note,
            isSolved = soru.isSolved,
            solvedAt = soru.solvedAt,
            aiExplanation = soru.aiExplanation,
            isFavorite = soru.isFavorite,
            reviewStage = soru.reviewStage,
            nextReviewDate = soru.nextReviewDate,
            lastReviewedAt = soru.lastReviewedAt,
            kaynakYayin = soru.kaynakYayin,
            kaynakDeneme = soru.kaynakDeneme,
            kaynakYil = soru.kaynakYil,
            kaynakSoruNo = soru.kaynakSoruNo,
            studentAnswer = soru.studentAnswer,
            difficulty = soru.difficulty,
            ocrMetni = soru.ocrMetni,
            isOcrUsed = soru.isOcrUsed,
            questionText = soru.questionText,
            audioFileName = null,
            fsrsStability = soru.fsrsStability,
            fsrsDifficulty = soru.fsrsDifficulty,
            fsrsReps = soru.fsrsReps,
            fsrsLapses = soru.fsrsLapses,
            createdAt = soru.createdAt,
            denemeId = soru.denemeId,
            denemeSoruNo = soru.denemeSoruNo ?: soru.kaynakSoruNo
        )
    }

    private fun backupItemToSoru(
        item: com.sorudefteri.data.backup.BackupSoruItem,
        soruYolu: String,
        cozumYolu: String?,
        sesYolu: String? = null,
        yerelDenemeId: Long? = null
    ): Soru {
        return Soru(
            id = 0L,
            evrenselId = item.evrenselId.ifBlank { UUID.randomUUID().toString() },
            isDeleted = 0,
            imagePath = soruYolu,
            solutionImagePath = cozumYolu,
            correctAnswer = item.correctAnswer,
            examType = item.examType,
            subjectId = item.subjectId,
            topic = item.topic,
            mistakeType = item.mistakeType?.let { HataTipi.fromName(it) },
            note = item.note,
            isSolved = item.isSolved,
            solvedAt = item.solvedAt,
            aiExplanation = item.aiExplanation,
            isFavorite = item.isFavorite,
            reviewStage = item.reviewStage,
            nextReviewDate = item.nextReviewDate,
            lastReviewedAt = item.lastReviewedAt,
            kaynakYayin = item.kaynakYayin,
            kaynakDeneme = item.kaynakDeneme,
            kaynakYil = item.kaynakYil,
            kaynakSoruNo = item.kaynakSoruNo ?: item.denemeSoruNo,
            studentAnswer = item.studentAnswer,
            difficulty = item.difficulty,
            ocrMetni = item.ocrMetni,
            isOcrUsed = item.isOcrUsed,
            questionText = item.questionText,
            audioPath = sesYolu,
            fsrsStability = item.fsrsStability,
            fsrsDifficulty = item.fsrsDifficulty,
            fsrsReps = item.fsrsReps,
            fsrsLapses = item.fsrsLapses,
            createdAt = item.createdAt,
            updatedAt = item.updatedAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
            contentHash = "",
            denemeId = yerelDenemeId ?: item.denemeId,
            denemeSoruNo = item.denemeSoruNo ?: item.kaynakSoruNo
        )
    }

    /** Soru→Entity dönüşümü (SoruEntity.toEntity ile aynı alan eşleşmesi). */
    private fun soruEntity(s: Soru): SoruEntity = SoruEntity(
        id = s.id,
        imagePath = s.imagePath,
        solutionImagePath = s.solutionImagePath,
        correctAnswer = s.correctAnswer,
        examType = s.examType,
        subjectId = s.subjectId,
        topic = s.topic,
        mistakeType = s.mistakeType?.name,
        note = s.note,
        isSolved = s.isSolved,
        solvedAt = s.solvedAt,
        aiExplanation = s.aiExplanation,
        isFavorite = s.isFavorite,
        reviewStage = s.reviewStage,
        nextReviewDate = s.nextReviewDate,
        lastReviewedAt = s.lastReviewedAt,
        kaynakYayin = s.kaynakYayin,
        kaynakDeneme = s.kaynakDeneme,
        kaynakYil = s.kaynakYil,
        kaynakSoruNo = s.kaynakSoruNo,
        studentAnswer = s.studentAnswer,
        difficulty = s.difficulty,
        ocrMetni = s.ocrMetni,
        isOcrUsed = s.isOcrUsed,
        questionText = s.questionText,
        audioPath = s.audioPath,
        fsrsStability = s.fsrsStability,
        fsrsDifficulty = s.fsrsDifficulty,
        fsrsReps = s.fsrsReps,
        fsrsLapses = s.fsrsLapses,
        createdAt = s.createdAt,
        updatedAt = s.updatedAt,
        contentHash = s.contentHash,
        evrenselId = s.evrenselId,
        isDeleted = s.isDeleted,
        denemeId = s.denemeId,
        denemeSoruNo = s.denemeSoruNo
    )
}
