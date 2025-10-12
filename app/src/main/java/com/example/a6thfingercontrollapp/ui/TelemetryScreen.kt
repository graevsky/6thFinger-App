package com.example.a6thfingercontrollapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.a6thfingercontrollapp.BleViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelemetryScreen(vm: BleViewModel, onBack: () -> Unit) {
    val t by vm.state.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Telemetry") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                }
            )
        }
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .padding(20.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Status: ${t.status}", style = MaterialTheme.typography.titleMedium)
            Text("Flex avg, Ω: ${fmt(t.flexOhm)}", style = MaterialTheme.typography.headlineSmall)
            Text("Servo, °: ${fmt(t.servoDeg)}", style = MaterialTheme.typography.headlineSmall)
        }
    }
}

private fun fmt(v: Float) = if (v.isFinite()) String.format("%.1f", v) else "--"
