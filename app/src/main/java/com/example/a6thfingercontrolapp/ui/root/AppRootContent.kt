package com.example.a6thfingercontrolapp.ui.root

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.a6thfingercontrolapp.account.AccountViewModel
import com.example.a6thfingercontrolapp.auth.AuthViewModel
import com.example.a6thfingercontrolapp.auth.PendingRecoveryCodes
import com.example.a6thfingercontrolapp.auth.UiAuthState
import com.example.a6thfingercontrolapp.ble.BleViewModel
import com.example.a6thfingercontrolapp.preferences.AppPreferencesViewModel
import com.example.a6thfingercontrolapp.ui.auth.LoginScreen
import com.example.a6thfingercontrolapp.ui.auth.PostRegisterAddEmailScreen
import com.example.a6thfingercontrolapp.ui.auth.PostRegisterVerifyEmailScreen
import com.example.a6thfingercontrolapp.ui.auth.RecoveryCodesScreen
import com.example.a6thfingercontrolapp.ui.auth.RegisterScreen
import com.example.a6thfingercontrolapp.ui.auth.StartScreen
import com.example.a6thfingercontrolapp.ui.common.DEFAULT_CODE_RESEND_COOLDOWN_SECONDS
import com.example.a6thfingercontrolapp.ui.common.nextCooldownDeadline
import com.example.a6thfingercontrolapp.ui.common.rememberCooldownRemainingSeconds
import com.example.a6thfingercontrolapp.ui.passwordreset.PasswordResetScreen
import com.example.a6thfingercontrolapp.ui.theme._6thFingerControllAppTheme
import kotlinx.coroutines.launch

/** Auth flow screens. */
private enum class AuthFlowScreen {
    Start,
    Login,
    Register,
    RecoveryCodes,
    PostRegisterEmail,
    PostRegisterEmailCode,
    ForgotPassword,
    ChangePassword
}

/** Root Compose host for theme, permissions, auth overlay and the main tabbed app. */
@Composable
fun AppRootContent(
    vm: BleViewModel,
    authVm: AuthViewModel,
    accountVm: AccountViewModel,
    appPreferencesVm: AppPreferencesViewModel
) {
    val appTheme by appPreferencesVm.appTheme.collectAsState()

    _6thFingerControllAppTheme(themeMode = appTheme) {
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val permissionSteps = remember { requiredBlePermissionSteps() }
        var permissionsGranted by remember(context) {
            mutableStateOf(permissionSteps.all { hasPermission(context, it.permission) })
        }

        if (!permissionsGranted) {
            PermissionsOnboardingContent(
                appPreferencesVm = appPreferencesVm,
                steps = permissionSteps,
                onAllGranted = { permissionsGranted = true }
            )
            return@_6thFingerControllAppTheme
        }

        val authState by authVm.auth.collectAsState()
        val pendingRecovery by authVm.pendingRecoveryCodes.collectAsState()

        var authFlowScreen by rememberSaveable { mutableStateOf(AuthFlowScreen.Start) }
        var prefillUsername by rememberSaveable { mutableStateOf("") }

        var postEmail by rememberSaveable { mutableStateOf("") }
        var postCode by rememberSaveable { mutableStateOf("") }
        var postCodeRequested by rememberSaveable { mutableStateOf(false) }
        var postResendCooldownUntilMs by rememberSaveable { mutableStateOf(0L) }
        var postLoading by remember { mutableStateOf(false) }
        var postErrKey by rememberSaveable { mutableStateOf<String?>(null) }

        LaunchedEffect(authFlowScreen, pendingRecovery) {
            if ((authFlowScreen == AuthFlowScreen.RecoveryCodes ||
                        authFlowScreen == AuthFlowScreen.PostRegisterEmail ||
                        authFlowScreen == AuthFlowScreen.PostRegisterEmailCode) &&
                pendingRecovery == null
            ) {
                authFlowScreen = AuthFlowScreen.Login
            }
        }

        if (showsAuthOverlay(authFlowScreen, pendingRecovery)) {
            AuthOverlayContent(
                authVm = authVm,
                authFlowScreen = authFlowScreen,
                pendingRecovery = pendingRecovery,
                prefillUsername = prefillUsername,
                onPrefillUsernameChange = { prefillUsername = it },
                postEmail = postEmail,
                onPostEmailChange = { postEmail = it },
                postCode = postCode,
                onPostCodeChange = { postCode = it },
                postCodeRequested = postCodeRequested,
                onPostCodeRequestedChange = { postCodeRequested = it },
                postResendCooldownUntilMs = postResendCooldownUntilMs,
                onPostResendCooldownUntilChange = { postResendCooldownUntilMs = it },
                postLoading = postLoading,
                onPostLoadingChange = { postLoading = it },
                postErrKey = postErrKey,
                onPostErrKeyChange = { postErrKey = it },
                onAuthFlowScreenChange = { authFlowScreen = it },
                onResetPostRegisterFields = {
                    postEmail = ""
                    postCode = ""
                    postCodeRequested = false
                    postResendCooldownUntilMs = 0L
                },
                scopeLaunch = { block -> scope.launch { block() } }
            )
            return@_6thFingerControllAppTheme
        }

        when (authState) {
            is UiAuthState.Loading -> LoadingContent()

            is UiAuthState.Unauthenticated -> {
                UnauthenticatedContent(
                    authVm = authVm,
                    appPreferencesVm = appPreferencesVm,
                    authFlowScreen = authFlowScreen,
                    prefillUsername = prefillUsername,
                    onPrefillUsernameChange = { prefillUsername = it },
                    onAuthFlowScreenChange = { authFlowScreen = it }
                )
            }

            is UiAuthState.Guest, is UiAuthState.LoggedIn -> {
                MainTabsHost(
                    vm = vm,
                    authVm = authVm,
                    accountVm = accountVm,
                    appPreferencesVm = appPreferencesVm,
                    permissionsGranted = permissionsGranted,
                    onOpenLogin = { authFlowScreen = AuthFlowScreen.Login },
                    onOpenRegister = { authFlowScreen = AuthFlowScreen.Register },
                    onOpenChangePassword = { username ->
                        prefillUsername = username
                        authFlowScreen = AuthFlowScreen.ChangePassword
                    }
                )
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun UnauthenticatedContent(
    authVm: AuthViewModel,
    appPreferencesVm: AppPreferencesViewModel,
    authFlowScreen: AuthFlowScreen,
    prefillUsername: String,
    onPrefillUsernameChange: (String) -> Unit,
    onAuthFlowScreenChange: (AuthFlowScreen) -> Unit
) {
    when (authFlowScreen) {
        AuthFlowScreen.Start -> {
            StartScreen(
                appPreferencesVm = appPreferencesVm,
                onLoginClick = { onAuthFlowScreenChange(AuthFlowScreen.Login) },
                onRegisterClick = { onAuthFlowScreenChange(AuthFlowScreen.Register) },
                onContinueAsGuest = { authVm.continueAsGuest() }
            )
        }

        AuthFlowScreen.Login -> {
            LoginScreen(
                vm = authVm,
                initialUsername = prefillUsername,
                onBack = { onAuthFlowScreenChange(AuthFlowScreen.Start) },
                onForgotPassword = { username ->
                    onPrefillUsernameChange(username)
                    onAuthFlowScreenChange(AuthFlowScreen.ForgotPassword)
                }
            )
        }

        AuthFlowScreen.Register -> {
            RegisterScreen(
                vm = authVm,
                onBack = { onAuthFlowScreenChange(AuthFlowScreen.Start) },
                onRegistered = { username ->
                    onPrefillUsernameChange(username)
                    onAuthFlowScreenChange(AuthFlowScreen.RecoveryCodes)
                },
                onGoToLogin = { username ->
                    onPrefillUsernameChange(username)
                    onAuthFlowScreenChange(AuthFlowScreen.Login)
                }
            )
        }

        AuthFlowScreen.RecoveryCodes,
        AuthFlowScreen.PostRegisterEmail,
        AuthFlowScreen.PostRegisterEmailCode,
        AuthFlowScreen.ForgotPassword,
        AuthFlowScreen.ChangePassword -> Unit
    }
}

@Composable
private fun AuthOverlayContent(
    authVm: AuthViewModel,
    authFlowScreen: AuthFlowScreen,
    pendingRecovery: PendingRecoveryCodes?,
    prefillUsername: String,
    onPrefillUsernameChange: (String) -> Unit,
    postEmail: String,
    onPostEmailChange: (String) -> Unit,
    postCode: String,
    onPostCodeChange: (String) -> Unit,
    postCodeRequested: Boolean,
    onPostCodeRequestedChange: (Boolean) -> Unit,
    postResendCooldownUntilMs: Long,
    onPostResendCooldownUntilChange: (Long) -> Unit,
    postLoading: Boolean,
    onPostLoadingChange: (Boolean) -> Unit,
    postErrKey: String?,
    onPostErrKeyChange: (String?) -> Unit,
    onAuthFlowScreenChange: (AuthFlowScreen) -> Unit,
    onResetPostRegisterFields: () -> Unit,
    scopeLaunch: (suspend () -> Unit) -> Unit
) {
    val postResendCooldownSeconds = rememberCooldownRemainingSeconds(postResendCooldownUntilMs)

    when (authFlowScreen) {
        AuthFlowScreen.RecoveryCodes -> {
            val data = pendingRecovery ?: return
            RecoveryCodesScreen(
                username = data.username,
                codes = data.codes,
                onBack = { onAuthFlowScreenChange(AuthFlowScreen.Register) },
                onContinue = {
                    onPrefillUsernameChange(data.username)
                    onPostErrKeyChange(null)
                    onPostLoadingChange(false)
                    onAuthFlowScreenChange(AuthFlowScreen.PostRegisterEmail)
                }
            )
        }

        AuthFlowScreen.PostRegisterEmail -> {
            PostRegisterAddEmailScreen(
                initialEmail = postEmail,
                loading = postLoading,
                errorKey = postErrKey,
                hasPendingVerification = postCodeRequested,
                onBack = {
                    onPostErrKeyChange(null)
                    onPostLoadingChange(false)
                    onAuthFlowScreenChange(
                        if (pendingRecovery != null) AuthFlowScreen.RecoveryCodes
                        else AuthFlowScreen.Login
                    )
                },
                onSkip = {
                    onPostErrKeyChange(null)
                    onPostLoadingChange(true)
                    scopeLaunch {
                        try {
                            authVm.postRegisterFinishWithoutEmail()
                            onResetPostRegisterFields()
                            onAuthFlowScreenChange(AuthFlowScreen.Start)
                        } catch (e: Exception) {
                            onPostErrKeyChange(e.message)
                        } finally {
                            onPostLoadingChange(false)
                        }
                    }
                },
                onStartAdd = { email ->
                    onPostEmailChange(email)
                    onPostErrKeyChange(null)
                    if (postCodeRequested && email.equals(postEmail, ignoreCase = true)) {
                        onAuthFlowScreenChange(AuthFlowScreen.PostRegisterEmailCode)
                    } else {
                        onPostLoadingChange(true)
                        scopeLaunch {
                            try {
                                authVm.postRegisterEmailStart(email)
                                onPostCodeRequestedChange(true)
                                onPostCodeChange("")
                                onPostResendCooldownUntilChange(
                                    nextCooldownDeadline(DEFAULT_CODE_RESEND_COOLDOWN_SECONDS)
                                )
                                onAuthFlowScreenChange(AuthFlowScreen.PostRegisterEmailCode)
                            } catch (e: Exception) {
                                onPostErrKeyChange(e.message)
                            } finally {
                                onPostLoadingChange(false)
                            }
                        }
                    }
                }
            )
        }

        AuthFlowScreen.PostRegisterEmailCode -> {
            PostRegisterVerifyEmailScreen(
                email = postEmail,
                loading = postLoading,
                errorKey = postErrKey,
                code = postCode,
                resendCooldownSeconds = postResendCooldownSeconds,
                onCodeChange = onPostCodeChange,
                onBackChangeEmail = {
                    onPostErrKeyChange(null)
                    onPostLoadingChange(false)
                    onAuthFlowScreenChange(AuthFlowScreen.PostRegisterEmail)
                },
                onResend = {
                    onPostErrKeyChange(null)
                    onPostLoadingChange(true)
                    scopeLaunch {
                        try {
                            authVm.emailStartAdd(postEmail)
                            onPostResendCooldownUntilChange(
                                nextCooldownDeadline(DEFAULT_CODE_RESEND_COOLDOWN_SECONDS)
                            )
                        } catch (e: Exception) {
                            onPostErrKeyChange(e.message)
                        } finally {
                            onPostLoadingChange(false)
                        }
                    }
                },
                onConfirm = {
                    onPostErrKeyChange(null)
                    onPostLoadingChange(true)
                    scopeLaunch {
                        try {
                            authVm.postRegisterEmailConfirm(postEmail, postCode)
                            onResetPostRegisterFields()
                            onAuthFlowScreenChange(AuthFlowScreen.Start)
                        } catch (e: Exception) {
                            onPostErrKeyChange(e.message)
                        } finally {
                            onPostLoadingChange(false)
                        }
                    }
                }
            )
        }

        AuthFlowScreen.ForgotPassword -> {
            PasswordResetScreen(
                authVm = authVm,
                initialUsername = prefillUsername,
                onBack = { onAuthFlowScreenChange(AuthFlowScreen.Login) },
                onFinishedGoToLogin = { username ->
                    onPrefillUsernameChange(username)
                    onAuthFlowScreenChange(AuthFlowScreen.Login)
                }
            )
        }

        AuthFlowScreen.ChangePassword -> {
            PasswordResetScreen(
                authVm = authVm,
                initialUsername = prefillUsername,
                skipUsername = true,
                onBack = { onAuthFlowScreenChange(AuthFlowScreen.Start) },
                onFinishedGoToLogin = { _ ->
                    onAuthFlowScreenChange(AuthFlowScreen.Start)
                }
            )
        }

        AuthFlowScreen.Start,
        AuthFlowScreen.Login,
        AuthFlowScreen.Register -> Unit
    }
}

private fun showsAuthOverlay(
    authFlowScreen: AuthFlowScreen,
    pendingRecovery: PendingRecoveryCodes?
): Boolean {
    return when (authFlowScreen) {
        AuthFlowScreen.RecoveryCodes -> pendingRecovery != null
        AuthFlowScreen.PostRegisterEmail -> true
        AuthFlowScreen.PostRegisterEmailCode -> true
        AuthFlowScreen.ForgotPassword -> true
        AuthFlowScreen.ChangePassword -> true
        else -> false
    }
}
