package com.example.a6thfingercontrollapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.a6thfingercontrollapp.ble.EspSettings

@Composable
fun FlexDialog(
    s: EspSettings,
    index: Int,
    currentFlexOhm: Float,
    onDismiss: () -> Unit,
    onChange: (EspSettings) -> Unit
) {
    val flexSetting = s.flexSettings[index]
    var pin by remember { mutableStateOf(flexSetting.flexPin.toString()) }
    var pull by remember { mutableStateOf(flexSetting.flexPullupOhm.toString()) }
    var straight by remember { mutableStateOf(flexSetting.flexStraightOhm.toString()) }
    var bend by remember { mutableStateOf(flexSetting.flexBendOhm.toString()) }

    BaseDialog(title = "Flex Settings", onDismiss = onDismiss) {
        Text("Current pair: $index", style = MaterialTheme.typography.bodyMedium)
        Text("Current Resistance: ${pretty(currentFlexOhm)} Ω", style = MaterialTheme.typography.bodyMedium)
        NumberField("Flex Pin", pin) { pin = it }
        NumberField("Flex Unfolded Resistance", straight) { straight = it }
        NumberField("Flex Folded Resistance", bend) { bend = it }
        NumberField("FSR Pullup", pull) { pull = it }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            Button(onClick = {
                // Изменяем только элемент массива flexSettings по индексу
                val updatedFlexSetting = flexSetting.copy(
                    flexPin = pin.toIntOrNull() ?: flexSetting.flexPin,
                    flexStraightOhm = straight.toIntOrNull() ?: flexSetting.flexStraightOhm,
                    flexBendOhm = bend.toIntOrNull() ?: flexSetting.flexBendOhm,
                    flexPullupOhm = pull.toIntOrNull() ?: flexSetting.flexPullupOhm
                )

                // Обновляем массив flexSettings
                val updatedEspSettings = s.copy(
                    flexSettings = s.flexSettings.toMutableList().apply {
                        set(index, updatedFlexSetting)  // Заменяем элемент массива
                    }.toTypedArray()
                )

                onChange(updatedEspSettings)
                onDismiss()
            }) { Text("OK") }
        }
    }
}
