package com.example.a6thfingercontrollapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.a6thfingercontrollapp.R

@Composable
fun SettingsDialog(
    currentLang: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,

    isLoggedIn: Boolean = false,
    emailLine: String? = null,
    emailErrorLine: String? = null,
    hasEmail: Boolean = false,
    onAddEmail: (() -> Unit)? = null,
    onChangeEmail: (() -> Unit)? = null,
    onRemoveEmail: (() -> Unit)? = null,
    onChangePassword: (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current

    AlertDialog(
        onDismissRequest = {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.VirtualKey)
            onDismiss()
        },
        title = { Text(stringResource(R.string.settings_app)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("${stringResource(R.string.settings_language)}:")

                LanguageOptionRow(
                    title = stringResource(R.string.settings_russian),
                    selected = currentLang == "ru",
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm)
                        onSelect("ru")
                    }
                )

                LanguageOptionRow(
                    title = stringResource(R.string.settings_english),
                    selected = currentLang == "en",
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm)
                        onSelect("en")
                    }
                )

                if (isLoggedIn) {
                    Divider(modifier = Modifier.padding(top = 4.dp))
                    Text(
                        stringResource(R.string.settings_account),
                        style = MaterialTheme.typography.titleSmall
                    )

                    if (!emailLine.isNullOrBlank()) {
                        Text(emailLine, style = MaterialTheme.typography.bodySmall)
                    }
                    if (!emailErrorLine.isNullOrBlank()) {
                        Text(
                            emailErrorLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    if (!hasEmail) {
                        TextButton(
                            onClick = { onAddEmail?.invoke() },
                            enabled = onAddEmail != null
                        ) { Text(stringResource(R.string.account_email_add)) }
                    } else {
                        TextButton(
                            onClick = { onChangeEmail?.invoke() },
                            enabled = onChangeEmail != null
                        ) { Text(stringResource(R.string.account_email_change)) }

                        TextButton(
                            onClick = { onRemoveEmail?.invoke() },
                            enabled = onRemoveEmail != null
                        ) { Text(stringResource(R.string.account_email_remove)) }
                    }

                    TextButton(
                        onClick = { onChangePassword?.invoke() },
                        enabled = onChangePassword != null
                    ) { Text(stringResource(R.string.settings_password_change)) }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.VirtualKey)
                    onDismiss()
                }
            ) { Text(stringResource(R.string.settings_close)) }
        }
    )
}

@Composable
private fun LanguageOptionRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge)
    }
}