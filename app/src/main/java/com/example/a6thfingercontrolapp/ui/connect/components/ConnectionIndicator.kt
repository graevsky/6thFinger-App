package com.example.a6thfingercontrolapp.ui.connect.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.a6thfingercontrolapp.R
import com.example.a6thfingercontrolapp.ui.connect.ConnectionTrafficLightState

/** "Traffic light" indicator for target prosthesis connection state. */
@Composable
fun ConnectionTrafficLight(
    state: ConnectionTrafficLightState,
    uiStatus: String,
    hasTargetDevice: Boolean,
    otherDeviceConnected: Boolean
) {
    val indicatorColor = when (state) {
        ConnectionTrafficLightState.Disconnected -> MaterialTheme.colorScheme.error
        ConnectionTrafficLightState.Pending -> MaterialTheme.colorScheme.tertiary
        ConnectionTrafficLightState.Ready -> MaterialTheme.colorScheme.primary
    }

    val title = when {
        !hasTargetDevice -> stringResource(R.string.connection_indicator_no_target)
        otherDeviceConnected -> stringResource(R.string.connection_indicator_other_connected)
        state == ConnectionTrafficLightState.Ready -> stringResource(R.string.connection_indicator_ready)
        state == ConnectionTrafficLightState.Pending -> stringResource(R.string.connection_indicator_waiting)
        else -> stringResource(R.string.connection_indicator_disconnected)
    }

    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(indicatorColor, CircleShape)
            )

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = indicatorColor
                )
                Text(
                    text = stringResource(R.string.connection_indicator_status, uiStatus),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
