package com.example.a6thfingercontrolapp.ui.simulation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Simple articulated finger visualization driven by a servo angle.
 */
@Composable
fun RoboFinger(
    angle: Float,
    tipColor: Color,
    modifier: Modifier = Modifier
) {
    val t = 1f - ((angle - 40f) / (180f - 40f))

    val baseBend = 75f * t
    val tipBend = 110f * t

    val metal = MaterialTheme.colorScheme.surfaceVariant
    val joint = MaterialTheme.colorScheme.outlineVariant

    val baseHeight = 80.dp
    val phalanxHeight = 110.dp
    val tipHeight = 80.dp
    val width = 38.dp

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .width(width * 1.25f)
                .height(baseHeight)
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
                        .height(phalanxHeight)
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
                            .height(tipHeight)
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
        modifier = Modifier
            .padding(vertical = 4.dp)
            .size(10.dp)
            .background(color, MaterialTheme.shapes.small)
    )
}
