package com.sorudefteri.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sorudefteri.data.local.entity.BenzerSoruEntity
import kotlinx.coroutines.flow.Flow

/**
 * Soruya bağlı kalıcı benzer sorular için Room DAO.
 */
@Dao
interface BenzerSoruDao {

    @Query("SELECT * FROM benzer_sorular WHERE soruId = :soruId ORDER BY createdAt DESC, id DESC")
    fun getBenzerSorularForSoru(soruId: Long): Flow<List<BenzerSoruEntity>>

    @Query("SELECT * FROM benzer_sorular WHERE soruId = :soruId ORDER BY createdAt DESC, id DESC")
    suspend fun getBenzerSorularDirect(soruId: Long): List<BenzerSoruEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBenzerSoru(entity: BenzerSoruEntity): Long

    @Query("DELETE FROM benzer_sorular WHERE id = :id")
    suspend fun deleteBenzerSoruById(id: Long)

    @Query("DELETE FROM benzer_sorular WHERE soruId = :soruId")
    suspend fun deleteAllForSoru(soruId: Long)

    @Query("SELECT COUNT(*) FROM benzer_sorular WHERE soruId = :soruId")
    fun getCountForSoru(soruId: Long): Flow<Int>

    @Query("SELECT * FROM benzer_sorular ORDER BY createdAt DESC, id DESC")
    suspend fun getAllBenzerSorularDirect(): List<BenzerSoruEntity>

    /** Akıllı içe aktarma: aynı soru + aynı metin + aynı tarih = mükerrer benzer soru. */
    @Query(
        "SELECT COUNT(*) FROM benzer_sorular WHERE soruId = :soruId AND questionText = :questionText " +
            "AND createdAt = :createdAt"
    )
    suspend fun existsDuplicate(soruId: Long, questionText: String, createdAt: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllBenzerSorular(list: List<BenzerSoruEntity>)
}
