package com.example.a6thfingercontrolapp.ui.account.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
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
import com.example.a6thfingercontrolapp.ui.account.RemoveStep
import com.example.a6thfingercontrolapp.utils.uiErrorText

/**
 * Remove email flow dialog with different steps.
 */
@Composable
internal fun EmailRemoveDialog(
    step: RemoveStep,
    code: String,
    recoveryCode: String,
    errorKey: String?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onCodeChange: (String) -> Unit,
    onRecoveryCodeChange: (String) -> Unit,
    onChooseEmailMethod: () -> Unit,
    onChooseRecoveryMethod: () -> Unit,
    onResend: () -> Unit,
    onBack: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.account_email_remove)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.account_email_remove_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )

                when (step) {
                    RemoveStep.ChooseMethod -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                enabled = !busy,
                                onClick = onChooseEmailMethod,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(stringResource(R.string.account_email_method_email)) }

                            OutlinedButton(
                                enabled = !busy,
                                onClick = onChooseRecoveryMethod,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(stringResource(R.string.account_email_method_recovery)) }
                        }
                    }

                    RemoveStep.EnterEmailCode -> {
                        OutlinedTextField(
                            value = code,
                            onValueChange = onCodeChange,
                            singleLine = true,
                            label = { Text(stringResource(R.string.postreg_email_code_label)) },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedButton(
                            enabled = !busy,
                            onClick = onResend,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.postreg_email_resend)) }

                        TextButton(
                            enabled = !busy,
                            onClick = onBack
                        ) { Text(stringResource(R.string.auth_back)) }
                    }

                    RemoveStep.EnterRecoveryCode -> {
                        OutlinedTextField(
                            value = recoveryCode,
                            onValueChange = onRecoveryCodeChange,
                            singleLine = true,
                            label = { Text(stringResource(R.string.account_email_recovery_code)) },
                            modifier = Modifier.fillMaxWidth()
                        )

                        TextButton(
                            enabled = !busy,
                            onClick = onBack
                        ) { Text(stringResource(R.string.auth_back)) }
                    }
                }

                uiErrorText(errorKey)?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            val canConfirm =
                !busy && (
                        (step == RemoveStep.EnterEmailCode && code.isNotBlank()) ||
                                (step == RemoveStep.EnterRecoveryCode && recoveryCode.isNotBlank())
                        )

            TextButton(
                enabled = canConfirm,
                onClick = onConfirm
            ) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = {
            TextButton(
                enabled = !busy,
                onClick = onDismiss
            ) { Text(stringResource(R.string.settings_close)) }
        }
    )
}
