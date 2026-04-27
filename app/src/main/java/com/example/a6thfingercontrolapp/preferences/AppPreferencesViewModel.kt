package com.example.a6thfingercontrolapp.preferences

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * App wide language and theme preferences used by screens.
 */
class AppPreferencesViewModel(app: Application) : AndroidViewModel(app) {
    private val preferences = AppPreferencesController(app, viewModelScope)

    val appLanguage: StateFlow<String> = preferences.language
    val appTheme: StateFlow<String> = preferences.themeMode

    fun setAppLanguage(language: String, afterApplied: (() -> Unit)? = null) {
        viewModelScope.launch {
            preferences.setLanguage(language)
            afterApplied?.invoke()
        }
    }

    fun setAppTheme(themeMode: String) {
        viewModelScope.launch {
            preferences.setThemeMode(themeMode)
        }
    }
}
