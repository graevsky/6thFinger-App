package com.example.a6thfingercontrolapp.ui.control.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.a6thfingercontrolapp.R
import com.example.a6thfingercontrolapp.ble.settings.EspSettings
import com.example.a6thfingercontrolapp.ui.control.pretty
import kotlin.math.roundToInt

/**
 * Dialog for configuring one flex sensor input pair.
 *
 * It supports manual resistance entry and a small calibration wizard based on
 * live telemetry from the board.
 */
@Composable
fun FlexDialog(
    s: EspSettings,
    index: Int,
    currentFlexOhm: Float,
    onDismiss: () -> Unit,
    onChange: (EspSettings) -> Unit,
    haptic: HapticFeedback
) {
    val flexSetting = s.flexSettings.getOrNull(index) ?: return

    var pin by remember(
        index,
        flexSetting.flexPin
    ) { mutableStateOf(flexSetting.flexPin.toString()) }
    var pull by remember(
        index,
        flexSetting.flexPullupOhm
    ) { mutableStateOf(flexSetting.flexPullupOhm.toString()) }
    var straight by remember(
        index,
        flexSetting.flexStraightOhm
    ) { mutableStateOf(flexSetting.flexStraightOhm.toString()) }
    var bend by remember(
        index,
        flexSetting.flexBendOhm
    ) { mutableStateOf(flexSetting.flexBendOhm.toString()) }

    var tolPct by remember(index, flexSetting.flexTolerancePct) {
        mutableStateOf(flexSetting.flexTolerancePct.toString())
    }

    var calibOpen by remember { mutableStateOf(false) }

    /** Returns a safe tolerance percentage accepted by firmware. */
    fun parsedTolPct(): Int =
        (tolPct.toIntOrNull() ?: flexSetting.flexTolerancePct).coerceIn(1, 50)

    /** Applies calibration values and stores them into the selected pair. */
    fun applyCalibration(straightOhm: Int, bendOhm: Int) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)

        val updatedFlex = flexSetting.copy(
            flexPin = pin.toIntOrNull() ?: flexSetting.flexPin,
            flexStraightOhm = straightOhm,
            flexBendOhm = bendOhm,
            flexPullupOhm = pull.toIntOrNull() ?: flexSetting.flexPullupOhm,
            flexTolerancePct = parsedTolPct()
        )

        val newFlexArr = s.flexSettings.copyOf()
        newFlexArr[index] = updatedFlex

        onChange(s.copy(flexSettings = newFlexArr))
        onDismiss()
    }

    BaseDialog(
        title = stringResource(R.string.flex_settings),
        onDismiss = onDismiss,
        haptic = haptic
    ) {
        Text(
            "${stringResource(R.string.pair_no)}: ${index + 1}",
            style = MaterialTheme.typography.bodyMedium
        )

        Text(
            "${stringResource(R.string.flex_current_resistance)}: ${pretty(currentFlexOhm)} Ω",
            style = MaterialTheme.typography.bodyMedium
        )

        OutlinedButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                calibOpen = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.flex_calibrate))
        }

        NumberField(stringResource(R.string.flex_pin), pin) { pin = it }
        NumberField(stringResource(R.string.flex_unfolded), straight) { straight = it }
        NumberField(stringResource(R.string.flex_folded), bend) { bend = it }
        NumberField(stringResource(R.string.fsr_pullup), pull) { pull = it }

        NumberField(stringResource(R.string.flex_tolerance_pct), tolPct) { tolPct = it }
        Text(
            text = stringResource(R.string.flex_tolerance_hint),
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                    val updatedFlex = flexSetting.copy(
                        flexPin = pin.toIntOrNull() ?: flexSetting.flexPin,
                        flexStraightOhm = straight.toIntOrNull() ?: flexSetting.flexStraightOhm,
                        flexBendOhm = bend.toIntOrNull() ?: flexSetting.flexBendOhm,
                        flexPullupOhm = pull.toIntOrNull() ?: flexSetting.flexPullupOhm,
                        flexTolerancePct = parsedTolPct()
                    )

                    val newFlexArr = s.flexSettings.copyOf()
                    newFlexArr[index] = updatedFlex

                    onChange(s.copy(flexSettings = newFlexArr))
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.generic_ok))
            }
        }
    }

    if (calibOpen) {
        FlexCalibrationWizardDialog(
            index = index,
            currentFlexOhm = currentFlexOhm,
            onDismiss = { calibOpen = false },
            onApply = { straightOhmFloat, bendOhmFloat ->
                calibOpen = false

                val straightInt = straightOhmFloat.roundToInt().coerceAtLeast(0)
                val bendInt = bendOhmFloat.roundToInt().coerceAtLeast(0)

                val finalStraight = minOf(straightInt, bendInt)
                val finalBend = maxOf(straightInt, bendInt)

                applyCalibration(finalStraight, finalBend)
            }
        )
    }
}
