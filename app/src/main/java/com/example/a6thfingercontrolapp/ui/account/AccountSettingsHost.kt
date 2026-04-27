package com.example.a6thfingercontrolapp.ui.account

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import com.example.a6thfingercontrolapp.BuildConfig
import com.example.a6thfingercontrolapp.R
import com.example.a6thfingercontrolapp.account.AccountViewModel
import com.example.a6thfingercontrolapp.preferences.AppPreferencesViewModel
import com.example.a6thfingercontrolapp.ui.common.SettingsDialog
import com.example.a6thfingercontrolapp.ui.common.SettingsLink
import com.example.a6thfingercontrolapp.utils.uiErrorText

/**
 * Shared settings dialog for the account screen.
 */
@Composable
internal fun AccountSettingsHost(
    visible: Boolean,
    lang: String,
    theme: String?,
    username: String?,
    appPreferencesVm: AppPreferencesViewModel,
    accountVm: AccountViewModel,
    emailState: AccountEmailUiState,
    onVisibleChange: (Boolean) -> Unit,
    onChangePassword: (String) -> Unit
) {
    if (!visible) return

    val haptic = LocalHapticFeedback.current
    val activity = LocalContext.current as? Activity

    val emailErrText = uiErrorText(emailState.emailErrorKey)
    val emailLine =
        when {
            emailState.emailLoading -> stringResource(R.string.loading)
            !emailErrText.isNullOrBlank() && !emailState.emailShown.isNullOrBlank() -> {
                stringResource(R.string.account_email_current, emailState.emailShown!!)
            }

            !emailErrText.isNullOrBlank() && emailState.emailShown.isNullOrBlank() -> {
                stringResource(R.string.account_email_not_set)
            }

            emailState.hasEmail && !emailState.emailShown.isNullOrBlank() -> {
                stringResource(R.string.account_email_current, emailState.emailShown!!)
            }

            else -> stringResource(R.string.account_email_not_set)
        }

    val links = listOf(
        SettingsLink(
            title = stringResource(R.string.settings_link_guide),
            url = BuildConfig.APP_GUIDE_URL
        ),
        SettingsLink(
            title = stringResource(R.string.settings_link_app),
            url = BuildConfig.APP_REPOSITORY_URL
        ),
        SettingsLink(
            title = stringResource(R.string.settings_link_esp32_firmware),
            url = BuildConfig.ESP32_FIRMWARE_URL
        ),
        SettingsLink(
            title = stringResource(R.string.settings_link_backend),
            url = BuildConfig.BACKEND_REPOSITORY_URL
        )
    )

    SettingsDialog(
        currentLang = lang,
        onDismiss = {
            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
            onVisibleChange(false)
        },
        onSelect = { newLang ->
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            appPreferencesVm.setAppLanguage(newLang) {
                if (username != null) {
                    accountVm.updateLanguageRemote(newLang)
                }
                onVisibleChange(false)
                activity?.recreate()
            }
        },
        currentTheme = theme,
        onThemeSelect = { appPreferencesVm.setAppTheme(it) },
        isLoggedIn = username != null,
        emailLine = emailLine,
        emailErrorLine = emailErrText,
        hasEmail = emailState.hasEmail,
        onAddEmail = {
            onVisibleChange(false)
            emailState.openAddEmail()
        },
        onChangeEmail = {
            onVisibleChange(false)
            emailState.openChangeEmail()
        },
        onRemoveEmail = {
            onVisibleChange(false)
            emailState.openRemoveEmail()
        },
        onChangePassword = {
            onVisibleChange(false)
            username?.let { onChangePassword(it) }
        },
        links = links
    )
}
