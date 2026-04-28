package com.example.a6thfingercontrolapp.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.example.a6thfingercontrolapp.ble.comms.BleInboundStreamRouter
import com.example.a6thfingercontrolapp.ble.comms.BleIncomingJsonContext
import com.example.a6thfingercontrolapp.ble.comms.BleNotifySubscriptionController
import com.example.a6thfingercontrolapp.ble.comms.BleWriteTracker
import com.example.a6thfingercontrolapp.ble.comms.handleIncomingJson
import com.example.a6thfingercontrolapp.ble.comms.resolveBleGattChannels
import com.example.a6thfingercontrolapp.ble.comms.writeBleJsonChunked
import com.example.a6thfingercontrolapp.ble.comms.writeBleLiveCommand
import com.example.a6thfingercontrolapp.ble.settings.ESP_PAIR_COUNT
import com.example.a6thfingercontrolapp.ble.settings.EspSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Low-level BLE transport layer for the ESP32 controller.
 *
 * Responsibilities:
 * - scan/connect/disconnect
 * - discover required GATT characteristics
 * - subscribe to telemetry/config/ack channels
 * - send chunked JSON commands to the board
 * - parse incoming telemetry/config/auth/ack JSON
 * - expose BLE state as StateFlow for the rest of the app
 */
class BleClient(private val context: Context) {

    /**
     * Debug-only raw config stream reconstructed exactly as received.
     */
    private val _rawCfgText = MutableStateFlow("")
    val rawCfgText: StateFlow<String> = _rawCfgText.asStateFlow()

    private val manager: BluetoothManager? =
        context.getSystemService(BluetoothManager::class.java)

    private val adapter: BluetoothAdapter?
        get() = manager?.adapter

    private val scanner
        get() = adapter?.bluetoothLeScanner

    private var gatt: BluetoothGatt? = null

    /**
     * Address of the device that currently owns an established GATT session.
     * It is used by UI to distinguish the target prosthesis from any other
     * connected BLE device.
     */
    private val _connectedAddress = MutableStateFlow<String?>(null)
    val connectedAddress: StateFlow<String?> = _connectedAddress.asStateFlow()

    /** Address requested by the current connect attempt. */
    @Volatile
    private var connectingAddress: String? = null

    // Cached characteristic references resolved after service discovery.
    private var chCfgIn: BluetoothGattCharacteristic? = null
    private var chCfgOut: BluetoothGattCharacteristic? = null
    private var chAck: BluetoothGattCharacteristic? = null
    private var chTele: BluetoothGattCharacteristic? = null
    private var chServoLive: BluetoothGattCharacteristic? = null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _telemetry = MutableStateFlow(Telemetry())
    val telemetry: StateFlow<Telemetry> = _telemetry.asStateFlow()

    /**
     * state is an alias kept for the rest of the app.
     * It combines transport status + parsed telemetry in one flow.
     */
    val state: StateFlow<Telemetry> = telemetry

    private val _settings = MutableStateFlow<EspSettings?>(null)
    val settings: StateFlow<EspSettings?> = _settings.asStateFlow()

    private val _devices = MutableStateFlow<List<BleDeviceUi>>(emptyList())
    val devices: StateFlow<List<BleDeviceUi>> = _devices.asStateFlow()

    private val _telemetryEnabled = MutableStateFlow(true)
    val telemetryEnabled: StateFlow<Boolean> = _telemetryEnabled.asStateFlow()

    private val scanning = AtomicBoolean(false)
    private val connecting = AtomicBoolean(false)
    private val writeTracker = BleWriteTracker()

    /**
     * Synchronizes writeJsonChunked and live-write operations.
     * Android BLE writes are sensitive to overlap.
     */
    private val writeMutex = Any()

    /**
     * Guard so cfg_ok is sent only once after the app is ready to consume telemetry.
     */
    @Volatile
    private var teleUnlocked: Boolean = false

    /**
     * Guard for an in-flight cfg_ok write. It becomes final only after write success.
     */
    @Volatile
    private var cfgOkSending: Boolean = false

    private val _authRequired = MutableStateFlow(false)
    val authRequired: StateFlow<Boolean> = _authRequired.asStateFlow()

    private val _authSending = MutableStateFlow(false)
    val authSending: StateFlow<Boolean> = _authSending.asStateFlow()

    private val _pinError = MutableStateFlow<String?>(null)
    val pinError: StateFlow<String?> = _pinError.asStateFlow()

    private val _controlUnlocked = MutableStateFlow(false)
    val controlUnlocked: StateFlow<Boolean> = _controlUnlocked.asStateFlow()

    @Volatile
    private var authAttemptId: Long = 0L

    @Volatile
    private var sessionAuthed: Boolean = false

    @Volatile
    private var devicePinSet: Boolean = false

    @Volatile
    private var seenTelemetry: Boolean = false

    /**
     * Used for commands that expect a specific ACK (e.g. tele_set).
     * Only one such command is allowed at a time.
     */
    private val commandMutex = Mutex()

    @Volatile
    private var expectedAckFor: String? = null

    @Volatile
    private var expectedAckDeferred: CompletableDeferred<JSONObject>? = null

    @Volatile
    private var lastTeleRxMs: Long = 0L

    private val notifySubscriptions = BleNotifySubscriptionController(
        updateStatus = ::updateStatus,
        onAllSubscribed = ::requestConfig
    )

    private val incomingJsonContext = object : BleIncomingJsonContext {
        override var sessionAuthed: Boolean
            get() = this@BleClient.sessionAuthed
            set(value) {
                this@BleClient.sessionAuthed = value
            }

        override var devicePinSet: Boolean
            get() = this@BleClient.devicePinSet
            set(value) {
                this@BleClient.devicePinSet = value
            }

        override var seenTelemetry: Boolean
            get() = this@BleClient.seenTelemetry
            set(value) {
                this@BleClient.seenTelemetry = value
            }

        override var lastTeleRxMs: Long
            get() = this@BleClient.lastTeleRxMs
            set(value) {
                this@BleClient.lastTeleRxMs = value
            }

        override var authRequired: Boolean
            get() = _authRequired.value
            set(value) {
                _authRequired.value = value
            }

        override var authSending: Boolean
            get() = _authSending.value
            set(value) {
                _authSending.value = value
            }

        override var pinError: String?
            get() = _pinError.value
            set(value) {
                _pinError.value = value
            }

        override var controlUnlocked: Boolean
            get() = _controlUnlocked.value
            set(value) {
                _controlUnlocked.value = value
            }

        override val currentTelemetry: Telemetry
            get() = _telemetry.value

        override fun updateTelemetry(value: Telemetry) {
            _telemetry.value = value
        }

        override fun updateSettings(value: EspSettings) {
            _settings.value = value
        }

        override fun updateStatus(text: String) {
            this@BleClient.updateStatus(text)
        }

        override fun requestConfig() {
            this@BleClient.requestConfig()
        }

        override fun sendCfgOkOnce() {
            this@BleClient.sendCfgOkOnce()
        }

        override fun maybeCompleteAckWaiter(json: JSONObject) {
            this@BleClient.maybeCompleteAckWaiter(json)
        }

        override fun canTelemetryUnlockControl(): Boolean =
            this@BleClient.canTelemetryUnlockControl()
    }

    private val inboundRouter = BleInboundStreamRouter(
        onJson = { incomingJsonContext.handleIncomingJson(it) },
        onRawCfgText = { _rawCfgText.value = it }
    )

    fun start() { /* no-op */
    }

    /**
     * Full BLE shutdown entry point used by the app lifecycle.
     */
    fun stop() {
        stopScan()
        disconnectNow()
    }

    fun disconnectNow() = disconnect()

    fun isBleReady(): Boolean = adapter?.isEnabled == true

    /**
     * Starts BLE scan using low-latency mode.
     */
    fun scan() {
        if (scanning.getAndSet(true)) return

        if (!hasScanPermission()) {
            scanning.set(false)
            updateStatus("No scan permission")
            return
        }

        if (!isBleReady()) {
            scanning.set(false)
            updateStatus("Bluetooth off")
            return
        }

        _devices.value = emptyList()
        updateStatus("Scanning")

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner?.startScan(null, settings, scanCallback)
        } catch (_: SecurityException) {
            scanning.set(false)
            updateStatus("Scan denied")
        } catch (_: Throwable) {
            scanning.set(false)
            updateStatus("Scan error")
        }
    }

    /**
     * Connects to a known MAC address
     */
    fun connectByAddress(addr: String) {
        val dev = try {
            adapter?.getRemoteDevice(addr)
        } catch (_: IllegalArgumentException) {
            null
        }
        if (dev == null) {
            updateStatus("Invalid device")
            return
        }
        connect(dev)
    }

    /**
     * Sends the full settings snapshot to the board using chunked JSON protocol.
     */
    fun applySettings(settings: EspSettings): Boolean {
        val base = JSONObject(settings.toJsonString())
        base.put("type", "cfg_set")
        val json = base.toString()
        //Log.d(TAG_CFG, "ANDROID_SEND_CFG_JSON = $json")
        return writeJsonChunked(json)
    }

    /**
     * Requests current board configuration. Uses delay for safety.
     */
    fun requestConfig() {
        scope.launch {
            delay(200)
            val obj = JSONObject().apply { put("type", "cfg_get") }
            //Log.d(TAG_CFG, "ANDROID_SEND_CFG_GET = $obj")
            writeJsonChunked(obj.toString())
        }
    }

    /**
     * Sends telemetry enable/disable command and waits for its ACK.
     */
    suspend fun setTelemetryEnabledBlocking(enabled: Boolean): Boolean =
        commandMutex.withLock {
            gatt ?: return false
            chCfgIn ?: return false
            if (!hasConnPermission()) return false

            val waiter = CompletableDeferred<JSONObject>()
            expectedAckFor = "tele_set"
            expectedAckDeferred = waiter

            val obj = JSONObject().apply {
                put("type", "tele_set")
                put("enabled", enabled)
            }

            //Log.d(TAG_CFG, "ANDROID_SEND_TELE_SET_BLOCKING = $obj")
            val sent = writeJsonChunked(obj.toString())
            if (!sent) {
                clearExpectedAckWaiter(waiter)
                updateStatus("Telemetry send failed")
                return false
            }

            val ack = withTimeoutOrNull(7000) { waiter.await() }
            if (ack == null) {
                clearExpectedAckWaiter(waiter)
                updateStatus("Telemetry ACK timeout")
                return false
            }

            val ok = ack.optBoolean("ok", false)
            if (!ok) {
                clearExpectedAckWaiter(waiter)
                updateStatus("Telemetry ACK fail")
                return false
            }

            // When telemetry is disabled, also wait until packets actually stop arriving.
            if (!enabled) {
                val quietOk = waitTelemetryQuiet(minQuietMs = 400, timeoutMs = 3500)
                if (!quietOk) {
                    //Log.w(TAG_CFG, "Telemetry quiet wait timeout (may still be flowing)")
                }
            }

            _telemetryEnabled.value = enabled
            true
        }

    /**
     * Waits until no telemetry packet has been received for the requested quiet period.
     */
    private suspend fun waitTelemetryQuiet(minQuietMs: Long, timeoutMs: Long): Boolean {
        val start = SystemClock.elapsedRealtime()
        while (SystemClock.elapsedRealtime() - start < timeoutMs) {
            val last = lastTeleRxMs
            if (last == 0L) return true
            val dt = SystemClock.elapsedRealtime() - last
            if (dt >= minQuietMs) return true
            delay(40)
        }
        return false
    }

    /**
     * Sends low-latency live servo angle command.
     *
     * Unlike config JSON writes, this uses the dedicated SERVO_LIVE characteristic
     * with a small CSV-like payload to reduce latency.
     */
    @SuppressLint("MissingPermission")
    fun sendServoLive(pairIdx: Int, deg: Int): Boolean =
        synchronized(writeMutex) {
            val idx = pairIdx.coerceIn(0, ESP_PAIR_COUNT - 1)
            val angle = deg.coerceIn(0, 180)
            writeBleLiveCommand(
                gatt = gatt,
                characteristic = chServoLive,
                hasConnPermission = ::hasConnPermission,
                updateStatus = ::updateStatus,
                payload = "A,$idx,$angle",
                failureStatus = "Live write failed",
                missingGattStatus = "No GATT",
                missingCharacteristicStatus = "No SERVO_LIVE"
            )
        }

    /**
     * Stops live control for a single servo pair.
     */
    @SuppressLint("MissingPermission")
    fun stopServoLive(pairIdx: Int): Boolean =
        synchronized(writeMutex) {
            val idx = pairIdx.coerceIn(0, ESP_PAIR_COUNT - 1)
            writeBleLiveCommand(
                gatt = gatt,
                characteristic = chServoLive,
                hasConnPermission = ::hasConnPermission,
                updateStatus = ::updateStatus,
                payload = "S,$idx",
                failureStatus = "Live stop failed"
            )
        }

    /**
     * Sends a 4-digit PIN to the board.
     *
     * Result is handled asynchronously when an auth JSON response arrives.
     */
    fun sendAuthPin(pin4: String): Boolean {
        val pin = pin4.trim()
        if (pin.length != 4 || !pin.all { it.isDigit() }) {
            _pinError.value = "pin_bad_format"
            return false
        }
        if (_authSending.value) return false

        _pinError.value = null
        _authSending.value = true
        _authRequired.value = true
        _controlUnlocked.value = false

        val myAttempt = ++authAttemptId

        scope.launch {
            val obj = JSONObject().apply {
                put("type", "auth")
                put("pin", pin)
            }
            //Log.d(TAG_CFG, "ANDROID_SEND_AUTH = $obj")

            val sentOk = writeJsonChunked(obj.toString())
            if (!sentOk) {
                if (authAttemptId == myAttempt) {
                    _authSending.value = false
                    _pinError.value = "pin_send_failed"
                }
                return@launch
            }

            delay(5500)
            if (authAttemptId == myAttempt && _authSending.value) {
                _authSending.value = false
                _pinError.value = "pin_timeout"
                _authRequired.value = true
                _controlUnlocked.value = false
            }
        }

        return true
    }

    /**
     * Sent once after config was received and the app is ready.
     *
     * In this protocol cfg_ok acts as a handshake telling firmware that the app
     * successfully consumed config and telemetry can proceed normally.
     */
    private fun sendCfgOkOnce() {
        if (teleUnlocked || cfgOkSending) return
        cfgOkSending = true

        scope.launch {
            try {
                val okObj = JSONObject().apply { put("type", "cfg_ok") }
                //Log.d(TAG_CFG, "ANDROID_SEND_CFG_OK = $okObj")
                val ok = writeJsonChunked(okObj.toString())
                if (ok) {
                    teleUnlocked = true
                    setTelemetryEnabledBlocking(_telemetryEnabled.value)
                } else {
                    updateStatus("Cfg OK failed")
                }
            } finally {
                cfgOkSending = false
            }
        }
    }

    private fun checkPerm(p: String): Boolean =
        ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    private fun hasScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= 31) {
            checkPerm(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            checkPerm(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun hasConnPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= 31) {
            checkPerm(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            true
        }
    }

    /**
     * Scan callback filters only devices that match expected service UUID or name.
     */
    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val rec = result.scanRecord ?: return
            val dev = result.device ?: return

            val uuids = rec.serviceUuids?.map { it.uuid } ?: emptyList()
            val name = dev.name ?: rec.deviceName ?: ""

            if (!uuids.contains(NewBleConstants.SERVICE_UUID) &&
                !name.contains("ESP32-Flex6", ignoreCase = true) &&
                !name.contains("ESP32", ignoreCase = true)
            ) return

            val list = _devices.value
            if (list.none { it.address == dev.address }) {
                val displayName = if (name.isNotBlank()) name else "ESP32"
                _devices.value = list + BleDeviceUi(name = displayName, address = dev.address)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            scanning.set(false)
            updateStatus("Scan failed: $errorCode")
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        if (!scanning.getAndSet(false)) return
        try {
            if (hasScanPermission()) scanner?.stopScan(scanCallback)
        } catch (_: Throwable) {
        }
    }

    /**
     * Internal connect routine that resets all per-session state before opening GATT.
     */
    @SuppressLint("MissingPermission")
    private fun connect(dev: BluetoothDevice) {
        if (connecting.getAndSet(true)) return

        stopScan()

        connectingAddress = dev.address
        _connectedAddress.value = null

        sessionAuthed = false
        devicePinSet = false
        seenTelemetry = false
        teleUnlocked = false
        cfgOkSending = false
        authAttemptId++

        _controlUnlocked.value = false
        _authRequired.value = false
        _authSending.value = false
        _pinError.value = null

        inboundRouter.reset()
        writeTracker.reset()
        notifySubscriptions.reset()

        expectedAckFor = null
        expectedAckDeferred = null
        lastTeleRxMs = 0L

        if (!hasConnPermission()) {
            connecting.set(false)
            connectingAddress = null
            updateStatus("No connect permission")
            return
        }

        updateStatus("Connecting")

        gatt = try {
            if (Build.VERSION.SDK_INT >= 33) {
                dev.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                @Suppress("DEPRECATION")
                dev.connectGatt(context, false, gattCallback)
            }
        } catch (_: Throwable) {
            connecting.set(false)
            connectingAddress = null
            updateStatus("Connect failed")
            null
        }
    }

    /**
     * BLE session is destroyed here
     */
    @SuppressLint("MissingPermission")
    private fun disconnect() {
        connecting.set(false)
        connectingAddress = null
        _connectedAddress.value = null

        sessionAuthed = false
        devicePinSet = false
        seenTelemetry = false
        teleUnlocked = false
        cfgOkSending = false
        authAttemptId++

        _authRequired.value = false
        _authSending.value = false
        _pinError.value = null
        _controlUnlocked.value = false

        inboundRouter.reset()
        writeTracker.reset()

        expectedAckFor = null
        expectedAckDeferred = null
        lastTeleRxMs = 0L

        try {
            if (hasConnPermission()) gatt?.disconnect()
        } catch (_: Throwable) {
        }
        try {
            gatt?.close()
        } catch (_: Throwable) {
        }

        gatt = null
        chCfgIn = null
        chCfgOut = null
        chAck = null
        chTele = null
        chServoLive = null
        notifySubscriptions.reset()

        updateStatus("Disconnected")
    }

    /**
     * Core GATT callback implementing the BLE connection state machine.
     */
    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            //Log.d(TAG_CFG, "onConnectionStateChange status=$status newState=$newState")

            if (status == BluetoothGatt.GATT_SUCCESS &&
                newState == BluetoothProfile.STATE_CONNECTED
            ) {
                connecting.set(false)
                _connectedAddress.value = connectingAddress ?: g.device.address

                updateStatus("Discovering")
                try {
                    g.discoverServices()
                } catch (_: Throwable) {
                    updateStatus("Service discovery error")
                    disconnect()
                }
            } else {
                connecting.set(false)
                disconnect()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            connecting.set(false)

            if (status != BluetoothGatt.GATT_SUCCESS) {
                updateStatus("Service discovery failed")
                disconnect()
                return
            }

            if (!hasConnPermission()) {
                updateStatus("No permission")
                disconnect()
                return
            }

            val svc = g.getService(NewBleConstants.SERVICE_UUID)
            if (svc == null) {
                updateStatus("Service not found")
                disconnect()
                return
            }

            val channels = resolveBleGattChannels(svc)
            if (channels == null) {
                updateStatus("Characteristics missing")
                disconnect()
                return
            }

            chCfgIn = channels.cfgIn
            chCfgOut = channels.cfgOut
            chAck = channels.ack
            chTele = channels.tele
            chServoLive = channels.servoLive

            notifySubscriptions.setTargets(
                tele = channels.tele,
                cfgOut = channels.cfgOut,
                ack = channels.ack
            )
            notifySubscriptions.enableNext(g)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            notifySubscriptions.onDescriptorWrite(gatt, descriptor)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
            val bytes = ch.value ?: return
            inboundRouter.handleNotify(ch.uuid, bytes)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            ch: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            inboundRouter.handleNotify(ch.uuid, value)
        }

        /**
         * Used by writeJsonChunked to know when the current write finished.
         */
        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            writeTracker.onWriteFinished(characteristic.uuid, status)
        }
    }

    /**
     * Sends reboot command to the board.
     */
    fun rebootEsp(): Boolean {
        val obj = JSONObject().apply { put("type", "reboot") }
        //Log.d(TAG_CFG, "ANDROID_SEND_REBOOT = $obj")
        return writeJsonChunked(obj.toString())
    }

    /**
     * Clears the current waiting ACK only if it still belongs to the given waiter.
     */
    private fun clearExpectedAckWaiter(waiter: CompletableDeferred<JSONObject>? = null) {
        val current = expectedAckDeferred
        if (waiter == null || current === waiter) {
            expectedAckDeferred = null
            expectedAckFor = null
        }
    }

    /**
     * Resolves the current waiting ACK if the received ACK matches expected "for".
     */
    private fun maybeCompleteAckWaiter(json: JSONObject) {
        val def = expectedAckDeferred ?: return
        val want = expectedAckFor

        val gotFor = json.optString("for", "")
        val matches =
            want == null || gotFor == want || gotFor.isBlank()
        if (matches && !def.isCompleted) {
            expectedAckDeferred = null
            expectedAckFor = null
            def.complete(json)
        }
    }

    /**
     * Returns true only when telemetry is allowed to unlock the UI control flow.
     */
    private fun canTelemetryUnlockControl(): Boolean {
        val pinGateActive = (devicePinSet || _authRequired.value) && !sessionAuthed
        return !pinGateActive
    }

    /**
     * Sends JSON in small BLE-friendly fragments.
     *
     * Protocol:
     * [BEGIN]
     * chunk1
     * chunk2
     * ...
     * [END]
     *
     * Each chunk write waits synchronously for onCharacteristicWrite callback.
     * This is important because Android BLE writes are strictly sequential.
     */
    @SuppressLint("MissingPermission")
    private fun writeJsonChunked(json: String): Boolean =
        synchronized(writeMutex) {
            writeBleJsonChunked(
                json = json,
                gatt = gatt,
                characteristic = chCfgIn,
                hasConnPermission = ::hasConnPermission,
                updateStatus = ::updateStatus,
                writeTracker = writeTracker
            )
        }

    /**
     * Stores normalized status key inside the telemetry flow.
     */
    private fun updateStatus(text: String) {
        val key = bleStatusKey(text)
        scope.launch {
            val tPrev = _telemetry.value
            _telemetry.value = tPrev.copy(status = key)
        }
    }
}
