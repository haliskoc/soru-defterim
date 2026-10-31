package com.sorudefteri.data.repository

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.sorudefteri.data.images.GorselDepo
import com.sorudefteri.data.local.FtsQuerySanitizer
import com.sorudefteri.data.local.SoruIcerikHash
import com.sorudefteri.data.local.dao.BenzerSoruDao
import com.sorudefteri.data.local.dao.DenemeSinaviDao
import com.sorudefteri.data.local.dao.SoruChatDao
import com.sorudefteri.data.local.dao.SoruDao
import com.sorudefteri.data.local.dao.TestSonucDao
import com.sorudefteri.data.local.entity.SoruEntity
import com.sorudefteri.data.local.entity.TestSonucEntity
import com.sorudefteri.data.local.entity.toEntity
import com.sorudefteri.domain.DersTaksonomisi
import com.sorudefteri.domain.model.BenzerSoru
import com.sorudefteri.domain.model.SimilarQuestionResult
import com.sorudefteri.domain.model.toBenzerSoru
import com.sorudefteri.domain.model.DersSoruSayisi
import com.sorudefteri.domain.model.GunlukAktivite
import com.sorudefteri.domain.model.HataTipi
import com.sorudefteri.domain.model.HataTipiDagilimi
import com.sorudefteri.domain.model.IstatistikOzet
import com.sorudefteri.domain.model.IstatistikOzetWrapper
import com.sorudefteri.domain.model.Soru
import com.sorudefteri.domain.repository.SoruRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import androidx.paging.map
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.sorudefteri.domain.manager.BadgeManager
import com.sorudefteri.domain.manager.FsrsRating
import com.sorudefteri.domain.manager.FsrsScheduler
import javax.inject.Inject
import javax.inject.Singleton

// Data layer alias for easy access
typealias SoruRepository = com.sorudefteri.domain.repository.SoruRepository

/**
 * SoruRepository arayüzünün Room DAO ve GorselDepo kullanan somut uygulaması
 */
@Singleton
class SoruRepositoryImpl @Inject constructor(
    private val soruDao: SoruDao,
    private val testSonucDao: TestSonucDao,
    private val benzerSoruDao: BenzerSoruDao,
    private val gorselDepo: GorselDepo,
    private val badgeManager: BadgeManager? = null,
    private val soruChatDao: SoruChatDao? = null,
    private val denemeSinaviDao: DenemeSinaviDao? = null,
    @ApplicationContext private val context: Context? = null
) : com.sorudefteri.domain.repository.SoruRepository {

    override val totalCount: Flow<Int> = soruDao.getTotalCount()

    override val solvedCount: Flow<Int> = soruDao.getSolvedCount()

    override val unsolvedCount: Flow<Int> = soruDao.getUnsolvedCount()

    override fun getAllSorular(): Flow<List<Soru>> {
        return soruDao.getAllSorular().map { list ->
            list.map { it.toDomain() }
        }.flowOn(Dispatchers.Default)
    }

    override suspend fun getAllSorularOnce(): List<Soru> = withContext(Dispatchers.IO) {
        soruDao.getAllSorularList().map { it.toDomain() }
    }

    override fun getFilteredSorular(
        examType: String?,
        subjectId: String?,
        mistakeType: String?,
        isSolved: Boolean?
    ): Flow<List<Soru>> {
        return soruDao.getFilteredSorular(examType, subjectId, mistakeType, isSolved).map { list ->
            list.map { it.toDomain() }
        }.flowOn(Dispatchers.Default)
    }

    override fun searchSorular(query: String): Flow<List<Soru>> {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) {
            return getAllSorular()
        }
        val normalizedQuery = DersTaksonomisi.normalizeTurkish(cleanQuery)
        return soruDao.getAllSorular().map { list ->
            list.filter { entity ->
                DersTaksonomisi.containsTurkish(DersTaksonomisi.getNormalizedDersAdi(entity.subjectId), normalizedQuery) ||
                DersTaksonomisi.containsTurkish(entity.examType, normalizedQuery) ||
                DersTaksonomisi.containsTurkish(entity.topic, normalizedQuery) ||
                DersTaksonomisi.containsTurkish(entity.note, normalizedQuery) ||
                DersTaksonomisi.containsTurkish(entity.kaynakYayin, normalizedQuery) ||
                DersTaksonomisi.containsTurkish(entity.kaynakDeneme, normalizedQuery) ||
                DersTaksonomisi.containsTurkish(entity.questionText, normalizedQuery) ||
                DersTaksonomisi.containsTurkish(entity.ocrMetni, normalizedQuery) ||
                DersTaksonomisi.containsTurkish(entity.aiExplanation, normalizedQuery)
            }.map { it.toDomain() }
        }.flowOn(Dispatchers.Default)
    }

    override fun searchSorularFts(query: String): Flow<List<Soru>> {
        val sanitized = FtsQuerySanitizer.sanitize(query)
        if (sanitized.isBlank()) {
            return searchSorular(query)
        }
        return soruDao.searchSorularFts(sanitized).map { list ->
            list.map { it.toDomain() }
        }.flowOn(Dispatchers.Default)
    }

    private fun sanitizeFtsQuery(rawQuery: String): String = FtsQuerySanitizer.sanitize(rawQuery)

    override fun getSoruById(id: Long): Flow<Soru?> {
        return soruDao.getSoruById(id).map { it?.toDomain() }.flowOn(Dispatchers.Default)
    }

    override suspend fun getSoru(id: Long): Soru? = withContext(Dispatchers.IO) {
        soruDao.getSoruByIdDirect(id)?.toDomain()
    }

    override suspend fun soruEkle(soru: Soru): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        // Akıllı içe aktarma/de-dedup: içerik parmak izini burada kesinleştir
        val hash = if (soru.contentHash.isBlank()) SoruIcerikHash.hesapla(soru) else soru.contentHash
        val entity = soru.copy(
            contentHash = hash,
            evrenselId = soru.evrenselId.ifBlank { java.util.UUID.randomUUID().toString() },
            createdAt = if (soru.createdAt > 0) soru.createdAt else now,
            updatedAt = now
        ).toEntity()
        soruDao.insertSoru(entity)
    }

    suspend fun insertSoru(soru: SoruEntity): Long = withContext(Dispatchers.IO) {
        soruDao.insertSoru(soru)
    }

    override suspend fun soruGuncelle(soru: Soru) = withContext(Dispatchers.IO) {
        val entity = soru.copy(updatedAt = System.currentTimeMillis()).toEntity()
        soruDao.updateSoru(entity)
    }

    suspend fun updateSoru(soru: SoruEntity) = withContext(Dispatchers.IO) {
        soruDao.updateSoru(soru)
    }

    private suspend fun cleanQuestionAssociatedFilesAndData(soru: SoruEntity) {
        // 1. Görselleri temizle
        gorselDepo.gorselSil(soru.imagePath)
        if (!soru.solutionImagePath.isNullOrBlank()) {
            gorselDepo.gorselSil(soru.solutionImagePath)
        }

        // 2. Ses kaydını temizle
        soru.audioPath?.let { path ->
            if (path.isNotBlank()) {
                try {
                    val f = File(path)
                    if (f.exists()) {
                        f.delete()
                    } else if (context != null) {
                        val relF = File(context.filesDir, path)
                        if (relF.exists()) relF.delete()
                    }
                } catch (_: Exception) {}
            }
        }

        // 3. Çizim karalama (Scratchpad) dosyasını temizle
        if (context != null) {
            try {
                com.sorudefteri.ui.ortak.drawing.ScratchpadStorageHelper.clearDrawing(context, soru.id)
            } catch (_: Exception) {}
        }

        // 4. İlgili SoruChat ve BenzerSoru kayıtlarını temizle
        try {
            soruChatDao?.clearMessagesForSoru(soru.id)
        } catch (_: Exception) {}
        try {
            benzerSoruDao.deleteAllForSoru(soru.id)
        } catch (_: Exception) {}
    }

    override suspend fun soruSil(soru: Soru) = withContext(Dispatchers.IO) {
        val entity = soru.toEntity()
        cleanQuestionAssociatedFilesAndData(entity)
        soruDao.deleteSoru(entity)
    }

    suspend fun deleteSoru(soru: SoruEntity) = withContext(Dispatchers.IO) {
        cleanQuestionAssociatedFilesAndData(soru)
        soruDao.deleteSoru(soru)
    }

    override suspend fun soruSilById(id: Long) = withContext(Dispatchers.IO) {
        val soruEntity = soruDao.getSoruByIdDirect(id)
        if (soruEntity != null) {
            cleanQuestionAssociatedFilesAndData(soruEntity)
            soruDao.deleteSoruById(id)
        }
    }

    suspend fun deleteSoruById(id: Long, soru: SoruEntity?) = withContext(Dispatchers.IO) {
        val entity = soru ?: soruDao.getSoruByIdDirect(id)
        if (entity != null) {
            cleanQuestionAssociatedFilesAndData(entity)
        }
        soruDao.deleteSoruById(id)
    }

    override suspend fun cozumDurumuDegistir(id: Long, isSolved: Boolean) = withContext(Dispatchers.IO) {
        val existing = soruDao.getSoruByIdDirect(id) ?: return@withContext
        val now = System.currentTimeMillis()
        val updated = existing.copy(
            isSolved = isSolved,
            solvedAt = if (isSolved) now else null,
            updatedAt = now
        )
        soruDao.updateSoru(updated)
    }

    override suspend fun notGuncelle(id: Long, not: String?) = withContext(Dispatchers.IO) {
        val existing = soruDao.getSoruByIdDirect(id) ?: return@withContext
        val updated = existing.copy(
            note = not,
            updatedAt = System.currentTimeMillis()
        )
        soruDao.updateSoru(updated)
    }

    override suspend fun cozumGorseliGuncelle(id: Long, solutionPath: String?) = withContext(Dispatchers.IO) {
        val existing = soruDao.getSoruByIdDirect(id) ?: return@withContext
        if (!existing.solutionImagePath.isNullOrBlank() && existing.solutionImagePath != solutionPath) {
            gorselDepo.gorselSil(existing.solutionImagePath)
        }
        val updated = existing.copy(
            solutionImagePath = solutionPath,
            updatedAt = System.currentTimeMillis()
        )
        soruDao.updateSoru(updated)
    }

    override suspend fun aiCozumuKaydet(id: Long, aiExplanation: String) = withContext(Dispatchers.IO) {
        val existing = soruDao.getSoruByIdDirect(id) ?: return@withContext
        val updated = existing.copy(
            aiExplanation = aiExplanation,
            updatedAt = System.currentTimeMillis()
        )
        soruDao.updateSoru(updated)
    }

    override val favoriteCount: Flow<Int> = soruDao.getFavoriteCount()

    override val dueReviewCount: Flow<Int> = soruDao.getDueReviewCount()

    override suspend fun toggleFavorite(id: Long, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        soruDao.toggleFavorite(id, isFavorite)
    }

    override suspend fun updateReviewStatus(
        id: Long,
        stage: Int,
        nextReviewDate: Long?,
        lastReviewedAt: Long
    ) = withContext(Dispatchers.IO) {
        soruDao.updateReviewStatus(id, stage, nextReviewDate, lastReviewedAt)
    }

    override suspend fun advanceReviewStage(id: Long) = withContext(Dispatchers.IO) {
        val soru = soruDao.getSoruByIdDirect(id) ?: return@withContext
        val currentStage = soru.reviewStage
        val nextStage = (currentStage + 1).coerceAtMost(5)
        val now = System.currentTimeMillis()
        val fsrsState = FsrsScheduler.calculateNextState(
            currentStability = soru.fsrsStability,
            currentDifficulty = soru.fsrsDifficulty,
            currentReps = soru.fsrsReps,
            currentLapses = soru.fsrsLapses,
            lastReviewedAt = soru.lastReviewedAt,
            rating = FsrsRating.GOOD,
            reviewTimestamp = now
        )
        soruDao.updateSoru(
            soru.copy(
                reviewStage = nextStage,
                nextReviewDate = fsrsState.nextReviewDate,
                lastReviewedAt = now,
                fsrsStability = fsrsState.stability,
                fsrsDifficulty = fsrsState.difficulty,
                fsrsReps = fsrsState.reps,
                fsrsLapses = fsrsState.lapses,
                updatedAt = now
            )
        )
        if (currentStage < 5 && nextStage == 5) {
            badgeManager?.onMasterStageReached()
        }
    }

    override suspend fun regressReviewStage(id: Long) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val soru = soruDao.getSoruByIdDirect(id)
        if (soru != null) {
            val fsrsState = FsrsScheduler.calculateNextState(
                currentStability = soru.fsrsStability,
                currentDifficulty = soru.fsrsDifficulty,
                currentReps = soru.fsrsReps,
                currentLapses = soru.fsrsLapses,
                lastReviewedAt = soru.lastReviewedAt,
                rating = FsrsRating.AGAIN,
                reviewTimestamp = now
            )
            soruDao.updateSoru(
                soru.copy(
                    reviewStage = 0,
                    nextReviewDate = fsrsState.nextReviewDate,
                    lastReviewedAt = now,
                    fsrsStability = fsrsState.stability,
                    fsrsDifficulty = fsrsState.difficulty,
                    fsrsReps = fsrsState.reps,
                    fsrsLapses = fsrsState.lapses,
                    updatedAt = now
                )
            )
        } else {
            val nextReviewTimestamp = now + (24 * 60 * 60 * 1000L) // +1 day
            soruDao.regressReviewStageAtomik(id, nextReviewTimestamp, now)
        }
    }

    override suspend fun regressReviewStageBatch(ids: List<Long>) = withContext(Dispatchers.IO) {
        val distinctIds = ids.distinct()
        if (distinctIds.isEmpty()) return@withContext
        val now = System.currentTimeMillis()
        val nextReviewTimestamp = now + (24 * 60 * 60 * 1000L) // +1 day
        distinctIds.chunked(500).forEach { chunk ->
            soruDao.regressReviewStageBatch(chunk, nextReviewTimestamp, now)
        }
    }

    /**
     * PERF: Test bitiminde N tekil advance (N okuma + N yazma) yerine tek
     * batch sorgu. Usta-rozete (+50 XP) adaylar tek sorguyla bulunur.
     */
    override suspend fun advanceReviewStageBatch(ids: List<Long>) = withContext(Dispatchers.IO) {
        val distinctIds = ids.distinct()
        if (distinctIds.isEmpty()) return@withContext
        val now = System.currentTimeMillis()
        val nextReviewTimestamp = now + (30 * 24 * 60 * 60 * 1000L) // üst sınır +30 gün
        distinctIds.chunked(500).forEach { chunk ->
            soruDao.advanceReviewStageBatch(chunk, nextReviewTimestamp, now)
        }
    }

    override suspend fun deleteSorularByIds(ids: List<Long>) = withContext(Dispatchers.IO) {
        val distinctIds = ids.distinct()
        if (distinctIds.isEmpty()) return@withContext
        // PERF: N+1 tekil sorgu yerine chunked (500) okuma + paralel dosya temizleme
        distinctIds.chunked(500).forEach { chunk ->
            try {
                val entities = soruDao.getSorularByIds(chunk)
                entities.chunked(20).forEach { fileBatch ->
                    fileBatch.map { entity ->
                        async {
                            try {
                                cleanQuestionAssociatedFilesAndData(entity)
                            } catch (_: Exception) {}
                        }
                    }.awaitAll()
                }
            } catch (_: Exception) {}
            // SQLite IN clause limit (~999) aşılmaması için chunked silme
            soruDao.deleteSorularByIds(chunk)
        }
    }

    suspend fun deleteSorularBatch(ids: List<Long>) = deleteSorularByIds(ids)

    override suspend fun updateSolvedStatusBatch(ids: List<Long>, isSolved: Boolean) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        val now = System.currentTimeMillis()
        val solvedAt = if (isSolved) now else null
        ids.distinct().chunked(500).forEach { batch ->
            soruDao.updateSolvedStatusBatch(batch, isSolved, solvedAt, now)
        }
    }

    override suspend fun updateFavoriteStatusBatch(ids: List<Long>, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        val now = System.currentTimeMillis()
        ids.distinct().chunked(500).forEach { batch ->
            soruDao.updateFavoriteStatusBatch(batch, isFavorite, now)
        }
    }

    override suspend fun insertAllSorular(sorular: List<Soru>): List<Long> = withContext(Dispatchers.IO) {
        if (sorular.isEmpty()) return@withContext emptyList()
        val now = System.currentTimeMillis()
        val entities = sorular.map { s ->
            val hash = if (s.contentHash.isBlank()) SoruIcerikHash.hesapla(s) else s.contentHash
            s.copy(
                contentHash = hash,
                evrenselId = s.evrenselId.ifBlank { java.util.UUID.randomUUID().toString() },
                createdAt = if (s.createdAt > 0) s.createdAt else now,
                updatedAt = if (s.updatedAt > 0) s.updatedAt else now
            ).toEntity()
        }
        entities.chunked(500).flatMap { batch ->
            soruDao.insertAll(batch)
        }
    }

    /** Akıllı içe aktarma: içerik parmak izine göre var olan soruları topluca getir. */
    suspend fun getSoruEntitiesByContentHashes(hashes: List<String>): List<SoruEntity> = withContext(Dispatchers.IO) {
        if (hashes.isEmpty()) emptyList()
        else hashes.distinct().chunked(400).flatMap { batch -> soruDao.getByContentHashes(batch) }
    }

    /** Merge güncelleme: id korunur, tüm alanlar gönderilen entity ile yazılır (bağlantılı kayıtlar etkilenmez). */
    suspend fun updateSoruEntityFull(entity: SoruEntity) = withContext(Dispatchers.IO) {
        soruDao.updateEntityFull(entity)
    }

    /**
     * Tek seferlik bakım: contentHash'i boş olan (eski sürümde eklenen) tüm sorulara
     * parmak izi yazar. PERF: toplu transaction içinde yazılır (tekil UPDATE yok).
     * Uygulama açılışında bayrakla bir kez çağrılır (bkz. Application).
     */
    override suspend fun backfillContentHashes(): Int = withContext(Dispatchers.IO) {
        val rows = soruDao.getRowsWithMissingContentHash()
        if (rows.isEmpty()) return@withContext 0
        val hashes = rows.mapNotNull { row ->
            val hash = SoruIcerikHash.hesapla(row.toDomain())
            if (hash.isNotBlank()) row.id to hash else null
        }
        if (hashes.isEmpty()) return@withContext 0
        soruDao.updateContentHashes(hashes)
        hashes.size
    }

    override fun getFavoriteSorular(): Flow<List<Soru>> {
        return soruDao.getFavoriteSorular().map { list -> list.map { it.toDomain() } }.flowOn(Dispatchers.Default)
    }

    override fun getDueReviewSorular(): Flow<List<Soru>> {
        return soruDao.getDueReviewSorular().map { list -> list.map { it.toDomain() } }.flowOn(Dispatchers.Default)
    }

    override suspend fun getDueReviewSorularList(currentTime: Long): List<Soru> = withContext(Dispatchers.IO) {
        soruDao.getDueReviewSorularList(currentTime).map { it.toDomain() }
    }

    override fun getPagedSorular(
        query: String?,
        examType: String?,
        subjectId: String?,
        mistakeType: String?,
        isSolved: Boolean?,
        isFavorite: Boolean?,
        onlyDueReview: Boolean?,
        kaynak: String?,
        sortOrder: String
    ): Flow<androidx.paging.PagingData<Soru>> {
        val sanitizedFts = query?.let { sanitizeFtsQuery(it) }?.ifBlank { null }
        return androidx.paging.Pager(
            config = androidx.paging.PagingConfig(
                pageSize = 20,
                prefetchDistance = 5,
                enablePlaceholders = false,
                initialLoadSize = 40,
                maxSize = 100
            )
        ) {
            if (sanitizedFts != null) {
                soruDao.getPagedFilteredSorularFts(
                    query = sanitizedFts,
                    examType = examType,
                    subjectId = subjectId,
                    mistakeType = mistakeType,
                    isSolved = isSolved,
                    isFavorite = isFavorite,
                    onlyDueReview = onlyDueReview,
                    kaynak = kaynak,
                    sortOrder = sortOrder
                )
            } else {
                soruDao.getPagedFilteredSorular(
                    query = query,
                    examType = examType,
                    subjectId = subjectId,
                    mistakeType = mistakeType,
                    isSolved = isSolved,
                    isFavorite = isFavorite,
                    onlyDueReview = onlyDueReview,
                    kaynak = kaynak,
                    sortOrder = sortOrder
                )
            }
        }.flow.map { pagingData ->
            pagingData.map { it.toDomain() }
        }
    }

    override suspend fun tumVerileriTemizle(): Unit = withContext(Dispatchers.IO) {
        // 1. Sokratik Koç sohbet geçmişi
        try {
            soruChatDao?.getAllMessagesDirect()?.map { it.soruId }?.distinct()?.forEach { soruId ->
                soruChatDao.clearMessagesForSoru(soruId)
            }
        } catch (_: Exception) {}
        // 2. Benzer sorular
        try {
            benzerSoruDao.getAllBenzerSorularDirect().map { it.soruId }?.distinct()?.forEach { soruId ->
                benzerSoruDao.deleteAllForSoru(soruId)
            }
        } catch (_: Exception) {}
        // 3. Test sonuçları
        try {
            testSonucDao.deleteAllTestResults()
        } catch (_: Exception) {}
        // 4. Deneme sınavları
        try {
            denemeSinaviDao?.clearAll()
        } catch (_: Exception) {}
        // 5. Sorular (CASCADE tetikleyicileri ile bağlı kayıtlar çift güvencede)
        soruDao.clearAll()
        // 6. Görsel ve dosya depoları
        gorselDepo.tumGorselleriSil()
        context?.let { ctx ->
            try { File(ctx.filesDir, "audio_memos").deleteRecursively() } catch (_: Exception) {}
            try { File(ctx.filesDir, "scratchpads").deleteRecursively() } catch (_: Exception) {}
            try { File(ctx.cacheDir, "pdf").deleteRecursively() } catch (_: Exception) {}
            try { File(ctx.cacheDir, "teacher_share").deleteRecursively() } catch (_: Exception) {}
        }
        Unit
    }

    override fun getDistinctTopics(subjectId: String): Flow<List<String>> {
        return soruDao.getDistinctTopicsForSubject(subjectId).flowOn(Dispatchers.Default)
    }

    override suspend fun getKutuphaneMeta(): com.sorudefteri.domain.model.KutuphaneMeta = withContext(Dispatchers.IO) {
        kutuphaneMetaHesapla()
    }

    /**
     * PERF: DB değişimlerinde metayı yeniden yayınlar. 5 hafif sorgu
     * (COUNT×4 + DISTINCT×2) IO'da çalışır; tam tablo belleğe çekilmez.
     */
    override fun getKutuphaneMetaFlow(): Flow<com.sorudefteri.domain.model.KutuphaneMeta> {
        return combine(
            soruDao.getTotalCount(),
            soruDao.getSolvedCount(),
            soruDao.getFavoriteCount(),
            soruDao.getDueReviewCount()
        ) { total, solved, fav, due -> MetaSayilar(total, solved, fav, due) }
            .flatMapLatest { sayilar ->
                flow {
                    val publishers = try { soruDao.getDistinctPublishers() } catch (_: Exception) { emptyList() }
                    val examNames = try { soruDao.getDistinctExamNames() } catch (_: Exception) { emptyList() }
                    emit(
                        com.sorudefteri.domain.model.KutuphaneMeta(
                            totalCount = sayilar.total,
                            solvedCount = sayilar.solved,
                            unsolvedCount = (sayilar.total - sayilar.solved).coerceAtLeast(0),
                            favoriteCount = sayilar.fav,
                            dueReviewCount = sayilar.due,
                            availablePublishers = publishers,
                            availableExamNames = examNames
                        )
                    )
                }.flowOn(Dispatchers.IO)
            }
            .flowOn(Dispatchers.Default)
    }

    private data class MetaSayilar(val total: Int, val solved: Int, val fav: Int, val due: Int)

    private suspend fun kutuphaneMetaHesapla(): com.sorudefteri.domain.model.KutuphaneMeta {
        val total = soruDao.getTotalCountDirect()
        val solved = soruDao.getSolvedCountDirect()
        return com.sorudefteri.domain.model.KutuphaneMeta(
            totalCount = total,
            solvedCount = solved,
            unsolvedCount = (total - solved).coerceAtLeast(0),
            favoriteCount = soruDao.getFavoriteCountDirect(),
            dueReviewCount = soruDao.getDueReviewCountDirect(),
            availablePublishers = soruDao.getDistinctPublishers(),
            availableExamNames = soruDao.getDistinctExamNames()
        )
    }

    override suspend fun getFilteredSoruIds(
        query: String?,
        examType: String?,
        subjectId: String?,
        mistakeType: String?,
        isSolved: Boolean?,
        isFavorite: Boolean?,
        onlyDueReview: Boolean?,
        kaynak: String?
    ): List<Long> = withContext(Dispatchers.IO) {
        soruDao.getFilteredIds(
            query = query,
            examType = examType,
            subjectId = subjectId,
            mistakeType = mistakeType,
            isSolved = isSolved,
            isFavorite = isFavorite,
            onlyDueReview = onlyDueReview?.let { if (it) true else null },
            kaynak = kaynak
        )
    }

    override suspend fun getSorularByIds(ids: List<Long>): List<Soru> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyList()
        val distinctIds = ids.distinct()
        val entities = if (distinctIds.size <= 500) {
            soruDao.getSorularByIds(distinctIds)
        } else {
            distinctIds.chunked(500).flatMap { batch -> soruDao.getSorularByIds(batch) }
        }
        entities.map { it.toDomain() }
    }

    override suspend fun getAllImagePaths(): List<String> = withContext(Dispatchers.IO) {
        try {
            soruDao.getAllImagePaths()
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun countAddedBetween(start: Long, end: Long): Int = withContext(Dispatchers.IO) {
        try {
            soruDao.countAddedBetween(start, end)
        } catch (_: Exception) {
            0
        }
    }

    override suspend fun countSolvedBetween(start: Long, end: Long): Int = withContext(Dispatchers.IO) {
        try {
            soruDao.countSolvedBetween(start, end)
        } catch (_: Exception) {
            0
        }
    }

    // ThreadLocal SDF cache for stats
    private val statsDateFormatTL = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    }

    override fun getIstatistikler(): Flow<IstatistikOzetWrapper> {
        return combine(
            soruDao.getTotalCount(),
            soruDao.getSolvedCount(),
            soruDao.getSubjectDistribution(),
            soruDao.getMistakeTypeDistribution(),
            soruDao.getAllSorular()
        ) { totalCount, solvedCount, subjectTuples, mistakeTuples, allSorular ->
            val dersList = subjectTuples.map { tuple ->
                DersSoruSayisi(
                    subjectId = tuple.subjectId,
                    subjectName = DersTaksonomisi.getDersAdi(subjectId = tuple.subjectId),
                    count = tuple.count
                )
            }

            val mistakeList = mistakeTuples.mapNotNull { tuple ->
                val type = HataTipi.fromName(tuple.mistakeType)
                type?.let { HataTipiDagilimi(mistakeType = it, count = tuple.count) }
            }

            val dateFormat = statsDateFormatTL.get() ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val dailyMap = allSorular.groupBy { synchronized(dateFormat) { dateFormat.format(Date(it.createdAt)) } }
            val sonAktiviteler = dailyMap.map { (dateStr, list) ->
                GunlukAktivite(
                    dateStr = dateStr,
                    count = list.size,
                    timestamp = list.maxOfOrNull { it.createdAt } ?: 0L
                )
            }.sortedByDescending { it.timestamp }.take(14)

            IstatistikOzet(
                toplamSoru = totalCount,
                cozulenSoru = solvedCount,
                cozulmeyenSoru = (totalCount - solvedCount).coerceAtLeast(0),
                dersDagilimi = dersList,
                hataTipiDagilimi = mistakeList,
                sonAktiviteler = sonAktiviteler
            )
        }.flowOn(Dispatchers.Default)
    }

    override fun getSubjectStats(): Flow<List<DersSoruSayisi>> {
        val subjectColors = listOf(
            Color(0xFF3F51B5), Color(0xFF009688), Color(0xFFFF9800), Color(0xFFE91E63),
            Color(0xFF9C27B0), Color(0xFF4CAF50), Color(0xFF00BCD4), Color(0xFFFF5722),
            Color(0xFF795548), Color(0xFF607D8B), Color(0xFF673AB7), Color(0xFF2196F3)
        )

        return soruDao.getSubjectDistributionWithSolved().map { tuples ->
            tuples.mapIndexed { index, tuple ->
                val subjectId = tuple.subjectId
                val subjectName = DersTaksonomisi.getDersAdi(subjectId)
                val color = subjectColors[index % subjectColors.size]

                DersSoruSayisi(
                    subjectId = subjectId,
                    subjectName = subjectName,
                    count = tuple.count,
                    solvedCount = tuple.solvedCount,
                    color = color
                )
            }.sortedByDescending { it.count }
        }.flowOn(Dispatchers.Default)
    }

    override fun getMistakeStats(): Flow<List<HataTipiDagilimi>> {
        return soruDao.getMistakeTypeDistribution().map { tuples ->
            val totalMistakes = tuples.sumOf { it.count }
            if (totalMistakes == 0) {
                return@map emptyList()
            }

            tuples.map { tuple ->
                val mistakeCode = tuple.mistakeType
                val count = tuple.count
                val hataTipi = HataTipi.fromString(mistakeCode)
                val displayName = hataTipi?.displayName ?: mistakeCode
                val color = hataTipi?.defaultColor ?: Color(0xFF78909C)
                val percentage = (count.toFloat() / totalMistakes.toFloat()) * 100f

                HataTipiDagilimi(
                    mistakeType = mistakeCode,
                    displayName = displayName,
                    count = count,
                    percentage = percentage,
                    color = color
                )
            }.sortedByDescending { it.count }
        }.flowOn(Dispatchers.Default)
    }

    override fun getWeeklyActivity(): Flow<List<GunlukAktivite>> {
        return soruDao.getTotalCount().flatMapLatest {
            flow { emit(weeklyActivityHesapla()) }
        }.flowOn(Dispatchers.Default)
    }

    /**
     * PERF: Haftalık aktivite 7 gün × 2 COUNT sorgusuyla hesaplanır.
     * Önceden tüm tablo belleğe çekilip Kotlin'de 14 kez filtreleniyordu.
     */
    // NOT: withContext(IO) YOK — çağıran flowOn(defaultDispatcher)'dadır.
    // (Unit testlerde gerçek IO'ya kaçıp advanceUntilIdle'ı ıskalamasın diye.)
    private suspend fun weeklyActivityHesapla(): List<GunlukAktivite> {
        val dateFormat = statsDateFormatTL.get() ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dayNameFormat = SimpleDateFormat("EEE", Locale("tr", "TR"))
        val last7Days = mutableListOf<GunlukAktivite>()
        for (i in 6 downTo 0) {
            val dayCal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -i)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startOfDay = dayCal.timeInMillis
            dayCal.add(Calendar.DAY_OF_YEAR, 1)
            val endOfDay = dayCal.timeInMillis
            val dayDateStr = dateFormat.format(Date(startOfDay))
            val dayNameStr = dayNameFormat.format(Date(startOfDay)).replace(".", "").replaceFirstChar { it.uppercase() }
            last7Days.add(
                GunlukAktivite(
                    date = dayDateStr,
                    dayName = dayNameStr,
                    count = soruDao.countAddedBetween(startOfDay, endOfDay),
                    solvedCount = soruDao.countSolvedBetween(startOfDay, endOfDay),
                    timestamp = startOfDay
                )
            )
        }
        return last7Days
    }

    override fun getAllTestResults(): Flow<List<TestSonucEntity>> = testSonucDao.getAllTestResults()

    override fun getBenzerSorular(soruId: Long): Flow<List<BenzerSoru>> =
        benzerSoruDao.getBenzerSorularForSoru(soruId).map { list ->
            list.map { it.toDomain() }
        }.flowOn(Dispatchers.Default)

    override suspend fun saveBenzerSoru(soruId: Long, result: SimilarQuestionResult): Long =
        withContext(Dispatchers.IO) {
            val benzer = result.toBenzerSoru(soruId)
            benzerSoruDao.insertBenzerSoru(benzer.toEntity())
        }

    override suspend fun deleteBenzerSoru(id: Long) = withContext(Dispatchers.IO) {
        benzerSoruDao.deleteBenzerSoruById(id)
    }

    override suspend fun deleteAllBenzerSorular(soruId: Long) = withContext(Dispatchers.IO) {
        benzerSoruDao.deleteAllForSoru(soruId)
    }

    override fun getChatMessagesForSoru(soruId: Long): Flow<List<com.sorudefteri.data.local.entity.SoruChatMessageEntity>> {
        return soruChatDao?.getMessagesForSoru(soruId) ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }

    override suspend fun insertChatMessage(message: com.sorudefteri.data.local.entity.SoruChatMessageEntity): Long = withContext(Dispatchers.IO) {
        soruChatDao?.insertMessage(message) ?: 0L
    }

    override suspend fun clearChatMessagesForSoru(soruId: Long) = withContext(Dispatchers.IO) {
        soruChatDao?.clearMessagesForSoru(soruId)
        Unit
    }

    override fun getAllDenemeler(): Flow<List<com.sorudefteri.data.local.entity.DenemeSinaviEntity>> {
        return denemeSinaviDao?.getAllDenemeler() ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }

    override fun getDenemeByIdFlow(id: Long): Flow<com.sorudefteri.data.local.entity.DenemeSinaviEntity?> {
        return denemeSinaviDao?.getDenemeByIdFlow(id) ?: kotlinx.coroutines.flow.flowOf(null)
    }

    override suspend fun getDenemeById(id: Long): com.sorudefteri.data.local.entity.DenemeSinaviEntity? = withContext(Dispatchers.IO) {
        denemeSinaviDao?.getDenemeById(id)
    }

    override suspend fun getDenemeByTestId(testId: String): com.sorudefteri.data.local.entity.DenemeSinaviEntity? = withContext(Dispatchers.IO) {
        if (testId.isBlank()) null else denemeSinaviDao?.getByTestId(testId)
    }

    override suspend fun insertDeneme(entity: com.sorudefteri.data.local.entity.DenemeSinaviEntity): Long = withContext(Dispatchers.IO) {
        val normalized = if (entity.testId.isBlank()) {
            entity.copy(testId = "SD-EXAM-${java.util.UUID.randomUUID().toString().take(8).uppercase()}")
        } else entity
        denemeSinaviDao?.insertDeneme(normalized) ?: 0L
    }

    override suspend fun updateDeneme(entity: com.sorudefteri.data.local.entity.DenemeSinaviEntity) = withContext(Dispatchers.IO) {
        denemeSinaviDao?.updateDeneme(entity.copy(updatedAt = System.currentTimeMillis()))
        Unit
    }

    override suspend fun deleteDeneme(id: Long) = withContext(Dispatchers.IO) {
        denemeSinaviDao?.softDelete(id)
        Unit
    }

    override fun getSorularByDenemeId(denemeId: Long): Flow<List<Soru>> {
        return soruDao.getSorularByDenemeId(denemeId).map { list ->
            list.map { it.toDomain() }
        }.flowOn(Dispatchers.Default)
    }

    override suspend fun getSorularByDenemeIdList(denemeId: Long): List<Soru> = withContext(Dispatchers.IO) {
        soruDao.getSorularByDenemeIdList(denemeId).map { it.toDomain() }
    }
}
