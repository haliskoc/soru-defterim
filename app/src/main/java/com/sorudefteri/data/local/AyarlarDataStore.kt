package com.sorudefteri.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sorudefteri.domain.model.AiProviderType
import com.sorudefteri.domain.model.Alan
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "soru_defteri_ayarlar")

/**
 * Kullanıcı tercihlerini ve API ayarlarını yöneten DataStore bileşeni
 */
@Singleton
class AyarlarDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private val KEY_ALAN = stringPreferencesKey("alan")
        private val KEY_AI_PROVIDER = stringPreferencesKey("ai_provider")
        private val KEY_GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        private val KEY_GROQ_API_KEY = stringPreferencesKey("groq_api_key")
        private val KEY_GEMINI_MODEL = stringPreferencesKey("gemini_model")
        private val KEY_GROQ_MODEL = stringPreferencesKey("groq_model")
        private val KEY_DARK_THEME = intPreferencesKey("dark_theme")

        const val DEFAULT_ALAN = "SAYISAL"
        const val DEFAULT_AI_PROVIDER = "GEMINI"
        const val DEFAULT_GEMINI_MODEL = "gemini-2.5-flash"
        const val DEFAULT_GROQ_MODEL = "meta-llama/llama-4-scout-17b-16e-instruct"
        const val DEFAULT_DARK_THEME = 0 // 0: Sistem, 1: Açık, 2: Koyu
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

    val geminiApiKeyFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_GEMINI_API_KEY] ?: ""
    }

    val geminiApiKey: Flow<String> get() = geminiApiKeyFlow

    val groqApiKeyFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_GROQ_API_KEY] ?: ""
    }

    val groqApiKey: Flow<String> get() = groqApiKeyFlow

    val geminiModelFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_GEMINI_MODEL] ?: DEFAULT_GEMINI_MODEL
    }

    val geminiModel: Flow<String> get() = geminiModelFlow

    val groqModelFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_GROQ_MODEL] ?: DEFAULT_GROQ_MODEL
    }

    val groqModel: Flow<String> get() = groqModelFlow

    val darkThemeFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_DARK_THEME] ?: DEFAULT_DARK_THEME
    }

    val themeMode: Flow<Int> get() = darkThemeFlow

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
        context.dataStore.edit { preferences ->
            preferences[KEY_GEMINI_API_KEY] = apiKey.trim()
        }
    }

    suspend fun setGroqApiKey(apiKey: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_GROQ_API_KEY] = apiKey.trim()
        }
    }

    suspend fun setGeminiModel(model: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_GEMINI_MODEL] = model.trim()
        }
    }

    suspend fun setGroqModel(model: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_GROQ_MODEL] = model.trim()
        }
    }

    suspend fun setDarkTheme(mode: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_DARK_THEME] = mode
        }
    }

    suspend fun setThemeMode(mode: Int) = setDarkTheme(mode)

    suspend fun clearSettings() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    // ==========================================
    // Suspend One-shot Getters
    // ==========================================

    suspend fun getAlan(): Alan = alanFlow.first()
    suspend fun getAiProvider(): AiProviderType = aiProviderFlow.first()
    suspend fun getGeminiApiKey(): String = geminiApiKeyFlow.first()
    suspend fun getGroqApiKey(): String = groqApiKeyFlow.first()
    suspend fun getGeminiModel(): String = geminiModelFlow.first()
    suspend fun getGroqModel(): String = groqModelFlow.first()
    suspend fun getDarkTheme(): Int = darkThemeFlow.first()

    suspend fun getActiveApiKey(): String {
        return when (getAiProvider()) {
            AiProviderType.GEMINI -> getGeminiApiKey()
            AiProviderType.GROQ -> getGroqApiKey()
        }
    }

    suspend fun getActiveModel(): String {
        return when (getAiProvider()) {
            AiProviderType.GEMINI -> getGeminiModel()
            AiProviderType.GROQ -> getGroqModel()
        }
    }
}
