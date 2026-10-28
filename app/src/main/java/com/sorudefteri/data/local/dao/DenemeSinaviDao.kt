package com.sorudefteri.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sorudefteri.data.local.entity.DenemeSinaviEntity
import kotlinx.coroutines.flow.Flow

/**
 * Deneme sınavları için Room DAO arayüzü
 */
@Dao
interface DenemeSinaviDao {

    @Query("SELECT * FROM denemeler WHERE isDeleted = 0 ORDER BY tarih DESC, id DESC")
    fun getAllDenemeler(): Flow<List<DenemeSinaviEntity>>

    @Query("SELECT * FROM denemeler WHERE isDeleted = 0 AND sinavTuru = :sinavTuru ORDER BY tarih ASC, id ASC")
    fun getDenemelerBySinavTuru(sinavTuru: String): Flow<List<DenemeSinaviEntity>>

    @Query("SELECT * FROM denemeler WHERE id = :id AND isDeleted = 0")
    suspend fun getDenemeById(id: Long): DenemeSinaviEntity?

    @Query("SELECT * FROM denemeler WHERE id = :id AND isDeleted = 0")
    fun getDenemeByIdFlow(id: Long): Flow<DenemeSinaviEntity?>

    @Query("SELECT * FROM denemeler WHERE testId = :testId AND isDeleted = 0 ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getByTestId(testId: String): DenemeSinaviEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeneme(deneme: DenemeSinaviEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllDenemeler(denemeler: List<DenemeSinaviEntity>)

    @Update
    suspend fun updateDeneme(deneme: DenemeSinaviEntity)

    @Query("UPDATE denemeler SET isDeleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM denemeler WHERE id = :id")
    suspend fun deleteDenemeById(id: Long)

    @Query("SELECT * FROM denemeler WHERE isDeleted = 0")
    suspend fun getAllDenemelerDirect(): List<DenemeSinaviEntity>

    /** Akıllı içe aktarma: aynı tarih + başlık + net kombinasyonu = mükerrer deneme. */
    @Query("SELECT COUNT(*) FROM denemeler WHERE isDeleted = 0 AND ( (testId != '' AND testId = :testId) OR (tarih = :tarih AND baslik = :baslik AND toplamNet = :toplamNet) )")
    suspend fun existsDuplicate(tarih: Long, baslik: String, toplamNet: Double, testId: String = ""): Boolean

    @Query("DELETE FROM denemeler")
    suspend fun clearAll()
}
