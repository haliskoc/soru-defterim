package com.sorudefteri.domain.repository

import com.sorudefteri.domain.model.AiProviderType
import com.sorudefteri.domain.model.QuestionMetadata
import com.sorudefteri.domain.model.SimilarQuestionResult
import com.sorudefteri.domain.model.Soru

/**
 * AI Çözüm Üretici, Otomatik Etiketleme, Soru Üretimi, OCR Temizleme ve Soru Klonlama Repository Arayüzü.
 */
interface AiRepository {
    /**
     * Soru nesnesi üzerinden AI çözümü üretir.
     * @param sendImage Görselin AI'ya iletilip iletilmeyeceği (Koşullu OCR akışında false seçilerek sadece metin gönderilebilir).
     * @param extractedText Varsa OCR ile ayıklanmış ve kullanıcı tarafından düzenlenmiş soru metni.
     */
    suspend fun getAiSolution(
        soru: Soru,
        sendImage: Boolean = true,
        extractedText: String? = null
    ): Result<String>

    /**
     * Sadece metin içeriği üzerinden adım adım AI çözümü üretir.
     */
    suspend fun getAiSolutionFromText(
        dersAdi: String,
        konu: String,
        soruMetni: String,
        correctAnswer: String? = null,
        mistakeType: String? = null
    ): Result<String>

    /**
     * OCR ile okunan ham ve bozuk metni temizler, formülleri LaTeX formatına çevirir.
     */
    suspend fun cleanOcrText(rawOcrText: String): Result<String>

    /**
     * Görsel ve/veya soru metninden yola çıkarak birebir benzer soru türetir (Question Cloner).
     */
    suspend fun generateSimilarQuestion(
        imageBytes: ByteArray?,
        dersAdi: String,
        konu: String,
        soruMetni: String? = null
    ): Result<SimilarQuestionResult>

    /**
     * imageBytes ByteArray olan eski imza ile tam geriye dönük uyumluluk.
     */
    suspend fun generateSimilarQuestion(
        imageBytes: ByteArray,
        dersAdi: String,
        konu: String
    ): Result<SimilarQuestionResult>

    /**
     * Konu, zorluk, alan ve sınav tipine göre sıfırdan özgün soru üretir.
     */
    suspend fun generateQuestion(
        dersAdi: String,
        konu: String,
        zorluk: String = "ÖSYM Tarzı",
        alan: String = "Sayısal",
        sinavTipi: String = "TYT"
    ): Result<SimilarQuestionResult>

    /**
     * Soru görselinden sınav türü, ders, konu ve cevap şıkkını tespit eder (Auto-Tagging).
     */
    suspend fun detectQuestionMetadata(imageBytes: ByteArray): Result<QuestionMetadata>

    /**
     * Aktif sağlayıcı için API anahtarı girilip girilmediğini kontrol eder.
     */
    suspend fun hasApiKey(): Boolean

    /**
     * Sağlayıcıdan canlı güncel modelleri çeker.
     */
    suspend fun fetchLiveModels(provider: AiProviderType): Result<List<Pair<String, String>>>
}
