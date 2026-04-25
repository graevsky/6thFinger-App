package com.example.a6thfingercontrollapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.a6thfingercontrollapp.ble.EMG_MODE_BEND_OTHER
import com.example.a6thfingercontrollapp.ble.EMG_MODE_DIRECTIONAL
import com.example.a6thfingercontrollapp.ble.EmgSettings
import com.example.a6thfingercontrollapp.ble.EspSettings

/** Firmware placeholder for an unused EMG pin. */
private const val UNUSED_PIN = 0xFF

/**
 * Dialog for editing EMG input configuration for one servo pair.
 */
@Composable
fun EmgDialog(
    s: EspSettings,
    index: Int,
    onDismiss: () -> Unit,
    onChange: (EspSettings) -> Unit,
    haptic: HapticFeedback
) {
    val emgSetting = s.emgSettings.getOrNull(index) ?: return

    var channels by remember(index, emgSetting.channels) { mutableIntStateOf(emgSetting.channels) }
    var pin0 by remember(index, emgSetting.pin0) {
        mutableStateOf(if (emgSetting.pin0 == UNUSED_PIN) "" else emgSetting.pin0.toString())
    }
    var pin1 by remember(index, emgSetting.pin1) {
        mutableStateOf(if (emgSetting.pin1 == UNUSED_PIN) "" else emgSetting.pin1.toString())
    }
    var pin2 by remember(index, emgSetting.pin2) {
        mutableStateOf(if (emgSetting.pin2 == UNUSED_PIN) "" else emgSetting.pin2.toString())
    }
    var mode by remember(index, emgSetting.mode) { mutableIntStateOf(emgSetting.mode) }
    var bendFullMoves by remember(index, emgSetting.bendFullMoves) {
        mutableStateOf(emgSetting.bendFullMoves.toString())
    }
    var unfoldFullMoves by remember(index, emgSetting.unfoldFullMoves) {
        mutableStateOf(emgSetting.unfoldFullMoves.toString())
    }
    var minSwitchDelaySec by remember(index, emgSetting.minSwitchDelaySec) {
        mutableStateOf(emgSetting.minSwitchDelaySec.toString())
    }
    var reverseDirection by remember(index, emgSetting.reverseDirection) {
        mutableStateOf(emgSetting.reverseDirection)
    }

    /** Builds a safe EMG settings object from the editable UI state. */
    fun buildUpdatedEmg(): EmgSettings {
        val normalizedChannels = channels.coerceIn(1, 3)
        val parsedPins = listOf(pin0, pin1, pin2).mapIndexed { idx, raw ->
            if (idx < normalizedChannels) {
                raw.toIntOrNull() ?: UNUSED_PIN
            } else {
                UNUSED_PIN
            }
        }

        return emgSetting.copy(
            channels = normalizedChannels,
            pin0 = parsedPins[0],
            pin1 = parsedPins[1],
            pin2 = parsedPins[2],
            mode = mode.coerceIn(EMG_MODE_BEND_OTHER, EMG_MODE_DIRECTIONAL),
            bendFullMoves = (bendFullMoves.toIntOrNull() ?: emgSetting.bendFullMoves).coerceIn(
                1,
                5
            ),
            unfoldFullMoves = (unfoldFullMoves.toIntOrNull()
                ?: emgSetting.unfoldFullMoves).coerceIn(1, 5),
            minSwitchDelaySec = (minSwitchDelaySec.toIntOrNull()
                ?: emgSetting.minSwitchDelaySec).coerceIn(1, 60),
            reverseDirection = reverseDirection
        )
    }

    val modeOptions = listOf(
        EMG_MODE_BEND_OTHER to stringResource(R.string.emg_mode_bend_other),
        EMG_MODE_DIRECTIONAL to stringResource(R.string.emg_mode_directional)
    )

    BaseDialog(
        title = stringResource(R.string.emg_settings),
        onDismiss = onDismiss,
        haptic = haptic
    ) {
        Text(text = "${stringResource(R.string.pair_no)}: ${index + 1}")

        SelectionDropdownField(
            label = stringResource(R.string.emg_channels),
            selectedText = channels.coerceIn(1, 3).toString(),
            options = listOf("1", "2", "3"),
            onSelected = { selectedIndex -> channels = selectedIndex + 1 }
        )

        NumberField(stringResource(R.string.emg_pin_1), pin0) { pin0 = it }
        if (channels >= 2) {
            NumberField(stringResource(R.string.emg_pin_2), pin1) { pin1 = it }
        }
        if (channels >= 3) {
            NumberField(stringResource(R.string.emg_pin_3), pin2) { pin2 = it }
        }

        SelectionDropdownField(
            label = stringResource(R.string.emg_mode),
            selectedText = modeOptions.firstOrNull { it.first == mode }?.second
                ?: stringResource(R.string.emg_mode_bend_other),
            options = modeOptions.map { it.second },
            onSelected = { selectedIndex ->
                mode = modeOptions.getOrNull(selectedIndex)?.first ?: EMG_MODE_BEND_OTHER
            }
        )

        if (mode == EMG_MODE_BEND_OTHER) {
            NumberField(stringResource(R.string.emg_bend_full_moves), bendFullMoves) {
                bendFullMoves = it
            }
            NumberField(stringResource(R.string.emg_unfold_full_moves), unfoldFullMoves) {
                unfoldFullMoves = it
            }
        }

        NumberField(stringResource(R.string.emg_min_delay_sec), minSwitchDelaySec) {
            minSwitchDelaySec = it
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.emg_reverse_direction))
            Switch(
                checked = reverseDirection,
                onCheckedChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    reverseDirection = it
                }
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)

                    val newEmg = s.emgSettings.copyOf()
                    newEmg[index] = buildUpdatedEmg()

                    onChange(s.copy(emgSettings = newEmg))
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.generic_ok))
            }
        }
    }
}

/**
 * Reusable dropdown field used by EMG settings for channel count and mode.
 */
@Composable
private fun SelectionDropdownField(
    label: String,
    selectedText: String,
    options: List<String>,
    onSelected: (Int) -> Unit
) {
    var expanded by remember(label, selectedText, options) { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label)

        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { expanded = true }
            ) {
                Text(selectedText)
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            expanded = false
                            onSelected(index)
                        }
                    )
                }
            }
        }
    }
}
