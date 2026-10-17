package com.sorudefteri.ui.kamera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.util.Log
import com.sorudefteri.domain.kamera.KareBirlestirici
import com.sorudefteri.domain.kamera.KareHizalayici
import com.sorudefteri.domain.kamera.KirpmaHesaplayici
import com.sorudefteri.domain.kamera.Kutu
import com.sorudefteri.domain.kamera.KutuOrani
import com.sorudefteri.domain.kamera.Ofset
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max

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
    fun tekCekimIsle(tamYol: String, hedefOraniKapatici: (tamGen: Int, tamYuk: Int) -> KutuOrani): String {
        return try {
            val tam = ornekliCoz(tamYol, TAVAN_TEK) ?: return tamYol
            val hedefOrani = hedefOraniKapatici(tam.width, tam.height)
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
        return try {
            val kirpikler = ArrayList<Bitmap>(dosyalar.size)
            var referansGen = 0
            var referansYuk = 0
            var referansKutu: Kutu? = null

            for (yol in dosyalar) {
                val tam = ornekliCoz(yol, TAVAN_UZUN) ?: continue
                if (referansKutu == null) {
                    val hedefOrani = hedefOraniKapatici(tam.width, tam.height)
                    referansKutu = KirpmaHesaplayici.oraniKutuya(hedefOrani, tam.width, tam.height)
                }
                val kirpik = kirp(tam, referansKutu) ?: continue
                tam.recycle()
                referansGen = referansKutu.genislik
                referansYuk = referansKutu.yukseklik
                kirpikler.add(kirpik)
            }
            if (kirpikler.size < 2) {
                kirpikler.forEach { it.recycle() }
                return null
            }

            // Boyutları referans kareye sabitle (aynı kare kümesi farklı dönüşte değilsemiştir)
            val boyutlu = kirpikler.map { b ->
                if (b.width == referansGen && b.height == referansYuk) b else {
                    val olcekli = Bitmap.createScaledBitmap(b, referansGen, referansYuk, true)
                    if (olcekli != b) b.recycle()
                    olcekli
                }
            }

            val argbDizileri = ArrayList<IntArray>(boyutlu.size)
            val griDizileri = ArrayList<IntArray>(boyutlu.size)
            for (b in boyutlu) {
                val dizi = IntArray(referansGen * referansYuk)
                b.getPixels(dizi, 0, referansGen, 0, 0, referansGen, referansYuk)
                b.recycle()
                argbDizileri.add(dizi)
                griDizileri.add(griDizisigine(dizi))
            }

            // Hizalama: kare 0 referans
            val ofsetler = ArrayList<Ofset?>(griDizileri.size)
            var hizalanan = 0
            for (i in griDizileri.indices) {
                val ofset = if (i == 0) null
                else KareHizalayici.hizala(griDizileri[0], griDizileri[i], referansGen, referansYuk)
                ofsetler.add(ofset)
                if (ofset != null) hizalanan++
            }

            val birlesik: IntArray? =
                if (hizalanan * 10 >= griDizileri.size * 5) { // en az yarısı hizalanabildiyse
                    KareBirlestirici.birlestir(argbDizileri, referansGen, referansYuk, ofsetler)
                } else null

            val sonDizi = birlesik ?: enKeskin(argbDizileri, griDizileri, referansGen, referansYuk)

            val sonuc = Bitmap.createBitmap(referansGen, referansYuk, Bitmap.Config.ARGB_8888)
            sonuc.setPixels(sonDizi, 0, referansGen, 0, 0, referansGen, referansYuk)
            val hedefDosya = File(hedefKlasor, "uzunpoz_${UUID.randomUUID()}.jpg")
            dosyayaYaz(sonuc, hedefDosya)
            sonuc.recycle()

            dosyalar.forEach { yol -> try { File(yol).delete() } catch (_: Exception) {} }
            hedefDosya.absolutePath
        } catch (oom: OutOfMemoryError) {
            Log.w(TAG, "Uzun pozlama OOM: ${oom.message}")
            null
        } catch (t: Throwable) {
            Log.w(TAG, "Uzun pozlama hatası: ${t.message}")
            null
        }
    }

    private fun enKeskin(
        argbDizileri: List<IntArray>,
        griDizileri: List<IntArray>,
        gen: Int,
        yuk: Int
    ): IntArray {
        var enIyi = 0
        var enIyiSkor = Double.NEGATIVE_INFINITY
        for (i in griDizileri.indices) {
            val skor = KareHizalayici.keskinlikOlcu(griDizileri[i], gen, yuk)
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
    /** Uzun pozlamada N kare bulunduran tavan (RAM çerçevesi). */
    private const val TAVAN_UZUN = 1600
}
