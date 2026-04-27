package com.example.a6thfingercontrolapp.ui.simulation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.a6thfingercontrolapp.R
import com.example.a6thfingercontrolapp.ble.Telemetry
import com.example.a6thfingercontrolapp.ble.settings.EspSettings

/** Main layout of the simulation tab. */
@Composable
fun SimulationContent(
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
                    text = "${stringResource(R.string.servo_manual_angle)}: ${liveAngle.toInt()} deg",
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
