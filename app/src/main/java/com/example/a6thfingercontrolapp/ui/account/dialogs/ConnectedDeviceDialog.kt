package com.example.a6thfingercontrolapp.ui.account.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.a6thfingercontrolapp.R
import com.example.a6thfingercontrolapp.ui.account.ConnectedDeviceSummary
import com.example.a6thfingercontrolapp.ui.account.currentDeviceVersionText
import com.example.a6thfingercontrolapp.ui.account.formatLocalUpdatedAt

/**
 * Dialog for the currently connected prosthesis.
 */
@Composable
internal fun ConnectedDeviceDialog(
    isLoggedIn: Boolean,
    device: ConnectedDeviceSummary?,
    json: String,
    isBusy: Boolean,
    canPullFromServer: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onPushToServer: () -> Unit,
    onPullFromServer: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    AlertDialog(
        onDismissRequest = {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.VirtualKey)
            onDismiss()
        },
        title = { Text(text = stringResource(R.string.prosthesis_current_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isLoggedIn) {
                    Text(
                        text = stringResource(R.string.prosthesis_guest_message),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    return@Column
                }

                val currentDevice = device ?: run {
                    Text(
                        text = stringResource(R.string.prosthesis_current_connect_first),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    return@Column
                }
                val updatedAtText = formatLocalUpdatedAt(currentDevice.updatedAtMillis)

                Text(
                    text = "${stringResource(R.string.alias)}: ${currentDevice.displayName}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "${stringResource(R.string.prosthesis_address)}: ${currentDevice.address}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "${stringResource(R.string.ble_status)}: ${stringResource(R.string.prosthesis_connected)}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "${stringResource(R.string.prosthesis_version)}: ${currentDeviceVersionText(currentDevice)}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "${stringResource(R.string.prosthesis_updated_at)}: ${
                        if (updatedAtText.isBlank()) {
                            stringResource(R.string.prosthesis_updated_at_unknown)
                        } else {
                            updatedAtText
                        }
                    }",
                    style = MaterialTheme.typography.bodySmall
                )

                OutlinedTextField(
                    value = json,
                    onValueChange = {},
                    label = { Text(stringResource(R.string.label_json)) },
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )

                if (!canPullFromServer) {
                    Text(
                        text = stringResource(R.string.prosthesis_pull_requires_exact_match),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

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

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isBusy,
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm)
                        onPushToServer()
                    }
                ) {
                    Text(stringResource(R.string.prosthesis_push_current_device))
                }

                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isBusy && canPullFromServer,
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm)
                        onPullFromServer()
                    }
                ) {
                    Text(stringResource(R.string.prosthesis_pull_current_device))
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
