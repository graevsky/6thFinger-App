package com.example.a6thfingercontrollapp

import android.app.Application
import com.example.a6thfingercontrollapp.data.AppSettingsStore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val prefs = AppSettingsStore(this)
        runBlocking {
            val lang = prefs.getLanguage().first()
            LocaleManager.setLocale(this@App, lang)
        }
    }
}
