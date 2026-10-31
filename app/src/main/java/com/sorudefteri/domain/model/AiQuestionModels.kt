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

/**
 * Yanlış Soru İkizi (3 Soruluk Kardeş Test) için tekil soru modeli.
 */
@Serializable
data class TwinQuestion(
    val id: Int = 1,
    val questionText: String = "",
    val options: Map<String, String> = emptyMap(), // "A" -> "...", "B" -> "..."
    val correctAnswer: String = "A",
    val solution: String = "",
    val trapWarning: String? = null
)

/**
 * 3 Soruluk Kardeş Testi paketi.
 */
@Serializable
data class TwinQuestionsResult(
    val originalTopic: String = "",
    val questions: List<TwinQuestion> = emptyList()
)
