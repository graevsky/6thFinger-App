package com.example.a6thfingercontrollapp.ui

import androidx.compose.foundation.Image
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.a6thfingercontrollapp.AuthViewModel
import com.example.a6thfingercontrollapp.BleViewModel
import com.example.a6thfingercontrollapp.MainActivity
import com.example.a6thfingercontrollapp.R

@Composable
fun StartScreen(
    bleVm: BleViewModel,
    authVm: AuthViewModel,
    onLoginClick: () -> Unit,
    onRegisterClick: () -> Unit,
    onContinueAsGuest: () -> Unit
) {
    val lang by bleVm.appLanguage.collectAsState()
    var showLangDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_logo),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.size(160.dp)
            )

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(Modifier.height(16.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onLoginClick
                ) {
                    Text(text = stringResource(R.string.auth_login))
                }

                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onRegisterClick
                ) {
                    Text(text = stringResource(R.string.auth_register))
                }

                TextButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onContinueAsGuest
                ) {
                    Text(text = stringResource(R.string.auth_continue_guest))
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            TextButton(
                onClick = { showLangDialog = true },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(
                    text = stringResource(R.string.settings_language) +
                            ": " + lang.uppercase()
                )
            }
        }
    }

    if (showLangDialog) {
        val activity = LocalContext.current as? MainActivity
        SettingsDialog(
            currentLang = lang,
            onDismiss = { showLangDialog = false },
            onSelect = { newLang ->
                bleVm.setAppLanguage(newLang)
                showLangDialog = false
                activity?.recreateApp()
            }
        )
    }
}

@Composable
fun LoginScreen(
    vm: AuthViewModel,
    initialUsername: String,
    onBack: () -> Unit
) {
    val error by vm.error.collectAsState()

    var username by remember { mutableStateOf(initialUsername) }
    var password by remember { mutableStateOf("") }

    Box(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.auth_sign_in),
                style = MaterialTheme.typography.headlineSmall
            )

            OutlinedTextField(
                value = username,
                onValueChange = { username = it.trim() },
                singleLine = true,
                label = { Text(stringResource(R.string.auth_username)) },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                label = { Text(stringResource(R.string.auth_password)) },
                modifier = Modifier.fillMaxWidth()
            )

            if (!error.isNullOrBlank()) {
                Text(
                    text = error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(onClick = {
                    vm.clearError()
                    onBack()
                }) {
                    Text(stringResource(R.string.auth_back))
                }
                Button(
                    onClick = { vm.login(username, password) },
                    enabled = username.isNotBlank() && password.isNotBlank()
                ) {
                    Text(stringResource(R.string.auth_login))
                }
            }
        }
    }
}

@Composable
fun RegisterScreen(
    vm: AuthViewModel,
    onBack: () -> Unit,
    onRegistered: (String) -> Unit
) {
    val error by vm.error.collectAsState()

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Box(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.auth_register),
                style = MaterialTheme.typography.headlineSmall
            )

            OutlinedTextField(
                value = username,
                onValueChange = { username = it.trim() },
                singleLine = true,
                label = { Text(stringResource(R.string.auth_username)) },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                label = { Text(stringResource(R.string.auth_password)) },
                modifier = Modifier.fillMaxWidth()
            )

            if (!error.isNullOrBlank()) {
                Text(
                    text = error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(onClick = {
                    vm.clearError()
                    onBack()
                }) {
                    Text(stringResource(R.string.auth_back))
                }
                Button(
                    onClick = {
                        val normalized = username.trim().lowercase()
                        vm.register(normalized, password) {
                            onRegistered(normalized)
                        }
                    },
                    enabled = username.isNotBlank() && password.isNotBlank()
                ) {
                    Text(stringResource(R.string.auth_register))
                }
            }
        }
    }
}
