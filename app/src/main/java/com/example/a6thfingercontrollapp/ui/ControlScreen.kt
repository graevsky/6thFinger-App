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
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.a6thfingercontrollapp.BleViewModel
import com.example.a6thfingercontrollapp.ble.DeviceSettings

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

    Scaffold(
        bottomBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = { vm.applyAndSaveToBoard() },
                    enabled = connected
                ) { Text("Сохранить") }
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
                        Text("Имя платы", style = MaterialTheme.typography.labelLarge)
                        Text(
                            if (alias.isBlank()) "Без имени" else alias,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text("Статус: ${t.status}", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.width(12.dp))
                    OutlinedButton(onClick = { renameOpen = true }, enabled = connected) {
                        Text("Переименовать")
                    }
                }
            }

            Text("Настройки", style = MaterialTheme.typography.titleMedium)

            SettingItem("FSR", "Чувствительность и подтяжка") { fsrOpen = true }
            SettingItem("Flex", "Калибровка сопротивлений") { flexOpen = true }
            SettingItem("Вибро", "Порог/частота/мощность") { vibroOpen = true }
            SettingItem("Серва", "Пределы и ручной режим") { servoOpen = true }

            Divider(Modifier.padding(vertical = 8.dp))
            Text("Диагностика", style = MaterialTheme.typography.titleMedium)
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
    if (vibroOpen) VibroDialog(s, onDismiss = { vibroOpen = false }) { next ->
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
            OutlinedButton(onClick = onClick) { Text("Открыть") }
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
        title = { Text("Переименовать плату") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("Имя") }
            )
        },
        confirmButton = {
            Button(onClick = { onSave(text.text) }) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
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

    BaseDialog("FSR настройки", onDismiss) {
        NumberField("Пин FSR", pin) { pin = it }
        NumberField("Подтяжка, Ом", pull) { pull = it }
        NumberField("Порог начала (Rstart, Ом)", start) { start = it }
        NumberField("Жёсткий нажим (Rmax, Ом)", max) { max = it }
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
    var flat by remember { mutableStateOf(s.flexFlatOhm.toString()) }
    var bend by remember { mutableStateOf(s.flexBendOhm.toString()) }

    BaseDialog("Flex настройки", onDismiss) {
        Text(
            "Текущее сопротивление: ${pretty(currentFlexOhm)} Ω",
            style = MaterialTheme.typography.bodyMedium
        )
        NumberField("Пин Flex", pin) { pin = it }
        NumberField("Разогнутый, Ω", flat) { flat = it }
        NumberField("Согнутый, Ω", bend) { bend = it }
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
    var pin by remember { mutableStateOf(s.vibroPin.toString()) }
    var freq by remember { mutableStateOf(s.vibroPulseFreqHz.toString()) }
    var thr by remember { mutableStateOf(s.vibroThreshold.toString()) }
    var pwr by remember { mutableStateOf(s.vibroPowerPct.toString()) }

    BaseDialog("Вибромотор", onDismiss) {
        NumberField("Пин вибромотора", pin) { pin = it }
        NumberField("Частота, Гц", freq) { freq = it }
        NumberField("Порог", thr) { thr = it }
        NumberField("Мощность, %", pwr) { pwr = it }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            Button(onClick = {
                onChange(
                    s.copy(
                        vibroPin = pin.toIntOrNull() ?: s.vibroPin,
                        vibroPulseFreqHz = freq.toIntOrNull() ?: s.vibroPulseFreqHz,
                        vibroThreshold = thr.toIntOrNull() ?: s.vibroThreshold,
                        vibroPowerPct = pwr.toIntOrNull() ?: s.vibroPowerPct
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
            (deg.toIntOrNull() ?: s.servoManualDeg).coerceIn(
                minV,
                maxV
            ).toFloat()
        )
    }

    BaseDialog("Серво", onDismiss) {
        Text(
            "Текущий угол: ${pretty(currentServoDeg)} °",
            style = MaterialTheme.typography.bodyMedium
        )
        NumberField("Пин серво", pin) { pin = it }
        NumberField("Мин. угол", min) { min = it }
        NumberField("Макс. угол", max) { max = it }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Ручное управление")
            Switch(checked = manual, onCheckedChange = { manual = it })
        }
        if (manual) {
            Slider(
                value = slider,
                onValueChange = { slider = it },
                valueRange = minV.toFloat()..maxV.toFloat(),
                steps = (maxV - minV).coerceAtLeast(0)
            )
            Text("Угол вручную: ${slider.toInt()}°", style = MaterialTheme.typography.bodySmall)
            deg = slider.toInt().toString()
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            Button(onClick = {
                val minAngle = min.toIntOrNull() ?: s.servoMinDeg
                val maxAngle = max.toIntOrNull() ?: s.servoMaxDeg
                val clamped = (deg.toIntOrNull() ?: s.servoManualDeg).coerceIn(minAngle, maxAngle)
                onChange(
                    s.copy(
                        servoPin = pin.toIntOrNull() ?: s.servoPin,
                        servoMinDeg = minAngle,
                        servoMaxDeg = maxAngle,
                        servoManualMode = manual,
                        servoManualDeg = clamped
                    )
                )
                onDismiss()
            }) { Text("OK") }
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
            TextButton(onClick = onDismiss) { Text("Отмена") }
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
