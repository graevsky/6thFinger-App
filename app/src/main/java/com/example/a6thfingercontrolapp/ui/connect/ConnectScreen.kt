package com.example.a6thfingercontrolapp.ui.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.a6thfingercontrolapp.R
import com.example.a6thfingercontrolapp.ble.BleDeviceUi
import com.example.a6thfingercontrolapp.ble.BleViewModel
import com.example.a6thfingercontrolapp.ble.classifyBleStatus
import com.example.a6thfingercontrolapp.ui.common.BlockingProgressDialog
import com.example.a6thfingercontrolapp.ui.common.bleStatusUiText
import com.example.a6thfingercontrolapp.ui.connect.components.ConnectionTrafficLight
import com.example.a6thfingercontrolapp.ui.connect.components.DeviceItem
import com.example.a6thfingercontrolapp.ui.connect.components.LastDeviceCard
import com.example.a6thfingercontrolapp.ui.connect.components.PinCodeDialog

/**
 * BLE connection screen.
 */
@Composable
fun ConnectScreen(vm: BleViewModel, permissionsGranted: Boolean) {
    val status by vm.state.collectAsState()
    val devices by vm.devices.collectAsState()
    val connectedAddress by vm.connectedAddress.collectAsState()
    val last by vm.lastDevice.collectAsState()

    val authRequired by vm.authRequired.collectAsState()
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
        authRequired = authRequired,
        pinSending = pinSending,
        controlReady = controlReady
    )

    val lastAlias: String? =
        when (val lastDevice = last) {
            null -> null
            else -> vm.aliasFlow(lastDevice.address).collectAsState(initial = null).value
        }

    var pinDialogOpen by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }

    // Automatically shows the PIN dialog only while authentication is required.
    LaunchedEffect(authRequired, unlocked, pinSending) {
        if (pinSending) {
            pinDialogOpen = false
        } else {
            val shouldOpen = authRequired && !unlocked
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
            modifier = Modifier
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
                onConnect = { address -> vm.connect(address) },
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
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(devices, key = { it.address }) { device: BleDeviceUi ->
                    val alias by vm.aliasFlow(device.address).collectAsState(initial = null)
                    DeviceItem(
                        title = (alias ?: device.name).ifBlank { alias ?: device.name },
                        address = device.address,
                        isConnected = connectedAddress.equals(device.address, ignoreCase = true),
                        enabled = !anyDeviceConnected && !bleSession.connecting,
                        onClick = {
                            vm.saveLastDevice(device.name, device.address)
                            vm.connect(device.address)
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
        PinCodeDialog(
            pin = pin,
            onPinChange = { pin = it },
            pinSending = pinSending,
            pinErrorText = pinErrorText,
            haptic = haptic,
            onConfirm = {
                val started = vm.sendPin(pin)
                if (started) pinDialogOpen = false
            },
            onDismiss = {
                vm.disconnect()
                pinDialogOpen = false
            }
        )
    }

    val waitingForUnlock = !pinDialogOpen && !controlReady && !pinSending &&
            (bleSession.connecting || targetConnected)

    val showWait = pinSending || waitingForUnlock
    BlockingProgressDialog(visible = showWait)
}
