package com.example.a6thfingercontrolapp.ui.account.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.a6thfingercontrolapp.R
import com.example.a6thfingercontrolapp.ui.account.CloudDeviceChoice
import com.example.a6thfingercontrolapp.ui.account.CloudSettingsState
import com.example.a6thfingercontrolapp.ui.account.cloudStatusText

/**
 * Cloud settings dialog used to inspect device payloads.
 */
@Composable
internal fun DeviceSettingsDialog(
    devices: List<CloudDeviceChoice>,
    selectedKey: String,
    selectedState: CloudSettingsState?,
    json: String,
    isBusy: Boolean,
    isPullEnabled: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSelectedKeyChange: (String) -> Unit,
    onPreviewClick: () -> Unit,
    onPullClick: () -> Unit,
    onPushClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val selectedChoice = devices.firstOrNull { it.key == selectedKey }

    AlertDialog(
        onDismissRequest = {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.VirtualKey)
            onDismiss()
        },
        title = { Text(text = stringResource(R.string.prosthesis_settings)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.prosthesis_settings_dialog_hint),
                    style = MaterialTheme.typography.bodyMedium
                )

                CloudDeviceSelector(
                    devices = devices,
                    selectedKey = selectedKey,
                    selectedState = selectedState,
                    onSelectedKeyChange = onSelectedKeyChange
                )

                selectedChoice?.let { choice ->
                    val alias = choice.alias?.takeIf { it.isNotBlank() }
                    val statusText = cloudStatusText(choice, selectedState)

                    if (!alias.isNullOrBlank()) {
                        Text(
                            text = "${stringResource(R.string.alias)}: $alias",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Text(
                        text = "${stringResource(R.string.prosthesis_address)}: ${choice.address}",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    selectedState?.record?.let { record ->
                        Text(
                            text = "${stringResource(R.string.prosthesis_version)}: ${record.version}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (record.updatedAt.isNotBlank()) {
                            Text(
                                text = "${stringResource(R.string.prosthesis_updated_at)}: ${record.updatedAt}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = json,
                    onValueChange = {},
                    label = { Text(stringResource(R.string.label_json)) },
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )

                if (isBusy) {
                    Text(
                        text = stringResource(R.string.loading),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (!error.isNullOrBlank()) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = !isBusy && isPullEnabled,
                        onClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm)
                            onPullClick()
                        }
                    ) { Text(text = stringResource(R.string.prosthesis_pull)) }

                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = !isBusy,
                        onClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm)
                            onPushClick()
                        }
                    ) { Text(text = stringResource(R.string.prosthesis_push)) }
                }

                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isBusy,
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.ContextClick)
                        onPreviewClick()
                    }
                ) { Text(stringResource(R.string.prosthesis_preview)) }
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
private fun CloudDeviceSelector(
    devices: List<CloudDeviceChoice>,
    selectedKey: String,
    selectedState: CloudSettingsState?,
    onSelectedKeyChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedChoice = devices.firstOrNull { it.key == selectedKey }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.prosthesis_target_device),
            style = MaterialTheme.typography.bodySmall
        )

        Box {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { expanded = true }
            ) {
                Text(selectedChoice?.title ?: stringResource(R.string.prosthesis_select_target))
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                devices.forEach { choice ->
                    val state = if (choice.key == selectedKey) selectedState else null
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(choice.title)
                                Text(
                                    text = cloudStatusText(choice, state),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        },
                        onClick = {
                            expanded = false
                            onSelectedKeyChange(choice.key)
                        }
                    )
                }
            }
        }
    }
}
