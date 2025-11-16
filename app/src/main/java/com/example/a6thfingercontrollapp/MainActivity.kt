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
import com.example.a6thfingercontrollapp.ui.RegisterScreen
import com.example.a6thfingercontrollapp.ui.SimulationScreen
import com.example.a6thfingercontrollapp.ui.StartScreen
import com.example.a6thfingercontrollapp.ui.theme._6thFingerControllAppTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

private enum class AuthFlowScreen {
    Start,
    Login,
    Register
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

                val permissions = remember { requiredPermissions() }
                var granted by remember { mutableStateOf(false) }
                val launcher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { res -> granted = res.all { it.value } }

                LaunchedEffect(Unit) { launcher.launch(permissions) }

                val authState by authVm.auth.collectAsState()

                var authFlowScreen by rememberSaveable { mutableStateOf(AuthFlowScreen.Start) }
                var prefillUsername by rememberSaveable { mutableStateOf("") }

                when (authState) {
                    is UiAuthState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
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
                                    onBack = { authFlowScreen = AuthFlowScreen.Start }
                                )
                            }

                            AuthFlowScreen.Register -> {
                                RegisterScreen(
                                    vm = authVm,
                                    onBack = { authFlowScreen = AuthFlowScreen.Start },
                                    onRegistered = { username ->
                                        prefillUsername = username
                                        authFlowScreen = AuthFlowScreen.Login
                                    }
                                )
                            }
                        }
                    }

                    is UiAuthState.Guest,
                    is UiAuthState.LoggedIn -> {
                        val routes = listOf(
                            NavRoute.Connect,
                            NavRoute.Control,
                            NavRoute.Sim,
                            NavRoute.Account
                        )

                        val backStack by nav.currentBackStackEntryAsState()
                        val current = backStack?.destination?.route ?: NavRoute.Connect.route
                        val state by vm.state.collectAsState()

                        Scaffold(
                            bottomBar = {
                                NavigationBar {
                                    routes.forEach { r ->
                                        val enabled = when (r) {
                                            NavRoute.Connect -> true
                                            NavRoute.Account -> true
                                            else -> state.status.contains("Subscribed", true) ||
                                                    state.status.contains("Connected", true)
                                        }

                                        NavigationBarItem(
                                            selected = current == r.route,
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
                                    ControlScreen(vm = vm)
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

    fun recreateApp() {
        val intent = intent
        finish()
        startActivity(intent)
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
