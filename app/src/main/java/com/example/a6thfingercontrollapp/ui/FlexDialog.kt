package com.example.a6thfingercontrollapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import com.example.a6thfingercontrollapp.ble.EspSettings

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

    // чтобы при смене index/настроек поля обновлялись
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

    BaseDialog(
        title = "Flex Settings",
        onDismiss = onDismiss,
        haptic = haptic
    ) {
        Text("Пара: ${index + 1}", style = MaterialTheme.typography.bodyMedium)
        Text(
            "Текущее сопротивление: ${pretty(currentFlexOhm)} Ω",
            style = MaterialTheme.typography.bodyMedium
        )

        NumberField("Flex Pin", pin) { pin = it }
        NumberField("Flex Unfolded Resistance", straight) { straight = it }
        NumberField("Flex Folded Resistance", bend) { bend = it }
        NumberField("Flex Pullup", pull) { pull = it }

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
                        flexPullupOhm = pull.toIntOrNull() ?: flexSetting.flexPullupOhm
                    )

                    val newFlexArr = s.flexSettings.copyOf()
                    newFlexArr[index] = updatedFlex

                    onChange(s.copy(flexSettings = newFlexArr))
                    onDismiss()
                }
            ) {
                Text("OK")
            }
        }
    }
}