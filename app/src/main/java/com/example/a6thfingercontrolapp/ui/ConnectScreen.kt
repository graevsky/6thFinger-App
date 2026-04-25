package com.example.a6thfingercontrolapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.a6thfingercontrolapp.BleViewModel
import com.example.a6thfingercontrolapp.R
import com.example.a6thfingercontrolapp.ble.BleDeviceUi
import com.example.a6thfingercontrolapp.ble.classifyBleStatus
import com.example.a6thfingercontrolapp.data.LastDevice

/**
 * BLE connection screen.
 *
 * Displays the last paired device, scanned devices, connection status and PIN
 * authentication dialog when firmware requires it.
 */
@Composable
fun ConnectScreen(vm: BleViewModel, permissionsGranted: Boolean) {
    val status by vm.state.collectAsState()
    val devices by vm.devices.collectAsState()
    val connectedAddress by vm.connectedAddress.collectAsState()
    val last by vm.lastDevice.collectAsState()

    val authReq by vm.authRequired.collectAsState()
    val pinSending by vm.pinSending.collectAsState()
    val pinErrorKey by vm.pinError.collectAsState()
    val unlocked by vm.controlUnlocked.collectAsState()

    val bleSession = classifyBleStatus(status.status, unlocked)
    val haptic = LocalHapticFeedback.current

    val targetAddress = last?.address.orEmpty()
    val targetConnected = targetAddress.isNotBlank() &&
            connectedAddress.equals(targetAddress, ignoreCase = true)
    val anyDeviceConnected = bleSession.transportConnected || connectedAddress.isNotBlank()
    val controlReady = targetConnected && bleSession.controlReady
    val uiStatus = bleStatusUiText(status.status)
    val otherDeviceConnected = targetAddress.isNotBlank() &&
            connectedAddress.isNotBlank() &&
            !connectedAddress.equals(targetAddress, ignoreCase = true)
    val trafficLightState = connectionTrafficLightState(
        hasTargetDevice = targetAddress.isNotBlank(),
        targetConnected = targetConnected,
        otherDeviceConnected = otherDeviceConnected,
        transportConnected = bleSession.transportConnected,
        connecting = bleSession.connecting,
        authRequired = authReq,
        pinSending = pinSending,
        controlReady = controlReady
    )

    val lastAlias: String? =
        when (val ld = last) {
            null -> null
            else -> vm.aliasFlow(ld.address).collectAsState(initial = null).value
        }

    var pinDialogOpen by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }

    // Automatically shows the PIN dialog only while authentication is required.
    LaunchedEffect(authReq, unlocked, pinSending) {
        if (pinSending) {
            pinDialogOpen = false
        } else {
            val shouldOpen = authReq && !unlocked
            pinDialogOpen = shouldOpen
            if (!shouldOpen) pin = ""
        }
    }

    val pinErrorText = pinErrorUiText(pinErrorKey)
    LaunchedEffect(pinErrorText) {
        if (!pinErrorText.isNullOrBlank()) {
            haptic.performHapticFeedback(HapticFeedbackType.Reject)
        }
    }

    Scaffold { inner ->
        Column(
            Modifier
                .padding(inner)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ConnectionTrafficLight(
                state = trafficLightState,
                uiStatus = uiStatus,
                hasTargetDevice = targetAddress.isNotBlank(),
                otherDeviceConnected = otherDeviceConnected
            )

            LastDeviceCard(
                last = last,
                alias = lastAlias,
                isConnected = targetConnected,
                connectEnabled = !anyDeviceConnected && !bleSession.connecting,
                haptic = haptic,
                onConnect = { addr -> vm.connect(addr) },
                onDisconnect = { vm.disconnect() }
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        if (permissionsGranted && vm.isBleReady()) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            vm.scan()
                        }
                    },
                    enabled = permissionsGranted && !anyDeviceConnected && !bleSession.connecting
                ) { Text(stringResource(R.string.ble_scan)) }

                if (!permissionsGranted) {
                    Text(stringResource(R.string.ble_access_denied))
                }
            }

            Text(
                stringResource(R.string.ble_available_devices),
                style = MaterialTheme.typography.titleMedium
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(devices, key = { it.address }) { d: BleDeviceUi ->
                    val alias by vm.aliasFlow(d.address).collectAsState(initial = null)
                    DeviceItem(
                        title = (alias ?: d.name).ifBlank { alias ?: d.name },
                        address = d.address,
                        isConnected = connectedAddress.equals(d.address, ignoreCase = true),
                        enabled = !anyDeviceConnected && !bleSession.connecting,
                        onClick = {
                            vm.saveLastDevice(d.name, d.address)
                            vm.connect(d.address)
                        }
                    )
                }
            }

            if (devices.isEmpty()) {
                Text(stringResource(R.string.ble_press_to_scan))
            }
        }
    }

    if (pinDialogOpen) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.pin_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it.filter(Char::isDigit).take(4) },
                        label = { Text(stringResource(R.string.pin_hint)) },
                        singleLine = true,
                        enabled = !pinSending,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (!pinErrorText.isNullOrBlank()) {
                        Text(
                            text = pinErrorText,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    if (pinSending) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp))
                            Text(
                                text = stringResource(R.string.pin_wait),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = (pin.length == 4) && !pinSending,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        val started = vm.sendPin(pin)
                        if (started) pinDialogOpen = false
                    }
                ) {
                    Text(stringResource(R.string.generic_ok))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !pinSending,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.Reject)
                        vm.disconnect()
                        pinDialogOpen = false
                    }
                ) { Text(stringResource(R.string.device_cancel)) }
            }
        )
    }

    val waitingStage1 = !pinDialogOpen && !controlReady && !pinSending &&
            (bleSession.connecting || targetConnected)

    val showWait = pinSending || waitingStage1
    BlockingProgressDialog(visible = showWait)
}

/** Three visible connection states used by the target-device indicator. */
private enum class ConnectionTrafficLightState { Disconnected, Pending, Ready }

/**
 * Calculates the target-device traffic light without relying only on generic
 * transport status. A
 */
private fun connectionTrafficLightState(
    hasTargetDevice: Boolean,
    targetConnected: Boolean,
    otherDeviceConnected: Boolean,
    transportConnected: Boolean,
    connecting: Boolean,
    authRequired: Boolean,
    pinSending: Boolean,
    controlReady: Boolean
): ConnectionTrafficLightState {
    if (!hasTargetDevice || otherDeviceConnected) return ConnectionTrafficLightState.Disconnected
    if (targetConnected && controlReady) return ConnectionTrafficLightState.Ready

    val waitingForTarget = connecting || pinSending || authRequired ||
            (targetConnected && !controlReady) ||
            (transportConnected && !targetConnected)

    return if (waitingForTarget) {
        ConnectionTrafficLightState.Pending
    } else {
        ConnectionTrafficLightState.Disconnected
    }
}

/** Compact themed traffic-light indicator for target prosthesis connection state. */
@Composable
private fun ConnectionTrafficLight(
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
                    .clip(CircleShape)
                    .background(indicatorColor)
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

/** Maps low-level PIN error keys to localized text. */
@Composable
private fun pinErrorUiText(key: String?): String? {
    return when (key) {
        null, "" -> null
        "pin_wrong" -> stringResource(R.string.pin_wrong)
        "pin_send_failed" -> stringResource(R.string.pin_send_failed)
        "pin_timeout" -> stringResource(R.string.pin_timeout)
        "pin_bad_format" -> stringResource(R.string.pin_bad_format)
        else -> key
    }
}

/** Card with the most recently used BLE device and quick connect/disconnect. */
@Composable
private fun LastDeviceCard(
    last: LastDevice?,
    alias: String?,
    isConnected: Boolean,
    connectEnabled: Boolean,
    haptic: HapticFeedback,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.device_last), style = MaterialTheme.typography.titleMedium)
            if (last == null) {
                Text(stringResource(R.string.device_no_stored))
            } else {
                val title = (alias ?: last.name).ifBlank { alias ?: last.name }
                Text(title.ifBlank { stringResource(R.string.device_no_name) })
                Text(last.address, style = MaterialTheme.typography.bodySmall)

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isConnected) {
                        OutlinedButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.ToggleOff)
                            onDisconnect()
                        }) {
                            Text(stringResource(R.string.device_disconnect))
                        }
                    } else {
                        Button(
                            enabled = connectEnabled,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                                onConnect(last.address)
                            }
                        ) {
                            Text(stringResource(R.string.device_connect))
                        }
                    }
                }
            }
        }
    }
}

/** One scanned BLE device row in the available devices list. */
@Composable
private fun DeviceItem(
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
