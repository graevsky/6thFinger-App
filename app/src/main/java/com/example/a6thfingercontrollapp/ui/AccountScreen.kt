package com.example.a6thfingercontrollapp.ui

import android.app.Activity
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView.CropShape
import com.canhub.cropper.CropImageView.Guidelines
import com.example.a6thfingercontrollapp.AuthViewModel
import com.example.a6thfingercontrollapp.BleViewModel
import com.example.a6thfingercontrollapp.R
import com.example.a6thfingercontrollapp.UiAuthState
import com.example.a6thfingercontrollapp.ble.EspSettings
import com.example.a6thfingercontrollapp.data.AppSettingsStore
import com.example.a6thfingercontrollapp.data.saveAvatarFromCroppedUri
import com.example.a6thfingercontrollapp.network.DeviceOut
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import com.example.a6thfingercontrollapp.data.avatarFile as dataAvatarFile
import com.example.a6thfingercontrollapp.data.deleteAvatarIfExists as dataDeleteAvatarIfExists
import com.example.a6thfingercontrollapp.data.loadBitmapFromFile as dataLoadBitmapFromFile

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

    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val errFailedLoadDevices = stringResource(R.string.err_failed_load_devices)
    val errFailedPullSettings = stringResource(R.string.err_failed_pull_settings)
    val errFailedPushSettings = stringResource(R.string.err_failed_push_settings)

    val avatarErrTitle = stringResource(R.string.avatar_error_title)
    val avatarErrSaveFailed = stringResource(R.string.avatar_error_save_failed)
    val avatarErrCropResult = stringResource(R.string.avatar_error_crop_result)

    val cropTitle = stringResource(R.string.avatar_crop_title)
    val cropDone = stringResource(R.string.avatar_crop_done)

    val settingsStore = remember { AppSettingsStore(context.applicationContext) }
    val avatarPath by settingsStore.getAvatarPath().collectAsState(initial = null)

    var avatarBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(avatarPath) {
        avatarBitmap = withContext(Dispatchers.IO) {
            dataLoadBitmapFromFile(avatarPath, maxDim = 1024)?.asImageBitmap()
        }
    }

    var avatarMenuOpen by remember { mutableStateOf(false) }
    var showFullscreen by remember { mutableStateOf(false) }
    var cropError by remember { mutableStateOf<String?>(null) }

    val cropOptions = remember(cropTitle, cropDone) {
        CropImageOptions().apply {
            guidelines = Guidelines.ON
            cropShape = CropShape.OVAL
            fixAspectRatio = true
            aspectRatioX = 1
            aspectRatioY = 1
            allowRotation = true
            allowFlipping = false
            outputCompressFormat = Bitmap.CompressFormat.JPEG
            outputCompressQuality = 92

            activityTitle = cropTitle
            cropMenuCropButtonTitle = cropDone
        }
    }

    val cropLauncher = rememberLauncherForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            val uri = result.uriContent
            if (uri != null) {
                scope.launch {
                    val savedPath = withContext(Dispatchers.IO) {
                        runCatching { dataAvatarFile(context).delete() }
                        saveAvatarFromCroppedUri(context, uri, outSize = 512, quality = 92)
                    }

                    if (!savedPath.isNullOrBlank()) {
                        val newBmp = withContext(Dispatchers.IO) {
                            dataLoadBitmapFromFile(savedPath, maxDim = 1024)?.asImageBitmap()
                        }

                        avatarBitmap = newBmp
                        settingsStore.setAvatarPath(savedPath)
                    } else {
                        cropError = avatarErrSaveFailed
                    }
                }
            } else {
                cropError = avatarErrCropResult
            }
        } else {
            val msg = result.error?.message
            if (!msg.isNullOrBlank()) cropError = msg
        }
    }

    val pickImageLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                cropLauncher.launch(CropImageContractOptions(uri, cropOptions))
            }
        }

    fun startPickAndCrop() {
        avatarMenuOpen = false
        cropError = null
        pickImageLauncher.launch("image/*")
    }

    fun removeAvatar() {
        avatarMenuOpen = false
        scope.launch {
            withContext(Dispatchers.IO) {
                dataDeleteAvatarIfExists(avatarPath)
                runCatching { dataAvatarFile(context).delete() }
            }
            avatarBitmap = null
            settingsStore.setAvatarPath(null)
        }
    }

    val username: String? =
        when (authState) {
            is UiAuthState.LoggedIn -> (authState as UiAuthState.LoggedIn).username
            else -> null
        }

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

    val rawStatus = bleState.status.lowercase()
    val connected =
        when {
            "disconnected" in rawStatus -> false
            "subscribed" in rawStatus -> true
            "tele" in rawStatus -> true
            "config" in rawStatus -> true
            "ack" in rawStatus -> true
            "auth" in rawStatus -> true
            "connected" in rawStatus -> true
            else -> false
        }

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
            devicesError = e.message ?: errFailedLoadDevices
            devices = emptyList()
        } finally {
            devicesLoading = false
        }
    }

    val avatarSize = 180.dp

    Scaffold { inner ->
        Column(
            Modifier
                .padding(inner)
                .padding(16.dp)
                .fillMaxSize(),
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
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.ContextClick)
                        showSettings = true
                    }
                ) {
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
                        .size(avatarSize)
                        .clip(CircleShape)
                        .clickable(enabled = avatarBitmap != null) { showFullscreen = true }
                ) {
                    if (avatarBitmap != null) {
                        Image(
                            bitmap = avatarBitmap!!,
                            contentDescription = stringResource(R.string.account_avatar),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Image(
                            painter = painterResource(id = R.drawable.ic_avatar_placeholder),
                            contentDescription = stringResource(R.string.account_avatar),
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                    ) {
                        IconButton(
                            onClick = { avatarMenuOpen = true },
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(R.string.avatar_change),
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = avatarMenuOpen,
                            onDismissRequest = { avatarMenuOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.avatar_choose_photo)) },
                                onClick = { startPickAndCrop() }
                            )
                            if (avatarBitmap != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.avatar_remove_photo)) },
                                    onClick = { removeAvatar() }
                                )
                            }
                        }
                    }
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
                            onClick = {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.ContextClick)
                                onLoginClick()
                            }
                        ) { Text(stringResource(R.string.auth_login)) }

                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.ContextClick)
                                onRegisterClick()
                            }
                        ) { Text(stringResource(R.string.auth_register)) }
                    }
                } else {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm)
                            authVm.logout()
                        }
                    ) { Text(stringResource(R.string.auth_logout)) }
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

                        if (devicesLoading) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.loading),
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
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.ContextClick)
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
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.ContextClick)
                                        scope.launch {
                                            devicesLoading = true
                                            devicesError = null
                                            try {
                                                val list = authVm.fetchDevices()
                                                devices = list
                                            } catch (e: Exception) {
                                                devicesError = e.message ?: errFailedLoadDevices
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

    if (cropError != null) {
        AlertDialog(
            onDismissRequest = { cropError = null },
            title = { Text(avatarErrTitle) },
            text = { Text(cropError ?: "") },
            confirmButton = {
                TextButton(onClick = { cropError = null }) {
                    Text(stringResource(R.string.generic_ok))
                }
            }
        )
    }

    if (showFullscreen && avatarBitmap != null) {
        FullscreenImageDialog(
            bitmap = avatarBitmap!!,
            onDismiss = { showFullscreen = false }
        )
    }

    if (showSettings) {
        val activity = LocalContext.current as? Activity

        SettingsDialog(
            currentLang = lang,
            onDismiss = {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.VirtualKey)
                showSettings = false
            },
            onSelect = { newLang: String ->
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm)
                vm.setAppLanguage(newLang)

                val currentAuth = authState
                if (currentAuth is UiAuthState.LoggedIn) {
                    authVm.updateLanguageRemote(newLang)
                }

                showSettings = false
                activity?.recreate()
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
            onDismiss = {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.VirtualKey)
                showDeviceSettingsDialog = false
            },
            onPullClick = {
                if (!connected) {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Reject)
                    showConnectWarning = true
                    return@DeviceSettingsDialog
                }

                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm)
                scope.launch {
                    dialogError = null
                    try {
                        val fromServer = authVm.pullDeviceSettings(dev.id)
                        if (fromServer != null) {
                            dialogJson = settingsToPrettyJson(fromServer)
                            vm.applySettingsFromCloud(fromServer)
                            showDeviceSettingsDialog = false
                            selectedDevice = null
                            onOpenControl()
                        } else {
                            dialogError = noSettingsMsg
                        }
                    } catch (e: Exception) {
                        dialogError = e.message ?: errFailedPullSettings
                    }
                }
            },
            onPushClick = {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm)
                scope.launch {
                    dialogError = null
                    try {
                        authVm.pushDeviceSettings(dev.id, currentSettings)
                        dialogJson = settingsToPrettyJson(currentSettings)
                    } catch (e: Exception) {
                        dialogError = e.message ?: errFailedPushSettings
                    }
                }
            }
        )
    }

    if (showConnectWarning) {
        AlertDialog(
            onDismissRequest = {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.VirtualKey)
                showConnectWarning = false
            },
            title = { Text(stringResource(R.string.prosthesis_not_connected_title)) },
            text = { Text(stringResource(R.string.prosthesis_not_connected_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.VirtualKey)
                        showConnectWarning = false
                    }
                ) {
                    Text(stringResource(R.string.generic_ok))
                }
            }
        )
    }
}

@Composable
private fun FullscreenImageDialog(
    bitmap: ImageBitmap,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Black)
        ) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .clip(CircleShape)
                    .background(androidx.compose.ui.graphics.Color(0x66000000))
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.settings_close),
                    tint = androidx.compose.ui.graphics.Color.White
                )
            }
        }
    }
}

@Composable
fun SettingsDialog(currentLang: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    val haptic = LocalHapticFeedback.current

    AlertDialog(
        onDismissRequest = {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.VirtualKey)
            onDismiss()
        },
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
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm)
                        onSelect("ru")
                    }
                )

                LanguageOptionRow(
                    title = stringResource(R.string.settings_english),
                    selected = currentLang == "en",
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm)
                        onSelect("en")
                    }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.VirtualKey)
                    onDismiss()
                }
            ) { Text(stringResource(R.string.settings_close)) }
        }
    )
}

@Composable
private fun LanguageOptionRow(title: String, selected: Boolean, onClick: () -> Unit) {
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
private fun DeviceRow(
    device: DeviceOut,
    isConnected: Boolean,
    enabled: Boolean,
    onOpen: () -> Unit
) {
    val title = device.alias ?: device.address

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
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
    val haptic = LocalHapticFeedback.current

    AlertDialog(
        onDismissRequest = {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.VirtualKey)
            onDismiss()
        },
        title = {
            Text(
                text = stringResource(
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
                    label = { Text(stringResource(R.string.label_json)) },
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
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
                        onClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm)
                            onPullClick()
                        }
                    ) { Text(text = stringResource(R.string.prosthesis_pull)) }

                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm)
                            onPushClick()
                        }
                    ) { Text(text = stringResource(R.string.prosthesis_push)) }
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
            TextButton(
                onClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.VirtualKey)
                    onDismiss()
                }
            ) { Text(stringResource(R.string.settings_close)) }
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
