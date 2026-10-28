package com.sorudefteri.ui.kamera

import android.media.Image
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.sorudefteri.domain.kamera.Kutu
import com.sorudefteri.domain.kamera.TarananSatir
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * CameraX `ImageAnalysis.Analyzer` + ML Kit Text köprüsü.
 *
 * Araştırma gereği uygulanan disiplin:
 *  - `STRATEGY_KEEP_ONLY_LATEST` (ekranda bağlanır; burada uyumlu çalışır),
 *  - kademeli hız sınırı (normal ~450ms / yüksek doğruluk ~300ms),
 *  - aynı anda tek tanıma (atomik kilit),
 *  - `ImageProxy` SADECE tanıma bitince kapatılır (MediaImage referansı yaşam sigortası).
 *
 * Çıktı: satır kutuları + metin (ML Kit döndürülmüş-UPRIGHT koordinatları) + analiz boyutu.
 * Metin, soru-sınır tespiti ([SoruIsaretleri]) için taşınır; geometriye ek yükü yoktur.
 *
 * Pil disiplini: `kontrolcu.yuksekDogruluk` kapalıysa analiz normal hızda çalışır;
 * ekran kapandığında `kapat()` ile tanıyıcı serbest bırakılır (yük yalnız kamera açıkken).
 */
class MetinAnalizci(
    private val kontrolcu: AnalizKontrolcu,
    private val sonucGeldi: (satirlar: List<TarananSatir>, analizGen: Int, analizYuk: Int) -> Unit,
    private val hataOldu: () -> Unit = {},
    private val callbackExecutor: Executor? = null
) : ImageAnalysis.Analyzer {

    private val tanici: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private val guvenliExecutor: Executor? = callbackExecutor?.let { raw ->
        Executor { komuta ->
            try {
                raw.execute(komuta)
            } catch (_: RejectedExecutionException) {
                try {
                    komuta.run()
                } catch (_: Throwable) {}
            }
        }
    }

    private val isleniyor = AtomicBoolean(false)
    private val sonIslemMs = AtomicLong(0L)

    override fun analyze(imageProxy: ImageProxy) {
        val aralik = if (kontrolcu.yuksekDogruluk) ARALIK_YUKSEK_MS else ARALIK_MS
        // Kısa istisnalar: anında kapat
        // PERF: Kilit sonrası / ayar paneli açıkken OCR durur.
        if (kontrolcu.duraklatildi || !kontrolcu.acik || isleniyor.get() ||
            System.currentTimeMillis() - sonIslemMs.get() < aralik
        ) {
            imageProxy.close()
            return
        }
        val mediaImage: Image = imageProxy.image ?: run {
            imageProxy.close()
            return
        }
        if (!isleniyor.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }
        try {
            val donusDerece = imageProxy.imageInfo.rotationDegrees
            val girdi = InputImage.fromMediaImage(mediaImage, donusDerece)
            val (analizGen, analizYuk) = dikBoyut(mediaImage.width, mediaImage.height, donusDerece)

            val gorev = tanici.process(girdi)
            val basariIsle: (com.google.mlkit.vision.text.Text) -> Unit = { metin ->
                val satirlar = ArrayList<TarananSatir>()
                for (blok in metin.textBlocks) {
                    for (satir in blok.lines) {
                        val r = satir.boundingBox ?: continue
                        if (r.width() > 0 && r.height() > 0) {
                            satirlar.add(
                                TarananSatir(
                                    Kutu(r.left, r.top, r.width(), r.height()),
                                    satir.text ?: ""
                                )
                            )
                        }
                    }
                }
                sonucGeldi(satirlar, analizGen, analizYuk)
            }
            val hataIsle: (Exception) -> Unit = { hata ->
                Log.w(TAG, "Metin analizi başarısız: ${hata.localizedMessage}")
                hataOldu()
            }

            if (guvenliExecutor != null) {
                gorev
                    .addOnSuccessListener(guvenliExecutor) { metin ->
                        try {
                            basariIsle(metin)
                        } catch (t: Throwable) {
                            Log.w(TAG, "Sonuç işleme hatası: ${t.localizedMessage}")
                        }
                    }
                    .addOnFailureListener(guvenliExecutor) { hata ->
                        hataIsle(hata)
                    }
                    .addOnCompleteListener(guvenliExecutor) {
                        tamam(imageProxy)
                    }
            } else {
                gorev
                    .addOnSuccessListener { metin ->
                        try {
                            basariIsle(metin)
                        } finally {
                            tamam(imageProxy)
                        }
                    }
                    .addOnFailureListener { hata ->
                        hataIsle(hata)
                        tamam(imageProxy)
                    }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Analiz hatası: ${t.localizedMessage}")
            tamam(imageProxy)
        }
    }

    private fun tamam(imageProxy: ImageProxy) {
        sonIslemMs.set(System.currentTimeMillis())
        isleniyor.set(false)
        imageProxy.close()
    }

    /** Ekran disposed olunca tanıyıcıyı serbest bırak. */
    fun kapat() {
        try {
            tanici.close()
        } catch (_: Exception) {}
    }

    /** 90/270 dönüşlerde boyut eksenleri takas olur. */
    private fun dikBoyut(gen: Int, yuk: Int, donus: Int): Pair<Int, Int> =
        if (donus % 180 != 0) yuk to gen else gen to yuk

    companion object {
        private const val TAG = "MetinAnalizci"
        /** Normal analiz aralığı (ms). ≈2 analiz/sn — pil dostu. */
        const val ARALIK_MS = 450L
        /** Yüksek doğruluk analiz aralığı (ms). ≈3 analiz/sn — yalnız yüksek doğrulukta. */
        const val ARALIK_YUKSEK_MS = 300L
    }
}
