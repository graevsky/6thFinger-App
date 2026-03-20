package com.example.a6thfingercontrollapp.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "app_settings")

class AppSettingsStore(private val context: Context) {
    private object Keys {
        val LANGUAGE = stringPreferencesKey("language")
        val AVATAR_PATH = stringPreferencesKey("avatar_path")

        // cache for offline UX
        val CACHED_EMAIL = stringPreferencesKey("cached_email")
        val CACHED_DEVICES_JSON = stringPreferencesKey("cached_devices_json")
    }

    fun getLanguage(): Flow<String> =
        context.dataStore.data.map { it[Keys.LANGUAGE] ?: "ru" }

    suspend fun setLanguage(code: String) {
        context.dataStore.edit { it[Keys.LANGUAGE] = code }
    }

    fun getAvatarPath(): Flow<String?> =
        context.dataStore.data.map { it[Keys.AVATAR_PATH] }

    suspend fun setAvatarPath(path: String?) {
        context.dataStore.edit { prefs ->
            if (path.isNullOrBlank()) prefs.remove(Keys.AVATAR_PATH)
            else prefs[Keys.AVATAR_PATH] = path
        }
    }

    fun getCachedEmail(): Flow<String?> =
        context.dataStore.data.map { it[Keys.CACHED_EMAIL] }

    suspend fun setCachedEmail(email: String?) {
        context.dataStore.edit { prefs ->
            if (email.isNullOrBlank()) prefs.remove(Keys.CACHED_EMAIL)
            else prefs[Keys.CACHED_EMAIL] = email
        }
    }

    fun getCachedDevicesJson(): Flow<String?> =
        context.dataStore.data.map { it[Keys.CACHED_DEVICES_JSON] }

    suspend fun setCachedDevicesJson(json: String?) {
        context.dataStore.edit { prefs ->
            if (json.isNullOrBlank()) prefs.remove(Keys.CACHED_DEVICES_JSON)
            else prefs[Keys.CACHED_DEVICES_JSON] = json
        }
    }

    suspend fun clearAccountCache() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.CACHED_EMAIL)
            prefs.remove(Keys.CACHED_DEVICES_JSON)
        }
    }
}