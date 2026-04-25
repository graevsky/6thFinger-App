package com.example.a6thfingercontrolapp.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import com.example.a6thfingercontrolapp.AuthViewModel
import com.example.a6thfingercontrolapp.BleViewModel
import com.example.a6thfingercontrolapp.R
import com.example.a6thfingercontrolapp.UiAuthState
import com.example.a6thfingercontrolapp.ble.EspSettings
import com.example.a6thfingercontrolapp.data.AppSettingsStore
import com.example.a6thfingercontrolapp.data.DeviceSettingsRecord
import com.example.a6thfingercontrolapp.data.saveAvatarFromCroppedUri
import com.example.a6thfingercontrolapp.network.DeviceOut
import com.example.a6thfingercontrolapp.network.PasswordResetStartOut
import com.example.a6thfingercontrolapp.utils.isNetworkErrorKey
import com.example.a6thfingercontrolapp.utils.uiErrorText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import com.example.a6thfingercontrolapp.data.avatarFile as dataAvatarFile
import com.example.a6thfingercontrolapp.data.deleteAvatarIfExists as dataDeleteAvatarIfExists
import com.example.a6thfingercontrolapp.data.loadBitmapFromFile as dataLoadBitmapFromFile

/** Dialog type currently used by the account email management flow. */
private enum class EmailDialogMode { None, Add, Remove, Change }

/** Steps for adding a new email address. */
private enum class AddStep { EnterEmail, EnterCode }

/** Steps for removing the current email address. */
private enum class RemoveStep { ChooseMethod, EnterEmailCode, EnterRecoveryCode }

/** Steps for replacing the current email address with a new one. */
private enum class ChangeStep { ChooseOldMethod, EnterOldEmailCode, EnterOldRecoveryCode, EnterNewEmail, EnterNewEmailCode }

/**
 * UI representation of a selectable device in the cloud settings dialog.
 */
private data class CloudDeviceChoice(
    val device: DeviceOut?,
    val address: String,
    val alias: String?,
    val isConnectedDevice: Boolean
) {
    val key: String = device?.id ?: "local:${address.lowercase()}"
    val title: String = alias?.takeIf { it.isNotBlank() } ?: address
}

/** Cached cloud settings lookup state for one registered device. */
private data class CloudSettingsState(
    val checked: Boolean = false,
    val record: DeviceSettingsRecord? = null,
    val errorKey: String? = null
)

/**
 * Account control page.
 *
 * This screen combines profile management, language, settings access,
 * email management and cloud synchronization of prosthesis settings.
 */
@Composable
fun AccountScreen(
    vm: BleViewModel,
    authVm: AuthViewModel,
    onLoginClick: () -> Unit,
    onRegisterClick: () -> Unit,
    onOpenControl: () -> Unit,
    onChangePassword: (String) -> Unit
) {
    var showSettings by remember { mutableStateOf(false) }

    val lang by vm.appLanguage.collectAsState()
    val authState by authVm.auth.collectAsState()
    val bleState by vm.state.collectAsState()
    val currentSettings by vm.activeSettings.collectAsState()

    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val errFailedPullSettings = stringResource(R.string.err_failed_pull_settings)
    val errFailedPushSettings = stringResource(R.string.err_failed_push_settings)

    val avatarErrTitle = stringResource(R.string.avatar_error_title)
    val avatarErrSaveFailed = stringResource(R.string.avatar_error_save_failed)
    val avatarErrCropResult = stringResource(R.string.avatar_error_crop_result)

    val cropTitle = stringResource(R.string.avatar_crop_title)
    val cropDone = stringResource(R.string.avatar_crop_done)

    val settingsStore = remember { AppSettingsStore(context.applicationContext) }
    val avatarPath by settingsStore.getAvatarPath().collectAsState(initial = null)

    val cachedEmail by settingsStore.getCachedEmail().collectAsState(initial = null)
    val cachedDevicesJson by settingsStore.getCachedDevicesJson().collectAsState(initial = null)

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

                        if (authState is UiAuthState.LoggedIn) {
                            authVm.scheduleAvatarUpload(savedPath)
                        }
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
            if (uri != null) cropLauncher.launch(CropImageContractOptions(uri, cropOptions))
        }

    /** Starts Android image picker and then sends the selected image to cropper. */
    fun startPickAndCrop() {
        avatarMenuOpen = false
        cropError = null
        pickImageLauncher.launch("image/*")
    }

    /** Deletes the local avatar and schedules remote deletion for logged-in users. */
    fun removeAvatar() {
        avatarMenuOpen = false
        scope.launch {
            withContext(Dispatchers.IO) {
                dataDeleteAvatarIfExists(avatarPath)
                runCatching { dataAvatarFile(context).delete() }
            }
            avatarBitmap = null
            settingsStore.setAvatarPath(null)

            if (authState is UiAuthState.LoggedIn) {
                authVm.deleteAvatarRemote()
            }
        }
    }

    val username: String? =
        when (authState) {
            is UiAuthState.LoggedIn -> (authState as UiAuthState.LoggedIn).username
            else -> null
        }

    var emailInfo by remember { mutableStateOf<PasswordResetStartOut?>(null) }
    var emailLoading by remember { mutableStateOf(false) }
    var emailErrorKey by remember { mutableStateOf<String?>(null) }
    var emailRefreshTick by remember { mutableStateOf(0) }
    fun refreshEmailInfo() {
        emailRefreshTick++
    }

    LaunchedEffect(username, emailRefreshTick) {
        if (username.isNullOrBlank()) {
            emailInfo = null
            emailErrorKey = null
            settingsStore.setCachedEmail(null)
            return@LaunchedEffect
        }
        emailLoading = true
        emailErrorKey = null
        try {
            val info = authVm.passwordResetStart(username)
            emailInfo = info
            if (info.has_email && !info.email.isNullOrBlank()) {
                settingsStore.setCachedEmail(info.email)
            } else {
                settingsStore.setCachedEmail(null)
            }
        } catch (e: Exception) {
            emailErrorKey = e.message
        } finally {
            emailLoading = false
        }
    }

    // Retry email metadata periodically when the error is network-related.
    LaunchedEffect(emailErrorKey) {
        if (!isNetworkErrorKey(emailErrorKey)) return@LaunchedEffect
        while (isNetworkErrorKey(emailErrorKey) && username != null) {
            delay(30_000L)
            refreshEmailInfo()
        }
    }

    val hasEmail = emailInfo?.has_email == true || !cachedEmail.isNullOrBlank()
    val emailShown = emailInfo?.email ?: cachedEmail

    var emailDialogMode by remember { mutableStateOf(EmailDialogMode.None) }

    var addStep by remember { mutableStateOf(AddStep.EnterEmail) }
    var addEmail by remember { mutableStateOf("") }
    var addCode by remember { mutableStateOf("") }
    var addErrKey by remember { mutableStateOf<String?>(null) }
    var addBusy by remember { mutableStateOf(false) }

    var removeStep by remember { mutableStateOf(RemoveStep.ChooseMethod) }
    var removeCode by remember { mutableStateOf("") }
    var removeRecovery by remember { mutableStateOf("") }
    var removeErrKey by remember { mutableStateOf<String?>(null) }
    var removeBusy by remember { mutableStateOf(false) }

    var changeStep by remember { mutableStateOf(ChangeStep.ChooseOldMethod) }
    var changeOldCode by remember { mutableStateOf("") }
    var changeOldRecovery by remember { mutableStateOf("") }
    var changeNewEmail by remember { mutableStateOf("") }
    var changeNewCode by remember { mutableStateOf("") }
    var changeErrKey by remember { mutableStateOf<String?>(null) }
    var changeBusy by remember { mutableStateOf(false) }

    /** Opens the add-email dialog with a clean state. */
    fun openAddEmail() {
        emailDialogMode = EmailDialogMode.Add
        addStep = AddStep.EnterEmail
        addEmail = ""
        addCode = ""
        addErrKey = null
        addBusy = false
    }

    /** Opens the remove-email dialog with a clean state. */
    fun openRemoveEmail() {
        emailDialogMode = EmailDialogMode.Remove
        removeStep = RemoveStep.ChooseMethod
        removeCode = ""
        removeRecovery = ""
        removeErrKey = null
        removeBusy = false
    }

    /** Opens the change-email dialog with a clean state. */
    fun openChangeEmail() {
        emailDialogMode = EmailDialogMode.Change
        changeStep = ChangeStep.ChooseOldMethod
        changeOldCode = ""
        changeOldRecovery = ""
        changeNewEmail = ""
        changeNewCode = ""
        changeErrKey = null
        changeBusy = false
    }

    var devices by remember { mutableStateOf<List<DeviceOut>>(emptyList()) }
    var devicesLoading by remember { mutableStateOf(false) }
    var devicesErrorKey by remember { mutableStateOf<String?>(null) }

    var showDeviceSettingsDialog by remember { mutableStateOf(false) }
    var dialogSelectedKey by remember { mutableStateOf<String?>(null) }
    var dialogJson by remember { mutableStateOf("{}") }
    var dialogErrorKey by remember { mutableStateOf<String?>(null) }
    var dialogBusy by remember { mutableStateOf(false) }
    var showConnectWarning by remember { mutableStateOf(false) }
    var cloudSettingsByDeviceId by remember {
        mutableStateOf<Map<String, CloudSettingsState>>(
            emptyMap()
        )
    }
    var cloudProbeLoading by remember { mutableStateOf(false) }

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

    /** Stores a compact device list locally for offline account UI. */
    fun cacheDevices(list: List<DeviceOut>) {
        val arr = JSONArray()
        list.forEach { d ->
            val o = JSONObject()
            o.put("id", d.id)
            o.put("address", d.address)
            o.put("alias", d.alias ?: JSONObject.NULL)
            arr.put(o)
        }
        scope.launch { settingsStore.setCachedDevicesJson(arr.toString()) }
    }

    /** Reads locally cached devices when the backend cannot be reached. */
    fun readCachedDevices(): List<DeviceOut> {
        val raw = cachedDevicesJson ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                DeviceOut(
                    id = o.getString("id"),
                    owner_id = "",
                    address = o.getString("address"),
                    alias = if (o.isNull("alias")) null else o.getString("alias"),
                    created_at = ""
                )
            }
        }.getOrDefault(emptyList())
    }

    /** Adds or replaces a device in the local list and refreshes the cache. */
    fun mergeDeviceIntoList(device: DeviceOut) {
        val updated =
            (devices.filterNot {
                it.id == device.id || it.address.equals(device.address, ignoreCase = true)
            } + device).sortedBy { (it.alias ?: it.address).lowercase() }

        devices = updated
        cacheDevices(updated)
    }

    /** Ensures a selected local-only device exists on the backend before sync. */
    suspend fun resolveCloudChoice(choice: CloudDeviceChoice): DeviceOut {
        val existing = choice.device
        if (existing != null && existing.id.isNotBlank()) return existing

        val ensured = authVm.ensureDevice(
            address = choice.address,
            alias = choice.alias
        )
        mergeDeviceIntoList(ensured)
        dialogSelectedKey = ensured.id
        return ensured
    }

    /** Fetches and caches cloud settings metadata for a device. */
    suspend fun refreshCloudSettingsState(
        device: DeviceOut,
        force: Boolean = false
    ): CloudSettingsState {
        val cached = cloudSettingsByDeviceId[device.id]
        if (!force && cached != null && (cached.checked || !cached.errorKey.isNullOrBlank())) {
            return cached
        }

        return try {
            val record = authVm.fetchDeviceSettingsRecord(device.id)
            val state = CloudSettingsState(
                checked = true,
                record = record,
                errorKey = null
            )
            cloudSettingsByDeviceId = cloudSettingsByDeviceId.toMutableMap().apply {
                put(device.id, state)
            }
            state
        } catch (e: Exception) {
            val state = CloudSettingsState(
                checked = false,
                record = null,
                errorKey = e.message
            )
            cloudSettingsByDeviceId = cloudSettingsByDeviceId.toMutableMap().apply {
                put(device.id, state)
            }
            state
        }
    }

    var devicesRefreshTick by remember { mutableStateOf(0) }
    fun refreshDevices() {
        devicesRefreshTick++
    }

    LaunchedEffect(username, connected, activeAddress, devicesRefreshTick) {
        if (username == null) {
            devices = emptyList()
            devicesErrorKey = null
            cloudSettingsByDeviceId = emptyMap()
            settingsStore.setCachedDevicesJson(null)
            return@LaunchedEffect
        }

        devicesLoading = true
        devicesErrorKey = null
        try {
            if (connected && activeAddress.isNotEmpty()) {
                runCatching {
                    authVm.ensureDevice(
                        address = activeAddress,
                        alias = activeAlias.ifBlank { null }
                    )
                }.onFailure { e -> devicesErrorKey = e.message }
            }

            val list = authVm.fetchDevices()
            devices = list
            cacheDevices(list)
        } catch (e: Exception) {
            val key = e.message
            devicesErrorKey = key
            val cached = readCachedDevices()
            if (isNetworkErrorKey(key) && cached.isNotEmpty()) {
                devices = cached
            } else {
                devices = emptyList()
            }
        } finally {
            devicesLoading = false
        }
    }

    LaunchedEffect(devices.map { it.id }.joinToString("|"), username) {
        if (username == null || devices.isEmpty()) {
            cloudSettingsByDeviceId = emptyMap()
            cloudProbeLoading = false
            return@LaunchedEffect
        }

        cloudProbeLoading = true
        val next = mutableMapOf<String, CloudSettingsState>()
        devices.forEach { dev ->
            next[dev.id] = try {
                val record = authVm.fetchDeviceSettingsRecord(dev.id)
                CloudSettingsState(
                    checked = true,
                    record = record,
                    errorKey = null
                )
            } catch (e: Exception) {
                CloudSettingsState(
                    checked = false,
                    record = null,
                    errorKey = e.message
                )
            }
        }
        cloudSettingsByDeviceId = next
        cloudProbeLoading = false
    }

    // Retry device list loading if the previous failure was caused by network.
    LaunchedEffect(devicesErrorKey) {
        if (!isNetworkErrorKey(devicesErrorKey)) return@LaunchedEffect
        while (isNetworkErrorKey(devicesErrorKey) && username != null) {
            delay(30_000L)
            refreshDevices()
        }
    }

    val selectableChoices = remember(devices, connected, activeAddress, activeAlias) {
        val serverChoices =
            devices.sortedWith(
                compareBy<DeviceOut>(
                    { !(connected && it.address.equals(activeAddress, ignoreCase = true)) },
                    { (it.alias ?: it.address).lowercase() }
                )
            ).map { dev ->
                CloudDeviceChoice(
                    device = dev,
                    address = dev.address,
                    alias = dev.alias,
                    isConnectedDevice = connected && dev.address.equals(
                        activeAddress,
                        ignoreCase = true
                    )
                )
            }

        val localOnlyChoice =
            if (connected && activeAddress.isNotBlank() && serverChoices.none {
                    it.address.equals(activeAddress, ignoreCase = true)
                }
            ) {
                CloudDeviceChoice(
                    device = null,
                    address = activeAddress,
                    alias = activeAlias.ifBlank { null },
                    isConnectedDevice = true
                )
            } else null

        buildList {
            if (localOnlyChoice != null) add(localOnlyChoice)
            addAll(serverChoices)
        }
    }

    /** Returns cached cloud settings state for a selected device choice. */
    fun cloudStateForChoice(choice: CloudDeviceChoice?): CloudSettingsState? {
        if (choice == null) return null
        val dev = choice.device ?: return CloudSettingsState(
            checked = true,
            record = null,
            errorKey = null
        )
        return cloudSettingsByDeviceId[dev.id]
    }

    /** Opens the cloud settings dialog and preloads JSON preview for selection. */
    fun openCloudDialog(initialKey: String) {
        dialogSelectedKey = initialKey
        dialogErrorKey = null
        dialogBusy = false
        val initialChoice = selectableChoices.firstOrNull { it.key == initialKey }
        val initialRecord = cloudStateForChoice(initialChoice)?.record
        dialogJson = initialRecord?.let { settingsToPrettyJson(it.settings) } ?: "{}"
        showDeviceSettingsDialog = true
    }

    LaunchedEffect(showDeviceSettingsDialog, selectableChoices.map { it.key }.joinToString("|")) {
        if (!showDeviceSettingsDialog) return@LaunchedEffect
        if (selectableChoices.isEmpty()) {
            showDeviceSettingsDialog = false
            return@LaunchedEffect
        }
        if (dialogSelectedKey == null || selectableChoices.none { it.key == dialogSelectedKey }) {
            dialogSelectedKey = selectableChoices.first().key
            val firstRecord = cloudStateForChoice(selectableChoices.first())?.record
            dialogJson = firstRecord?.let { settingsToPrettyJson(it.settings) } ?: "{}"
        }
    }

    val scroll = rememberScrollState()
    val avatarSize = 180.dp

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
                horizontalAlignment = Alignment.CenterHorizontally
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
                            onDismissRequest = { avatarMenuOpen = false }) {
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

                Text(
                    text = username ?: stringResource(R.string.auth_guest),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(top = 12.dp)
                )

                if (username == null) {
                    Text(
                        text = stringResource(R.string.auth_guest_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                        },
                        modifier = Modifier.padding(top = 8.dp)
                    ) { Text(stringResource(R.string.auth_logout)) }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        stringResource(R.string.prosthesis_settings),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        stringResource(R.string.prosthesis_settings_descr),
                        style = MaterialTheme.typography.bodySmall
                    )

                    if (devicesLoading) {
                        Text(
                            stringResource(R.string.loading),
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        val errText = uiErrorTextOrRaw(devicesErrorKey)
                        if (!errText.isNullOrBlank()) {
                            Text(
                                text = errText,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        val localOnlyChoice = selectableChoices.firstOrNull { it.device == null }
                        val hasAnyChoice = selectableChoices.isNotEmpty()

                        if (!hasAnyChoice) {
                            Text(
                                stringResource(R.string.prosthesis_no_devices),
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            localOnlyChoice?.let { choice ->
                                DeviceRow(
                                    title = choice.title,
                                    address = choice.address,
                                    isConnected = choice.isConnectedDevice,
                                    enabled = username != null,
                                    cloudStateLabel = stringResource(R.string.prosthesis_local_device_not_registered),
                                    onOpen = {
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.ContextClick)
                                        openCloudDialog(choice.key)
                                    }
                                )
                            }

                            devices.forEach { dev ->
                                val choice =
                                    selectableChoices.firstOrNull { it.device?.id == dev.id }
                                val cloudState = cloudSettingsByDeviceId[dev.id]
                                val cloudStatus = when {
                                    cloudState?.record != null ->
                                        stringResource(
                                            R.string.prosthesis_server_settings_saved_version,
                                            cloudState.record.version
                                        )

                                    cloudState?.checked == true ->
                                        stringResource(R.string.prosthesis_server_settings_missing)

                                    !cloudState?.errorKey.isNullOrBlank() ->
                                        stringResource(R.string.prosthesis_server_status_unknown)

                                    cloudProbeLoading -> stringResource(R.string.loading)
                                    else -> stringResource(R.string.prosthesis_server_status_unknown)
                                }

                                DeviceRow(
                                    title = choice?.title ?: (dev.alias ?: dev.address),
                                    address = dev.address,
                                    isConnected = connected && activeAddress.equals(
                                        dev.address,
                                        ignoreCase = true
                                    ),
                                    enabled = username != null,
                                    cloudStateLabel = cloudStatus,
                                    onOpen = {
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.ContextClick)
                                        openCloudDialog(dev.id)
                                    }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(
                                onClick = {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.ContextClick)
                                    refreshDevices()
                                }
                            ) { Text(stringResource(R.string.refresh)) }
                        }

                        if (!connected) {
                            Text(
                                stringResource(R.string.prosthesis_connect_hint),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    if (showSettings) {
        val activity = LocalContext.current as? Activity

        val emailErrText = uiErrorText(emailErrorKey)
        val emailLine =
            when {
                emailLoading -> stringResource(R.string.loading)
                !emailErrText.isNullOrBlank() && !emailShown.isNullOrBlank() ->
                    stringResource(R.string.account_email_current, emailShown)

                !emailErrText.isNullOrBlank() && emailShown.isNullOrBlank() ->
                    stringResource(R.string.account_email_not_set)

                hasEmail && !emailShown.isNullOrBlank() ->
                    stringResource(R.string.account_email_current, emailShown)

                else -> stringResource(R.string.account_email_not_set)
            }

        val guideUrl =
            "https://docs.google.com/document/d/1MEejkdQEGTkvxDuX7fgXnzfzSTgcVONKTlj-WCBkAp0/edit?usp=sharing"

        val links = listOf(
            SettingsLink(
                title = "Guide",
                url = guideUrl
            ),
            SettingsLink(
                title = "App",
                url = "https://github.com/graevsky/6thFinger-App"
            ),
            SettingsLink(
                title = "ESP32 firmware",
                url = "https://github.com/graevsky/6thFinger-Controller"
            ),
            SettingsLink(
                title = "Backend",
                url = "https://github.com/graevsky/6thFinger-Backend"
            )
        )

        SettingsDialog(
            currentLang = lang,
            onDismiss = {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.VirtualKey)
                showSettings = false
            },
            onSelect = { newLang: String ->
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm)
                vm.setAppLanguage(newLang)

                if (authState is UiAuthState.LoggedIn) {
                    authVm.updateLanguageRemote(newLang)
                }

                showSettings = false
                activity?.recreate()
            },

            isLoggedIn = username != null,
            emailLine = emailLine,
            emailErrorLine = emailErrText,
            hasEmail = hasEmail,
            onAddEmail = {
                showSettings = false
                openAddEmail()
            },
            onChangeEmail = {
                showSettings = false
                openChangeEmail()
            },
            onRemoveEmail = {
                showSettings = false
                openRemoveEmail()
            },
            onChangePassword = {
                showSettings = false
                username?.let { onChangePassword(it) }
            },

            links = links
        )
    }

    if (emailDialogMode == EmailDialogMode.Add) {
        AlertDialog(
            onDismissRequest = { if (!addBusy) emailDialogMode = EmailDialogMode.None },
            title = { Text(stringResource(R.string.account_email_add)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (addStep == AddStep.EnterEmail) {
                        OutlinedTextField(
                            value = addEmail,
                            onValueChange = { addEmail = it.trim() },
                            singleLine = true,
                            label = { Text(stringResource(R.string.postreg_email_label)) },
                            modifier = Modifier.fillMaxWidth()
                        )

                        uiErrorText(addErrKey)?.let {
                            Text(it, color = MaterialTheme.colorScheme.error)
                        }

                        Button(
                            enabled = !addBusy && addEmail.isNotBlank(),
                            onClick = {
                                addBusy = true
                                addErrKey = null
                                scope.launch {
                                    try {
                                        authVm.emailStartAdd(addEmail)
                                        addCode = ""
                                        addStep = AddStep.EnterCode
                                    } catch (e: Exception) {
                                        addErrKey = e.message
                                    } finally {
                                        addBusy = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.account_email_send_code)) }
                    } else {
                        Text(stringResource(R.string.postreg_email_code_hint, addEmail))

                        OutlinedTextField(
                            value = addCode,
                            onValueChange = { addCode = it.trim() },
                            singleLine = true,
                            label = { Text(stringResource(R.string.postreg_email_code_label)) },
                            modifier = Modifier.fillMaxWidth()
                        )

                        uiErrorText(addErrKey)?.let {
                            Text(it, color = MaterialTheme.colorScheme.error)
                        }

                        OutlinedButton(
                            enabled = !addBusy,
                            onClick = {
                                addBusy = true
                                addErrKey = null
                                scope.launch {
                                    try {
                                        authVm.emailStartAdd(addEmail)
                                    } catch (e: Exception) {
                                        addErrKey = e.message
                                    } finally {
                                        addBusy = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.postreg_email_resend)) }

                        TextButton(
                            enabled = !addBusy,
                            onClick = { addStep = AddStep.EnterEmail }
                        ) { Text(stringResource(R.string.postreg_email_change)) }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !addBusy && addStep == AddStep.EnterCode && addCode.isNotBlank(),
                    onClick = {
                        addBusy = true
                        addErrKey = null
                        scope.launch {
                            try {
                                authVm.emailConfirmAdd(addEmail, addCode)
                                emailDialogMode = EmailDialogMode.None
                                refreshEmailInfo()
                            } catch (e: Exception) {
                                addErrKey = e.message
                            } finally {
                                addBusy = false
                            }
                        }
                    }
                ) { Text(stringResource(R.string.account_email_confirm)) }
            },
            dismissButton = {
                TextButton(
                    enabled = !addBusy,
                    onClick = { emailDialogMode = EmailDialogMode.None }
                ) { Text(stringResource(R.string.settings_close)) }
            }
        )
    }

    if (emailDialogMode == EmailDialogMode.Remove) {
        AlertDialog(
            onDismissRequest = { if (!removeBusy) emailDialogMode = EmailDialogMode.None },
            title = { Text(stringResource(R.string.account_email_remove)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.account_email_remove_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )

                    when (removeStep) {
                        RemoveStep.ChooseMethod -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    enabled = !removeBusy,
                                    onClick = {
                                        removeBusy = true
                                        removeErrKey = null
                                        scope.launch {
                                            try {
                                                authVm.emailStartRemove()
                                                removeCode = ""
                                                removeStep = RemoveStep.EnterEmailCode
                                            } catch (e: Exception) {
                                                removeErrKey = e.message
                                            } finally {
                                                removeBusy = false
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text(stringResource(R.string.account_email_method_email)) }

                                OutlinedButton(
                                    enabled = !removeBusy,
                                    onClick = { removeStep = RemoveStep.EnterRecoveryCode },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text(stringResource(R.string.account_email_method_recovery)) }
                            }
                        }

                        RemoveStep.EnterEmailCode -> {
                            OutlinedTextField(
                                value = removeCode,
                                onValueChange = { removeCode = it.trim() },
                                singleLine = true,
                                label = { Text(stringResource(R.string.postreg_email_code_label)) },
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedButton(
                                enabled = !removeBusy,
                                onClick = {
                                    removeBusy = true
                                    removeErrKey = null
                                    scope.launch {
                                        try {
                                            authVm.emailStartRemove()
                                        } catch (e: Exception) {
                                            removeErrKey = e.message
                                        } finally {
                                            removeBusy = false
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(stringResource(R.string.postreg_email_resend)) }

                            TextButton(
                                enabled = !removeBusy,
                                onClick = { removeStep = RemoveStep.ChooseMethod }
                            ) { Text(stringResource(R.string.auth_back)) }
                        }

                        RemoveStep.EnterRecoveryCode -> {
                            OutlinedTextField(
                                value = removeRecovery,
                                onValueChange = { removeRecovery = it },
                                singleLine = true,
                                label = { Text(stringResource(R.string.account_email_recovery_code)) },
                                modifier = Modifier.fillMaxWidth()
                            )

                            TextButton(
                                enabled = !removeBusy,
                                onClick = { removeStep = RemoveStep.ChooseMethod }
                            ) { Text(stringResource(R.string.auth_back)) }
                        }
                    }

                    uiErrorText(removeErrKey)?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                val canConfirm =
                    !removeBusy && (
                            (removeStep == RemoveStep.EnterEmailCode && removeCode.isNotBlank()) ||
                                    (removeStep == RemoveStep.EnterRecoveryCode && removeRecovery.isNotBlank())
                            )

                TextButton(
                    enabled = canConfirm,
                    onClick = {
                        removeBusy = true
                        removeErrKey = null
                        scope.launch {
                            try {
                                when (removeStep) {
                                    RemoveStep.EnterEmailCode ->
                                        authVm.emailConfirmRemove(
                                            code = removeCode,
                                            recoveryCode = null
                                        )

                                    RemoveStep.EnterRecoveryCode ->
                                        authVm.emailConfirmRemove(
                                            code = null,
                                            recoveryCode = removeRecovery
                                        )

                                    else -> Unit
                                }

                                emailDialogMode = EmailDialogMode.None
                                refreshEmailInfo()
                            } catch (e: Exception) {
                                removeErrKey = e.message
                            } finally {
                                removeBusy = false
                            }
                        }
                    }
                ) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(
                    enabled = !removeBusy,
                    onClick = { emailDialogMode = EmailDialogMode.None }
                ) { Text(stringResource(R.string.settings_close)) }
            }
        )
    }

    if (emailDialogMode == EmailDialogMode.Change) {
        AlertDialog(
            onDismissRequest = { if (!changeBusy) emailDialogMode = EmailDialogMode.None },
            title = { Text(stringResource(R.string.account_email_change)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (changeStep) {
                        ChangeStep.ChooseOldMethod -> {
                            Text(stringResource(R.string.account_email_change_step_old))

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    enabled = !changeBusy,
                                    onClick = {
                                        changeBusy = true
                                        changeErrKey = null
                                        scope.launch {
                                            try {
                                                authVm.emailStartRemove()
                                                changeOldCode = ""
                                                changeStep = ChangeStep.EnterOldEmailCode
                                            } catch (e: Exception) {
                                                changeErrKey = e.message
                                            } finally {
                                                changeBusy = false
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text(stringResource(R.string.account_email_method_email)) }

                                OutlinedButton(
                                    enabled = !changeBusy,
                                    onClick = { changeStep = ChangeStep.EnterOldRecoveryCode },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text(stringResource(R.string.account_email_method_recovery)) }
                            }
                        }

                        ChangeStep.EnterOldEmailCode -> {
                            OutlinedTextField(
                                value = changeOldCode,
                                onValueChange = { changeOldCode = it.trim() },
                                singleLine = true,
                                label = { Text(stringResource(R.string.postreg_email_code_label)) },
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedButton(
                                enabled = !changeBusy,
                                onClick = {
                                    changeBusy = true
                                    changeErrKey = null
                                    scope.launch {
                                        try {
                                            authVm.emailStartRemove()
                                        } catch (e: Exception) {
                                            changeErrKey = e.message
                                        } finally {
                                            changeBusy = false
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(stringResource(R.string.postreg_email_resend)) }

                            TextButton(
                                enabled = !changeBusy,
                                onClick = { changeStep = ChangeStep.ChooseOldMethod }
                            ) { Text(stringResource(R.string.auth_back)) }
                        }

                        ChangeStep.EnterOldRecoveryCode -> {
                            OutlinedTextField(
                                value = changeOldRecovery,
                                onValueChange = { changeOldRecovery = it },
                                singleLine = true,
                                label = { Text(stringResource(R.string.account_email_recovery_code)) },
                                modifier = Modifier.fillMaxWidth()
                            )

                            TextButton(
                                enabled = !changeBusy,
                                onClick = { changeStep = ChangeStep.ChooseOldMethod }
                            ) { Text(stringResource(R.string.auth_back)) }
                        }

                        ChangeStep.EnterNewEmail -> {
                            Text(stringResource(R.string.account_email_change_step_new))

                            OutlinedTextField(
                                value = changeNewEmail,
                                onValueChange = { changeNewEmail = it.trim() },
                                singleLine = true,
                                label = { Text(stringResource(R.string.postreg_email_label)) },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Button(
                                enabled = !changeBusy && changeNewEmail.isNotBlank(),
                                onClick = {
                                    changeBusy = true
                                    changeErrKey = null
                                    scope.launch {
                                        try {
                                            authVm.emailStartAdd(changeNewEmail)
                                            changeNewCode = ""
                                            changeStep = ChangeStep.EnterNewEmailCode
                                        } catch (e: Exception) {
                                            changeErrKey = e.message
                                        } finally {
                                            changeBusy = false
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(stringResource(R.string.account_email_send_code)) }
                        }

                        ChangeStep.EnterNewEmailCode -> {
                            Text(stringResource(R.string.postreg_email_code_hint, changeNewEmail))

                            OutlinedTextField(
                                value = changeNewCode,
                                onValueChange = { changeNewCode = it.trim() },
                                singleLine = true,
                                label = { Text(stringResource(R.string.postreg_email_code_label)) },
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedButton(
                                enabled = !changeBusy,
                                onClick = {
                                    changeBusy = true
                                    changeErrKey = null
                                    scope.launch {
                                        try {
                                            authVm.emailStartAdd(changeNewEmail)
                                        } catch (e: Exception) {
                                            changeErrKey = e.message
                                        } finally {
                                            changeBusy = false
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(stringResource(R.string.postreg_email_resend)) }

                            TextButton(
                                enabled = !changeBusy,
                                onClick = { changeStep = ChangeStep.EnterNewEmail }
                            ) { Text(stringResource(R.string.postreg_email_change)) }
                        }
                    }

                    uiErrorText(changeErrKey)?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                val canConfirm =
                    !changeBusy && when (changeStep) {
                        ChangeStep.EnterOldEmailCode -> changeOldCode.isNotBlank()
                        ChangeStep.EnterOldRecoveryCode -> changeOldRecovery.isNotBlank()
                        ChangeStep.EnterNewEmailCode -> changeNewCode.isNotBlank()
                        else -> false
                    }

                TextButton(
                    enabled = canConfirm,
                    onClick = {
                        changeBusy = true
                        changeErrKey = null
                        scope.launch {
                            try {
                                when (changeStep) {
                                    ChangeStep.EnterOldEmailCode -> {
                                        authVm.emailConfirmRemove(
                                            code = changeOldCode,
                                            recoveryCode = null
                                        )
                                        changeStep = ChangeStep.EnterNewEmail
                                    }

                                    ChangeStep.EnterOldRecoveryCode -> {
                                        authVm.emailConfirmRemove(
                                            code = null,
                                            recoveryCode = changeOldRecovery
                                        )
                                        changeStep = ChangeStep.EnterNewEmail
                                    }

                                    ChangeStep.EnterNewEmailCode -> {
                                        authVm.emailConfirmAdd(changeNewEmail, changeNewCode)
                                        emailDialogMode = EmailDialogMode.None
                                        refreshEmailInfo()
                                    }

                                    else -> Unit
                                }
                            } catch (e: Exception) {
                                changeErrKey = e.message
                            } finally {
                                changeBusy = false
                            }
                        }
                    }
                ) {
                    Text(
                        when (changeStep) {
                            ChangeStep.EnterOldEmailCode,
                            ChangeStep.EnterOldRecoveryCode -> stringResource(R.string.confirm)

                            ChangeStep.EnterNewEmailCode -> stringResource(R.string.account_email_confirm)
                            else -> stringResource(R.string.confirm)
                        }
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !changeBusy,
                    onClick = { emailDialogMode = EmailDialogMode.None }
                ) { Text(stringResource(R.string.settings_close)) }
            }
        )
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
        FullscreenImageDialog(bitmap = avatarBitmap!!, onDismiss = { showFullscreen = false })
    }

    val dialogSelectedChoice = selectableChoices.firstOrNull { it.key == dialogSelectedKey }
    val dialogSelectedState = cloudStateForChoice(dialogSelectedChoice)
    val dialogErrorText = uiErrorTextOrRaw(dialogErrorKey ?: dialogSelectedState?.errorKey)

    if (showDeviceSettingsDialog && dialogSelectedChoice != null) {
        DeviceSettingsDialog(
            devices = selectableChoices,
            selectedKey = dialogSelectedChoice.key,
            selectedState = dialogSelectedState,
            json = dialogJson,
            isBusy = dialogBusy,
            isPullEnabled = connected,
            error = dialogErrorText,
            onDismiss = {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.VirtualKey)
                showDeviceSettingsDialog = false
            },
            onSelectedKeyChange = { key ->
                dialogSelectedKey = key
                dialogErrorKey = null
                val choice = selectableChoices.firstOrNull { it.key == key }
                val record = cloudStateForChoice(choice)?.record
                dialogJson = record?.let { settingsToPrettyJson(it.settings) } ?: "{}"
            },
            onPreviewClick = {
                val choice = dialogSelectedChoice
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.ContextClick)
                scope.launch {
                    dialogBusy = true
                    dialogErrorKey = null
                    try {
                        val device = resolveCloudChoice(choice)
                        val state = refreshCloudSettingsState(device, force = true)
                        val record = state.record
                        if (record != null) {
                            dialogJson = settingsToPrettyJson(record.settings)
                        } else {
                            dialogJson = "{}"
                            dialogErrorKey = "prosthesis_no_settings_on_server"
                        }
                    } catch (e: Exception) {
                        dialogErrorKey = e.message ?: errFailedPullSettings
                    } finally {
                        dialogBusy = false
                    }
                }
            },
            onPullClick = {
                if (!connected) {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Reject)
                    showConnectWarning = true
                    return@DeviceSettingsDialog
                }

                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm)
                scope.launch {
                    dialogBusy = true
                    dialogErrorKey = null
                    try {
                        val device = resolveCloudChoice(dialogSelectedChoice)
                        val state = refreshCloudSettingsState(device, force = true)
                        val record = state.record
                        if (record != null) {
                            dialogJson = settingsToPrettyJson(record.settings)
                            vm.applySettingsFromCloud(record.settings)
                            showDeviceSettingsDialog = false
                            onOpenControl()
                        } else {
                            dialogErrorKey = "prosthesis_no_settings_on_server"
                        }
                    } catch (e: Exception) {
                        dialogErrorKey = e.message ?: errFailedPullSettings
                    } finally {
                        dialogBusy = false
                    }
                }
            },
            onPushClick = {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm)
                scope.launch {
                    dialogBusy = true
                    dialogErrorKey = null
                    try {
                        val device = resolveCloudChoice(dialogSelectedChoice)
                        val record = authVm.pushDeviceSettings(device.id, currentSettings)
                        cloudSettingsByDeviceId = cloudSettingsByDeviceId.toMutableMap().apply {
                            put(
                                device.id,
                                CloudSettingsState(
                                    checked = true,
                                    record = record,
                                    errorKey = null
                                )
                            )
                        }
                        dialogSelectedKey = device.id
                        dialogJson = settingsToPrettyJson(record.settings)
                    } catch (e: Exception) {
                        dialogErrorKey = e.message ?: errFailedPushSettings
                    } finally {
                        dialogBusy = false
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
                ) { Text(stringResource(R.string.generic_ok)) }
            }
        )
    }
}

/** Fullscreen avatar preview dialog. */
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

/** Row displaying one registered/local prosthesis device. */
@Composable
private fun DeviceRow(
    title: String,
    address: String,
    isConnected: Boolean,
    enabled: Boolean,
    cloudStateLabel: String?,
    onOpen: () -> Unit
) {
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
                Text(text = address, style = MaterialTheme.typography.bodySmall)

                if (!cloudStateLabel.isNullOrBlank()) {
                    Text(
                        text = cloudStateLabel,
                        style = MaterialTheme.typography.bodySmall
                    )
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

/** Dialog for previewing, pulling and pushing cloud device settings. */
@Composable
private fun DeviceSettingsDialog(
    devices: List<CloudDeviceChoice>,
    selectedKey: String,
    selectedState: CloudSettingsState?,
    json: String,
    isBusy: Boolean,
    isPullEnabled: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSelectedKeyChange: (String) -> Unit,
    onPreviewClick: () -> Unit,
    onPullClick: () -> Unit,
    onPushClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val selectedChoice = devices.firstOrNull { it.key == selectedKey }

    AlertDialog(
        onDismissRequest = {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.VirtualKey)
            onDismiss()
        },
        title = { Text(text = stringResource(R.string.prosthesis_settings)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.prosthesis_settings_dialog_hint),
                    style = MaterialTheme.typography.bodyMedium
                )

                CloudDeviceSelector(
                    devices = devices,
                    selectedKey = selectedKey,
                    selectedState = selectedState,
                    onSelectedKeyChange = onSelectedKeyChange
                )

                selectedChoice?.let { choice ->
                    val alias = choice.alias?.takeIf { it.isNotBlank() }
                    val statusText = cloudStatusText(choice, selectedState)

                    if (!alias.isNullOrBlank()) {
                        Text(
                            text = "${stringResource(R.string.alias)}: $alias",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Text(
                        text = "${stringResource(R.string.prosthesis_address)}: ${choice.address}",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    selectedState?.record?.let { record ->
                        Text(
                            text = "${stringResource(R.string.prosthesis_version)}: ${record.version}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (record.updatedAt.isNotBlank()) {
                            Text(
                                text = "${stringResource(R.string.prosthesis_updated_at)}: ${record.updatedAt}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = json,
                    onValueChange = {},
                    label = { Text(stringResource(R.string.label_json)) },
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )

                if (isBusy) {
                    Text(
                        text = stringResource(R.string.loading),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (!error.isNullOrBlank()) {
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
                        enabled = !isBusy && isPullEnabled,
                        onClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm)
                            onPullClick()
                        }
                    ) { Text(text = stringResource(R.string.prosthesis_pull)) }

                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = !isBusy,
                        onClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm)
                            onPushClick()
                        }
                    ) { Text(text = stringResource(R.string.prosthesis_push)) }
                }

                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isBusy,
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.ContextClick)
                        onPreviewClick()
                    }
                ) { Text(stringResource(R.string.prosthesis_preview)) }
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

/** Device dropdown used inside the cloud settings dialog. */
@Composable
private fun CloudDeviceSelector(
    devices: List<CloudDeviceChoice>,
    selectedKey: String,
    selectedState: CloudSettingsState?,
    onSelectedKeyChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedChoice = devices.firstOrNull { it.key == selectedKey }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.prosthesis_target_device),
            style = MaterialTheme.typography.bodySmall
        )

        Box {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { expanded = true }
            ) {
                Text(selectedChoice?.title ?: stringResource(R.string.prosthesis_select_target))
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                devices.forEach { choice ->
                    val state = if (choice.key == selectedKey) selectedState else null
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(choice.title)
                                Text(
                                    text = cloudStatusText(choice, state),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        },
                        onClick = {
                            expanded = false
                            onSelectedKeyChange(choice.key)
                        }
                    )
                }
            }
        }
    }
}

/** Converts cloud state into a localized status line. */
@Composable
private fun cloudStatusText(
    choice: CloudDeviceChoice,
    state: CloudSettingsState?
): String {
    val baseStatus = when {
        choice.device == null -> stringResource(R.string.prosthesis_local_device_not_registered)
        state?.record != null -> stringResource(
            R.string.prosthesis_server_settings_saved_version,
            state.record.version
        )

        state?.checked == true -> stringResource(R.string.prosthesis_server_settings_missing)
        !state?.errorKey.isNullOrBlank() -> stringResource(R.string.prosthesis_server_status_unknown)
        else -> stringResource(R.string.loading)
    }

    return if (choice.isConnectedDevice) {
        "${stringResource(R.string.prosthesis_connected_device)} • $baseStatus"
    } else {
        baseStatus
    }
}

/** Maps known error keys or returns raw text when mapping is not available. */
@Composable
private fun uiErrorTextOrRaw(raw: String?): String? {
    val normalized = raw?.trim()?.lowercase()?.replace("\n", "") ?: return null
    if (normalized.isBlank()) return null

    val unknown = stringResource(R.string.err_unknown)
    val mapped = when (normalized) {
        "prosthesis_no_settings_on_server" -> stringResource(R.string.prosthesis_no_settings_on_server)
        else -> uiErrorText(raw)
    }

    return if (mapped == unknown && !normalized.startsWith("http_")) raw else mapped
}

/** Formats ESP settings as readable JSON for cloud settings preview. */
private fun settingsToPrettyJson(s: EspSettings): String {
    return try {
        val obj = JSONObject(s.toJsonString())
        obj.put("pinSet", s.pinSet)
        obj.put("authRequired", s.authRequired)
        obj.toString(2)
    } catch (_: Exception) {
        s.toJsonString()
    }
}