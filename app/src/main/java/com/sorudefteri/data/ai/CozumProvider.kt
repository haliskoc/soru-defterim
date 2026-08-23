package com.sorudefteri.data.ai

/**
 * AI çözüm sağlayıcıları için ortak arayüz.
 * Gemini, Groq ve diğer LLM/VLM sağlayıcıları bu arayüzü uygular.
 */
interface CozumProvider {
    /**
     * Soru görseli ve ders/konu bilgileri ile yapay zekadan adım adım çözüm ister.
     *
     * @param imageBytes Soru görselinin byte dizisi.
     * @param dersAdi Sorunun ait olduğu ders adı (örn. "Matematik", "Fizik").
     * @param konu Sorunun ait olduğu konu (örn. "Türev", "Optik").
     * @param correctAnswer Varsa sorunun doğru cevap şıkkı (örn. "A", "B", "C", "D", "E") veya null.
     * @param modelName Kullanılacak model adı (örn. "gemini-2.5-flash", "meta-llama/llama-4-scout-17b-16e-instruct").
     * @param apiKey İlgili sağlayıcıya ait API anahtarı.
     * @return Başarılı ise çözüm metni [Result.success], hata durumunda açıklayıcı hata mesajı [Result.failure].
     */
    suspend fun cozumIste(
        imageBytes: ByteArray,
        dersAdi: String,
        konu: String,
        correctAnswer: String?,
        modelName: String,
        apiKey: String
    ): Result<String>
}
