package com.example.a6thfingercontrollapp.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.a6thfingercontrollapp.R

/** External link item displayed inside the settings dialog. */
data class SettingsLink(
    val title: String,
    val url: String
)

/**
 * Shared application settings dialog.
 *
 * It is used both on the start screen and inside the account screen. Optional
 * account callbacks enable email/password controls only for logged-in users.
 */
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
    onChangePassword: (() -> Unit)? = null,

    links: List<SettingsLink> = emptyList()
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    /** Opens project documentation/repository links in an external app. */
    fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
        } catch (_: Exception) {
        }
    }

    val linksHeader = if (currentLang == "ru") "Ссылки" else "Links"

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

                if (links.isNotEmpty()) {
                    Divider(modifier = Modifier.padding(top = 4.dp))
                    Text(
                        linksHeader,
                        style = MaterialTheme.typography.titleSmall
                    )

                    links.forEach { link ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.ContextClick)
                                    openUrl(link.url)
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = link.title,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

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

/** Single radio row used to choose the application language. */
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