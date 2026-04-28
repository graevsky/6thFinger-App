package com.example.a6thfingercontrolapp.ui.control

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.a6thfingercontrolapp.R
import com.example.a6thfingercontrolapp.auth.AuthViewModel
import com.example.a6thfingercontrolapp.ble.BleViewModel
import com.example.a6thfingercontrolapp.ble.classifyBleStatus
import com.example.a6thfingercontrolapp.ble.settings.ESP_PAIR_COUNT
import com.example.a6thfingercontrolapp.ble.settings.EspSettings
import com.example.a6thfingercontrolapp.ble.settings.INPUT_SOURCE_EMG
import com.example.a6thfingercontrolapp.ble.settings.INPUT_SOURCE_FLEX
import com.example.a6thfingercontrolapp.ble.settings.PairInputSettings
import com.example.a6thfingercontrolapp.restartApp
import com.example.a6thfingercontrolapp.ui.common.BlockingProgressDialog
import com.example.a6thfingercontrolapp.ui.control.dialogs.EmgDialog
import com.example.a6thfingercontrolapp.ui.control.dialogs.FlexDialog
import com.example.a6thfingercontrolapp.ui.control.dialogs.FsrDialog
import com.example.a6thfingercontrolapp.ui.control.dialogs.PinDialog
import com.example.a6thfingercontrolapp.ui.control.dialogs.RenameDialog
import com.example.a6thfingercontrolapp.ui.control.dialogs.ServoDialog
import com.example.a6thfingercontrolapp.ui.control.dialogs.VibroDialog
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

    val bleSession = classifyBleStatus(t.status)
    val connected = bleSession.transportConnected

    val pairNoStr = stringResource(R.string.pair_no)
    val flexNotSetStr = stringResource(R.string.flex_not_set)
    val servoNotSetStr = stringResource(R.string.servo_not_set)
    val emgNotSetStr = stringResource(R.string.emg_not_set)

    val dirty = (s != applied) || pendingBoardApply

    val haptic = LocalHapticFeedback.current

    var rebootOpen by remember { mutableStateOf(false) }
    var rebooting by remember { mutableStateOf(false) }

    var busy by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val telemetryEnabled by vm.telemetryEnabled.collectAsState()

    val livePairs by vm.liveServoPairs.collectAsState()
    val liveControlErrorKey by vm.liveControlError.collectAsState()

    var waitTeleAfterSave by remember { mutableStateOf(false) }
    var saveStartedMs by remember { mutableStateOf(0L) }

    val hasTeleData = hasTelemetryData(t)
    val liveControlErrorText = liveControlErrorUiText(liveControlErrorKey)

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
                            pairsCount = pairsCount,
                            pairNo = pairNoStr,
                            flexNotSet = flexNotSetStr,
                            servoNotSet = servoNotSetStr,
                            emgNotSet = emgNotSetStr
                        )

                        if (issues.isNotEmpty()) {
                            saveWarnText = issues.joinToString("\n\n")
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
                        if (pairsCount < ESP_PAIR_COUNT) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            pairsCount++
                        }
                    },
                    enabled = pairsCount < ESP_PAIR_COUNT && !busy
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

                if (!liveControlErrorText.isNullOrBlank()) {
                    Text(
                        text = liveControlErrorText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
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

