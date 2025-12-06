package com.example.a6thfingercontrollapp.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.a6thfingercontrollapp.BleViewModel
import com.example.a6thfingercontrollapp.R
import kotlin.math.max

@Composable
fun SimulationScreen(vm: BleViewModel) {
    val t by vm.state.collectAsState()
    val s by vm.activeSettings.collectAsState()

    SimulationContent(
        servoDeg = t.servoDeg,
        flexOhm = t.flexOhm,
        fsrForceN = t.fsrForceN,
        fsrSoftThresholdN = s.fsrSoftThresholdN
    )
}

@Composable
private fun SimulationContent(
    servoDeg: Float,
    flexOhm: Float,
    fsrForceN: Float,
    fsrSoftThresholdN: Float
) {
    val fsrPressed = fsrForceN.isFinite() && fsrForceN >= fsrSoftThresholdN.coerceAtLeast(0.1f)

    val angleClamp = servoDeg.coerceIn(40f, 180f)
    val animAngle by animateFloatAsState(angleClamp, label = "angle")

    val tipColor by animateColorAsState(
        if (fsrPressed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        label = "tipColor"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.nav_simulation),
            style = MaterialTheme.typography.titleLarge
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(24.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                RoboFinger(
                    angle = animAngle,
                    tipColor = tipColor
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(stringResource(R.string.sim_angle, prettyValue(servoDeg)))
                Text(stringResource(R.string.sim_flex, prettyValue(flexOhm)))
                Text(stringResource(R.string.sim_force, prettyValue(fsrForceN)))
                Text(
                    text = stringResource(
                        if (fsrPressed) R.string.sim_fsr_pressed else R.string.sim_fsr_idle
                    ),
                    color = if (fsrPressed) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

private fun prettyValue(v: Float) =
    if (v.isFinite()) "%.1f".format(v) else "--"

@Composable
private fun RoboFinger(
    angle: Float,
    tipColor: Color
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

    Column(horizontalAlignment = Alignment.CenterHorizontally) {

        Box(
            modifier = Modifier
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
                    modifier = Modifier
                        .width(width)
                        .height(phalanxH)
                        .background(metal, MaterialTheme.shapes.large)
                )

                JointDot(joint)

                Box(
                    modifier = Modifier.graphicsLayer(
                        rotationZ = tipBend,
                        transformOrigin = TransformOrigin(0.5f, 0f)
                    )
                ) {
                    Box(
                        modifier = Modifier
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
        modifier = Modifier
            .padding(vertical = 4.dp)
            .size(10.dp)
            .background(color, MaterialTheme.shapes.small)
    )
}
