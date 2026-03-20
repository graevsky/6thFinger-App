package com.example.a6thfingercontrollapp

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.a6thfingercontrollapp.data.AppSettingsStore
import com.example.a6thfingercontrollapp.ui.AccountScreen
import com.example.a6thfingercontrollapp.ui.ConnectScreen
import com.example.a6thfingercontrollapp.ui.ControlScreen
import com.example.a6thfingercontrollapp.ui.LoginScreen
import com.example.a6thfingercontrollapp.ui.NavRoute
import com.example.a6thfingercontrollapp.ui.PasswordResetScreen
import com.example.a6thfingercontrollapp.ui.PostRegisterAddEmailScreen
import com.example.a6thfingercontrollapp.ui.PostRegisterVerifyEmailScreen
import com.example.a6thfingercontrollapp.ui.RecoveryCodesScreen
import com.example.a6thfingercontrollapp.ui.RegisterScreen
import com.example.a6thfingercontrollapp.ui.SimulationScreen
import com.example.a6thfingercontrollapp.ui.StartScreen
import com.example.a6thfingercontrollapp.ui.theme._6thFingerControllAppTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

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

class MainActivity : ComponentActivity() {
    private val vm by viewModels<BleViewModel>()
    private val authVm by viewModels<AuthViewModel>()

    override fun attachBaseContext(newBase: Context) {
        val prefs = AppSettingsStore(newBase)
        val lang = runBlocking { prefs.getLanguage().first() }
        val ctx = LocaleManager.setLocale(newBase, lang)
        super.attachBaseContext(ctx)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            _6thFingerControllAppTheme {
                val nav = rememberNavController()
                val scope = rememberCoroutineScope()

                val permissions = remember { requiredPermissions() }
                var granted by remember { mutableStateOf(false) }
                val launcher =
                    rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestMultiplePermissions()
                    ) { res -> granted = res.all { it.value } }

                LaunchedEffect(Unit) { launcher.launch(permissions) }

                val authState by authVm.auth.collectAsState()
                val pendingRecovery by authVm.pendingRecoveryCodes.collectAsState()

                var authFlowScreen by rememberSaveable { mutableStateOf(AuthFlowScreen.Start) }
                var prefillUsername by rememberSaveable { mutableStateOf("") }

                var postEmail by rememberSaveable { mutableStateOf("") }
                var postCode by rememberSaveable { mutableStateOf("") }
                var postLoading by remember { mutableStateOf(false) }
                var postErrKey by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(authFlowScreen, pendingRecovery) {
                    if (
                        (authFlowScreen == AuthFlowScreen.RecoveryCodes ||
                                authFlowScreen == AuthFlowScreen.PostRegisterEmail ||
                                authFlowScreen == AuthFlowScreen.PostRegisterEmailCode) &&
                        pendingRecovery == null
                    ) {
                        authFlowScreen = AuthFlowScreen.Login
                    }
                }

                val overlayActive =
                    when (authFlowScreen) {
                        AuthFlowScreen.RecoveryCodes -> pendingRecovery != null
                        AuthFlowScreen.PostRegisterEmail -> true
                        AuthFlowScreen.PostRegisterEmailCode -> true
                        AuthFlowScreen.ForgotPassword -> true
                        AuthFlowScreen.ChangePassword -> true
                        else -> false
                    }

                if (overlayActive) {
                    when (authFlowScreen) {
                        AuthFlowScreen.RecoveryCodes -> {
                            val data = pendingRecovery!!
                            RecoveryCodesScreen(
                                username = data.username,
                                codes = data.codes,
                                onBack = { authFlowScreen = AuthFlowScreen.Register },
                                onContinue = {
                                    prefillUsername = data.username
                                    postErrKey = null
                                    authFlowScreen = AuthFlowScreen.PostRegisterEmail
                                }
                            )
                        }

                        AuthFlowScreen.PostRegisterEmail -> {
                            PostRegisterAddEmailScreen(
                                initialEmail = postEmail,
                                loading = postLoading,
                                errorKey = postErrKey,
                                onBack = { authFlowScreen = AuthFlowScreen.Login },
                                onSkip = {
                                    postErrKey = null
                                    postLoading = true
                                    scope.launch {
                                        try {
                                            authVm.postRegisterFinishWithoutEmail()
                                            authFlowScreen = AuthFlowScreen.Start
                                        } catch (e: Exception) {
                                            postErrKey = e.message
                                        } finally {
                                            postLoading = false
                                        }
                                    }
                                },
                                onStartAdd = { email ->
                                    postEmail = email
                                    postErrKey = null
                                    postLoading = true
                                    scope.launch {
                                        try {
                                            authVm.postRegisterEmailStart(email)
                                            postCode = ""
                                            authFlowScreen = AuthFlowScreen.PostRegisterEmailCode
                                        } catch (e: Exception) {
                                            postErrKey = e.message
                                        } finally {
                                            postLoading = false
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
                                onCodeChange = { postCode = it },
                                onBackChangeEmail = { authFlowScreen = AuthFlowScreen.Login },
                                onResend = {
                                    postErrKey = null
                                    postLoading = true
                                    scope.launch {
                                        try {
                                            authVm.emailStartAdd(postEmail)
                                        } catch (e: Exception) {
                                            postErrKey = e.message
                                        } finally {
                                            postLoading = false
                                        }
                                    }
                                },
                                onConfirm = {
                                    postErrKey = null
                                    postLoading = true
                                    scope.launch {
                                        try {
                                            authVm.postRegisterEmailConfirm(postEmail, postCode)
                                            authFlowScreen = AuthFlowScreen.Start
                                        } catch (e: Exception) {
                                            postErrKey = e.message
                                        } finally {
                                            postLoading = false
                                        }
                                    }
                                }
                            )
                        }

                        AuthFlowScreen.ForgotPassword -> {
                            PasswordResetScreen(
                                authVm = authVm,
                                initialUsername = prefillUsername,
                                onBack = { authFlowScreen = AuthFlowScreen.Login },
                                onFinishedGoToLogin = { u ->
                                    prefillUsername = u
                                    authFlowScreen = AuthFlowScreen.Login
                                }
                            )
                        }

                        AuthFlowScreen.ChangePassword -> {
                            PasswordResetScreen(
                                authVm = authVm,
                                initialUsername = prefillUsername,
                                skipUsername = true,
                                onBack = { authFlowScreen = AuthFlowScreen.Start },
                                onFinishedGoToLogin = { _ -> authFlowScreen = AuthFlowScreen.Start }
                            )
                        }

                        else -> Unit
                    }
                    return@_6thFingerControllAppTheme
                }

                when (authState) {
                    is UiAuthState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) { CircularProgressIndicator() }
                    }

                    is UiAuthState.Unauthenticated -> {
                        when (authFlowScreen) {
                            AuthFlowScreen.Start -> {
                                StartScreen(
                                    bleVm = vm,
                                    authVm = authVm,
                                    onLoginClick = { authFlowScreen = AuthFlowScreen.Login },
                                    onRegisterClick = { authFlowScreen = AuthFlowScreen.Register },
                                    onContinueAsGuest = { authVm.continueAsGuest() }
                                )
                            }

                            AuthFlowScreen.Login -> {
                                LoginScreen(
                                    vm = authVm,
                                    initialUsername = prefillUsername,
                                    onBack = { authFlowScreen = AuthFlowScreen.Start },
                                    onForgotPassword = { u ->
                                        prefillUsername = u
                                        authFlowScreen = AuthFlowScreen.ForgotPassword
                                    }
                                )
                            }

                            AuthFlowScreen.Register -> {
                                RegisterScreen(
                                    vm = authVm,
                                    onBack = { authFlowScreen = AuthFlowScreen.Start },
                                    onRegistered = { username ->
                                        prefillUsername = username
                                        authFlowScreen = AuthFlowScreen.RecoveryCodes
                                    },
                                    onGoToLogin = { u ->
                                        prefillUsername = u
                                        authFlowScreen = AuthFlowScreen.Login
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

                    is UiAuthState.Guest, is UiAuthState.LoggedIn -> {
                        val routes =
                            listOf(
                                NavRoute.Connect,
                                NavRoute.Control,
                                NavRoute.Sim,
                                NavRoute.Account
                            )

                        val backStack by nav.currentBackStackEntryAsState()
                        val currentRoute = backStack?.destination?.route ?: NavRoute.Connect.route
                        val bleState by vm.state.collectAsState()
                        val unlocked by vm.controlUnlocked.collectAsState()

                        val rawStatus = bleState.status.lowercase()
                        val connected =
                            when {
                                "disconnected" in rawStatus -> false
                                "subscribed" in rawStatus -> true
                                "tele" in rawStatus -> true
                                "config" in rawStatus -> true
                                "ack" in rawStatus -> true
                                "auth" in rawStatus -> true
                                else -> false
                            }

                        LaunchedEffect(connected, unlocked, currentRoute) {
                            if ((!connected || !unlocked) &&
                                (currentRoute == NavRoute.Control.route ||
                                        currentRoute == NavRoute.Sim.route)
                            ) {
                                nav.navigate(NavRoute.Connect.route) {
                                    launchSingleTop = true
                                    restoreState = true
                                    popUpTo(nav.graph.startDestinationId) { saveState = true }
                                }
                            }
                        }

                        Scaffold(
                            bottomBar = {
                                NavigationBar {
                                    routes.forEach { r ->
                                        val enabled =
                                            when (r) {
                                                NavRoute.Connect -> true
                                                NavRoute.Account -> true
                                                else -> connected && unlocked
                                            }

                                        NavigationBarItem(
                                            selected = currentRoute == r.route,
                                            onClick = {
                                                if (enabled) {
                                                    nav.navigate(r.route) {
                                                        launchSingleTop = true
                                                        restoreState = true
                                                        popUpTo(nav.graph.startDestinationId) {
                                                            saveState = true
                                                        }
                                                    }
                                                }
                                            },
                                            icon = {
                                                when (r) {
                                                    NavRoute.Connect -> Icon(
                                                        Icons.Default.Bluetooth,
                                                        null
                                                    )

                                                    NavRoute.Control -> Icon(
                                                        Icons.Default.Settings,
                                                        null
                                                    )

                                                    NavRoute.Sim -> Icon(
                                                        Icons.Default.PlayArrow,
                                                        null
                                                    )

                                                    NavRoute.Account -> Icon(
                                                        Icons.Default.Person,
                                                        null
                                                    )
                                                }
                                            },
                                            label = { Text(stringResource(r.labelRes)) },
                                            enabled = enabled
                                        )
                                    }
                                }
                            }
                        ) { innerPadding ->
                            NavHost(
                                navController = nav,
                                startDestination = NavRoute.Connect.route,
                                modifier = Modifier.padding(innerPadding)
                            ) {
                                composable(NavRoute.Connect.route) {
                                    ConnectScreen(vm = vm, permissionsGranted = granted)
                                }
                                composable(NavRoute.Control.route) {
                                    ControlScreen(vm = vm, authVm = authVm)
                                }
                                composable(NavRoute.Sim.route) {
                                    SimulationScreen(vm = vm)
                                }
                                composable(NavRoute.Account.route) {
                                    AccountScreen(
                                        vm = vm,
                                        authVm = authVm,
                                        onLoginClick = {
                                            authFlowScreen = AuthFlowScreen.Login
                                            authVm.logout()
                                        },
                                        onRegisterClick = {
                                            authFlowScreen = AuthFlowScreen.Register
                                            authVm.logout()
                                        },
                                        onOpenControl = {
                                            nav.navigate(NavRoute.Control.route) {
                                                launchSingleTop = true
                                                restoreState = true
                                                popUpTo(nav.graph.startDestinationId) {
                                                    saveState = true
                                                }
                                            }
                                        },
                                        onChangePassword = { u ->
                                            prefillUsername = u
                                            authFlowScreen = AuthFlowScreen.ChangePassword
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        vm.start()
    }
}

private fun requiredPermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= 31) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}