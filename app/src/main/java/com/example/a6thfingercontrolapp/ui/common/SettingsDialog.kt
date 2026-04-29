package com.example.a6thfingercontrolapp.ui.common

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.a6thfingercontrolapp.R
import com.example.a6thfingercontrolapp.data.APP_THEME_DARK
import com.example.a6thfingercontrolapp.data.APP_THEME_LIGHT
import com.example.a6thfingercontrolapp.data.APP_THEME_SYSTEM
import com.example.a6thfingercontrolapp.data.AppSettingsStore
import kotlinx.coroutines.launch

/** External link item display. */
data class SettingsLink(
    val title: String,
    val url: String
)

/**
 * Shared application settings dialog.
 *
 * It is used both on the start screen and inside the account screen for app wide settings.
 */
@Composable
fun SettingsDialog(
    currentLang: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,

    currentTheme: String? = null,
    onThemeSelect: ((String) -> Unit)? = null,

    isLoggedIn: Boolean = false,
    showEmailManagement: Boolean = true,
    emailLine: String? = null,
    emailErrorLine: String? = null,
    hasEmail: Boolean = false,
    onAddEmail: (() -> Unit)? = null,
    onChangeEmail: (() -> Unit)? = null,
    onRemoveEmail: (() -> Unit)? = null,
    onChangePassword: (() -> Unit)? = null,
    onLogout: (() -> Unit)? = null,

    links: List<SettingsLink> = emptyList()
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsStore = remember { AppSettingsStore(context.applicationContext) }
    val storedTheme by settingsStore.getThemeMode().collectAsState(initial = APP_THEME_SYSTEM)
    val selectedTheme = currentTheme ?: storedTheme
    var languageMenuExpanded by remember { mutableStateOf(false) }
    var themeMenuExpanded by remember { mutableStateOf(false) }
    val canManageEmail = showEmailManagement &&
            (
                    onAddEmail != null ||
                            onChangeEmail != null ||
                            onRemoveEmail != null ||
                            !emailLine.isNullOrBlank() ||
                            !emailErrorLine.isNullOrBlank() ||
                            hasEmail
                    )
    val languageOptions = listOf(
        SettingsDropdownOption("ru", stringResource(R.string.settings_russian)),
        SettingsDropdownOption("en", stringResource(R.string.settings_english))
    )
    val selectedLanguageLabel = languageOptions
        .firstOrNull { it.value == currentLang }
        ?.label
        ?: currentLang.uppercase()
    val themeOptions = listOf(
        SettingsDropdownOption(APP_THEME_SYSTEM, stringResource(R.string.settings_theme_system)),
        SettingsDropdownOption(APP_THEME_LIGHT, stringResource(R.string.settings_theme_light)),
        SettingsDropdownOption(APP_THEME_DARK, stringResource(R.string.settings_theme_dark))
    )
    val selectedThemeLabel = themeOptions
        .firstOrNull { it.value == selectedTheme }
        ?.label
        ?: themeOptions.first().label

    /** Opens project links in an external app. */
    fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
        } catch (_: Exception) {
        }
    }

    /** Updates app theme. */
    fun selectTheme(theme: String) {
        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm)
        if (onThemeSelect != null) {
            onThemeSelect(theme)
        } else {
            scope.launch { settingsStore.setThemeMode(theme) }
        }
    }

    AlertDialog(
        onDismissRequest = {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.VirtualKey)
            onDismiss()
        },
        title = { Text(stringResource(R.string.settings_app)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                SettingsDropdownSelector(
                    title = stringResource(R.string.settings_language),
                    selectedLabel = selectedLanguageLabel,
                    expanded = languageMenuExpanded,
                    onExpandedChange = { languageMenuExpanded = it },
                    options = languageOptions,
                    onSelect = { language ->
                        languageMenuExpanded = false
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm)
                        onSelect(language)
                    }
                )

                Divider(modifier = Modifier.padding(top = 4.dp))
                SettingsDropdownSelector(
                    title = stringResource(R.string.settings_theme),
                    selectedLabel = selectedThemeLabel,
                    expanded = themeMenuExpanded,
                    onExpandedChange = { themeMenuExpanded = it },
                    options = themeOptions,
                    onSelect = { theme ->
                        themeMenuExpanded = false
                        selectTheme(theme)
                    }
                )

                if (isLoggedIn) {
                    Divider(modifier = Modifier.padding(top = 4.dp))
                    Text(
                        stringResource(R.string.settings_account),
                        style = MaterialTheme.typography.titleSmall
                    )

                    if (canManageEmail) {
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
                    }

                    TextButton(
                        onClick = { onChangePassword?.invoke() },
                        enabled = onChangePassword != null
                    ) { Text(stringResource(R.string.settings_password_change)) }
                }

                if (links.isNotEmpty()) {
                    Divider(modifier = Modifier.padding(top = 4.dp))
                    Text(
                        stringResource(R.string.settings_links),
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

                if (onLogout != null) {
                    Divider(modifier = Modifier.padding(top = 4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Button(
                            onClick = onLogout,
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        ) {
                            Text(stringResource(R.string.auth_logout))
                        }
                    }
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

private data class SettingsDropdownOption(
    val value: String,
    val label: String
)

/** Compact dropdown selector used by settings dialog. */
@Composable
private fun SettingsDropdownSelector(
    title: String,
    selectedLabel: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    options: List<SettingsDropdownOption>,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("${title}:", style = MaterialTheme.typography.bodyMedium)

        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { onExpandedChange(true) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedLabel,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null
                    )
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { onExpandedChange(false) }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = { onSelect(option.value) }
                    )
                }
            }
        }
    }
}
