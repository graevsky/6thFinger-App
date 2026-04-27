package com.example.a6thfingercontrolapp.auth

import android.app.Application
import com.example.a6thfingercontrolapp.data.AppSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import com.example.a6thfingercontrolapp.data.avatarFile as dataAvatarFile
import com.example.a6thfingercontrolapp.data.deleteAvatarIfExists as dataDeleteAvatarIfExists

/**
 * Removes locally cached avatar file and clears the stored avatar path.
 */
internal suspend fun clearLocalAvatarCopies(
    app: Application,
    appSettings: AppSettingsStore
) {
    val avatarPath = appSettings.getAvatarPath().first()

    withContext(Dispatchers.IO) {
        dataDeleteAvatarIfExists(avatarPath)
        runCatching { dataAvatarFile(app).delete() }
    }

    appSettings.setAvatarPath(null)
}

/**
 * Stores downloaded avatar path.
 * If no avatar exists, local data is cleared.
 */
internal suspend fun persistDownloadedAvatarPath(
    app: Application,
    appSettings: AppSettingsStore,
    path: String?
) {
    if (!path.isNullOrBlank()) {
        appSettings.setAvatarPath(path)
    } else {
        clearLocalAvatarCopies(app, appSettings)
    }
}
