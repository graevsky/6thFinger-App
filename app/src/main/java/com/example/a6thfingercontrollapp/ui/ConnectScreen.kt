package com.example.a6thfingercontrollapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.a6thfingercontrollapp.BleViewModel
import com.example.a6thfingercontrollapp.ble.BleDeviceUi
import com.example.a6thfingercontrollapp.data.LastDevice

@Composable
fun ConnectScreen(
    vm: BleViewModel,
    permissionsGranted: Boolean
) {
    val status by vm.state.collectAsState()
    val devices by vm.devices.collectAsState()
    val last by vm.lastDevice.collectAsState()

    val uiStatus = remember(status.status) {
        when {
            status.status.contains("discover", ignoreCase = true) -> "Connecting"
            status.status.contains("subscribed", ignoreCase = true) -> "Connected"
            else -> status.status
        }
    }

    val isConnected = remember(uiStatus) {
        uiStatus.contains("Connected", ignoreCase = true)
    }

    val lastAlias: String? = when (val ld = last) {
        null -> null
        else -> vm.aliasFlow(ld.address).collectAsState(initial = null).value
    }


    Scaffold { inner ->
        Column(
            Modifier
                .padding(inner)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Статус: $uiStatus", style = MaterialTheme.typography.titleMedium)

            LastDeviceCard(
                last = last,
                alias = lastAlias,
                isConnected = isConnected,
                onConnect = { addr -> vm.connect(addr) },
                onDisconnect = { vm.disconnect() }
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { if (permissionsGranted && vm.isBleReady()) vm.scan() },
                    enabled = permissionsGranted && !isConnected
                ) { Text("Сканировать") }
                if (!permissionsGranted) Text("Разрешения не выданы")
            }

            Text("Доступные устройства", style = MaterialTheme.typography.titleMedium)

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(devices, key = { it.address }) { d: BleDeviceUi ->
                    val alias by vm.aliasFlow(d.address).collectAsState(initial = null)
                    DeviceItem(
                        title = (alias ?: d.name).ifBlank { alias ?: d.name },
                        address = d.address,
                        isConnected = isConnected,
                        onClick = {
                            vm.saveLastDevice(d.name, d.address)
                            vm.connect(d.address)
                        }
                    )
                }
            }

            if (devices.isEmpty()) {
                Text("Press scan to scan :P")
            }
        }
    }
}

@Composable
private fun LastDeviceCard(
    last: LastDevice?,
    alias: String?,
    isConnected: Boolean,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Последнее устройство", style = MaterialTheme.typography.titleMedium)
            if (last == null) {
                Text("Нет сохранённых устройств")
            } else {
                val title = (alias ?: last.name).ifBlank { alias ?: last.name }
                Text(title.ifBlank { "Unnamed" })
                Text(last.address, style = MaterialTheme.typography.bodySmall)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isConnected) {
                        OutlinedButton(onClick = onDisconnect) { Text("Отключиться") }
                    } else {
                        Button(onClick = { onConnect(last.address) }) { Text("Подключиться") }
                    }
                }
            }
        }
    }
}


@Composable
private fun DeviceItem(
    title: String,
    address: String,
    isConnected: Boolean,
    onClick: () -> Unit
) {
    Card(
        Modifier
            .fillMaxWidth()
            .then(if (!isConnected) Modifier.clickable { onClick() } else Modifier)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title.ifBlank { "Unnamed" }, style = MaterialTheme.typography.titleMedium)
            Text(address, style = MaterialTheme.typography.bodySmall)
            if (isConnected) {
                Text("Уже подключено", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}