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
        defaultModel = "meta-llama/llama-4-scout-17b-16e-instruct",
        supportedModels = listOf(
            "meta-llama/llama-4-scout-17b-16e-instruct" to "Llama 4 Scout 17B (Önerilen - Hızlı & Vision)",
            "meta-llama/llama-4-maverick-17b-128e-instruct" to "Llama 4 Maverick 17B (Gelişmiş Akıl Yürütme)",
            "llama-3.3-70b-versatile" to "Llama 3.3 70B Versatile (Metin)"
        ),
        consoleUrl = "https://console.groq.com/keys",
        consoleName = "Groq Console"
    ),
    NVIDIA(
        displayName = "NVIDIA Build",
        defaultModel = "meta/llama-3.1-405b-instruct",
        supportedModels = listOf(
            "meta/llama-3.1-405b-instruct" to "Llama 3.1 405B Instruct (Önerilen - En Güçlü)",
            "meta/llama-3.1-70b-instruct" to "Llama 3.1 70B Instruct (Hızlı)",
            "mistralai/mixtral-8x22b-instruct-v0.1" to "Mixtral 8x22B Instruct",
            "nvidia/llama-3.1-nemotron-70b-instruct" to "Nemotron 70B Instruct (NVIDIA)",
            "google/gemma-2-27b-it" to "Gemma 2 27B IT"
        ),
        consoleUrl = "https://build.nvidia.com/explore/reasoning",
        consoleName = "NVIDIA Build"
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
