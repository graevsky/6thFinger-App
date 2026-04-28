package com.example.a6thfingercontrolapp.ui.control

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.a6thfingercontrolapp.R
import com.example.a6thfingercontrolapp.ble.Telemetry
import com.example.a6thfingercontrolapp.ble.settings.EmgSettings
import com.example.a6thfingercontrolapp.ble.settings.EspSettings
import com.example.a6thfingercontrolapp.ble.settings.INPUT_SOURCE_EMG
import com.example.a6thfingercontrolapp.ble.settings.INPUT_SOURCE_FLEX

/**
 * Complete control card for one visible servo/flex/EMG pair.
 */
@Composable
internal fun PairControlSection(
    pairIdx: Int,
    settings: EspSettings,
    telemetry: Telemetry,
    teleReasonText: String?,
    telePretty: (Float) -> String,
    teleInt: (Int) -> String,
    showTelePlaceholder: Boolean,
    onServoClick: () -> Unit,
    onFlexClick: () -> Unit,
    onEmgClick: () -> Unit,
    onSourceChanged: (Boolean) -> Unit,
    onDelete: (() -> Unit)?
) {
    val source = settings.pairInputSettings.getOrNull(pairIdx)?.inputSource ?: INPUT_SOURCE_FLEX
    val useEmg = source == INPUT_SOURCE_EMG
    settings.emgSettings.getOrNull(pairIdx) ?: EmgSettings()

    Text(
        text = "${stringResource(R.string.pair_no)} ${pairIdx + 1}",
        style = MaterialTheme.typography.titleMedium
    )

    SettingToggleItem(
        title = stringResource(R.string.emg_use_instead_of_flex),
        subtitle = inputSourceLabel(source),
        checked = useEmg
    ) { onSourceChanged(it) }

    SettingItem(
        title = stringResource(R.string.servo_settings),
        subtitle = stringResource(R.string.control_servo_manual)
    ) { onServoClick() }

    if (useEmg) {
        SettingItem(
            title = stringResource(R.string.emg_settings),
            subtitle = emgModelLabel()
        ) { onEmgClick() }
    } else {
        SettingItem(
            title = stringResource(R.string.flex_settings),
            subtitle = stringResource(R.string.control_resistance_cal)
        ) { onFlexClick() }
    }

    Text(
        stringResource(R.string.flex_n_servo_curr_vals),
        style = MaterialTheme.typography.titleMedium
    )
    if (teleReasonText != null) {
        Text(teleReasonText, style = MaterialTheme.typography.bodySmall)
    }

    DiagnosticRow(
        stringResource(R.string.input_source),
        inputSourceLabel(source)
    )
    DiagnosticRow(
        stringResource(R.string.control_diag_servo_deg),
        telePretty(telemetry.servoDeg[pairIdx])
    )

    if (useEmg) {
        val eventText =
            if (showTelePlaceholder) stringResource(R.string.telemetry_placeholder)
            else emgEventLabel(telemetry.emgEvent[pairIdx])

        val actionText =
            if (showTelePlaceholder) stringResource(R.string.telemetry_placeholder)
            else emgActionLabel(telemetry.emgAction[pairIdx])

        DiagnosticRow(stringResource(R.string.emg_model), emgModelLabel())
        DiagnosticRow(stringResource(R.string.emg_current_event), eventText)
        DiagnosticRow(stringResource(R.string.emg_current_action), actionText)
        DiagnosticRow(
            stringResource(R.string.emg_cooldown),
            teleInt(telemetry.emgCooldownMs[pairIdx])
        )

        DiagnosticRow(
            stringResource(R.string.emg_bend_progress),
            teleInt(telemetry.emgBendProgress[pairIdx])
        )
        DiagnosticRow(
            stringResource(R.string.emg_unfold_progress),
            teleInt(telemetry.emgUnfoldProgress[pairIdx])
        )
        DiagnosticRow(
            stringResource(R.string.emg_channel_value, 1),
            telePretty(telemetry.emgChannelValue(pairIdx, 0))
        )
    } else {
        DiagnosticRow(
            stringResource(R.string.control_diag_flex_ohm),
            telePretty(telemetry.flexOhm[pairIdx])
        )
    }

    if (onDelete != null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                onClick = onDelete,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.delete_pair)
                )
                Spacer(Modifier.width(8.dp))
                Text("${stringResource(R.string.pair_no)} ${pairIdx + 1}")
            }
        }
    }

    Divider(Modifier.padding(vertical = 8.dp))
}

@Composable
internal fun SettingItem(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
    ) {
        Row(
            Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = onClick) { Text(stringResource(R.string.device_open)) }
        }
    }
}

@Composable
internal fun SettingToggleItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
    ) {
        Row(
            Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = if (enabled) onCheckedChange else null
            )
        }
    }
}

@Composable
internal fun DiagnosticRow(name: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(name)
        Text(value)
    }
}

internal fun pretty(v: Float) = if (v.isFinite()) String.format("%.1f", v) else "--"
