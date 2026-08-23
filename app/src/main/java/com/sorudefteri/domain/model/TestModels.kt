package com.sorudefteri.domain.model

/**
 * Test oluşturma filtreleme kriterleri
 *
 * @property examType Sınav türü ("TYT", "AYT" veya null: Tümü)
 * @property selectedSubjects Seçilen ders kimlikleri (örn. "matematik", "fizik")
 * @property selectedTopics Seçilen konu başlıkları
 * @property selectedMistakeTypes Seçilen hata tipleri
 * @property onlyUnsolved Sadece çözülmemiş soruları dahil et
 * @property questionCount Testteki hedeflenen soru sayısı (örn: 5, 10, 15, 20, 0: Tümü)
 */
data class TestFiltre(
    val examType: String? = null,
    val selectedSubjects: Set<String> = emptySet(),
    val selectedTopics: Set<String> = emptySet(),
    val selectedMistakeTypes: Set<HataTipi> = emptySet(),
    val onlyUnsolved: Boolean = false,
    val questionCount: Int = 10
)

/**
 * Test oturumunda yer alan tekil soru ve kullanıcının cevap bilgisi
 *
 * @property soru İlgili soru domain modeli
 * @property userAnswer Kullanıcının verdiği cevap şıkkı ("A", "B", "C", "D", "E") veya null (boş)
 * @property isCorrect Sorunun doğru cevaplanıp cevaplanmadığı (true: Doğru, false: Yanlış, null: Boş/Belirsiz)
 * @property timeSpentSeconds Bu sorunun çözümünde harcanan süre (saniye cinsinden)
 */
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
 */
data class TestResult(
    val totalQuestions: Int,
    val correctCount: Int,
    val wrongCount: Int,
    val emptyCount: Int,
    val totalTimeSeconds: Long,
    val questions: List<TestQuestion>,
    val completedAt: Long = System.currentTimeMillis()
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
