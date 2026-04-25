package com.example.a6thfingercontrolapp.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.a6thfingercontrolapp.AuthViewModel
import com.example.a6thfingercontrolapp.BleViewModel
import com.example.a6thfingercontrolapp.R
import com.example.a6thfingercontrolapp.utils.PasswordPolicy
import com.example.a6thfingercontrolapp.utils.uiErrorText

/**
 * First screen shown to unauthenticated users.
 *
 * It provides login/register/guest entry points, language switching and a link
 * to the quick start guide.
 */
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
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    val guideUrl =
        "https://docs.google.com/document/d/1MEejkdQEGTkvxDuX7fgXnzfzSTgcVONKTlj-WCBkAp0/edit?usp=sharing" // temp hardcode :/

    /** Opens the external guide from the landing screen. */
    fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
        } catch (_: Exception) {
        }
    }

    val guideTitle = "Quick start guide"
    val guideSubtitle = "Open step-by-step instructions"

    Scaffold { inner ->
        Box(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
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
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(Modifier.height(16.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            onLoginClick()
                        }
                    ) { Text(stringResource(R.string.auth_login)) }

                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            onRegisterClick()
                        }
                    ) { Text(stringResource(R.string.auth_register)) }

                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            onContinueAsGuest()
                        }
                    ) { Text(stringResource(R.string.auth_continue_guest)) }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                openUrl(guideUrl)
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = guideTitle,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = guideSubtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                TextButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        showLangDialog = true
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(text = stringResource(R.string.settings_language) + ": " + lang.uppercase())
                }
            }
        }
    }

    if (showLangDialog) {
        val activity = LocalContext.current as? Activity
        SettingsDialog(
            currentLang = lang,
            onDismiss = { showLangDialog = false },
            onSelect = { newLang ->
                bleVm.setAppLanguage(newLang)
                showLangDialog = false
                activity?.recreate()
            }
        )
    }
}

/**
 * Login form that delegates authentication to AuthViewModel.
 */
@Composable
fun LoginScreen(
    vm: AuthViewModel,
    initialUsername: String,
    onBack: () -> Unit,
    onForgotPassword: (String) -> Unit
) {
    val rawError by vm.error.collectAsState()
    val error = uiErrorText(rawError) ?: rawError

    var username by remember { mutableStateOf(initialUsername) }
    var password by remember { mutableStateOf("") }
    var pwVisible by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    Scaffold { inner ->
        Box(
            Modifier
                .padding(inner)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
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
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
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
                    visualTransformation = if (pwVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { pwVisible = !pwVisible }) {
                            Icon(
                                imageVector = if (pwVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (pwVisible)
                                    stringResource(R.string.auth_password_hide)
                                else
                                    stringResource(R.string.auth_password_show)
                            )
                        }
                    },
                    label = { Text(stringResource(R.string.auth_password)) },
                    modifier = Modifier.fillMaxWidth()
                )

                if (!error.isNullOrBlank()) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                }

                TextButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        vm.clearError()
                        onForgotPassword(username.trim())
                    },
                    modifier = Modifier.align(Alignment.End)
                ) { Text(stringResource(R.string.auth_forgot_password)) }

                Spacer(Modifier.height(8.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                            vm.clearError()
                            onBack()
                        }
                    ) { Text(stringResource(R.string.auth_back)) }

                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                            vm.login(username, password)
                        },
                        enabled = username.isNotBlank() && password.isNotBlank()
                    ) { Text(stringResource(R.string.auth_login)) }
                }
            }
        }
    }
}

/**
 * Registration form with local password policy validation.
 */
@Composable
fun RegisterScreen(
    vm: AuthViewModel,
    onBack: () -> Unit,
    onRegistered: (String) -> Unit,
    onGoToLogin: (String) -> Unit
) {
    val rawError by vm.error.collectAsState()
    val errorKey = rawError?.trim()?.lowercase()
    val error = uiErrorText(rawError) ?: rawError

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var pwVisible by remember { mutableStateOf(false) }
    val rules = remember(password) { PasswordPolicy.check(password) }

    val haptic = LocalHapticFeedback.current

    Scaffold { inner ->
        Box(
            Modifier
                .padding(inner)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
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
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
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
                    visualTransformation = if (pwVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { pwVisible = !pwVisible }) {
                            Icon(
                                imageVector = if (pwVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (pwVisible)
                                    stringResource(R.string.auth_password_hide)
                                else
                                    stringResource(R.string.auth_password_show)
                            )
                        }
                    },
                    label = { Text(stringResource(R.string.auth_password)) },
                    modifier = Modifier.fillMaxWidth()
                )

                PasswordRulesHint(rules = rules)

                if (!error.isNullOrBlank()) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                }

                if (errorKey == "username_taken") {
                    TextButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            onGoToLogin(username.trim().lowercase())
                        }
                    ) { Text(stringResource(R.string.auth_login)) }
                }

                Spacer(Modifier.height(8.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                            vm.clearError()
                            onBack()
                        }
                    ) { Text(stringResource(R.string.auth_back)) }

                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                            val normalized = username.trim().lowercase()
                            vm.register(normalized, password) { onRegistered(normalized) }
                        },
                        enabled = username.isNotBlank() && rules.ok
                    ) { Text(stringResource(R.string.auth_register)) }
                }
            }
        }
    }
}