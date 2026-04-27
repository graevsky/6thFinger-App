package com.example.a6thfingercontrolapp.auth

import com.example.a6thfingercontrolapp.data.AppSettingsStore
import com.example.a6thfingercontrolapp.data.normalizeAppThemeMode
import kotlinx.coroutines.flow.first

/**
 * Account & app settings payload build based on local preferences.
 */
internal suspend fun buildLocalAppSettingsPayload(
    appSettings: AppSettingsStore
): Map<String, Any?> = mapOf(
    "language" to appSettings.getLanguage().first(),
    "theme" to appSettings.getThemeMode().first()
)

/**
 * Applies settings received from backend to local storage.
 */
internal suspend fun applyRemoteAppSettingsPayload(
    payload: Map<String, Any?>,
    appSettings: AppSettingsStore,
    setApplyingRemoteSettings: (Boolean) -> Unit
) {
    setApplyingRemoteSettings(true)
    try {
        val lang = payload["language"]?.toString()?.trim()?.takeIf { it.isNotBlank() }
        if (lang != null) {
            appSettings.setLanguage(lang)
        }

        val theme = payload["theme"]?.toString()?.trim()?.takeIf { it.isNotBlank() }
        if (theme != null) {
            appSettings.setThemeMode(normalizeAppThemeMode(theme))
        }
    } finally {
        setApplyingRemoteSettings(false)
    }
}

/**
 * Updated app settings.
 */
internal suspend fun pushMergedAppSettingsPayload(
    localPayload: Map<String, Any?>,
    pullRemote: suspend () -> Map<String, Any?>,
    pushPayload: suspend (Map<String, Any?>) -> Unit
) {
    val remote = pullRemote()
    val merged = remote.toMutableMap().apply { putAll(localPayload) }
    pushPayload(merged)
}