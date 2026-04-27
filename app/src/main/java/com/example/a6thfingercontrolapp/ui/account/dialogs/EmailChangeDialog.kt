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
import com.example.a6thfingercontrolapp.ui.account.ChangeStep
import com.example.a6thfingercontrolapp.utils.uiErrorText

/**
 * Change email flow dialog with different steps.
 */
@Composable
internal fun EmailChangeDialog(
    step: ChangeStep,
    oldCode: String,
    oldRecoveryCode: String,
    newEmail: String,
    newCode: String,
    errorKey: String?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onOldCodeChange: (String) -> Unit,
    onOldRecoveryCodeChange: (String) -> Unit,
    onNewEmailChange: (String) -> Unit,
    onNewCodeChange: (String) -> Unit,
    onChooseOldEmailMethod: () -> Unit,
    onChooseOldRecoveryMethod: () -> Unit,
    onResendOldCode: () -> Unit,
    onResendNewCode: () -> Unit,
    onBackToOldMethod: () -> Unit,
    onBackToNewEmail: () -> Unit,
    onStartNewEmail: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.account_email_change)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (step) {
                    ChangeStep.ChooseOldMethod -> {
                        Text(stringResource(R.string.account_email_change_step_old))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                enabled = !busy,
                                onClick = onChooseOldEmailMethod,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(stringResource(R.string.account_email_method_email)) }

                            OutlinedButton(
                                enabled = !busy,
                                onClick = onChooseOldRecoveryMethod,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(stringResource(R.string.account_email_method_recovery)) }
                        }
                    }

                    ChangeStep.EnterOldEmailCode -> {
                        OutlinedTextField(
                            value = oldCode,
                            onValueChange = onOldCodeChange,
                            singleLine = true,
                            label = { Text(stringResource(R.string.postreg_email_code_label)) },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedButton(
                            enabled = !busy,
                            onClick = onResendOldCode,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.postreg_email_resend)) }

                        TextButton(
                            enabled = !busy,
                            onClick = onBackToOldMethod
                        ) { Text(stringResource(R.string.auth_back)) }
                    }

                    ChangeStep.EnterOldRecoveryCode -> {
                        OutlinedTextField(
                            value = oldRecoveryCode,
                            onValueChange = onOldRecoveryCodeChange,
                            singleLine = true,
                            label = { Text(stringResource(R.string.account_email_recovery_code)) },
                            modifier = Modifier.fillMaxWidth()
                        )

                        TextButton(
                            enabled = !busy,
                            onClick = onBackToOldMethod
                        ) { Text(stringResource(R.string.auth_back)) }
                    }

                    ChangeStep.EnterNewEmail -> {
                        Text(stringResource(R.string.account_email_change_step_new))

                        OutlinedTextField(
                            value = newEmail,
                            onValueChange = onNewEmailChange,
                            singleLine = true,
                            label = { Text(stringResource(R.string.postreg_email_label)) },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Button(
                            enabled = !busy && newEmail.isNotBlank(),
                            onClick = onStartNewEmail,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.account_email_send_code)) }
                    }

                    ChangeStep.EnterNewEmailCode -> {
                        Text(stringResource(R.string.postreg_email_code_hint, newEmail))

                        OutlinedTextField(
                            value = newCode,
                            onValueChange = onNewCodeChange,
                            singleLine = true,
                            label = { Text(stringResource(R.string.postreg_email_code_label)) },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedButton(
                            enabled = !busy,
                            onClick = onResendNewCode,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.postreg_email_resend)) }

                        TextButton(
                            enabled = !busy,
                            onClick = onBackToNewEmail
                        ) { Text(stringResource(R.string.postreg_email_change)) }
                    }
                }

                uiErrorText(errorKey)?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            val canConfirm =
                !busy && when (step) {
                    ChangeStep.EnterOldEmailCode -> oldCode.isNotBlank()
                    ChangeStep.EnterOldRecoveryCode -> oldRecoveryCode.isNotBlank()
                    ChangeStep.EnterNewEmailCode -> newCode.isNotBlank()
                    else -> false
                }

            TextButton(
                enabled = canConfirm,
                onClick = onConfirm
            ) {
                Text(
                    when (step) {
                        ChangeStep.EnterNewEmailCode -> stringResource(R.string.account_email_confirm)
                        else -> stringResource(R.string.confirm)
                    }
                )
            }
        },
        dismissButton = {
            TextButton(
                enabled = !busy,
                onClick = onDismiss
            ) { Text(stringResource(R.string.settings_close)) }
        }
    )
}
