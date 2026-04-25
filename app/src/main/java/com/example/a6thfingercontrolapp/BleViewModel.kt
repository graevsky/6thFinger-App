package com.example.a6thfingercontrolapp

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.a6thfingercontrolapp.ble.BleDeviceUi
import com.example.a6thfingercontrolapp.ble.BleRepository
import com.example.a6thfingercontrolapp.ble.EspSettings
import com.example.a6thfingercontrolapp.ble.Telemetry
import com.example.a6thfingercontrolapp.data.AliasStore
import com.example.a6thfingercontrolapp.data.AppSettingsStore
import com.example.a6thfingercontrolapp.data.DeviceSettingsStore
import com.example.a6thfingercontrolapp.data.LastDevice
import com.example.a6thfingercontrolapp.data.LastDeviceStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * UI-facing ViewModel for BLE connection, active device settings and live servo control.
 *
 * BleClient owns the low-level transport; this class adapts it to screen state,
 * local persistence and user actions from Compose screens.
 */
class BleViewModel(app: Application) : AndroidViewModel(app) {
    private val client = BleRepository.get(app)
    val rawCfgText = client.rawCfgText

    private val lastStore = LastDeviceStore(app)
    private val aliasStore = AliasStore(app)
    private val settingsStore = DeviceSettingsStore(app)
    private val appSettings = AppSettingsStore(app)

    /** Current settings currently edited by UI. */
    private val _uiSettings = MutableStateFlow(EspSettings())
    val activeSettings: StateFlow<EspSettings> = _uiSettings

    /** Last settings snapshot known to be applied on the ESP32. */
    private val _lastAppliedSettings = MutableStateFlow(_uiSettings.value)
    val lastAppliedSettings: StateFlow<EspSettings> = _lastAppliedSettings

    /** True when settings were pulled from cloud and still need to be applied to the board. */
    private val _pendingBoardApply = MutableStateFlow(false)
    val pendingBoardApply: StateFlow<Boolean> = _pendingBoardApply

    val state: StateFlow<Telemetry> =
        client.state.stateIn(viewModelScope, SharingStarted.Eagerly, Telemetry())

    val devices: StateFlow<List<BleDeviceUi>> =
        client.devices.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val connectedAddress: StateFlow<String> =
        client.connectedAddress
            .map { it.orEmpty() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val lastDevice: StateFlow<LastDevice?> =
        lastStore.lastDevice.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val activeAddress: StateFlow<String> =
        lastDevice.map { it?.address.orEmpty() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val activeAlias: StateFlow<String> =
        activeAddress.flatMapLatest { addr ->
            if (addr.isEmpty()) flowOf("")
            else aliasStore.alias(addr).map { it ?: "" }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val appLanguage: StateFlow<String> =
        appSettings.getLanguage().stateIn(viewModelScope, SharingStarted.Eagerly, "ru")

    val appTheme: StateFlow<String> =
        appSettings.getThemeMode().stateIn(viewModelScope, SharingStarted.Eagerly, "system")

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

    private val _liveServoPairs = MutableStateFlow<Set<Int>>(emptySet())
    val liveServoPairs: StateFlow<Set<Int>> = _liveServoPairs

    private val _liveControlError = MutableStateFlow<String?>(null)
    val liveControlError: StateFlow<String?> = _liveControlError

    private fun isLiveControlAvailable(): Boolean {
        return connectedAddress.value.isNotBlank() && controlUnlocked.value
    }


    private fun resetLiveControlTracking(errorKey: String? = null) {
        val hadLiveSession = _liveServoPairs.value.isNotEmpty()

        _liveServoPairs.value = emptySet()
        for (i in 0..3) {
            liveReady[i] = false
            pendingAngle[i] = -1
            lastLiveSendMs[i] = 0L
            lastLiveAngle[i] = -1
        }

        if (hadLiveSession && errorKey != null) {
            _liveControlError.value = errorKey
        }
    }

    fun setTelemetryEnabled(enabled: Boolean) {
        if (_liveServoPairs.value.isNotEmpty()) {
            if (enabled) {
                _liveControlError.value = "live_control_active"
            }
            return
        }

        viewModelScope.launch {
            val ok = client.setTelemetryEnabledBlocking(enabled)
            _liveControlError.value = if (ok) {
                null
            } else if (enabled) {
                "telemetry_enable_failed"
            } else {
                "telemetry_disable_failed"
            }
        }
    }

    fun sendPin(pin4: String): Boolean = client.sendAuthPin(pin4)

    private val liveOpMutex = Mutex()
    private val liveReady = BooleanArray(4) { false }
    private val pendingAngle = IntArray(4) { -1 }

    /** Telemetry state before entering live mode, restored after the last live session ends. */
    private var teleBeforeAnyLive: Boolean = true

    private val lastLiveSendMs = LongArray(4) { 0L }
    private val lastLiveAngle = IntArray(4) { -1 }

    init {
        viewModelScope.launch {
            client.settings.collect { fromBoard ->
                val addr = activeAddress.value
                if (fromBoard != null) {
                    _uiSettings.value = fromBoard
                    _lastAppliedSettings.value = fromBoard
                    _pendingBoardApply.value = false

                    if (addr.isNotEmpty()) {
                        settingsStore.set(addr, fromBoard)
                    }
                }
            }
        }

        viewModelScope.launch {
            connectedAddress.collect { address ->
                if (address.isBlank()) {
                    resetLiveControlTracking(errorKey = "live_not_connected")
                }
            }
        }

        viewModelScope.launch {
            controlUnlocked.collect { unlocked ->
                if (!unlocked) {
                    resetLiveControlTracking(errorKey = "live_control_locked")
                }
            }
        }
    }

    fun start() = client.start()
    fun stop() = client.stop()
    fun scan() = client.scan()
    fun connect(address: String) = client.connectByAddress(address)
    fun isBleReady(): Boolean = client.isBleReady()

    /**
     * Stops all live channels before closing the BLE session.
     */
    fun disconnect() {
        val pairs = _liveServoPairs.value
        _liveServoPairs.value = emptySet()
        _liveControlError.value = null
        for (i in 0..3) {
            liveReady[i] = false
            pendingAngle[i] = -1
        }

        viewModelScope.launch {
            pairs.forEach { client.stopServoLive(it) }
            client.disconnectNow()
        }
    }

    fun rebootEsp(): Boolean = client.rebootEsp()

    fun aliasFlow(address: String): Flow<String?> = aliasStore.alias(address)

    /** Saves selected device as the quick reconnect target. */
    fun saveLastDevice(name: String, address: String) {
        viewModelScope.launch { lastStore.save(name, address) }
    }

    /** Renames only the currently active device locally. */
    fun renameActive(newAlias: String) {
        val addr = activeAddress.value
        if (addr.isEmpty()) return
        viewModelScope.launch { aliasStore.setAlias(addr, newAlias.trim()) }
    }

    /**
     * Applies UI edits to the active settings snapshot and persists them locally.
     */
    fun updateActiveSettings(pairIndex: Int, update: (EspSettings) -> EspSettings) {
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

        viewModelScope.launch { settingsStore.set(activeAddress.value, next) }
    }

    /**
     * Sends current settings to the ESP32 and marks them as applied on success.
     */
    fun applyAndSaveToBoard(): Boolean {
        val addr = activeAddress.value
        if (addr.isEmpty()) return false

        val cfg = _uiSettings.value
        val ok = client.applySettings(cfg)

        if (ok) {
            _lastAppliedSettings.value = cfg
            _pendingBoardApply.value = false
            viewModelScope.launch { settingsStore.set(addr, cfg) }
        }

        return ok
    }

    /** Restores UI settings to built-in defaults. */
    fun resetToDefaults() {
        _uiSettings.value = EspSettings()
    }

    /**
     * Loads settings from cloud into UI and marks them as pending for board apply.
     */
    fun applySettingsFromCloud(settings: EspSettings) {
        _uiSettings.value = settings
        _pendingBoardApply.value = true
    }

    fun setAppLanguage(language: String) {
        viewModelScope.launch { appSettings.setLanguage(language) }
    }

    fun setAppTheme(themeMode: String) {
        viewModelScope.launch { appSettings.setThemeMode(themeMode) }
    }

    /**
     * Enables or disables live servo control for one pair.
     *
     * Live control temporarily disables telemetry to reduce BLE traffic and latency.
     */
    fun setServoLiveEnabled(pairIdx: Int, enabled: Boolean) {
        val idx = pairIdx.coerceIn(0, 3)

        if (enabled) {
            if (!isLiveControlAvailable()) {
                _liveControlError.value =
                    if (connectedAddress.value.isBlank()) "live_not_connected" else "live_control_locked"
                return
            }

            if (_liveServoPairs.value.contains(idx)) return

            _liveServoPairs.value = _liveServoPairs.value + idx
            _liveControlError.value = null
            liveReady[idx] = false
            lastLiveSendMs[idx] = 0L
            lastLiveAngle[idx] = -1

            viewModelScope.launch {
                liveOpMutex.withLock {
                    val isFirstLiveNow =
                        (_liveServoPairs.value.size == 1 && _liveServoPairs.value.contains(idx))

                    if (isFirstLiveNow) {
                        teleBeforeAnyLive = telemetryEnabled.value
                        if (teleBeforeAnyLive) {
                            val ok = client.setTelemetryEnabledBlocking(false)
                            if (!ok) {
                                _liveServoPairs.value = _liveServoPairs.value - idx
                                liveReady[idx] = false
                                _liveControlError.value = "live_telemetry_disable_failed"
                                return@withLock
                            }
                        } else {
                        }
                    }

                    liveReady[idx] = true

                    val a = pendingAngle[idx]
                    if (a >= 0) {
                        val sent = client.sendServoLive(idx, a)
                        pendingAngle[idx] = -1
                        if (sent) {
                            lastLiveSendMs[idx] = SystemClock.elapsedRealtime()
                            lastLiveAngle[idx] = a
                            _liveControlError.value = null
                        } else {
                            _liveControlError.value = "live_write_failed"
                        }
                    }
                }
            }
        } else {
            if (!_liveServoPairs.value.contains(idx)) return

            liveReady[idx] = false

            viewModelScope.launch {
                liveOpMutex.withLock {
                    val stopped = client.stopServoLive(idx)
                    if (!stopped) {
                        _liveControlError.value = "live_stop_failed"
                    }

                    delay(160)

                    _liveServoPairs.value = _liveServoPairs.value - idx
                    pendingAngle[idx] = -1

                    if (_liveServoPairs.value.isEmpty() && teleBeforeAnyLive) {
                        val restored = client.setTelemetryEnabledBlocking(true)
                        _liveControlError.value =
                            if (restored) null else "live_telemetry_restore_failed"
                    } else if (stopped) {
                        _liveControlError.value = null
                    }
                }
            }
        }
    }

    /**
     * Sends throttled live servo angle updates.
     */
    fun sendServoLiveAngle(pairIdx: Int, angleDeg: Int) {
        val idx = pairIdx.coerceIn(0, 3)
        val angle = angleDeg.coerceIn(0, 180)

        pendingAngle[idx] = angle

        if (!isLiveControlAvailable()) {
            _liveControlError.value =
                if (connectedAddress.value.isBlank()) "live_not_connected" else "live_control_locked"
            resetLiveControlTracking(errorKey = null)
            return
        }

        if (!_liveServoPairs.value.contains(idx)) return
        if (!liveReady[idx]) return

        val now = SystemClock.elapsedRealtime()
        if (angle == lastLiveAngle[idx] && (now - lastLiveSendMs[idx]) < 220) return
        if ((now - lastLiveSendMs[idx]) < 80) return

        lastLiveSendMs[idx] = now
        lastLiveAngle[idx] = angle

        viewModelScope.launch {
            val ok = client.sendServoLive(idx, angle)
            _liveControlError.value = if (ok) null else "live_write_failed"
        }
    }
}
