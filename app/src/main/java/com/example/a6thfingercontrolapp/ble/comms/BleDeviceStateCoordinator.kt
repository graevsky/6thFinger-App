package com.example.a6thfingercontrolapp.ble.comms

import android.app.Application
import com.example.a6thfingercontrolapp.ble.BleClient
import com.example.a6thfingercontrolapp.ble.settings.EspSettings
import com.example.a6thfingercontrolapp.data.AliasStore
import com.example.a6thfingercontrolapp.data.DeviceSettingsStore
import com.example.a6thfingercontrolapp.data.LastDevice
import com.example.a6thfingercontrolapp.data.LastDeviceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * BLE device id, local settings persistence and applied settings state controller.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class BleDeviceStateCoordinator(
    app: Application,
    private val scope: CoroutineScope,
    private val client: BleClient
) {
    private val lastStore = LastDeviceStore(app)
    private val aliasStore = AliasStore(app)
    private val settingsStore = DeviceSettingsStore(app)

    private val _uiSettings = MutableStateFlow(EspSettings())
    val activeSettings: StateFlow<EspSettings> = _uiSettings

    private val _lastAppliedSettings = MutableStateFlow(_uiSettings.value)
    val lastAppliedSettings: StateFlow<EspSettings> = _lastAppliedSettings

    private val _pendingBoardApply = MutableStateFlow(false)
    val pendingBoardApply: StateFlow<Boolean> = _pendingBoardApply

    val lastDevice: StateFlow<LastDevice?> =
        lastStore.lastDevice.stateIn(scope, SharingStarted.Companion.Eagerly, null)

    val activeAddress: StateFlow<String> =
        lastDevice.map { it?.address.orEmpty() }
            .stateIn(scope, SharingStarted.Companion.Eagerly, "")

    val activeAlias: StateFlow<String> =
        activeAddress.flatMapLatest { address ->
            if (address.isEmpty()) flowOf("")
            else aliasStore.alias(address).map { it ?: "" }
        }.stateIn(scope, SharingStarted.Companion.Eagerly, "")

    init {
        scope.launch {
            client.settings.collect { fromBoard ->
                val address = activeAddress.value
                if (fromBoard != null) {
                    _uiSettings.value = fromBoard
                    _lastAppliedSettings.value = fromBoard
                    _pendingBoardApply.value = false

                    if (address.isNotEmpty()) {
                        settingsStore.set(address, fromBoard)
                    }
                }
            }
        }
    }

    fun aliasFlow(address: String): Flow<String?> = aliasStore.alias(address)

    fun saveLastDevice(name: String, address: String) {
        scope.launch { lastStore.save(name, address) }
    }

    fun renameActive(activeAddress: String, newAlias: String) {
        if (activeAddress.isEmpty()) return
        scope.launch { aliasStore.setAlias(activeAddress, newAlias.trim()) }
    }

    fun updateActiveSettings(
        activeAddress: String,
        pairIndex: Int,
        update: (EspSettings) -> EspSettings
    ) {
        val current = _uiSettings.value
        val next = update(current)

        when (pairIndex) {
            0, 1, 2, 3 -> {
                _uiSettings.value = next.copy(
                    flexSettings = next.flexSettings,
                    servoSettings = next.servoSettings
                )
            }
        }

        scope.launch { settingsStore.set(activeAddress, next) }
    }

    fun applyAndSaveToBoard(activeAddress: String): Boolean {
        if (activeAddress.isEmpty()) return false

        val config = _uiSettings.value
        val ok = client.applySettings(config)

        if (ok) {
            _lastAppliedSettings.value = config
            _pendingBoardApply.value = false
            scope.launch { settingsStore.set(activeAddress, config) }
        }

        return ok
    }

    fun resetToDefaults() {
        _uiSettings.value = EspSettings()
    }

    fun applySettingsFromCloud(settings: EspSettings) {
        _uiSettings.value = settings
        _pendingBoardApply.value = true
    }
}