package com.example.a6thfingercontrolapp.ui.auth

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.a6thfingercontrolapp.R
import com.example.a6thfingercontrolapp.utils.uiErrorText

/**
 * Post-registration step for email integration.
 * This step is optional.
 */
@Composable
fun PostRegisterAddEmailScreen(
    initialEmail: String,
    loading: Boolean,
    errorKey: String?,
    hasPendingVerification: Boolean,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onStartAdd: (String) -> Unit
) {
    var email by rememberSaveable(initialEmail) { mutableStateOf(initialEmail) }
    val errText = uiErrorText(errorKey) ?: errorKey?.takeIf { it.isNotBlank() }
    val continueVerification =
        hasPendingVerification && email.equals(initialEmail, ignoreCase = true)

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
                    text = stringResource(R.string.postreg_email_title),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = stringResource(R.string.postreg_email_subtitle),
                    style = MaterialTheme.typography.bodyMedium
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it.trim() },
                    singleLine = true,
                    label = { Text(stringResource(R.string.postreg_email_label)) },
                    modifier = Modifier.fillMaxWidth()
                )

                if (!errText.isNullOrBlank()) {
                    Text(errText, color = MaterialTheme.colorScheme.error)
                }

                Spacer(Modifier.height(6.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(enabled = !loading, onClick = onBack) {
                    Text(stringResource(R.string.auth_back))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = !loading, onClick = onSkip) {
                        Text(stringResource(R.string.postreg_email_skip))
                    }
                    Button(
                        enabled = !loading && email.isNotBlank(),
                        onClick = { onStartAdd(email) }
                    ) {
                        Text(
                            stringResource(
                                if (continueVerification) {
                                    R.string.postreg_email_continue_verification
                                } else {
                                    R.string.postreg_email_add
                                }
                            )
                        )
                    }
                }
            }
        }
    }
}

/**
 * Verification step for email integration.
 */
@Composable
fun PostRegisterVerifyEmailScreen(
    email: String,
    loading: Boolean,
    errorKey: String?,
    code: String,
    resendCooldownSeconds: Int,
    onCodeChange: (String) -> Unit,
    onBackChangeEmail: () -> Unit,
    onResend: () -> Unit,
    onConfirm: () -> Unit
) {
    val errText = uiErrorText(errorKey) ?: errorKey?.takeIf { it.isNotBlank() }

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
                    text = stringResource(R.string.postreg_email_code_title),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = stringResource(R.string.postreg_email_code_hint, email),
                    style = MaterialTheme.typography.bodyMedium
                )

                OutlinedTextField(
                    value = code,
                    onValueChange = { onCodeChange(it.trim()) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.postreg_email_code_label)) },
                    modifier = Modifier.fillMaxWidth()
                )

                if (!errText.isNullOrBlank()) {
                    Text(errText, color = MaterialTheme.colorScheme.error)
                }

                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loading && resendCooldownSeconds == 0,
                    onClick = onResend
                ) {
                    Text(
                        if (resendCooldownSeconds > 0) {
                            stringResource(R.string.code_resend_wait, resendCooldownSeconds)
                        } else {
                            stringResource(R.string.postreg_email_resend)
                        }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(enabled = !loading, onClick = onBackChangeEmail) {
                    Text(stringResource(R.string.postreg_email_change))
                }

                Button(
                    enabled = !loading && code.isNotBlank(),
                    onClick = onConfirm
                ) {
                    Text(stringResource(R.string.postreg_email_confirm))
                }
            }
        }
    }
}
