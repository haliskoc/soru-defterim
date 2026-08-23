package com.sorudefteri.domain.model

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
