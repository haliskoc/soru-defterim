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
    val isFavorite: Boolean = false,
    val reviewStage: Int = 0, // 0: Yeni, 1: 1 Gün, 2: 3 Gün, 3: 7 Gün, 4: 14 Gün, 5: 30 Gün (Usta)
    val nextReviewDate: Long? = null, // Sonraki tekrar zamanı (timestamp)
    val lastReviewedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * Sorunun tekrar zamanının gelip gelmediğini kontrol eder.
     */
    val isDueForReview: Boolean
        get() {
            val next = nextReviewDate ?: return !isSolved
            return System.currentTimeMillis() >= next
        }
}

