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
    }

    fun getLanguage(): Flow<String> = context.dataStore.data.map { it[Keys.LANGUAGE] ?: "ru" }

    suspend fun setLanguage(code: String) {
        context.dataStore.edit { it[Keys.LANGUAGE] = code }
    }
}
