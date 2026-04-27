package com.example.a6thfingercontrolapp.ui.connect.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.a6thfingercontrolapp.R
import com.example.a6thfingercontrolapp.data.LastDevice

/** Last used BLE device card. */
@Composable
fun LastDeviceCard(
    last: LastDevice?,
    alias: String?,
    isConnected: Boolean,
    connectEnabled: Boolean,
    haptic: HapticFeedback,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.device_last), style = MaterialTheme.typography.titleMedium)
            if (last == null) {
                Text(stringResource(R.string.device_no_stored))
            } else {
                val title = (alias ?: last.name).ifBlank { alias ?: last.name }
                Text(title.ifBlank { stringResource(R.string.device_no_name) })
                Text(last.address, style = MaterialTheme.typography.bodySmall)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isConnected) {
                        OutlinedButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.ToggleOff)
                            onDisconnect()
                        }) {
                            Text(stringResource(R.string.device_disconnect))
                        }
                    } else {
                        Button(
                            enabled = connectEnabled,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                                onConnect(last.address)
                            }
                        ) {
                            Text(stringResource(R.string.device_connect))
                        }
                    }
                }
            }
        }
    }
}
