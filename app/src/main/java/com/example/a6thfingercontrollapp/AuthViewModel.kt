package com.example.a6thfingercontrollapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.a6thfingercontrollapp.ble.EspSettings
import com.example.a6thfingercontrollapp.data.AppSettingsStore
import com.example.a6thfingercontrollapp.data.AuthRepository
import com.example.a6thfingercontrollapp.data.AuthState
import com.example.a6thfingercontrollapp.data.DeviceSettingsRecord
import com.example.a6thfingercontrollapp.network.DeviceOut
import com.example.a6thfingercontrollapp.network.PasswordResetStartOut
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.a6thfingercontrollapp.data.avatarFile as dataAvatarFile
import com.example.a6thfingercontrollapp.data.deleteAvatarIfExists as dataDeleteAvatarIfExists

sealed class UiAuthState {
    object Loading : UiAuthState()
    object Guest : UiAuthState()
    object Unauthenticated : UiAuthState()
    data class LoggedIn(val username: String) : UiAuthState()
}

data class PendingRecoveryCodes(
    val username: String,
    val codes: List<String>
)

class AuthViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = AuthRepository(app)
    private val appSettings = AppSettingsStore(app)

    private val _auth = MutableStateFlow<UiAuthState>(UiAuthState.Loading)
    val auth: StateFlow<UiAuthState> = _auth

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var lastRegisteredUser: String? = null

    private val pendingAppSettingsUpload = MutableStateFlow<Map<String, Any?>?>(null)
    private val pendingAvatarUploadPath = MutableStateFlow<String?>(null)

    private val _pendingRecoveryCodes = MutableStateFlow<PendingRecoveryCodes?>(null)
    val pendingRecoveryCodes: StateFlow<PendingRecoveryCodes?> = _pendingRecoveryCodes

    private var pendingRegisterUsername: String? = null
    private var pendingRegisterPassword: String? = null

    init {
        viewModelScope.launch {
            val st = repo.resolveInitialState()
            _auth.value =
                when (st) {
                    is AuthState.Guest -> UiAuthState.Guest
                    is AuthState.LoggedIn -> UiAuthState.LoggedIn(st.username)
                    is AuthState.Unauthenticated -> UiAuthState.Unauthenticated
                }

            if (st is AuthState.LoggedIn) {
                tryPullAvatarToLocal()
            }
        }

        viewModelScope.launch { uploadSettingsWorker() }
        viewModelScope.launch { uploadAvatarWorker() }

        viewModelScope.launch {
            auth.collectLatest { state ->
                if (state is UiAuthState.LoggedIn) {
                    periodicSettingsPullLoop()
                }
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun continueAsGuest() {
        viewModelScope.launch {
            repo.continueAsGuest()
            _auth.value = UiAuthState.Guest
            _error.value = null
        }
    }

    private fun clearPostRegisterState() {
        pendingRegisterUsername = null
        pendingRegisterPassword = null
        _pendingRecoveryCodes.value = null
    }

    private suspend fun clearLocalAvatar() {
        val avatarPath = appSettings.getAvatarPath().first()

        withContext(Dispatchers.IO) {
            dataDeleteAvatarIfExists(avatarPath)
            runCatching { dataAvatarFile(getApplication()).delete() }
        }

        appSettings.setAvatarPath(null)
    }

    private suspend fun forceUnauthenticatedCleanup() {
        pendingAppSettingsUpload.value = null
        pendingAvatarUploadPath.value = null
        _error.value = null
        clearPostRegisterState()
        clearLocalAvatar()
        appSettings.clearAccountCache()
        _auth.value = UiAuthState.Unauthenticated
    }

    private suspend fun <T> runProtected(action: suspend () -> T): T {
        try {
            return action()
        } catch (e: Exception) {
            if (e.message == "session_expired") {
                forceUnauthenticatedCleanup()
            }
            throw e
        }
    }

    fun register(username: String, password: String, onSuccess: (() -> Unit)? = null) {
        viewModelScope.launch {
            try {
                val normalized = username.trim().lowercase()
                val codes = repo.register(normalized, password)

                pendingRegisterUsername = normalized
                pendingRegisterPassword = password

                lastRegisteredUser = normalized
                _auth.value = UiAuthState.Unauthenticated
                _error.value = null

                _pendingRecoveryCodes.value = PendingRecoveryCodes(normalized, codes)
                onSuccess?.invoke()
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun login(username: String, password: String) {
        viewModelScope.launch {
            try {
                val normalized = username.trim().lowercase()
                val st = repo.login(normalized, password)
                if (st is AuthState.LoggedIn) {
                    _auth.value = UiAuthState.LoggedIn(st.username)
                    _error.value = null
                    syncAppSettingsOnLogin(st.username)
                    tryPullAvatarToLocal()
                }
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repo.logout()
            forceUnauthenticatedCleanup()
        }
    }

    suspend fun postRegisterFinishWithoutEmail() {
        val u = pendingRegisterUsername ?: throw Exception("Registration state expired")
        val p = pendingRegisterPassword ?: throw Exception("Registration state expired")

        val st = repo.login(u, p)
        if (st is AuthState.LoggedIn) {
            _auth.value = UiAuthState.LoggedIn(st.username)
            _error.value = null
            syncAppSettingsOnLogin(st.username)
            tryPullAvatarToLocal()
        }
        clearPostRegisterState()
    }

    suspend fun postRegisterEmailStart(email: String) {
        val u = pendingRegisterUsername ?: throw Exception("Registration state expired")
        val p = pendingRegisterPassword ?: throw Exception("Registration state expired")

        if (auth.value !is UiAuthState.LoggedIn) {
            val st = repo.login(u, p)
            if (st is AuthState.LoggedIn) {
                _auth.value = UiAuthState.LoggedIn(st.username)
                _error.value = null
                syncAppSettingsOnLogin(st.username)
                tryPullAvatarToLocal()
            }
        }

        runProtected { repo.emailStartAdd(email) }
    }

    suspend fun postRegisterEmailConfirm(email: String, code: String) {
        runProtected { repo.emailConfirmAdd(email, code) }
        clearPostRegisterState()
    }

    fun scheduleAppSettingsUpload(payload: Map<String, Any?>) {
        val state = _auth.value
        if (state is UiAuthState.LoggedIn) {
            pendingAppSettingsUpload.value = payload
        }
    }

    fun scheduleAvatarUpload(localPath: String) {
        val state = _auth.value
        if (state is UiAuthState.LoggedIn) {
            pendingAvatarUploadPath.value = localPath
        }
    }

    fun deleteAvatarRemote() {
        val state = _auth.value
        if (state !is UiAuthState.LoggedIn) return

        viewModelScope.launch {
            runCatching {
                runProtected { repo.deleteAvatarRemote() }
            }
        }
    }

    private fun uploadAvatarWorker() {
        viewModelScope.launch {
            val retryDelayMs = 30_000L
            while (true) {
                val path = pendingAvatarUploadPath.value
                val state = auth.value

                if (path.isNullOrBlank() || state !is UiAuthState.LoggedIn) {
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
    }

    private fun tryPullAvatarToLocal() {
        viewModelScope.launch {
            runCatching {
                runProtected { repo.downloadAvatarToLocal() }
            }.onSuccess { path ->
                if (!path.isNullOrBlank()) {
                    appSettings.setAvatarPath(path)
                } else {
                    clearLocalAvatar()
                }
            }
        }
    }

    suspend fun passwordResetStart(username: String): PasswordResetStartOut =
        repo.passwordResetStart(username)

    suspend fun passwordResetEmailSend(username: String, email: String) =
        repo.passwordResetEmailSend(username, email)

    suspend fun passwordResetEmailVerify(username: String, email: String, code: String): String =
        repo.passwordResetEmailVerify(username, email, code)

    suspend fun passwordResetRecoveryVerify(username: String, recoveryCode: String): String =
        repo.passwordResetRecoveryVerify(username, recoveryCode)

    suspend fun passwordResetFinish(resetSessionId: String, username: String, newPassword: String) =
        repo.passwordResetFinish(resetSessionId, username, newPassword)

    suspend fun emailStartAdd(email: String) = runProtected { repo.emailStartAdd(email) }
    suspend fun emailConfirmAdd(email: String, code: String) =
        runProtected { repo.emailConfirmAdd(email, code) }

    suspend fun emailStartRemove() = runProtected { repo.emailStartRemove() }

    suspend fun emailConfirmRemove(code: String?, recoveryCode: String?) =
        runProtected { repo.emailConfirmRemove(code, recoveryCode) }

    private suspend fun syncAppSettingsOnLogin(username: String) {
        val localLang = appSettings.getLanguage().first()

        if (lastRegisteredUser == username) {
            lastRegisteredUser = null
            try {
                runProtected { repo.pushAppSettings(mapOf("language" to localLang)) }
            } catch (_: Exception) {
                pendingAppSettingsUpload.value = mapOf("language" to localLang)
            }
            return
        }

        try {
            val payload = runProtected { repo.pullAppSettings() }
            if (payload != null && payload["language"] != null) {
                val remoteLang = payload["language"].toString()
                appSettings.setLanguage(remoteLang)
            } else {
                try {
                    runProtected { repo.pushAppSettings(mapOf("language" to localLang)) }
                } catch (_: Exception) {
                    pendingAppSettingsUpload.value = mapOf("language" to localLang)
                }
            }
        } catch (_: Exception) {
            pendingAppSettingsUpload.value = mapOf("language" to localLang)
        }
    }

    private suspend fun periodicSettingsPullLoop() {
        val intervalMs = 15 * 60 * 1000L
        while (auth.value is UiAuthState.LoggedIn) {
            delay(intervalMs)
            runCatching {
                runProtected { repo.pullAppSettings() }
            }.onSuccess { payload ->
                if (payload != null && payload["language"] != null) {
                    val lang = payload["language"].toString()
                    appSettings.setLanguage(lang)
                }
            }
        }
    }

    private suspend fun uploadSettingsWorker() {
        val retryDelayMs = 30_000L
        while (true) {
            val payload = pendingAppSettingsUpload.value
            val state = auth.value

            if (payload == null || state !is UiAuthState.LoggedIn) {
                delay(1_000L)
                continue
            }

            try {
                runProtected { repo.pushAppSettings(payload) }
                pendingAppSettingsUpload.value = null
            } catch (_: Exception) {
                delay(retryDelayMs)
            }
        }
    }

    suspend fun fetchDevices(): List<DeviceOut> =
        runProtected { repo.listDevices() }

    suspend fun fetchDeviceSettingsRecord(deviceId: String): DeviceSettingsRecord? =
        runProtected { repo.getDeviceSettingsRecord(deviceId) }

    suspend fun pushDeviceSettings(deviceId: String, settings: EspSettings): DeviceSettingsRecord =
        runProtected { repo.pushDeviceSettings(deviceId, settings) }

    suspend fun pullDeviceSettings(deviceId: String): EspSettings? =
        runProtected { repo.pullDeviceSettings(deviceId) }

    suspend fun ensureDevice(address: String, alias: String?): DeviceOut =
        runProtected { repo.ensureDevice(address, alias) }

    fun updateLanguageRemote(newLang: String) {
        viewModelScope.launch {
            try {
                runProtected { repo.pushAppSettings(mapOf("language" to newLang)) }
                pendingAppSettingsUpload.value = null
            } catch (_: Exception) {
                pendingAppSettingsUpload.value = mapOf("language" to newLang)
            }
        }
    }
}