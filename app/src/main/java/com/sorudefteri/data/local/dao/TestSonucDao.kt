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

    @Query("DELETE FROM test_sonuclari")
    suspend fun deleteAllTestResults()
}
