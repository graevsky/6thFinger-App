package com.example.a6thfingercontrollapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.a6thfingercontrollapp.BleViewModel
import com.example.a6thfingercontrollapp.ble.BleDeviceUi

@Composable
fun DeviceListScreen(
    vm: BleViewModel,
    granted: Boolean,
    onSelect: (String) -> Unit
) {
    val devices by vm.devices.collectAsState()
    val state by vm.state.collectAsState()

    Scaffold { inner ->
        Column(
            Modifier
                .padding(inner)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            Text("BLE Status: ${state.status}", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { if (granted && vm.isBleReady()) vm.scan() },
                    enabled = granted
                ) { Text("Scan") }
                if (!granted) Text("Access is required")
            }
            Spacer(Modifier.height(16.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(devices, key = { it.address }) { d: BleDeviceUi ->
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(d.address) }
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                d.name.ifBlank { "Unnamed" },
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(d.address, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            if (devices.isEmpty()) {
                Spacer(Modifier.height(24.dp))
                Text("To connect select board and press scan")
            }
        }
    }
}
