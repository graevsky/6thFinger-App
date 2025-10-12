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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.a6thfingercontrollapp.ui.DeviceListScreen
import com.example.a6thfingercontrollapp.ui.TelemetryScreen
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

                NavHost(navController = nav, startDestination = "devices") {
                    composable("devices") {
                        DeviceListScreen(
                            vm = vm,
                            granted = granted,
                            onSelect = { address ->
                                vm.connect(address)
                                nav.navigate("telemetry")
                            }
                        )
                    }
                    composable("telemetry") {
                        TelemetryScreen(vm = vm, onBack = { nav.popBackStack() })
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
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}
