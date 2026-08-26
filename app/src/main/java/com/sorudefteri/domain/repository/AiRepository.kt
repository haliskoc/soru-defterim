package com.sorudefteri.domain.repository

import com.sorudefteri.domain.model.AiProviderType
import com.sorudefteri.domain.model.QuestionMetadata
import com.sorudefteri.domain.model.SimilarQuestionResult
import com.sorudefteri.domain.model.Soru

/**
 * AI Çözüm Üretici, Otomatik Etiketleme ve Soru Klonlama Repository Arayüzü.
 */
interface AiRepository {
    suspend fun getAiSolution(soru: Soru): Result<String>
    suspend fun detectQuestionMetadata(imageBytes: ByteArray): Result<QuestionMetadata>
    suspend fun generateSimilarQuestion(imageBytes: ByteArray, dersAdi: String, konu: String): Result<SimilarQuestionResult>
    suspend fun hasApiKey(): Boolean
    suspend fun fetchLiveModels(provider: AiProviderType): Result<List<Pair<String, String>>>
}
