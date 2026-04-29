package com.example.a6thfingercontrolapp.ui.account

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.a6thfingercontrolapp.auth.AuthViewModel
import com.example.a6thfingercontrolapp.data.AppSettingsStore
import com.example.a6thfingercontrolapp.network.PasswordResetStartOut
import com.example.a6thfingercontrolapp.utils.FeatureFlags
import com.example.a6thfingercontrolapp.utils.isNetworkErrorKey
import kotlinx.coroutines.delay

/**
 * App owned UI state for email management.
 */
internal class AccountEmailUiState {
    var emailInfo by mutableStateOf<PasswordResetStartOut?>(null)
    var emailLoading by mutableStateOf(false)
    var emailErrorKey by mutableStateOf<String?>(null)
    var cachedEmail by mutableStateOf<String?>(null)

    var emailDialogMode by mutableStateOf(EmailDialogMode.None)

    var addStep by mutableStateOf(AddStep.EnterEmail)
    var addEmail by mutableStateOf("")
    var addCode by mutableStateOf("")
    var addErrKey by mutableStateOf<String?>(null)
    var addBusy by mutableStateOf(false)

    var removeStep by mutableStateOf(RemoveStep.ChooseMethod)
    var removeCode by mutableStateOf("")
    var removeRecovery by mutableStateOf("")
    var removeErrKey by mutableStateOf<String?>(null)
    var removeBusy by mutableStateOf(false)

    var changeStep by mutableStateOf(ChangeStep.ChooseOldMethod)
    var changeOldCode by mutableStateOf("")
    var changeOldRecovery by mutableStateOf("")
    var changeNewEmail by mutableStateOf("")
    var changeNewCode by mutableStateOf("")
    var changeErrKey by mutableStateOf<String?>(null)
    var changeBusy by mutableStateOf(false)

    private var refreshTick by mutableIntStateOf(0)

    val hasEmail: Boolean
        get() = emailInfo?.has_email == true || !cachedEmail.isNullOrBlank()

    val emailShown: String?
        get() = emailInfo?.email ?: cachedEmail

    internal val refreshKey: Int
        get() = refreshTick

    fun refreshEmailInfo() {
        if (!FeatureFlags.isEmailEnabled) return
        refreshTick++
    }

    fun openAddEmail() {
        if (!FeatureFlags.isEmailEnabled) return
        emailDialogMode = EmailDialogMode.Add
        addStep = AddStep.EnterEmail
        addEmail = ""
        addCode = ""
        addErrKey = null
        addBusy = false
    }

    fun openRemoveEmail() {
        if (!FeatureFlags.isEmailEnabled) return
        emailDialogMode = EmailDialogMode.Remove
        removeStep = RemoveStep.ChooseMethod
        removeCode = ""
        removeRecovery = ""
        removeErrKey = null
        removeBusy = false
    }

    fun openChangeEmail() {
        if (!FeatureFlags.isEmailEnabled) return
        emailDialogMode = EmailDialogMode.Change
        changeStep = ChangeStep.ChooseOldMethod
        changeOldCode = ""
        changeOldRecovery = ""
        changeNewEmail = ""
        changeNewCode = ""
        changeErrKey = null
        changeBusy = false
    }
}

@Composable
internal fun rememberAccountEmailState(
    username: String?,
    authVm: AuthViewModel,
    settingsStore: AppSettingsStore
): AccountEmailUiState {
    val state = remember { AccountEmailUiState() }
    state.cachedEmail = settingsStore.getCachedEmail().collectAsState(initial = null).value

    LaunchedEffect(username, state.refreshKey, FeatureFlags.isEmailEnabled) {
        if (username.isNullOrBlank() || !FeatureFlags.isEmailEnabled) {
            state.emailInfo = null
            state.emailLoading = false
            state.emailErrorKey = null
            state.emailDialogMode = EmailDialogMode.None
            settingsStore.setCachedEmail(null)
            return@LaunchedEffect
        }

        state.emailLoading = true
        state.emailErrorKey = null
        try {
            val info = authVm.passwordResetStart(username)
            state.emailInfo = info
            if (info.has_email && !info.email.isNullOrBlank()) {
                settingsStore.setCachedEmail(info.email)
            } else {
                settingsStore.setCachedEmail(null)
            }
        } catch (e: Exception) {
            state.emailErrorKey = e.message
        } finally {
            state.emailLoading = false
        }
    }

    LaunchedEffect(state.emailErrorKey, username, FeatureFlags.isEmailEnabled) {
        if (!FeatureFlags.isEmailEnabled) return@LaunchedEffect
        if (!isNetworkErrorKey(state.emailErrorKey)) return@LaunchedEffect
        while (isNetworkErrorKey(state.emailErrorKey) && username != null) {
            delay(30_000L)
            state.refreshEmailInfo()
        }
    }

    return state
}
