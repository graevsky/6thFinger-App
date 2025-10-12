package com.example.a6thfingercontrollapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.a6thfingercontrollapp.ble.BleDeviceUi
import com.example.a6thfingercontrollapp.ble.BleRepository
import com.example.a6thfingercontrollapp.ble.Telemetry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class BleViewModel(app: Application) : AndroidViewModel(app) {
    private val client = BleRepository.get(app)

    val state: StateFlow<Telemetry> =
        client.state.stateIn(viewModelScope, SharingStarted.Eagerly, Telemetry())

    val devices: StateFlow<List<BleDeviceUi>> =
        client.devices.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun start() = client.start()
    fun stop() = client.stop()
    fun scan() = client.scan()
    fun connect(address: String) = client.connectByAddress(address)
    fun isBleReady() = client.isBleReady()
}
