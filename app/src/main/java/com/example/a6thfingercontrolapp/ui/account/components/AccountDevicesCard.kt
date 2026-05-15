package com.example.a6thfingercontrolapp.ui.account.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.a6thfingercontrolapp.R
import com.example.a6thfingercontrolapp.network.DeviceOut
import com.example.a6thfingercontrolapp.ui.account.CloudSettingsState
import com.example.a6thfingercontrolapp.ui.account.ConnectedDeviceSummary
import com.example.a6thfingercontrolapp.ui.account.connectedServerStatusText
import com.example.a6thfingercontrolapp.ui.account.currentDeviceVersionText
import com.example.a6thfingercontrolapp.ui.account.serverDeviceVersionText

/**
 * Account screen card with the current connected prosthesis and server prosthesis list.
 */
@Composable
internal fun AccountDevicesCard(
    devicesLoading: Boolean,
    devicesErrorText: String?,
    devices: List<DeviceOut>,
    cloudSettingsByDeviceId: Map<String, CloudSettingsState>,
    cloudProbeLoading: Boolean,
    isLoggedIn: Boolean,
    connectedDevice: ConnectedDeviceSummary?,
    onOpenCurrentDevice: () -> Unit,
    onOpenServerDevice: (String) -> Unit,
    onRefreshDevices: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(R.string.prosthesis_settings),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(R.string.prosthesis_settings_descr),
                style = MaterialTheme.typography.bodySmall
            )

            Text(
                stringResource(R.string.prosthesis_current_title),
                style = MaterialTheme.typography.titleSmall
            )

            if (connectedDevice == null) {
                Text(
                    stringResource(R.string.prosthesis_current_connect_first),
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                ConnectedDeviceRow(
                    device = connectedDevice,
                    isLoggedIn = isLoggedIn,
                    cloudProbeLoading = cloudProbeLoading,
                    hasServerError = !devicesErrorText.isNullOrBlank(),
                    onOpen = onOpenCurrentDevice
                )
            }

            HorizontalDivider()

            Text(
                stringResource(R.string.prosthesis_server_title),
                style = MaterialTheme.typography.titleSmall
            )

            if (!isLoggedIn) {
                Text(
                    stringResource(R.string.prosthesis_server_login_required),
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                if (devicesLoading) {
                    Text(
                        stringResource(R.string.loading),
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    if (!devicesErrorText.isNullOrBlank()) {
                        Text(
                            text = devicesErrorText,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    if (devices.isEmpty()) {
                        Text(
                            stringResource(R.string.prosthesis_no_devices),
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        devices.forEach { device ->
                            ServerDeviceRow(
                                device = device,
                                cloudState = cloudSettingsByDeviceId[device.id],
                                cloudProbeLoading = cloudProbeLoading,
                                onOpen = { onOpenServerDevice(device.id) }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onRefreshDevices) {
                        Text(stringResource(R.string.refresh))
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectedDeviceRow(
    device: ConnectedDeviceSummary,
    isLoggedIn: Boolean,
    cloudProbeLoading: Boolean,
    hasServerError: Boolean,
    onOpen: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "${stringResource(R.string.alias)}: ${device.displayName}",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "${stringResource(R.string.prosthesis_version)}: ${currentDeviceVersionText(device)}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "${stringResource(R.string.prosthesis_server_status_label)}: ${
                        connectedServerStatusText(
                            isLoggedIn = isLoggedIn,
                            matchedDevice = device.matchedServerDevice,
                            matchedState = device.matchedServerState,
                            settingsInSync = device.serverSettingsInSync,
                            isLoading = cloudProbeLoading,
                            hasServerError = hasServerError
                        )
                    }",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "${stringResource(R.string.ble_status)}: ${stringResource(R.string.prosthesis_connected)}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "${stringResource(R.string.prosthesis_address)}: ${device.address}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            OutlinedButton(onClick = onOpen) {
                Text(stringResource(R.string.device_open))
            }
        }
    }
}

@Composable
private fun ServerDeviceRow(
    device: DeviceOut,
    cloudState: CloudSettingsState?,
    cloudProbeLoading: Boolean,
    onOpen: () -> Unit
) {
    val title = device.alias?.takeIf { it.isNotBlank() } ?: device.address

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "${stringResource(R.string.prosthesis_address)}: ${device.address}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "${stringResource(R.string.prosthesis_version)}: ${
                        serverDeviceVersionText(
                            state = cloudState,
                            isLoading = cloudProbeLoading
                        )
                    }",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            OutlinedButton(onClick = onOpen) {
                Text(stringResource(R.string.device_open))
            }
        }
    }
}
