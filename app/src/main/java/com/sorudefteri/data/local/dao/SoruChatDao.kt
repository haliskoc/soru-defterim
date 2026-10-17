package com.sorudefteri.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sorudefteri.data.local.entity.SoruChatMessageEntity
import kotlinx.coroutines.flow.Flow

/**
 * Soru sohbet mesajları için Room DAO arayüzü.
 */
@Dao
interface SoruChatDao {

    @Query("SELECT * FROM soru_chat_mesajlari WHERE soruId = :soruId ORDER BY timestamp ASC, id ASC")
    fun getMessagesForSoru(soruId: Long): Flow<List<SoruChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: SoruChatMessageEntity): Long

    @Query("DELETE FROM soru_chat_mesajlari WHERE soruId = :soruId")
    suspend fun clearMessagesForSoru(soruId: Long)

    @Query("SELECT * FROM soru_chat_mesajlari WHERE soruId = :soruId ORDER BY timestamp ASC, id ASC")
    suspend fun getMessagesDirect(soruId: Long): List<SoruChatMessageEntity>

    @Query("SELECT * FROM soru_chat_mesajlari ORDER BY timestamp ASC, id ASC")
    suspend fun getAllMessagesDirect(): List<SoruChatMessageEntity>

    /** Akıllı içe aktarma: aynı soru + zaman + gönderen + mesaj = mükerrer koç mesajı. */
    @Query(
        "SELECT COUNT(*) FROM soru_chat_mesajlari WHERE soruId = :soruId AND timestamp = :timestamp " +
            "AND sender = :sender AND messageText = :messageText"
    )
    suspend fun existsDuplicate(soruId: Long, timestamp: Long, sender: String, messageText: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllMessages(messages: List<SoruChatMessageEntity>)
}
