package com.example.a6thfingercontrolapp.account

import android.app.Application
import com.example.a6thfingercontrolapp.auth.applyRemoteAppSettingsPayload
import com.example.a6thfingercontrolapp.auth.buildLocalAppSettingsPayload
import com.example.a6thfingercontrolapp.auth.clearLocalAvatarCopies
import com.example.a6thfingercontrolapp.auth.persistDownloadedAvatarPath
import com.example.a6thfingercontrolapp.auth.pushMergedAppSettingsPayload
import com.example.a6thfingercontrolapp.ble.settings.EspSettings
import com.example.a6thfingercontrolapp.data.AppSettingsStore
import com.example.a6thfingercontrolapp.data.repositories.AuthRepository
import com.example.a6thfingercontrolapp.data.DeviceSettingsRecord
import com.example.a6thfingercontrolapp.network.DeviceOut
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

/**
 * Post login account sync, retry workers, local cache cleanup for logout.
 */
class AccountSyncCoordinator(
    private val app: Application
) {
    private val repo = AuthRepository.get(app)
    private val appSettings = AppSettingsStore(app)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val session = MutableStateFlow<AccountSessionState>(AccountSessionState.SignedOut)
    private val pendingAppSettingsUpload = MutableStateFlow<Map<String, Any?>?>(null)
    private val pendingAvatarUploadPath = MutableStateFlow<String?>(null)
    private val _sessionExpiredEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private var settingsSyncReady = false
    private var applyingRemoteSettings = false

    val sessionExpiredEvents: SharedFlow<Unit> = _sessionExpiredEvents

    init {
        scope.launch { uploadSettingsWorker() }
        scope.launch { uploadAvatarWorker() }
        scope.launch { periodicSettingsPullLoop() }
        scope.launch { themeChangeSyncLoop() }
    }

    suspend fun onLoggedIn(username: String, preferLocalSettings: Boolean) {
        settingsSyncReady = false
        session.value = AccountSessionState.LoggedIn(username)
        syncAppSettingsOnLogin(username, preferLocalSettings)
        settingsSyncReady = true
        scope.launch { tryPullAvatarToLocal() }
    }

    fun onGuest() {
        settingsSyncReady = false
        pendingAppSettingsUpload.value = null
        pendingAvatarUploadPath.value = null
        session.value = AccountSessionState.Guest
    }

    suspend fun onLoggedOut() {
        session.value = AccountSessionState.SignedOut
        clearAccountCache()
    }

    fun scheduleAvatarUpload(localPath: String) {
        if (session.value is AccountSessionState.LoggedIn) {
            pendingAvatarUploadPath.value = localPath
        }
    }

    fun deleteAvatarRemote() {
        if (session.value !is AccountSessionState.LoggedIn) return

        scope.launch {
            runCatching {
                runProtected { repo.deleteAvatarRemote() }
            }
        }
    }

    suspend fun fetchDevices(): List<DeviceOut> =
        runProtected { repo.listDevices() }

    suspend fun fetchDeviceSettingsRecord(deviceId: String): DeviceSettingsRecord? =
        runProtected { repo.getDeviceSettingsRecord(deviceId) }

    suspend fun pushDeviceSettings(
        deviceId: String,
        settings: EspSettings
    ): DeviceSettingsRecord = runProtected { repo.pushDeviceSettings(deviceId, settings) }

    suspend fun ensureDevice(address: String, alias: String?): DeviceOut =
        runProtected { repo.ensureDevice(address, alias) }

    fun updateAppSettingsRemote(patch: Map<String, Any?>) {
        if (session.value !is AccountSessionState.LoggedIn) return

        scope.launch {
            val payload = localAppSettingsPayload().toMutableMap().apply { putAll(patch) }
            try {
                pushMergedAppSettings(payload)
                pendingAppSettingsUpload.value = null
            } catch (_: Exception) {
                pendingAppSettingsUpload.value = payload
            }
        }
    }

    fun updateLanguageRemote(newLang: String) {
        updateAppSettingsRemote(mapOf("language" to newLang))
    }

    private suspend fun <T> runProtected(action: suspend () -> T): T {
        try {
            return action()
        } catch (e: Exception) {
            if (e.message == "session_expired") {
                handleSessionExpired()
            }
            throw e
        }
    }

    private suspend fun handleSessionExpired() {
        session.value = AccountSessionState.SignedOut
        clearAccountCache()
        _sessionExpiredEvents.tryEmit(Unit)
    }

    private suspend fun clearAccountCache() {
        settingsSyncReady = false
        pendingAppSettingsUpload.value = null
        pendingAvatarUploadPath.value = null
        clearLocalAvatarCopies(app, appSettings)
        appSettings.clearAccountCache()
    }

    private suspend fun localAppSettingsPayload(): Map<String, Any?> =
        buildLocalAppSettingsPayload(appSettings)

    private suspend fun applyRemoteAppSettings(payload: Map<String, Any?>) {
        applyRemoteAppSettingsPayload(payload, appSettings) { applyingRemoteSettings = it }
    }

    private suspend fun pushMergedAppSettings(localPayload: Map<String, Any?>) {
        pushMergedAppSettingsPayload(
            localPayload = localPayload,
            pullRemote = { runProtected { repo.pullAppSettings() }.orEmpty() },
            pushPayload = { payload -> runProtected { repo.pushAppSettings(payload) } }
        )
    }

    private suspend fun syncAppSettingsOnLogin(
        username: String,
        preferLocalSettings: Boolean
    ) {
        val localPayload = localAppSettingsPayload()

        if (preferLocalSettings) {
            try {
                pushMergedAppSettings(localPayload)
            } catch (_: Exception) {
                pendingAppSettingsUpload.value = localPayload
            }
            return
        }

        try {
            val payload = runProtected { repo.pullAppSettings() }
            if (payload != null) {
                applyRemoteAppSettings(payload)

                val needsBackfill = payload["language"] == null || payload["theme"] == null
                if (needsBackfill) {
                    val backfilled =
                        payload.toMutableMap().apply { putAll(localAppSettingsPayload()) }
                    runProtected { repo.pushAppSettings(backfilled) }
                }
            } else {
                try {
                    pushMergedAppSettings(localPayload)
                } catch (_: Exception) {
                    pendingAppSettingsUpload.value = localPayload
                }
            }
        } catch (_: Exception) {
            pendingAppSettingsUpload.value = localPayload
        }
    }

    private suspend fun periodicSettingsPullLoop() {
        val intervalMs = 15 * 60 * 1000L
        while (true) {
            val currentSession = session.value
            if (currentSession !is AccountSessionState.LoggedIn) {
                delay(1_000L)
                continue
            }

            delay(intervalMs)

            if (session.value !is AccountSessionState.LoggedIn) continue
            if (pendingAppSettingsUpload.value != null) continue

            runCatching {
                runProtected { repo.pullAppSettings() }
            }.onSuccess { payload ->
                if (payload != null) {
                    applyRemoteAppSettings(payload)
                }
            }
        }
    }

    private suspend fun themeChangeSyncLoop() {
        appSettings.getThemeMode().collect { theme ->
            if (session.value is AccountSessionState.LoggedIn &&
                settingsSyncReady &&
                !applyingRemoteSettings
            ) {
                updateAppSettingsRemote(mapOf("theme" to theme))
            }
        }
    }

    private suspend fun uploadSettingsWorker() {
        val retryDelayMs = 30_000L
        while (true) {
            val payload = pendingAppSettingsUpload.value
            val currentSession = session.value

            if (payload == null || currentSession !is AccountSessionState.LoggedIn) {
                delay(1_000L)
                continue
            }

            try {
                pushMergedAppSettings(payload)
                pendingAppSettingsUpload.value = null
            } catch (_: Exception) {
                delay(retryDelayMs)
            }
        }
    }

    private suspend fun uploadAvatarWorker() {
        val retryDelayMs = 30_000L
        while (true) {
            val path = pendingAvatarUploadPath.value
            val currentSession = session.value

            if (path.isNullOrBlank() || currentSession !is AccountSessionState.LoggedIn) {
                delay(1_000L)
                continue
            }

            try {
                runProtected { repo.uploadAvatar(path) }
                pendingAvatarUploadPath.value = null
            } catch (_: Exception) {
                delay(retryDelayMs)
            }
        }
    }

    private suspend fun tryPullAvatarToLocal() {
        runCatching {
            runProtected { repo.downloadAvatarToLocal() }
        }.onSuccess { path ->
            persistDownloadedAvatarPath(app, appSettings, path)
        }
    }
}

private sealed class AccountSessionState {
    data object SignedOut : AccountSessionState()
    data object Guest : AccountSessionState()
    data class LoggedIn(val username: String) : AccountSessionState()
}
