package com.example.a6thfingercontrolapp.utils

import com.example.a6thfingercontrolapp.BuildConfig

/**
 * Centralized access to build-time feature flags.
 */
object FeatureFlags {
    val isEmailEnabled: Boolean
        get() = !BuildConfig.EMAIL_OFF
}
