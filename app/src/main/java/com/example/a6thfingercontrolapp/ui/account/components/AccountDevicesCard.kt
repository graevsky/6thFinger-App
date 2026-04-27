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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.a6thfingercontrolapp.R
import com.example.a6thfingercontrolapp.network.DeviceOut
import com.example.a6thfingercontrolapp.ui.account.CloudDeviceChoice
import com.example.a6thfingercontrolapp.ui.account.CloudSettingsState

/**
 * Account screen list of known prosthesis devices.
 */
@Composable
internal fun AccountDevicesCard(
    devicesLoading: Boolean,
    devicesErrorText: String?,
    devices: List<DeviceOut>,
    selectableChoices: List<CloudDeviceChoice>,
    cloudSettingsByDeviceId: Map<String, CloudSettingsState>,
    cloudProbeLoading: Boolean,
    isLoggedIn: Boolean,
    connected: Boolean,
    activeAddress: String,
    onOpenDevice: (String) -> Unit,
    onRefreshDevices: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(R.string.prosthesis_settings),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(R.string.prosthesis_settings_descr),
                style = MaterialTheme.typography.bodySmall
            )

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

                val localOnlyChoice = selectableChoices.firstOrNull { it.device == null }
                val hasAnyChoice = selectableChoices.isNotEmpty()

                if (!hasAnyChoice) {
                    Text(
                        stringResource(R.string.prosthesis_no_devices),
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    localOnlyChoice?.let { choice ->
                        DeviceRow(
                            title = choice.title,
                            address = choice.address,
                            isConnected = choice.isConnectedDevice,
                            enabled = isLoggedIn,
                            cloudStateLabel = stringResource(R.string.prosthesis_local_device_not_registered),
                            onOpen = { onOpenDevice(choice.key) }
                        )
                    }

                    devices.forEach { dev ->
                        val choice = selectableChoices.firstOrNull { it.device?.id == dev.id }
                        val cloudState = cloudSettingsByDeviceId[dev.id]
                        val cloudStatus = when {
                            cloudState?.record != null ->
                                stringResource(
                                    R.string.prosthesis_server_settings_saved_version,
                                    cloudState.record.version
                                )

                            cloudState?.checked == true ->
                                stringResource(R.string.prosthesis_server_settings_missing)

                            !cloudState?.errorKey.isNullOrBlank() ->
                                stringResource(R.string.prosthesis_server_status_unknown)

                            cloudProbeLoading -> stringResource(R.string.loading)
                            else -> stringResource(R.string.prosthesis_server_status_unknown)
                        }

                        DeviceRow(
                            title = choice?.title ?: (dev.alias ?: dev.address),
                            address = dev.address,
                            isConnected = connected && activeAddress.equals(
                                dev.address,
                                ignoreCase = true
                            ),
                            enabled = isLoggedIn,
                            cloudStateLabel = cloudStatus,
                            onOpen = { onOpenDevice(dev.id) }
                        )
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

                if (!connected) {
                    Text(
                        stringResource(R.string.prosthesis_connect_hint),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
