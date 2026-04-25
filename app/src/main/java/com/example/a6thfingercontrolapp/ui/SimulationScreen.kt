package com.example.a6thfingercontrolapp.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.a6thfingercontrolapp.BleViewModel
import com.example.a6thfingercontrolapp.R
import com.example.a6thfingercontrolapp.ble.EMG_MODE_BEND_OTHER
import com.example.a6thfingercontrolapp.ble.EmgSettings
import com.example.a6thfingercontrolapp.ble.EspSettings
import com.example.a6thfingercontrolapp.ble.INPUT_SOURCE_EMG
import com.example.a6thfingercontrolapp.ble.INPUT_SOURCE_FLEX
import com.example.a6thfingercontrolapp.ble.Telemetry
import com.example.a6thfingercontrolapp.ble.classifyBleStatus

/**
 * Visual simulation screen for the prosthetic finger.
 */
@Composable
fun SimulationScreen(vm: BleViewModel) {
    val t by vm.state.collectAsState()
    val s by vm.activeSettings.collectAsState()
    val telemetryEnabled by vm.telemetryEnabled.collectAsState()
    val livePairs by vm.liveServoPairs.collectAsState()
    val unlocked by vm.controlUnlocked.collectAsState()
    val liveControlErrorKey by vm.liveControlError.collectAsState()

    val bleSession = classifyBleStatus(t.status, unlocked)
    val connected = bleSession.transportConnected
    val controlReady = bleSession.controlReady
    val liveControlAvailable = connected && controlReady
    val statusText = bleStatusUiText(t.status)
    val liveControlErrorText = liveControlErrorUiText(liveControlErrorKey)

    val availablePairs: List<Int> = remember(t, s) {
        (0 until 4).filter { idx -> pairShouldBeVisibleInSimulation(s, t, idx) }
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
    val source = s.pairInputSettings.getOrNull(selectedPair)?.inputSource ?: INPUT_SOURCE_FLEX

    val currentServoDeg = t.servoDeg.getOrNull(selectedPair) ?: Float.NaN
    val currentFlexOhm = t.flexOhm.getOrNull(selectedPair) ?: Float.NaN

    val liveAngles = remember { mutableStateMapOf<Int, Float>() }
    val liveAngle = liveAngles[selectedPair]
        ?: (if (currentServoDeg.isFinite()) currentServoDeg else s.servoSettings[selectedPair].servoManualDeg.toFloat())
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
        settings = s,
        telemetry = t,
        servoDeg = if (isLive) liveAngle else currentServoDeg,
        flexOhm = currentFlexOhm,
        fsrForceN = t.fsrForceN,
        fsrSoftThresholdN = s.fsrSoftThresholdN,
        liveEnabled = isLive,
        onLiveEnabled = { en ->
            if (liveControlAvailable) {
                vm.setServoLiveEnabled(selectedPair, en)
                if (en) vm.sendServoLiveAngle(selectedPair, liveAngle.toInt())
            }
        },
        liveAngle = liveAngle,
        onLiveAngleChange = { a ->
            liveAngles[selectedPair] = a
            if (liveControlAvailable) vm.sendServoLiveAngle(selectedPair, a.toInt())
        }
    )
}

/** Main layout of the simulation tab. */
@Composable
private fun SimulationContent(
    connected: Boolean,
    controlReady: Boolean,
    liveControlAvailable: Boolean,
    liveControlErrorText: String?,
    blockedStatusText: String,
    telemetryEnabled: Boolean,
    selectedPairIndex: Int,
    hasMultiplePairs: Boolean,
    availablePairs: List<Int>,
    onPairSelected: (Int) -> Unit,
    source: Int,
    settings: EspSettings,
    telemetry: Telemetry,
    servoDeg: Float,
    flexOhm: Float,
    fsrForceN: Float,
    fsrSoftThresholdN: Float,
    liveEnabled: Boolean,
    onLiveEnabled: (Boolean) -> Unit,
    liveAngle: Float,
    onLiveAngleChange: (Float) -> Unit
) {
    val fsrPressed = fsrForceN.isFinite() && fsrForceN >= fsrSoftThresholdN.coerceAtLeast(0.1f)

    val rawAngle = if (servoDeg.isFinite()) servoDeg else 90f
    val angleClamp = rawAngle.coerceIn(40f, 180f)
    val animAngle by animateFloatAsState(angleClamp, label = "angle")

    val tipColor by animateColorAsState(
        if (fsrPressed) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.primary,
        label = "tipColor"
    )

    val pairNo = stringResource(R.string.pair_no)
    val emgCfg = settings.emgSettings[selectedPairIndex]
    val liveUnavailableText = when {
        !connected -> stringResource(R.string.live_control_connect_required)
        !controlReady -> stringResource(R.string.live_control_unlock_required)
        else -> null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.nav_simulation),
                style = MaterialTheme.typography.titleLarge
            )

            if (hasMultiplePairs) {
                PairSelector(
                    selectedPairIndex = selectedPairIndex,
                    availablePairs = availablePairs,
                    onPairSelected = onPairSelected
                )
            } else {
                Text(
                    text = "$pairNo ${selectedPairIndex + 1}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        when {
            !connected -> StatusBanner(stringResource(R.string.disconnected))
            !controlReady -> StatusBanner(blockedStatusText)
            !telemetryEnabled -> StatusBanner(stringResource(R.string.tele_off_turn_on))
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.live_control))
                    Switch(
                        checked = liveEnabled,
                        enabled = liveControlAvailable,
                        onCheckedChange = { onLiveEnabled(it) }
                    )
                }

                Slider(
                    value = liveAngle.coerceIn(0f, 180f),
                    onValueChange = { onLiveAngleChange(it) },
                    valueRange = 0f..180f,
                    enabled = liveEnabled && liveControlAvailable
                )
                Text(
                    text = "${stringResource(R.string.servo_manual_angle)}: ${liveAngle.toInt()}°",
                    style = MaterialTheme.typography.bodySmall
                )

                if (!liveUnavailableText.isNullOrBlank()) {
                    Text(
                        text = liveUnavailableText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!liveControlErrorText.isNullOrBlank()) {
                    Text(
                        text = liveControlErrorText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                val parentMaxHeight = this.maxHeight
                val sheetHeight = (parentMaxHeight * 0.68f).coerceIn(180.dp, 360.dp)

                Box(modifier = Modifier.fillMaxSize()) {
                    RoboFinger(
                        angle = animAngle,
                        tipColor = tipColor,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(bottom = 24.dp)
                    )

                    TelemetryBottomSheet(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(sheetHeight),
                        sheetHeight = sheetHeight,
                        selectedPairIndex = selectedPairIndex,
                        source = source,
                        telemetry = telemetry,
                        emgCfg = emgCfg,
                        servoDeg = servoDeg,
                        flexOhm = flexOhm,
                        fsrForceN = fsrForceN,
                        fsrPressed = fsrPressed
                    )
                }
            }
        }
    }
}

/** Draggable bottom sheet with detailed telemetry for the selected pair. */
@Composable
private fun TelemetryBottomSheet(
    modifier: Modifier = Modifier,
    sheetHeight: androidx.compose.ui.unit.Dp,
    selectedPairIndex: Int,
    source: Int,
    telemetry: Telemetry,
    emgCfg: EmgSettings,
    servoDeg: Float,
    flexOhm: Float,
    fsrForceN: Float,
    fsrPressed: Boolean
) {
    val density = LocalDensity.current
    val peekHeight = 56.dp
    val scrollState = rememberScrollState()
    val pairNo = stringResource(R.string.pair_no)
    val title = stringResource(R.string.tele)

    val maxOffsetPx = with(density) { (sheetHeight - peekHeight).toPx() }

    var sheetOffsetPx by remember(selectedPairIndex) { mutableFloatStateOf(Float.NaN) }

    LaunchedEffect(maxOffsetPx) {
        sheetOffsetPx = if (sheetOffsetPx.isNaN()) {
            maxOffsetPx
        } else {
            sheetOffsetPx.coerceIn(0f, maxOffsetPx)
        }
    }

    val animatedOffsetPx by animateFloatAsState(
        targetValue = if (sheetOffsetPx.isNaN()) maxOffsetPx else sheetOffsetPx,
        label = "telemetrySheetOffset"
    )

    Card(
        modifier = modifier.graphicsLayer { translationY = animatedOffsetPx },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
        )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(maxOffsetPx) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                sheetOffsetPx =
                                    (sheetOffsetPx + dragAmount).coerceIn(0f, maxOffsetPx)
                            },
                            onDragEnd = {
                                sheetOffsetPx = if (sheetOffsetPx > maxOffsetPx / 2f) {
                                    maxOffsetPx
                                } else {
                                    0f
                                }
                            }
                        )
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .width(42.dp)
                        .height(4.dp)
                        .background(
                            MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(50)
                        )
                )

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 6.dp)
                )

                Text(
                    text = "$pairNo ${selectedPairIndex + 1}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("${stringResource(R.string.input_source)}: ${inputSourceLabel(source)}")
                Text(stringResource(R.string.sim_angle, prettyValue(servoDeg)))
                Text(stringResource(R.string.sim_force, prettyValue(fsrForceN)))
                Text(
                    text = stringResource(
                        if (fsrPressed) R.string.sim_fsr_pressed
                        else R.string.sim_fsr_idle
                    ),
                    color = if (fsrPressed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )

                if (source == INPUT_SOURCE_EMG) {
                    val channelCount = emgCfg.channels.coerceIn(1, 3)
                    val modeText = if (telemetry.emgMode[selectedPairIndex] >= 0) {
                        emgModeLabel(telemetry.emgMode[selectedPairIndex])
                    } else {
                        emgModeLabel(emgCfg.mode)
                    }

                    Text("${stringResource(R.string.emg_mode)}: $modeText")
                    Text(
                        "${stringResource(R.string.emg_current_event)}: ${
                            emgEventLabel(telemetry.emgEvent[selectedPairIndex])
                        }"
                    )
                    Text(
                        "${stringResource(R.string.emg_current_action)}: ${
                            emgActionLabel(telemetry.emgAction[selectedPairIndex])
                        }"
                    )
                    Text(
                        "${stringResource(R.string.emg_cooldown)}: ${
                            prettyIntValue(telemetry.emgCooldownMs[selectedPairIndex])
                        }"
                    )

                    if (emgCfg.mode == EMG_MODE_BEND_OTHER) {
                        Text(
                            "${stringResource(R.string.emg_bend_progress)}: ${
                                prettyIntValue(telemetry.emgBendProgress[selectedPairIndex])
                            }"
                        )
                        Text(
                            "${stringResource(R.string.emg_unfold_progress)}: ${
                                prettyIntValue(telemetry.emgUnfoldProgress[selectedPairIndex])
                            }"
                        )
                    }

                    repeat(channelCount) { ch ->
                        Text(
                            "${stringResource(R.string.emg_channel_value, ch + 1)}: ${
                                prettyValue(telemetry.emgChannelValue(selectedPairIndex, ch))
                            }"
                        )
                    }
                } else {
                    Text(stringResource(R.string.sim_flex, prettyValue(flexOhm)))
                }
            }
        }
    }
}

/** Warning banner shown when simulation data/control is unavailable. */
@Composable
private fun StatusBanner(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(12.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/** Dropdown for switching between configured servo pairs. */
@Composable
private fun PairSelector(
    selectedPairIndex: Int,
    availablePairs: List<Int>,
    onPairSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text("${stringResource(R.string.pair_no)} ${selectedPairIndex + 1}")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            availablePairs.forEach { idx ->
                DropdownMenuItem(
                    text = { Text("${stringResource(R.string.pair_no)} ${idx + 1}") },
                    onClick = {
                        expanded = false
                        onPairSelected(idx)
                    }
                )
            }
        }
    }
}

/** Checks whether a pair should appear in the simulation selector. */
private fun pairShouldBeVisibleInSimulation(
    settings: EspSettings,
    telemetry: Telemetry,
    pairIdx: Int
): Boolean {
    val source = settings.pairInputSettings.getOrNull(pairIdx)?.inputSource ?: INPUT_SOURCE_FLEX
    val servoSet = settings.servoSettings.getOrNull(pairIdx)?.servoPin != 0xFF
    val flexSet = settings.flexSettings.getOrNull(pairIdx)?.flexPin != 0xFF
    val emgSet = settings.emgSettings.getOrNull(pairIdx)?.activePins()?.all { it != 0xFF } == true

    val telePresent =
        telemetry.servoDeg.getOrNull(pairIdx)?.isFinite() == true ||
                telemetry.flexOhm.getOrNull(pairIdx)?.isFinite() == true ||
                telemetry.emgChannelValue(pairIdx, 0).isFinite() ||
                telemetry.emgChannelValue(pairIdx, 1).isFinite() ||
                telemetry.emgChannelValue(pairIdx, 2).isFinite() ||
                telemetry.emgEvent.getOrNull(pairIdx)?.let { it >= 0 } == true ||
                telemetry.emgAction.getOrNull(pairIdx)?.let { it >= 0 } == true

    return telePresent || servoSet || (source == INPUT_SOURCE_FLEX && flexSet) || (source == INPUT_SOURCE_EMG && emgSet)
}

/** Formats floating point telemetry for compact display. */
private fun prettyValue(v: Float) = if (v.isFinite()) "%.1f".format(v) else "--"

/** Formats optional integer telemetry for compact display. */
private fun prettyIntValue(v: Int) = if (v >= 0) v.toString() else "--"

/**
 * Simple articulated finger visualization driven by a servo angle.
 */
@Composable
private fun RoboFinger(
    angle: Float,
    tipColor: Color,
    modifier: Modifier = Modifier
) {
    val t = 1f - ((angle - 40f) / (180f - 40f))

    val baseBend = 75f * t
    val tipBend = 110f * t

    val metal = MaterialTheme.colorScheme.surfaceVariant
    val joint = MaterialTheme.colorScheme.outlineVariant

    val baseH = 80.dp
    val phalanxH = 110.dp
    val tipH = 80.dp
    val width = 38.dp

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier =
                Modifier
                    .width(width * 1.25f)
                    .height(baseH)
                    .background(metal, MaterialTheme.shapes.large)
        )

        JointDot(joint)

        Box(
            modifier = Modifier.graphicsLayer(
                rotationZ = baseBend,
                transformOrigin = TransformOrigin(0.5f, 0f)
            )
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier =
                        Modifier
                            .width(width)
                            .height(phalanxH)
                            .background(metal, MaterialTheme.shapes.large)
                )

                JointDot(joint)

                Box(
                    modifier =
                        Modifier.graphicsLayer(
                            rotationZ = tipBend,
                            transformOrigin = TransformOrigin(0.5f, 0f)
                        )
                ) {
                    Box(
                        modifier =
                            Modifier
                                .width(width * 0.9f)
                                .height(tipH)
                                .background(tipColor, MaterialTheme.shapes.large)
                    )
                }
            }
        }
    }
}

/** Small joint marker used by the finger visualization. */
@Composable
private fun JointDot(color: Color) {
    Box(
        modifier =
            Modifier
                .padding(vertical = 4.dp)
                .size(10.dp)
                .background(color, MaterialTheme.shapes.small)
    )
}
