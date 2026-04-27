package com.example.a6thfingercontrolapp.ui.control.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.a6thfingercontrolapp.R

/**
 * Dialog that is used to rename active prothesis.
 */
@Composable
internal fun RenameDialog(
    current: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    haptic: HapticFeedback
) {
    var text by remember { mutableStateOf(TextFieldValue(current)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.device_rename)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text(stringResource(R.string.device_name)) }
            )
        },
        confirmButton = {
            Button(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                onSave(text.text)
            }) {
                Text(stringResource(R.string.device_save))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.Reject)
                onDismiss()
            }) { Text(stringResource(R.string.device_cancel)) }
        }
    )
}

/**
 * Dialog that is used to change active prothesis PIN.
 */
@Composable
internal fun PinDialog(
    currentPinCode: Int,
    onDismiss: () -> Unit,
    onSetPin: (Int) -> Unit,
    haptic: HapticFeedback
) {
    var pin by remember {
        mutableStateOf(if (currentPinCode == 0) "0000" else "%04d".format(currentPinCode))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pin_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(4) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.pin_hint)) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        pin = "0000"
                    }
                ) { Text(stringResource(R.string.pin_reset)) }
            }
        },
        confirmButton = {
            Button(
                enabled = pin.length == 4,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    onSetPin(pin.toIntOrNull() ?: 0)
                }
            ) { Text(stringResource(R.string.device_save)) }
        },
        dismissButton = {
            TextButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.Reject)
                onDismiss()
            }) { Text(stringResource(R.string.device_cancel)) }
        }
    )
}

@Composable
internal fun BaseDialog(
    title: String,
    onDismiss: () -> Unit,
    haptic: HapticFeedback,
    content: @Composable ColumnScope.() -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                content()
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.Reject)
                onDismiss()
            }) { Text(stringResource(R.string.device_cancel)) }
        }
    )
}

@Composable
internal fun NumberField(label: String, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValue(it.filter { ch -> ch.isDigit() }) },
        singleLine = true,
        label = { Text(label) }
    )
}

@Composable
internal fun SegmentedButtons(items: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    require(items.size >= 2)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEachIndexed { i, label ->
            val selected = i == selectedIndex
            if (selected) {
                FilledTonalButton(onClick = { onSelect(i) }) { Text(label) }
            } else {
                OutlinedButton(onClick = { onSelect(i) }) { Text(label) }
            }
        }
    }
}
