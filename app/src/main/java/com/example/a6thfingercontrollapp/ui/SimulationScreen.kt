package com.example.a6thfingercontrollapp.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.a6thfingercontrollapp.BleViewModel
import com.example.a6thfingercontrollapp.R

@Composable
fun SimulationScreen(vm: BleViewModel) {
    val t by vm.state.collectAsState()
    val s by vm.activeSettings.collectAsState()

    val servoArray = t.servoDeg
    val flexArray = t.flexOhm

    val availablePairs: List<Int> = remember(servoArray, flexArray) {
        servoArray.indices.filter { idx ->
            servoArray[idx].isFinite() || flexArray[idx].isFinite()
        }.ifEmpty { listOf(0) }
    }

    var selectedPair by remember(availablePairs) { mutableStateOf(availablePairs.first()) }

    if (selectedPair !in availablePairs) {
        selectedPair = availablePairs.first()
    }

    val hasMultiplePairs = availablePairs.size > 1

    val currentServoDeg = servoArray.getOrNull(selectedPair) ?: Float.NaN
    val currentFlexOhm = flexArray.getOrNull(selectedPair) ?: Float.NaN

    SimulationContent(
        selectedPairIndex = selectedPair,
        hasMultiplePairs = hasMultiplePairs,
        availablePairs = availablePairs,
        onPairSelected = { selectedPair = it },
        servoDeg = currentServoDeg,
        flexOhm = currentFlexOhm,
        fsrForceN = t.fsrForceN,
        fsrSoftThresholdN = s.fsrSoftThresholdN
    )
}

@Composable
private fun SimulationContent(
    selectedPairIndex: Int,
    hasMultiplePairs: Boolean,
    availablePairs: List<Int>,
    onPairSelected: (Int) -> Unit,
    servoDeg: Float,
    flexOhm: Float,
    fsrForceN: Float,
    fsrSoftThresholdN: Float
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // заголовок + справа либо надпись "Пара 1", либо выпадашка
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
                    text = "Пара ${selectedPairIndex + 1}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Card(modifier = Modifier
            .fillMaxWidth()
            .weight(1f)) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(24.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                RoboFinger(angle = animAngle, tipColor = tipColor)
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Пара ${selectedPairIndex + 1}", style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.sim_angle, prettyValue(servoDeg)))
                Text(stringResource(R.string.sim_flex, prettyValue(flexOhm)))
                Text(stringResource(R.string.sim_force, prettyValue(fsrForceN)))
                Text(
                    text =
                        stringResource(
                            if (fsrPressed) R.string.sim_fsr_pressed
                            else R.string.sim_fsr_idle
                        ),
                    color =
                        if (fsrPressed) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun PairSelector(
    selectedPairIndex: Int,
    availablePairs: List<Int>,
    onPairSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text("Пара ${selectedPairIndex + 1}")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            availablePairs.forEach { idx ->
                DropdownMenuItem(
                    text = { Text("Пара ${idx + 1}") },
                    onClick = {
                        expanded = false
                        onPairSelected(idx)
                    }
                )
            }
        }
    }
}

private fun prettyValue(v: Float) = if (v.isFinite()) "%.1f".format(v) else "--"

@Composable
private fun RoboFinger(angle: Float, tipColor: Color) {
    val t = 1f - ((angle - 40f) / (180f - 40f))

    val baseBend = 75f * t
    val tipBend = 110f * t

    val metal = MaterialTheme.colorScheme.surfaceVariant
    val joint = MaterialTheme.colorScheme.outlineVariant

    val baseH = 80.dp
    val phalanxH = 110.dp
    val tipH = 80.dp
    val width = 38.dp

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier =
                Modifier
                    .width(width * 1.25f)
                    .height(baseH)
                    .background(metal, MaterialTheme.shapes.large)
        )

        JointDot(joint)

        Box(
            modifier =
                Modifier.graphicsLayer(
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
