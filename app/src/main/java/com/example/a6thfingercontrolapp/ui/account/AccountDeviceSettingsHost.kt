package com.example.a6thfingercontrolapp.ui.account

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import com.example.a6thfingercontrolapp.R
import com.example.a6thfingercontrolapp.account.AccountViewModel
import com.example.a6thfingercontrolapp.ble.settings.EspSettings
import com.example.a6thfingercontrolapp.data.AppSettingsStore
import com.example.a6thfingercontrolapp.data.DeviceSettingsRecord
import com.example.a6thfingercontrolapp.network.DeviceOut
import com.example.a6thfingercontrolapp.ui.account.dialogs.ConnectedDeviceDialog
import com.example.a6thfingercontrolapp.ui.account.dialogs.ServerDeviceDialog
import kotlinx.coroutines.launch

/**
 * Account prosthesis dialogs host.
 */
@Composable
internal fun AccountDeviceSettingsHost(
    state: AccountDevicesUiState,
    devices: List<DeviceOut>,
    connectedDevice: ConnectedDeviceSummary?,
    isLoggedIn: Boolean,
    currentSettings: EspSettings,
    accountVm: AccountViewModel,
    settingsStore: AppSettingsStore,
    onApplyPulledSettings: (EspSettings) -> Unit,
    onOpenControl: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val errFailedPullSettings = stringResource(R.string.err_failed_pull_settings)
    val errFailedPushSettings = stringResource(R.string.err_failed_push_settings)
    val errFailedDeleteDevice = stringResource(R.string.err_failed_delete_device)

    val selectedServerDevice = devices.firstOrNull { it.id == state.dialogSelectedKey }
    val selectedServerState = state.cloudStateForDeviceId(selectedServerDevice?.id)
    val dialogErrorText = uiErrorTextOrRaw(state.dialogErrorKey ?: selectedServerState?.errorKey)

    fun closeDialog() {
        haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
        state.closeDialog()
    }

    fun updateCloudRecord(deviceId: String, record: DeviceSettingsRecord) {
        state.cloudSettingsByDeviceId =
            state.cloudSettingsByDeviceId.toMutableMap().apply {
                put(
                    deviceId,
                    CloudSettingsState(
                        checked = true,
                        record = record,
                        errorKey = null
                    )
                )
            }
    }

    if (state.showDeviceSettingsDialog && state.dialogSelectedKey == CURRENT_DEVICE_DIALOG_KEY) {
        ConnectedDeviceDialog(
            isLoggedIn = isLoggedIn,
            device = connectedDevice,
            json = state.dialogJson,
            isBusy = state.dialogBusy,
            canPullFromServer = connectedDevice?.matchedServerDevice != null &&
                    connectedDevice.matchedServerState?.record != null,
            error = dialogErrorText,
            onDismiss = { closeDialog() },
            onPushToServer = {
                val currentDevice = connectedDevice ?: return@ConnectedDeviceDialog
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                scope.launch {
                    state.dialogBusy = true
                    state.dialogErrorKey = null
                    try {
                        val targetDevice = currentDevice.matchedServerDevice ?: accountVm.createDevice(
                            address = currentDevice.address,
                            alias = currentDevice.cloudAlias
                        ).also {
                            mergeDeviceIntoList(state, settingsStore, it)
                        }

                        val record = accountVm.pushDeviceSettings(targetDevice.id, currentSettings)
                        updateCloudRecord(targetDevice.id, record)
                        state.dialogJson = settingsToPrettyJson(currentSettings)
                    } catch (e: Exception) {
                        state.dialogErrorKey = e.message ?: errFailedPushSettings
                    } finally {
                        state.dialogBusy = false
                    }
                }
            },
            onPullFromServer = {
                val currentDevice = connectedDevice ?: return@ConnectedDeviceDialog
                val matchedDevice = currentDevice.matchedServerDevice ?: return@ConnectedDeviceDialog
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                scope.launch {
                    state.dialogBusy = true
                    state.dialogErrorKey = null
                    try {
                        val result = refreshCloudSettingsState(
                            device = matchedDevice,
                            force = true,
                            accountVm = accountVm,
                            state = state
                        )
                        val record = result.record
                        if (record != null) {
                            onApplyPulledSettings(record.settings)
                            state.closeDialog()
                            onOpenControl()
                        } else {
                            state.dialogErrorKey = "prosthesis_no_settings_on_server"
                        }
                    } catch (e: Exception) {
                        state.dialogErrorKey = e.message ?: errFailedPullSettings
                    } finally {
                        state.dialogBusy = false
                    }
                }
            }
        )
    }

    if (state.showDeviceSettingsDialog && selectedServerDevice != null) {
        ServerDeviceDialog(
            isLoggedIn = isLoggedIn,
            device = selectedServerDevice,
            cloudState = selectedServerState,
            connectedDevice = connectedDevice,
            json = state.dialogJson,
            isBusy = state.dialogBusy,
            error = dialogErrorText,
            onDismiss = { closeDialog() },
            onDeleteDevice = {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                scope.launch {
                    state.dialogBusy = true
                    state.dialogErrorKey = null
                    try {
                        accountVm.deleteDevice(selectedServerDevice.id)
                        removeDeviceFromList(state, settingsStore, selectedServerDevice.id)
                        state.closeDialog()
                    } catch (e: Exception) {
                        state.dialogErrorKey = e.message ?: errFailedDeleteDevice
                    } finally {
                        state.dialogBusy = false
                    }
                }
            },
            onPullToConnected = {
                val currentDevice = connectedDevice
                if (currentDevice == null) {
                    haptic.performHapticFeedback(HapticFeedbackType.Reject)
                    state.showConnectWarning = true
                    return@ServerDeviceDialog
                }

                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                scope.launch {
                    state.dialogBusy = true
                    state.dialogErrorKey = null
                    try {
                        val result = refreshCloudSettingsState(
                            device = selectedServerDevice,
                            force = true,
                            accountVm = accountVm,
                            state = state
                        )
                        val record = result.record
                        if (record != null) {
                            onApplyPulledSettings(record.settings)
                            state.closeDialog()
                            onOpenControl()
                        } else {
                            state.dialogErrorKey = "prosthesis_no_settings_on_server"
                        }
                    } catch (e: Exception) {
                        state.dialogErrorKey = e.message ?: errFailedPullSettings
                    } finally {
                        state.dialogBusy = false
                    }
                }
            },
            onPushFromConnected = {
                val currentDevice = connectedDevice
                if (currentDevice == null) {
                    haptic.performHapticFeedback(HapticFeedbackType.Reject)
                    state.showConnectWarning = true
                    return@ServerDeviceDialog
                }

                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                scope.launch {
                    state.dialogBusy = true
                    state.dialogErrorKey = null
                    try {
                        val record = accountVm.pushDeviceSettings(
                            deviceId = selectedServerDevice.id,
                            settings = currentSettings
                        )
                        updateCloudRecord(selectedServerDevice.id, record)
                        state.dialogJson = settingsToPrettyJson(record.settings)
                    } catch (e: Exception) {
                        state.dialogErrorKey = e.message ?: errFailedPushSettings
                    } finally {
                        state.dialogBusy = false
                    }
                }
            }
        )
    }

    if (state.showConnectWarning) {
        AlertDialog(
            onDismissRequest = {
                haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                state.showConnectWarning = false
            },
            title = { Text(stringResource(R.string.prosthesis_not_connected_title)) },
            text = { Text(stringResource(R.string.prosthesis_not_connected_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        state.showConnectWarning = false
                    }
                ) { Text(stringResource(R.string.generic_ok)) }
            }
        )
    }
}
