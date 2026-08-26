package com.sorudefteri.domain.model

import kotlinx.serialization.Serializable

/**
 * AI tarafından görselden otomatik tespit edilen soru üst verileri (Auto-Tagging).
 */
@Serializable
data class QuestionMetadata(
    val examType: String = "TYT",
    val subjectId: String = "matematik",
    val topic: String = "",
    val correctAnswer: String? = null
)

/**
 * AI tarafından türetilen benzer soru (Question Cloner).
 */
@Serializable
data class SimilarQuestionResult(
    val questionText: String = "",
    val options: Map<String, String> = emptyMap(), // "A" -> "...", "B" -> "..."
    val correctAnswer: String = "A",
    val solution: String = ""
)
