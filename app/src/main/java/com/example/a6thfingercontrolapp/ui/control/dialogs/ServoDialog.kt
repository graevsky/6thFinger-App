package com.example.a6thfingercontrolapp.ui.control.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.a6thfingercontrolapp.R
import com.example.a6thfingercontrolapp.ble.settings.EspSettings
import com.example.a6thfingercontrolapp.ui.control.pretty
import kotlin.math.max

/**
 * Dialog for editing servo pin/range and testing the servo in live mode.
 *
 * Live mode keeps the dialog open until disabled so the app can safely stop the
 * dedicated low-latency BLE control stream before closing.
 */
@Composable
fun ServoDialog(
    s: EspSettings,
    index: Int,
    currentServoDeg: Float,
    onDismiss: () -> Unit,
    onChange: (EspSettings) -> Unit,

    liveEnabled: Boolean,
    onLiveEnabledChange: (Boolean) -> Unit,
    onLiveAngle: (Int) -> Unit,

    haptic: HapticFeedback
) {
    val servoSetting = s.servoSettings[index]

    var pin by remember { mutableStateOf(servoSetting.servoPin.toString()) }
    var min by remember { mutableStateOf(servoSetting.servoMinDeg.toString()) }
    var max by remember { mutableStateOf(servoSetting.servoMaxDeg.toString()) }

    val minV = (min.toIntOrNull() ?: servoSetting.servoMinDeg).coerceIn(0, 180)
    val maxV = (max.toIntOrNull() ?: servoSetting.servoMaxDeg).coerceIn(0, 180).coerceAtLeast(minV)

    var slider by remember {
        mutableStateOf(
            (servoSetting.servoManualDeg)
                .coerceIn(minV, maxV)
                .toFloat()
        )
    }

    var live by remember(liveEnabled) { mutableStateOf(liveEnabled) }
    var showDismissHint by remember { mutableStateOf(false) }

    /** Builds a new settings snapshot with the current servo fields applied. */
    fun buildSettingsWith(angle: Int): EspSettings {
        val minAngle = (min.toIntOrNull() ?: servoSetting.servoMinDeg).coerceIn(0, 180)
        val maxAngle =
            (max.toIntOrNull() ?: servoSetting.servoMaxDeg).coerceIn(0, 180).coerceAtLeast(minAngle)
        val clamped = angle.coerceIn(minAngle, maxAngle)

        return s.copy(
            servoSettings = s.servoSettings.toMutableList().apply {
                set(
                    index,
                    servoSetting.copy(
                        servoPin = pin.toIntOrNull() ?: servoSetting.servoPin,
                        servoMinDeg = minAngle,
                        servoMaxDeg = maxAngle,
                        servoManual = 0,
                        servoManualDeg = clamped
                    )
                )
            }.toTypedArray()
        )
    }

    /** Prevents closing the dialog while live control is still active. */
    fun tryDismiss() {
        if (live) {
            showDismissHint = true
            haptic.performHapticFeedback(HapticFeedbackType.Reject)
            return
        }
        onDismiss()
    }

    LaunchedEffect(minV, maxV) {
        val clamped = slider.toInt().coerceIn(minV, maxV)
        if (clamped.toFloat() != slider) {
            slider = clamped.toFloat()
            if (live) onLiveAngle(clamped)
        }
    }

    BaseDialog(
        title = stringResource(R.string.servo_settings),
        onDismiss = { tryDismiss() },
        haptic = haptic
    ) {
        Text(
            "${stringResource(R.string.servo_current_angle)}: ${pretty(currentServoDeg)}°",
            style = MaterialTheme.typography.bodyMedium
        )

        if (showDismissHint) {
            Text(
                text = stringResource(R.string.live_control_disable_to_close),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        MaterialTheme.shapes.medium
                    )
                    .padding(10.dp),
                style = MaterialTheme.typography.bodySmall
            )
        }

        NumberField(stringResource(R.string.servo_pin), pin) { pin = it }
        NumberField(stringResource(R.string.servo_min_angle), min) { min = it }
        NumberField(stringResource(R.string.servo_max_angle), max) { max = it }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.live_control))
            Switch(
                checked = live,
                onCheckedChange = { en ->
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    showDismissHint = false
                    live = en
                    onLiveEnabledChange(en)
                    if (en) onLiveAngle(slider.toInt())
                }
            )
        }

        Slider(
            value = slider,
            onValueChange = {
                slider = it
                if (live) onLiveAngle(it.toInt())
            },
            valueRange = minV.toFloat()..maxV.toFloat(),
            steps = max(0, maxV - minV),
            enabled = live
        )

        Text(
            "${stringResource(R.string.servo_manual_angle)}: ${slider.toInt()}°",
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            Button(onClick = {
                if (live) {
                    showDismissHint = true
                    haptic.performHapticFeedback(HapticFeedbackType.Reject)
                    return@Button
                }

                haptic.performHapticFeedback(HapticFeedbackType.Confirm)

                val finalCfg = buildSettingsWith(slider.toInt())
                onChange(finalCfg)
                onDismiss()
            }) { Text(stringResource(R.string.generic_ok)) }
        }
    }
}
