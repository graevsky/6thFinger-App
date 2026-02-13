package com.example.a6thfingercontrollapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.a6thfingercontrollapp.BleViewModel
import com.example.a6thfingercontrollapp.R
import com.example.a6thfingercontrollapp.ble.BleDeviceUi
import com.example.a6thfingercontrollapp.data.LastDevice

@Composable
fun ConnectScreen(vm: BleViewModel, permissionsGranted: Boolean) {
    val status by vm.state.collectAsState()
    val devices by vm.devices.collectAsState()
    val last by vm.lastDevice.collectAsState()

    val authReq by vm.authRequired.collectAsState()
    val pinSending by vm.pinSending.collectAsState()
    val pinErrorKey by vm.pinError.collectAsState()
    val unlocked by vm.controlUnlocked.collectAsState()

    val rawStatus = status.status.lowercase()
    val haptic = LocalHapticFeedback.current

    val isConnected =
        when {
            "disconnected" in rawStatus -> false
            "subscribed" in rawStatus -> true
            "tele" in rawStatus -> true
            "config" in rawStatus -> true
            "ack" in rawStatus -> true
            "auth" in rawStatus -> true
            else -> false
        }

    val uiStatus = bleStatusUiText(status.status)

    val lastAlias: String? =
        when (val ld = last) {
            null -> null
            else -> vm.aliasFlow(ld.address).collectAsState(initial = null).value
        }

    var pinDialogOpen by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }

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
            Text(
                "${stringResource(R.string.ble_status)}: $uiStatus",
                style = MaterialTheme.typography.titleMedium
            )

            LastDeviceCard(
                last = last,
                alias = lastAlias,
                isConnected = isConnected,
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
                    enabled = permissionsGranted && !isConnected
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
                        isConnected = isConnected,
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

    val statusKey = status.status.lowercase()
    val waitingStage1 = !pinDialogOpen && !unlocked && !pinSending && (
            statusKey == "connecting" ||
                    statusKey == "discovering" ||
                    statusKey == "subscribed" ||
                    statusKey == "config_updated" ||
                    isConnected
            )

    val showWait = pinSending || waitingStage1
    BlockingProgressDialog(visible = showWait)
}

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

@Composable
private fun LastDeviceCard(
    last: LastDevice?,
    alias: String?,
    isConnected: Boolean,
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
                        Button(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                            onConnect(last.address)
                        }) {
                            Text(stringResource(R.string.device_connect))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceItem(title: String, address: String, isConnected: Boolean, onClick: () -> Unit) {
    Card(
        Modifier
            .fillMaxWidth()
            .then(if (!isConnected) Modifier.clickable { onClick() } else Modifier)
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
