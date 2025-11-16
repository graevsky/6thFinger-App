package com.example.a6thfingercontrollapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.a6thfingercontrollapp.data.AppSettingsStore
import com.example.a6thfingercontrollapp.data.AuthRepository
import com.example.a6thfingercontrollapp.data.AuthState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import retrofit2.HttpException

sealed class UiAuthState {
    object Loading : UiAuthState()
    object Guest : UiAuthState()
    object Unauthenticated : UiAuthState()
    data class LoggedIn(val username: String) : UiAuthState()
}

class AuthViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = AuthRepository(app)
    private val appSettings = AppSettingsStore(app)

    private val _auth = MutableStateFlow<UiAuthState>(UiAuthState.Loading)
    val auth: StateFlow<UiAuthState> = _auth

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var lastRegisteredUser: String? = null

    private val pendingAppSettingsUpload = MutableStateFlow<Map<String, Any?>?>(null)

    init {
        viewModelScope.launch {
            val st = repo.resolveInitialState()
            _auth.value = when (st) {
                is AuthState.Guest -> UiAuthState.Guest
                is AuthState.LoggedIn -> UiAuthState.LoggedIn(st.username)
                is AuthState.Unauthenticated -> UiAuthState.Unauthenticated
            }
        }

        viewModelScope.launch {
            uploadSettingsWorker()
        }

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

    fun register(username: String, password: String, onSuccess: (() -> Unit)? = null) {
        viewModelScope.launch {
            try {
                val normalized = username.trim().lowercase()
                repo.register(normalized, password)
                lastRegisteredUser = normalized
                _auth.value = UiAuthState.Unauthenticated
                _error.value = null
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
                }
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repo.logout()
            _auth.value = UiAuthState.Unauthenticated
            _error.value = null
            pendingAppSettingsUpload.value = null
        }
    }


    fun scheduleAppSettingsUpload(payload: Map<String, Any?>) {
        val state = _auth.value
        if (state is UiAuthState.LoggedIn) {
            pendingAppSettingsUpload.value = payload
        }
    }

    private suspend fun syncAppSettingsOnLogin(username: String) {
        val localLang = appSettings.getLanguage().first()

        if (lastRegisteredUser == username) {
            lastRegisteredUser = null
            try {
                repo.pushAppSettings(mapOf("language" to localLang))
            } catch (_: Exception) {
                pendingAppSettingsUpload.value = mapOf("language" to localLang)
            }
            return
        }

        try {
            val payload = repo.pullAppSettings()
            if (payload != null && payload["language"] != null) {
                val remoteLang = payload["language"].toString()
                appSettings.setLanguage(remoteLang)
            } else {
                try {
                    repo.pushAppSettings(mapOf("language" to localLang))
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
            try {
                val payload = repo.pullAppSettings()
                if (payload != null && payload["language"] != null) {
                    val lang = payload["language"].toString()
                    appSettings.setLanguage(lang)
                }
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Воркер, который ждёт, пока в [pendingAppSettingsUpload] что-то появится,
     * и пытается отгрузить на сервер. При ошибке – ретраи с задержкой.
     */
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
                repo.pushAppSettings(payload)
                pendingAppSettingsUpload.value = null
            } catch (_: Exception) {
                delay(retryDelayMs)
            }
        }
    }
}
