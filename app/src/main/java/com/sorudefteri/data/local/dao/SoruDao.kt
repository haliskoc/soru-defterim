package com.sorudefteri.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sorudefteri.data.local.entity.SoruEntity
import kotlinx.coroutines.flow.Flow

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

@Dao
interface SoruDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSoru(soru: SoruEntity): Long

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
        ORDER BY createdAt DESC
    """)
    fun searchSorular(query: String): Flow<List<SoruEntity>>

    @Query("SELECT DISTINCT topic FROM sorular WHERE subjectId = :subjectId AND topic != '' ORDER BY topic ASC")
    fun getDistinctTopicsForSubject(subjectId: String): Flow<List<String>>

    @Query("SELECT DISTINCT topic FROM sorular WHERE subjectId = :subjectId AND topic != '' ORDER BY topic ASC")
    fun getDistinctTopicsBySubject(subjectId: String): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM sorular")
    fun getTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sorular WHERE isSolved = 1")
    fun getSolvedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sorular WHERE isSolved = 0")
    fun getUnsolvedCount(): Flow<Int>

    @Query("SELECT subjectId, COUNT(*) as count FROM sorular GROUP BY subjectId ORDER BY count DESC")
    fun getSubjectDistribution(): Flow<List<SubjectCountTuple>>

    @Query("SELECT mistakeType, COUNT(*) as count FROM sorular WHERE mistakeType IS NOT NULL AND mistakeType != '' GROUP BY mistakeType ORDER BY count DESC")
    fun getMistakeTypeDistribution(): Flow<List<MistakeCountTuple>>

    @Query("DELETE FROM sorular")
    suspend fun clearAll()

    @Query("DELETE FROM sorular")
    suspend fun deleteAllSorular()
}
