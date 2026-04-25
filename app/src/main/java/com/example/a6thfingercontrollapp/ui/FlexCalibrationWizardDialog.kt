package com.example.a6thfingercontrollapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.a6thfingercontrollapp.R

/** Steps of the flex sensor calibration wizard. */
private enum class FlexCalibStep {
    Intro,
    Flat,
    Bend,
    Review
}

/**
 * Guided calibration flow for flex sensor resistance values.
 *
 * The wizard captures live resistance once in the straight position and once in
 * the bent position, then sends the normalized pair back to the caller.
 */
@Composable
fun FlexCalibrationWizardDialog(
    index: Int,
    currentFlexOhm: Float,
    onDismiss: () -> Unit,
    onApply: (straightOhm: Float, bendOhm: Float) -> Unit
) {
    var step by remember { mutableStateOf(FlexCalibStep.Intro) }

    var flatOhm by remember { mutableStateOf<Float?>(null) }
    var bendOhm by remember { mutableStateOf<Float?>(null) }

    /** Calibration can capture only a valid live telemetry value. */
    fun canCaptureNow(): Boolean = currentFlexOhm.isFinite() && currentFlexOhm > 0f

    val liveText = if (currentFlexOhm.isFinite()) "${pretty(currentFlexOhm)} Ω" else "—"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.flex_calib_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "${stringResource(R.string.pair_no)}: ${index + 1}",
                    style = MaterialTheme.typography.bodySmall
                )

                when (step) {
                    FlexCalibStep.Intro -> {
                        Text(stringResource(R.string.flex_calib_intro))
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.flex_calib_live, liveText),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    FlexCalibStep.Flat -> {
                        Text(stringResource(R.string.flex_calib_step_flat))
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.flex_calib_live, liveText),
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (!canCaptureNow()) {
                            Text(
                                text = stringResource(R.string.flex_calib_wait_telemetry),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    FlexCalibStep.Bend -> {
                        Text(stringResource(R.string.flex_calib_step_bend))
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.flex_calib_live, liveText),
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (!canCaptureNow()) {
                            Text(
                                text = stringResource(R.string.flex_calib_wait_telemetry),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    FlexCalibStep.Review -> {
                        Text(stringResource(R.string.flex_calib_result_title))
                        Spacer(Modifier.height(6.dp))

                        val flatTxt = flatOhm?.let { "${pretty(it)} Ω" } ?: "—"
                        val bendTxt = bendOhm?.let { "${pretty(it)} Ω" } ?: "—"

                        Text(stringResource(R.string.flex_calib_flat_value, flatTxt))
                        Text(stringResource(R.string.flex_calib_bend_value, bendTxt))

                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.flex_calib_live, liveText),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            when (step) {
                FlexCalibStep.Intro -> {
                    Button(onClick = { step = FlexCalibStep.Flat }) {
                        Text(stringResource(R.string.flex_calib_next))
                    }
                }

                FlexCalibStep.Flat -> {
                    Button(
                        enabled = canCaptureNow(),
                        onClick = {
                            flatOhm = currentFlexOhm
                            step = FlexCalibStep.Bend
                        }
                    ) {
                        Text(stringResource(R.string.confirm))
                    }
                }

                FlexCalibStep.Bend -> {
                    Button(
                        enabled = canCaptureNow(),
                        onClick = {
                            bendOhm = currentFlexOhm
                            step = FlexCalibStep.Review
                        }
                    ) {
                        Text(stringResource(R.string.confirm))
                    }
                }

                FlexCalibStep.Review -> {
                    val canApply = (flatOhm?.isFinite() == true) && (bendOhm?.isFinite() == true)
                    Button(
                        enabled = canApply,
                        onClick = {
                            val a = flatOhm ?: return@Button
                            val b = bendOhm ?: return@Button
                            onApply(a, b)
                        }
                    ) {
                        Text(stringResource(R.string.flex_calib_apply))
                    }
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (step) {
                    FlexCalibStep.Intro -> {
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.settings_close))
                        }
                    }

                    FlexCalibStep.Flat -> {
                        TextButton(onClick = { step = FlexCalibStep.Intro }) {
                            Text(stringResource(R.string.auth_back))
                        }
                        OutlinedButton(onClick = onDismiss) {
                            Text(stringResource(R.string.settings_close))
                        }
                    }

                    FlexCalibStep.Bend -> {
                        TextButton(onClick = { step = FlexCalibStep.Flat }) {
                            Text(stringResource(R.string.auth_back))
                        }
                        OutlinedButton(onClick = onDismiss) {
                            Text(stringResource(R.string.settings_close))
                        }
                    }

                    FlexCalibStep.Review -> {
                        TextButton(onClick = { step = FlexCalibStep.Bend }) {
                            Text(stringResource(R.string.auth_back))
                        }
                        OutlinedButton(onClick = onDismiss) {
                            Text(stringResource(R.string.settings_close))
                        }
                    }
                }
            }
        }
    )
}