package com.sorudefteri.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sorudefteri.domain.model.HataTipi
import com.sorudefteri.domain.model.Soru

/**
 * Soruların Room veritabanı tablosundaki karşılığı
 */
@Entity(tableName = "sorular")
data class SoruEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val imagePath: String,
    val solutionImagePath: String? = null,
    val correctAnswer: String? = null,
    val examType: String,
    val subjectId: String,
    val topic: String,
    val mistakeType: String? = null,
    val note: String? = null,
    val isSolved: Boolean = false,
    val solvedAt: Long? = null,
    val aiExplanation: String? = null,
    val isFavorite: Boolean = false,
    val reviewStage: Int = 0,
    val nextReviewDate: Long? = null,
    val lastReviewedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * Entity'yi Domain modeline dönüştürür.
     */
    fun toDomain(): Soru {
        return Soru(
            id = id,
            imagePath = imagePath,
            solutionImagePath = solutionImagePath,
            correctAnswer = correctAnswer,
            examType = examType,
            subjectId = subjectId,
            topic = topic,
            mistakeType = mistakeType?.let { HataTipi.fromName(it) },
            note = note,
            isSolved = isSolved,
            solvedAt = solvedAt,
            aiExplanation = aiExplanation,
            isFavorite = isFavorite,
            reviewStage = reviewStage,
            nextReviewDate = nextReviewDate,
            lastReviewedAt = lastReviewedAt,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}

/**
 * Soru domain modelini Room Entity'ye dönüştürür.
 */
fun Soru.toEntity(): SoruEntity {
    return SoruEntity(
        id = id,
        imagePath = imagePath,
        solutionImagePath = solutionImagePath,
        correctAnswer = correctAnswer,
        examType = examType,
        subjectId = subjectId,
        topic = topic,
        mistakeType = mistakeType?.name,
        note = note,
        isSolved = isSolved,
        solvedAt = solvedAt,
        aiExplanation = aiExplanation,
        isFavorite = isFavorite,
        reviewStage = reviewStage,
        nextReviewDate = nextReviewDate,
        lastReviewedAt = lastReviewedAt,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

