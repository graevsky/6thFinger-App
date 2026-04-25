package com.example.a6thfingercontrollapp

import android.app.Application
import com.example.a6thfingercontrollapp.data.AppSettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Application entry point.
 */
class App : Application() {
    /**
     * Loads the saved language before the first activity is created.
     */
    override fun onCreate() {
        super.onCreate()
        val prefs = AppSettingsStore(this)
        runBlocking {
            val lang = prefs.getLanguage().first()
            LocaleManager.setLocale(this@App, lang)
        }
    }
}
