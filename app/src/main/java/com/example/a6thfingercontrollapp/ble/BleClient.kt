package com.example.a6thfingercontrollapp.ble

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
import android.util.Log
import androidx.core.content.ContextCompat
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

    private var rawCfgInProgress = false
    private val rawCfgBuf = StringBuilder()

    private val TAG_CFG = "BLE_CFG"
    private val TAG_CFG_JSON = "BLE_CFG_JSON"
    private val TAG_TELE = "BLE_TELE"
    private val TAG_LIVE = "BLE_LIVE"

    private val manager: BluetoothManager? =
        context.getSystemService(BluetoothManager::class.java)

    private val adapter: BluetoothAdapter?
        get() = manager?.adapter

    private val scanner
        get() = adapter?.bluetoothLeScanner

    private var gatt: BluetoothGatt? = null

    // Cached characteristic references resolved after service discovery.
    private var chCfgIn: BluetoothGattCharacteristic? = null
    private var chCfgOut: BluetoothGattCharacteristic? = null
    private var chAck: BluetoothGattCharacteristic? = null
    private var chTele: BluetoothGattCharacteristic? = null
    private var chServoLive: BluetoothGattCharacteristic? = null

    /**
     * Notification enabling must be serialized: Android writes CCC descriptor
     * one characteristic at a time
     */
    private val notifyQueue = ArrayDeque<BluetoothGattCharacteristic>()

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

    private var teleParser = ChunkParser()
    private var cfgParser = ChunkParser()
    private var ackParser = ChunkParser()

    /**
     * Synchronizes writeJsonChunked and live-write operations.
     * Android BLE writes are sensitive to overlap.
     */
    private val writeMutex = Any()

    @Volatile
    private var writeInProgress: Boolean = false

    @Volatile
    private var lastWriteOk: Boolean = true

    @Volatile
    private var pendingWriteUuid: java.util.UUID? = null

    /**
     * Guard so cfg_ok is sent only once after the app is ready to consume telemetry.
     */
    @Volatile
    private var teleUnlocked: Boolean = false

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
     *
     * This is blocking from the caller's perspective and is used when the app
     * must guarantee telemetry state before continuing
     */
    suspend fun setTelemetryEnabledBlocking(enabled: Boolean): Boolean =
        commandMutex.withLock {
            _telemetryEnabled.value = enabled

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
                expectedAckFor = null
                expectedAckDeferred = null
                return false
            }

            val ack = withTimeoutOrNull(7000) { waiter.await() }
            if (ack == null) return false

            val ok = ack.optBoolean("ok", false)
            if (!ok) return false

            // When telemetry is disabled, also wait until packets actually stop arriving.
            if (!enabled) {
                val quietOk = waitTelemetryQuiet(minQuietMs = 400, timeoutMs = 3500)
                if (!quietOk) {
                    //Log.w(TAG_CFG, "Telemetry quiet wait timeout (may still be flowing)")
                }
            }

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
     * Dumb wrapper for telemetry switching.
     */
    fun setTelemetryEnabled(enabled: Boolean) {
        _telemetryEnabled.value = enabled
        scope.launch {
            setTelemetryEnabledBlocking(enabled)
        }
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
            val g = gatt ?: run { updateStatus("No GATT"); return false }
            val ch = chServoLive ?: run { updateStatus("No SERVO_LIVE"); return false }
            if (!hasConnPermission()) return false

            val idx = pairIdx.coerceIn(0, 3)
            val angle = deg.coerceIn(0, 180)

            val payload = "A,$idx,$angle"
            //Log.d(TAG_LIVE, "ANDROID_SEND_LIVE='$payload'")

            val props = ch.properties
            val supportsNoResp =
                (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0

            ch.writeType =
                if (supportsNoResp) BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                else BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

            ch.value = payload.toByteArray()
            val ok = g.writeCharacteristic(ch)
            if (!ok) updateStatus("Live write failed")
            ok
        }

    /**
     * Stops live control for a single servo pair.
     */
    @SuppressLint("MissingPermission")
    fun stopServoLive(pairIdx: Int): Boolean =
        synchronized(writeMutex) {
            val g = gatt ?: return false
            val ch = chServoLive ?: return false
            if (!hasConnPermission()) return false

            val idx = pairIdx.coerceIn(0, 3)
            val payload = "S,$idx"
            //Log.d(TAG_LIVE, "ANDROID_SEND_LIVE_STOP='$payload'")

            val props = ch.properties
            val supportsNoResp =
                (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0

            ch.writeType =
                if (supportsNoResp) BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                else BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

            ch.value = payload.toByteArray()
            val ok = g.writeCharacteristic(ch)
            if (!ok) updateStatus("Live stop failed")
            ok
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
        if (teleUnlocked) return
        teleUnlocked = true

        scope.launch {
            val okObj = JSONObject().apply { put("type", "cfg_ok") }
            //Log.d(TAG_CFG, "ANDROID_SEND_CFG_OK = $okObj")
            val ok = writeJsonChunked(okObj.toString())
            if (ok) {
                setTelemetryEnabledBlocking(_telemetryEnabled.value)
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

        sessionAuthed = false
        devicePinSet = false
        seenTelemetry = false
        teleUnlocked = false
        authAttemptId++

        _controlUnlocked.value = false
        _authRequired.value = false
        _authSending.value = false
        _pinError.value = null

        teleParser = ChunkParser()
        cfgParser = ChunkParser()
        ackParser = ChunkParser()
        rawCfgInProgress = false
        rawCfgBuf.clear()
        _rawCfgText.value = ""

        writeInProgress = false
        lastWriteOk = true
        pendingWriteUuid = null

        expectedAckFor = null
        expectedAckDeferred = null
        lastTeleRxMs = 0L

        if (!hasConnPermission()) {
            connecting.set(false)
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

        sessionAuthed = false
        devicePinSet = false
        seenTelemetry = false
        teleUnlocked = false
        authAttemptId++

        _authRequired.value = false
        _authSending.value = false
        _pinError.value = null
        _controlUnlocked.value = false

        teleParser = ChunkParser()
        cfgParser = ChunkParser()
        ackParser = ChunkParser()

        writeInProgress = false
        lastWriteOk = true
        pendingWriteUuid = null

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
        notifyQueue.clear()

        rawCfgInProgress = false
        rawCfgBuf.clear()
        _rawCfgText.value = ""

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

            chCfgIn = svc.getCharacteristic(NewBleConstants.CFG_IN_UUID)
            chCfgOut = svc.getCharacteristic(NewBleConstants.CFG_OUT_UUID)
            chAck = svc.getCharacteristic(NewBleConstants.ACK_UUID)
            chTele = svc.getCharacteristic(NewBleConstants.TELE_UUID)
            chServoLive = svc.getCharacteristic(NewBleConstants.SERVO_LIVE_UUID)

            if (chCfgIn == null || chCfgOut == null || chAck == null || chTele == null || chServoLive == null) {
                updateStatus("Characteristics missing")
                disconnect()
                return
            }

            notifyQueue.clear()
            notifyQueue.addLast(chTele!!)
            notifyQueue.addLast(chCfgOut!!)
            notifyQueue.addLast(chAck!!)

            enableNextNotify(g)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (descriptor.uuid == NewBleConstants.CCC_UUID) {
                if (notifyQueue.isNotEmpty()) notifyQueue.removeFirst()

                if (notifyQueue.isNotEmpty()) {
                    enableNextNotify(gatt)
                } else {
                    updateStatus("Subscribed (tele/cfg/ack)")
                    requestConfig()
                }
            }
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
            val bytes = ch.value ?: return
            handleNotify(ch.uuid, bytes)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            ch: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleNotify(ch.uuid, value)
        }

        /**
         * Used by writeJsonChunked to know when the current write finished.
         */
        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            val p = pendingWriteUuid
            if (!writeInProgress || p == null || characteristic.uuid != p) {
                return
            }

            lastWriteOk = (status == BluetoothGatt.GATT_SUCCESS)
            writeInProgress = false
            pendingWriteUuid = null
        }
    }

    /**
     * Routes incoming bytes to the correct parser by characteristic UUID.
     */
    private fun handleNotify(uuid: java.util.UUID, bytes: ByteArray) {
        val text = decodeAscii(bytes)
        when (uuid) {
            NewBleConstants.TELE_UUID -> handleTeleStreamChunk(text)
            NewBleConstants.CFG_OUT_UUID -> handleCfgStreamChunk(text)
            NewBleConstants.ACK_UUID -> handleAckStreamChunk(text)
            else -> Log.w(TAG_CFG, "notify from unexpected UUID=$uuid")
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableNextNotify(g: BluetoothGatt) {
        val ch = notifyQueue.firstOrNull() ?: return
        enableNotifyOrIndicate(g, ch)
    }

    /**
     * Enables either notifications or indications depending on the channel type.
     */
    @SuppressLint("MissingPermission")
    private fun enableNotifyOrIndicate(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
        try {
            g.setCharacteristicNotification(ch, true)
            val ccc = ch.getDescriptor(NewBleConstants.CCC_UUID)

            if (ccc != null) {
                val isIndicate =
                    (ch.uuid == NewBleConstants.CFG_OUT_UUID || ch.uuid == NewBleConstants.ACK_UUID)

                ccc.value = if (isIndicate)
                    BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                else
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE

                val ok = g.writeDescriptor(ccc)
                if (!ok) {
                    if (notifyQueue.isNotEmpty() && notifyQueue.first() == ch) notifyQueue.removeFirst()
                    enableNextNotify(g)
                }
            } else {
                if (notifyQueue.isNotEmpty() && notifyQueue.first() == ch) notifyQueue.removeFirst()
                enableNextNotify(g)
            }
        } catch (_: Throwable) {
            updateStatus("Notify error")
        }
    }

    private fun handleTeleStreamChunk(s: String) {
        if (!teleParser.push(s)) return
        val json = teleParser.jsonOrNull() ?: return
        handleIncomingJson(json)
    }

    private fun handleCfgStreamChunk(s: String) {
        pushRawCfgChunk(s)
        if (!cfgParser.push(s)) return
        val json = cfgParser.jsonOrNull() ?: return
        //Log.d(TAG_CFG_JSON, "FULL_JSON (cfg) = $json")
        handleIncomingJson(json)
    }

    private fun handleAckStreamChunk(s: String) {
        if (!ackParser.push(s)) return
        val json = ackParser.jsonOrNull() ?: return
        handleIncomingJson(json)
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
     * Main protocol dispatcher for every JSON object received from the board.
     */
    private fun handleIncomingJson(json: JSONObject) {
        val type = json.optString("type", "")

        when (type) {
            "auth" -> {
                val required = json.optBoolean("required", false)
                if (required && !json.has("ok")) {
                    _authRequired.value = true
                    _controlUnlocked.value = false
                    _authSending.value = false
                    return
                }

                val ok = json.optBoolean("ok", false)
                _authSending.value = false

                if (ok) {
                    sessionAuthed = true
                    _pinError.value = null
                    _authRequired.value = false
                    _controlUnlocked.value = true
                    updateStatus("AUTH OK")
                    requestConfig()
                } else {
                    sessionAuthed = false
                    _authRequired.value = true
                    _controlUnlocked.value = false
                    _pinError.value = json.optString("err").ifBlank { "pin_wrong" }
                    updateStatus("AUTH FAIL")
                }
                return
            }

            "cfg" -> {
                val cfg = try {
                    EspSettings.fromJson(json)
                } catch (t: Throwable) {
                    updateStatus("Config parse error")
                    return
                }

                _settings.value = cfg
                updateStatus("Config updated")

                devicePinSet = cfg.pinSet || (cfg.pinCode != 0)

                val mustAuth = (cfg.authRequired || devicePinSet) && !sessionAuthed
                if (mustAuth) {
                    _authRequired.value = true
                    _controlUnlocked.value = false
                    _authSending.value = false
                } else {
                    _authRequired.value = false
                    _controlUnlocked.value = true
                    sendCfgOkOnce()
                }
                return
            }

            "tele" -> {
                val tParsed = try {
                    Telemetry.fromJson(json)
                } catch (t: Throwable) {
                    updateStatus("Tele parse error")
                    return
                }

                val rx = SystemClock.elapsedRealtime()
                lastTeleRxMs = rx
                seenTelemetry = true

                val prev = _telemetry.value
                _telemetry.value = tParsed.copy(status = prev.status, rxMs = rx)

                if (!_controlUnlocked.value) {
                    _authRequired.value = false
                    _authSending.value = false
                    _pinError.value = null
                    _controlUnlocked.value = true
                }
                return
            }

            "ack" -> {
                maybeCompleteAckWaiter(json)
                val ok = json.optBoolean("ok", false)
                updateStatus(if (ok) "ACK OK" else "ACK FAIL")
                return
            }

            else -> {
                when {
                    json.has("servoSettings") -> {
                        val cfg = try {
                            EspSettings.fromJson(json)
                        } catch (t: Throwable) {
                            updateStatus("Config parse error")
                            return
                        }

                        _settings.value = cfg
                        updateStatus("Config updated")

                        devicePinSet = cfg.pinSet || (cfg.pinCode != 0)
                        val mustAuth = (cfg.authRequired || devicePinSet) && !sessionAuthed
                        if (mustAuth) {
                            _authRequired.value = true
                            _controlUnlocked.value = false
                            _authSending.value = false
                        } else {
                            _authRequired.value = false
                            _controlUnlocked.value = true
                            sendCfgOkOnce()
                        }
                    }

                    json.has("fsr_raw") || json.has("flex_raw_0") -> {
                        val tParsed = try {
                            Telemetry.fromJson(json)
                        } catch (t: Throwable) {
                            updateStatus("Tele parse error")
                            return
                        }

                        val rx = SystemClock.elapsedRealtime()
                        lastTeleRxMs = rx
                        seenTelemetry = true

                        val prev = _telemetry.value
                        _telemetry.value = tParsed.copy(status = prev.status, rxMs = rx)

                        if (!_controlUnlocked.value) {
                            _authRequired.value = false
                            _authSending.value = false
                            _pinError.value = null
                            _controlUnlocked.value = true
                        }
                    }
                }
            }
        }
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
            val ch = chCfgIn ?: run { updateStatus("No CFG_IN"); return false }
            val g = gatt ?: run { updateStatus("No GATT"); return false }

            if (!hasConnPermission()) {
                updateStatus("No write permission")
                return false
            }

            ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

            fun safeWrite(str: String): Boolean {
                writeInProgress = true
                lastWriteOk = true
                pendingWriteUuid = ch.uuid

                ch.value = str.toByteArray()

                val started = g.writeCharacteristic(ch)
                if (!started) {
                    pendingWriteUuid = null
                    writeInProgress = false
                    return false
                }

                var waited = 0
                while (writeInProgress && waited < 6000) {
                    try {
                        Thread.sleep(20)
                    } catch (_: InterruptedException) {
                    }
                    waited += 20
                }

                val ok = !writeInProgress && lastWriteOk
                if (!ok) pendingWriteUuid = null
                return ok
            }

            return try {
                val payload = json
                val chunkSize = 18

                if (!safeWrite("[BEGIN]")) {
                    updateStatus("Write BEGIN failed"); return false
                }

                var i = 0
                while (i < payload.length) {
                    val end = (i + chunkSize).coerceAtMost(payload.length)
                    val part = payload.substring(i, end)
                    if (!safeWrite(part)) {
                        updateStatus("Write chunk failed"); return false
                    }
                    i = end
                }

                if (!safeWrite("[END]")) {
                    updateStatus("Write END failed"); return false
                }
                true
            } catch (_: Throwable) {
                updateStatus("Write failed")
                false
            }
        }

    /**
     * Stores normalized status key inside the telemetry flow.
     */
    private fun updateStatus(text: String) {
        val key = normalizeStatusKey(text)
        scope.launch {
            val tPrev = _telemetry.value
            _telemetry.value = tPrev.copy(status = key)
        }
    }

    /**
     * Converts arbitrary debug text into stable UI-friendly status keys.
     */
    private fun normalizeStatusKey(text: String): String {
        val t = text.trim().lowercase()

        return when {
            t.startsWith("disconnected") -> "disconnected"
            t.startsWith("connecting") -> "connecting"
            t.startsWith("discovering") -> "discovering"
            t.startsWith("scanning") -> "scanning"
            t.startsWith("bluetooth off") -> "bluetooth_off"
            t.startsWith("no scan permission") -> "no_scan_permission"
            t.startsWith("scan denied") -> "scan_denied"
            t.startsWith("scan error") -> "scan_error"
            t.startsWith("scan failed") -> "scan_failed"
            t.startsWith("invalid device") -> "invalid_device"
            t.startsWith("connect failed") -> "connect_failed"
            t.startsWith("no connect permission") -> "no_connect_permission"
            t.startsWith("service discovery failed") -> "service_discovery_failed"
            t.startsWith("service discovery error") -> "service_discovery_error"
            t.startsWith("service not found") -> "service_not_found"
            t.startsWith("characteristics missing") -> "characteristics_missing"
            t.startsWith("notify error") -> "notify_error"
            t.startsWith("subscribed") -> "subscribed"
            t.startsWith("auth ok") -> "auth_ok"
            t.startsWith("auth fail") -> "auth_fail"
            t.startsWith("config updated") -> "config_updated"
            t.startsWith("config parse error") -> "config_parse_error"
            t.startsWith("tele parse error") -> "tele_parse_error"
            t.startsWith("ack ok") -> "ack_ok"
            t.startsWith("ack fail") -> "ack_fail"
            t.startsWith("write") -> "write_failed"
            else -> t.replace(Regex("[^a-z0-9]+"), "_").trim('_')
        }
    }

    /**
     * Keeps only printable ASCII characters from BLE payload.
     */
    private fun decodeAscii(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            if (v in 32..126) sb.append(v.toChar())
        }
        return sb.toString().trim()
    }

    /**
     * Stores the raw config text exactly as it is reconstructed from chunks. Used for debugging mostly
     */
    private fun pushRawCfgChunk(s: String) {
        when (s) {
            "[BEGIN]" -> {
                rawCfgInProgress = true
                rawCfgBuf.clear()
                _rawCfgText.value = "[BEGIN]"
            }

            "[END]" -> {
                rawCfgInProgress = false
                _rawCfgText.value = "[BEGIN]\n${rawCfgBuf}\n[END]"
            }

            else -> {
                if (rawCfgInProgress) {
                    rawCfgBuf.append(s)
                    _rawCfgText.value = "[BEGIN]\n${rawCfgBuf}"
                }
            }
        }
    }
}