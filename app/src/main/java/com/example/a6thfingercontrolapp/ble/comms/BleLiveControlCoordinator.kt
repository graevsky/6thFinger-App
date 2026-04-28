package com.example.a6thfingercontrolapp.ble.comms

import android.os.SystemClock
import com.example.a6thfingercontrolapp.ble.BleClient
import com.example.a6thfingercontrolapp.ble.settings.ESP_PAIR_COUNT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Live servo mode, telemetry and live-control error reporting controller.
 */
internal class BleLiveControlCoordinator(
    private val scope: CoroutineScope,
    private val client: BleClient,
    private val connectedAddress: StateFlow<String>,
    private val controlUnlocked: StateFlow<Boolean>,
    private val telemetryEnabled: StateFlow<Boolean>
) {
    private val _liveServoPairs = MutableStateFlow<Set<Int>>(emptySet())
    val liveServoPairs: StateFlow<Set<Int>> = _liveServoPairs

    private val _liveControlError = MutableStateFlow<String?>(null)
    val liveControlError: StateFlow<String?> = _liveControlError

    private val liveOpMutex = Mutex()
    private val liveReady = BooleanArray(ESP_PAIR_COUNT) { false }
    private val pendingAngle = IntArray(ESP_PAIR_COUNT) { -1 }
    private var teleBeforeAnyLive: Boolean = true
    private val lastLiveSendMs = LongArray(ESP_PAIR_COUNT) { 0L }
    private val lastLiveAngle = IntArray(ESP_PAIR_COUNT) { -1 }

    init {
        scope.launch {
            connectedAddress.collect { address ->
                if (address.isBlank()) {
                    resetLiveControlTracking(errorKey = "live_not_connected")
                }
            }
        }

        scope.launch {
            controlUnlocked.collect { unlocked ->
                if (!unlocked) {
                    resetLiveControlTracking(errorKey = "live_control_locked")
                }
            }
        }
    }

    private fun isLiveControlAvailable(): Boolean {
        return connectedAddress.value.isNotBlank() && controlUnlocked.value
    }

    private fun resetLiveControlTracking(errorKey: String? = null) {
        val hadLiveSession = _liveServoPairs.value.isNotEmpty()

        _liveServoPairs.value = emptySet()
        for (i in 0 until ESP_PAIR_COUNT) {
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

        scope.launch {
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

    fun disconnect() {
        val pairs = _liveServoPairs.value
        _liveServoPairs.value = emptySet()
        _liveControlError.value = null
        for (i in 0 until ESP_PAIR_COUNT) {
            liveReady[i] = false
            pendingAngle[i] = -1
        }

        scope.launch {
            pairs.forEach { client.stopServoLive(it) }
            client.disconnectNow()
        }
    }

    fun setServoLiveEnabled(pairIdx: Int, enabled: Boolean) {
        val idx = pairIdx.coerceIn(0, ESP_PAIR_COUNT - 1)

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

            scope.launch {
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
                        }
                    }

                    liveReady[idx] = true

                    val angle = pendingAngle[idx]
                    if (angle >= 0) {
                        val sent = client.sendServoLive(idx, angle)
                        pendingAngle[idx] = -1
                        if (sent) {
                            lastLiveSendMs[idx] = SystemClock.elapsedRealtime()
                            lastLiveAngle[idx] = angle
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

            scope.launch {
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

    fun sendServoLiveAngle(pairIdx: Int, angleDeg: Int) {
        val idx = pairIdx.coerceIn(0, ESP_PAIR_COUNT - 1)
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

        scope.launch {
            val ok = client.sendServoLive(idx, angle)
            _liveControlError.value = if (ok) null else "live_write_failed"
        }
    }
}
