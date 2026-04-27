package com.example.a6thfingercontrolapp.ui.simulation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.a6thfingercontrolapp.ble.BleViewModel
import com.example.a6thfingercontrolapp.ble.classifyBleStatus
import com.example.a6thfingercontrolapp.ble.settings.INPUT_SOURCE_FLEX
import com.example.a6thfingercontrolapp.ui.common.bleStatusUiText
import com.example.a6thfingercontrolapp.ui.control.liveControlErrorUiText
import com.example.a6thfingercontrolapp.ui.simulation.components.SimulationContent

/**
 * Visual simulation screen for the prosthetic finger.
 */
@Composable
fun SimulationScreen(vm: BleViewModel) {
    val telemetry by vm.state.collectAsState()
    val settings by vm.activeSettings.collectAsState()
    val telemetryEnabled by vm.telemetryEnabled.collectAsState()
    val livePairs by vm.liveServoPairs.collectAsState()
    val unlocked by vm.controlUnlocked.collectAsState()
    val liveControlErrorKey by vm.liveControlError.collectAsState()

    val bleSession = classifyBleStatus(telemetry.status, unlocked)
    val connected = bleSession.transportConnected
    val controlReady = bleSession.controlReady
    val liveControlAvailable = connected && controlReady
    val statusText = bleStatusUiText(telemetry.status)
    val liveControlErrorText = liveControlErrorUiText(liveControlErrorKey)

    val availablePairs: List<Int> = remember(telemetry, settings) {
        (0 until 4).filter { idx -> pairShouldBeVisibleInSimulation(settings, telemetry, idx) }
            .ifEmpty { listOf(0) }
    }

    var selectedPair by remember(availablePairs) { mutableStateOf(availablePairs.first()) }

    LaunchedEffect(availablePairs) {
        if (selectedPair !in availablePairs) {
            selectedPair = availablePairs.first()
        }
    }

    val hasMultiplePairs = availablePairs.size > 1
    val isLive = livePairs.contains(selectedPair)
    val source =
        settings.pairInputSettings.getOrNull(selectedPair)?.inputSource ?: INPUT_SOURCE_FLEX

    val currentServoDeg = telemetry.servoDeg.getOrNull(selectedPair) ?: Float.NaN
    val currentFlexOhm = telemetry.flexOhm.getOrNull(selectedPair) ?: Float.NaN

    val liveAngles = remember { mutableStateMapOf<Int, Float>() }
    val liveAngle = liveAngles[selectedPair]
        ?: (if (currentServoDeg.isFinite()) currentServoDeg else settings.servoSettings[selectedPair].servoManualDeg.toFloat())
            .coerceIn(0f, 180f)

    SimulationContent(
        connected = connected,
        controlReady = controlReady,
        liveControlAvailable = liveControlAvailable,
        liveControlErrorText = liveControlErrorText,
        blockedStatusText = statusText,
        telemetryEnabled = telemetryEnabled,
        selectedPairIndex = selectedPair,
        hasMultiplePairs = hasMultiplePairs,
        availablePairs = availablePairs,
        onPairSelected = { selectedPair = it },
        source = source,
        settings = settings,
        telemetry = telemetry,
        servoDeg = if (isLive) liveAngle else currentServoDeg,
        flexOhm = currentFlexOhm,
        fsrForceN = telemetry.fsrForceN,
        fsrSoftThresholdN = settings.fsrSoftThresholdN,
        liveEnabled = isLive,
        onLiveEnabled = { enabled ->
            if (liveControlAvailable) {
                vm.setServoLiveEnabled(selectedPair, enabled)
                if (enabled) vm.sendServoLiveAngle(selectedPair, liveAngle.toInt())
            }
        },
        liveAngle = liveAngle,
        onLiveAngleChange = { angle ->
            liveAngles[selectedPair] = angle
            if (liveControlAvailable) vm.sendServoLiveAngle(selectedPair, angle.toInt())
        }
    )
}
