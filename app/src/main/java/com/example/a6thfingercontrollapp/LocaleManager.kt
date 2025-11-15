package com.example.a6thfingercontrollapp

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleManager {
    fun setLocale(context: Context, lang: String): Context {
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
