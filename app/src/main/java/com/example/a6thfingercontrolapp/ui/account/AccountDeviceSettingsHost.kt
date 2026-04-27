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
import com.example.a6thfingercontrolapp.ui.account.dialogs.DeviceSettingsDialog
import kotlinx.coroutines.launch

/**
 * Device settings dialog host.
 */
@Composable
internal fun AccountDeviceSettingsHost(
    state: AccountDevicesUiState,
    selectableChoices: List<CloudDeviceChoice>,
    connected: Boolean,
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

    val dialogSelectedChoice = selectableChoices.firstOrNull { it.key == state.dialogSelectedKey }
    val dialogSelectedState = state.cloudStateForChoice(dialogSelectedChoice)
    val dialogErrorText = uiErrorTextOrRaw(state.dialogErrorKey ?: dialogSelectedState?.errorKey)

    if (state.showDeviceSettingsDialog && dialogSelectedChoice != null) {
        DeviceSettingsDialog(
            devices = selectableChoices,
            selectedKey = dialogSelectedChoice.key,
            selectedState = dialogSelectedState,
            json = state.dialogJson,
            isBusy = state.dialogBusy,
            isPullEnabled = connected,
            error = dialogErrorText,
            onDismiss = {
                haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                state.showDeviceSettingsDialog = false
            },
            onSelectedKeyChange = { key ->
                state.dialogSelectedKey = key
                state.dialogErrorKey = null
                val choice = selectableChoices.firstOrNull { it.key == key }
                val record = state.cloudStateForChoice(choice)?.record
                state.dialogJson = record?.let { settingsToPrettyJson(it.settings) } ?: "{}"
            },
            onPreviewClick = {
                val choice = dialogSelectedChoice
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                scope.launch {
                    state.dialogBusy = true
                    state.dialogErrorKey = null
                    try {
                        val device = resolveCloudChoice(choice, accountVm, state, settingsStore)
                        val result = refreshCloudSettingsState(
                            device = device,
                            force = true,
                            accountVm = accountVm,
                            state = state
                        )
                        val record = result.record
                        if (record != null) {
                            state.dialogJson = settingsToPrettyJson(record.settings)
                        } else {
                            state.dialogJson = "{}"
                            state.dialogErrorKey = "prosthesis_no_settings_on_server"
                        }
                    } catch (e: Exception) {
                        state.dialogErrorKey = e.message ?: errFailedPullSettings
                    } finally {
                        state.dialogBusy = false
                    }
                }
            },
            onPullClick = {
                if (!connected) {
                    haptic.performHapticFeedback(HapticFeedbackType.Reject)
                    state.showConnectWarning = true
                    return@DeviceSettingsDialog
                }

                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                scope.launch {
                    state.dialogBusy = true
                    state.dialogErrorKey = null
                    try {
                        val device =
                            resolveCloudChoice(
                                dialogSelectedChoice,
                                accountVm,
                                state,
                                settingsStore
                            )
                        val result = refreshCloudSettingsState(
                            device = device,
                            force = true,
                            accountVm = accountVm,
                            state = state
                        )
                        val record = result.record
                        if (record != null) {
                            state.dialogJson = settingsToPrettyJson(record.settings)
                            onApplyPulledSettings(record.settings)
                            state.showDeviceSettingsDialog = false
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
            onPushClick = {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                scope.launch {
                    state.dialogBusy = true
                    state.dialogErrorKey = null
                    try {
                        val device =
                            resolveCloudChoice(
                                dialogSelectedChoice,
                                accountVm,
                                state,
                                settingsStore
                            )
                        val record = accountVm.pushDeviceSettings(device.id, currentSettings)
                        state.cloudSettingsByDeviceId =
                            state.cloudSettingsByDeviceId.toMutableMap().apply {
                                put(
                                    device.id,
                                    CloudSettingsState(
                                        checked = true,
                                        record = record,
                                        errorKey = null
                                    )
                                )
                            }
                        state.dialogSelectedKey = device.id
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
