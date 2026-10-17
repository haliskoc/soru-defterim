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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * CameraX `ImageAnalysis.Analyzer` + ML Kit Text köprüsü.
 *
 * Araştırma gereği uygulanan disiplin:
 *  - `STRATEGY_KEEP_ONLY_LATEST` (ekranda bağlanır; burada uyumlu çalışır),
 *  - ~2.2 fps hız sınırı (RAM/CPU dostu; zynkware referansı ~400px analiz + az gecikme),
 *  - aynı anda tek tanıma (atomik kilit),
 *  - `ImageProxy` SADECE tanıma bitince kapatılır (MediaImage referansı yaşam sigortası).
 *
 * Çıktı: satır kutuları (ML Kit döndürülmüş-UPRIGHT koordinatları) + analiz boyutu.
 */
class MetinAnalizci(
    private val kontrolcu: AnalizKontrolcu,
    private val sonucGeldi: (satirlar: List<Kutu>, analizGen: Int, analizYuk: Int) -> Unit
) : ImageAnalysis.Analyzer {

    private val tanici: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private val isleniyor = AtomicBoolean(false)
    private val sonIslemMs = AtomicLong(0L)

    override fun analyze(imageProxy: ImageProxy) {
        // Kısa istisnalar: anında kapat
        if (!kontrolcu.acik || isleniyor.get() ||
            System.currentTimeMillis() - sonIslemMs.get() < ARALIK_MS
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

            tanici.process(girdi)
                .addOnSuccessListener { metin ->
                    try {
                        val satirlar = ArrayList<Kutu>()
                        for (blok in metin.textBlocks) {
                            for (satir in blok.lines) {
                                val r = satir.boundingBox ?: continue
                                if (r.width() > 0 && r.height() > 0) {
                                    satirlar.add(Kutu(r.left, r.top, r.width(), r.height()))
                                }
                            }
                        }
                        sonucGeldi(satirlar, analizGen, analizYuk)
                    } finally {
                        tamam(imageProxy)
                    }
                }
                .addOnFailureListener { hata ->
                    Log.w(TAG, "Metin analizi başarısız: ${hata.localizedMessage}")
                    tamam(imageProxy)
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
        /** Analiz frekansının alt sınırı (ms). ≈2 analiz/sn. */
        private const val ARALIK_MS = 450L
    }
}
