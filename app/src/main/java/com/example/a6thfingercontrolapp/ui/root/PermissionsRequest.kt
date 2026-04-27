package com.example.a6thfingercontrolapp.ui.root

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.a6thfingercontrolapp.R
import com.example.a6thfingercontrolapp.preferences.AppPreferencesViewModel
import com.example.a6thfingercontrolapp.ui.common.SettingsDialog

internal data class PermissionStep(
    val permission: String,
    val titleRes: Int,
    val bodyRes: Int
)

/** Explains and requests BLE permissions one by one. */
@Composable
internal fun PermissionsOnboardingContent(
    appPreferencesVm: AppPreferencesViewModel,
    steps: List<PermissionStep>,
    onAllGranted: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lang by appPreferencesVm.appLanguage.collectAsState()
    val theme by appPreferencesVm.appTheme.collectAsState()

    var requestedPermissions by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var currentPermission by rememberSaveable { mutableStateOf<String?>(null) }
    var showLanguageDialog by rememberSaveable { mutableStateOf(false) }

    fun refreshProgress() {
        currentPermission = steps.firstOrNull { !hasPermission(context, it.permission) }?.permission
        if (currentPermission == null) {
            onAllGranted()
        }
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            refreshProgress()
        }

    val settingsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            refreshProgress()
        }

    LaunchedEffect(Unit) {
        refreshProgress()
    }

    val currentStep = steps.firstOrNull { it.permission == currentPermission } ?: return
    val permanentlyDenied =
        activity != null &&
                requestedPermissions.contains(currentStep.permission) &&
                !hasPermission(context, currentStep.permission) &&
                !ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    currentStep.permission
                )

    val stepNumber = steps.indexOfFirst { it.permission == currentStep.permission }.let { index ->
        if (index >= 0) index + 1 else 1
    }

    Scaffold { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .padding(24.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.permissions_required_title),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = stringResource(R.string.permissions_required_step, stepNumber, steps.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                text = stringResource(currentStep.titleRes),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .padding(top = 20.dp)
                    .fillMaxWidth()
            )
            Text(
                text = stringResource(currentStep.bodyRes),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
            )
            Text(
                text = stringResource(
                    if (permanentlyDenied) {
                        R.string.permissions_required_settings_hint
                    } else {
                        R.string.permissions_required_request_hint
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .fillMaxWidth()
            )

            Button(
                onClick = {
                    if (permanentlyDenied) {
                        settingsLauncher.launch(appSettingsIntent(context))
                    } else {
                        requestedPermissions =
                            (requestedPermissions + currentStep.permission).distinct()
                        permissionLauncher.launch(currentStep.permission)
                    }
                },
                modifier = Modifier
                    .padding(top = 24.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    stringResource(
                        if (permanentlyDenied) {
                            R.string.permissions_open_settings
                        } else {
                            R.string.permissions_grant
                        }
                    )
                )
            }

            if (!permanentlyDenied && requestedPermissions.contains(currentStep.permission)) {
                OutlinedButton(
                    onClick = { permissionLauncher.launch(currentStep.permission) },
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth()
                ) {
                    Text(stringResource(R.string.permissions_try_again))
                }
            }

            TextButton(
                onClick = { showLanguageDialog = true },
                modifier = Modifier.padding(top = 20.dp)
            ) {
                Text("${stringResource(R.string.settings_language)}: ${lang.uppercase()}")
            }
        }
    }

    if (showLanguageDialog) {
        SettingsDialog(
            currentLang = lang,
            onDismiss = { showLanguageDialog = false },
            onSelect = { newLang ->
                appPreferencesVm.setAppLanguage(newLang) {
                    showLanguageDialog = false
                    activity?.recreate()
                }
            },
            currentTheme = theme,
            onThemeSelect = { appPreferencesVm.setAppTheme(it) }
        )
    }
}

internal fun requiredBlePermissionSteps(): List<PermissionStep> {
    return buildList {
        if (Build.VERSION.SDK_INT >= 31) {
            add(
                PermissionStep(
                    permission = Manifest.permission.BLUETOOTH_SCAN,
                    titleRes = R.string.permissions_scan_title,
                    bodyRes = R.string.permissions_scan_body
                )
            )
            add(
                PermissionStep(
                    permission = Manifest.permission.BLUETOOTH_CONNECT,
                    titleRes = R.string.permissions_connect_title,
                    bodyRes = R.string.permissions_connect_body
                )
            )
            add(
                PermissionStep(
                    permission = Manifest.permission.ACCESS_FINE_LOCATION,
                    titleRes = R.string.permissions_location_title,
                    bodyRes = R.string.permissions_location_body
                )
            )
        } else {
            add(
                PermissionStep(
                    permission = Manifest.permission.ACCESS_FINE_LOCATION,
                    titleRes = R.string.permissions_location_title,
                    bodyRes = R.string.permissions_location_body
                )
            )
        }
    }
}

internal fun hasPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun appSettingsIntent(context: Context): Intent =
    Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    )
