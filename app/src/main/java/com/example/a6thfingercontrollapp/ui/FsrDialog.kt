package com.example.a6thfingercontrollapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.a6thfingercontrollapp.ble.EspSettings

@Composable
fun FsrDialog(s: EspSettings, onDismiss: () -> Unit, onChange: (EspSettings) -> Unit) {
    var pin by remember { mutableStateOf(s.fsrPin.toString()) }
    var pull by remember { mutableStateOf(s.fsrPullupOhm.toString()) }
    var soft by remember { mutableStateOf(s.fsrSoftThresholdN.toInt().toString()) }
    var hard by remember { mutableStateOf(s.fsrHardMaxN.toInt().toString()) }

    BaseDialog(title = "FSR Settings", onDismiss = onDismiss) {
        NumberField("FSR Pin", pin) { pin = it }
        NumberField("FSR Pullup", pull) { pull = it }
        NumberField("FSR Threshold (Soft)", soft) { soft = it }
        NumberField("FSR Max Vibro", hard) { hard = it }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            Button(onClick = {
                onChange(
                    s.copy(
                        fsrPin = pin.toIntOrNull() ?: s.fsrPin,
                        fsrPullupOhm = pull.toIntOrNull() ?: s.fsrPullupOhm,
                        fsrSoftThresholdN = (soft.toFloatOrNull() ?: s.fsrSoftThresholdN),
                        fsrHardMaxN = (hard.toFloatOrNull() ?: s.fsrHardMaxN)
                    )
                )
                onDismiss()
            }) { Text("OK") }
        }
    }
}
