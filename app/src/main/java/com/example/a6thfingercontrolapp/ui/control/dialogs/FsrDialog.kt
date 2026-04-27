package com.example.a6thfingercontrolapp.ui.control.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.a6thfingercontrolapp.R
import com.example.a6thfingercontrolapp.ble.settings.EspSettings

/**
 * Dialog for editing global FSR pressure sensor and vibration threshold settings.
 */
@Composable
fun FsrDialog(
    s: EspSettings,
    onDismiss: () -> Unit,
    onChange: (EspSettings) -> Unit,
    haptic: HapticFeedback
) {
    var pin by remember { mutableStateOf(s.fsrPin.toString()) }
    var pull by remember { mutableStateOf(s.fsrPullupOhm.toString()) }
    var soft by remember { mutableStateOf(s.fsrSoftThresholdN.toInt().toString()) }
    var hard by remember { mutableStateOf(s.fsrHardMaxN.toInt().toString()) }

    BaseDialog(
        title = stringResource(R.string.fsr_settings),
        onDismiss = onDismiss,
        haptic = haptic
    ) {
        NumberField(stringResource(R.string.fsr_pin), pin) { pin = it }
        NumberField(stringResource(R.string.fsr_pullup), pull) { pull = it }
        NumberField(stringResource(R.string.fsr_start_threshold), soft) { soft = it }
        NumberField(stringResource(R.string.fsr_max_vibro), hard) { hard = it }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            Button(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                onChange(
                    s.copy(
                        fsrPin = pin.toIntOrNull() ?: s.fsrPin,
                        fsrPullupOhm = pull.toIntOrNull() ?: s.fsrPullupOhm,
                        fsrSoftThresholdN = soft.toIntOrNull()?.toFloat() ?: s.fsrSoftThresholdN,
                        fsrHardMaxN = hard.toIntOrNull()?.toFloat() ?: s.fsrHardMaxN
                    )
                )
                onDismiss()
            }) { Text(stringResource(R.string.generic_ok)) }
        }
    }
}
