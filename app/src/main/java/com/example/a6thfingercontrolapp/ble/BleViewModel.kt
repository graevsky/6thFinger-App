package com.example.a6thfingercontrolapp.ble

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.a6thfingercontrolapp.ble.comms.BleDeviceStateCoordinator
import com.example.a6thfingercontrolapp.ble.comms.BleLiveControlCoordinator
import com.example.a6thfingercontrolapp.ble.settings.EspSettings
import com.example.a6thfingercontrolapp.data.LastDevice
import com.example.a6thfingercontrolapp.data.repositories.BleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * UI ViewModel for BLE connection, active device settings and live servo control.
 */
class BleViewModel(app: Application) : AndroidViewModel(app) {
    private val client = BleRepository.get(app)
    private val deviceState = BleDeviceStateCoordinator(app, viewModelScope, client)

    val rawCfgText = client.rawCfgText

    val state: StateFlow<Telemetry> =
        client.state.stateIn(viewModelScope, SharingStarted.Eagerly, Telemetry())

    val devices: StateFlow<List<BleDeviceUi>> =
        client.devices.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val connectedAddress: StateFlow<String> =
        client.connectedAddress
            .map { it.orEmpty() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val lastDevice: StateFlow<LastDevice?> = deviceState.lastDevice
    val activeAddress: StateFlow<String> = deviceState.activeAddress
    val activeAlias: StateFlow<String> = deviceState.activeAlias

    val authRequired: StateFlow<Boolean> =
        client.authRequired.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val pinSending: StateFlow<Boolean> =
        client.authSending.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val pinError: StateFlow<String?> =
        client.pinError.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val controlUnlocked: StateFlow<Boolean> =
        client.controlUnlocked.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val telemetryEnabled: StateFlow<Boolean> =
        client.telemetryEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val activeSettings: StateFlow<EspSettings> = deviceState.activeSettings
    val lastAppliedSettings: StateFlow<EspSettings> = deviceState.lastAppliedSettings
    val pendingBoardApply: StateFlow<Boolean> = deviceState.pendingBoardApply

    private val liveControl = BleLiveControlCoordinator(
        scope = viewModelScope,
        client = client,
        connectedAddress = connectedAddress,
        controlUnlocked = controlUnlocked,
        telemetryEnabled = telemetryEnabled
    )

    val liveServoPairs: StateFlow<Set<Int>> = liveControl.liveServoPairs
    val liveControlError: StateFlow<String?> = liveControl.liveControlError

    fun start() = client.start()
    fun stop() = client.stop()
    fun scan() = client.scan()
    fun connect(address: String) = client.connectByAddress(address)
    fun isBleReady(): Boolean = client.isBleReady()

    fun setTelemetryEnabled(enabled: Boolean) =
        liveControl.setTelemetryEnabled(enabled)

    fun sendPin(pin4: String): Boolean = client.sendAuthPin(pin4)

    fun disconnect() = liveControl.disconnect()

    fun rebootEsp(): Boolean = client.rebootEsp()

    fun aliasFlow(address: String): Flow<String?> =
        deviceState.aliasFlow(address)

    fun saveLastDevice(name: String, address: String) =
        deviceState.saveLastDevice(name, address)

    fun renameActive(newAlias: String) =
        deviceState.renameActive(activeAddress.value, newAlias)

    fun updateActiveSettings(pairIndex: Int, update: (EspSettings) -> EspSettings) =
        deviceState.updateActiveSettings(activeAddress.value, pairIndex, update)

    fun applyAndSaveToBoard(): Boolean =
        deviceState.applyAndSaveToBoard(activeAddress.value)

    fun resetToDefaults() =
        deviceState.resetToDefaults()

    fun applySettingsFromCloud(settings: EspSettings) =
        deviceState.applySettingsFromCloud(settings)

    fun setServoLiveEnabled(pairIdx: Int, enabled: Boolean) =
        liveControl.setServoLiveEnabled(pairIdx, enabled)

    fun sendServoLiveAngle(pairIdx: Int, angleDeg: Int) =
        liveControl.sendServoLiveAngle(pairIdx, angleDeg)
}
