package com.example.a6thfingercontrollapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.a6thfingercontrollapp.AuthViewModel
import com.example.a6thfingercontrollapp.R
import com.example.a6thfingercontrollapp.network.PasswordResetStartOut
import com.example.a6thfingercontrollapp.utils.PasswordPolicy
import com.example.a6thfingercontrollapp.utils.maskEmail
import com.example.a6thfingercontrollapp.utils.uiErrorText
import kotlinx.coroutines.launch

private enum class ResetStep {
    EnterUsername,
    ChooseMethod,
    EnterRecoveryCode,
    EnterEmail,
    EnterEmailCode,
    NewPassword,
    Done
}

@Composable
fun PasswordResetScreen(
    authVm: AuthViewModel,
    initialUsername: String,
    onBack: () -> Unit,
    onFinishedGoToLogin: (String) -> Unit,
    skipUsername: Boolean = false
) {
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf(if (skipUsername) ResetStep.ChooseMethod else ResetStep.EnterUsername) }
    var loading by remember { mutableStateOf(false) }
    var errorKey by remember { mutableStateOf<String?>(null) }

    var username by remember { mutableStateOf(initialUsername) }
    var startInfo by remember { mutableStateOf<PasswordResetStartOut?>(null) }

    var selectedEmail by remember { mutableStateOf("") }
    var emailCode by remember { mutableStateOf("") }
    var recoveryCode by remember { mutableStateOf("") }

    var resetSessionId by remember { mutableStateOf<String?>(null) }

    var newPass1 by remember { mutableStateOf("") }
    var newPass2 by remember { mutableStateOf("") }

    var pw1Visible by remember { mutableStateOf(false) }
    var pw2Visible by remember { mutableStateOf(false) }
    val rules = remember(newPass1) { PasswordPolicy.check(newPass1) }

    val error = uiErrorText(errorKey) ?: errorKey?.takeIf { it.isNotBlank() }

    fun setErr(e: Throwable) {
        errorKey = e.message ?: authVm.error.value ?: "unknown_error"
    }

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
                    Text(error ?: "", color = MaterialTheme.colorScheme.error)
                }

                when (step) {
                    ResetStep.EnterUsername -> {
                        Text(stringResource(R.string.password_reset_enter_username))
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it.trim() },
                            singleLine = true,
                            label = { Text(stringResource(R.string.auth_username)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    ResetStep.ChooseMethod -> {
                        val info = startInfo
                        Text(stringResource(R.string.password_reset_choose_method))

                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !loading,
                            onClick = { step = ResetStep.EnterRecoveryCode }
                        ) {
                            Text(stringResource(R.string.password_reset_by_recovery))
                        }

                        val emailAvailable = info?.has_email == true && !info.email.isNullOrBlank()
                        if (emailAvailable) {
                            OutlinedButton(
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !loading,
                                onClick = {
                                    selectedEmail = ""
                                    step = ResetStep.EnterEmail
                                }
                            ) {
                                Text(stringResource(R.string.password_reset_by_email))
                            }

                            Text(
                                text = stringResource(R.string.password_reset_email_available),
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.password_reset_email_not_available),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    ResetStep.EnterRecoveryCode -> {
                        Text(stringResource(R.string.password_reset_enter_recovery))
                        OutlinedTextField(
                            value = recoveryCode,
                            onValueChange = { recoveryCode = it },
                            singleLine = true,
                            label = { Text(stringResource(R.string.password_reset_recovery_code)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    ResetStep.EnterEmail -> {
                        val realEmail = startInfo?.email.orEmpty()
                        val masked =
                            if (realEmail.isNotBlank()) maskEmail(realEmail) else "********"

                        Text(stringResource(R.string.password_reset_enter_email))
                        Text(
                            text = stringResource(
                                R.string.password_reset_email_masked_hint,
                                masked
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )

                        OutlinedTextField(
                            value = selectedEmail,
                            onValueChange = { selectedEmail = it.trim() },
                            singleLine = true,
                            label = { Text(stringResource(R.string.password_reset_email)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    ResetStep.EnterEmailCode -> {
                        Text(stringResource(R.string.password_reset_enter_email_code))

                        OutlinedTextField(
                            value = emailCode,
                            onValueChange = { emailCode = it.trim() },
                            singleLine = true,
                            label = { Text(stringResource(R.string.password_reset_code)) },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !loading,
                            onClick = {
                                errorKey = null
                                loading = true
                                scope.launch {
                                    try {
                                        authVm.passwordResetEmailSend(username, selectedEmail)
                                    } catch (e: Exception) {
                                        setErr(e)
                                    } finally {
                                        loading = false
                                    }
                                }
                            }
                        ) { Text(stringResource(R.string.password_reset_resend)) }
                    }

                    ResetStep.NewPassword -> {
                        Text(stringResource(R.string.password_reset_new_password))

                        OutlinedTextField(
                            value = newPass1,
                            onValueChange = { newPass1 = it },
                            singleLine = true,
                            visualTransformation = if (pw1Visible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { pw1Visible = !pw1Visible }) {
                                    Icon(
                                        imageVector = if (pw1Visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (pw1Visible)
                                            stringResource(R.string.auth_password_hide)
                                        else
                                            stringResource(R.string.auth_password_show)
                                    )
                                }
                            },
                            label = { Text(stringResource(R.string.password_reset_password1)) },
                            modifier = Modifier.fillMaxWidth()
                        )

                        PasswordRulesHint(rules = rules)

                        OutlinedTextField(
                            value = newPass2,
                            onValueChange = { newPass2 = it },
                            singleLine = true,
                            visualTransformation = if (pw2Visible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { pw2Visible = !pw2Visible }) {
                                    Icon(
                                        imageVector = if (pw2Visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (pw2Visible)
                                            stringResource(R.string.auth_password_hide)
                                        else
                                            stringResource(R.string.auth_password_show)
                                    )
                                }
                            },
                            label = { Text(stringResource(R.string.password_reset_password2)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    ResetStep.Done -> Text(stringResource(R.string.password_reset_done))
                }

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
                            ResetStep.ChooseMethod -> if (skipUsername) onBack() else step =
                                ResetStep.EnterUsername

                            ResetStep.EnterRecoveryCode -> step = ResetStep.ChooseMethod
                            ResetStep.EnterEmail -> step = ResetStep.ChooseMethod
                            ResetStep.EnterEmailCode -> step = ResetStep.EnterEmail
                            ResetStep.NewPassword -> step = ResetStep.ChooseMethod
                            ResetStep.Done -> onFinishedGoToLogin(username.trim().lowercase())
                        }
                    }
                ) { Text(stringResource(R.string.auth_back)) }

                Button(
                    enabled = !loading && when (step) {
                        ResetStep.EnterUsername -> username.isNotBlank()
                        ResetStep.ChooseMethod -> true
                        ResetStep.EnterRecoveryCode -> recoveryCode.isNotBlank()
                        ResetStep.EnterEmail -> selectedEmail.isNotBlank()
                        ResetStep.EnterEmailCode -> emailCode.isNotBlank()
                        ResetStep.NewPassword -> newPass1.isNotBlank() && newPass2.isNotBlank() && rules.ok
                        ResetStep.Done -> true
                    },
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

                            ResetStep.ChooseMethod,
                            ResetStep.Done -> onFinishedGoToLogin(username.trim().lowercase())
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