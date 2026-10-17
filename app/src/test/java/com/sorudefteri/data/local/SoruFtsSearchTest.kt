package com.sorudefteri.data.local

import com.sorudefteri.data.images.GorselDepo
import com.sorudefteri.data.local.dao.SoruDao
import com.sorudefteri.data.local.entity.SoruEntity
import com.sorudefteri.data.local.entity.SoruFtsEntity
import com.sorudefteri.data.repository.SoruRepositoryImpl
import com.sorudefteri.domain.model.HataTipi
import com.sorudefteri.domain.model.Soru
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SoruFtsSearchTest {

    private lateinit var fakeDao: FakeSoruDao
    private lateinit var mockGorselDepo: GorselDepo
    private lateinit var repository: SoruRepositoryImpl

    @Before
    fun setUp() {
        fakeDao = FakeSoruDao()
        mockGorselDepo = mockk(relaxed = true)
        repository = SoruRepositoryImpl(fakeDao, mockk(relaxed = true), mockk(relaxed = true), mockGorselDepo)
    }

    @Test
    fun `soruFtsEntity holds required text fields for full-text indexing`() {
        val fts = SoruFtsEntity(
            topic = "Trigonometri",
            note = "Toplam fark formülleri",
            ocrMetni = "sin(a+b) = sin a cos b + cos a sin b",
            questionText = "İfadesinin en sade hali nedir?",
            aiExplanation = "Toplam formülü uygulandığında sonuç 1 çıkar."
        )

        assertEquals("Trigonometri", fts.topic)
        assertEquals("Toplam fark formülleri", fts.note)
        assertEquals("sin(a+b) = sin a cos b + cos a sin b", fts.ocrMetni)
        assertEquals("İfadesinin en sade hali nedir?", fts.questionText)
        assertEquals("Toplam formülü uygulandığında sonuç 1 çıkar.", fts.aiExplanation)
    }

    @Test
    fun `searchSorularFts finds question matching OCR text`() = runTest {
        val soru = Soru(
            id = 1L,
            imagePath = "/images/soru_ocr.webp",
            examType = "AYT",
            subjectId = "matematik",
            topic = "Polinomlar",
            ocrMetni = "P(x) polinomunun x-2 ile bolumunden kalan 7 dir",
            questionText = "P(3) degeri kactir?"
        )
        repository.soruEkle(soru)

        val results = repository.searchSorularFts("bolumunden kalan").first()
        assertEquals(1, results.size)
        assertEquals("Polinomlar", results[0].topic)
        assertTrue(results[0].ocrMetni?.contains("bolumunden") == true)
    }

    @Test
    fun `searchSorularFts finds question matching AI explanation`() = runTest {
        val soru = Soru(
            id = 2L,
            imagePath = "/images/soru_ai.webp",
            examType = "TYT",
            subjectId = "fizik",
            topic = "Optik",
            aiExplanation = "Snell yasası n1*sin(teta1) = n2*sin(teta2) bağıntısı kullanılır."
        )
        repository.soruEkle(soru)

        val results = repository.searchSorularFts("Snell yasası").first()
        assertEquals(1, results.size)
        assertEquals("Optik", results[0].topic)
        assertTrue(results[0].aiExplanation?.contains("Snell") == true)
    }

    @Test
    fun `searchSorularFts finds question matching question text`() = runTest {
        val soru = Soru(
            id = 3L,
            imagePath = "/images/soru_q.webp",
            examType = "AYT",
            subjectId = "biyoloji",
            topic = "Fotosentez",
            questionText = "Klorofil molekülünün yapısında magnezyum atomu bulunur mu?"
        )
        repository.soruEkle(soru)

        val results = repository.searchSorularFts("magnezyum").first()
        assertEquals(1, results.size)
        assertEquals("Fotosentez", results[0].topic)
    }

    @Test
    fun `searchSorularFts finds question matching note`() = runTest {
        val soru = Soru(
            id = 4L,
            imagePath = "/images/soru_note.webp",
            examType = "TYT",
            subjectId = "turkce",
            topic = "Yazım Kuralları",
            note = "Şey sözcüğü daima ayrı yazılır: her şey, hiçbir şey"
        )
        repository.soruEkle(soru)

        val results = repository.searchSorularFts("ayrı yazılır").first()
        assertEquals(1, results.size)
        assertEquals("Yazım Kuralları", results[0].topic)
    }

    @Test
    fun `searchSorularFts handles special characters and quotes without crashing`() = runTest {
        val soru = Soru(
            id = 5L,
            imagePath = "/images/soru_special.webp",
            examType = "AYT",
            subjectId = "kimya",
            topic = "Kimyasal Denge",
            ocrMetni = "Kc = [NO2]^2 / [N2O4] denge sabiti hesabı"
        )
        repository.soruEkle(soru)

        // Special characters like ^, [], *, :, -, quotes should be sanitized safely
        val results = repository.searchSorularFts("denge: \"hesabı\"*").first()
        assertEquals(1, results.size)
        assertEquals("Kimyasal Denge", results[0].topic)
    }

    @Test
    fun `searchSorularFts returns empty list when query does not match any field`() = runTest {
        val soru = Soru(
            id = 6L,
            imagePath = "/images/soru_geo.webp",
            examType = "TYT",
            subjectId = "geometri",
            topic = "Üçgende Alan",
            questionText = "Taban çarpı yükseklik bölü 2 formülü"
        )
        repository.soruEkle(soru)

        val results = repository.searchSorularFts("Kuantum Mekaniği").first()
        assertTrue("Non-matching search should return empty list", results.isEmpty())
    }
}
