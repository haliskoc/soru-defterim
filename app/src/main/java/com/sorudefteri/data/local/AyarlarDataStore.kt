package com.sorudefteri.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sorudefteri.data.ai.AiPromptType
import com.sorudefteri.domain.model.AiProviderType
import com.sorudefteri.domain.model.Alan
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "soru_defteri_ayarlar")

/**
 * Kullanıcı tercihlerini ve API ayarlarını yöneten DataStore bileşeni.
 * P0-1: API anahtarları EncryptedSharedPreferences ile AES256-GCM şifreli tutulur.
 * Diğer tercihler (alan, theme, model, özel sistem promptları) normal DataStore'da saklanır.
 */
@Singleton
class AyarlarDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val encryptedPrefs: SharedPreferences
) {

    companion object {
        private val KEY_ALAN = stringPreferencesKey("alan")
        private val KEY_AI_PROVIDER = stringPreferencesKey("ai_provider")
        // Legacy plain keys (migration için saklı, artık yazılmıyor)
        private val KEY_GEMINI_API_KEY_LEGACY = stringPreferencesKey("gemini_api_key")
        private val KEY_GROQ_API_KEY_LEGACY = stringPreferencesKey("groq_api_key")
        private val KEY_NVIDIA_API_KEY_LEGACY = stringPreferencesKey("nvidia_api_key")
        private val KEY_GEMINI_MODEL = stringPreferencesKey("gemini_model")
        private val KEY_GROQ_MODEL = stringPreferencesKey("groq_model")
        private val KEY_NVIDIA_MODEL = stringPreferencesKey("nvidia_model")
        private val KEY_DARK_THEME = intPreferencesKey("dark_theme")

        // EncryptedSharedPreferences keys
        private const val ENC_GEMINI_API_KEY = "gemini_api_key"
        private const val ENC_GROQ_API_KEY = "groq_api_key"
        private const val ENC_NVIDIA_API_KEY = "nvidia_api_key"

        // Auto backup preferences
        private val KEY_AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        private val KEY_WIFI_ONLY_BACKUP = booleanPreferencesKey("wifi_only_backup")

        // Özel System Prompt preferences
        private val KEY_PROMPT_SORU_URETIMI = stringPreferencesKey("prompt_soru_uretimi")
        private val KEY_PROMPT_COZUM_URETIMI = stringPreferencesKey("prompt_cozum_uretimi")
        private val KEY_PROMPT_OCR_TEMIZLEME = stringPreferencesKey("prompt_ocr_temizleme")
        private val KEY_PROMPT_BENZER_SORU = stringPreferencesKey("prompt_benzer_soru")

        // Onboarding
        private val KEY_ONBOARDING_SEEN = booleanPreferencesKey("onboarding_seen")

        const val DEFAULT_ALAN = "SAYISAL"
        const val DEFAULT_AI_PROVIDER = "GEMINI"
        const val DEFAULT_GEMINI_MODEL = "gemini-2.5-flash"
        const val DEFAULT_GROQ_MODEL = "meta-llama/llama-4-scout-17b-16e-instruct"
        const val DEFAULT_NVIDIA_MODEL = "meta/llama-3.1-405b-instruct"
        const val DEFAULT_DARK_THEME = 0 // 0: Sistem, 1: Açık, 2: Koyu
        const val DEFAULT_AUTO_BACKUP_ENABLED = false
        const val DEFAULT_WIFI_ONLY_BACKUP = true
        const val DEFAULT_ONBOARDING_SEEN = false
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Encrypted flows - MutableStateFlow ile anlık + güncellenebilir
    private val _geminiApiKeyFlow = MutableStateFlow(
        encryptedPrefs.getString(ENC_GEMINI_API_KEY, "") ?: ""
    )
    private val _groqApiKeyFlow = MutableStateFlow(
        encryptedPrefs.getString(ENC_GROQ_API_KEY, "") ?: ""
    )
    private val _nvidiaApiKeyFlow = MutableStateFlow(
        encryptedPrefs.getString(ENC_NVIDIA_API_KEY, "") ?: ""
    )

    private val encryptedListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            ENC_GEMINI_API_KEY -> {
                _geminiApiKeyFlow.value = encryptedPrefs.getString(ENC_GEMINI_API_KEY, "") ?: ""
            }
            ENC_GROQ_API_KEY -> {
                _groqApiKeyFlow.value = encryptedPrefs.getString(ENC_GROQ_API_KEY, "") ?: ""
            }
            ENC_NVIDIA_API_KEY -> {
                _nvidiaApiKeyFlow.value = encryptedPrefs.getString(ENC_NVIDIA_API_KEY, "") ?: ""
            }
        }
    }

    init {
        encryptedPrefs.registerOnSharedPreferenceChangeListener(encryptedListener)
        // Migration: plain DataStore'daki eski API key'leri şifreli store'a taşı (one-shot)
        scope.launch {
            try {
                migrateLegacyKeysIfNeeded()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Legacy plain DataStore'dan encrypted prefs'e tek seferlik taşıma.
     * Eğer encrypted boş ama plain dolu ise kopyala, sonra plain'den sil.
     */
    private suspend fun migrateLegacyKeysIfNeeded() {
        val prefs = context.dataStore.data.first()
        val legacyGemini = prefs[KEY_GEMINI_API_KEY_LEGACY]?.trim().orEmpty()
        val legacyGroq = prefs[KEY_GROQ_API_KEY_LEGACY]?.trim().orEmpty()
        val legacyNvidia = prefs[KEY_NVIDIA_API_KEY_LEGACY]?.trim().orEmpty()
        val encGemini = encryptedPrefs.getString(ENC_GEMINI_API_KEY, "") ?: ""
        val encGroq = encryptedPrefs.getString(ENC_GROQ_API_KEY, "") ?: ""
        val encNvidia = encryptedPrefs.getString(ENC_NVIDIA_API_KEY, "") ?: ""

        var migrated = false
        val editor = encryptedPrefs.edit()
        if (encGemini.isBlank() && legacyGemini.isNotBlank()) {
            editor.putString(ENC_GEMINI_API_KEY, legacyGemini)
            _geminiApiKeyFlow.value = legacyGemini
            migrated = true
        }
        if (encGroq.isBlank() && legacyGroq.isNotBlank()) {
            editor.putString(ENC_GROQ_API_KEY, legacyGroq)
            _groqApiKeyFlow.value = legacyGroq
            migrated = true
        }
        if (encNvidia.isBlank() && legacyNvidia.isNotBlank()) {
            editor.putString(ENC_NVIDIA_API_KEY, legacyNvidia)
            _nvidiaApiKeyFlow.value = legacyNvidia
            migrated = true
        }
        if (migrated) {
            editor.apply()
            // Legacy plain key'leri temizle
            context.dataStore.edit { ds ->
                if (legacyGemini.isNotBlank()) ds.remove(KEY_GEMINI_API_KEY_LEGACY)
                if (legacyGroq.isNotBlank()) ds.remove(KEY_GROQ_API_KEY_LEGACY)
                if (legacyNvidia.isNotBlank()) ds.remove(KEY_NVIDIA_API_KEY_LEGACY)
            }
        }
        // Groq model migration: eski vision-preview -> yeni scout
        val currentGroqModel = prefs[KEY_GROQ_MODEL]?.trim().orEmpty()
        if (currentGroqModel == "llama-3.2-11b-vision-preview" || currentGroqModel == "llama-3.2-90b-vision-preview") {
            context.dataStore.edit { ds ->
                ds[KEY_GROQ_MODEL] = DEFAULT_GROQ_MODEL
            }
        }
    }

    // ==========================================
    // Flows
    // ==========================================

    val alanFlow: Flow<Alan> = context.dataStore.data.map { preferences ->
        val raw = preferences[KEY_ALAN] ?: DEFAULT_ALAN
        Alan.fromString(raw)
    }

    val currentAlan: Flow<Alan> get() = alanFlow

    val alanRawFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_ALAN] ?: DEFAULT_ALAN
    }

    val aiProviderFlow: Flow<AiProviderType> = context.dataStore.data.map { preferences ->
        val raw = preferences[KEY_AI_PROVIDER] ?: DEFAULT_AI_PROVIDER
        AiProviderType.fromString(raw)
    }

    val aiProvider: Flow<AiProviderType> get() = aiProviderFlow

    val aiProviderRawFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_AI_PROVIDER] ?: DEFAULT_AI_PROVIDER
    }

    // P0-1: Şifreli kaynak
    val geminiApiKeyFlow: Flow<String> = _geminiApiKeyFlow.asStateFlow()

    val geminiApiKey: Flow<String> get() = geminiApiKeyFlow

    val groqApiKeyFlow: Flow<String> = _groqApiKeyFlow.asStateFlow()

    val groqApiKey: Flow<String> get() = groqApiKeyFlow

    val nvidiaApiKeyFlow: Flow<String> = _nvidiaApiKeyFlow.asStateFlow()

    val nvidiaApiKey: Flow<String> get() = nvidiaApiKeyFlow

    val geminiModelFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_GEMINI_MODEL] ?: DEFAULT_GEMINI_MODEL
    }

    val geminiModel: Flow<String> get() = geminiModelFlow

    val groqModelFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_GROQ_MODEL] ?: DEFAULT_GROQ_MODEL
    }

    val groqModel: Flow<String> get() = groqModelFlow

    val nvidiaModelFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_NVIDIA_MODEL] ?: DEFAULT_NVIDIA_MODEL
    }

    val nvidiaModel: Flow<String> get() = nvidiaModelFlow

    val darkThemeFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_DARK_THEME] ?: DEFAULT_DARK_THEME
    }

    val themeMode: Flow<Int> get() = darkThemeFlow

    val autoBackupEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_AUTO_BACKUP_ENABLED] ?: DEFAULT_AUTO_BACKUP_ENABLED
    }

    val isAutoBackupEnabled: Flow<Boolean> get() = autoBackupEnabledFlow

    val wifiOnlyBackupFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_WIFI_ONLY_BACKUP] ?: DEFAULT_WIFI_ONLY_BACKUP
    }

    val isWifiOnlyBackup: Flow<Boolean> get() = wifiOnlyBackupFlow

    val isOnboardingSeenFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_ONBOARDING_SEEN] ?: DEFAULT_ONBOARDING_SEEN
    }

    val isOnboardingSeen: Flow<Boolean> get() = isOnboardingSeenFlow

    // Custom Prompts Flows
    val promptSoruUretimiFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_PROMPT_SORU_URETIMI] ?: ""
    }

    val promptCozumUretimiFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_PROMPT_COZUM_URETIMI] ?: ""
    }

    val promptOcrTemizlemeFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_PROMPT_OCR_TEMIZLEME] ?: ""
    }

    val promptBenzerSoruFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_PROMPT_BENZER_SORU] ?: ""
    }

    // ==========================================
    // Custom Prompt Helpers
    // ==========================================

    fun getCustomPrompt(type: AiPromptType): Flow<String> {
        return when (type) {
            AiPromptType.SORU_URETIMI -> promptSoruUretimiFlow
            AiPromptType.COZUM_URETIMI -> promptCozumUretimiFlow
            AiPromptType.OCR_TEMIZLEME -> promptOcrTemizlemeFlow
            AiPromptType.BENZER_SORU -> promptBenzerSoruFlow
        }
    }

    suspend fun getCustomPromptValue(type: AiPromptType): String {
        return getCustomPrompt(type).first()
    }

    suspend fun setCustomPrompt(type: AiPromptType, prompt: String) {
        val trimmed = prompt.trim()
        val key = when (type) {
            AiPromptType.SORU_URETIMI -> KEY_PROMPT_SORU_URETIMI
            AiPromptType.COZUM_URETIMI -> KEY_PROMPT_COZUM_URETIMI
            AiPromptType.OCR_TEMIZLEME -> KEY_PROMPT_OCR_TEMIZLEME
            AiPromptType.BENZER_SORU -> KEY_PROMPT_BENZER_SORU
        }
        context.dataStore.edit { preferences ->
            if (trimmed.isBlank()) {
                preferences.remove(key)
            } else {
                preferences[key] = trimmed
            }
        }
    }

    suspend fun resetPromptToDefault(type: AiPromptType) {
        val key = when (type) {
            AiPromptType.SORU_URETIMI -> KEY_PROMPT_SORU_URETIMI
            AiPromptType.COZUM_URETIMI -> KEY_PROMPT_COZUM_URETIMI
            AiPromptType.OCR_TEMIZLEME -> KEY_PROMPT_OCR_TEMIZLEME
            AiPromptType.BENZER_SORU -> KEY_PROMPT_BENZER_SORU
        }
        context.dataStore.edit { preferences ->
            preferences.remove(key)
        }
    }

    suspend fun resetAllPromptsToDefault() {
        context.dataStore.edit { preferences ->
            preferences.remove(KEY_PROMPT_SORU_URETIMI)
            preferences.remove(KEY_PROMPT_COZUM_URETIMI)
            preferences.remove(KEY_PROMPT_OCR_TEMIZLEME)
            preferences.remove(KEY_PROMPT_BENZER_SORU)
        }
    }

    // ==========================================
    // Setters
    // ==========================================

    suspend fun setAlan(alan: Alan) {
        context.dataStore.edit { preferences ->
            preferences[KEY_ALAN] = alan.name
        }
    }

    suspend fun setAlan(alanName: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_ALAN] = alanName
        }
    }

    suspend fun setAiProvider(provider: AiProviderType) {
        context.dataStore.edit { preferences ->
            preferences[KEY_AI_PROVIDER] = provider.name
        }
    }

    suspend fun setAiProvider(providerName: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_AI_PROVIDER] = providerName
        }
    }

    suspend fun setGeminiApiKey(apiKey: String) {
        val trimmed = apiKey.trim()
        encryptedPrefs.edit().putString(ENC_GEMINI_API_KEY, trimmed).apply()
        _geminiApiKeyFlow.value = trimmed
    }

    suspend fun setGroqApiKey(apiKey: String) {
        val trimmed = apiKey.trim()
        encryptedPrefs.edit().putString(ENC_GROQ_API_KEY, trimmed).apply()
        _groqApiKeyFlow.value = trimmed
    }

    suspend fun setNvidiaApiKey(apiKey: String) {
        val trimmed = apiKey.trim()
        encryptedPrefs.edit().putString(ENC_NVIDIA_API_KEY, trimmed).apply()
        _nvidiaApiKeyFlow.value = trimmed
    }

    suspend fun setGeminiModel(model: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_GEMINI_MODEL] = model.trim()
        }
    }

    suspend fun setGroqModel(model: String) {
        // Eski model otomatik migrate
        val safe = model.trim().let {
            if (it == "llama-3.2-11b-vision-preview" || it == "llama-3.2-90b-vision-preview") DEFAULT_GROQ_MODEL else it
        }
        context.dataStore.edit { preferences ->
            preferences[KEY_GROQ_MODEL] = safe
        }
    }

    suspend fun setNvidiaModel(model: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_NVIDIA_MODEL] = model.trim()
        }
    }

    suspend fun setDarkTheme(mode: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_DARK_THEME] = mode
        }
    }

    suspend fun setThemeMode(mode: Int) = setDarkTheme(mode)

    suspend fun setAutoBackupEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_AUTO_BACKUP_ENABLED] = enabled
        }
    }

    suspend fun setWifiOnlyBackup(wifiOnly: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_WIFI_ONLY_BACKUP] = wifiOnly
        }
    }

    suspend fun setOnboardingSeen(seen: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_ONBOARDING_SEEN] = seen
        }
    }

    suspend fun clearSettings() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
        // Encrypted prefs'i de temizle
        encryptedPrefs.edit().clear().apply()
        _geminiApiKeyFlow.value = ""
        _groqApiKeyFlow.value = ""
        _nvidiaApiKeyFlow.value = ""
    }

    // ==========================================
    // Suspend One-shot Getters
    // ==========================================

    suspend fun getAlan(): Alan = alanFlow.first()
    suspend fun getAiProvider(): AiProviderType = aiProviderFlow.first()
    suspend fun getGeminiApiKey(): String = geminiApiKeyFlow.first()
    suspend fun getGroqApiKey(): String = groqApiKeyFlow.first()
    suspend fun getNvidiaApiKey(): String = nvidiaApiKeyFlow.first()
    suspend fun getGeminiModel(): String = geminiModelFlow.first()
    suspend fun getGroqModel(): String = groqModelFlow.first()
    suspend fun getNvidiaModel(): String = nvidiaModelFlow.first()
    suspend fun getDarkTheme(): Int = darkThemeFlow.first()
    suspend fun getAutoBackupEnabled(): Boolean = autoBackupEnabledFlow.first()
    suspend fun getWifiOnlyBackup(): Boolean = wifiOnlyBackupFlow.first()
    suspend fun getIsOnboardingSeen(): Boolean = isOnboardingSeenFlow.first()

    suspend fun getActiveApiKey(): String {
        return when (getAiProvider()) {
            AiProviderType.GEMINI -> getGeminiApiKey()
            AiProviderType.GROQ -> getGroqApiKey()
            AiProviderType.NVIDIA -> getNvidiaApiKey()
        }
    }

    suspend fun getActiveModel(): String {
        return when (getAiProvider()) {
            AiProviderType.GEMINI -> getGeminiModel()
            AiProviderType.GROQ -> getGroqModel()
            AiProviderType.NVIDIA -> getNvidiaModel()
        }
    }
}
