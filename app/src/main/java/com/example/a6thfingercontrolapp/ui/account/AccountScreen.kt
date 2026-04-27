package com.example.a6thfingercontrolapp.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.a6thfingercontrolapp.R
import com.example.a6thfingercontrolapp.account.AccountViewModel
import com.example.a6thfingercontrolapp.auth.AuthViewModel
import com.example.a6thfingercontrolapp.auth.UiAuthState
import com.example.a6thfingercontrolapp.ble.BleViewModel
import com.example.a6thfingercontrolapp.ble.classifyBleStatus
import com.example.a6thfingercontrolapp.data.AppSettingsStore
import com.example.a6thfingercontrolapp.preferences.AppPreferencesViewModel
import com.example.a6thfingercontrolapp.ui.account.components.AccountDevicesCard

/**
 * Account main screen.
 */
@Composable
fun AccountScreen(
    vm: BleViewModel,
    authVm: AuthViewModel,
    accountVm: AccountViewModel,
    appPreferencesVm: AppPreferencesViewModel,
    onLoginClick: () -> Unit,
    onRegisterClick: () -> Unit,
    onOpenControl: () -> Unit,
    onChangePassword: (String) -> Unit
) {
    var showSettings by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val lang by appPreferencesVm.appLanguage.collectAsState()
    val theme by appPreferencesVm.appTheme.collectAsState()
    val authState by authVm.auth.collectAsState()
    val bleState by vm.state.collectAsState()
    val currentSettings by vm.activeSettings.collectAsState()
    val activeAddress by vm.activeAddress.collectAsState()
    val activeAlias by vm.activeAlias.collectAsState()

    val username = (authState as? UiAuthState.LoggedIn)?.username
    val connected = classifyBleStatus(bleState.status).transportConnected

    val settingsStore = remember { AppSettingsStore(context.applicationContext) }
    val emailState = rememberAccountEmailState(username, authVm, settingsStore)
    val deviceState = rememberAccountDevicesState(
        username = username,
        connected = connected,
        activeAddress = activeAddress,
        activeAlias = activeAlias,
        accountVm = accountVm,
        settingsStore = settingsStore
    )
    val selectableChoices = rememberSelectableCloudChoices(
        devices = deviceState.devices,
        connected = connected,
        activeAddress = activeAddress,
        activeAlias = activeAlias
    )

    LaunchedEffect(
        deviceState.showDeviceSettingsDialog,
        selectableChoices.map { it.key }.joinToString("|")
    ) {
        if (!deviceState.showDeviceSettingsDialog) return@LaunchedEffect
        if (selectableChoices.isEmpty()) {
            deviceState.showDeviceSettingsDialog = false
            return@LaunchedEffect
        }
        if (
            deviceState.dialogSelectedKey == null ||
            selectableChoices.none { it.key == deviceState.dialogSelectedKey }
        ) {
            deviceState.dialogSelectedKey = selectableChoices.first().key
            val firstRecord = deviceState.cloudStateForChoice(selectableChoices.first())?.record
            deviceState.dialogJson = firstRecord?.let { settingsToPrettyJson(it.settings) } ?: "{}"
        }
    }

    val scroll = rememberScrollState()
    Scaffold { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .verticalScroll(scroll)
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.nav_account),
                    style = MaterialTheme.typography.titleLarge
                )
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.ContextClick)
                        showSettings = true
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = stringResource(R.string.settings_title)
                    )
                }
            }

            AccountProfileHost(
                username = username,
                accountVm = accountVm,
                settingsStore = settingsStore,
                onLoginClick = onLoginClick,
                onRegisterClick = onRegisterClick,
                onLogoutClick = { authVm.logout() }
            )

            AccountDevicesCard(
                devicesLoading = deviceState.devicesLoading,
                devicesErrorText = uiErrorTextOrRaw(deviceState.devicesErrorKey),
                devices = deviceState.devices,
                selectableChoices = selectableChoices,
                cloudSettingsByDeviceId = deviceState.cloudSettingsByDeviceId,
                cloudProbeLoading = deviceState.cloudProbeLoading,
                isLoggedIn = username != null,
                connected = connected,
                activeAddress = activeAddress,
                onOpenDevice = { key ->
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.ContextClick)
                    deviceState.openCloudDialog(key, selectableChoices)
                },
                onRefreshDevices = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.ContextClick)
                    deviceState.refreshDevices()
                }
            )

            Spacer(Modifier.height(8.dp))
        }
    }

    AccountSettingsHost(
        visible = showSettings,
        lang = lang,
        theme = theme,
        username = username,
        appPreferencesVm = appPreferencesVm,
        accountVm = accountVm,
        emailState = emailState,
        onVisibleChange = { showSettings = it },
        onChangePassword = onChangePassword
    )

    AccountEmailDialogsHost(
        state = emailState,
        authVm = authVm
    )

    AccountDeviceSettingsHost(
        state = deviceState,
        selectableChoices = selectableChoices,
        connected = connected,
        currentSettings = currentSettings,
        accountVm = accountVm,
        settingsStore = settingsStore,
        onApplyPulledSettings = { vm.applySettingsFromCloud(it) },
        onOpenControl = onOpenControl
    )
}
