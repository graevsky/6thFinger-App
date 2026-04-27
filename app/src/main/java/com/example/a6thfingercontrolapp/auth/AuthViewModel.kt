package com.example.a6thfingercontrolapp.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.a6thfingercontrolapp.data.repositories.AccountSyncRepository
import com.example.a6thfingercontrolapp.data.repositories.AuthRepository
import com.example.a6thfingercontrolapp.data.AuthState
import com.example.a6thfingercontrolapp.network.PasswordResetStartOut
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Authentication state exposed to the UI layer.
 */
sealed class UiAuthState {
    object Loading : UiAuthState()
    object Guest : UiAuthState()
    object Unauthenticated : UiAuthState()
    data class LoggedIn(val username: String) : UiAuthState()
}

/**
 * Temporary registration result that must be shown to the user before finishing signup.
 */
data class PendingRecoveryCodes(
    val username: String,
    val codes: List<String>
)

/**
 * ViewModel that owns authentication flow.
 *
 * Post-login account sync, avatar sync and app-settings sync are delegated.
 */
class AuthViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = AuthRepository.get(app)
    private val accountSync = AccountSyncRepository.get(app)

    private val _auth = MutableStateFlow<UiAuthState>(UiAuthState.Loading)
    val auth: StateFlow<UiAuthState> = _auth

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var lastRegisteredUser: String? = null

    private val _pendingRecoveryCodes = MutableStateFlow<PendingRecoveryCodes?>(null)
    val pendingRecoveryCodes: StateFlow<PendingRecoveryCodes?> = _pendingRecoveryCodes

    /** Credentials are kept only during post-registration email setup flow. */
    private var pendingRegisterUsername: String? = null
    private var pendingRegisterPassword: String? = null

    init {
        viewModelScope.launch {
            accountSync.sessionExpiredEvents.collect {
                applyUnauthenticatedUiState(clearError = true)
            }
        }

        viewModelScope.launch {
            when (val st = repo.resolveInitialState()) {
                is AuthState.Guest -> {
                    accountSync.onGuest()
                    _auth.value = UiAuthState.Guest
                }

                is AuthState.LoggedIn -> activateLoggedInSession(st)
                is AuthState.Unauthenticated -> _auth.value = UiAuthState.Unauthenticated
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    /**
     * Switches the app into guest mode without creating a server session.
     */
    fun continueAsGuest() {
        viewModelScope.launch {
            repo.continueAsGuest()
            accountSync.onGuest()
            _auth.value = UiAuthState.Guest
            _error.value = null
        }
    }

    private fun clearPostRegisterState() {
        pendingRegisterUsername = null
        pendingRegisterPassword = null
        _pendingRecoveryCodes.value = null
    }

    private fun applyUnauthenticatedUiState(clearError: Boolean) {
        if (clearError) {
            _error.value = null
        }
        clearPostRegisterState()
        _auth.value = UiAuthState.Unauthenticated
    }

    /**
     * Clears account-side cached data and resets auth UI state.
     */
    private suspend fun forceUnauthenticatedCleanup() {
        accountSync.onLoggedOut()
        applyUnauthenticatedUiState(clearError = true)
    }

    /**
     * Runs an authorized operation and forces local logout if the server session expired.
     */
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

    /**
     * Starts the dedicated account sync controller after a successful login.
     */
    private suspend fun activateLoggedInSession(state: AuthState.LoggedIn) {
        val preferLocalSettings = lastRegisteredUser == state.username
        if (preferLocalSettings) {
            lastRegisteredUser = null
        }

        _auth.value = UiAuthState.LoggedIn(state.username)
        _error.value = null
        accountSync.onLoggedIn(state.username, preferLocalSettings)
    }

    /**
     * Registers a new user and stores recovery codes for the mandatory post-register screen.
     */
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

    /**
     * Performs login and starts account sync.
     */
    fun login(username: String, password: String) {
        viewModelScope.launch {
            try {
                val normalized = username.trim().lowercase()
                val st = repo.login(normalized, password)
                if (st is AuthState.LoggedIn) {
                    activateLoggedInSession(st)
                }
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    /**
     * Logs out remotely when possible and always clears local account state.
     */
    fun logout() {
        viewModelScope.launch {
            repo.logout()
            forceUnauthenticatedCleanup()
        }
    }

    /**
     * Finishes registration without adding email and immediately logs in.
     */
    suspend fun postRegisterFinishWithoutEmail() {
        val u = pendingRegisterUsername ?: throw Exception("Registration state expired")
        val p = pendingRegisterPassword ?: throw Exception("Registration state expired")

        val st = repo.login(u, p)
        if (st is AuthState.LoggedIn) {
            activateLoggedInSession(st)
        }
        clearPostRegisterState()
    }

    /**
     * Logs in during registration if needed and starts email verification.
     */
    suspend fun postRegisterEmailStart(email: String) {
        val u = pendingRegisterUsername ?: throw Exception("Registration state expired")
        val p = pendingRegisterPassword ?: throw Exception("Registration state expired")

        if (auth.value !is UiAuthState.LoggedIn) {
            val st = repo.login(u, p)
            if (st is AuthState.LoggedIn) {
                activateLoggedInSession(st)
            }
        }

        runProtected { repo.emailStartAdd(email) }
    }

    /**
     * Confirms post-registration email and clears temporary registration state.
     */
    suspend fun postRegisterEmailConfirm(email: String, code: String) {
        runProtected { repo.emailConfirmAdd(email, code) }
        clearPostRegisterState()
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
}
