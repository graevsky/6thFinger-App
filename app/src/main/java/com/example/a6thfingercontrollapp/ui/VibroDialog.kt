package com.example.a6thfingercontrollapp.ui

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.a6thfingercontrollapp.R
import com.example.a6thfingercontrollapp.ble.EspSettings
import kotlin.math.max

@Composable
fun VibroDialog(
    s: EspSettings,
    onDismiss: () -> Unit,
    onChange: (EspSettings) -> Unit,
    haptic: HapticFeedback
) {
    var mode by remember {
        mutableStateOf(if (s.vibroMode == 0) VibMode.Constant else VibMode.Pulse)
    }

    var pin by remember { mutableStateOf(s.vibroPin.toString()) }

    var intensity by remember {
        mutableStateOf(
            ((s.vibroSoftPower.coerceIn(0, 255) * 100) / 255).coerceIn(0, 100).toString()
        )
    }

    var onMs by remember { mutableStateOf("150") }
    var offMs by remember { mutableStateOf("150") }

    fun toDeviceValues(): Quad<Int, Int, Int, Int> {
        val intensityPct = intensity.toIntOrNull()?.coerceIn(0, 100) ?: 60
        val softPower = ((intensityPct * 255) / 100).coerceIn(0, 255)

        return if (mode == VibMode.Constant) {
            val freq = s.vibroFreqHz
            Quad(0, freq, softPower, s.vibroPulseBase)
        } else {
            val on = max(1, onMs.toIntOrNull() ?: 150)
            val off = max(1, offMs.toIntOrNull() ?: 150)
            val period = (on + off).coerceAtLeast(2)
            val freq = (1000f / period).toInt().coerceAtLeast(1)

            val base = ((intensityPct * 200) / 100).coerceIn(0, 255)

            Quad(1, freq, softPower, base)
        }
    }

    BaseDialog(title = "Vibro Settings", onDismiss, haptic = haptic) {
        // Используем stringResource для текста
        NumberField(stringResource(R.string.vibro_pin), pin) { pin = it }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.vibro_mode))  // Переводим текст через stringResource
            SegmentedButtons(
                items = listOf(
                    stringResource(R.string.vibro_continuous),
                    stringResource(R.string.vibro_pulse)
                ),
                selectedIndex = if (mode == VibMode.Constant) 0 else 1,
                onSelect = { idx -> mode = if (idx == 0) VibMode.Constant else VibMode.Pulse }
            )
        }

        NumberField("${stringResource(R.string.vibro_intensity)} %", intensity) { intensity = it }

        if (mode == VibMode.Pulse) {
            NumberField(stringResource(R.string.vibro_work_time), onMs) { onMs = it }
            NumberField(stringResource(R.string.vibro_pause_time), offMs) { offMs = it }
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    val (modeInt, freqHz, softPower, pulseBase) = toDeviceValues()
                    onChange(
                        s.copy(
                            vibroPin = pin.toIntOrNull() ?: s.vibroPin,
                            vibroMode = modeInt,
                            vibroFreqHz = freqHz,
                            vibroSoftPower = softPower,
                            vibroPulseBase = pulseBase,
                            vibroMinDuty = 0,
                            vibroMaxDuty = 255
                        )
                    )
                    onDismiss()
                }
            ) { Text(stringResource(R.string.generic_ok)) }
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
