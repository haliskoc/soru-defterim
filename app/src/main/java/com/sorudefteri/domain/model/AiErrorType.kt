package com.sorudefteri.domain.model

/**
 * AI servislerinde karşılaşılabilecek hata türleri ve kullanıcı dostu Türkçe açıklamaları.
 */
enum class AiErrorType(val defaultMessage: String) {
    INVALID_API_KEY("Geçersiz veya eksik API Anahtarı. Lütfen Ayarlar sayfasından API anahtarınızı kontrol edin."),
    QUOTA_EXCEEDED("API kullanım kotanız doldu veya istek sınırı aşıldı (Rate limit). Lütfen bir süre bekleyip tekrar deneyin."),
    NETWORK_TIMEOUT("İstek zaman aşımına uğradı. Lütfen internet bağlantınızı kontrol edip tekrar deneyin."),
    NETWORK_ERROR("Ağ bağlantı hatası oluştu. Lütfen internet bağlantınızı kontrol edin."),
    MODEL_UNAVAILABLE("Seçilen yapay zeka modeli şu anda kullanılamıyor veya sunucu geçici olarak yanıt vermiyor."),
    PARSING_FAILED("Yapay zeka yanıtı beklenen formatta çözümlenemedi."),
    UNKNOWN("Beklenmeyen bir yapay zeka hatası oluştu.")
}

/**
 * AI katmanı için yapılandırılmış, kullanıcı dostu hata sınıfı.
 */
class AiException(
    val errorType: AiErrorType,
    val userFriendlyMessage: String = errorType.defaultMessage,
    cause: Throwable? = null
) : Exception(userFriendlyMessage, cause)
