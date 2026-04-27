package com.example.a6thfingercontrolapp.ui.connect.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.a6thfingercontrolapp.R

/** PIN dialog for ESP32 authentication. */
@Composable
fun PinCodeDialog(
    pin: String,
    onPinChange: (String) -> Unit,
    pinSending: Boolean,
    pinErrorText: String?,
    haptic: HapticFeedback,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { },
        title = { Text(stringResource(R.string.pin_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { onPinChange(it.filter(Char::isDigit).take(4)) },
                    label = { Text(stringResource(R.string.pin_hint)) },
                    singleLine = true,
                    enabled = !pinSending,
                    modifier = Modifier.fillMaxWidth()
                )

                if (!pinErrorText.isNullOrBlank()) {
                    Text(
                        text = pinErrorText,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (pinSending) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp))
                        Text(
                            text = stringResource(R.string.pin_wait),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = (pin.length == 4) && !pinSending,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    onConfirm()
                }
            ) {
                Text(stringResource(R.string.generic_ok))
            }
        },
        dismissButton = {
            TextButton(
                enabled = !pinSending,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.Reject)
                    onDismiss()
                }
            ) { Text(stringResource(R.string.device_cancel)) }
        }
    )
}
