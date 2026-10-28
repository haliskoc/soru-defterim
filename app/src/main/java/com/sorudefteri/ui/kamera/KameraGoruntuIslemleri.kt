package com.sorudefteri.ui.kamera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.sorudefteri.domain.kamera.KareBirlestirici
import com.sorudefteri.domain.kamera.KareHizalayici
import com.sorudefteri.domain.kamera.KirpmaHesaplayici
import com.sorudefteri.domain.kamera.Kutu
import com.sorudefteri.domain.kamera.KutuOrani
import com.sorudefteri.domain.kamera.Ofset
import com.sorudefteri.domain.kamera.SoruAlgilayici
import com.sorudefteri.domain.kamera.TarananSatir
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * "Ölç-sonra-kırp" görüntü HAT'ı (Android tarafı).
 *
 * Tek çekim:  dosya → EXIF döndürmeli örnekleme → kırpma → JPEG (yeni cache dosyası)
 * Uzun pozlama: N kare → kırpma → gri piramit hizalama (KareHizalayici) →
 *   medyan birleştirme (KareBirlestirici) → hafif dosya; hizalama tutmazsa
 *   Laplacian varyansıyla EN KESKİN kare seçilir.
 *
 * RAM disiplini: RGB_565 örnekleme, tavan ~1600–2560 px, Bitmap'ler anında recycle.
 */
object KameraGoruntuIslemleri {

    private const val TAG = "KameraGoruntuIslemleri"

    /** Hedef oranı: soru-adayı öncelikli; yoksa statik çerçeveyi görüntü oranına çevir. */
    fun hedefOrani(
        soruOrani: KutuOrani?,
        cerceveKutu: Kutu?,
        tamGen: Int,
        tamYuk: Int,
        onizlemeGen: Int,
        onizlemeYuk: Int,
        aynaliMi: Boolean,
        minOran: Double = 0.15
    ): KutuOrani {
        val temel = soruOrani ?: cerceveKutu?.let {
            KirpmaHesaplayici.gorunumPxiGorselOranina(
                it, tamGen, tamYuk, onizlemeGen, onizlemeYuk, aynaliMi
            )
        } ?: KutuOrani(0.0, 0.0, 1.0, 1.0)
        return KirpmaHesaplayici.minimumBoyutOrani(temel.kisnala(), minOran).kisnala()
    }

    /** EXIF'a göre döndürülmüş (upright), inSampleSize ile küçültülmüş çözümleme. */
    fun ornekliCoz(path: String, tavanPiksel: Int): Bitmap? {
        return try {
            val sinirlar = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, sinirlar)
            if (sinirlar.outWidth <= 0 || sinirlar.outHeight <= 0) return null

            var ornekleme = 1
            while (
                max(sinirlar.outWidth, sinirlar.outHeight) / (ornekleme * 2) >= tavanPiksel
            ) {
                ornekleme *= 2
            }

            val secenekler = BitmapFactory.Options().apply {
                inSampleSize = ornekleme
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bitmap = BitmapFactory.decodeFile(path, secenekler) ?: return null

            val derece = exifDerecesi(path)
            if (derece == 0) bitmap else dondur(bitmap, derece)
        } catch (oom: OutOfMemoryError) {
            Log.w(TAG, "Cozme OOM: ${oom.message}")
            null
        } catch (t: Throwable) {
            Log.w(TAG, "Cozme hatası: ${t.message}")
            null
        }
    }

    private fun exifDerecesi(path: String): Int {
        return try {
            val exif = ExifInterface(path)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (t: Throwable) {
            0
        }
    }

    private fun dondur(bitmap: Bitmap, derece: Int): Bitmap {
        if (derece % 360 == 0) return bitmap
        return try {
            val m = Matrix().apply { postRotate(derece.toFloat()) }
            val donusmus = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
            if (donusmus != bitmap) bitmap.recycle()
            donusmus
        } catch (t: Throwable) {
            bitmap
        }
    }

    /** Tek kare: kırpma gerekmiyorsa özgün dosya yolunu aynen döner. */
    fun tekCekimIsle(
        tamYol: String,
        hedefOraniKapatici: (tamGen: Int, tamYuk: Int) -> KutuOrani,
        refine: Boolean = false
    ): String {
        return try {
            val tam = ornekliCoz(tamYol, TAVAN_TEK) ?: return tamYol
            val hedefOrani = hedefOraniKapatici(tam.width, tam.height).let { oran ->
                if (refine) refineOrani(tam, oran) ?: oran else oran
            }
            val kutu = KirpmaHesaplayici.oraniKutuya(hedefOrani, tam.width, tam.height)

            if (kutu.genislik >= tam.width - 2 && kutu.yukseklik >= tam.height - 2) {
                tam.recycle()
                return tamYol
            }

            val kirpik = kirp(tam, kutu)
            tam.recycle()
            if (kirpik == null) return tamYol

            val hedefDosya = File(
                File(tamYol).parentFile,
                "kirpilmis_${UUID.randomUUID()}.jpg"
            )
            dosyayaYaz(kirpik, hedefDosya)
            kirpik.recycle()
            File(tamYol).delete()
            hedefDosya.absolutePath
        } catch (t: Throwable) {
            Log.w(TAG, "Kırpma hatası: ${t.message}")
            tamYol
        }
    }

    /**
     * [dosyalar] kareleri: kırp → hizala → medyan birleştir → JPEG.
     * @return birleşmiş dosyanın yolu; kalıcı başarısızlıkta null.
     */
    fun uzunPozlamaIsle(
        dosyalar: List<String>,
        hedefOraniKapatici: (tamGen: Int, tamYuk: Int) -> KutuOrani,
        hedefKlasor: File
    ): String? {
        if (dosyalar.isEmpty()) return null
        // PERF: Hata/OOM yolunda yarım bitmap'ler recycle edilsin diye
        // tüm ara bitmap'ler izlenir; tepe RAM düşer.
        val izlenen = ArrayList<Bitmap>()
        fun izle(b: Bitmap): Bitmap {
            izlenen.add(b)
            return b
        }
        fun birak(b: Bitmap) {
            izlenen.remove(b)
        }
        return try {
            val kirpikler = ArrayList<Bitmap>(dosyalar.size)
            var referansGen = 0
            var referansYuk = 0
            var referansKutu: Kutu? = null

            for (yol in dosyalar) {
                val tam = ornekliCoz(yol, TAVAN_UZUN) ?: continue
                izle(tam)
                if (referansKutu == null) {
                    val hedefOrani = hedefOraniKapatici(tam.width, tam.height)
                    referansKutu = KirpmaHesaplayici.oraniKutuya(hedefOrani, tam.width, tam.height)
                }
                val kirpik = kirp(tam, referansKutu) ?: continue
                izle(kirpik)
                if (!tam.isRecycled) tam.recycle()
                birak(tam)
                referansGen = referansKutu.genislik
                referansYuk = referansKutu.yukseklik
                kirpikler.add(kirpik)
            }
            if (kirpikler.size < 2) {
                return null
            }

            // Boyutları referans kareye sabitle (aynı kare kümesi farklı dönüşte değilsemiştir)
            val boyutlu = kirpikler.map { b ->
                if (b.width == referansGen && b.height == referansYuk) b else {
                    val olcekli = Bitmap.createScaledBitmap(b, referansGen, referansYuk, true)
                    if (olcekli != b) {
                        izle(olcekli)
                        if (!b.isRecycled) b.recycle()
                        birak(b)
                    }
                    olcekli
                }
            }

            // PERF: Kare başına ARGB + gri IntArray aynı anda tutuluyordu
            // (~2× piksel × kare sayısı). Gri dizi hizalamadan sonra
            // serbest bırakılır; referans gri korunur.
            val argbDizileri = ArrayList<IntArray>(boyutlu.size)
            var referansGri: IntArray? = null
            val griDizileri = ArrayList<IntArray>(boyutlu.size)
            for (b in boyutlu) {
                val dizi = IntArray(referansGen * referansYuk)
                b.getPixels(dizi, 0, referansGen, 0, 0, referansGen, referansYuk)
                if (!b.isRecycled) b.recycle()
                birak(b)
                argbDizileri.add(dizi)
                val gri = griDizisigine(dizi)
                if (referansGri == null) referansGri = gri
                griDizileri.add(gri)
            }
            val refGri = referansGri ?: return null

            // Hizalama: kare 0 referans
            val ofsetler = ArrayList<Ofset?>(griDizileri.size)
            var hizalanan = 0
            for (i in griDizileri.indices) {
                val ofset = if (i == 0) null
                else KareHizalayici.hizala(refGri, griDizileri[i], referansGen, referansYuk)
                ofsetler.add(ofset)
                if (ofset != null) hizalanan++
            }
            griDizileri.clear()

            val birlesik: IntArray? =
                if (hizalanan * 10 >= argbDizileri.size * 5) { // en az yarısı hizalanabildiyse
                    KareBirlestirici.birlestir(argbDizileri, referansGen, referansYuk, ofsetler)
                } else null

            val sonDizi = birlesik ?: enKeskinHafif(argbDizileri, refGri, referansGen, referansYuk)
            argbDizileri.clear()

            val sonuc = Bitmap.createBitmap(referansGen, referansYuk, Bitmap.Config.ARGB_8888)
            sonuc.setPixels(sonDizi, 0, referansGen, 0, 0, referansGen, referansYuk)
            val hedefDosya = File(hedefKlasor, "uzunpoz_${UUID.randomUUID()}.jpg")
            dosyayaYaz(sonuc, hedefDosya)
            if (!sonuc.isRecycled) sonuc.recycle()

            dosyalar.forEach { yol -> try { File(yol).delete() } catch (_: Exception) {} }
            hedefDosya.absolutePath
        } catch (oom: OutOfMemoryError) {
            Log.w(TAG, "Uzun pozlama OOM: ${oom.message}")
            null
        } catch (t: Throwable) {
            Log.w(TAG, "Uzun pozlama hatası: ${t.message}")
            null
        } finally {
            for (b in izlenen) {
                try { if (!b.isRecycled) b.recycle() } catch (_: Exception) {}
            }
            izlenen.clear()
        }
    }

    /**
     * PERF: En keskin kare seçimi gri dizileri yeniden hesaplamadan yapılır
     * (önceden tüm gri diziler bellekte tutuluyordu).
     */
    private fun enKeskinHafif(
        argbDizileri: List<IntArray>,
        referansGri: IntArray,
        gen: Int,
        yuk: Int
    ): IntArray {
        var enIyi = 0
        var enIyiSkor = KareHizalayici.keskinlikOlcu(referansGri, gen, yuk)
        for (i in 1 until argbDizileri.size) {
            val skor = KareHizalayici.keskinlikOlcu(griDizisigine(argbDizileri[i]), gen, yuk)
            if (skor > enIyiSkor) {
                enIyiSkor = skor
                enIyi = i
            }
        }
        return argbDizileri[enIyi]
    }

    private fun griDizisigine(argb: IntArray): IntArray {
        val gri = IntArray(argb.size)
        for (i in argb.indices) {
            val p = argb[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            gri[i] = (r * 77 + g * 151 + b * 28) shr 8
        }
        return gri
    }

    /**
     * Deklanşör-anında refine: çekilen tam çözünürlük kareye bir kez daha ML Kit
     * çalıştırıp kutuyu düzeltir. Preview/analiz/çekim boy uyumsuzluğunu bitirir.
     * Yalnız yüksek doğrulukta çağrılır (anlık ~200-400ms yük); başarısızlıkta null.
     */
    private fun refineOrani(tam: Bitmap, onizlemeOrani: KutuOrani): KutuOrani? {
        // PERF: tanici.close() finally'de — timeout/hata yolunda client
        // hafızada kalmıyordu (her çekimde model client birikiyordu).
        val tanici = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val sonuc = tanici.process(InputImage.fromBitmap(tam, 0))
            val metin = Tasks.await(sonuc, REFINE_ZAMAN_ASIMI_MS, TimeUnit.MILLISECONDS)
            val satirlar = ArrayList<TarananSatir>()
            for (blok in metin.textBlocks) {
                for (satir in blok.lines) {
                    val r = satir.boundingBox ?: continue
                    if (r.width() > 0 && r.height() > 0) {
                        satirlar.add(TarananSatir(Kutu(r.left, r.top, r.width(), r.height()), satir.text ?: ""))
                    }
                }
            }
            if (satirlar.isEmpty()) return null
            val aday = SoruAlgilayici.secMetinli(satirlar, tam.width, tam.height)
                ?.let { KirpmaHesaplayici.kutuyuOrana(it, tam.width, tam.height) }
                ?: return null
            val iou = iouOran(onizlemeOrani, aday)
            if (iou < 0.30) onizlemeOrani else aday
        } catch (t: Throwable) {
            Log.w(TAG, "Refine başarısız: ${t.message}")
            null
        } finally {
            try { tanici.close() } catch (_: Exception) {}
        }
    }

    private fun iouOran(a: KutuOrani, b: KutuOrani): Double {
        val sol = maxOf(a.sol, b.sol)
        val ust = maxOf(a.ust, b.ust)
        val sag = minOf(a.sag, b.sag)
        val alt = minOf(a.alt, b.alt)
        val ortus = (sag - sol).coerceAtLeast(0.0) * (alt - ust).coerceAtLeast(0.0)
        val toplam = a.genislik * a.yukseklik + b.genislik * b.yukseklik - ortus
        if (toplam <= 0.0) return 0.0
        return ortus / toplam
    }

    private fun kirp(kaynak: Bitmap, kutu: Kutu): Bitmap? {
        val sol = kutu.sol.coerceIn(0, kaynak.width - 1)
        val ust = kutu.ust.coerceIn(0, kaynak.height - 1)
        val gen = kutu.genislik.coerceIn(1, kaynak.width - sol)
        val yuk = kutu.yukseklik.coerceIn(1, kaynak.height - ust)
        return try {
            Bitmap.createBitmap(kaynak, sol, ust, gen, yuk)
        } catch (t: Throwable) {
            Log.w(TAG, "Kırpma pahası: ${t.message}")
            null
        }
    }

    private fun dosyayaYaz(bitmap: Bitmap, hedef: File) {
        FileOutputStream(hedef).use { cikti ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, cikti)
            cikti.flush()
        }
    }

    /** Tek çekimde çözülen thumbnail tavanı (crop kalitesi için geniş). */
    private const val TAVAN_TEK = 2560
    /** Uzun pozlamada N kare bulunduran tavan (RAM çerçevesi, PERF: 1600→1280). */
    private const val TAVAN_UZUN = 1280
    /** Refine için ML Kit bekleme üst sınırı (ms) — çekimi kilitlemez. */
    private const val REFINE_ZAMAN_ASIMI_MS = 2500L
}
