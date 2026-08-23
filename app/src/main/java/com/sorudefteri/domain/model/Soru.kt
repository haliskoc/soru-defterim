package com.sorudefteri.domain.model

import com.sorudefteri.data.local.SoruEntity

/**
 * Domain Soru Modeli
 */
data class Soru(
    val id: Long = 0L,
    val imagePath: String,
    val solutionImagePath: String? = null,
    val correctAnswer: String? = null,
    val examType: String, // "TYT" or "AYT"
    val subjectId: String,
    val topic: String,
    val mistakeType: HataTipi? = null,
    val note: String? = null,
    val isSolved: Boolean = false,
    val solvedAt: Long? = null,
    val aiExplanation: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

fun SoruEntity.toDomain(): Soru {
    return Soru(
        id = id,
        imagePath = imagePath,
        solutionImagePath = solutionImagePath,
        correctAnswer = correctAnswer,
        examType = examType,
        subjectId = subjectId,
        topic = topic,
        mistakeType = HataTipi.fromString(mistakeType),
        note = note,
        isSolved = isSolved,
        solvedAt = solvedAt,
        aiExplanation = aiExplanation,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

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
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
