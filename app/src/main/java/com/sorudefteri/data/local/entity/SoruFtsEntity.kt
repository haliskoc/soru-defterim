package com.sorudefteri.data.local.entity

import androidx.room.Entity
import androidx.room.Fts4

@Entity(tableName = "sorular_fts")
@Fts4(contentEntity = SoruEntity::class)
data class SoruFtsEntity(
    val topic: String,
    val note: String?,
    val ocrMetni: String?,
    val questionText: String?,
    val aiExplanation: String?
)
