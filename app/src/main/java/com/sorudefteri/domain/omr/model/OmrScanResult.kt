package com.sorudefteri.domain.omr.model

import com.sorudefteri.domain.model.DenemeDersNeti
import kotlinx.serialization.Serializable

/**
 * Bir optik form sorusunun değerlendirme durumu.
 */
@Serializable
enum class OmrAnswerStatus {
    CORRECT,        // 🟩 Doğru
    WRONG,          // 🟥 Yanlış
    EMPTY,          // ⬜ Boş
    MULTI_INVALID   // 🟥 Çift İşaret (Geçersiz / Yanlış sayılır)
}

/**
 * Tek bir sorunun optik okuma ve cevap anahtarı karşılaştırma sonucu.
 */
@Serializable
data class OmrQuestionEvaluation(
    val questionNumber: Int,
    val studentAnswer: String?,
    val correctAnswer: String?,
    val status: OmrAnswerStatus,
    val dersAdi: String = "",
    val subjectId: String = "",
    val topic: String = ""
) {
    val isCorrect: Boolean get() = status == OmrAnswerStatus.CORRECT
    val isWrong: Boolean get() = status == OmrAnswerStatus.WRONG || status == OmrAnswerStatus.MULTI_INVALID
    val isEmpty: Boolean get() = status == OmrAnswerStatus.EMPTY
    val isActionableForLibrary: Boolean get() = !isCorrect
}

/**
 * Kamera tabanlı OMR (Optik Okuma) taramasının tam değerlendirme ve net karnesi modeli.
 */
@Serializable
data class OmrScanResult(
    val testId: String,
    val denemeId: Long? = null,
    val baslik: String = "Optik Sınav Tarama Sonucu",
    val yayinEvi: String = "",
    val sinavTuru: String = "TYT",
    val totalQuestions: Int = 120,
    val studentAnswers: Map<Int, String?> = emptyMap(), // null = Boş, "MULTI" = Çift İşaret
    val correctAnswers: Map<Int, String> = emptyMap(),
    val questionResults: List<OmrQuestionEvaluation> = emptyList(),
    val toplamDogru: Int = 0,
    val toplamYanlis: Int = 0,
    val toplamBos: Int = 0,
    val toplamNet: Double = 0.0,
    val dersSonuclari: List<DenemeDersNeti> = emptyList(),
    val scanTimestamp: Long = System.currentTimeMillis()
) {
    val basariYuzdesi: Float
        get() = if (totalQuestions > 0) {
            ((toplamNet.coerceAtLeast(0.0) / totalQuestions.toDouble()) * 100.0).toFloat().coerceIn(0f, 100f)
        } else 0f
}
