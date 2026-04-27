package com.example.a6thfingercontrolapp.preferences

import android.content.Context
import com.example.a6thfingercontrolapp.data.AppSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * App settings store wrapper for update helpers.
 */
class AppPreferencesController(
    context: Context,
    private val scope: CoroutineScope
) {
    private val store = AppSettingsStore(context)

    val language: StateFlow<String> =
        store.getLanguage().stateIn(scope, SharingStarted.Companion.Eagerly, "ru")

    val themeMode: StateFlow<String> =
        store.getThemeMode().stateIn(scope, SharingStarted.Companion.Eagerly, "system")

    suspend fun setLanguage(language: String) {
        store.setLanguage(language)
    }

    suspend fun setThemeMode(themeMode: String) {
        store.setThemeMode(themeMode)
    }
}