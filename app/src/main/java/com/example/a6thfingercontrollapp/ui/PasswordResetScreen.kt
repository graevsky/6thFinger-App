package com.example.a6thfingercontrollapp.ui

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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.a6thfingercontrollapp.AuthViewModel
import com.example.a6thfingercontrollapp.R
import com.example.a6thfingercontrollapp.network.PasswordResetStartOut
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
    onFinishedGoToLogin: (String) -> Unit
) {
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf(ResetStep.EnterUsername) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    var username by remember { mutableStateOf(initialUsername) }
    var startInfo by remember { mutableStateOf<PasswordResetStartOut?>(null) }

    var selectedEmail by remember { mutableStateOf("") }
    var emailCode by remember { mutableStateOf("") }

    var recoveryCode by remember { mutableStateOf("") }

    var resetSessionId by remember { mutableStateOf<String?>(null) }

    var newPass1 by remember { mutableStateOf("") }
    var newPass2 by remember { mutableStateOf("") }

    val errPasswordsMismatch = stringResource(R.string.password_reset_passwords_mismatch)
    val errNoResetSession = stringResource(R.string.password_reset_no_reset_session)

    fun setErr(e: Throwable) {
        error = e.message ?: authVm.error.value ?: "Error"
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
                            onClick = { step = ResetStep.EnterRecoveryCode }
                        ) {
                            Text(stringResource(R.string.password_reset_by_recovery))
                        }

                        if (info?.has_email == true && !info.email.isNullOrBlank()) {
                            OutlinedButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    selectedEmail = info.email ?: ""
                                    step = ResetStep.EnterEmail
                                }
                            ) {
                                Text(stringResource(R.string.password_reset_by_email))
                            }

                            Text(
                                text = stringResource(
                                    R.string.password_reset_email_hint,
                                    info.email ?: ""
                                ),
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
                        Text(stringResource(R.string.password_reset_enter_email))

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
                                error = null
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
                        ) {
                            Text(stringResource(R.string.password_reset_resend))
                        }
                    }

                    ResetStep.NewPassword -> {
                        Text(stringResource(R.string.password_reset_new_password))

                        OutlinedTextField(
                            value = newPass1,
                            onValueChange = { newPass1 = it },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            label = { Text(stringResource(R.string.password_reset_password1)) },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = newPass2,
                            onValueChange = { newPass2 = it },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            label = { Text(stringResource(R.string.password_reset_password2)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    ResetStep.Done -> {
                        Text(stringResource(R.string.password_reset_done))
                    }
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
                        error = null
                        when (step) {
                            ResetStep.EnterUsername -> onBack()
                            ResetStep.ChooseMethod -> step = ResetStep.EnterUsername
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

                Button(
                    enabled = !loading && when (step) {
                        ResetStep.EnterUsername -> username.isNotBlank()
                        ResetStep.ChooseMethod -> true
                        ResetStep.EnterRecoveryCode -> recoveryCode.isNotBlank()
                        ResetStep.EnterEmail -> selectedEmail.isNotBlank()
                        ResetStep.EnterEmailCode -> emailCode.isNotBlank()
                        ResetStep.NewPassword -> newPass1.isNotBlank() && newPass2.isNotBlank()
                        ResetStep.Done -> true
                    },
                    onClick = {
                        error = null
                        when (step) {
                            ResetStep.EnterUsername -> {
                                loading = true
                                scope.launch {
                                    try {
                                        val info = authVm.passwordResetStart(username)
                                        startInfo = info
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
                                        val sid = authVm.passwordResetRecoveryVerify(
                                            username,
                                            recoveryCode
                                        )
                                        resetSessionId = sid
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
                                        val sid = authVm.passwordResetEmailVerify(
                                            username,
                                            selectedEmail,
                                            emailCode
                                        )
                                        resetSessionId = sid
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
                                    error = errPasswordsMismatch
                                    return@Button
                                }

                                val sid = resetSessionId
                                if (sid.isNullOrBlank()) {
                                    error = errNoResetSession
                                    return@Button
                                }

                                loading = true
                                scope.launch {
                                    try {
                                        authVm.passwordResetFinish(sid, username, newPass1)
                                        step = ResetStep.Done
                                    } catch (e: Exception) {
                                        setErr(e)
                                    } finally {
                                        loading = false
                                    }
                                }
                            }

                            ResetStep.ChooseMethod,
                            ResetStep.Done -> {
                                onFinishedGoToLogin(username.trim().lowercase())
                            }
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