package com.sorudefteri.domain.repository

import com.sorudefteri.domain.model.Soru

/**
 * AI Çözüm Üretici Repository Arayüzü.
 */
interface AiRepository {
    suspend fun getAiSolution(soru: Soru): Result<String>
    suspend fun hasApiKey(): Boolean
}
