package com.example.a6thfingercontrolapp.ui.account.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.a6thfingercontrolapp.R

/**
 * Displays one prosthesis entry with address, connection state and cloud action button.
 */
@Composable
internal fun DeviceRow(
    title: String,
    address: String,
    isConnected: Boolean,
    enabled: Boolean,
    cloudStateLabel: String?,
    onOpen: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                Text(text = address, style = MaterialTheme.typography.bodySmall)

                if (!cloudStateLabel.isNullOrBlank()) {
                    Text(
                        text = cloudStateLabel,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (isConnected) {
                    Text(
                        text = stringResource(R.string.prosthesis_connected),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            OutlinedButton(onClick = onOpen, enabled = enabled) {
                Text(stringResource(R.string.device_open))
            }
        }
    }
}
