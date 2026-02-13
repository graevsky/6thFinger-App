package com.example.a6thfingercontrollapp.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.a6thfingercontrollapp.AuthViewModel
import com.example.a6thfingercontrollapp.BleViewModel
import com.example.a6thfingercontrollapp.R
import com.example.a6thfingercontrollapp.ble.EspSettings
import com.example.a6thfingercontrollapp.ble.FlexSettings
import com.example.a6thfingercontrollapp.ble.ServoSettings
import com.example.a6thfingercontrollapp.ble.Telemetry
import com.example.a6thfingercontrollapp.restartApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class VibMode {
    Constant,
    Pulse
}

@Composable
fun ControlScreen(vm: BleViewModel, authVm: AuthViewModel) {
    val activeAddress by vm.activeAddress.collectAsState()
    val alias by vm.activeAlias.collectAsState()

    val t by vm.state.collectAsState()
    val s by vm.activeSettings.collectAsState()
    val applied by vm.lastAppliedSettings.collectAsState()

    val pendingBoardApply by vm.pendingBoardApply.collectAsState()

    var renameOpen by remember { mutableStateOf(false) }
    var pinOpen by remember { mutableStateOf(false) }

    var fsrOpen by remember { mutableStateOf(false) }
    var flexOpen by remember { mutableStateOf(false) }
    var vibroOpen by remember { mutableStateOf(false) }
    var servoOpen by remember { mutableStateOf(false) }
    var flexIndex by remember { mutableStateOf(1) }
    var servoIndex by remember { mutableStateOf(1) }

    var flexOpenPO by remember { mutableStateOf(false) }
    var servoOpenPO by remember { mutableStateOf(false) }

    var pairsCount by remember { mutableStateOf(1) }

    var saveWarnOpen by remember { mutableStateOf(false) }
    var saveWarnText by remember { mutableStateOf("") }

    val rawStatus = t.status.lowercase()

    val pairNoStr = stringResource(R.string.pair_no)
    val flexNotSetStr = stringResource(R.string.flex_not_set)
    val servoNotSetStr = stringResource(R.string.servo_not_set)

    val connected =
        when {
            "disconnected" in rawStatus -> false
            "subscribed" in rawStatus -> true
            "tele" in rawStatus -> true
            "config" in rawStatus -> true
            "ack" in rawStatus -> true
            "auth" in rawStatus -> true
            else -> false
        }

    val dirty = (s != applied) || pendingBoardApply

    val haptic = LocalHapticFeedback.current

    var rebootOpen by remember { mutableStateOf(false) }
    var rebooting by remember { mutableStateOf(false) }

    var busy by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val telemetryEnabled by vm.telemetryEnabled.collectAsState()

    var waitTeleAfterSave by remember { mutableStateOf(false) }

    var saveTeleSig by remember { mutableStateOf(telemetrySignature(t)) }
    var saveStartedMs by remember { mutableStateOf(0L) }

    val hasTeleData = hasTelemetryData(t)

    val nowMs = SystemClock.elapsedRealtime()

    var lastTeleSig by remember { mutableStateOf(telemetrySignature(t)) }
    var lastTeleMs by remember { mutableStateOf(nowMs) }

    LaunchedEffect(t) {
        val sig = telemetrySignature(t)
        if (sig != lastTeleSig) {
            lastTeleSig = sig
            lastTeleMs = SystemClock.elapsedRealtime()
        }
    }

    LaunchedEffect(telemetryEnabled) {
        if (!telemetryEnabled) waitTeleAfterSave = false
    }

    LaunchedEffect(waitTeleAfterSave, telemetryEnabled, lastTeleMs, saveStartedMs) {
        if (!telemetryEnabled) {
            waitTeleAfterSave = false
            return@LaunchedEffect
        }
        if (waitTeleAfterSave && saveStartedMs > 0L) {
            if (lastTeleMs > saveStartedMs) {
                waitTeleAfterSave = false
            }
        }
    }

    val isTeleStale = telemetryEnabled && (nowMs - lastTeleMs) > 1200L

    val showTelePlaceholder =
        !telemetryEnabled || waitTeleAfterSave || !hasTeleData || isTeleStale

    val teleReasonText: String? =
        when {
            !telemetryEnabled -> stringResource(R.string.telemetry_disabled_short)
            waitTeleAfterSave -> stringResource(R.string.telemetry_wait_after_save)
            !hasTeleData -> stringResource(R.string.telemetry_wait)
            isTeleStale -> stringResource(R.string.telemetry_wait)
            else -> null
        }

    val telePlaceholder = stringResource(R.string.telemetry_placeholder)
    val telePretty: (Float) -> String = { v ->
        if (showTelePlaceholder) telePlaceholder else pretty(v)
    }

    LaunchedEffect(s) {
        val activePairs = (0 until 4).count { i ->
            s.flexSettings.getOrNull(i)?.flexPin != 0xFF ||
                    s.servoSettings.getOrNull(i)?.servoPin != 0xFF
        }
        pairsCount = activePairs.coerceIn(1, 4)
    }

    val doSave: () -> Unit = {
        scope.launch {
            busy = true

            saveTeleSig = telemetrySignature(t)
            saveStartedMs = SystemClock.elapsedRealtime()
            waitTeleAfterSave = telemetryEnabled

            val ok = withContext(Dispatchers.IO) {
                vm.applyAndSaveToBoard()
            }

            busy = false
            if (!ok) waitTeleAfterSave = false
        }
    }

    Scaffold(
        bottomBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.Reject)
                        scope.launch {
                            busy = true
                            vm.resetToDefaults()
                            delay(200)
                            busy = false
                        }
                    },
                    enabled = !busy
                ) {
                    Text(stringResource(R.string.device_reset))
                }

                Spacer(Modifier.width(12.dp))

                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        val issues = findIncompletePairsIssuesVisibleOnly(
                            s = s,
                            pairsCount = pairsCount
                        )

                        if (issues.isNotEmpty()) {
                            saveWarnText = issues.joinToString("\n\n") {
                                it.toUiText(pairNoStr, flexNotSetStr, servoNotSetStr)
                            }
                            saveWarnOpen = true
                        } else {
                            doSave()
                        }
                    },
                    enabled = connected && dirty && !busy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (dirty) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
                    )
                ) { Text(stringResource(R.string.device_save)) }

                Spacer(Modifier.width(12.dp))

                Button(
                    onClick = {
                        if (pairsCount < 4) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            pairsCount++
                        }
                    },
                    enabled = pairsCount < 4 && !busy
                ) {
                    Text(stringResource(R.string.add_pair))
                }
            }
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .padding(inner)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            item {
                Text(
                    stringResource(R.string.device),
                    style = MaterialTheme.typography.titleMedium
                )

                val aliasSubtitle = when {
                    activeAddress.isBlank() -> stringResource(R.string.no_active)
                    alias.isNotBlank() -> "${stringResource(R.string.alias)} $alias"
                    else -> stringResource(R.string.alias_not_set)
                }

                SettingItem(
                    title = stringResource(R.string.alias),
                    subtitle = aliasSubtitle
                ) { renameOpen = true }

                val pinSubtitle =
                    if (s.pinSet || s.authRequired || s.pinCode != 0) stringResource(R.string.pin_enabled)
                    else stringResource(R.string.pin_disabled)

                SettingItem(
                    title = stringResource(R.string.pin_title),
                    subtitle = pinSubtitle
                ) { pinOpen = true }

                SettingItem(
                    title = stringResource(R.string.device_reboot),
                    subtitle = stringResource(R.string.device_reboot_subtitle)
                ) { rebootOpen = true }

                SettingToggleItem(
                    title = stringResource(R.string.tele),
                    subtitle = if (telemetryEnabled) stringResource(R.string.tele_on) else stringResource(
                        R.string.tele_off
                    ),
                    checked = telemetryEnabled,
                    enabled = true
                ) { next ->
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    vm.setTelemetryEnabled(next)
                }

                Divider(Modifier.padding(vertical = 8.dp))
            }

            item {
                Text(
                    stringResource(R.string.fsr_n_vibro_settings),
                    style = MaterialTheme.typography.titleMedium
                )

                SettingItem(
                    title = stringResource(R.string.fsr_settings),
                    subtitle = stringResource(R.string.control_sensitivity_pullup)
                ) { fsrOpen = true }

                SettingItem(
                    title = stringResource(R.string.vibro_settings),
                    subtitle = stringResource(R.string.control_vibro_mode)
                ) { vibroOpen = true }
            }

            item {
                Text(
                    stringResource(R.string.fsr_n_force_curr_vals),
                    style = MaterialTheme.typography.titleMedium
                )
                if (teleReasonText != null) {
                    Text(teleReasonText, style = MaterialTheme.typography.bodySmall)
                }
            }

            item {
                DiagnosticRow(stringResource(R.string.control_diag_fsr_ohm), telePretty(t.fsrOhm))
                DiagnosticRow(
                    stringResource(R.string.control_diag_force_n),
                    telePretty(t.fsrForceN)
                )
                Divider(Modifier.padding(vertical = 8.dp))
            }

            item {
                Text(
                    stringResource(R.string.pair_1),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            item {
                SettingItem(
                    title = stringResource(R.string.servo_settings),
                    subtitle = stringResource(R.string.control_servo_manual)
                ) { servoOpenPO = true }

                SettingItem(
                    title = stringResource(R.string.flex_settings),
                    subtitle = stringResource(R.string.control_resistance_cal)
                ) { flexOpenPO = true }
            }

            item {
                Text(
                    stringResource(R.string.flex_n_servo_curr_vals),
                    style = MaterialTheme.typography.titleMedium
                )
                if (teleReasonText != null) {
                    Text(teleReasonText, style = MaterialTheme.typography.bodySmall)
                }
            }

            item {
                DiagnosticRow(
                    stringResource(R.string.control_diag_flex_ohm),
                    telePretty(t.flexOhm[0])
                )
                DiagnosticRow(
                    stringResource(R.string.control_diag_servo_deg),
                    telePretty(t.servoDeg[0])
                )
            }

            (1..3).forEach { pairIdx ->
                val flex = s.flexSettings.getOrNull(pairIdx)
                val servo = s.servoSettings.getOrNull(pairIdx)

                val hasFlex = flex != null && flex.flexPin != 0xFF
                val hasServo = servo != null && servo.servoPin != 0xFF

                val shouldShow = pairIdx < pairsCount || hasFlex || hasServo
                if (!shouldShow) return@forEach

                item {
                    Divider(Modifier.padding(vertical = 8.dp))
                    Text(
                        text = "${stringResource(R.string.pair_no)} ${pairIdx + 1}",
                        style = MaterialTheme.typography.titleMedium
                    )

                    SettingItem(
                        title = stringResource(R.string.servo_settings),
                        subtitle = stringResource(R.string.control_servo_manual)
                    ) {
                        servoIndex = pairIdx
                        servoOpen = true
                    }

                    SettingItem(
                        title = stringResource(R.string.flex_settings),
                        subtitle = stringResource(R.string.control_resistance_cal)
                    ) {
                        flexIndex = pairIdx
                        flexOpen = true
                    }

                    DiagnosticRow(
                        stringResource(R.string.control_diag_flex_ohm),
                        telePretty(t.flexOhm[pairIdx])
                    )
                    DiagnosticRow(
                        stringResource(R.string.control_diag_servo_deg),
                        telePretty(t.servoDeg[pairIdx])
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.Reject)
                                vm.updateActiveSettings(pairIdx) { current ->
                                    val newFlex = current.flexSettings.copyOf()
                                    val newServo = current.servoSettings.copyOf()

                                    newFlex[pairIdx] = FlexSettings(
                                        flexPin = 0xFF,
                                        flexPullupOhm = 0,
                                        flexStraightOhm = 0,
                                        flexBendOhm = 0
                                    )
                                    newServo[pairIdx] = ServoSettings(
                                        servoPin = 0xFF,
                                        servoMinDeg = 40,
                                        servoMaxDeg = 180,
                                        servoManual = 0,
                                        servoManualDeg = 90,
                                        servoMaxSpeedDegPerSec = 300.0f
                                    )

                                    current.copy(
                                        flexSettings = newFlex,
                                        servoSettings = newServo
                                    )
                                }

                                if (pairsCount > 1) pairsCount--
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "Delete pair"
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("${stringResource(R.string.pair_no)} ${pairIdx + 1}")
                        }
                    }
                }
            }
        }
    }

    if (rebootOpen) {
        AlertDialog(
            onDismissRequest = {
                if (!rebooting) rebootOpen = false
            },
            title = { Text(stringResource(R.string.device_reboot)) },
            text = { Text(stringResource(R.string.device_reboot_warning)) },
            confirmButton = {
                Button(
                    enabled = !rebooting,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        rebooting = true

                        scope.launch {
                            if (connected) {
                                vm.rebootEsp()
                                delay(250)
                            } else {
                                delay(50)
                            }

                            restartApp(context)
                        }
                    }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !rebooting,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.Reject)
                        rebootOpen = false
                    }
                ) { Text(stringResource(R.string.device_cancel)) }
            }
        )
    }

    if (renameOpen)
        RenameDialog(
            current = alias,
            onDismiss = { renameOpen = false },
            onSave = { newName ->
                vm.renameActive(newName)
                renameOpen = false
            },
            haptic = haptic
        )

    if (pinOpen)
        PinDialog(
            currentPinCode = s.pinCode,
            onDismiss = { pinOpen = false },
            onSetPin = { newPin ->
                vm.updateActiveSettings(0) { cur -> cur.copy(pinCode = newPin) }
                pinOpen = false
            },
            haptic = haptic
        )

    if (fsrOpen)
        FsrDialog(
            s = s,
            onDismiss = { fsrOpen = false },
            onChange = { next ->
                vm.updateActiveSettings(flexIndex) { next }
            },
            haptic = haptic
        )

    if (flexOpen)
        FlexDialog(
            s = s,
            index = flexIndex,
            currentFlexOhm = t.flexOhm[flexIndex],
            onDismiss = { flexOpen = false },
            onChange = { next ->
                vm.updateActiveSettings(flexIndex) { next }
            },
            haptic = haptic
        )

    if (vibroOpen)
        VibroDialog(
            s = s,
            onDismiss = { vibroOpen = false },
            onChange = { next ->
                vm.updateActiveSettings(flexIndex) { next }
            },
            haptic = haptic
        )

    if (servoOpen)
        ServoDialog(
            s = s,
            index = servoIndex,
            currentServoDeg = t.servoDeg[servoIndex],
            onDismiss = { servoOpen = false },
            onChange = { next -> vm.updateActiveSettings(servoIndex) { next } },
            onLiveChange = { next -> vm.applySettingsLive { next } },
            haptic = haptic
        )

    if (servoOpenPO)
        ServoDialog(
            s = s,
            index = 0,
            currentServoDeg = t.servoDeg[0],
            onDismiss = { servoOpenPO = false },
            onChange = { next -> vm.updateActiveSettings(0) { next } },
            onLiveChange = { next -> vm.applySettingsLive { next } },
            haptic = haptic
        )

    if (flexOpenPO)
        FlexDialog(
            s = s,
            index = 0,
            currentFlexOhm = t.flexOhm[0],
            onDismiss = { flexOpenPO = false },
            onChange = { next -> vm.updateActiveSettings(0) { next } },
            haptic = haptic
        )

    if (saveWarnOpen) {
        AlertDialog(
            onDismissRequest = { saveWarnOpen = false },
            title = { Text(stringResource(R.string.notification)) },
            text = { Text(stringResource(R.string.setup_not_full) + saveWarnText) },
            confirmButton = {
                Button(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    saveWarnOpen = false
                    doSave()
                }) {
                    Text(stringResource(R.string.continue_anyway))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.Reject)
                    saveWarnOpen = false
                }) {
                    Text(stringResource(R.string.device_cancel))
                }
            }
        )
    }

    BlockingProgressDialog(visible = busy)
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
private fun SettingToggleItem(
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
private fun DiagnosticRow(name: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(name)
        Text(value)
    }
}

fun pretty(v: Float) = if (v.isFinite()) String.format("%.1f", v) else "--"

@Composable
private fun RenameDialog(
    current: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    haptic: HapticFeedback
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
            Button(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                onSave(text.text)
            }) {
                Text(stringResource(R.string.device_save))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.Reject)
                onDismiss()
            }) { Text(stringResource(R.string.device_cancel)) }
        }
    )
}

@Composable
private fun PinDialog(
    currentPinCode: Int,
    onDismiss: () -> Unit,
    onSetPin: (Int) -> Unit,
    haptic: HapticFeedback
) {
    var pin by remember {
        mutableStateOf(if (currentPinCode == 0) "0000" else "%04d".format(currentPinCode))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pin_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(4) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.pin_hint)) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        pin = "0000"
                    }
                ) { Text(stringResource(R.string.pin_reset)) }
            }
        },
        confirmButton = {
            Button(
                enabled = pin.length == 4,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    onSetPin(pin.toIntOrNull() ?: 0)
                }
            ) { Text(stringResource(R.string.device_save)) }
        },
        dismissButton = {
            TextButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.Reject)
                onDismiss()
            }) { Text(stringResource(R.string.device_cancel)) }
        }
    )
}

@Composable
fun BaseDialog(
    title: String,
    onDismiss: () -> Unit,
    haptic: HapticFeedback,
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
            TextButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.Reject)
                onDismiss()
            }) { Text(stringResource(R.string.device_cancel)) }
        }
    )
}

@Composable
fun NumberField(label: String, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValue(it.filter { ch -> ch.isDigit() }) },
        singleLine = true,
        label = { Text(label) }
    )
}

@Composable
fun SegmentedButtons(items: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
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

private const val PIN_PLACEHOLDER = 0xFF

private data class PairIssue(
    val pairIdx: Int,
    val missing: MissingPart
)

private enum class MissingPart { Flex, Servo }

private fun findIncompletePairsIssuesVisibleOnly(
    s: EspSettings,
    pairsCount: Int
): List<PairIssue> {
    val res = mutableListOf<PairIssue>()

    fun isVisible(i: Int): Boolean {
        if (i == 0) return true

        val flexPin = s.flexSettings.getOrNull(i)?.flexPin ?: PIN_PLACEHOLDER
        val servoPin = s.servoSettings.getOrNull(i)?.servoPin ?: PIN_PLACEHOLDER

        val hasFlex = flexPin != PIN_PLACEHOLDER
        val hasServo = servoPin != PIN_PLACEHOLDER

        return (i < pairsCount) || hasFlex || hasServo
    }

    for (i in 0 until 4) {
        if (!isVisible(i)) continue

        val flexPin = s.flexSettings.getOrNull(i)?.flexPin ?: PIN_PLACEHOLDER
        val servoPin = s.servoSettings.getOrNull(i)?.servoPin ?: PIN_PLACEHOLDER

        val flexSet = flexPin != PIN_PLACEHOLDER
        val servoSet = servoPin != PIN_PLACEHOLDER

        if ((flexSet || servoSet) && (flexSet xor servoSet)) {
            res += PairIssue(
                pairIdx = i,
                missing = if (!flexSet) MissingPart.Flex else MissingPart.Servo
            )
        }
    }

    return res
}

private fun PairIssue.toUiText(
    pairNo: String,
    flexNotSet: String,
    servoNotSet: String
): String {
    val pairNum = pairIdx + 1
    return when (missing) {
        MissingPart.Flex -> "$pairNo $pairNum: $flexNotSet"
        MissingPart.Servo -> "$pairNo $pairNum: $servoNotSet"
    }
}

private fun hasTelemetryData(t: Telemetry): Boolean {
    if (t.fsrOhm.isFinite()) return true
    if (t.fsrForceN.isFinite()) return true
    if (t.flexOhm.any { it.isFinite() }) return true
    if (t.servoDeg.any { it.isFinite() }) return true
    return false
}

private fun telemetrySignature(t: Telemetry): Int {
    var h = 1
    fun addInt(x: Int) {
        h = 31 * h + x
    }

    fun addFloat(x: Float) {
        addInt(x.toRawBits())
    }

    addFloat(t.fsrOhm)
    addFloat(t.fsrForceN)

    for (i in 0 until 4) addFloat(t.flexOhm[i])
    for (i in 0 until 4) addFloat(t.servoDeg[i])

    addInt(t.vibroDuty)
    addInt(t.vibroMode)

    return h
}
