package com.example.a6thfingercontrolapp.ui.root

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.a6thfingercontrolapp.account.AccountViewModel
import com.example.a6thfingercontrolapp.auth.AuthViewModel
import com.example.a6thfingercontrolapp.ble.BleViewModel
import com.example.a6thfingercontrolapp.ble.classifyBleStatus
import com.example.a6thfingercontrolapp.preferences.AppPreferencesViewModel
import com.example.a6thfingercontrolapp.ui.account.AccountScreen
import com.example.a6thfingercontrolapp.ui.connect.ConnectScreen
import com.example.a6thfingercontrolapp.ui.control.ControlScreen
import com.example.a6thfingercontrolapp.ui.navigation.NavRoute
import com.example.a6thfingercontrolapp.ui.simulation.SimulationScreen

/** Main tabs application host shown for guest and logged-in sessions. */
@Composable
fun MainTabsHost(
    vm: BleViewModel,
    authVm: AuthViewModel,
    accountVm: AccountViewModel,
    appPreferencesVm: AppPreferencesViewModel,
    permissionsGranted: Boolean,
    onOpenLogin: () -> Unit,
    onOpenRegister: () -> Unit,
    onOpenChangePassword: (String) -> Unit
) {
    val navController = rememberNavController()
    val routes = listOf(
        NavRoute.Connect,
        NavRoute.Control,
        NavRoute.Sim,
        NavRoute.Account
    )

    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: NavRoute.Connect.route
    val bleState by vm.state.collectAsState()
    val unlocked by vm.controlUnlocked.collectAsState()

    val bleSession = classifyBleStatus(bleState.status, unlocked)
    val controlReady = bleSession.controlReady

    // Control and Simulation screens require an active unlocked BLE session.
    LaunchedEffect(controlReady, currentRoute) {
        if (!controlReady &&
            (currentRoute == NavRoute.Control.route || currentRoute == NavRoute.Sim.route)
        ) {
            navController.navigate(NavRoute.Connect.route) {
                launchSingleTop = true
                restoreState = true
                popUpTo(navController.graph.startDestinationId) { saveState = true }
            }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                routes.forEach { route ->
                    val enabled = when (route) {
                        NavRoute.Connect -> true
                        NavRoute.Account -> true
                        else -> controlReady
                    }

                    NavigationBarItem(
                        selected = currentRoute == route.route,
                        onClick = {
                            if (enabled) {
                                navController.navigate(route.route) {
                                    launchSingleTop = true
                                    restoreState = true
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                }
                            }
                        },
                        icon = {
                            when (route) {
                                NavRoute.Connect -> Icon(Icons.Default.Bluetooth, null)
                                NavRoute.Control -> Icon(Icons.Default.Settings, null)
                                NavRoute.Sim -> Icon(Icons.Default.PlayArrow, null)
                                NavRoute.Account -> Icon(Icons.Default.Person, null)
                            }
                        },
                        label = { Text(stringResource(route.labelRes)) },
                        enabled = enabled
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = NavRoute.Connect.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(NavRoute.Connect.route) {
                ConnectScreen(vm = vm, permissionsGranted = permissionsGranted)
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
                    accountVm = accountVm,
                    appPreferencesVm = appPreferencesVm,
                    onLoginClick = {
                        onOpenLogin()
                        authVm.logout()
                    },
                    onRegisterClick = {
                        onOpenRegister()
                        authVm.logout()
                    },
                    onOpenControl = {
                        navController.navigate(NavRoute.Control.route) {
                            launchSingleTop = true
                            restoreState = true
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                        }
                    },
                    onChangePassword = onOpenChangePassword
                )
            }
        }
    }
}
