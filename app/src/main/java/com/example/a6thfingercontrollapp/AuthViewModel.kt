package com.example.a6thfingercontrollapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.a6thfingercontrollapp.data.AuthRepository
import com.example.a6thfingercontrollapp.data.AuthState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class UiAuthState {
    object Loading : UiAuthState()
    object Guest : UiAuthState()
    object Unauthenticated : UiAuthState()
    data class LoggedIn(val username: String) : UiAuthState()
}

class AuthViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = AuthRepository(app)

    private val _auth = MutableStateFlow<UiAuthState>(UiAuthState.Loading)
    val auth: StateFlow<UiAuthState> = _auth

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        viewModelScope.launch {
            val st = repo.resolveInitialState()
            _auth.value = when (st) {
                is AuthState.Guest -> UiAuthState.Guest
                is AuthState.LoggedIn -> UiAuthState.LoggedIn(st.username)
                is AuthState.Unauthenticated -> UiAuthState.Unauthenticated
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
        }
    }

    fun register(username: String, password: String) {
        viewModelScope.launch {
            try {
                repo.register(username, password)
                _auth.value = UiAuthState.Unauthenticated
            } catch (e: Exception) {
                _error.value = e.message ?: "Registration failed"
            }
        }
    }

    fun login(username: String, password: String) {
        viewModelScope.launch {
            try {
                val st = repo.login(username, password)
                if (st is AuthState.LoggedIn) {
                    _auth.value = UiAuthState.LoggedIn(st.username)
                } else {
                    _error.value = "Login failed"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Login failed"
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repo.logout()
            _auth.value = UiAuthState.Unauthenticated
        }
    }

    fun pullAppSettings(onLoaded: (Map<String, Any?>) -> Unit) {
        viewModelScope.launch {
            try {
                val payload = repo.pullAppSettings()
                if (payload != null) onLoaded(payload)
            } catch (e: Exception) {
                _error.value = e.message ?: "Settings load failed"
            }
        }
    }

    fun pushAppSettings(payload: Map<String, Any?>) {
        viewModelScope.launch {
            try {
                repo.pushAppSettings(payload)
            } catch (e: Exception) {
                _error.value = e.message ?: "Settings upload failed"
            }
        }
    }
}
