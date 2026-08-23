package com.sorudefteri.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sorudefteri.domain.model.TestResult

/**
 * Kullanıcının tamamladığı test ve denemelerin Room veritabanı kayıt modeli.
 */
@Entity(tableName = "test_sonuclari")
data class TestSonucEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val totalQuestions: Int,
    val correctCount: Int,
    val wrongCount: Int,
    val emptyCount: Int,
    val netCount: Float,
    val successRate: Int,
    val totalTimeSeconds: Long,
    val examType: String? = null,
    val completedAt: Long = System.currentTimeMillis()
)
