package com.example.a6thfingercontrollapp

import android.app.Application
import android.os.SystemClock
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

class BleViewModel(app: Application) : AndroidViewModel(app) {
    private val client = BleRepository.get(app)
    val rawCfgText = client.rawCfgText

    private val lastStore = LastDeviceStore(app)
    private val aliasStore = AliasStore(app)
    private val settingsStore = DeviceSettingsStore(app)
    private val appSettings = AppSettingsStore(app)

    private val _uiSettings = MutableStateFlow(EspSettings())
    val activeSettings: StateFlow<EspSettings> = _uiSettings

    private val _lastAppliedSettings = MutableStateFlow(_uiSettings.value)
    val lastAppliedSettings: StateFlow<EspSettings> = _lastAppliedSettings

    private val _pendingBoardApply = MutableStateFlow(false)
    val pendingBoardApply: StateFlow<Boolean> = _pendingBoardApply

    val state: StateFlow<Telemetry> =
        client.state.stateIn(viewModelScope, SharingStarted.Eagerly, Telemetry())

    val devices: StateFlow<List<BleDeviceUi>> =
        client.devices.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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

    fun setTelemetryEnabled(enabled: Boolean) {
        if (_liveServoPairs.value.isNotEmpty()) {
            if (!enabled) client.setTelemetryEnabled(false)
            return
        }
        client.setTelemetryEnabled(enabled)
    }

    fun sendPin(pin4: String): Boolean = client.sendAuthPin(pin4)

    private val _liveServoPairs = MutableStateFlow<Set<Int>>(emptySet())
    val liveServoPairs: StateFlow<Set<Int>> = _liveServoPairs

    private val liveOpMutex = Mutex()
    private val liveReady = BooleanArray(4) { false }
    private val pendingAngle = IntArray(4) { -1 }

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
    }

    fun start() = client.start()
    fun stop() = client.stop()
    fun scan() = client.scan()
    fun connect(address: String) = client.connectByAddress(address)
    fun isBleReady(): Boolean = client.isBleReady()

    fun disconnect() {
        val pairs = _liveServoPairs.value
        _liveServoPairs.value = emptySet()
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

    fun saveLastDevice(name: String, address: String) {
        viewModelScope.launch { lastStore.save(name, address) }
    }

    fun renameActive(newAlias: String) {
        val addr = activeAddress.value
        if (addr.isEmpty()) return
        viewModelScope.launch { aliasStore.setAlias(addr, newAlias.trim()) }
    }

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

    fun resetToDefaults() {
        _uiSettings.value = EspSettings()
    }

    fun applySettingsFromCloud(settings: EspSettings) {
        _uiSettings.value = settings
        _pendingBoardApply.value = true
    }

    fun setAppLanguage(language: String) {
        viewModelScope.launch { appSettings.setLanguage(language) }
    }

    fun setServoLiveEnabled(pairIdx: Int, enabled: Boolean) {
        val idx = pairIdx.coerceIn(0, 3)

        if (enabled) {
            if (_liveServoPairs.value.contains(idx)) return

            _liveServoPairs.value = _liveServoPairs.value + idx
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
                                return@withLock
                            }
                        } else {
                        }
                    }

                    liveReady[idx] = true

                    val a = pendingAngle[idx]
                    if (a >= 0) {
                        client.sendServoLive(idx, a)
                        pendingAngle[idx] = -1
                        lastLiveSendMs[idx] = SystemClock.elapsedRealtime()
                        lastLiveAngle[idx] = a
                    }
                }
            }
        } else {
            if (!_liveServoPairs.value.contains(idx)) return

            liveReady[idx] = false

            viewModelScope.launch {
                liveOpMutex.withLock {
                    client.stopServoLive(idx)
                    delay(160)

                    _liveServoPairs.value = _liveServoPairs.value - idx
                    pendingAngle[idx] = -1

                    if (_liveServoPairs.value.isEmpty() && teleBeforeAnyLive) {
                        client.setTelemetryEnabledBlocking(true)
                    }
                }
            }
        }
    }

    fun sendServoLiveAngle(pairIdx: Int, angleDeg: Int) {
        val idx = pairIdx.coerceIn(0, 3)
        val angle = angleDeg.coerceIn(0, 180)

        pendingAngle[idx] = angle

        if (!_liveServoPairs.value.contains(idx)) return
        if (!liveReady[idx]) return

        val now = SystemClock.elapsedRealtime()
        if (angle == lastLiveAngle[idx] && (now - lastLiveSendMs[idx]) < 220) return
        if ((now - lastLiveSendMs[idx]) < 80) return

        lastLiveSendMs[idx] = now
        lastLiveAngle[idx] = angle

        viewModelScope.launch {
            client.sendServoLive(idx, angle)
        }
    }
}