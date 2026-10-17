package com.sorudefteri.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Belirli bir soruya ait öğrenci ile Sokratik AI koçu arasındaki sohbet mesajlarını temsil eden Room tablosu.
 */
@Entity(
    tableName = "soru_chat_mesajlari",
    foreignKeys = [
        ForeignKey(
            entity = SoruEntity::class,
            parentColumns = ["id"],
            childColumns = ["soruId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["soruId"])]
)
data class SoruChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val soruId: Long,
    val sender: String, // "USER" or "AI"
    val messageText: String,
    val timestamp: Long = System.currentTimeMillis()
)
