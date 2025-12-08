package com.example.a6thfingercontrollapp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.a6thfingercontrollapp.AuthViewModel
import com.example.a6thfingercontrollapp.BleViewModel
import com.example.a6thfingercontrollapp.MainActivity
import com.example.a6thfingercontrollapp.R
import com.example.a6thfingercontrollapp.UiAuthState
import com.example.a6thfingercontrollapp.ble.EspSettings
import com.example.a6thfingercontrollapp.network.DeviceOut
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun AccountScreen(
        vm: BleViewModel,
        authVm: AuthViewModel,
        onLoginClick: () -> Unit,
        onRegisterClick: () -> Unit,
        onOpenControl: () -> Unit
) {
    var showSettings by remember { mutableStateOf(false) }

    val lang by vm.appLanguage.collectAsState()
    val authState by authVm.auth.collectAsState()
    val bleState by vm.state.collectAsState()
    val currentSettings by vm.activeSettings.collectAsState()

    val username: String? =
            when (authState) {
                is UiAuthState.LoggedIn -> (authState as UiAuthState.LoggedIn).username
                else -> null
            }

    val scope = rememberCoroutineScope()

    var devices by remember { mutableStateOf<List<DeviceOut>>(emptyList()) }
    var devicesLoading by remember { mutableStateOf(false) }
    var devicesError by remember { mutableStateOf<String?>(null) }

    var showDeviceSettingsDialog by remember { mutableStateOf(false) }
    var selectedDevice by remember { mutableStateOf<DeviceOut?>(null) }
    var dialogJson by remember { mutableStateOf("{}") }
    var dialogError by remember { mutableStateOf<String?>(null) }
    var showConnectWarning by remember { mutableStateOf(false) }

    val activeAddress by vm.activeAddress.collectAsState()
    val activeAlias by vm.activeAlias.collectAsState()

    val connected =
            bleState.status.contains("Subscribed", true) ||
                    bleState.status.contains("Connected", true)

    LaunchedEffect(username, activeAddress) {
        if (username == null) {
            devices = emptyList()
            devicesError = null
            return@LaunchedEffect
        }

        devicesLoading = true
        devicesError = null
        try {
            if (activeAddress.isNotEmpty()) {
                try {
                    authVm.ensureDevice(
                            address = activeAddress,
                            alias = activeAlias.ifBlank { null }
                    )
                } catch (e: Exception) {
                    devicesError = e.message
                }
            }

            val list = authVm.fetchDevices()
            devices = list
        } catch (e: Exception) {
            devicesError = e.message ?: "Failed to load devices"
            devices = emptyList()
        } finally {
            devicesLoading = false
        }
    }

    Scaffold { inner ->
        Column(
                Modifier.padding(inner).padding(16.dp).fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                        stringResource(R.string.nav_account),
                        style = MaterialTheme.typography.titleLarge
                )
                IconButton(onClick = { showSettings = true }) {
                    Icon(
                            Icons.Default.Build,
                            contentDescription = stringResource(R.string.settings_title)
                    )
                }
            }

            Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
            ) {
                Box(modifier = Modifier.size(120.dp).clip(CircleShape)) {
                    Image(
                            painter = painterResource(id = R.drawable.ic_avatar_placeholder),
                            contentDescription = stringResource(R.string.account_avatar)
                    )
                }

                Spacer(Modifier.height(16.dp))

                Text(
                        text = username ?: stringResource(R.string.auth_guest),
                        style =
                                MaterialTheme.typography.headlineSmall.copy(
                                        fontWeight = FontWeight.Bold
                                )
                )

                Spacer(Modifier.height(12.dp))

                if (username == null) {
                    Text(
                            text = stringResource(R.string.auth_guest_hint),
                            style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(modifier = Modifier.weight(1f), onClick = onLoginClick) {
                            Text(stringResource(R.string.auth_login))
                        }
                        Button(modifier = Modifier.weight(1f), onClick = onRegisterClick) {
                            Text(stringResource(R.string.auth_register))
                        }
                    }
                } else {
                    Button(onClick = { authVm.logout() }) {
                        Text(stringResource(R.string.auth_logout))
                    }
                }

                Spacer(Modifier.height(24.dp))

                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                                stringResource(R.string.prosthesis_settings),
                                style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                                text = stringResource(R.string.prosthesis_settings_descr),
                                style = MaterialTheme.typography.bodySmall
                        )

                        if (devicesLoading) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                    text = stringResource(R.string.loading),
                                    style = MaterialTheme.typography.bodySmall
                            )
                        } else if (devicesError != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                    text = devicesError ?: "",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                            )
                        } else if (devices.isEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                    text = stringResource(R.string.prosthesis_no_devices),
                                    style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            Spacer(Modifier.height(8.dp))
                            devices.forEach { dev ->
                                DeviceRow(
                                        device = dev,
                                        isConnected = connected,
                                        enabled = username != null,
                                        onOpen = {
                                            selectedDevice = dev
                                            dialogError = null
                                            dialogJson = settingsToPrettyJson(currentSettings)
                                            showDeviceSettingsDialog = true
                                        }
                                )
                            }
                        }

                        if (username != null) {
                            Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                            ) {
                                OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                devicesLoading = true
                                                devicesError = null
                                                try {
                                                    val list = authVm.fetchDevices()
                                                    devices = list
                                                } catch (e: Exception) {
                                                    devicesError =
                                                            e.message ?: "Failed to load devices"
                                                    devices = emptyList()
                                                } finally {
                                                    devicesLoading = false
                                                }
                                            }
                                        }
                                ) { Text(stringResource(R.string.refresh)) }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    if (showSettings) {
        val activity = LocalContext.current as? MainActivity

        SettingsDialog(
                currentLang = lang,
                onDismiss = { showSettings = false },
                onSelect = { newLang: String ->
                    vm.setAppLanguage(newLang)

                    val currentAuth = authState
                    if (currentAuth is UiAuthState.LoggedIn) {
                        authVm.updateLanguageRemote(newLang)
                    }

                    showSettings = false
                    activity?.recreateApp()
                }
        )
    }

    if (showDeviceSettingsDialog && selectedDevice != null) {
        val dev = selectedDevice!!
        val noSettingsMsg = stringResource(R.string.prosthesis_no_settings_on_server)

        DeviceSettingsDialog(
                device = dev,
                json = dialogJson,
                isPullEnabled = connected,
                error = dialogError,
                onDismiss = { showDeviceSettingsDialog = false },
                onPullClick = {
                    if (!connected) {
                        showConnectWarning = true
                        return@DeviceSettingsDialog
                    }
                    scope.launch {
                        dialogError = null
                        try {
                            val fromServer = authVm.pullDeviceSettings(dev.id)
                            if (fromServer != null) {
                                dialogJson = settingsToPrettyJson(fromServer)
                                vm.applySettingsFromCloud(fromServer)
                            } else {
                                dialogError = noSettingsMsg
                            }
                        } catch (e: Exception) {
                            dialogError = e.message ?: "Failed to pull settings"
                        }
                    }
                },
                onPushClick = {
                    scope.launch {
                        dialogError = null
                        try {
                            authVm.pushDeviceSettings(dev.id, currentSettings)
                            dialogJson = settingsToPrettyJson(currentSettings)
                        } catch (e: Exception) {
                            dialogError = e.message ?: "Failed to push settings"
                        }
                    }
                }
        )
    }

    if (showConnectWarning) {
        AlertDialog(
                onDismissRequest = { showConnectWarning = false },
                title = { Text(stringResource(R.string.prosthesis_not_connected_title)) },
                text = { Text(stringResource(R.string.prosthesis_not_connected_message)) },
                confirmButton = {
                    TextButton(onClick = { showConnectWarning = false }) {
                        Text(stringResource(R.string.generic_ok))
                    }
                }
        )
    }
}

@Composable
fun SettingsDialog(currentLang: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.settings_app)) },
            text = {
                Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                ) {
                    Text("${stringResource(R.string.settings_language)}:")

                    LanguageOptionRow(
                            title = stringResource(R.string.settings_russian),
                            selected = currentLang == "ru",
                            onClick = { onSelect("ru") }
                    )

                    LanguageOptionRow(
                            title = stringResource(R.string.settings_english),
                            selected = currentLang == "en",
                            onClick = { onSelect("en") }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_close)) }
            }
    )
}

@Composable
private fun LanguageOptionRow(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(
            Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun DeviceRow(
        device: DeviceOut,
        isConnected: Boolean,
        enabled: Boolean,
        onOpen: () -> Unit
) {
    val title = device.alias ?: device.address

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
                modifier = Modifier.padding(8.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)

                if (device.alias == null) {
                    Text(text = device.address, style = MaterialTheme.typography.bodySmall)
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

@Composable
private fun DeviceSettingsDialog(
        device: DeviceOut,
        json: String,
        isPullEnabled: Boolean,
        error: String?,
        onDismiss: () -> Unit,
        onPullClick: () -> Unit,
        onPushClick: () -> Unit
) {
    AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                        text =
                                stringResource(
                                        R.string.prosthesis_settings_for_device,
                                        device.alias ?: device.address
                                )
                )
            },
            text = {
                Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                            text = stringResource(R.string.prosthesis_settings_dialog_hint),
                            style = MaterialTheme.typography.bodyMedium
                    )

                    OutlinedTextField(
                            value = json,
                            onValueChange = {},
                            label = { Text("JSON") },
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth().height(200.dp)
                    )

                    if (error != null) {
                        Text(
                                text = error,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                                modifier = Modifier.weight(1f),
                                enabled = isPullEnabled,
                                onClick = onPullClick
                        ) { Text(text = stringResource(R.string.prosthesis_pull)) }
                        Button(modifier = Modifier.weight(1f), onClick = onPushClick) {
                            Text(text = stringResource(R.string.prosthesis_push))
                        }
                    }

                    if (!isPullEnabled) {
                        Text(
                                text = stringResource(R.string.prosthesis_connect_hint),
                                style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_close)) }
            }
    )
}

private fun settingsToPrettyJson(s: EspSettings): String {
    return try {
        val raw = s.toJsonString()
        val obj = JSONObject(raw)
        obj.toString(2)
    } catch (_: Exception) {
        s.toJsonString()
    }
}
