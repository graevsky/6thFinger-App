package com.example.a6thfingercontrolapp.ui.account

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView.CropShape
import com.canhub.cropper.CropImageView.Guidelines
import com.example.a6thfingercontrolapp.R
import com.example.a6thfingercontrolapp.account.AccountViewModel
import com.example.a6thfingercontrolapp.data.AppSettingsStore
import com.example.a6thfingercontrolapp.data.saveAvatarFromCroppedUri
import com.example.a6thfingercontrolapp.ui.account.components.AccountProfileSection
import com.example.a6thfingercontrolapp.ui.account.components.FullscreenImageDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.a6thfingercontrolapp.data.avatarFile as dataAvatarFile
import com.example.a6thfingercontrolapp.data.deleteAvatarIfExists as dataDeleteAvatarIfExists
import com.example.a6thfingercontrolapp.data.loadBitmapFromFile as dataLoadBitmapFromFile

/**
 * Avatar loading, cropper integration and profile actions for the account screen.
 */
@Composable
internal fun AccountProfileHost(
    username: String?,
    accountVm: AccountViewModel,
    settingsStore: AppSettingsStore,
    onLoginClick: () -> Unit,
    onRegisterClick: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val avatarPath by settingsStore.getAvatarPath().collectAsState(initial = null)
    var avatarBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var avatarMenuOpen by remember { mutableStateOf(false) }
    var showFullscreen by remember { mutableStateOf(false) }
    var cropError by remember { mutableStateOf<String?>(null) }

    val avatarErrTitle = stringResource(R.string.avatar_error_title)
    val avatarErrSaveFailed = stringResource(R.string.avatar_error_save_failed)
    val avatarErrCropResult = stringResource(R.string.avatar_error_crop_result)
    val cropTitle = stringResource(R.string.avatar_crop_title)
    val cropDone = stringResource(R.string.avatar_crop_done)

    LaunchedEffect(avatarPath) {
        avatarBitmap = withContext(Dispatchers.IO) {
            dataLoadBitmapFromFile(avatarPath, maxDim = 1024)?.asImageBitmap()
        }
    }

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
                        saveAvatarFromCroppedUri(
                            context,
                            uri,
                            outSize = 512,
                            quality = 92
                        )
                    }

                    if (!savedPath.isNullOrBlank()) {
                        val newBitmap = withContext(Dispatchers.IO) {
                            dataLoadBitmapFromFile(savedPath, maxDim = 1024)?.asImageBitmap()
                        }

                        avatarBitmap = newBitmap
                        settingsStore.setAvatarPath(savedPath)

                        if (username != null) {
                            accountVm.scheduleAvatarUpload(savedPath)
                        }
                    } else {
                        cropError = avatarErrSaveFailed
                    }
                }
            } else {
                cropError = avatarErrCropResult
            }
        } else {
            val message = result.error?.message
            if (!message.isNullOrBlank()) cropError = message
        }
    }

    val pickImageLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) cropLauncher.launch(CropImageContractOptions(uri, cropOptions))
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

            if (username != null) {
                accountVm.deleteAvatarRemote()
            }
        }
    }

    AccountProfileSection(
        username = username,
        avatarBitmap = avatarBitmap,
        avatarMenuOpen = avatarMenuOpen,
        onAvatarMenuOpenChange = { avatarMenuOpen = it },
        onAvatarOpenFullscreen = { showFullscreen = true },
        onPickAvatar = { startPickAndCrop() },
        onRemoveAvatar = { removeAvatar() },
        onLoginClick = {
            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
            onLoginClick()
        },
        onRegisterClick = {
            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
            onRegisterClick()
        }
    )

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
}
