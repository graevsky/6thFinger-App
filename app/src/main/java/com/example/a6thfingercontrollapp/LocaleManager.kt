package com.example.a6thfingercontrollapp

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Locale helper used by Application and Activity context setup.
 */
object LocaleManager {
    /**
     * Returns a context configured for the requested language code.
     */
    fun setLocale(context: Context, lang: String): Context {
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
