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

    @Query("SELECT * FROM denemeler ORDER BY tarih DESC, id DESC")
    fun getAllDenemeler(): Flow<List<DenemeSinaviEntity>>

    @Query("SELECT * FROM denemeler WHERE sinavTuru = :sinavTuru ORDER BY tarih ASC, id ASC")
    fun getDenemelerBySinavTuru(sinavTuru: String): Flow<List<DenemeSinaviEntity>>

    @Query("SELECT * FROM denemeler WHERE id = :id")
    suspend fun getDenemeById(id: Long): DenemeSinaviEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeneme(deneme: DenemeSinaviEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllDenemeler(denemeler: List<DenemeSinaviEntity>)

    @Update
    suspend fun updateDeneme(deneme: DenemeSinaviEntity)

    @Query("DELETE FROM denemeler WHERE id = :id")
    suspend fun deleteDenemeById(id: Long)

    @Query("SELECT * FROM denemeler")
    suspend fun getAllDenemelerDirect(): List<DenemeSinaviEntity>

    /** Akıllı içe aktarma: aynı tarih + başlık + net kombinasyonu = mükerrer deneme. */
    @Query("SELECT COUNT(*) FROM denemeler WHERE tarih = :tarih AND baslik = :baslik AND toplamNet = :toplamNet")
    suspend fun existsDuplicate(tarih: Long, baslik: String, toplamNet: Double): Boolean

    @Query("DELETE FROM denemeler")
    suspend fun clearAll()
}
