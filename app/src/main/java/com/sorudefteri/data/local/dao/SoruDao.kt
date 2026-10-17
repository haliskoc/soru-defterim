package com.sorudefteri.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.sorudefteri.data.local.entity.SoruEntity
import kotlinx.coroutines.flow.Flow

// FTS - Full-text search: sorular_fts (FTS4) topic, note, ocrMetni, questionText, aiExplanation alanlarında.
// v7->v8 ile gelen cihazlarda senkron tetikleyiciler MIGRATION_8_9 ile onarılır.

/**
 * Ders bazlı soru sayısı özeti için Room Tuple
 */
data class SubjectCountTuple(
    val subjectId: String,
    val count: Int
)

/**
 * Hata tipi bazlı soru sayısı özeti için Room Tuple
 */
data class MistakeCountTuple(
    val mistakeType: String,
    val count: Int
)

/**
 * Ders bazlı soru ve çözülen sayısı özeti için Room Tuple
 */
data class SubjectSolvedCountTuple(
    val subjectId: String,
    val count: Int,
    val solvedCount: Int
)

@Dao
interface SoruDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSoru(soru: SoruEntity): Long

    @Query("UPDATE sorular SET contentHash = :hash, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateContentHash(id: Long, hash: String, updatedAt: Long = System.currentTimeMillis())

    /** Merge güncellemesi: mevcut kaydın tüm alanlarını gönderilen entity ile değiştir. */
    @Update
    suspend fun updateEntityFull(soru: SoruEntity)

    @Update
    suspend fun updateSoru(soru: SoruEntity)

    @Delete
    suspend fun deleteSoru(soru: SoruEntity)

    @Query("DELETE FROM sorular WHERE id = :id")
    suspend fun deleteSoruById(id: Long)

    @Query("SELECT * FROM sorular WHERE id = :id")
    fun getSoruById(id: Long): Flow<SoruEntity?>

    @Query("SELECT * FROM sorular WHERE id = :id")
    suspend fun getSoruByIdDirect(id: Long): SoruEntity?

    @Query("SELECT * FROM sorular ORDER BY createdAt DESC")
    fun getAllSorular(): Flow<List<SoruEntity>>

    @Query("SELECT * FROM sorular ORDER BY createdAt DESC")
    suspend fun getAllSorularList(): List<SoruEntity>

    @Query("SELECT * FROM sorular WHERE id IN (:ids)")
    suspend fun getSorularByIds(ids: List<Long>): List<SoruEntity>

    @Query("""
        SELECT * FROM sorular
        WHERE (:examType IS NULL OR examType = :examType)
          AND (:subjectId IS NULL OR subjectId = :subjectId)
          AND (:mistakeType IS NULL OR mistakeType = :mistakeType)
          AND (:isSolved IS NULL OR isSolved = :isSolved)
        ORDER BY createdAt DESC
    """)
    fun getFilteredSorular(
        examType: String?,
        subjectId: String?,
        mistakeType: String?,
        isSolved: Boolean?
    ): Flow<List<SoruEntity>>

    @Query("""
        SELECT * FROM sorular
        WHERE topic LIKE '%' || :query || '%'
           OR note LIKE '%' || :query || '%'
           OR subjectId LIKE '%' || :query || '%'
           OR kaynakYayin LIKE '%' || :query || '%'
           OR kaynakDeneme LIKE '%' || :query || '%'
           OR ocrMetni LIKE '%' || :query || '%'
           OR questionText LIKE '%' || :query || '%'
           OR aiExplanation LIKE '%' || :query || '%'
           OR examType LIKE '%' || :query || '%'
        ORDER BY createdAt DESC
    """)
    fun searchSorular(query: String): Flow<List<SoruEntity>>

    @Query("""
        SELECT s.* FROM sorular s
        JOIN sorular_fts fts ON s.id = fts.docid
        WHERE sorular_fts MATCH :query
        ORDER BY s.createdAt DESC
    """)
    fun searchSorularFts(query: String): Flow<List<SoruEntity>>

    @Query("SELECT DISTINCT topic FROM sorular WHERE subjectId = :subjectId AND topic != '' ORDER BY topic ASC")
    fun getDistinctTopicsForSubject(subjectId: String): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM sorular")
    fun getTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sorular WHERE isSolved = 1")
    fun getSolvedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sorular WHERE isSolved = 0")
    fun getUnsolvedCount(): Flow<Int>

    @Query("SELECT subjectId, COUNT(*) as count FROM sorular GROUP BY subjectId ORDER BY count DESC")
    fun getSubjectDistribution(): Flow<List<SubjectCountTuple>>

    @Query("SELECT subjectId, COUNT(*) as count, COALESCE(SUM(CASE WHEN isSolved = 1 THEN 1 ELSE 0 END), 0) as solvedCount FROM sorular GROUP BY subjectId ORDER BY count DESC")
    fun getSubjectDistributionWithSolved(): Flow<List<SubjectSolvedCountTuple>>

    @Query("SELECT mistakeType, COUNT(*) as count FROM sorular WHERE mistakeType IS NOT NULL AND mistakeType != '' GROUP BY mistakeType ORDER BY count DESC")
    fun getMistakeTypeDistribution(): Flow<List<MistakeCountTuple>>

    @Query("SELECT COUNT(*) FROM sorular WHERE isSolved = 1 AND mistakeType = :mistakeType")
    suspend fun getSolvedCountByMistakeType(mistakeType: String): Int

    @Query("SELECT COUNT(*) FROM sorular WHERE isSolved = 1 AND (LOWER(subjectId) LIKE '%geometri%' OR LOWER(topic) LIKE '%geometri%')")
    suspend fun getSolvedGeometriCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sorular: List<SoruEntity>): List<Long>

    @Query("UPDATE sorular SET isFavorite = :isFavorite, updatedAt = :updatedAt WHERE id = :id")
    suspend fun toggleFavorite(id: Long, isFavorite: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE sorular SET reviewStage = :stage, nextReviewDate = :nextReviewDate, lastReviewedAt = :lastReviewedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateReviewStatus(
        id: Long,
        stage: Int,
        nextReviewDate: Long?,
        lastReviewedAt: Long,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Transaction
    @Query("DELETE FROM sorular WHERE id IN (:ids)")
    suspend fun deleteSorularByIds(ids: List<Long>)

    @Transaction
    @Query("UPDATE sorular SET isSolved = :isSolved, solvedAt = :solvedAt, updatedAt = :updatedAt WHERE id IN (:ids)")
    suspend fun updateSolvedStatusBatch(
        ids: List<Long>,
        isSolved: Boolean,
        solvedAt: Long?,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Transaction
    @Query("UPDATE sorular SET isFavorite = :isFavorite, updatedAt = :updatedAt WHERE id IN (:ids)")
    suspend fun updateFavoriteStatusBatch(
        ids: List<Long>,
        isFavorite: Boolean,
        updatedAt: Long = System.currentTimeMillis()
    )

    // Atomik tekil güncellemeler (race-safe)
    @Query("UPDATE sorular SET isSolved = :isSolved, solvedAt = CASE WHEN :isSolved = 1 THEN :now ELSE NULL END, updatedAt = :now WHERE id = :id")
    suspend fun updateCozumDurumuAtomik(id: Long, isSolved: Boolean, now: Long = System.currentTimeMillis())

    @Query("UPDATE sorular SET isFavorite = :isFavorite, updatedAt = :now WHERE id = :id")
    suspend fun updateFavoriteAtomik(id: Long, isFavorite: Boolean, now: Long = System.currentTimeMillis())

    @Query("UPDATE sorular SET reviewStage = CASE WHEN reviewStage < 5 THEN reviewStage + 1 ELSE 5 END, nextReviewDate = :nextReviewDate, lastReviewedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun advanceReviewStageAtomik(id: Long, nextReviewDate: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE sorular SET reviewStage = 0, nextReviewDate = :nextReviewDate, lastReviewedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun regressReviewStageAtomik(id: Long, nextReviewDate: Long, now: Long = System.currentTimeMillis())

    @Transaction
    @Query("UPDATE sorular SET reviewStage = 0, nextReviewDate = :nextReviewDate, lastReviewedAt = :now, updatedAt = :now WHERE id IN (:ids)")
    suspend fun regressReviewStageBatch(ids: List<Long>, nextReviewDate: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE sorular SET note = :note, updatedAt = :now WHERE id = :id")
    suspend fun updateNoteAtomik(id: Long, note: String?, now: Long = System.currentTimeMillis())

    @Query("UPDATE sorular SET aiExplanation = :explanation, updatedAt = :now WHERE id = :id")
    suspend fun updateAiExplanationAtomik(id: Long, explanation: String, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM sorular WHERE isFavorite = 1 ORDER BY createdAt DESC")
    fun getFavoriteSorular(): Flow<List<SoruEntity>>

    @Query("SELECT * FROM sorular WHERE (nextReviewDate IS NOT NULL AND nextReviewDate <= :currentTime) OR (nextReviewDate IS NULL AND isSolved = 0) ORDER BY CASE WHEN nextReviewDate IS NULL THEN 0 ELSE 1 END, nextReviewDate ASC")
    fun getDueReviewSorular(currentTime: Long = System.currentTimeMillis()): Flow<List<SoruEntity>>

    @Query("SELECT * FROM sorular WHERE (nextReviewDate IS NOT NULL AND nextReviewDate <= :currentTime) OR (nextReviewDate IS NULL AND isSolved = 0) ORDER BY CASE WHEN nextReviewDate IS NULL THEN 0 ELSE 1 END, nextReviewDate ASC")
    suspend fun getDueReviewSorularList(currentTime: Long = System.currentTimeMillis()): List<SoruEntity>

    @Query("""
        SELECT * FROM sorular
        WHERE (:query IS NULL OR :query = '' OR topic LIKE '%' || :query || '%' OR note LIKE '%' || :query || '%' OR subjectId LIKE '%' || :query || '%' OR kaynakYayin LIKE '%' || :query || '%' OR kaynakDeneme LIKE '%' || :query || '%' OR ocrMetni LIKE '%' || :query || '%' OR questionText LIKE '%' || :query || '%' OR aiExplanation LIKE '%' || :query || '%' OR examType LIKE '%' || :query || '%')
          AND (:examType IS NULL OR examType = :examType)
          AND (:subjectId IS NULL OR subjectId = :subjectId)
          AND (:mistakeType IS NULL OR mistakeType = :mistakeType)
          AND (:isSolved IS NULL OR isSolved = :isSolved)
          AND (:isFavorite IS NULL OR isFavorite = :isFavorite)
          AND (:onlyDueReview IS NULL OR :onlyDueReview = 0 OR ((nextReviewDate IS NOT NULL AND nextReviewDate <= :currentTime) OR (nextReviewDate IS NULL AND isSolved = 0)))
          AND (:kaynak IS NULL OR :kaynak = '' OR kaynakYayin LIKE '%' || :kaynak || '%' OR kaynakDeneme LIKE '%' || :kaynak || '%')
        ORDER BY createdAt DESC
    """)
    fun getPagedFilteredSorular(
        query: String? = null,
        examType: String? = null,
        subjectId: String? = null,
        mistakeType: String? = null,
        isSolved: Boolean? = null,
        isFavorite: Boolean? = null,
        onlyDueReview: Boolean? = null,
        currentTime: Long = System.currentTimeMillis(),
        kaynak: String? = null
    ): androidx.paging.PagingSource<Int, SoruEntity>

    @Query("""
        SELECT s.* FROM sorular s
        JOIN sorular_fts fts ON s.id = fts.docid
        WHERE sorular_fts MATCH :query
          AND (:examType IS NULL OR s.examType = :examType)
          AND (:subjectId IS NULL OR s.subjectId = :subjectId)
          AND (:mistakeType IS NULL OR s.mistakeType = :mistakeType)
          AND (:isSolved IS NULL OR s.isSolved = :isSolved)
          AND (:isFavorite IS NULL OR s.isFavorite = :isFavorite)
          AND (:onlyDueReview IS NULL OR :onlyDueReview = 0 OR ((s.nextReviewDate IS NOT NULL AND s.nextReviewDate <= :currentTime) OR (s.nextReviewDate IS NULL AND s.isSolved = 0)))
          AND (:kaynak IS NULL OR :kaynak = '' OR s.kaynakYayin LIKE '%' || :kaynak || '%' OR s.kaynakDeneme LIKE '%' || :kaynak || '%')
        ORDER BY s.createdAt DESC
    """)
    fun getPagedFilteredSorularFts(
        query: String,
        examType: String? = null,
        subjectId: String? = null,
        mistakeType: String? = null,
        isSolved: Boolean? = null,
        isFavorite: Boolean? = null,
        onlyDueReview: Boolean? = null,
        currentTime: Long = System.currentTimeMillis(),
        kaynak: String? = null
    ): androidx.paging.PagingSource<Int, SoruEntity>

    @Query("SELECT COUNT(*) FROM sorular WHERE isFavorite = 1")
    fun getFavoriteCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sorular WHERE (nextReviewDate IS NOT NULL AND nextReviewDate <= :currentTime) OR (nextReviewDate IS NULL AND isSolved = 0)")
    fun getDueReviewCount(currentTime: Long = System.currentTimeMillis()): Flow<Int>

    @Query("SELECT COUNT(*) FROM sorular WHERE (nextReviewDate IS NOT NULL AND nextReviewDate <= :currentTime) OR (nextReviewDate IS NULL AND isSolved = 0)")
    suspend fun getDueReviewCountDirect(currentTime: Long = System.currentTimeMillis()): Int

    // ===== Akıllı içe aktarma (dedup/merge) sorguları =====

    @Query("SELECT * FROM sorular WHERE contentHash IN (:hashes)")
    suspend fun getByContentHashes(hashes: List<String>): List<SoruEntity>

    @Query("SELECT * FROM sorular WHERE contentHash = :hash LIMIT 1")
    suspend fun getFirstByContentHash(hash: String): SoruEntity?

    @Query("SELECT * FROM sorular WHERE contentHash = '' OR contentHash IS NULL")
    suspend fun getRowsWithMissingContentHash(): List<SoruEntity>

    @Query("DELETE FROM sorular")
    suspend fun clearAll()
}
