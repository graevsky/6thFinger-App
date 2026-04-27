package com.example.a6thfingercontrolapp.ui.account.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.a6thfingercontrolapp.R
import com.example.a6thfingercontrolapp.ui.account.AddStep
import com.example.a6thfingercontrolapp.utils.uiErrorText

/**
 * Add email flow dialog with different steps.
 */
@Composable
internal fun EmailAddDialog(
    step: AddStep,
    email: String,
    code: String,
    errorKey: String?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onEmailChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onStartAdd: () -> Unit,
    onResend: () -> Unit,
    onBackToEmail: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.account_email_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (step == AddStep.EnterEmail) {
                    OutlinedTextField(
                        value = email,
                        onValueChange = onEmailChange,
                        singleLine = true,
                        label = { Text(stringResource(R.string.postreg_email_label)) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    uiErrorText(errorKey)?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }

                    Button(
                        enabled = !busy && email.isNotBlank(),
                        onClick = onStartAdd,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.account_email_send_code)) }
                } else {
                    Text(stringResource(R.string.postreg_email_code_hint, email))

                    OutlinedTextField(
                        value = code,
                        onValueChange = onCodeChange,
                        singleLine = true,
                        label = { Text(stringResource(R.string.postreg_email_code_label)) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    uiErrorText(errorKey)?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }

                    OutlinedButton(
                        enabled = !busy,
                        onClick = onResend,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.postreg_email_resend)) }

                    TextButton(
                        enabled = !busy,
                        onClick = onBackToEmail
                    ) { Text(stringResource(R.string.postreg_email_change)) }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && step == AddStep.EnterCode && code.isNotBlank(),
                onClick = onConfirm
            ) { Text(stringResource(R.string.account_email_confirm)) }
        },
        dismissButton = {
            TextButton(
                enabled = !busy,
                onClick = onDismiss
            ) { Text(stringResource(R.string.settings_close)) }
        }
    )
}
