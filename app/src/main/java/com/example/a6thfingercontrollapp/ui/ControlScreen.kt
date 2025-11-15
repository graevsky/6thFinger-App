package com.example.a6thfingercontrollapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.a6thfingercontrollapp.BleViewModel
import com.example.a6thfingercontrollapp.R
import com.example.a6thfingercontrollapp.ble.DeviceSettings
import kotlin.math.max

private enum class VibMode { Constant, Pulse }

@Composable
fun ControlScreen(vm: BleViewModel) {
    val alias by vm.activeAlias.collectAsState()
    val t by vm.state.collectAsState()
    val s by vm.activeSettings.collectAsState()

    var renameOpen by remember { mutableStateOf(false) }
    var fsrOpen by remember { mutableStateOf(false) }
    var flexOpen by remember { mutableStateOf(false) }
    var vibroOpen by remember { mutableStateOf(false) }
    var servoOpen by remember { mutableStateOf(false) }

    val connected = t.status.contains("Subscribed", true) || t.status.contains("Connected", true)

    var savedSnapshot by remember { mutableStateOf(s) }
    val dirty = s != savedSnapshot

    Scaffold(
        bottomBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = {
                        if (vm.applyAndSaveToBoard()) {
                            savedSnapshot = s
                        }
                    },
                    enabled = connected,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (dirty) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(stringResource(R.string.device_save))
                }
            }
        }
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.device_name),
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            if (alias.isBlank()) {
                                stringResource(R.string.device_no_name)
                            } else alias,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    OutlinedButton(onClick = { renameOpen = true }, enabled = connected) {
                        Text(stringResource(R.string.device_rename))
                    }
                }
            }

            Text(
                stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleMedium
            )

            SettingItem(
                title = stringResource(R.string.fsr_settings),
                subtitle = stringResource(R.string.control_sensitivity_pullup)
            ) { fsrOpen = true }

            SettingItem(
                title = stringResource(R.string.flex_settings),
                subtitle = stringResource(R.string.control_resistance_cal)
            ) { flexOpen = true }

            SettingItem(
                title = stringResource(R.string.vibro_settings),
                subtitle = stringResource(R.string.control_vibro_mode)
            ) { vibroOpen = true }

            SettingItem(
                title = stringResource(R.string.servo_settings),
                subtitle = stringResource(R.string.control_servo_manual)
            ) { servoOpen = true }

            Divider(Modifier.padding(vertical = 8.dp))
            Text(
                stringResource(R.string.control_diagnostic),
                style = MaterialTheme.typography.titleMedium
            )
            DiagnosticRow("FSR, Ω", pretty(t.fsrOhm))
            DiagnosticRow("Flex, Ω", pretty(t.flexOhm))
            DiagnosticRow("Servo, °", pretty(t.servoDeg))
        }
    }

    if (renameOpen) RenameDialog(
        current = alias,
        onDismiss = { renameOpen = false },
        onSave = { newName ->
            vm.renameActive(newName)
            renameOpen = false
        }
    )

    if (fsrOpen) FsrDialog(s, onDismiss = { fsrOpen = false }) { next ->
        vm.updateActiveSettings { next }
    }

    if (flexOpen) FlexDialog(
        s,
        currentFlexOhm = t.flexOhm,
        onDismiss = { flexOpen = false }
    ) { next ->
        vm.updateActiveSettings { next }
    }

    if (vibroOpen) VibroDialog(
        s,
        onDismiss = { vibroOpen = false }
    ) { next ->
        vm.updateActiveSettings { next }
    }

    if (servoOpen) ServoDialog(
        s,
        currentServoDeg = t.servoDeg,
        onDismiss = { servoOpen = false }
    ) { next ->
        vm.updateActiveSettings { next }
    }
}

@Composable
private fun SettingItem(title: String, subtitle: String, onClick: () -> Unit) {
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
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = onClick) {
                Text(stringResource(R.string.device_open))
            }
        }
    }
}

@Composable
private fun DiagnosticRow(name: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(name)
        Text(value)
    }
}

private fun pretty(v: Float) = if (v.isFinite()) String.format("%.1f", v) else "--"

@Composable
private fun RenameDialog(
    current: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
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
            Button(onClick = { onSave(text.text) }) {
                Text(stringResource(R.string.device_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.device_cancel))
            }
        }
    )
}

@Composable
private fun FsrDialog(
    s: DeviceSettings,
    onDismiss: () -> Unit,
    onChange: (DeviceSettings) -> Unit
) {
    var pin by remember { mutableStateOf(s.fsrPin.toString()) }
    var pull by remember { mutableStateOf(s.fsrPullupOhm.toString()) }
    var start by remember { mutableStateOf(s.fsrStartOhm.toString()) }
    var max by remember { mutableStateOf(s.fsrMaxOhm.toString()) }

    BaseDialog(stringResource(R.string.fsr_settings), onDismiss) {
        NumberField(stringResource(R.string.fsr_pin), pin) { pin = it }
        NumberField(stringResource(R.string.fsr_pullup), pull) { pull = it }
        NumberField(stringResource(R.string.fsr_start_threshold), start) { start = it }
        NumberField(stringResource(R.string.fsr_max_vibro), max) { max = it }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            Button(onClick = {
                onChange(
                    s.copy(
                        fsrPin = pin.toIntOrNull() ?: s.fsrPin,
                        fsrPullupOhm = pull.toIntOrNull() ?: s.fsrPullupOhm,
                        fsrStartOhm = start.toIntOrNull() ?: s.fsrStartOhm,
                        fsrMaxOhm = max.toIntOrNull() ?: s.fsrMaxOhm
                    )
                )
                onDismiss()
            }) { Text("OK") }
        }
    }
}

@Composable
private fun FlexDialog(
    s: DeviceSettings,
    currentFlexOhm: Float,
    onDismiss: () -> Unit,
    onChange: (DeviceSettings) -> Unit
) {
    var pin by remember { mutableStateOf(s.flexPin.toString()) }
    var bend by remember { mutableStateOf(s.flexBendOhm.toString()) }
    var flat by remember { mutableStateOf(s.flexFlatOhm.toString()) }

    BaseDialog(stringResource(R.string.flex_settings), onDismiss) {
        Text(
            "${stringResource(R.string.flex_current_resistance)}: ${pretty(currentFlexOhm)} Ω",
            style = MaterialTheme.typography.bodyMedium
        )
        NumberField(stringResource(R.string.flex_pin), pin) { pin = it }
        NumberField(stringResource(R.string.flex_unfolded), bend) { bend = it }
        NumberField(stringResource(R.string.flex_folded), flat) { flat = it }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            Button(onClick = {
                onChange(
                    s.copy(
                        flexPin = pin.toIntOrNull() ?: s.flexPin,
                        flexFlatOhm = flat.toIntOrNull() ?: s.flexFlatOhm,
                        flexBendOhm = bend.toIntOrNull() ?: s.flexBendOhm
                    )
                )
                onDismiss()
            }) { Text("OK") }
        }
    }
}

@Composable
private fun VibroDialog(
    s: DeviceSettings,
    onDismiss: () -> Unit,
    onChange: (DeviceSettings) -> Unit
) {
    var mode by remember { mutableStateOf(VibMode.Pulse) }

    var pin by remember { mutableStateOf(s.vibroPin.toString()) }
    var intensity by remember { mutableStateOf(s.vibroPowerPct.coerceIn(0, 100).toString()) }
    var onMs by remember { mutableStateOf("150") }
    var offMs by remember { mutableStateOf("150") }

    fun toDeviceValues(): Pair<Int, Int> {
        return if (mode == VibMode.Constant) {
            val pwr = intensity.toIntOrNull()?.coerceIn(0, 100) ?: s.vibroPowerPct
            200 to pwr
        } else {
            val on = max(1, onMs.toIntOrNull() ?: 150)
            val off = max(1, offMs.toIntOrNull() ?: 150)
            val period = (on + off).coerceAtLeast(2)
            val freq = (1000f / period).toInt().coerceAtLeast(1)
            val dutyPct = ((on * 100f) / period).toInt().coerceIn(1, 99)
            freq to dutyPct
        }
    }

    BaseDialog(stringResource(R.string.vibro_settings), onDismiss) {
        NumberField(stringResource(R.string.vibro_pin), pin) { pin = it }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.vibro_mode))
            SegmentedButtons(
                items = listOf(
                    stringResource(R.string.vibro_continuous),
                    stringResource(R.string.vibro_pulse)
                ),
                selectedIndex = if (mode == VibMode.Constant) 0 else 1,
                onSelect = { idx -> mode = if (idx == 0) VibMode.Constant else VibMode.Pulse }
            )
        }

        NumberField("${stringResource(R.string.vibro_intensity)}, %", intensity) { intensity = it }

        if (mode == VibMode.Pulse) {
            NumberField(stringResource(R.string.vibro_work_time), onMs) { onMs = it }
            NumberField(stringResource(R.string.vibro_pause_time), offMs) { offMs = it }
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            Button(onClick = {
                val (freq, pwrPct) = toDeviceValues()
                onChange(
                    s.copy(
                        vibroPin = pin.toIntOrNull() ?: s.vibroPin,
                        vibroPulseFreqHz = freq,
                        vibroPowerPct = pwrPct
                    )
                )
                onDismiss()
            }) { Text("OK") }
        }
    }
}

@Composable
private fun ServoDialog(
    s: DeviceSettings,
    currentServoDeg: Float,
    onDismiss: () -> Unit,
    onChange: (DeviceSettings) -> Unit
) {
    var pin by remember { mutableStateOf(s.servoPin.toString()) }
    var min by remember { mutableStateOf(s.servoMinDeg.toString()) }
    var max by remember { mutableStateOf(s.servoMaxDeg.toString()) }
    var manual by remember { mutableStateOf(s.servoManualMode) }
    var deg by remember { mutableStateOf(s.servoManualDeg.toString()) }

    val minV = min.toIntOrNull() ?: s.servoMinDeg
    val maxV = max.toIntOrNull() ?: s.servoMaxDeg
    var slider by remember {
        mutableStateOf(
            (deg.toIntOrNull() ?: s.servoManualDeg).coerceIn(minV, maxV).toFloat()
        )
    }

    fun pushImmediate(value: Int) {
        val minAngle = min.toIntOrNull() ?: s.servoMinDeg
        val maxAngle = max.toIntOrNull() ?: s.servoMaxDeg
        val clamped = value.coerceIn(minAngle, maxAngle)
        onChange(
            s.copy(
                servoPin = pin.toIntOrNull() ?: s.servoPin,
                servoMinDeg = minAngle,
                servoMaxDeg = maxAngle,
                servoManualMode = manual,
                servoManualDeg = clamped
            )
        )
    }

    BaseDialog(stringResource(R.string.servo_settings), onDismiss) {
        Text(
            "${stringResource(R.string.servo_current_angle)}: ${pretty(currentServoDeg)} °",
            style = MaterialTheme.typography.bodyMedium
        )
        NumberField(stringResource(R.string.servo_pin), pin) { pin = it }
        NumberField(stringResource(R.string.servo_min_angle), min) {
            min = it
            if (manual) pushImmediate(slider.toInt())
        }
        NumberField(stringResource(R.string.servo_max_angle), max) {
            max = it
            if (manual) pushImmediate(slider.toInt())
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.servo_manual_control))
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
                valueRange = (min.toIntOrNull() ?: s.servoMinDeg).toFloat()..
                        (max.toIntOrNull() ?: s.servoMaxDeg).toFloat(),
                steps = max(
                    0,
                    (max.toIntOrNull() ?: s.servoMaxDeg) - (min.toIntOrNull() ?: s.servoMinDeg)
                )
            )
            Text(
                "${stringResource(R.string.servo_manual_angle)}: ${slider.toInt()}°",
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { onDismiss() }) { Text("OK") }
        }
    }
}

@Composable
private fun BaseDialog(
    title: String,
    onDismiss: () -> Unit,
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
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.device_cancel))
            }
        }
    )
}

@Composable
private fun NumberField(label: String, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValue(it.filter { ch -> ch.isDigit() }) },
        singleLine = true,
        label = { Text(label) }
    )
}

@Composable
private fun SegmentedButtons(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
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
