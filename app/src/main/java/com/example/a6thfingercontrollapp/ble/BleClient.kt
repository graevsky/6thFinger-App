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
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

class BleClient(private val context: Context) {

    private val _rawCfgText = MutableStateFlow("")
    val rawCfgText: StateFlow<String> = _rawCfgText.asStateFlow()

    private var rawCfgInProgress = false
    private val rawCfgBuf = StringBuilder()

    private val TAG_CFG = "BLE_CFG"
    private val TAG_CFG_JSON = "BLE_CFG_JSON"
    private val TAG_TELE = "BLE_TELE"

    private val manager: BluetoothManager? =
        context.getSystemService(BluetoothManager::class.java)

    private val adapter: BluetoothAdapter?
        get() = manager?.adapter

    private val scanner
        get() = adapter?.bluetoothLeScanner

    private var gatt: BluetoothGatt? = null

    private var chCfgIn: BluetoothGattCharacteristic? = null
    private var chCfgOut: BluetoothGattCharacteristic? = null
    private var chAck: BluetoothGattCharacteristic? = null
    private var chTele: BluetoothGattCharacteristic? = null

    private val notifyQueue = ArrayDeque<BluetoothGattCharacteristic>()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _telemetry = MutableStateFlow(Telemetry())
    val telemetry: StateFlow<Telemetry> = _telemetry.asStateFlow()
    val state: StateFlow<Telemetry> = telemetry

    private val _settings = MutableStateFlow<EspSettings?>(null)
    val settings: StateFlow<EspSettings?> = _settings.asStateFlow()

    private val _devices = MutableStateFlow<List<BleDeviceUi>>(emptyList())
    val devices: StateFlow<List<BleDeviceUi>> = _devices.asStateFlow()

    private val scanning = AtomicBoolean(false)
    private val connecting = AtomicBoolean(false)

    private var teleParser = ChunkParser()
    private var cfgParser = ChunkParser()
    private var ackParser = ChunkParser()

    private val writeMutex = Any()

    @Volatile
    private var writeInProgress: Boolean = false
    @Volatile
    private var lastWriteOk: Boolean = true

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

    fun start() { /* no-op */
    }

    fun stop() {
        stopScan()
        disconnectNow()
    }

    fun disconnectNow() = disconnect()

    fun isBleReady(): Boolean = adapter?.isEnabled == true

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

    fun applySettings(settings: EspSettings): Boolean {
        val base = JSONObject(settings.toJsonString())
        base.put("type", "cfg_set")
        val json = base.toString()
        Log.d(TAG_CFG, "ANDROID_SEND_CFG_JSON = $json")
        return writeJsonChunked(json)
    }

    fun requestConfig() {
        scope.launch {
            delay(200)
            val obj = JSONObject().apply { put("type", "cfg_get") }
            Log.d(TAG_CFG, "ANDROID_SEND_CFG_GET = $obj")
            writeJsonChunked(obj.toString())
        }
    }

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
            Log.d(TAG_CFG, "ANDROID_SEND_AUTH = $obj")

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

    private fun sendCfgOkOnce() {
        if (teleUnlocked) return
        teleUnlocked = true

        scope.launch {
            val obj = JSONObject().apply { put("type", "cfg_ok") }
            Log.d(TAG_CFG, "ANDROID_SEND_CFG_OK = $obj")
            writeJsonChunked(obj.toString())
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
        notifyQueue.clear()

        rawCfgInProgress = false
        rawCfgBuf.clear()
        _rawCfgText.value = ""

        updateStatus("Disconnected")
    }

    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            Log.d(TAG_CFG, "onConnectionStateChange status=$status newState=$newState")

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

            if (chCfgIn == null || chCfgOut == null || chAck == null || chTele == null) {
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

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            lastWriteOk = (status == BluetoothGatt.GATT_SUCCESS)
            writeInProgress = false
        }
    }

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
        Log.d(TAG_TELE, "tele chunk='$s'")
        if (!teleParser.push(s)) return
        val json = teleParser.jsonOrNull() ?: return
        Log.d(TAG_TELE, "FULL_JSON (tele) = $json")
        handleIncomingJson(json)
    }

    private fun handleCfgStreamChunk(s: String) {
        Log.d(TAG_CFG, "cfg chunk='$s'")
        pushRawCfgChunk(s)

        if (!cfgParser.push(s)) return
        val json = cfgParser.jsonOrNull() ?: return
        Log.d(TAG_CFG_JSON, "FULL_JSON (cfg) = $json")
        handleIncomingJson(json)
    }

    private fun handleAckStreamChunk(s: String) {
        Log.d(TAG_CFG, "ack chunk='$s'")
        if (!ackParser.push(s)) return
        val json = ackParser.jsonOrNull() ?: return
        Log.d(TAG_CFG, "FULL_JSON (ack) = $json")
        handleIncomingJson(json)
    }

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
                    Log.e(TAG_CFG, "Config parse error", t)
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
                val t = try {
                    Telemetry.fromJson(json)
                } catch (t: Throwable) {
                    Log.e(TAG_TELE, "Tele parse error", t)
                    updateStatus("Tele parse error")
                    return
                }

                seenTelemetry = true
                val prev = _telemetry.value
                _telemetry.value = t.copy(status = prev.status)

                if (!_controlUnlocked.value) {
                    _authRequired.value = false
                    _authSending.value = false
                    _pinError.value = null
                    _controlUnlocked.value = true
                }
                return
            }

            "ack" -> {
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
                            Log.e(TAG_CFG, "Config parse error", t)
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
                        val t = try {
                            Telemetry.fromJson(json)
                        } catch (t: Throwable) {
                            Log.e(TAG_TELE, "Tele parse error", t)
                            updateStatus("Tele parse error")
                            return
                        }
                        seenTelemetry = true
                        val prev = _telemetry.value
                        _telemetry.value = t.copy(status = prev.status)

                        if (!_controlUnlocked.value) {
                            _authRequired.value = false
                            _authSending.value = false
                            _pinError.value = null
                            _controlUnlocked.value = true
                        }
                    }

                    else -> Log.w(TAG_CFG, "Unknown JSON type: $json")
                }
            }
        }
    }

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
                ch.value = str.toByteArray()

                val started = g.writeCharacteristic(ch)
                if (!started) {
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
                return !writeInProgress && lastWriteOk
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

    private fun updateStatus(text: String) {
        scope.launch {
            val tPrev = _telemetry.value
            _telemetry.value = tPrev.copy(status = text)
        }
    }

    private fun decodeAscii(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            if (v in 32..126) sb.append(v.toChar())
        }
        return sb.toString().trim()
    }

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
