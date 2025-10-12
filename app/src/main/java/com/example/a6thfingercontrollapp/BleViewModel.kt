package com.example.a6thfingercontrollapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.a6thfingercontrollapp.ble.BleClient
import com.example.a6thfingercontrollapp.ble.Telemetry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class BleViewModel(app: Application) : AndroidViewModel(app) {
    private val client = BleClient(app)

    val state: StateFlow<Telemetry> =
        client.state.stateIn(viewModelScope, SharingStarted.Eagerly, Telemetry())

    fun start() {
        client.start()
    }

    fun stop() {
        client.stop()
    }

    fun scan() {
        client.scan()
    }

    fun isBleReady() = client.isBleReady()
}
