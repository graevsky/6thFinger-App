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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

@Composable
fun AccountScreen(
    vm: BleViewModel,
    authVm: AuthViewModel,
    onLoginClick: () -> Unit,
    onRegisterClick: () -> Unit
) {
    var showSettings by remember { mutableStateOf(false) }
    var showDeviceSettingsDialog by remember { mutableStateOf(false) }

    val lang by vm.appLanguage.collectAsState()
    val authState by authVm.auth.collectAsState()

    val username: String? = when (authState) {
        is UiAuthState.LoggedIn -> (authState as UiAuthState.LoggedIn).username
        else -> null
    }

    Scaffold { inner ->
        Column(
            Modifier
                .padding(inner)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                Modifier
                    .fillMaxWidth(),
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
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_avatar_placeholder),
                        contentDescription = stringResource(R.string.account_avatar)
                    )
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = username ?: stringResource(R.string.auth_guest),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
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
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = onLoginClick
                        ) {
                            Text(stringResource(R.string.auth_login))
                        }
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = onRegisterClick
                        ) {
                            Text(stringResource(R.string.auth_register))
                        }
                    }
                } else {
                    Button(onClick = { authVm.logout() }) {
                        Text(stringResource(R.string.auth_logout))
                    }
                }

                Spacer(Modifier.height(24.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(
                                enabled = username != null,
                                onClick = { showDeviceSettingsDialog = true }
                            ) {
                                Text(stringResource(R.string.device_open))
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
                    authVm.scheduleAppSettingsUpload(
                        mapOf("language" to newLang)
                    )
                }

                showSettings = false
                activity?.recreateApp()
            }
        )
    }

    if (showDeviceSettingsDialog) {
        DeviceSettingsDialog(
            onDismiss = { showDeviceSettingsDialog = false },
            enabled = username != null
        )
    }
}

@Composable
fun SettingsDialog(
    currentLang: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
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
private fun LanguageOptionRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun DeviceSettingsDialog(
    onDismiss: () -> Unit,
    enabled: Boolean
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.prosthesis_settings)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.prosthesis_settings_dialog_hint),
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = enabled,
                        onClick = {
                            // TODO: получить настройки протеза с сервера
                        }
                    ) {
                        Text(text = stringResource(R.string.prosthesis_pull))
                    }
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = enabled,
                        onClick = {
                            // TODO: отправить настройки протеза на сервер
                        }
                    ) {
                        Text(text = stringResource(R.string.prosthesis_push))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_close))
            }
        }
    )
}
