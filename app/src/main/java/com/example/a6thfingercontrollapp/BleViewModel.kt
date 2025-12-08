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
import kotlinx.coroutines.flow.MutableStateFlow
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

    private val _uiSettings = MutableStateFlow(EspSettings())
    val activeSettings: StateFlow<EspSettings> = _uiSettings

    private val _lastAppliedSettings = MutableStateFlow(EspSettings())
    val lastAppliedSettings: StateFlow<EspSettings> = _lastAppliedSettings

    val state: StateFlow<Telemetry> =
            client.state.stateIn(viewModelScope, SharingStarted.Eagerly, Telemetry())

    val devices: StateFlow<List<BleDeviceUi>> =
            client.devices.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val lastDevice: StateFlow<LastDevice?> =
            lastStore.lastDevice.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val activeAddress: StateFlow<String> =
            lastDevice
                    .map { it?.address.orEmpty() }
                    .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val activeAlias: StateFlow<String> =
            activeAddress
                    .flatMapLatest { addr ->
                        if (addr.isEmpty()) {
                            flowOf("")
                        } else {
                            aliasStore.alias(addr).map { it ?: "" }
                        }
                    }
                    .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val appLanguage: StateFlow<String> =
            appSettings.getLanguage().stateIn(viewModelScope, SharingStarted.Eagerly, "ru")

    init {
        viewModelScope.launch {
            client.settings.collect { fromBoard ->
                val addr = activeAddress.value
                if (fromBoard != null) {
                    _uiSettings.value = fromBoard
                    _lastAppliedSettings.value = fromBoard
                    if (addr.isNotEmpty()) {
                        settingsStore.set(addr, fromBoard)
                    }
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
        viewModelScope.launch { lastStore.save(name, address) }
    }

    fun renameActive(newAlias: String) {
        val addr = activeAddress.value
        if (addr.isEmpty()) return

        viewModelScope.launch { aliasStore.setAlias(addr, newAlias.trim()) }
    }

    fun updateActiveSettings(update: (EspSettings) -> EspSettings) {
        val addr = activeAddress.value
        if (addr.isEmpty()) return

        val current = _uiSettings.value
        val next = update(current)

        _uiSettings.value = next
        viewModelScope.launch { settingsStore.set(addr, next) }
    }

    fun applyAndSaveToBoard(): Boolean {
        val addr = activeAddress.value
        if (addr.isEmpty()) return false

        val cfg = _uiSettings.value
        val ok = client.applySettings(cfg)

        if (ok) {
            _lastAppliedSettings.value = cfg
            viewModelScope.launch { settingsStore.set(addr, cfg) }
        }

        return ok
    }
    fun applySettingsLive(update: (EspSettings) -> EspSettings) {
        val addr = activeAddress.value
        if (addr.isEmpty()) return

        val current = _uiSettings.value
        val next = update(current)

        _uiSettings.value = next

        viewModelScope.launch {
            settingsStore.set(addr, next)
            client.applySettings(next)
        }
    }

    fun resetToDefaults() {
        val defaults = EspSettings()
        _uiSettings.value = defaults
    }

    fun applySettingsFromCloud(settings: EspSettings) {
        _uiSettings.value = settings
    }

    fun setAppLanguage(language: String) {
        viewModelScope.launch { appSettings.setLanguage(language) }
    }
}
