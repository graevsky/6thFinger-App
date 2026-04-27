package com.example.a6thfingercontrolapp.ui.passwordreset

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.a6thfingercontrolapp.R
import com.example.a6thfingercontrolapp.auth.AuthViewModel
import com.example.a6thfingercontrolapp.network.PasswordResetStartOut
import com.example.a6thfingercontrolapp.ui.common.DEFAULT_CODE_RESEND_COOLDOWN_SECONDS
import com.example.a6thfingercontrolapp.ui.common.nextCooldownDeadline
import com.example.a6thfingercontrolapp.ui.common.rememberCooldownRemainingSeconds
import com.example.a6thfingercontrolapp.utils.PasswordPolicy
import com.example.a6thfingercontrolapp.utils.uiErrorText
import kotlinx.coroutines.launch

/**
 * Multi-step password reset screen.
 *
 * It supports both public forgotten-password flow and logged-in password change
 * flow. Verification can be done through recovery code or email code.
 */
@Composable
fun PasswordResetScreen(
    authVm: AuthViewModel,
    initialUsername: String,
    onBack: () -> Unit,
    onFinishedGoToLogin: (String) -> Unit,
    skipUsername: Boolean = false
) {
    val scope = rememberCoroutineScope()

    var step by rememberSaveable {
        mutableStateOf(if (skipUsername) ResetStep.ChooseMethod else ResetStep.EnterUsername)
    }
    var loading by remember { mutableStateOf(false) }
    var errorKey by rememberSaveable { mutableStateOf<String?>(null) }

    var username by rememberSaveable(initialUsername) { mutableStateOf(initialUsername) }
    var startInfo by remember { mutableStateOf<PasswordResetStartOut?>(null) }

    var selectedEmail by rememberSaveable { mutableStateOf("") }
    var emailCode by rememberSaveable { mutableStateOf("") }
    var recoveryCode by rememberSaveable { mutableStateOf("") }
    var emailResendCooldownUntilMs by rememberSaveable { mutableStateOf(0L) }

    var resetSessionId by rememberSaveable { mutableStateOf<String?>(null) }

    var newPass1 by rememberSaveable { mutableStateOf("") }
    var newPass2 by rememberSaveable { mutableStateOf("") }

    var pw1Visible by rememberSaveable { mutableStateOf(false) }
    var pw2Visible by rememberSaveable { mutableStateOf(false) }
    val rules = remember(newPass1) { PasswordPolicy.check(newPass1) }
    val emailResendCooldownSeconds = rememberCooldownRemainingSeconds(emailResendCooldownUntilMs)

    val error = uiErrorText(errorKey) ?: errorKey?.takeIf { it.isNotBlank() }

    /** Stores a backend/localized error key for the currently visible step. */
    fun setErr(error: Throwable) {
        errorKey = error.message ?: authVm.error.value ?: "unknown_error"
    }

    // In change-password mode the username is already known, so start immediately.
    LaunchedEffect(skipUsername, username) {
        if (!skipUsername) return@LaunchedEffect
        if (username.isBlank()) return@LaunchedEffect
        if (startInfo != null) return@LaunchedEffect

        loading = true
        errorKey = null
        try {
            startInfo = authVm.passwordResetStart(username)
        } catch (e: Exception) {
            setErr(e)
        } finally {
            loading = false
        }
    }

    Scaffold { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.password_reset_title),
                    style = MaterialTheme.typography.titleLarge
                )

                if (!error.isNullOrBlank()) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                }

                PasswordResetStepContent(
                    step = step,
                    loading = loading,
                    username = username,
                    onUsernameChange = { username = it },
                    startInfo = startInfo,
                    onChooseRecoveryMethod = { step = ResetStep.EnterRecoveryCode },
                    onChooseEmailMethod = {
                        selectedEmail = ""
                        step = ResetStep.EnterEmail
                    },
                    selectedEmail = selectedEmail,
                    onSelectedEmailChange = { selectedEmail = it },
                    recoveryCode = recoveryCode,
                    onRecoveryCodeChange = { recoveryCode = it },
                    emailCode = emailCode,
                    onEmailCodeChange = { emailCode = it },
                    onResendEmailCode = {
                        errorKey = null
                        loading = true
                        scope.launch {
                            try {
                                authVm.passwordResetEmailSend(username, selectedEmail)
                                emailResendCooldownUntilMs =
                                    nextCooldownDeadline(DEFAULT_CODE_RESEND_COOLDOWN_SECONDS)
                            } catch (e: Exception) {
                                setErr(e)
                            } finally {
                                loading = false
                            }
                        }
                    },
                    emailResendCooldownSeconds = emailResendCooldownSeconds,
                    newPass1 = newPass1,
                    onNewPass1Change = { newPass1 = it },
                    newPass2 = newPass2,
                    onNewPass2Change = { newPass2 = it },
                    pw1Visible = pw1Visible,
                    onPw1VisibleChange = { pw1Visible = it },
                    pw2Visible = pw2Visible,
                    onPw2VisibleChange = { pw2Visible = it },
                    rules = rules
                )

                Spacer(Modifier.height(6.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    enabled = !loading,
                    onClick = {
                        errorKey = null
                        when (step) {
                            ResetStep.EnterUsername -> onBack()
                            ResetStep.ChooseMethod ->
                                if (skipUsername) onBack() else step = ResetStep.EnterUsername

                            ResetStep.EnterRecoveryCode -> step = ResetStep.ChooseMethod
                            ResetStep.EnterEmail -> step = ResetStep.ChooseMethod
                            ResetStep.EnterEmailCode -> step = ResetStep.EnterEmail
                            ResetStep.NewPassword -> step = ResetStep.ChooseMethod
                            ResetStep.Done -> onFinishedGoToLogin(username.trim().lowercase())
                        }
                    }
                ) {
                    Text(stringResource(R.string.auth_back))
                }

                if (step != ResetStep.ChooseMethod) {
                    Button(
                        enabled = isPasswordResetNextEnabled(
                            step = step,
                            loading = loading,
                            username = username,
                            recoveryCode = recoveryCode,
                            selectedEmail = selectedEmail,
                            emailCode = emailCode,
                            newPass1 = newPass1,
                            newPass2 = newPass2,
                            rulesOk = rules.ok
                        ),
                        onClick = {
                            errorKey = null
                            when (step) {
                                ResetStep.EnterUsername -> {
                                    loading = true
                                    scope.launch {
                                        try {
                                            startInfo = authVm.passwordResetStart(username)
                                            step = ResetStep.ChooseMethod
                                        } catch (e: Exception) {
                                            setErr(e)
                                        } finally {
                                            loading = false
                                        }
                                    }
                                }

                                ResetStep.EnterRecoveryCode -> {
                                    loading = true
                                    scope.launch {
                                        try {
                                            resetSessionId = authVm.passwordResetRecoveryVerify(
                                                username,
                                                recoveryCode
                                            )
                                            step = ResetStep.NewPassword
                                        } catch (e: Exception) {
                                            setErr(e)
                                        } finally {
                                            loading = false
                                        }
                                    }
                                }

                                ResetStep.EnterEmail -> {
                                    loading = true
                                    scope.launch {
                                        try {
                                            authVm.passwordResetEmailSend(username, selectedEmail)
                                            emailCode = ""
                                            emailResendCooldownUntilMs =
                                                nextCooldownDeadline(
                                                    DEFAULT_CODE_RESEND_COOLDOWN_SECONDS
                                                )
                                            step = ResetStep.EnterEmailCode
                                        } catch (e: Exception) {
                                            setErr(e)
                                        } finally {
                                            loading = false
                                        }
                                    }
                                }

                                ResetStep.EnterEmailCode -> {
                                    loading = true
                                    scope.launch {
                                        try {
                                            resetSessionId = authVm.passwordResetEmailVerify(
                                                username,
                                                selectedEmail,
                                                emailCode
                                            )
                                            step = ResetStep.NewPassword
                                        } catch (e: Exception) {
                                            setErr(e)
                                        } finally {
                                            loading = false
                                        }
                                    }
                                }

                                ResetStep.NewPassword -> {
                                    if (newPass1 != newPass2) {
                                        errorKey = "passwords_mismatch"
                                        return@Button
                                    }
                                    if (!rules.ok) {
                                        errorKey = "password_rules_invalid"
                                        return@Button
                                    }
                                    if (resetSessionId.isNullOrBlank()) {
                                        errorKey = "no_reset_session"
                                        return@Button
                                    }

                                    loading = true
                                    scope.launch {
                                        try {
                                            authVm.passwordResetFinish(
                                                resetSessionId!!,
                                                username,
                                                newPass1
                                            )
                                            step = ResetStep.Done
                                        } catch (e: Exception) {
                                            setErr(e)
                                        } finally {
                                            loading = false
                                        }
                                    }
                                }

                                ResetStep.Done -> onFinishedGoToLogin(username.trim().lowercase())
                                ResetStep.ChooseMethod -> Unit
                            }
                        }
                    ) {
                        Text(
                            when (step) {
                                ResetStep.Done -> stringResource(R.string.auth_login)
                                else -> stringResource(R.string.password_reset_next)
                            }
                        )
                    }
                }
            }
        }
    }
}
