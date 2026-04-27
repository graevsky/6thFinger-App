package com.example.a6thfingercontrolapp.ui.connect.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.a6thfingercontrolapp.R

/** Scanned BLE device row in the available devices list. */
@Composable
fun DeviceItem(
    title: String,
    address: String,
    isConnected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        Modifier
            .fillMaxWidth()
            .then(if (enabled && !isConnected) Modifier.clickable { onClick() } else Modifier)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                title.ifBlank { stringResource(R.string.device_no_name) },
                style = MaterialTheme.typography.titleMedium
            )
            Text(address, style = MaterialTheme.typography.bodySmall)
            if (isConnected) {
                Text(
                    stringResource(R.string.device_already_connected),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
