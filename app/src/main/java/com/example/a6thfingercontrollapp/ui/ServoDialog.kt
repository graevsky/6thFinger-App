package com.example.a6thfingercontrollapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import com.example.a6thfingercontrollapp.ble.EspSettings
import kotlin.math.max

@Composable
fun ServoDialog(
    s: EspSettings,
    index: Int,
    currentServoDeg: Float,
    onDismiss: () -> Unit,
    onChange: (EspSettings) -> Unit,
    onLiveChange: (EspSettings) -> Unit,
    haptic: HapticFeedback
) {
    val servoSetting = s.servoSettings[index]

    var pin by remember { mutableStateOf(servoSetting.servoPin.toString()) }
    var min by remember { mutableStateOf(servoSetting.servoMinDeg.toString()) }
    var max by remember { mutableStateOf(servoSetting.servoMaxDeg.toString()) }
    var manual by remember { mutableStateOf(servoSetting.servoManual != 0) }
    var deg by remember { mutableStateOf(servoSetting.servoManualDeg.toString()) }

    val minV = min.toIntOrNull() ?: servoSetting.servoMinDeg
    val maxV = max.toIntOrNull() ?: servoSetting.servoMaxDeg
    var slider by remember {
        mutableStateOf(
            (deg.toIntOrNull() ?: servoSetting.servoManualDeg).coerceIn(minV, maxV).toFloat()
        )
    }

    fun buildSettingsWith(angle: Int): EspSettings {
        val minAngle = min.toIntOrNull() ?: servoSetting.servoMinDeg
        val maxAngle = max.toIntOrNull() ?: servoSetting.servoMaxDeg
        val clamped = angle.coerceIn(minAngle, maxAngle)
        return s.copy(
            servoSettings = s.servoSettings.toMutableList().apply {
                set(
                    index, servoSetting.copy(
                        servoPin = pin.toIntOrNull() ?: servoSetting.servoPin,
                        servoMinDeg = minAngle,
                        servoMaxDeg = maxAngle,
                        servoManual = if (manual) 1 else 0,
                        servoManualDeg = clamped
                    )
                )
            }.toTypedArray()
        )
    }

    fun pushImmediate(value: Int) {
        val next = buildSettingsWith(value)
        onLiveChange(next)
    }

    BaseDialog(title = "Servo Settings", onDismiss = onDismiss, haptic = haptic) {
        Text(
            "Current Angle: ${pretty(currentServoDeg)}°",
            style = MaterialTheme.typography.bodyMedium
        )
        NumberField("Servo Pin", pin) { pin = it }
        NumberField("Min Angle", min) { min = it }
        NumberField("Max Angle", max) { max = it }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Manual Control")
            Switch(checked = manual, onCheckedChange = {
                manual = it
                pushImmediate(slider.toInt())
            })
        }
        if (manual) {
            Slider(
                value = slider,
                onValueChange = {
                    slider = it
                    deg = it.toInt().toString()
                    pushImmediate(it.toInt())
                },
                valueRange = (min.toIntOrNull()
                    ?: servoSetting.servoMinDeg).toFloat()..(max.toIntOrNull()
                    ?: servoSetting.servoMaxDeg).toFloat(),
                steps = max(
                    0,
                    (max.toIntOrNull() ?: servoSetting.servoMaxDeg) - (min.toIntOrNull()
                        ?: servoSetting.servoMinDeg)
                )
            )
            Text("Manual Angle: ${slider.toInt()}°", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            Button(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                val finalCfg = buildSettingsWith(slider.toInt())
                onChange(finalCfg)
                onDismiss()
            }) { Text("OK") }
        }
    }
}
