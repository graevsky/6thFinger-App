package com.example.a6thfingercontrollapp

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.a6thfingercontrollapp.ui.AccountScreen
import com.example.a6thfingercontrollapp.ui.ConnectScreen
import com.example.a6thfingercontrollapp.ui.ControlScreen
import com.example.a6thfingercontrollapp.ui.NavRoute
import com.example.a6thfingercontrollapp.ui.theme._6thFingerControllAppTheme

class MainActivity : ComponentActivity() {
    private val vm by viewModels<BleViewModel>()

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
                                    NavRoute.Account -> true // аккаунт всегда доступен
                                    else -> state.status.contains("Subscribed") ||
                                            state.status.contains("Connected")
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
                                            NavRoute.Connect -> Icon(Icons.Default.Bluetooth, null)
                                            NavRoute.Control -> Icon(Icons.Default.Settings, null)
                                            NavRoute.Sim -> Icon(Icons.Default.PlayArrow, null)
                                            NavRoute.Account -> Icon(Icons.Default.Person, null)
                                        }
                                    },
                                    label = { Text(r.label) },
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
                            Text("Симуляция (пока пусто)")
                        }
                        composable(NavRoute.Account.route) {
                            AccountScreen(vm = vm)
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
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}
