package com.sorudefteri.domain.model

import androidx.compose.runtime.Immutable

/**
 * Test oluşturma filtreleme kriterleri
 *
 * @property examType Sınav türü ("TYT", "AYT" veya null: Tümü)
 * @property selectedSubjects Seçilen ders kimlikleri (örn. "matematik", "fizik")
 * @property selectedTopics Seçilen konu başlıkları
 * @property selectedMistakeTypes Seçilen hata tipleri
 * @property onlyUnsolved Sadece çözülmemiş soruları dahil et
 * @property questionCount Testteki hedeflenen soru sayısı (örn: 5, 10, 15, 20, 0: Tümü)
 * @property timeLimitPerQuestionSeconds Soru başına tanınan süre limiti (0 = Süresiz, 75 = TYT, 120 = AYT)
 */
@Immutable
data class TestFiltre(
    val examType: String? = null,
    val selectedSubjects: Set<String> = emptySet(),
    val selectedTopics: Set<String> = emptySet(),
    val selectedMistakeTypes: Set<HataTipi> = emptySet(),
    val onlyUnsolved: Boolean = false,
    val questionCount: Int = 10,
    val timeLimitPerQuestionSeconds: Long = 0L
)

/**
 * TYT / AYT Zaman Baskısı Modu (Speed Run)
 */
enum class ZamanBaskisiModu(val displayName: String, val secondsPerQuestion: Long) {
    NORMAL("Normal (Süresiz)", 0L),
    TYT("TYT (75 sn / soru)", 75L),
    AYT("AYT (120 sn / soru)", 120L);

    companion object {
        fun fromSeconds(seconds: Long): ZamanBaskisiModu = when (seconds) {
            75L -> TYT
            120L -> AYT
            else -> NORMAL
        }
    }
}

/**
 * Test oturumunda yer alan tekil soru ve kullanıcının cevap bilgisi
 *
 * @property soru İlgili soru domain modeli
 * @property userAnswer Kullanıcının verdiği cevap şıkkı ("A", "B", "C", "D", "E") veya null (boş)
 * @property isCorrect Sorunun doğru cevaplanıp cevaplanmadığı (true: Doğru, false: Yanlış, null: Boş/Belirsiz)
 * @property timeSpentSeconds Bu sorunun çözümünde harcanan süre (saniye cinsinden)
 */
@Immutable
data class TestQuestion(
    val soru: Soru,
    val userAnswer: String? = null,
    val isCorrect: Boolean? = null,
    val timeSpentSeconds: Long = 0L
)

/**
 * Test tamamlandığında elde edilen özet ve detaylı sonuç verisi
 *
 * @property totalQuestions Testteki toplam soru sayısı
 * @property correctCount Doğru cevap sayısı
 * @property wrongCount Yanlış cevap sayısı
 * @property emptyCount Boş bırakılan soru sayısı
 * @property totalTimeSeconds Test boyunca harcanan toplam süre (saniye cinsinden)
 * @property questions Testteki tüm sorular ve cevap durumu
 * @property completedAt Testin tamamlanma zaman damgası (milisaniye)
 * @property targetTimePerQuestionSeconds Soru başına hedef süre (0 = Sınırsız, 75 = TYT, 120 = AYT)
 */
@Immutable
data class TestResult(
    val totalQuestions: Int,
    val correctCount: Int,
    val wrongCount: Int,
    val emptyCount: Int,
    val totalTimeSeconds: Long,
    val questions: List<TestQuestion>,
    val completedAt: Long = System.currentTimeMillis(),
    val targetTimePerQuestionSeconds: Long = 0L
) {
    /**
     * Yüzde cinsinden başarı oranı (örn: 80)
     */
    val successRate: Int
        get() = if (totalQuestions > 0) ((correctCount.toFloat() / totalQuestions.toFloat()) * 100).toInt() else 0

    /**
     * YKS net hesabı: Doğru - (Yanlış / 4)
     */
    val netCount: Float
        get() = (correctCount - (wrongCount * 0.25f)).coerceAtLeast(0f)
}
