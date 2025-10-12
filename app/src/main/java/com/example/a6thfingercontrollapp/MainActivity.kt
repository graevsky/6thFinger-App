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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.a6thfingercontrollapp.ui.theme._6thFingerControllAppTheme

class MainActivity : ComponentActivity() {

    private val vm by viewModels<BleViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            _6thFingerControllAppTheme {
                AppScreen(vm)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        vm.start()
    }

    override fun onStop() {
        vm.stop()
        super.onStop()
    }
}

@Composable
private fun AppScreen(vm: BleViewModel) {
    val telemetry by vm.state.collectAsState()
    val permissions = remember { requiredPermissions() }

    var permissionsGranted by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        permissionsGranted = result.all { it.value }
    }

    LaunchedEffect(Unit) {
        launcher.launch(permissions)
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .padding(20.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
        ) {
            Text("BLE Status: ${telemetry.status}", style = MaterialTheme.typography.titleMedium)
            Text(
                "Flex avg res: ${formatFloat(telemetry.flexOhm)}",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                "Servo angle: ${formatFloat(telemetry.servoDeg)}",
                style = MaterialTheme.typography.headlineSmall
            )

            Button(
                onClick = { if (permissionsGranted && vm.isBleReady()) vm.scan() },
                enabled = permissionsGranted
            ) {
                Text("Scan + Connect")
            }

            if (!permissionsGranted) {
                Text("Access required")
            }
        }
    }
}

private fun requiredPermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= 31) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }
}

private fun formatFloat(f: Float): String {
    return if (f.isNaN() || f.isInfinite()) "--" else String.format("%.1f", f)
}
