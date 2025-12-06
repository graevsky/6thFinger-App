package com.example.a6thfingercontrollapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.a6thfingercontrollapp.ble.BleDeviceUi
import com.example.a6thfingercontrollapp.ble.BleRepository
import com.example.a6thfingercontrollapp.ble.EspSettings
import com.example.a6thfingercontrollapp.ble.Telemetry
import com.example.a6thfingercontrollapp.data.AliasStore
import com.example.a6thfingercontrollapp.data.AppSettingsStore
import com.example.a6thfingercontrollapp.data.DeviceSettingsStore
import com.example.a6thfingercontrollapp.data.LastDevice
import com.example.a6thfingercontrollapp.data.LastDeviceStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BleViewModel(app: Application) : AndroidViewModel(app) {
    private val client = BleRepository.get(app)
    private val lastStore = LastDeviceStore(app)
    private val aliasStore = AliasStore(app)
    private val settingsStore = DeviceSettingsStore(app)
    private val appSettings = AppSettingsStore(app)

    val state: StateFlow<Telemetry> =
        client.state.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            Telemetry()
        )


    val devices: StateFlow<List<BleDeviceUi>> =
        client.devices.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            emptyList()
        )


    val lastDevice: StateFlow<LastDevice?> =
        lastStore.lastDevice.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            null
        )

    private val activeAddress: StateFlow<String> =
        lastDevice.map { it?.address.orEmpty() }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                ""
            )

    val activeAlias: StateFlow<String> =
        activeAddress.flatMapLatest { addr ->
            if (addr.isEmpty()) {
                flowOf("")
            } else {
                aliasStore.alias(addr).map { it ?: "" }
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            ""
        )

    val activeSettings: StateFlow<EspSettings> =
        activeAddress.flatMapLatest { addr ->
            if (addr.isEmpty()) {
                flowOf(EspSettings())
            } else {
                settingsStore.get(addr).map { it ?: EspSettings() }
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            EspSettings()
        )

    val appLanguage: StateFlow<String> =
        appSettings.getLanguage()
            .stateIn(viewModelScope, SharingStarted.Eagerly, "ru")

    init {
        viewModelScope.launch {
            client.settings.collect { fromBoard ->
                val addr = activeAddress.value
                if (addr.isNotEmpty() && fromBoard != null) {
                    settingsStore.set(addr, fromBoard)
                }
            }
        }
    }

    fun start() = client.start()

    fun stop() = client.stop()

    fun scan() = client.scan()

    fun connect(address: String) = client.connectByAddress(address)

    fun isBleReady(): Boolean = client.isBleReady()

    fun disconnect() = client.disconnectNow()


    fun aliasFlow(address: String): Flow<String?> = aliasStore.alias(address)

    fun saveLastDevice(name: String, address: String) {
        viewModelScope.launch {
            lastStore.save(name, address)
        }
    }

    fun renameActive(newAlias: String) {
        val addr = activeAddress.value
        if (addr.isEmpty()) return

        viewModelScope.launch {
            aliasStore.setAlias(addr, newAlias.trim())
        }
    }

    fun updateActiveSettings(update: (EspSettings) -> EspSettings) {
        val addr = activeAddress.value
        if (addr.isEmpty()) return

        val current = activeSettings.value
        val next = update(current)

        viewModelScope.launch {
            settingsStore.set(addr, next)
        }
    }


    fun applyAndSaveToBoard(): Boolean {
        val addr = activeAddress.value
        if (addr.isEmpty()) return false

        val cfg = activeSettings.value
        return client.applySettings(cfg)
    }

    fun setAppLanguage(language: String) {
        viewModelScope.launch {
            appSettings.setLanguage(language)
        }
    }
}
