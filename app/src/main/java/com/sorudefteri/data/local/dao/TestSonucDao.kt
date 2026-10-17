package com.sorudefteri.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sorudefteri.data.local.entity.TestSonucEntity
import kotlinx.coroutines.flow.Flow

/**
 * Tamamlanan test oturumlarının veritabanı erişim arayüzü (DAO).
 */
@Dao
interface TestSonucDao {

    @Query("SELECT * FROM test_sonuclari ORDER BY completedAt DESC")
    fun getAllTestResults(): Flow<List<TestSonucEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTestResult(result: TestSonucEntity): Long

    @Query("DELETE FROM test_sonuclari WHERE id = :id")
    suspend fun deleteTestResult(id: Long)

    @Query("SELECT * FROM test_sonuclari ORDER BY completedAt DESC")
    suspend fun getAllTestResultsDirect(): List<TestSonucEntity>

    @Query("DELETE FROM test_sonuclari")
    suspend fun deleteAllTestResults()

    /** Akıllı içe aktarma için: aynı zaman damgaları + aynı sayaçlar = mükerrer kayıt. */
    @Query(
        "SELECT COUNT(*) FROM test_sonuclari WHERE completedAt = :completedAt " +
            "AND totalQuestions = :totalQuestions AND correctCount = :correctCount AND wrongCount = :wrongCount"
    )
    suspend fun existsDuplicate(completedAt: Long, totalQuestions: Int, correctCount: Int, wrongCount: Int): Boolean
}
