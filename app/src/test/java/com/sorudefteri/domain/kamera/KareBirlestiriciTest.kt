package com.sorudefteri.domain.kamera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * KareBirlestirici medyan birleştirme testleri:
 * - sabit gürültü medyanla temizlenir,
 * - ofsetli kareler doğru örneklendiğinde içerik korunur,
 * - kenarlar REF karesinden doldurulur.
 */
class KareBirlestiriciTest {

    private fun argb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    @Test
    fun `sabit renkte gurultu medyanla duzelir`() {
        val gen = 8
        val yuk = 6
        val temiz = argb(100, 100, 100)
        val ref = IntArray(gen * yuk) { temiz }
        val k1 = IntArray(gen * yuk) { argb(60, 60, 60) }
        val k2 = IntArray(gen * yuk) { argb(140, 140, 140) }
        val k3 = IntArray(gen * yuk) { temiz }
        val ofsetler = listOf(
            Ofset(0, 0, 1.0), Ofset(0, 0, 0.9), Ofset(0, 0, 0.9), Ofset(0, 0, 0.9)
        )

        val merged = KareBirlestirici.birlestir(listOf(ref, k1, k2, k3), gen, yuk, ofsetler)

        for (i in merged.indices) {
            assertEquals("piksel $i uyumsuz", temiz, merged[i])
        }
    }

    @Test
    fun `ofsetli kare ayni icerigi yeniden uretir`() {
        val gen = 24
        val yuk = 20
        val kirmizilar = argb(200, 30, 30)
        val gri = argb(128, 128, 128)
        val ref = IntArray(gen * yuk) { i ->
            val x = i % gen
            val y = i / gen
            if (x in 10..13 && y in 10..13) kirmizilar else gri
        }
        // k1: içerik +2 px sağa kaydırılmış → k1(x,y) = ref(x-2, y)
        val k1 = IntArray(gen * yuk) { i ->
            val x = i % gen
            val y = i / gen
            ref[y * gen + (x - 2).coerceAtLeast(0)]
        }

        val merged = KareBirlestirici.birlestir(
            listOf(ref, k1),
            gen, yuk,
            listOf(null, Ofset(2, 0, 0.9))
        )

        // Blok içi piksel: iki karede de kırmızıya örneklenir
        assertEquals(kirmizilar, merged[11 * gen + 11])
        // Arka plan korunsun
        assertEquals(gri, merged[5 * gen + 20])
        // Geçerli kesişimin sağına düşen kenar sütun REF'ten gelmeli
        assertEquals(ref[5 * gen + 23], merged[5 * gen + 23])
    }

    @Test
    fun `tek kare aynen doner`() {
        val gen = 4
        val yuk = 3
        val kare = IntArray(gen * yuk) { it * 7 + 1 }
        val merged = KareBirlestirici.birlestir(listOf(kare), gen, yuk, listOf(null))
        assertTrue(merged.contentEquals(kare))
    }

    @Test
    fun `tek-buyuk boolean ofset karesi yerine ref doner`() {
        // Tüm ofsetler null: medyansız REF kopyası
        val gen = 4
        val yuk = 3
        val ref = IntArray(gen * yuk) { 5 }
        val diger = IntArray(gen * yuk) { 9 }
        val merged = KareBirlestirici.birlestir(listOf(ref, diger), gen, yuk, listOf(null, null))
        assertTrue(merged.contentEquals(ref))
    }
}
