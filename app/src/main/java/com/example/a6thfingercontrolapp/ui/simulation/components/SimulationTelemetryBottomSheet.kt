package com.example.a6thfingercontrolapp.ui.simulation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.a6thfingercontrolapp.R
import com.example.a6thfingercontrolapp.ble.Telemetry
import com.example.a6thfingercontrolapp.ble.settings.EmgSettings
import com.example.a6thfingercontrolapp.ble.settings.INPUT_SOURCE_EMG
import com.example.a6thfingercontrolapp.ui.control.emgActionLabel
import com.example.a6thfingercontrolapp.ui.control.emgEventLabel
import com.example.a6thfingercontrolapp.ui.control.emgModelLabel
import com.example.a6thfingercontrolapp.ui.control.inputSourceLabel
import com.example.a6thfingercontrolapp.ui.simulation.prettySimulationIntValue
import com.example.a6thfingercontrolapp.ui.simulation.prettySimulationValue

/** Draggable bottom sheet with detailed telemetry for the selected pair. */
@Composable
fun TelemetryBottomSheet(
    modifier: Modifier = Modifier,
    sheetHeight: Dp,
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
                Text(stringResource(R.string.sim_angle, prettySimulationValue(servoDeg)))
                Text(stringResource(R.string.sim_force, prettySimulationValue(fsrForceN)))
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
                    Text("${stringResource(R.string.emg_model)}: ${emgModelLabel()}")
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
                            prettySimulationIntValue(telemetry.emgCooldownMs[selectedPairIndex])
                        }"
                    )
                    Text(
                        "${stringResource(R.string.emg_bend_progress)}: ${
                            prettySimulationIntValue(telemetry.emgBendProgress[selectedPairIndex])
                        }"
                    )
                    Text(
                        "${stringResource(R.string.emg_unfold_progress)}: ${
                            prettySimulationIntValue(telemetry.emgUnfoldProgress[selectedPairIndex])
                        }"
                    )
                    Text(
                        "${stringResource(R.string.emg_channel_value, 1)}: ${
                            prettySimulationValue(
                                telemetry.emgChannelValue(selectedPairIndex, 0)
                            )
                        }"
                    )
                } else {
                    Text(stringResource(R.string.sim_flex, prettySimulationValue(flexOhm)))
                }
            }
        }
    }
}
