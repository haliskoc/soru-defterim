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
        defaultModel = "gemini-2.0-flash",
        supportedModels = listOf(
            "gemini-2.0-flash" to "Gemini 2.0 Flash (Önerilen - Hızlı & Güncel)",
            "gemini-1.5-flash" to "Gemini 1.5 Flash (Hızlı & Dengeli)",
            "gemini-1.5-pro" to "Gemini 1.5 Pro (Gelişmiş Akıl Yürütme)"
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
