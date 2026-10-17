package com.sorudefteri.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sorudefteri.data.ai.AiPromptType
import com.sorudefteri.domain.model.AiProviderType
import com.sorudefteri.domain.model.Alan
import com.sorudefteri.ui.ortak.drawing.StylusDoubleTapAction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private val Context.defaultDataStore: DataStore<Preferences> by preferencesDataStore(name = "soru_defteri_ayarlar")
private val Context.dataStore: DataStore<Preferences>
    get() = AyarlarDataStore.testDataStore ?: this.defaultDataStore

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
        internal var testDataStore: DataStore<Preferences>? = null
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

        // Lenovo Stylus Preferences
        private val KEY_STYLUS_DOUBLE_TAP_ACTION = stringPreferencesKey("stylus_double_tap_action")
        private val KEY_STYLUS_PALM_REJECTION = booleanPreferencesKey("stylus_palm_rejection")
        private val KEY_STYLUS_HAPTIC_FEEDBACK = booleanPreferencesKey("stylus_haptic_feedback")

        const val DEFAULT_ALAN = "SAYISAL"
        const val DEFAULT_AI_PROVIDER = "GEMINI"
        const val DEFAULT_GEMINI_MODEL = "gemini-3.7-flash"
        const val DEFAULT_GROQ_MODEL = "llama-3.2-11b-vision-preview"
        const val DEFAULT_NVIDIA_MODEL = "meta/llama-3.2-90b-vision-instruct"
        const val DEFAULT_DARK_THEME = 0 // 0: Sistem, 1: Açık, 2: Koyu
        const val DEFAULT_AUTO_BACKUP_ENABLED = false
        const val DEFAULT_WIFI_ONLY_BACKUP = true
        const val DEFAULT_ONBOARDING_SEEN = false
        val DEFAULT_STYLUS_DOUBLE_TAP_ACTION = StylusDoubleTapAction.PEN_ERASER_TOGGLE.name
        const val DEFAULT_STYLUS_PALM_REJECTION = true
        const val DEFAULT_STYLUS_HAPTIC_FEEDBACK = true

        // Faz 4: Gamification, Streak & Badges
        private val KEY_CURRENT_STREAK = intPreferencesKey("current_streak")
        private val KEY_LAST_STREAK_DATE = stringPreferencesKey("last_streak_date")
        private val KEY_STREAK_SHIELD_COUNT = intPreferencesKey("streak_shield_count")
        private val KEY_LAST_SHIELD_RESET_WEEK = intPreferencesKey("last_shield_reset_week")
        private val KEY_USER_XP = intPreferencesKey("user_xp")
        private val KEY_UNLOCKED_BADGES = stringSetPreferencesKey("unlocked_badges")
        private val KEY_TODAY_ACTIVITY_DATE = stringPreferencesKey("today_activity_date")
        private val KEY_TODAY_SOLVED_COUNT = intPreferencesKey("today_solved_count")
        private val KEY_TODAY_ADDED_COUNT = intPreferencesKey("today_added_count")
        private val KEY_DRIVE_LAST_SYNC_TIME = longPreferencesKey("drive_last_sync_time")
        private val KEY_EXAM_TARGET_DATE = longPreferencesKey("exam_target_date")

        // Kamera ayarları
        private val KEY_KAMERA_UZUN_POZLAMA = booleanPreferencesKey("kamera_uzun_pozlama")
        private val KEY_KAMERA_OTOMATIK_CEKIM = booleanPreferencesKey("kamera_otomatik_cekim")

        const val DEFAULT_CURRENT_STREAK = 0
        const val DEFAULT_LAST_STREAK_DATE = ""
        const val DEFAULT_STREAK_SHIELD_COUNT = 1
        const val DEFAULT_LAST_SHIELD_RESET_WEEK = -1
        const val DEFAULT_USER_XP = 0

        // Widget & Sınav Geri Sayımı (0 = tarih ayarlanmadı)
        const val DEFAULT_EXAM_TARGET_DATE = 0L

        // Kamera ayarları varsayılanları
        const val DEFAULT_KAMERA_UZUN_POZLAMA = false
        const val DEFAULT_KAMERA_OTOMATIK_CEKIM = false
    }

    // TODO: Scope leak - CoroutineScope(SupervisorJob + IO) Application scope'a bağlanmalı, unregisterOnSharedPreferenceChangeListener close'da çağrılmalı, aksi halde memory leak riski
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
        // TODO: unregister listener when DataStore is cleared - add onCleared or use lifecycle-aware scope
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
        // Migration of broken/legacy models to valid defaults
        val currentGeminiModel = prefs[KEY_GEMINI_MODEL]?.trim().orEmpty()
        if (currentGeminiModel.isBlank() || currentGeminiModel == "gemini-2.5-flash" || currentGeminiModel == "gemini-2.5-pro") {
            context.dataStore.edit { ds ->
                ds[KEY_GEMINI_MODEL] = DEFAULT_GEMINI_MODEL
            }
        }
        val currentGroqModel = prefs[KEY_GROQ_MODEL]?.trim().orEmpty()
        if (currentGroqModel.isBlank() || currentGroqModel.contains("llama-4-scout") || currentGroqModel.contains("llama-4-maverick")) {
            context.dataStore.edit { ds ->
                ds[KEY_GROQ_MODEL] = DEFAULT_GROQ_MODEL
            }
        }
        val currentNvidiaModel = prefs[KEY_NVIDIA_MODEL]?.trim().orEmpty()
        if (currentNvidiaModel.isBlank() || currentNvidiaModel == "meta/llama-3.1-405b-instruct") {
            context.dataStore.edit { ds ->
                ds[KEY_NVIDIA_MODEL] = DEFAULT_NVIDIA_MODEL
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

    // Lenovo Stylus Flows
    val stylusDoubleTapActionFlow: Flow<StylusDoubleTapAction> = context.dataStore.data.map { preferences ->
        val raw = preferences[KEY_STYLUS_DOUBLE_TAP_ACTION] ?: DEFAULT_STYLUS_DOUBLE_TAP_ACTION
        StylusDoubleTapAction.fromString(raw)
    }

    val stylusDoubleTapAction: Flow<StylusDoubleTapAction> get() = stylusDoubleTapActionFlow

    val stylusPalmRejectionFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_STYLUS_PALM_REJECTION] ?: DEFAULT_STYLUS_PALM_REJECTION
    }

    val isStylusPalmRejectionEnabled: Flow<Boolean> get() = stylusPalmRejectionFlow

    val stylusHapticFeedbackFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_STYLUS_HAPTIC_FEEDBACK] ?: DEFAULT_STYLUS_HAPTIC_FEEDBACK
    }

    val isStylusHapticFeedbackEnabled: Flow<Boolean> get() = stylusHapticFeedbackFlow

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
    // Faz 4: Gamification Flows
    // ==========================================

    val currentStreak: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_CURRENT_STREAK] ?: DEFAULT_CURRENT_STREAK
    }
    val currentStreakFlow: Flow<Int> get() = currentStreak

    val lastStreakDate: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_LAST_STREAK_DATE] ?: DEFAULT_LAST_STREAK_DATE
    }
    val lastStreakDateFlow: Flow<String> get() = lastStreakDate

    val streakShieldCount: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_STREAK_SHIELD_COUNT] ?: DEFAULT_STREAK_SHIELD_COUNT
    }
    val streakShieldCountFlow: Flow<Int> get() = streakShieldCount

    val lastShieldResetWeek: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_LAST_SHIELD_RESET_WEEK] ?: DEFAULT_LAST_SHIELD_RESET_WEEK
    }
    val lastShieldResetWeekFlow: Flow<Int> get() = lastShieldResetWeek

    val userXp: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_USER_XP] ?: DEFAULT_USER_XP
    }
    val userXpFlow: Flow<Int> get() = userXp

    val unlockedBadges: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[KEY_UNLOCKED_BADGES] ?: emptySet()
    }
    val unlockedBadgesFlow: Flow<Set<String>> get() = unlockedBadges

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
        withContext(Dispatchers.IO) {
            encryptedPrefs.edit().putString(ENC_GEMINI_API_KEY, trimmed).commit()
        }
        _geminiApiKeyFlow.value = trimmed
    }

    suspend fun setGroqApiKey(apiKey: String) {
        val trimmed = apiKey.trim()
        withContext(Dispatchers.IO) {
            encryptedPrefs.edit().putString(ENC_GROQ_API_KEY, trimmed).commit()
        }
        _groqApiKeyFlow.value = trimmed
    }

    suspend fun setNvidiaApiKey(apiKey: String) {
        val trimmed = apiKey.trim()
        withContext(Dispatchers.IO) {
            encryptedPrefs.edit().putString(ENC_NVIDIA_API_KEY, trimmed).commit()
        }
        _nvidiaApiKeyFlow.value = trimmed
    }

    suspend fun setGeminiModel(model: String) {
        val trimmed = model.trim().ifBlank { DEFAULT_GEMINI_MODEL }
        context.dataStore.edit { preferences ->
            preferences[KEY_GEMINI_MODEL] = trimmed
        }
    }

    suspend fun setGroqModel(model: String) {
        val trimmed = model.trim().ifBlank { DEFAULT_GROQ_MODEL }
        context.dataStore.edit { preferences ->
            preferences[KEY_GROQ_MODEL] = trimmed
        }
    }

    suspend fun setNvidiaModel(model: String) {
        val trimmed = model.trim().ifBlank { DEFAULT_NVIDIA_MODEL }
        context.dataStore.edit { preferences ->
            preferences[KEY_NVIDIA_MODEL] = trimmed
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

    suspend fun setStylusDoubleTapAction(action: StylusDoubleTapAction) {
        context.dataStore.edit { preferences ->
            preferences[KEY_STYLUS_DOUBLE_TAP_ACTION] = action.name
        }
    }

    suspend fun setStylusPalmRejection(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_STYLUS_PALM_REJECTION] = enabled
        }
    }

    suspend fun setStylusHapticFeedback(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_STYLUS_HAPTIC_FEEDBACK] = enabled
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
    suspend fun getStylusDoubleTapAction(): StylusDoubleTapAction = stylusDoubleTapActionFlow.first()
    suspend fun getStylusPalmRejection(): Boolean = stylusPalmRejectionFlow.first()
    suspend fun getStylusHapticFeedback(): Boolean = stylusHapticFeedbackFlow.first()

    // ==========================================
    // Faz 4: Gamification, Streak & XP Methods
    // ==========================================

    private fun calculateDayDifference(dateStr1: String, dateStr2: String): Int {
        if (dateStr1.isBlank() || dateStr2.isBlank()) return Int.MAX_VALUE
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val d1 = sdf.parse(dateStr1) ?: return Int.MAX_VALUE
            val d2 = sdf.parse(dateStr2) ?: return Int.MAX_VALUE
            val cal1 = Calendar.getInstance().apply {
                time = d1
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val cal2 = Calendar.getInstance().apply {
                time = d2
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val diffMillis = cal2.timeInMillis - cal1.timeInMillis
            (diffMillis / (1000L * 60 * 60 * 24)).toInt()
        } catch (_: Exception) {
            Int.MAX_VALUE
        }
    }

    /**
     * Günlük aktivite kaydı ve Seri (Streak) mekanizması.
     * Öğrenci o gün >= 5 soru çözer veya >= 3 soru eklerse:
     * - Eğer bugün zaten kaydedildiyse: seri korunur.
     * - Eğer dün kaydedildiyse: seri + 1 artırılır.
     * - Eğer 1 gün kaçırıldıysa ve kalkan varsa: 1 kalkan tüketilir, seri korunur.
     * - Eğer kalkansız veya 1 günden fazla kaçırıldıysa: seri 1'e sıfırlanır.
     *
     * @return Günlük hedefe ulaşılıp seri sağlandıysa true, aksi halde false.
     */
    suspend fun recordDailyActivity(
        questionsSolvedDelta: Int = 0,
        questionsAddedDelta: Int = 0,
        customDate: String? = null
    ): Boolean {
        var streakAchieved = false
        context.dataStore.edit { preferences ->
            val todayStr = customDate ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

            val currentWeek = try {
                val cal = Calendar.getInstance()
                if (customDate != null) {
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    sdf.parse(customDate)?.let { cal.time = it }
                }
                cal.get(Calendar.WEEK_OF_YEAR)
            } catch (_: Exception) {
                1
            }

            val lastResetWeek = preferences[KEY_LAST_SHIELD_RESET_WEEK] ?: DEFAULT_LAST_SHIELD_RESET_WEEK
            if (lastResetWeek != currentWeek) {
                preferences[KEY_STREAK_SHIELD_COUNT] = DEFAULT_STREAK_SHIELD_COUNT
                preferences[KEY_LAST_SHIELD_RESET_WEEK] = currentWeek
            }

            var shieldCount = preferences[KEY_STREAK_SHIELD_COUNT] ?: DEFAULT_STREAK_SHIELD_COUNT
            val lastStreakDateStr = preferences[KEY_LAST_STREAK_DATE] ?: DEFAULT_LAST_STREAK_DATE
            var streak = preferences[KEY_CURRENT_STREAK] ?: DEFAULT_CURRENT_STREAK

            val activityDate = preferences[KEY_TODAY_ACTIVITY_DATE] ?: ""
            val currentSolved = if (activityDate == todayStr) (preferences[KEY_TODAY_SOLVED_COUNT] ?: 0) else 0
            val currentAdded = if (activityDate == todayStr) (preferences[KEY_TODAY_ADDED_COUNT] ?: 0) else 0

            val newSolved = currentSolved + questionsSolvedDelta
            val newAdded = currentAdded + questionsAddedDelta

            preferences[KEY_TODAY_ACTIVITY_DATE] = todayStr
            preferences[KEY_TODAY_SOLVED_COUNT] = newSolved
            preferences[KEY_TODAY_ADDED_COUNT] = newAdded

            val goalReached = newSolved >= 5 || newAdded >= 3

            if (goalReached) {
                streakAchieved = true
                if (lastStreakDateStr == todayStr) {
                    // Bugün zaten seri yapılmış, aynı seri korunur
                } else if (lastStreakDateStr.isBlank()) {
                    // İlk seri başlangıcı
                    streak = 1
                    preferences[KEY_CURRENT_STREAK] = 1
                    preferences[KEY_LAST_STREAK_DATE] = todayStr
                } else {
                    val diffDays = calculateDayDifference(lastStreakDateStr, todayStr)
                    when {
                        diffDays <= 0 -> {
                            // Aynı gün ya da geçmiş gün: seri korunur
                        }
                        diffDays == 1 -> {
                            // Dün kaydedilmiş, ardışık gün: seri + 1 artar
                            streak += 1
                            preferences[KEY_CURRENT_STREAK] = streak
                            preferences[KEY_LAST_STREAK_DATE] = todayStr
                        }
                        diffDays == 2 -> {
                            // 1 gün kaçırılmış (dünden önceki gün son aktivite)
                            if (shieldCount > 0) {
                                shieldCount -= 1
                                preferences[KEY_STREAK_SHIELD_COUNT] = shieldCount
                                // Seri korunur ve bugünkü aktiviteyle +1 devam eder
                                streak += 1
                                preferences[KEY_CURRENT_STREAK] = streak
                                preferences[KEY_LAST_STREAK_DATE] = todayStr
                            } else {
                                // Kalkan yok: seri 1'e sıfırlanır
                                streak = 1
                                preferences[KEY_CURRENT_STREAK] = 1
                                preferences[KEY_LAST_STREAK_DATE] = todayStr
                            }
                        }
                        else -> {
                            // > 1 gün kaçırılmış: seri 1'e sıfırlanır
                            streak = 1
                            preferences[KEY_CURRENT_STREAK] = 1
                            preferences[KEY_LAST_STREAK_DATE] = todayStr
                        }
                    }
                }
            }
        }
        return streakAchieved
    }

    suspend fun addXp(amount: Int): Int {
        var updatedXp = 0
        context.dataStore.edit { preferences ->
            val currentXp = preferences[KEY_USER_XP] ?: DEFAULT_USER_XP
            updatedXp = (currentXp + amount).coerceAtLeast(0)
            preferences[KEY_USER_XP] = updatedXp
        }
        return updatedXp
    }

    suspend fun unlockBadge(badgeId: String): Boolean {
        var isNew = false
        context.dataStore.edit { preferences ->
            val currentBadges = preferences[KEY_UNLOCKED_BADGES]?.toMutableSet() ?: mutableSetOf()
            if (!currentBadges.contains(badgeId)) {
                currentBadges.add(badgeId)
                preferences[KEY_UNLOCKED_BADGES] = currentBadges
                isNew = true
            }
        }
        return isNew
    }

    suspend fun unlockBadges(badgeIds: Set<String>): Set<String> {
        val newlyUnlocked = mutableSetOf<String>()
        context.dataStore.edit { preferences ->
            val currentBadges = preferences[KEY_UNLOCKED_BADGES]?.toMutableSet() ?: mutableSetOf()
            badgeIds.forEach { id ->
                if (!currentBadges.contains(id)) {
                    currentBadges.add(id)
                    newlyUnlocked.add(id)
                }
            }
            if (newlyUnlocked.isNotEmpty()) {
                preferences[KEY_UNLOCKED_BADGES] = currentBadges
            }
        }
        return newlyUnlocked
    }

    suspend fun setStreakShieldCount(count: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_STREAK_SHIELD_COUNT] = count
        }
    }

    suspend fun restoreGamificationState(
        streak: Int,
        lastStreakDate: String,
        streakShieldCount: Int = 1,
        xp: Int = 0,
        badges: Set<String> = emptySet()
    ) {
        context.dataStore.edit { preferences ->
            preferences[KEY_CURRENT_STREAK] = streak
            preferences[KEY_LAST_STREAK_DATE] = lastStreakDate
            preferences[KEY_STREAK_SHIELD_COUNT] = streakShieldCount
            preferences[KEY_USER_XP] = xp
            if (badges.isNotEmpty()) {
                val current = preferences[KEY_UNLOCKED_BADGES]?.toMutableSet() ?: mutableSetOf()
                current.addAll(badges)
                preferences[KEY_UNLOCKED_BADGES] = current
            }
        }
    }

    suspend fun getCurrentStreak(): Int = currentStreak.first()
    suspend fun getLastStreakDate(): String = lastStreakDate.first()
    suspend fun getStreakShieldCount(): Int = streakShieldCount.first()
    suspend fun getUserXp(): Int = userXp.first()
    suspend fun getUnlockedBadges(): Set<String> = unlockedBadges.first()

    // TODO: activeApiKey cache - her çağrıda 2 Flow first() yapılıyor, in-memory cache + combine ile optimize edilebilir
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

    val driveLastSyncTime: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[KEY_DRIVE_LAST_SYNC_TIME] ?: 0L
    }

    val examTargetDate: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[KEY_EXAM_TARGET_DATE] ?: DEFAULT_EXAM_TARGET_DATE
    }

    // ==========================================
    // Kamera Ayarları Flows
    // ==========================================

    /** "Uzun Pozlama": birden fazla karenin akıllıca birleştirilmesi (kamera ayarında anahtar). */
    val kameraUzunPozlamaAkisi: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_KAMERA_UZUN_POZLAMA] ?: DEFAULT_KAMERA_UZUN_POZLAMA
    }

    /** "Otomatik Çekim": soru kilitlendiğinde fotoğrafın otomatik çekilmesi (yalnız tekli mod). */
    val kameraOtomatikCekimAkisi: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_KAMERA_OTOMATIK_CEKIM] ?: DEFAULT_KAMERA_OTOMATIK_CEKIM
    }

    suspend fun setExamTargetDate(dateMillis: Long) {
        context.dataStore.edit { preferences ->
            preferences[KEY_EXAM_TARGET_DATE] = dateMillis
        }
    }

    suspend fun getExamTargetDate(): Long = examTargetDate.first()

    suspend fun setDriveLastSyncTime(time: Long) {
        context.dataStore.edit { preferences ->
            preferences[KEY_DRIVE_LAST_SYNC_TIME] = time
        }
    }

    suspend fun setKameraUzunPozlama(acik: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_KAMERA_UZUN_POZLAMA] = acik
        }
    }

    suspend fun setKameraOtomatikCekim(acik: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_KAMERA_OTOMATIK_CEKIM] = acik
        }
    }

    suspend fun getDriveLastSyncTime(): Long = driveLastSyncTime.first()

    suspend fun getKameraUzunPozlama(): Boolean = kameraUzunPozlamaAkisi.first()
    suspend fun getKameraOtomatikCekim(): Boolean = kameraOtomatikCekimAkisi.first()
}
