package com.sorudefteri.domain.model

/**
 * Desteklenen Yapay Zeka (AI) sağlayıcıları.
 */
enum class AiProviderType(
    val displayName: String,
    val defaultModel: String,
    val supportedModels: List<Pair<String, String>>, // Pair<ModelID, ModelLabel>
    val consoleUrl: String,
    val consoleName: String
) {
    GEMINI(
        displayName = "Google Gemini",
        defaultModel = "gemini-2.5-flash",
        supportedModels = listOf(
            "gemini-2.5-flash" to "Gemini 2.5 Flash (En Yeni & Ultra Hızlı)",
            "gemini-2.0-flash" to "Gemini 2.0 Flash (Kararlı & Hızlı)",
            "gemini-2.5-pro" to "Gemini 2.5 Pro (İleri Düzey Akıl Yürütme)",
            "gemini-1.5-flash" to "Gemini 1.5 Flash (Klasik Hızlı)",
            "gemini-1.5-pro" to "Gemini 1.5 Pro (Klasik Pro)"
        ),
        consoleUrl = "https://aistudio.google.com/app/apikey",
        consoleName = "Google AI Studio"
    ),
    GROQ(
        displayName = "Groq",
        defaultModel = "llama-3.2-11b-vision-preview",
        supportedModels = listOf(
            "llama-3.2-11b-vision-preview" to "Llama 3.2 11B Vision (Önerilen - Hızlı)",
            "llama-3.2-90b-vision-preview" to "Llama 3.2 90B Vision (Gelişmiş Görsel)"
        ),
        consoleUrl = "https://console.groq.com/keys",
        consoleName = "Groq Console"
    );

    companion object {
        fun fromString(value: String?): AiProviderType {
            if (value.isNullOrBlank()) return GEMINI
            return entries.firstOrNull {
                it.name.equals(value, ignoreCase = true) ||
                it.displayName.equals(value, ignoreCase = true)
            } ?: GEMINI
        }
    }
}
