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
import com.example.a6thfingercontrollapp.ble.EMG_MODE_BEND_OTHER
import com.example.a6thfingercontrollapp.ble.EmgSettings
import com.example.a6thfingercontrollapp.ble.EspSettings
import com.example.a6thfingercontrollapp.ble.INPUT_SOURCE_EMG
import com.example.a6thfingercontrollapp.ble.INPUT_SOURCE_FLEX
import com.example.a6thfingercontrollapp.ble.PairInputSettings
import com.example.a6thfingercontrollapp.ble.Telemetry
import com.example.a6thfingercontrollapp.restartApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Vibration mode presented by the simplified UI controls. */
enum class VibMode {
    Constant,
    Pulse
}

/**
 * Main control screen for device configuration.
 *
 * It edits the active settings snapshot, shows current telemetry and pushes the
 * final configuration to the board only when the user saves.
 */
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
    var emgOpen by remember { mutableStateOf(false) }

    var flexIndex by remember { mutableStateOf(0) }
    var servoIndex by remember { mutableStateOf(0) }
    var emgIndex by remember { mutableStateOf(0) }

    var pairsCount by remember { mutableStateOf(1) }

    var saveWarnOpen by remember { mutableStateOf(false) }
    var saveWarnText by remember { mutableStateOf("") }

    val rawStatus = t.status.lowercase()

    val pairNoStr = stringResource(R.string.pair_no)
    val flexNotSetStr = stringResource(R.string.flex_not_set)
    val servoNotSetStr = stringResource(R.string.servo_not_set)
    val emgNotSetStr = stringResource(R.string.emg_not_set)

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

    val livePairs by vm.liveServoPairs.collectAsState()

    var waitTeleAfterSave by remember { mutableStateOf(false) }
    var saveStartedMs by remember { mutableStateOf(0L) }

    val hasTeleData = hasTelemetryData(t)

    LaunchedEffect(telemetryEnabled) {
        if (!telemetryEnabled) waitTeleAfterSave = false
    }

    LaunchedEffect(waitTeleAfterSave, telemetryEnabled, t.rxMs, saveStartedMs) {
        if (!telemetryEnabled) {
            waitTeleAfterSave = false
            return@LaunchedEffect
        }
        if (waitTeleAfterSave && saveStartedMs > 0L) {
            if (t.rxMs > saveStartedMs) {
                waitTeleAfterSave = false
            }
        }
    }

    val nowMs = SystemClock.elapsedRealtime()

    val isTeleStale =
        telemetryEnabled &&
                hasTeleData &&
                t.rxMs > 0L &&
                (nowMs - t.rxMs) > 1200L

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
    val teleInt: (Int) -> String = { v ->
        if (showTelePlaceholder || v < 0) telePlaceholder else v.toString()
    }

    LaunchedEffect(s) {
        pairsCount = calculateVisiblePairsCount(s)
    }

    /** Saves the active settings snapshot to the ESP32 board. */
    val doSave: () -> Unit = {
        scope.launch {
            busy = true
            saveStartedMs = SystemClock.elapsedRealtime()
            waitTeleAfterSave = telemetryEnabled

            val ok = withContext(Dispatchers.IO) {
                vm.applyAndSaveToBoard()
            }

            busy = false
            if (!ok) waitTeleAfterSave = false
        }
    }

    val visiblePairs = remember(s, pairsCount) { visiblePairIndices(s, pairsCount) }

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
                                it.toUiText(pairNoStr, flexNotSetStr, servoNotSetStr, emgNotSetStr)
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
                    enabled = livePairs.isEmpty()
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

            visiblePairs.forEach { pairIdx ->
                item {
                    PairControlSection(
                        pairIdx = pairIdx,
                        settings = s,
                        telemetry = t,
                        teleReasonText = teleReasonText,
                        telePretty = telePretty,
                        teleInt = teleInt,
                        showTelePlaceholder = showTelePlaceholder,
                        onServoClick = {
                            servoIndex = pairIdx
                            servoOpen = true
                        },
                        onFlexClick = {
                            flexIndex = pairIdx
                            flexOpen = true
                        },
                        onEmgClick = {
                            emgIndex = pairIdx
                            emgOpen = true
                        },
                        onSourceChanged = { useEmg ->
                            vm.updateActiveSettings(pairIdx) { current ->
                                val nextPairInput = current.pairInputSettings.copyOf()
                                nextPairInput[pairIdx] = PairInputSettings(
                                    inputSource = if (useEmg) INPUT_SOURCE_EMG else INPUT_SOURCE_FLEX
                                )
                                current.copy(pairInputSettings = nextPairInput)
                            }
                        },
                        onDelete = if (pairIdx == 0) {
                            null
                        } else {
                            {
                                haptic.performHapticFeedback(HapticFeedbackType.Reject)
                                vm.updateActiveSettings(pairIdx) { current ->
                                    val defaults = EspSettings()
                                    val newFlex = current.flexSettings.copyOf()
                                    val newServo = current.servoSettings.copyOf()
                                    val newEmg = current.emgSettings.copyOf()
                                    val newPairInput = current.pairInputSettings.copyOf()

                                    newFlex[pairIdx] = defaults.flexSettings[pairIdx]
                                    newServo[pairIdx] = defaults.servoSettings[pairIdx]
                                    newEmg[pairIdx] = defaults.emgSettings[pairIdx]
                                    newPairInput[pairIdx] = defaults.pairInputSettings[pairIdx]

                                    current.copy(
                                        flexSettings = newFlex,
                                        servoSettings = newServo,
                                        emgSettings = newEmg,
                                        pairInputSettings = newPairInput
                                    )
                                }

                                pairsCount = calculateVisiblePairsCount(s)
                            }
                        }
                    )
                }
            }
        }
    }

    if (rebootOpen) {
        AlertDialog(
            onDismissRequest = { if (!rebooting) rebootOpen = false },
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
                ) { Text(stringResource(R.string.confirm)) }
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
            onChange = { next -> vm.updateActiveSettings(0) { next } },
            haptic = haptic
        )

    if (flexOpen)
        FlexDialog(
            s = s,
            index = flexIndex,
            currentFlexOhm = t.flexOhm[flexIndex],
            onDismiss = { flexOpen = false },
            onChange = { next -> vm.updateActiveSettings(flexIndex) { next } },
            haptic = haptic
        )

    if (emgOpen)
        EmgDialog(
            s = s,
            index = emgIndex,
            onDismiss = { emgOpen = false },
            onChange = { next -> vm.updateActiveSettings(emgIndex) { next } },
            haptic = haptic
        )

    if (vibroOpen)
        VibroDialog(
            s = s,
            onDismiss = { vibroOpen = false },
            onChange = { next -> vm.updateActiveSettings(0) { next } },
            haptic = haptic
        )

    if (servoOpen)
        ServoDialog(
            s = s,
            index = servoIndex,
            currentServoDeg = t.servoDeg[servoIndex],
            onDismiss = { servoOpen = false },
            onChange = { next -> vm.updateActiveSettings(servoIndex) { next } },
            liveEnabled = livePairs.contains(servoIndex),
            onLiveEnabledChange = { en -> vm.setServoLiveEnabled(servoIndex, en) },
            onLiveAngle = { angle -> vm.sendServoLiveAngle(servoIndex, angle) },
            haptic = haptic
        )

    if (saveWarnOpen) {
        AlertDialog(
            onDismissRequest = { saveWarnOpen = false },
            title = { Text(stringResource(R.string.notification)) },
            text = { Text(stringResource(R.string.setup_not_full) + "\n\n" + saveWarnText) },
            confirmButton = {
                Button(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    saveWarnOpen = false
                    doSave()
                }) { Text(stringResource(R.string.continue_anyway)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.Reject)
                    saveWarnOpen = false
                }) { Text(stringResource(R.string.device_cancel)) }
            }
        )
    }

    BlockingProgressDialog(visible = busy)
}

/** UI section for one flex/EMG + servo pair. */
@Composable
private fun PairControlSection(
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
    val emgSettings = settings.emgSettings.getOrNull(pairIdx) ?: EmgSettings()
    val channelCount = emgSettings.channels.coerceIn(1, 3)

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
            subtitle = emgModeLabel(emgSettings.mode)
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
        val modeText =
            if (showTelePlaceholder || telemetry.emgMode[pairIdx] < 0) {
                emgModeLabel(emgSettings.mode)
            } else {
                emgModeLabel(telemetry.emgMode[pairIdx])
            }

        val eventText =
            if (showTelePlaceholder) stringResource(R.string.telemetry_placeholder)
            else emgEventLabel(telemetry.emgEvent[pairIdx])

        val actionText =
            if (showTelePlaceholder) stringResource(R.string.telemetry_placeholder)
            else emgActionLabel(telemetry.emgAction[pairIdx])

        DiagnosticRow(stringResource(R.string.emg_mode), modeText)
        DiagnosticRow(stringResource(R.string.emg_current_event), eventText)
        DiagnosticRow(stringResource(R.string.emg_current_action), actionText)
        DiagnosticRow(
            stringResource(R.string.emg_cooldown),
            teleInt(telemetry.emgCooldownMs[pairIdx])
        )

        if (emgSettings.mode == EMG_MODE_BEND_OTHER) {
            DiagnosticRow(
                stringResource(R.string.emg_bend_progress),
                teleInt(telemetry.emgBendProgress[pairIdx])
            )
            DiagnosticRow(
                stringResource(R.string.emg_unfold_progress),
                teleInt(telemetry.emgUnfoldProgress[pairIdx])
            )
        }

        repeat(channelCount) { ch ->
            DiagnosticRow(
                stringResource(R.string.emg_channel_value, ch + 1),
                telePretty(telemetry.emgChannelValue(pairIdx, ch))
            )
        }
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

/** Clickable card that opens a settings dialog. */
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

/** Card with a switch control and description. */
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

/** Key-value telemetry row. */
@Composable
private fun DiagnosticRow(name: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(name)
        Text(value)
    }
}

/** Formats finite float values for diagnostic rows. */
fun pretty(v: Float) = if (v.isFinite()) String.format("%.1f", v) else "--"

/** Dialog for changing the locally stored BLE device alias. */
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

/** Dialog for enabling/changing the 4-digit board PIN. */
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

/**
 * Shared simple dialog shell used by the small settings dialogs.
 */
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

/** Numeric text field that keeps only digits in the editable value. */
@Composable
fun NumberField(label: String, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValue(it.filter { ch -> ch.isDigit() }) },
        singleLine = true,
        label = { Text(label) }
    )
}

/** Small two-or-more item segmented button row. */
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

/** Firmware placeholder used for disabled pins. */
private const val PIN_PLACEHOLDER = 0xFF

/** Validation issue for an incompletely configured pair. */
private data class PairIssue(
    val pairIdx: Int,
    val missing: MissingPart
)

/** Component missing from a partially configured pair. */
private enum class MissingPart { Flex, Servo, Emg }

/** Returns pair indices that should be visible in the control list. */
private fun visiblePairIndices(
    s: EspSettings,
    pairsCount: Int
): List<Int> {
    val maxVisibleIndex = maxOf(pairsCount - 1, highestConfiguredPairIndex(s))
    return (0..maxVisibleIndex.coerceIn(0, 3)).toList()
}

/** Finds the last pair index that has any non-default data. */
private fun highestConfiguredPairIndex(s: EspSettings): Int {
    var result = 0
    for (i in 0 until 4) {
        if (hasAnyPairData(s, i)) {
            result = i
        }
    }
    return result
}

/** Calculates how many pair sections should be visible initially. */
private fun calculateVisiblePairsCount(s: EspSettings): Int {
    return (highestConfiguredPairIndex(s) + 1).coerceIn(1, 4)
}

/** Checks whether a pair contains flex, servo, EMG, or source-selection data. */
private fun hasAnyPairData(s: EspSettings, pairIdx: Int): Boolean {
    val flexSet = isFlexConfigured(s, pairIdx)
    val servoSet = isServoConfigured(s, pairIdx)
    val emgSet = isEmgConfigured(s, pairIdx)
    val source = s.pairInputSettings.getOrNull(pairIdx)?.inputSource ?: INPUT_SOURCE_FLEX
    return flexSet || servoSet || emgSet || source == INPUT_SOURCE_EMG
}

/** True when the pair has an active flex pin. */
private fun isFlexConfigured(s: EspSettings, pairIdx: Int): Boolean {
    val flexPin = s.flexSettings.getOrNull(pairIdx)?.flexPin ?: PIN_PLACEHOLDER
    return flexPin != PIN_PLACEHOLDER
}

/** True when the pair has an active servo pin. */
private fun isServoConfigured(s: EspSettings, pairIdx: Int): Boolean {
    val servoPin = s.servoSettings.getOrNull(pairIdx)?.servoPin ?: PIN_PLACEHOLDER
    return servoPin != PIN_PLACEHOLDER
}

/** True when every active EMG channel has a real pin. */
private fun isEmgConfigured(s: EspSettings, pairIdx: Int): Boolean {
    val emg = s.emgSettings.getOrNull(pairIdx) ?: return false
    val activePins = emg.activePins()
    return activePins.isNotEmpty() && activePins.all { it != PIN_PLACEHOLDER }
}

/**
 * Finds visible pairs where one side of the control pair is configured but the
 * matching input/output part is still missing.
 */
private fun findIncompletePairsIssuesVisibleOnly(
    s: EspSettings,
    pairsCount: Int
): List<PairIssue> {
    val res = mutableListOf<PairIssue>()

    fun isVisible(i: Int): Boolean {
        return i in visiblePairIndices(s, pairsCount)
    }

    for (i in 0 until 4) {
        if (!isVisible(i)) continue

        val source = s.pairInputSettings.getOrNull(i)?.inputSource ?: INPUT_SOURCE_FLEX
        val flexSet = isFlexConfigured(s, i)
        val servoSet = isServoConfigured(s, i)
        val emgSet = isEmgConfigured(s, i)

        if (source == INPUT_SOURCE_EMG) {
            if ((emgSet || servoSet) && (emgSet xor servoSet)) {
                res += PairIssue(
                    pairIdx = i,
                    missing = if (!emgSet) MissingPart.Emg else MissingPart.Servo
                )
            }
        } else {
            if ((flexSet || servoSet) && (flexSet xor servoSet)) {
                res += PairIssue(
                    pairIdx = i,
                    missing = if (!flexSet) MissingPart.Flex else MissingPart.Servo
                )
            }
        }
    }

    return res
}

/** Converts a validation issue into localized user-facing text. */
private fun PairIssue.toUiText(
    pairNo: String,
    flexNotSet: String,
    servoNotSet: String,
    emgNotSet: String
): String {
    val pairNum = pairIdx + 1
    return when (missing) {
        MissingPart.Flex -> "$pairNo $pairNum: $flexNotSet"
        MissingPart.Servo -> "$pairNo $pairNum: $servoNotSet"
        MissingPart.Emg -> "$pairNo $pairNum: $emgNotSet"
    }
}

/** Checks if any telemetry field contains meaningful data. */
private fun hasTelemetryData(t: Telemetry): Boolean {
    if (t.fsrOhm.isFinite()) return true
    if (t.fsrForceN.isFinite()) return true
    if (t.flexOhm.any { it.isFinite() }) return true
    if (t.servoDeg.any { it.isFinite() }) return true
    if (t.emgCh0.any { it.isFinite() }) return true
    if (t.emgCh1.any { it.isFinite() }) return true
    if (t.emgCh2.any { it.isFinite() }) return true
    if (t.emgEvent.any { it >= 0 }) return true
    if (t.emgAction.any { it >= 0 }) return true
    return false
}
