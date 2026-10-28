package com.sorudefteri.domain.omr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.PointF
import com.sorudefteri.data.pdf.OmrMatrixCodeCodec
import com.sorudefteri.domain.omr.model.OmrTemplate
import com.sorudefteri.domain.omr.vision.BubbleReader
import com.sorudefteri.domain.omr.vision.PerspectiveWarper
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Kamera Tabanlı Optik Okuma (OMR) Çekirdek Motoru Birim Testleri.
 *
 * 1. [OmrTemplate] Geometri & Dönüşüm Doğrulaması (120 Soru, A4 pt ↔ Warp Uzayı).
 * 2. [BubbleReader] Bağıl Kontrast, Baloncuk Okuma, Çift İşaret ("MULTI") ve Silgi İzi Ayrımı.
 * 3. [PerspectiveWarper] Homografi ve 4 Köşe Dönüşümü.
 * 4. [OmrMatrixCodeCodec] Deterministik 2D Vektörel Optik Matris Kodlayıcı/Çözücü & CRC-8.
 */
class OmrEngineUnitTest {

    @Before
    fun setUp() {
        mockkStatic(Bitmap::class)
        mockkConstructor(Matrix::class)
        mockkConstructor(Canvas::class)

        // Android stub jar'da PointF constructor'ı alanları doldurmadığı için statik fiducial koordinatlarını initialize et
        OmrTemplate.FIDUCIAL_TOP_LEFT_PT.x = 45f
        OmrTemplate.FIDUCIAL_TOP_LEFT_PT.y = 45f
        OmrTemplate.FIDUCIAL_TOP_RIGHT_PT.x = 550f
        OmrTemplate.FIDUCIAL_TOP_RIGHT_PT.y = 45f
        OmrTemplate.FIDUCIAL_BOTTOM_RIGHT_PT.x = 550f
        OmrTemplate.FIDUCIAL_BOTTOM_RIGHT_PT.y = 797f
        OmrTemplate.FIDUCIAL_BOTTOM_LEFT_PT.x = 45f
        OmrTemplate.FIDUCIAL_BOTTOM_LEFT_PT.y = 797f
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // =========================================================================
    // 1. OMR TEMPLATE GEOMETRİ VE KOORDİNAT TESTLERİ
    // =========================================================================

    @Test
    fun `all 120 questions have bubble coordinates strictly within warp boundary 1200x1700`() {
        for (q in 1..OmrTemplate.MAX_QUESTIONS) {
            val bubbleCenters = OmrTemplate.getBubbleCenters(q)
            assertEquals("Soru $q için tam 5 şık (A..E) olmalı", 5, bubbleCenters.size)

            for ((opt, coord) in bubbleCenters) {
                val (cx, cy) = coord
                assertTrue(
                    "Soru $q Şık $opt X koordinatı (cx=$cx) [0..1200] aralığında olmalı",
                    cx in 0f..OmrTemplate.WARP_WIDTH.toFloat()
                )
                assertTrue(
                    "Soru $q Şık $opt Y koordinatı (cy=$cy) [0..1700] aralığında olmalı",
                    cy in 0f..OmrTemplate.WARP_HEIGHT.toFloat()
                )
            }
        }
    }

    @Test
    fun `timing tracks are within warp boundaries for all questions`() {
        for (q in 1..OmrTemplate.MAX_QUESTIONS) {
            val (tx, ty) = OmrTemplate.getTimingTrackCenter(q)
            assertTrue("Zamanlama rayı X ($tx) [0..1200] olmalı", tx in 0f..OmrTemplate.WARP_WIDTH.toFloat())
            assertTrue("Zamanlama rayı Y ($ty) [0..1700] olmalı", ty in 0f..OmrTemplate.WARP_HEIGHT.toFloat())
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `getBubbleCenters throws when question number is 0`() {
        OmrTemplate.getBubbleCenters(0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `getBubbleCenters throws when question number exceeds 120`() {
        OmrTemplate.getBubbleCenters(121)
    }

    @Test
    fun `ptToWarp and warpToPt conversions are strictly invertible`() {
        // Sol-Üst Fiducial: (45f, 45f) pt -> (0f, 0f) warp
        val (wTLx, wTLy) = OmrTemplate.ptToWarp(
            OmrTemplate.FIDUCIAL_TOP_LEFT_PT.x,
            OmrTemplate.FIDUCIAL_TOP_LEFT_PT.y
        )
        assertEquals(0f, wTLx, 0.001f)
        assertEquals(0f, wTLy, 0.001f)

        // Sağ-Alt Fiducial: (550f, 797f) pt -> (1200f, 1700f) warp
        val (wBRx, wBRy) = OmrTemplate.ptToWarp(
            OmrTemplate.FIDUCIAL_BOTTOM_RIGHT_PT.x,
            OmrTemplate.FIDUCIAL_BOTTOM_RIGHT_PT.y
        )
        assertEquals(OmrTemplate.WARP_WIDTH.toFloat(), wBRx, 0.001f)
        assertEquals(OmrTemplate.WARP_HEIGHT.toFloat(), wBRy, 0.001f)

        // Ters dönüşüm (warpToPt)
        val (ptTLx, ptTLy) = OmrTemplate.warpToPt(0f, 0f)
        assertEquals(OmrTemplate.FIDUCIAL_TOP_LEFT_PT.x, ptTLx, 0.001f)
        assertEquals(OmrTemplate.FIDUCIAL_TOP_LEFT_PT.y, ptTLy, 0.001f)

        val (ptBRx, ptBRy) = OmrTemplate.warpToPt(1200f, 1700f)
        assertEquals(OmrTemplate.FIDUCIAL_BOTTOM_RIGHT_PT.x, ptBRx, 0.001f)
        assertEquals(OmrTemplate.FIDUCIAL_BOTTOM_RIGHT_PT.y, ptBRy, 0.001f)
    }

    @Test
    fun `resolveSubjectForQuestion maps major exams correctly`() {
        // TYT
        val (turkceDers, turkceId) = OmrTemplate.resolveSubjectForQuestion("TYT", 1)
        assertEquals("Türkçe", turkceDers)
        assertEquals("turkce", turkceId)

        val (sosyalDers, _) = OmrTemplate.resolveSubjectForQuestion("TYT", 45)
        assertEquals("Sosyal Bilimler", sosyalDers)

        val (matDers, matId) = OmrTemplate.resolveSubjectForQuestion("TYT", 65)
        assertEquals("Matematik", matDers)
        assertEquals("matematik", matId)

        val (fenDers, _) = OmrTemplate.resolveSubjectForQuestion("TYT", 110)
        assertEquals("Fen Bilimleri", fenDers)

        // AYT, LGS, KPSS
        val (aytDers, _) = OmrTemplate.resolveSubjectForQuestion("AYT", 10)
        assertEquals("Matematik", aytDers)

        val (lgsDers, _) = OmrTemplate.resolveSubjectForQuestion("LGS", 5)
        assertEquals("Türkçe", lgsDers)

        val (kpssDers, _) = OmrTemplate.resolveSubjectForQuestion("KPSS", 65)
        assertEquals("Tarih", kpssDers)
    }

    @Test
    fun `defaultQuestionCount returns standard values for exam types`() {
        assertEquals(120, OmrTemplate.defaultQuestionCount("TYT"))
        assertEquals(80, OmrTemplate.defaultQuestionCount("AYT"))
        assertEquals(90, OmrTemplate.defaultQuestionCount("LGS"))
        assertEquals(120, OmrTemplate.defaultQuestionCount("KPSS"))
        assertEquals(80, OmrTemplate.defaultQuestionCount("YDT"))
        assertEquals(120, OmrTemplate.defaultQuestionCount("GENEL"))
    }

    // =========================================================================
    // 2. BUBBLE READER VE KONTRAST ALGORİTMASI TESTLERİ
    // =========================================================================

    /**
     * Sentetik 1200x1700 piksel alanında çalışan mock Bitmap üretir.
     * Varsayılan olarak tüm pikseller beyaz (0xFFFFFFFF).
     * `darkenedCircles` içindeki dairesel alanlar belirtilen renk/koyuluk ile doldurulur.
     */
    private fun createSyntheticOpticalBitmap(
        darkenedCircles: List<Triple<Float, Float, Float>> = emptyList(), // (cx, cy, radius)
        fillColor: Int = -0x1000000 // Siyah (0xFF000000)
    ): Bitmap {
        val mockBmp = mockk<Bitmap>()
        every { mockBmp.width } returns OmrTemplate.WARP_WIDTH
        every { mockBmp.height } returns OmrTemplate.WARP_HEIGHT

        every { mockBmp.getPixel(any(), any()) } answers {
            val px = firstArg<Int>()
            val py = secondArg<Int>()

            var isDark = false
            for (circle in darkenedCircles) {
                val dx = px - circle.first
                val dy = py - circle.second
                if (dx * dx + dy * dy <= circle.third * circle.third) {
                    isDark = true
                    break
                }
            }
            if (isDark) fillColor else -1 // -1 = 0xFFFFFFFF (Beyaz)
        }
        return mockBmp
    }

    @Test
    fun `readRow correctly detects single filled bubble option A`() {
        val centers = OmrTemplate.getBubbleCenters(1)
        val aCenter = centers["A"] ?: error("A şıkkı bulunamadı")

        // A şıkkı baloncuk alanını siyahla doldur
        val bitmap = createSyntheticOpticalBitmap(
            darkenedCircles = listOf(Triple(aCenter.first, aCenter.second, OmrTemplate.BUBBLE_RADIUS))
        )

        val detected = BubbleReader.readRow(bitmap, centers, OmrTemplate.BUBBLE_RADIUS)
        assertEquals("A", detected)
    }

    @Test
    fun `readRow correctly detects single filled bubble option C`() {
        val centers = OmrTemplate.getBubbleCenters(15)
        val cCenter = centers["C"] ?: error("C şıkkı bulunamadı")

        val bitmap = createSyntheticOpticalBitmap(
            darkenedCircles = listOf(Triple(cCenter.first, cCenter.second, OmrTemplate.BUBBLE_RADIUS))
        )

        val detected = BubbleReader.readRow(bitmap, centers, OmrTemplate.BUBBLE_RADIUS)
        assertEquals("C", detected)
    }

    @Test
    fun `readRow returns null when row is completely empty`() {
        val centers = OmrTemplate.getBubbleCenters(1)
        val bitmap = createSyntheticOpticalBitmap(darkenedCircles = emptyList())

        val detected = BubbleReader.readRow(bitmap, centers, OmrTemplate.BUBBLE_RADIUS)
        assertNull("Boş bırakılmış soru için null dönmeli", detected)
    }

    @Test
    fun `readRow returns MULTI when both A and B are strongly filled`() {
        val centers = OmrTemplate.getBubbleCenters(1)
        val aCenter = centers["A"] ?: error("A şıkkı bulunamadı")
        val bCenter = centers["B"] ?: error("B şıkkı bulunamadı")

        // Hem A hem B doldurulmuş
        val bitmap = createSyntheticOpticalBitmap(
            darkenedCircles = listOf(
                Triple(aCenter.first, aCenter.second, OmrTemplate.BUBBLE_RADIUS),
                Triple(bCenter.first, bCenter.second, OmrTemplate.BUBBLE_RADIUS)
            )
        )

        val detected = BubbleReader.readRow(bitmap, centers, OmrTemplate.BUBBLE_RADIUS)
        assertEquals(BubbleReader.MULTI_MARK, detected)
    }

    @Test
    fun `readRow suppresses light smudge and chooses genuinely filled option`() {
        val centers = OmrTemplate.getBubbleCenters(2)
        val bCenter = centers["B"] ?: error("B şıkkı bulunamadı")
        val cCenter = centers["C"] ?: error("C şıkkı bulunamadı")

        // B şıkkı tam kurşun kalem siyahı (0xFF000000), C şıkkı silinmiş hafif silgi lekesi (0xFFBBBBBB)
        val mockBmp = mockk<Bitmap>()
        every { mockBmp.width } returns OmrTemplate.WARP_WIDTH
        every { mockBmp.height } returns OmrTemplate.WARP_HEIGHT

        every { mockBmp.getPixel(any(), any()) } answers {
            val px = firstArg<Int>()
            val py = secondArg<Int>()

            val bDistSq = (px - bCenter.first) * (px - bCenter.first) + (py - bCenter.second) * (py - bCenter.second)
            val cDistSq = (px - cCenter.first) * (px - cCenter.first) + (py - cCenter.second) * (py - cCenter.second)
            val rSq = OmrTemplate.BUBBLE_RADIUS * OmrTemplate.BUBBLE_RADIUS

            when {
                bDistSq <= rSq -> 0xFF141414.toInt() // Çok koyu dolgu
                cDistSq <= rSq -> 0xFFC0C0C0.toInt() // Açık gri silgi izi (luminance ~192 -> darkness ~63)
                else -> -1 // Beyaz
            }
        }

        val detected = BubbleReader.readRow(mockBmp, centers, OmrTemplate.BUBBLE_RADIUS)
        assertEquals("B", detected)
    }

    @Test
    fun `readAllQuestions processes all requested question rows`() {
        val bitmap = createSyntheticOpticalBitmap(darkenedCircles = emptyList())
        val results = BubbleReader.readAllQuestions(bitmap, 120)

        assertEquals(120, results.size)
        assertTrue("Boş kağıtta tüm sorular null olmalı", results.values.all { it == null })
    }

    // =========================================================================
    // 3. PERSPECTIVE WARPER TESTLERİ
    // =========================================================================

    @Test
    fun `warp executes with 4 corners and creates expected 1200x1700 output`() {
        val mockSource = mockk<Bitmap>()
        every { mockSource.width } returns 1080
        every { mockSource.height } returns 1920

        val mockOutput = mockk<Bitmap>(relaxed = true)
        every { mockOutput.width } returns OmrTemplate.WARP_WIDTH
        every { mockOutput.height } returns OmrTemplate.WARP_HEIGHT

        every {
            Bitmap.createBitmap(
                OmrTemplate.WARP_WIDTH,
                OmrTemplate.WARP_HEIGHT,
                Bitmap.Config.ARGB_8888
            )
        } returns mockOutput

        every { anyConstructed<Matrix>().setPolyToPoly(any(), any(), any(), any(), any()) } returns true

        val corners = listOf(
            PointF(80f, 120f),
            PointF(1000f, 110f),
            PointF(1020f, 1820f),
            PointF(90f, 1800f)
        )

        val warped = PerspectiveWarper.warp(
            source = mockSource,
            corners = corners,
            outWidth = OmrTemplate.WARP_WIDTH,
            outHeight = OmrTemplate.WARP_HEIGHT
        )

        assertNotNull(warped)
        assertEquals(OmrTemplate.WARP_WIDTH, warped.width)
        assertEquals(OmrTemplate.WARP_HEIGHT, warped.height)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `warp throws when corner count is not exactly 4`() {
        val mockSource = mockk<Bitmap>()
        PerspectiveWarper.warp(
            source = mockSource,
            corners = listOf(PointF(0f, 0f), PointF(100f, 0f), PointF(100f, 100f))
        )
    }

    // =========================================================================
    // 4. OMR MATRIX CODE CODEC & CRC8 TESTLERİ
    // =========================================================================

    @Test
    fun `encode and decode 64-bit round trip for standard exam test IDs`() {
        val testPayloads = listOf(
            "SD-EXAM-2026X8F9",
            "SD-EXAM-ABC12345",
            "SD-YKS-DENEME1",
            "SD-YKS-TYT2026",
            "sorudefteri://soru/45892",
            "sorudefteri://soru/100001",
            "CUSTOM-ID"
        )

        for (payload in testPayloads) {
            val bits64 = OmrMatrixCodeCodec.encodePayloadTo64Bits(payload)
            assertTrue("64-bit kod 0 olmamalı", bits64 != 0L)

            val decoded = OmrMatrixCodeCodec.decode64BitsToPayload(bits64)
            assertNotNull("Payload decode edilemedi: $payload", decoded)
            assertEquals("Orijinal payload ile çözülen eşleşmeli", payload, decoded)
        }
    }

    @Test
    fun `crc8 corruption triggers validation failure and returns null`() {
        val originalPayload = "SD-EXAM-SAFE1234"
        val validBits = OmrMatrixCodeCodec.encodePayloadTo64Bits(originalPayload)

        // CRC veya veri bitini boz
        val corruptedBits = validBits xor 0x01L // En düşük CRC bitini ters çevir
        val decoded = OmrMatrixCodeCodec.decode64BitsToPayload(corruptedBits)

        assertNull("Bozuk CRC-8 ile çözümleme null dönmelidir", decoded)
    }

    @Test
    fun `crc8 calculation is deterministic and non-zero for test data`() {
        val data56 = 0x00A1B2C3D4E5F6L
        val crcA = OmrMatrixCodeCodec.crc8(data56)
        val crcB = OmrMatrixCodeCodec.crc8(data56)

        assertEquals("CRC-8 deterministik olmalıdır", crcA, crcB)
        assertTrue("CRC-8 0..255 aralığında olmalıdır", crcA in 0..255)
    }

    @Test
    fun `encodeMatrixGrid builds valid 10x10 matrix with L-Finder and Timing borders`() {
        val grid = OmrMatrixCodeCodec.encodeMatrixGrid("SD-EXAM-2026X8F9")

        assertEquals(OmrMatrixCodeCodec.GRID_SIZE, grid.size)
        assertEquals(OmrMatrixCodeCodec.GRID_SIZE, grid[0].size)

        // L-Finder sol kenar (col = 0): Tüm satırlar true (siyah) olmalı
        for (r in 0 until OmrMatrixCodeCodec.GRID_SIZE) {
            assertTrue("L-Finder sol kenar ($r, 0) siyah olmalı", grid[r][0])
        }

        // L-Finder alt kenar (row = 9): Tüm sütunlar true (siyah) olmalı
        for (c in 0 until OmrMatrixCodeCodec.GRID_SIZE) {
            assertTrue("L-Finder alt kenar (9, $c) siyah olmalı", grid[OmrMatrixCodeCodec.GRID_SIZE - 1][c])
        }

        // 4 Köşe hücresi sabit siyah
        assertTrue(grid[0][0])
        assertTrue(grid[0][OmrMatrixCodeCodec.GRID_SIZE - 1])
        assertTrue(grid[OmrMatrixCodeCodec.GRID_SIZE - 1][0])
        assertTrue(grid[OmrMatrixCodeCodec.GRID_SIZE - 1][OmrMatrixCodeCodec.GRID_SIZE - 1])

        // Üst Timing kenarı: ardışık siyah-beyaz
        assertTrue(grid[0][0])
        assertFalse(grid[0][1])
        assertTrue(grid[0][2])
        assertFalse(grid[0][3])
    }
}
