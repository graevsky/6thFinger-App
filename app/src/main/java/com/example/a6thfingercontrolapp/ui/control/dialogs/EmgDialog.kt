package com.example.a6thfingercontrolapp.ui.control.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.a6thfingercontrolapp.R
import com.example.a6thfingercontrolapp.ble.settings.EmgSettings
import com.example.a6thfingercontrolapp.ble.settings.EspSettings
import com.example.a6thfingercontrolapp.ble.settings.PIN_PLACEHOLDER

/**
 * Dialog for editing EMG configuration for one servo pair.
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

    val pin = remember(index, emgSetting.pin) {
        mutableStateOf(if (emgSetting.pin == PIN_PLACEHOLDER) "" else emgSetting.pin.toString())
    }
    val bendSnapshotsToBend = remember(index, emgSetting.bendSnapshotsToBend) {
        mutableStateOf(emgSetting.bendSnapshotsToBend.toString())
    }
    val bendSnapshotsToUnfold = remember(index, emgSetting.bendSnapshotsToUnfold) {
        mutableStateOf(emgSetting.bendSnapshotsToUnfold.toString())
    }
    val snapshotTimeoutSec = remember(index, emgSetting.snapshotTimeoutSec) {
        mutableStateOf(emgSetting.snapshotTimeoutSec.toString())
    }
    val snapshotSize = remember(index, emgSetting.snapshotSize) {
        mutableStateOf(emgSetting.snapshotSize.toString())
    }
    val minUnfoldDelaySec = remember(index, emgSetting.minUnfoldDelaySec) {
        mutableStateOf(emgSetting.minUnfoldDelaySec.toString())
    }
    val reverseDirection = remember(index, emgSetting.reverseDirection) {
        mutableStateOf(emgSetting.reverseDirection)
    }

    /** Builds a safe EMG settings object from the editable UI state. */
    fun buildUpdatedEmg(): EmgSettings {
        return emgSetting.copy(
            pin = pin.value.toIntOrNull() ?: PIN_PLACEHOLDER,
            bendSnapshotsToBend = (
                    bendSnapshotsToBend.value.toIntOrNull() ?: emgSetting.bendSnapshotsToBend
                    ).coerceIn(1, 5),
            bendSnapshotsToUnfold = (
                    bendSnapshotsToUnfold.value.toIntOrNull() ?: emgSetting.bendSnapshotsToUnfold
                    ).coerceIn(1, 8),
            snapshotTimeoutSec = (
                    snapshotTimeoutSec.value.toIntOrNull() ?: emgSetting.snapshotTimeoutSec
                    ).coerceIn(1, 15),
            snapshotSize = (
                    snapshotSize.value.toIntOrNull() ?: emgSetting.snapshotSize
                    ).coerceIn(1, 32),
            minUnfoldDelaySec = (
                    minUnfoldDelaySec.value.toIntOrNull() ?: emgSetting.minUnfoldDelaySec
                    ).coerceIn(0, 30),
            reverseDirection = reverseDirection.value
        )
    }

    BaseDialog(
        title = stringResource(R.string.emg_settings),
        onDismiss = onDismiss,
        haptic = haptic
    ) {
        Text(text = "${stringResource(R.string.pair_no)}: ${index + 1}")
        Text(text = "${stringResource(R.string.emg_model)}: ${stringResource(R.string.emg_model_1ch_binary)}")

        NumberField(stringResource(R.string.emg_pin), pin.value) { pin.value = it }
        NumberField(stringResource(R.string.emg_bend_full_moves), bendSnapshotsToBend.value) {
            bendSnapshotsToBend.value = it
        }
        NumberField(stringResource(R.string.emg_unfold_full_moves), bendSnapshotsToUnfold.value) {
            bendSnapshotsToUnfold.value = it
        }
        NumberField(stringResource(R.string.emg_snapshot_timeout_sec), snapshotTimeoutSec.value) {
            snapshotTimeoutSec.value = it
        }
        NumberField(stringResource(R.string.emg_snapshot_size), snapshotSize.value) {
            snapshotSize.value = it
        }
        NumberField(stringResource(R.string.emg_min_delay_sec), minUnfoldDelaySec.value) {
            minUnfoldDelaySec.value = it
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.emg_reverse_direction))
            Switch(
                checked = reverseDirection.value,
                onCheckedChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    reverseDirection.value = it
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
