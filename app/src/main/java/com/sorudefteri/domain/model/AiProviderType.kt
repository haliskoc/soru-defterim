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
            "gemini-2.5-flash" to "Gemini 2.5 Flash (Önerilen - Hızlı & Güçlü)",
            "gemini-1.5-flash" to "Gemini 1.5 Flash (Standart)"
        ),
        consoleUrl = "https://aistudio.google.com/app/apikey",
        consoleName = "Google AI Studio"
    ),
    GROQ(
        displayName = "Groq",
        defaultModel = "meta-llama/llama-4-scout-17b-16e-instruct",
        supportedModels = listOf(
            "meta-llama/llama-4-scout-17b-16e-instruct" to "Llama 4 Scout 17B (Görsel Destekli)",
            "llama-3.2-11b-vision-preview" to "Llama 3.2 11B Vision (Önizleme)"
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
