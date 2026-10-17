package com.sorudefteri.domain.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * Soruya bağlı kalıcı benzer soru (Question Cloner çıktısı).
 * Hem AI ile türetilen hem de manuel JSON yapıştırma ile eklenen kayıtlar için kullanılır.
 */
@Immutable
@Serializable
data class BenzerSoru(
    val id: Long = 0L,
    val soruId: Long = 0L,
    val questionText: String = "",
    val options: Map<String, String> = emptyMap(),
    val correctAnswer: String = "A",
    val solution: String = "",
    val createdAt: Long = 0L
) {
    fun toSimilarResult(): SimilarQuestionResult = SimilarQuestionResult(
        questionText = questionText,
        options = options,
        correctAnswer = correctAnswer,
        solution = solution
    )
}

fun SimilarQuestionResult.toBenzerSoru(soruId: Long): BenzerSoru = BenzerSoru(
    soruId = soruId,
    questionText = questionText,
    options = options,
    correctAnswer = correctAnswer,
    solution = solution,
    createdAt = System.currentTimeMillis()
)
