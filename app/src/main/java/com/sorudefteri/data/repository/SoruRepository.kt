package com.sorudefteri.data.repository

import androidx.compose.ui.graphics.Color
import com.sorudefteri.data.images.GorselDepo
import com.sorudefteri.data.local.dao.SoruDao
import com.sorudefteri.data.local.entity.SoruEntity
import com.sorudefteri.data.local.entity.toEntity
import com.sorudefteri.domain.DersTaksonomisi
import com.sorudefteri.domain.model.DersSoruSayisi
import com.sorudefteri.domain.model.GunlukAktivite
import com.sorudefteri.domain.model.HataTipi
import com.sorudefteri.domain.model.HataTipiDagilimi
import com.sorudefteri.domain.model.IstatistikOzet
import com.sorudefteri.domain.model.IstatistikOzetWrapper
import com.sorudefteri.domain.model.Soru
import com.sorudefteri.domain.repository.SoruRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import androidx.paging.map
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
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
    private val gorselDepo: GorselDepo
) : com.sorudefteri.domain.repository.SoruRepository {

    override val totalCount: Flow<Int> = soruDao.getTotalCount()

    override val solvedCount: Flow<Int> = soruDao.getSolvedCount()

    override val unsolvedCount: Flow<Int> = soruDao.getUnsolvedCount()

    override fun getAllSorular(): Flow<List<Soru>> {
        return soruDao.getAllSorular().map { list ->
            list.map { it.toDomain() }
        }.flowOn(Dispatchers.Default)
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
        return soruDao.searchSorular(query).map { list ->
            list.map { it.toDomain() }
        }.flowOn(Dispatchers.Default)
    }

    override fun getSoruById(id: Long): Flow<Soru?> {
        return soruDao.getSoruById(id).map { it?.toDomain() }.flowOn(Dispatchers.Default)
    }

    override suspend fun getSoru(id: Long): Soru? = withContext(Dispatchers.IO) {
        soruDao.getSoruByIdDirect(id)?.toDomain()
    }

    override suspend fun soruEkle(soru: Soru): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val entity = soru.copy(
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

    override suspend fun soruSil(soru: Soru) = withContext(Dispatchers.IO) {
        // Görselleri yerel depodan temizle
        gorselDepo.gorselSil(soru.imagePath)
        if (!soru.solutionImagePath.isNullOrBlank()) {
            gorselDepo.gorselSil(soru.solutionImagePath)
        }
        soruDao.deleteSoru(soru.toEntity())
    }

    suspend fun deleteSoru(soru: SoruEntity) = withContext(Dispatchers.IO) {
        gorselDepo.gorselSil(soru.imagePath)
        soru.solutionImagePath?.let { gorselDepo.gorselSil(it) }
        soruDao.deleteSoru(soru)
    }

    override suspend fun soruSilById(id: Long) = withContext(Dispatchers.IO) {
        val soruEntity = soruDao.getSoruByIdDirect(id)
        if (soruEntity != null) {
            gorselDepo.gorselSil(soruEntity.imagePath)
            if (!soruEntity.solutionImagePath.isNullOrBlank()) {
                gorselDepo.gorselSil(soruEntity.solutionImagePath)
            }
            soruDao.deleteSoruById(id)
        }
    }

    suspend fun deleteSoruById(id: Long, soru: SoruEntity?) = withContext(Dispatchers.IO) {
        soru?.let {
            gorselDepo.gorselSil(it.imagePath)
            it.solutionImagePath?.let { sol -> gorselDepo.gorselSil(sol) }
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
        // Atomik increment: read current stage via direct query then compute next review date
        val soru = soruDao.getSoruByIdDirect(id) ?: return@withContext
        val currentStage = soru.reviewStage
        val nextStage = (currentStage + 1).coerceAtMost(5)
        val now = System.currentTimeMillis()
        val intervalDays = when (nextStage) {
            1 -> 1L
            2 -> 3L
            3 -> 7L
            4 -> 14L
            else -> 30L
        }
        val nextReviewTimestamp = now + (intervalDays * 24 * 60 * 60 * 1000L)
        // Atomik güncelleme (race-safe) - DAO'daki atomik sorgu
        soruDao.advanceReviewStageAtomik(id, nextReviewTimestamp, now)
    }

    override suspend fun deleteSorularByIds(ids: List<Long>) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        // Görselleri temizle
        ids.forEach { id ->
            val soru = soruDao.getSoruByIdDirect(id)
            if (soru != null) {
                gorselDepo.gorselSil(soru.imagePath)
                if (!soru.solutionImagePath.isNullOrBlank()) {
                    gorselDepo.gorselSil(soru.solutionImagePath)
                }
            }
        }
        // SQLite IN clause limit (~999) aşılmaması için chunked
        ids.chunked(500).forEach { chunk ->
            soruDao.deleteSorularByIds(chunk)
        }
    }

    override suspend fun updateSolvedStatusBatch(ids: List<Long>, isSolved: Boolean) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        val now = System.currentTimeMillis()
        soruDao.updateSolvedStatusBatch(ids, isSolved, if (isSolved) now else null)
    }

    override suspend fun updateFavoriteStatusBatch(ids: List<Long>, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        soruDao.updateFavoriteStatusBatch(ids, isFavorite)
    }

    override suspend fun insertAllSorular(sorular: List<Soru>): List<Long> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val entities = sorular.map { s ->
            s.copy(
                createdAt = if (s.createdAt > 0) s.createdAt else now,
                updatedAt = now
            ).toEntity()
        }
        soruDao.insertAll(entities)
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
        kaynak: String?
    ): Flow<androidx.paging.PagingData<Soru>> {
        return androidx.paging.Pager(
            config = androidx.paging.PagingConfig(
                pageSize = 20,
                enablePlaceholders = false,
                initialLoadSize = 40
            )
        ) {
            soruDao.getPagedFilteredSorular(
                query = query,
                examType = examType,
                subjectId = subjectId,
                mistakeType = mistakeType,
                isSolved = isSolved,
                isFavorite = isFavorite,
                kaynak = kaynak
            )
        }.flow.map { pagingData ->
            pagingData.map { it.toDomain() }
        }
    }

    override suspend fun tumVerileriTemizle(): Unit = withContext(Dispatchers.IO) {
        soruDao.clearAll()
        gorselDepo.tumGorselleriSil()
        Unit
    }

    override fun getDistinctTopics(subjectId: String): Flow<List<String>> {
        return soruDao.getDistinctTopicsForSubject(subjectId).flowOn(Dispatchers.Default)
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

        return soruDao.getAllSorular().map { list ->
            if (list.isEmpty()) return@map emptyList()

            val grouped = list.groupBy { it.subjectId }
            grouped.entries.mapIndexed { index, entry ->
                val subjectId = entry.key
                val subjectQuestions = entry.value
                val count = subjectQuestions.size
                val solved = subjectQuestions.count { it.isSolved }
                val subjectName = DersTaksonomisi.getDersAdi(subjectId)
                val color = subjectColors[index % subjectColors.size]

                DersSoruSayisi(
                    subjectId = subjectId,
                    subjectName = subjectName,
                    count = count,
                    solvedCount = solved,
                    color = color
                )
            }.sortedByDescending { it.count }
        }.flowOn(Dispatchers.Default)
    }

    override fun getMistakeStats(): Flow<List<HataTipiDagilimi>> {
        return soruDao.getAllSorular().map { list ->
            val mistakeQuestions = list.filter { !it.mistakeType.isNullOrBlank() }
            val totalMistakes = mistakeQuestions.size

            if (totalMistakes == 0) {
                return@map emptyList()
            }

            val grouped = mistakeQuestions.groupBy { it.mistakeType!! }

            grouped.entries.map { entry ->
                val mistakeCode = entry.key
                val count = entry.value.size
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
        return soruDao.getAllSorular().map { list ->
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

                val addedCount = list.count { it.createdAt in startOfDay until endOfDay }
                val solvedCount = list.count {
                    it.isSolved && it.solvedAt != null && it.solvedAt in startOfDay until endOfDay
                }

                last7Days.add(
                    GunlukAktivite(
                        date = dayDateStr,
                        dayName = dayNameStr,
                        count = addedCount,
                        solvedCount = solvedCount,
                        timestamp = startOfDay
                    )
                )
            }

            last7Days
        }.flowOn(Dispatchers.Default)
    }
}
