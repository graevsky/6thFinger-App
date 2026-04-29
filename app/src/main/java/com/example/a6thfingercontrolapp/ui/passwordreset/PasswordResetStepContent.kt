package com.example.a6thfingercontrolapp.ui.passwordreset

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.example.a6thfingercontrolapp.R
import com.example.a6thfingercontrolapp.network.PasswordResetStartOut
import com.example.a6thfingercontrolapp.ui.PasswordRulesHint
import com.example.a6thfingercontrolapp.utils.PasswordPolicy
import com.example.a6thfingercontrolapp.utils.maskEmail

/** Visible content block for the currently active password reset step. */
@Composable
fun PasswordResetStepContent(
    step: ResetStep,
    loading: Boolean,
    emailEnabled: Boolean,
    username: String,
    onUsernameChange: (String) -> Unit,
    startInfo: PasswordResetStartOut?,
    onChooseRecoveryMethod: () -> Unit,
    onChooseEmailMethod: () -> Unit,
    selectedEmail: String,
    onSelectedEmailChange: (String) -> Unit,
    recoveryCode: String,
    onRecoveryCodeChange: (String) -> Unit,
    emailCode: String,
    onEmailCodeChange: (String) -> Unit,
    onResendEmailCode: () -> Unit,
    emailResendCooldownSeconds: Int,
    newPass1: String,
    onNewPass1Change: (String) -> Unit,
    newPass2: String,
    onNewPass2Change: (String) -> Unit,
    pw1Visible: Boolean,
    onPw1VisibleChange: (Boolean) -> Unit,
    pw2Visible: Boolean,
    onPw2VisibleChange: (Boolean) -> Unit,
    rules: PasswordPolicy.Result
) {
    when (step) {
        ResetStep.EnterUsername -> EnterUsernameStep(
            username = username,
            onUsernameChange = onUsernameChange
        )

        ResetStep.ChooseMethod -> ChooseMethodStep(
            loading = loading,
            emailEnabled = emailEnabled,
            startInfo = startInfo,
            onChooseRecoveryMethod = onChooseRecoveryMethod,
            onChooseEmailMethod = onChooseEmailMethod
        )

        ResetStep.EnterRecoveryCode -> EnterRecoveryCodeStep(
            recoveryCode = recoveryCode,
            onRecoveryCodeChange = onRecoveryCodeChange
        )

        ResetStep.EnterEmail -> EnterEmailStep(
            startInfo = startInfo,
            selectedEmail = selectedEmail,
            onSelectedEmailChange = onSelectedEmailChange
        )

        ResetStep.EnterEmailCode -> EnterEmailCodeStep(
            loading = loading,
            emailCode = emailCode,
            onEmailCodeChange = onEmailCodeChange,
            onResendEmailCode = onResendEmailCode,
            resendCooldownSeconds = emailResendCooldownSeconds
        )

        ResetStep.NewPassword -> NewPasswordStep(
            newPass1 = newPass1,
            onNewPass1Change = onNewPass1Change,
            newPass2 = newPass2,
            onNewPass2Change = onNewPass2Change,
            pw1Visible = pw1Visible,
            onPw1VisibleChange = onPw1VisibleChange,
            pw2Visible = pw2Visible,
            onPw2VisibleChange = onPw2VisibleChange,
            rules = rules
        )

        ResetStep.Done -> Text(stringResource(R.string.password_reset_done))
    }
}

@Composable
private fun EnterUsernameStep(
    username: String,
    onUsernameChange: (String) -> Unit
) {
    Text(stringResource(R.string.password_reset_enter_username))
    OutlinedTextField(
        value = username,
        onValueChange = { onUsernameChange(it.trim()) },
        singleLine = true,
        label = { Text(stringResource(R.string.auth_username)) },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ChooseMethodStep(
    loading: Boolean,
    emailEnabled: Boolean,
    startInfo: PasswordResetStartOut?,
    onChooseRecoveryMethod: () -> Unit,
    onChooseEmailMethod: () -> Unit
) {
    Text(stringResource(R.string.password_reset_choose_method))

    OutlinedButton(
        modifier = Modifier.fillMaxWidth(),
        enabled = !loading,
        onClick = onChooseRecoveryMethod
    ) {
        Text(stringResource(R.string.password_reset_by_recovery))
    }

    val emailAvailable = emailEnabled &&
            startInfo?.has_email == true &&
            !startInfo.email.isNullOrBlank()
    if (emailAvailable) {
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading,
            onClick = onChooseEmailMethod
        ) {
            Text(stringResource(R.string.password_reset_by_email))
        }

        Text(
            text = stringResource(R.string.password_reset_email_available),
            style = MaterialTheme.typography.bodySmall
        )
    } else if (emailEnabled) {
        Text(
            text = stringResource(R.string.password_reset_email_not_available),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun EnterRecoveryCodeStep(
    recoveryCode: String,
    onRecoveryCodeChange: (String) -> Unit
) {
    val hasInvalidFormat = recoveryCode.isNotBlank() && !isValidRecoveryCodeFormat(recoveryCode)

    Text(stringResource(R.string.password_reset_enter_recovery))
    OutlinedTextField(
        value = recoveryCode,
        onValueChange = { onRecoveryCodeChange(sanitizeRecoveryCodeInput(it)) },
        singleLine = true,
        isError = hasInvalidFormat,
        label = { Text(stringResource(R.string.password_reset_recovery_code)) },
        modifier = Modifier.fillMaxWidth()
    )

    if (hasInvalidFormat) {
        Text(
            text = stringResource(R.string.password_reset_recovery_code_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun EnterEmailStep(
    startInfo: PasswordResetStartOut?,
    selectedEmail: String,
    onSelectedEmailChange: (String) -> Unit
) {
    val realEmail = startInfo?.email.orEmpty()
    val masked = if (realEmail.isNotBlank()) maskEmail(realEmail) else "********"

    Text(stringResource(R.string.password_reset_enter_email))
    Text(
        text = stringResource(R.string.password_reset_email_masked_hint, masked),
        style = MaterialTheme.typography.bodySmall
    )

    OutlinedTextField(
        value = selectedEmail,
        onValueChange = { onSelectedEmailChange(it.trim()) },
        singleLine = true,
        label = { Text(stringResource(R.string.password_reset_email)) },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun EnterEmailCodeStep(
    loading: Boolean,
    emailCode: String,
    onEmailCodeChange: (String) -> Unit,
    onResendEmailCode: () -> Unit,
    resendCooldownSeconds: Int
) {
    Text(stringResource(R.string.password_reset_enter_email_code))

    OutlinedTextField(
        value = emailCode,
        onValueChange = { onEmailCodeChange(it.trim()) },
        singleLine = true,
        label = { Text(stringResource(R.string.password_reset_code)) },
        modifier = Modifier.fillMaxWidth()
    )

    OutlinedButton(
        modifier = Modifier.fillMaxWidth(),
        enabled = !loading && resendCooldownSeconds == 0,
        onClick = onResendEmailCode
    ) {
        Text(
            if (resendCooldownSeconds > 0) {
                stringResource(R.string.code_resend_wait, resendCooldownSeconds)
            } else {
                stringResource(R.string.password_reset_resend)
            }
        )
    }
}

@Composable
private fun NewPasswordStep(
    newPass1: String,
    onNewPass1Change: (String) -> Unit,
    newPass2: String,
    onNewPass2Change: (String) -> Unit,
    pw1Visible: Boolean,
    onPw1VisibleChange: (Boolean) -> Unit,
    pw2Visible: Boolean,
    onPw2VisibleChange: (Boolean) -> Unit,
    rules: PasswordPolicy.Result
) {
    Text(stringResource(R.string.password_reset_new_password))

    OutlinedTextField(
        value = newPass1,
        onValueChange = onNewPass1Change,
        singleLine = true,
        visualTransformation = if (pw1Visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { onPw1VisibleChange(!pw1Visible) }) {
                Icon(
                    imageVector = if (pw1Visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (pw1Visible) {
                        stringResource(R.string.auth_password_hide)
                    } else {
                        stringResource(R.string.auth_password_show)
                    }
                )
            }
        },
        label = { Text(stringResource(R.string.password_reset_password1)) },
        modifier = Modifier.fillMaxWidth()
    )

    PasswordRulesHint(rules = rules)

    OutlinedTextField(
        value = newPass2,
        onValueChange = onNewPass2Change,
        singleLine = true,
        visualTransformation = if (pw2Visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { onPw2VisibleChange(!pw2Visible) }) {
                Icon(
                    imageVector = if (pw2Visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (pw2Visible) {
                        stringResource(R.string.auth_password_hide)
                    } else {
                        stringResource(R.string.auth_password_show)
                    }
                )
            }
        },
        label = { Text(stringResource(R.string.password_reset_password2)) },
        modifier = Modifier.fillMaxWidth()
    )
}
