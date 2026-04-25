package com.example.a6thfingercontrolapp.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** App follows the Android system theme. */
const val APP_THEME_SYSTEM = "system"

/** App always uses the light theme. */
const val APP_THEME_LIGHT = "light"

/** App always uses the dark theme. */
const val APP_THEME_DARK = "dark"

/**
 * Normalizes app theme values stored locally or received from the backend.
 */
fun normalizeAppThemeMode(raw: String?): String {
    return when (raw?.trim()?.lowercase()) {
        APP_THEME_LIGHT -> APP_THEME_LIGHT
        APP_THEME_DARK -> APP_THEME_DARK
        else -> APP_THEME_SYSTEM
    }
}

/**
 * DataStore instance for global app settings.
 */
private val Context.dataStore by preferencesDataStore(name = "app_settings")

/**
 * Small persistence layer for app-wide preferences and offline account cache.
 *
 * Responsibilities:
 * - store selected language
 * - store selected app theme mode
 * - store local avatar path
 * - cache account email and cloud devices for offline UX
 */
class AppSettingsStore(private val context: Context) {
    /**
     * Preference keys used by the app settings DataStore.
     */
    private object Keys {
        val LANGUAGE = stringPreferencesKey("language")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val AVATAR_PATH = stringPreferencesKey("avatar_path")

        // cache for offline UX
        val CACHED_EMAIL = stringPreferencesKey("cached_email")
        val CACHED_DEVICES_JSON = stringPreferencesKey("cached_devices_json")
    }

    /**
     * Emits selected language code. Russian by default :)
     */
    fun getLanguage(): Flow<String> =
        context.dataStore.data.map { it[Keys.LANGUAGE] ?: "ru" }

    /**
     * Persists selected language code.
     */
    suspend fun setLanguage(code: String) {
        context.dataStore.edit { it[Keys.LANGUAGE] = code }
    }

    /**
     * Emits selected app theme mode: system, light or dark.
     */
    fun getThemeMode(): Flow<String> =
        context.dataStore.data.map { prefs ->
            normalizeAppThemeMode(prefs[Keys.THEME_MODE])
        }

    /**
     * Persists selected app theme mode.
     */
    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = normalizeAppThemeMode(mode)
        }
    }

    /**
     * Emits the current local avatar file path, if one was saved.
     */
    fun getAvatarPath(): Flow<String?> =
        context.dataStore.data.map { it[Keys.AVATAR_PATH] }

    /**
     * Stores or clears the local avatar path.
     */
    suspend fun setAvatarPath(path: String?) {
        context.dataStore.edit { prefs ->
            if (path.isNullOrBlank()) prefs.remove(Keys.AVATAR_PATH)
            else prefs[Keys.AVATAR_PATH] = path
        }
    }

    /**
     * Emits cached account email used when the network is unavailable.
     */
    fun getCachedEmail(): Flow<String?> =
        context.dataStore.data.map { it[Keys.CACHED_EMAIL] }

    /**
     * Stores or clears cached account email.
     */
    suspend fun setCachedEmail(email: String?) {
        context.dataStore.edit { prefs ->
            if (email.isNullOrBlank()) prefs.remove(Keys.CACHED_EMAIL)
            else prefs[Keys.CACHED_EMAIL] = email
        }
    }

    /**
     * Emits cached cloud device list JSON used as offline fallback.
     */
    fun getCachedDevicesJson(): Flow<String?> =
        context.dataStore.data.map { it[Keys.CACHED_DEVICES_JSON] }

    /**
     * Stores or clears cached cloud device list JSON.
     */
    suspend fun setCachedDevicesJson(json: String?) {
        context.dataStore.edit { prefs ->
            if (json.isNullOrBlank()) prefs.remove(Keys.CACHED_DEVICES_JSON)
            else prefs[Keys.CACHED_DEVICES_JSON] = json
        }
    }

    /**
     * Clears all account-related cached values after logout or session expiry.
     */
    suspend fun clearAccountCache() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.CACHED_EMAIL)
            prefs.remove(Keys.CACHED_DEVICES_JSON)
            prefs.remove(Keys.AVATAR_PATH)
        }
    }
}