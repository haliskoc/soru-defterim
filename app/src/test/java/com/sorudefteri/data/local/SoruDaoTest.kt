package com.sorudefteri.data.local

import com.sorudefteri.data.images.GorselDepo
import com.sorudefteri.data.local.dao.MistakeCountTuple
import com.sorudefteri.data.local.dao.SoruDao
import com.sorudefteri.data.local.dao.SubjectCountTuple
import com.sorudefteri.data.local.entity.SoruEntity
import com.sorudefteri.data.local.entity.toEntity
import com.sorudefteri.data.repository.SoruRepositoryImpl
import com.sorudefteri.domain.model.HataTipi
import com.sorudefteri.domain.model.Soru
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

/**
 * Soru DAO ve Repository seviyesinde veri tabanı işlemlerini,
 * filtrelemeyi, aramayı ve Spaced Repetition sorgularını doğrulayan kapsamlı birim testleri.
 */
class SoruDaoTest {

    private lateinit var fakeDao: FakeSoruDao
    private lateinit var mockGorselDepo: GorselDepo
    private lateinit var repository: SoruRepositoryImpl

    @Before
    fun setUp() {
        fakeDao = FakeSoruDao()
        mockGorselDepo = mockk(relaxed = true)
        repository = SoruRepositoryImpl(fakeDao, mockGorselDepo)
    }

    @Test
    fun soruEkle_ve_idIleGetir_tumAlanlarEksiksizDoner() = runTest {
        val soru = Soru(
            id = 0L,
            imagePath = "/images/soru1.jpg",
            solutionImagePath = "/images/cozum1.jpg",
            correctAnswer = "C",
            examType = "TYT",
            subjectId = "matematik",
            topic = "Fonksiyonlar",
            mistakeType = HataTipi.DIKKAT_HATASI,
            note = "Grafik okuma hatası yapıldı",
            isSolved = false,
            solvedAt = null,
            aiExplanation = "Grafikte f(2) değeri 5'e eşittir.",
            isFavorite = true,
            reviewStage = 1,
            nextReviewDate = System.currentTimeMillis() + 86400000L,
            lastReviewedAt = System.currentTimeMillis(),
            kaynakYayin = "3D Yayınları",
            kaynakDeneme = "TYT Genel Deneme 1",
            kaynakYil = 2026,
            kaynakSoruNo = 15,
            ocrMetni = "f(x) = 2x + 1 olduğuna göre",
            isOcrUsed = true,
            questionText = "f(x) fonksiyonunun tepe noktası"
        )

        val insertedId = repository.soruEkle(soru)
        assertTrue("Eklenen sorunun ID'si pozitif olmalı", insertedId > 0)

        val fetched = repository.getSoru(insertedId)
        assertNotNull("Eklenen soru veritabanından çekilebilmeli", fetched)
        assertEquals("/images/soru1.jpg", fetched?.imagePath)
        assertEquals("/images/cozum1.jpg", fetched?.solutionImagePath)
        assertEquals("C", fetched?.correctAnswer)
        assertEquals("TYT", fetched?.examType)
        assertEquals("matematik", fetched?.subjectId)
        assertEquals("Fonksiyonlar", fetched?.topic)
        assertEquals(HataTipi.DIKKAT_HATASI, fetched?.mistakeType)
        assertEquals("Grafik okuma hatası yapıldı", fetched?.note)
        assertFalse(fetched!!.isSolved)
        assertTrue(fetched.isFavorite)
        assertEquals(1, fetched.reviewStage)
        assertEquals("3D Yayınları", fetched.kaynakYayin)
        assertEquals(15, fetched.kaynakSoruNo)
        assertEquals("f(x) = 2x + 1 olduğuna göre", fetched.ocrMetni)
        assertTrue(fetched.isOcrUsed)
    }

    @Test
    fun soruGuncelle_guncelVerilerVeZamanDamgasiKaydedilir() = runTest {
        val soru = Soru(
            imagePath = "/images/soru2.jpg",
            examType = "AYT",
            subjectId = "fizik",
            topic = "Elektrik ve Manyetizma",
            isSolved = false
        )
        val id = repository.soruEkle(soru)
        val inserted = repository.getSoru(id)!!

        val updated = inserted.copy(
            isSolved = true,
            solvedAt = System.currentTimeMillis(),
            note = "Konu pekiştirildi",
            correctAnswer = "E",
            isFavorite = true
        )
        repository.soruGuncelle(updated)

        val result = repository.getSoru(id)
        assertNotNull(result)
        assertTrue(result!!.isSolved)
        assertNotNull(result.solvedAt)
        assertEquals("Konu pekiştirildi", result.note)
        assertEquals("E", result.correctAnswer)
        assertTrue(result.isFavorite)
    }

    @Test
    fun soruSil_veritabanindanVeGorselDepodanSilinir() = runTest {
        val soru = Soru(
            imagePath = "/images/soru3.jpg",
            solutionImagePath = "/images/cozum3.jpg",
            examType = "TYT",
            subjectId = "turkce",
            topic = "Paragrafta Anlam"
        )
        val id = repository.soruEkle(soru)
        val inserted = repository.getSoru(id)!!

        repository.soruSil(inserted)

        val deleted = repository.getSoru(id)
        assertNull("Silinen soru veritabanında bulunamamalı", deleted)
        coVerify(exactly = 1) { mockGorselDepo.gorselSil("/images/soru3.jpg") }
        coVerify(exactly = 1) { mockGorselDepo.gorselSil("/images/cozum3.jpg") }
    }

    @Test
    fun soruSilById_idIleGorsellerVeKayitTemizlenir() = runTest {
        val soru = Soru(
            imagePath = "/images/soru4.jpg",
            solutionImagePath = "/images/cozum4.jpg",
            examType = "AYT",
            subjectId = "kimya",
            topic = "Organik Kimya"
        )
        val id = repository.soruEkle(soru)

        repository.soruSilById(id)

        val deleted = repository.getSoru(id)
        assertNull(deleted)
        coVerify(exactly = 1) { mockGorselDepo.gorselSil("/images/soru4.jpg") }
        coVerify(exactly = 1) { mockGorselDepo.gorselSil("/images/cozum4.jpg") }
    }

    @Test
    fun cokluFiltreleme_ders_sinavTipi_hataTipi_cozumDurumuDogruFiltrelenir() = runTest {
        val s1 = Soru(imagePath = "1", examType = "TYT", subjectId = "matematik", topic = "Sayılar", mistakeType = HataTipi.BILGI_EKSIKLIGI, isSolved = false)
        val s2 = Soru(imagePath = "2", examType = "TYT", subjectId = "fizik", topic = "Optik", mistakeType = HataTipi.DIKKAT_HATASI, isSolved = true)
        val s3 = Soru(imagePath = "3", examType = "AYT", subjectId = "matematik", topic = "Türev", mistakeType = HataTipi.ISLEM_HATASI, isSolved = false)
        val s4 = Soru(imagePath = "4", examType = "AYT", subjectId = "fizik", topic = "Dalgalar", mistakeType = HataTipi.BILGI_EKSIKLIGI, isSolved = false)

        repository.insertAllSorular(listOf(s1, s2, s3, s4))

        // 1. Sadece TYT
        val tytSorulari = repository.getFilteredSorular(examType = "TYT", subjectId = null, mistakeType = null, isSolved = null).first()
        assertEquals(2, tytSorulari.size)

        // 2. Matematik dersi
        val matSorulari = repository.getFilteredSorular(examType = null, subjectId = "matematik", mistakeType = null, isSolved = null).first()
        assertEquals(2, matSorulari.size)

        // 3. AYT + Matematik
        val aytMat = repository.getFilteredSorular(examType = "AYT", subjectId = "matematik", mistakeType = null, isSolved = null).first()
        assertEquals(1, aytMat.size)
        assertEquals("Türev", aytMat[0].topic)

        // 4. Hata tipi: BILGI_EKSIKLIGI
        val bilgiEksigi = repository.getFilteredSorular(examType = null, subjectId = null, mistakeType = HataTipi.BILGI_EKSIKLIGI.name, isSolved = null).first()
        assertEquals(2, bilgiEksigi.size)

        // 5. Çözülmemiş sorular
        val cozulmemis = repository.getFilteredSorular(examType = null, subjectId = null, mistakeType = null, isSolved = false).first()
        assertEquals(3, cozulmemis.size)
    }

    @Test
    fun aramaVeFavoriListeleme_queryEslestirmeVeFavoriler() = runTest {
        val s1 = Soru(imagePath = "1", examType = "TYT", subjectId = "matematik", topic = "Trigonometri", note = "Formül unutuldu", isFavorite = true)
        val s2 = Soru(imagePath = "2", examType = "TYT", subjectId = "fizik", topic = "Vektörler", note = "Bileşke kuvvet hesabı", isFavorite = false)
        val s3 = Soru(imagePath = "3", examType = "AYT", subjectId = "biyoloji", topic = "Kalıtım", note = "Mendel genetiği", isFavorite = true)

        repository.insertAllSorular(listOf(s1, s2, s3))

        // Arama testi (Konu veya Not içinde)
        val aramaTrig = repository.searchSorular("Trigono").first()
        assertEquals(1, aramaTrig.size)
        assertEquals("Trigonometri", aramaTrig[0].topic)

        val aramaFormul = repository.searchSorular("Formül").first()
        assertEquals(1, aramaFormul.size)
        assertEquals("Trigonometri", aramaFormul[0].topic)

        val aramaGenetik = repository.searchSorular("genetiği").first()
        assertEquals(1, aramaGenetik.size)
        assertEquals("Kalıtım", aramaGenetik[0].topic)

        // Favori listeleme
        val favoriler = repository.getFavoriteSorular().first()
        assertEquals(2, favoriler.size)
        assertTrue(favoriler.all { it.isFavorite })

        val favoriteCount = repository.favoriteCount.first()
        assertEquals(2, favoriteCount)
    }

    @Test
    fun spacedRepetitionTekrarSorgulari_dueReviewSorularVeSayilari() = runTest {
        val now = System.currentTimeMillis()
        val oneDay = 86400000L

        val sGecmis = Soru(imagePath = "1", examType = "TYT", subjectId = "mat", topic = "T1", nextReviewDate = now - oneDay, isSolved = true)
        val sSimdi = Soru(imagePath = "2", examType = "TYT", subjectId = "fiz", topic = "T2", nextReviewDate = now - 1000L, isSolved = true)
        val sGelecek = Soru(imagePath = "3", examType = "AYT", subjectId = "kim", topic = "T3", nextReviewDate = now + oneDay, isSolved = true)
        val sTarihsizCozulmus = Soru(imagePath = "4", examType = "AYT", subjectId = "biy", topic = "T4", nextReviewDate = null, isSolved = true)

        repository.insertAllSorular(listOf(sGecmis, sSimdi, sGelecek, sTarihsizCozulmus))

        val dueList = repository.getDueReviewSorularList(now)
        assertEquals("Zamanı dolmuş 2 soru olmalı", 2, dueList.size)
        assertTrue(dueList.any { it.topic == "T1" })
        assertTrue(dueList.any { it.topic == "T2" })
        assertFalse(dueList.any { it.topic == "T3" })

        val dueCount = repository.dueReviewCount.first()
        assertEquals(2, dueCount)
    }

    @Test
    fun advanceReviewStage_kademeyiArttirirVeTarihOteliyor() = runTest {
        val now = System.currentTimeMillis()
        val soru = Soru(imagePath = "1", examType = "TYT", subjectId = "mat", topic = "Logaritma", reviewStage = 0)
        val id = repository.soruEkle(soru)

        // Stage 0 -> 1 (+1 Gün)
        repository.advanceReviewStage(id)
        val stage1 = repository.getSoru(id)!!
        assertEquals(1, stage1.reviewStage)
        assertNotNull(stage1.nextReviewDate)
        assertTrue(stage1.nextReviewDate!! >= now + (86400000L - 5000L))

        // Stage 1 -> 2 (+3 Gün)
        repository.advanceReviewStage(id)
        val stage2 = repository.getSoru(id)!!
        assertEquals(2, stage2.reviewStage)

        // Stage 2 -> 3 (+7 Gün)
        repository.advanceReviewStage(id)
        val stage3 = repository.getSoru(id)!!
        assertEquals(3, stage3.reviewStage)

        // Stage 3 -> 4 (+14 Gün)
        repository.advanceReviewStage(id)
        val stage4 = repository.getSoru(id)!!
        assertEquals(4, stage4.reviewStage)

        // Stage 4 -> 5 (+30 Gün)
        repository.advanceReviewStage(id)
        val stage5 = repository.getSoru(id)!!
        assertEquals(5, stage5.reviewStage)

        // Stage 5 -> 5 (Maksimum sınır)
        repository.advanceReviewStage(id)
        val stageMax = repository.getSoru(id)!!
        assertEquals("Kademe maksimum 5'i geçemez", 5, stageMax.reviewStage)
    }

    @Test
    fun topluIslemler_batchDeleteBatchSolveBatchFavoriteDogruCalisir() = runTest {
        val s1 = Soru(imagePath = "1", examType = "TYT", subjectId = "mat", topic = "A", isSolved = false, isFavorite = false)
        val s2 = Soru(imagePath = "2", examType = "TYT", subjectId = "fiz", topic = "B", isSolved = false, isFavorite = false)
        val s3 = Soru(imagePath = "3", examType = "TYT", subjectId = "kim", topic = "C", isSolved = false, isFavorite = false)

        val id1 = repository.soruEkle(s1)
        val id2 = repository.soruEkle(s2)
        val id3 = repository.soruEkle(s3)

        // Toplu çözüldü yap
        repository.updateSolvedStatusBatch(listOf(id1, id2), true)
        assertTrue(repository.getSoru(id1)!!.isSolved)
        assertTrue(repository.getSoru(id2)!!.isSolved)
        assertFalse(repository.getSoru(id3)!!.isSolved)

        // Toplu favorile
        repository.updateFavoriteStatusBatch(listOf(id2, id3), true)
        assertFalse(repository.getSoru(id1)!!.isFavorite)
        assertTrue(repository.getSoru(id2)!!.isFavorite)
        assertTrue(repository.getSoru(id3)!!.isFavorite)

        // Toplu sil
        repository.deleteSorularByIds(listOf(id1, id3))
        assertNull(repository.getSoru(id1))
        assertNotNull(repository.getSoru(id2))
        assertNull(repository.getSoru(id3))
    }
}

/**
 * Room DAO davranışını in-memory simüle eden yüksek sadakatli Fake DAO.
 */
class FakeSoruDao : SoruDao {
    private val data = mutableMapOf<Long, SoruEntity>()
    private val idCounter = AtomicLong(1L)
    private val flowEmitter = MutableStateFlow<List<SoruEntity>>(emptyList())

    private fun emit() {
        flowEmitter.value = data.values.toList()
    }

    override suspend fun insertSoru(soru: SoruEntity): Long {
        val id = if (soru.id > 0) soru.id else idCounter.getAndIncrement()
        val entity = soru.copy(id = id)
        data[id] = entity
        emit()
        return id
    }

    override suspend fun updateSoru(soru: SoruEntity) {
        data[soru.id] = soru
        emit()
    }

    override suspend fun deleteSoru(soru: SoruEntity) {
        data.remove(soru.id)
        emit()
    }

    override suspend fun deleteSoruById(id: Long) {
        data.remove(id)
        emit()
    }

    override fun getSoruById(id: Long): Flow<SoruEntity?> {
        return flowEmitter.map { list -> list.find { it.id == id } }
    }

    override suspend fun getSoruByIdDirect(id: Long): SoruEntity? {
        return data[id]
    }

    override fun getAllSorular(): Flow<List<SoruEntity>> {
        return flowEmitter
    }

    override fun getFilteredSorular(
        examType: String?,
        subjectId: String?,
        mistakeType: String?,
        isSolved: Boolean?
    ): Flow<List<SoruEntity>> {
        return flowEmitter.map { list ->
            list.filter { item ->
                (examType == null || item.examType.equals(examType, ignoreCase = true)) &&
                        (subjectId == null || item.subjectId.equals(subjectId, ignoreCase = true)) &&
                        (mistakeType == null || item.mistakeType.equals(mistakeType, ignoreCase = true)) &&
                        (isSolved == null || item.isSolved == isSolved)
            }
        }
    }

    override fun searchSorular(query: String): Flow<List<SoruEntity>> {
        return flowEmitter.map { list ->
            list.filter { item ->
                item.topic.contains(query, ignoreCase = true) ||
                        (item.note?.contains(query, ignoreCase = true) == true) ||
                        item.subjectId.contains(query, ignoreCase = true) ||
                        (item.questionText?.contains(query, ignoreCase = true) == true) ||
                        (item.ocrMetni?.contains(query, ignoreCase = true) == true)
            }
        }
    }

    override fun getDistinctTopicsForSubject(subjectId: String): Flow<List<String>> {
        return flowEmitter.map { list ->
            list.filter { it.subjectId == subjectId && it.topic.isNotBlank() }
                .map { it.topic }
                .distinct()
        }
    }

    override suspend fun getAllSorularList(): List<SoruEntity> {
        return data.values.toList()
    }

    override suspend fun getSorularByIds(ids: List<Long>): List<SoruEntity> {
        return data.values.filter { it.id in ids }
    }

    override fun getTotalCount(): Flow<Int> = flowEmitter.map { it.size }

    override fun getSolvedCount(): Flow<Int> = flowEmitter.map { list -> list.count { it.isSolved } }

    override fun getUnsolvedCount(): Flow<Int> = flowEmitter.map { list -> list.count { !it.isSolved } }

    override fun getSubjectDistribution(): Flow<List<SubjectCountTuple>> {
        return flowEmitter.map { list ->
            list.groupBy { it.subjectId }.map { SubjectCountTuple(it.key, it.value.size) }
        }
    }

    override fun getMistakeTypeDistribution(): Flow<List<MistakeCountTuple>> {
        return flowEmitter.map { list ->
            list.filter { !it.mistakeType.isNullOrBlank() }
                .groupBy { it.mistakeType!! }
                .map { MistakeCountTuple(it.key, it.value.size) }
        }
    }

    override suspend fun insertAll(sorular: List<SoruEntity>): List<Long> {
        return sorular.map { insertSoru(it) }
    }

    override suspend fun toggleFavorite(id: Long, isFavorite: Boolean, updatedAt: Long) {
        val existing = data[id] ?: return
        data[id] = existing.copy(isFavorite = isFavorite, updatedAt = updatedAt)
        emit()
    }

    override suspend fun updateReviewStatus(
        id: Long,
        stage: Int,
        nextReviewDate: Long?,
        lastReviewedAt: Long,
        updatedAt: Long
    ) {
        val existing = data[id] ?: return
        data[id] = existing.copy(
            reviewStage = stage,
            nextReviewDate = nextReviewDate,
            lastReviewedAt = lastReviewedAt,
            updatedAt = updatedAt
        )
        emit()
    }

    override suspend fun deleteSorularByIds(ids: List<Long>) {
        ids.forEach { data.remove(it) }
        emit()
    }

    override suspend fun updateSolvedStatusBatch(
        ids: List<Long>,
        isSolved: Boolean,
        solvedAt: Long?,
        updatedAt: Long
    ) {
        ids.forEach { id ->
            val existing = data[id]
            if (existing != null) {
                data[id] = existing.copy(isSolved = isSolved, solvedAt = solvedAt, updatedAt = updatedAt)
            }
        }
        emit()
    }

    override suspend fun updateFavoriteStatusBatch(ids: List<Long>, isFavorite: Boolean, updatedAt: Long) {
        ids.forEach { id ->
            val existing = data[id]
            if (existing != null) {
                data[id] = existing.copy(isFavorite = isFavorite, updatedAt = updatedAt)
            }
        }
        emit()
    }

    override fun getFavoriteSorular(): Flow<List<SoruEntity>> {
        return flowEmitter.map { list -> list.filter { it.isFavorite } }
    }

    override fun getDueReviewSorular(currentTime: Long): Flow<List<SoruEntity>> {
        return flowEmitter.map { list ->
            list.filter { it.nextReviewDate != null && it.nextReviewDate!! <= currentTime }
        }
    }

    override suspend fun getDueReviewSorularList(currentTime: Long): List<SoruEntity> {
        return data.values.filter { it.nextReviewDate != null && it.nextReviewDate!! <= currentTime }
    }

    override fun getPagedFilteredSorular(
        query: String?,
        examType: String?,
        subjectId: String?,
        mistakeType: String?,
        isSolved: Boolean?,
        isFavorite: Boolean?,
        kaynak: String?
    ): androidx.paging.PagingSource<Int, SoruEntity> {
        throw UnsupportedOperationException("PagingSource unit test için mock kullanılmalıdır.")
    }

    override fun getFavoriteCount(): Flow<Int> = flowEmitter.map { list -> list.count { it.isFavorite } }

    override fun getDueReviewCount(currentTime: Long): Flow<Int> {
        return flowEmitter.map { list ->
            list.count { it.nextReviewDate != null && it.nextReviewDate!! <= currentTime }
        }
    }

    override suspend fun getDueReviewCountDirect(currentTime: Long): Int {
        return data.values.count { it.nextReviewDate != null && it.nextReviewDate!! <= currentTime }
    }

    override suspend fun clearAll() {
        data.clear()
        emit()
    }

    override suspend fun updateCozumDurumuAtomik(id: Long, isSolved: Boolean, now: Long) {
        val existing = data[id] ?: return
        data[id] = existing.copy(isSolved = isSolved, solvedAt = if (isSolved) now else null, updatedAt = now)
        emit()
    }

    override suspend fun updateFavoriteAtomik(id: Long, isFavorite: Boolean, now: Long) {
        val existing = data[id] ?: return
        data[id] = existing.copy(isFavorite = isFavorite, updatedAt = now)
        emit()
    }

    override suspend fun advanceReviewStageAtomik(id: Long, nextReviewDate: Long, now: Long) {
        val existing = data[id] ?: return
        val newStage = if (existing.reviewStage < 5) existing.reviewStage + 1 else 5
        data[id] = existing.copy(reviewStage = newStage, nextReviewDate = nextReviewDate, lastReviewedAt = now, updatedAt = now)
        emit()
    }

    override suspend fun updateNoteAtomik(id: Long, note: String?, now: Long) {
        val existing = data[id] ?: return
        data[id] = existing.copy(note = note, updatedAt = now)
        emit()
    }

    override suspend fun updateAiExplanationAtomik(id: Long, explanation: String, now: Long) {
        val existing = data[id] ?: return
        data[id] = existing.copy(aiExplanation = explanation, updatedAt = now)
        emit()
    }
}
