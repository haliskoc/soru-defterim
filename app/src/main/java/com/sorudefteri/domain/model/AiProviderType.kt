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
        defaultModel = "gemini-3.7-flash",
        supportedModels = listOf(
            "gemini-3.7-flash" to "Gemini 3.7 Flash (Önerilen)",
            "gemini-2.0-flash" to "Gemini 2.0 Flash (Hızlı & Kararlı)",
            "gemini-1.5-pro" to "Gemini 1.5 Pro (İleri Düzey Akıl Yürütme)",
            "gemini-1.5-flash" to "Gemini 1.5 Flash (Standart Hızlı)",
            "gemini-2.0-flash-lite" to "Gemini 2.0 Flash Lite"
        ),
        consoleUrl = "https://aistudio.google.com/app/apikey",
        consoleName = "Google AI Studio"
    ),
    GROQ(
        displayName = "Groq",
        defaultModel = "llama-3.2-11b-vision-preview",
        supportedModels = listOf(
            "llama-3.2-11b-vision-preview" to "Llama 3.2 11B Vision (Hızlı & Görsel)",
            "llama-3.2-90b-vision-preview" to "Llama 3.2 90B Vision (Gelişmiş & Görsel)",
            "llama-3.3-70b-versatile" to "Llama 3.3 70B Versatile (Metin)",
            "llama-3.1-8b-instant" to "Llama 3.1 8B Instant (Hafif & Hızlı)"
        ),
        consoleUrl = "https://console.groq.com/keys",
        consoleName = "Groq Console"
    ),
    NVIDIA(
        displayName = "NVIDIA Build",
        defaultModel = "meta/llama-3.2-90b-vision-instruct",
        supportedModels = listOf(
            "meta/llama-3.2-90b-vision-instruct" to "Llama 3.2 90B Vision (Önerilen - En Zeki & Detaylı)",
            "meta/llama-3.2-11b-vision-instruct" to "Llama 3.2 11B Vision (Hızlı Görsel Çözüm)",
            "minimaxai/minimax-m3" to "MiniMax M3 (NVIDIA NIM)",
            "google/gemma-2-27b-it" to "Gemma 2 27B Instruct (Google NIM)",
            "microsoft/phi-3-vision-128k-instruct" to "Phi-3 Vision 128K (Microsoft)",
            "meta/llama-3.1-70b-instruct" to "Llama 3.1 70B Instruct (Metin)",
            "nvidia/llama-3.1-nemotron-70b-instruct" to "Nemotron 70B Instruct (NVIDIA Metin)"
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
