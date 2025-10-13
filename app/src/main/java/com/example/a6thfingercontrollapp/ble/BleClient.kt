package com.example.a6thfingercontrollapp.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean

data class Telemetry(
    val fsrOhm: Float = 0f,
    val flexOhm: Float = 0f,
    val servoDeg: Float = 0f,
    val status: String = "Idle"
)

class BleClient(private val context: Context) {

    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val adapter: BluetoothAdapter? get() = bluetoothManager?.adapter
    private val scanner: BluetoothLeScanner? get() = adapter?.bluetoothLeScanner

    private var gatt: BluetoothGatt? = null
    private var flexChar: BluetoothGattCharacteristic? = null
    private var fsrChar: BluetoothGattCharacteristic? = null

    private var servoChar: BluetoothGattCharacteristic? = null

    private var cfg = ConfigChars()

    private val scope = CoroutineScope(Dispatchers.Main)

    private val _state = MutableStateFlow(Telemetry(status = "Idle"))
    val state = _state.asStateFlow()

    private val _devices = MutableStateFlow<List<BleDeviceUi>>(emptyList())
    val devices = _devices.asStateFlow()

    private val _boardSettings = MutableStateFlow<DeviceSettings?>(null)
    val boardSettings = _boardSettings.asStateFlow()

    private val scanning = AtomicBoolean(false)
    private val connecting = AtomicBoolean(false)

    private val subscribeQueue: ArrayDeque<BluetoothGattCharacteristic> = ArrayDeque()
    private var subscribeInProgress = false

    private val readQueue: ArrayDeque<BluetoothGattCharacteristic> = ArrayDeque()
    private var readInProgress = false

    private val writeQueue: ArrayDeque<Pair<BluetoothGattCharacteristic, ByteArray>> = ArrayDeque()
    private var writeInProgress = false

    private var reconnectAttempted = false

    fun start() {}
    fun stop() {
        stopScan()
    }

    fun isBleReady(): Boolean = adapter?.isEnabled == true

    fun disconnectNow() {
        disconnect()
    }

    private fun hasScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= 31) {
            checkSelf(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            checkSelf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= 31) {
            checkSelf(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            true
        }
    }

    private fun checkSelf(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    private var scanStopPosted = false

    fun scan() {
        if (scanning.getAndSet(true)) return

        if (!hasScanPermission()) {
            scanning.set(false)
            _state.value = _state.value.copy(status = "No scan permission")
            return
        }
        if (!isBleReady()) {
            scanning.set(false)
            _state.value = _state.value.copy(status = "Bluetooth off")
            return
        }

        _devices.value = emptyList()
        _state.value = _state.value.copy(status = "Scanning")

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner?.startScan(null, settings, scanCallback)
            if (!scanStopPosted) {
                scanStopPosted = true
                CoroutineScope(Dispatchers.Main).launch {
                    kotlinx.coroutines.delay(10_000)
                    if (scanning.get()) {
                        stopScan()
                        if (_devices.value.isEmpty()) {
                            _state.value =
                                _state.value.copy(status = "Not found (timeout). Enable Location?")
                        }
                    }
                    scanStopPosted = false
                }
            }
        } catch (_: SecurityException) {
            scanning.set(false)
            _state.value = _state.value.copy(status = "Scan denied")
        }
    }

    private fun stopScan() {
        if (!scanning.getAndSet(false)) return
        if (!hasScanPermission()) return
        try {
            scanner?.stopScan(scanCallback)
        } catch (_: SecurityException) {
        }
    }

    @SuppressLint("MissingPermission")
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = (result.scanRecord?.deviceName ?: result.device.name
            ?: "Unnamed").ifBlank { "Unnamed" }
            val uuids =
                result.scanRecord?.serviceUuids?.mapNotNull { it?.uuid }?.toSet() ?: emptySet()
            val hasTele = uuids.contains(BleConstants.SERVICE_UUID)
            val hasCfg = uuids.contains(BleConstants.CFG_SERVICE_UUID)
            if (!hasTele && !hasCfg) return

            val addr = result.device.address
            val curr = _devices.value
            if (curr.none { it.address == addr }) {
                _devices.value = curr + BleDeviceUi(name = name, address = addr)
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
        }

        override fun onScanFailed(errorCode: Int) {
            _state.value = _state.value.copy(status = "Scan failed: $errorCode")
            scanning.set(false)
        }
    }

    fun connectByAddress(address: String) {
        val dev = adapter?.getRemoteDevice(address) ?: run {
            _state.value = _state.value.copy(status = "Device not found")
            return
        }
        connect(dev)
    }

    private fun connect(device: BluetoothDevice) {
        if (connecting.getAndSet(true)) return

        if (!hasConnectPermission()) {
            connecting.set(false)
            _state.value = _state.value.copy(status = "No connect permission")
            return
        }

        _state.value = _state.value.copy(status = "Connecting")
        try {
            reconnectAttempted = false
            gatt = if (Build.VERSION.SDK_INT >= 33) {
                device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(context, false, gattCallback)
            }
        } catch (_: SecurityException) {
            connecting.set(false)
            _state.value = _state.value.copy(status = "Connect denied")
        }
    }

    @SuppressLint("MissingPermission")
    private fun disconnect() {
        connecting.set(false)
        subscribeQueue.clear()
        subscribeInProgress = false
        readQueue.clear(); readInProgress = false
        writeQueue.clear(); writeInProgress = false
        try {
            if (hasConnectPermission()) gatt?.disconnect()
        } catch (_: SecurityException) {
        }
        try {
            gatt?.close()
        } catch (_: Throwable) {
        }
        gatt = null
        flexChar = null
        servoChar = null
        cfg = ConfigChars()
        _state.value = _state.value.copy(status = "Disconnected")
    }

    @SuppressLint("MissingPermission")
    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                try {
                    g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                } catch (_: SecurityException) {
                }
                scope.launch { _state.value = _state.value.copy(status = "Discovering") }
                try {
                    g.discoverServices()
                } catch (_: SecurityException) {
                    scope.launch { _state.value = _state.value.copy(status = "Discovery denied") }
                    disconnect()
                }
            } else {
                scope.launch { _state.value = _state.value.copy(status = "Disconnected") }
                disconnect()
                if (!reconnectAttempted && status != BluetoothGatt.GATT_SUCCESS) {
                    reconnectAttempted = true
                    scope.launch {
                        kotlinx.coroutines.delay(600)
                        scan()
                    }
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                scope.launch {
                    _state.value = _state.value.copy(status = "Service discovery failed")
                }
                disconnect(); return
            }
            if (!hasConnectPermission()) {
                scope.launch { _state.value = _state.value.copy(status = "No connect permission") }
                disconnect(); return
            }

            val tele = safeGetService(g, BleConstants.SERVICE_UUID)
            fsrChar = tele?.getCharacteristic(BleConstants.FSR_UUID)
            flexChar = tele?.getCharacteristic(BleConstants.FLEX_UUID)
            servoChar = tele?.getCharacteristic(BleConstants.SERVO_UUID)

            if (fsrChar == null || flexChar == null || servoChar == null) {
                scope.launch {
                    _state.value = _state.value.copy(status = "Characteristics not found")
                }
                disconnect(); return
            }

            val cs = safeGetService(g, BleConstants.CFG_SERVICE_UUID)
            cfg = ConfigChars().initFrom(cs)

            subscribeQueue.clear()
            subscribeQueue.add(fsrChar!!)
            subscribeQueue.add(flexChar!!)
            subscribeQueue.add(servoChar!!)
            subscribeInProgress = false
            proceedSubscribe(g)
        }


        override fun onDescriptorWrite(
            g: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            proceedSubscribe(g)
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val value = characteristic.value ?: return
            val f = bytesToFloatLE(value)
            scope.launch {
                val cur = _state.value
                when (characteristic.uuid) {
                    BleConstants.FSR_UUID -> _state.value =
                        cur.copy(fsrOhm = f, status = "Subscribed")

                    BleConstants.FLEX_UUID -> _state.value =
                        cur.copy(flexOhm = f, status = "Subscribed")

                    BleConstants.SERVO_UUID -> _state.value =
                        cur.copy(servoDeg = f, status = "Subscribed")
                }
            }
        }

        override fun onCharacteristicRead(
            g: BluetoothGatt,
            ch: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) onConfigPieceRead(ch)
            readInProgress = false
            proceedRead(g)
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            ch: BluetoothGattCharacteristic,
            status: Int
        ) {
            writeInProgress = false
            proceedWrite(g)
        }

    }

    private fun safeGetService(g: BluetoothGatt, uuid: java.util.UUID): BluetoothGattService? {
        return try {
            g.getService(uuid)
        } catch (_: SecurityException) {
            null
        }
    }


    @SuppressLint("MissingPermission")
    private fun proceedSubscribe(gatt: BluetoothGatt) {
        if (subscribeInProgress) return
        val next = subscribeQueue.pollFirst()
        if (next == null) {
            scope.launch { _state.value = _state.value.copy(status = "Subscribed") }
            connecting.set(false)
            requestReadSettings()
            return
        }

        if (!hasConnectPermission()) {
            scope.launch { _state.value = _state.value.copy(status = "No connect permission") }
            disconnect(); return
        }

        subscribeInProgress = true
        val ok = enableNotificationsOnce(gatt, next)
        subscribeInProgress = false
        if (!ok) {
            scope.launch { _state.value = _state.value.copy(status = "Notify denied") }
            disconnect()
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableNotificationsOnce(
        gatt: BluetoothGatt,
        ch: BluetoothGattCharacteristic
    ): Boolean {
        if (!hasConnectPermission()) return false
        return try {
            gatt.setCharacteristicNotification(ch, true)
            val ccc = ch.getDescriptor(BleConstants.CCC_DESCRIPTOR_UUID)
                ?: BluetoothGattDescriptor(
                    BleConstants.CCC_DESCRIPTOR_UUID,
                    BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
                ).also { ch.addDescriptor(it) }
            ccc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(ccc)
        } catch (_: SecurityException) {
            false
        } catch (_: Throwable) {
            false
        }
    }


    fun requestReadSettings() {
        val g = gatt ?: return
        if (!hasConnectPermission()) return
        if (!cfg.isReady()) return

        readQueue.clear()
        cfg.allForRead().forEach { ch -> if (ch != null) readQueue.add(ch) }
        proceedRead(g)
    }

    @SuppressLint("MissingPermission")
    private fun proceedRead(g: BluetoothGatt) {
        if (readInProgress) return
        val next = readQueue.pollFirst() ?: run { return }
        readInProgress = true
        try {
            g.readCharacteristic(next)
        } catch (_: Throwable) {
            readInProgress = false
            proceedRead(g)
        }
    }

    private fun onConfigPieceRead(ch: BluetoothGattCharacteristic) {
        cfg.takeValue(ch)
        if (readQueue.isEmpty()) {
            _boardSettings.value = cfg.toDeviceSettingsOrNull()
            scope.launch {
                _state.value = _state.value.copy(status = "Config synced")
            }
        }
    }

    fun applySettings(address: String, settings: DeviceSettings): Boolean {
        val g = gatt ?: return false
        if (!hasConnectPermission()) return false
        if (!cfg.isReady()) return false

        writeQueue.clear()
        fun u8(v: Int) = byteArrayOf((v and 0xFF).toByte())
        fun u16(v: Int) =
            ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(v.toShort()).array()

        fun u32(v: Int) = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()

        enqueue(cfg.fsrPin, u8(settings.fsrPin))
        enqueue(cfg.fsrPull, u16(settings.fsrPullupOhm))
        enqueue(cfg.fsrStart, u32(settings.fsrStartOhm))
        enqueue(cfg.fsrMax, u32(settings.fsrMaxOhm))


        enqueue(cfg.flexPin, u8(settings.flexPin))
        enqueue(cfg.flexFlat, u32(settings.flexFlatOhm))
        enqueue(cfg.flexBend, u32(settings.flexBendOhm))

        enqueue(cfg.vibroPin, u8(settings.vibroPin))
        enqueue(cfg.vibroFreq, u16(settings.vibroPulseFreqHz))
        enqueue(cfg.vibroThreshold, u16(settings.vibroThreshold))
        enqueue(cfg.vibroPower, u8(settings.vibroPowerPct))

        enqueue(cfg.servoPin, u8(settings.servoPin))
        enqueue(cfg.servoMin, u8(settings.servoMinDeg))
        enqueue(cfg.servoMax, u8(settings.servoMaxDeg))
        enqueue(cfg.servoManual, u8(if (settings.servoManualMode) 1 else 0))
        enqueue(cfg.servoManualDeg, u8(settings.servoManualDeg))

        enqueue(cfg.apply, byteArrayOf(1))

        proceedWrite(g)
        return true
    }

    private fun enqueue(ch: BluetoothGattCharacteristic?, data: ByteArray) {
        if (ch == null) return
        writeQueue.add(ch to data)
    }

    @SuppressLint("MissingPermission")
    private fun proceedWrite(g: BluetoothGatt) {
        if (writeInProgress) return
        val next = writeQueue.pollFirst() ?: run {
            scope.launch { _state.value = _state.value.copy(status = "Config applied") }
            return
        }
        writeInProgress = true
        try {
            next.first.value = next.second
            g.writeCharacteristic(next.first)
        } catch (_: Throwable) {
            writeInProgress = false
            proceedWrite(g)
        }
    }

    companion object {
        fun bytesToFloatLE(bytes: ByteArray): Float {
            if (bytes.size < 4) return 0f
            return ByteBuffer.wrap(bytes.copyOfRange(0, 4))
                .order(ByteOrder.LITTLE_ENDIAN)
                .float
        }
    }

    private class ConfigChars {
        var fsrPin: BluetoothGattCharacteristic? = null
        var fsrPull: BluetoothGattCharacteristic? = null
        var fsrStart: BluetoothGattCharacteristic? = null
        var fsrMax: BluetoothGattCharacteristic? = null

        var flexPin: BluetoothGattCharacteristic? = null
        var flexFlat: BluetoothGattCharacteristic? = null
        var flexBend: BluetoothGattCharacteristic? = null

        var vibroPin: BluetoothGattCharacteristic? = null
        var vibroFreq: BluetoothGattCharacteristic? = null
        var vibroThreshold: BluetoothGattCharacteristic? = null
        var vibroPower: BluetoothGattCharacteristic? = null

        var servoPin: BluetoothGattCharacteristic? = null
        var servoMin: BluetoothGattCharacteristic? = null
        var servoMax: BluetoothGattCharacteristic? = null
        var servoManual: BluetoothGattCharacteristic? = null
        var servoManualDeg: BluetoothGattCharacteristic? = null

        var apply: BluetoothGattCharacteristic? = null


        var v_fsrStart: Int? = null
        var v_fsrMax: Int? = null
        var v_fsrPin: Int? = null
        var v_fsrPull: Int? = null
        var v_flexPin: Int? = null
        var v_flexFlat: Int? = null
        var v_flexBend: Int? = null
        var v_vibroPin: Int? = null
        var v_vibroFreq: Int? = null
        var v_vibroThresh: Int? = null
        var v_vibroPower: Int? = null
        var v_servoPin: Int? = null
        var v_servoMin: Int? = null
        var v_servoMax: Int? = null
        var v_servoManual: Int? = null
        var v_servoManualDeg: Int? = null

        fun initFrom(service: BluetoothGattService?): ConfigChars {
            fun c(u: java.util.UUID) = service?.getCharacteristic(u)
            fsrPin = c(BleConstants.CFG_FSR_PIN)
            fsrPull = c(BleConstants.CFG_FSR_PULLUP)
            fsrStart = c(BleConstants.CFG_FSR_START)
            fsrMax = c(BleConstants.CFG_FSR_MAX)


            flexPin = c(BleConstants.CFG_FLEX_PIN)
            flexFlat = c(BleConstants.CFG_FLEX_FLAT)
            flexBend = c(BleConstants.CFG_FLEX_BEND)

            vibroPin = c(BleConstants.CFG_VIBRO_PIN)
            vibroFreq = c(BleConstants.CFG_VIBRO_FREQ)
            vibroThreshold = c(BleConstants.CFG_VIBRO_THRESH)
            vibroPower = c(BleConstants.CFG_VIBRO_POWER)

            servoPin = c(BleConstants.CFG_SERVO_PIN)
            servoMin = c(BleConstants.CFG_SERVO_MIN)
            servoMax = c(BleConstants.CFG_SERVO_MAX)
            servoManual = c(BleConstants.CFG_SERVO_MANUAL)
            servoManualDeg = c(BleConstants.CFG_SERVO_MANUAL_DEG)

            apply = c(BleConstants.CFG_APPLY)
            return this
        }

        fun isReady(): Boolean {
            return fsrPin != null && flexPin != null && vibroPin != null && servoPin != null && apply != null
        }

        fun allForRead(): List<BluetoothGattCharacteristic?> = listOf(
            fsrPin, fsrPull, fsrStart, fsrMax,
            flexPin, flexFlat, flexBend,
            vibroPin, vibroFreq, vibroThreshold, vibroPower,
            servoPin, servoMin, servoMax, servoManual, servoManualDeg
        )


        fun takeValue(ch: BluetoothGattCharacteristic) {
            val b = ch.value ?: return
            val le = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
            fun u8() = (b.first().toInt() and 0xFF)
            fun u16() = (le.short.toInt() and 0xFFFF)
            fun u32() = le.int

            when (ch.uuid) {
                BleConstants.CFG_FSR_PIN -> v_fsrPin = u8()
                BleConstants.CFG_FSR_PULLUP -> v_fsrPull = u16()
                BleConstants.CFG_FSR_START -> v_fsrStart = u32()
                BleConstants.CFG_FSR_MAX -> v_fsrMax = u32()

                BleConstants.CFG_FLEX_PIN -> v_flexPin = u8()
                BleConstants.CFG_FLEX_FLAT -> v_flexFlat = u32()
                BleConstants.CFG_FLEX_BEND -> v_flexBend = u32()

                BleConstants.CFG_VIBRO_PIN -> v_vibroPin = u8()
                BleConstants.CFG_VIBRO_FREQ -> v_vibroFreq = u16()
                BleConstants.CFG_VIBRO_THRESH -> v_vibroThresh = u16()
                BleConstants.CFG_VIBRO_POWER -> v_vibroPower = u8()

                BleConstants.CFG_SERVO_PIN -> v_servoPin = u8()
                BleConstants.CFG_SERVO_MIN -> v_servoMin = u8()
                BleConstants.CFG_SERVO_MAX -> v_servoMax = u8()
                BleConstants.CFG_SERVO_MANUAL -> v_servoManual = u8()
                BleConstants.CFG_SERVO_MANUAL_DEG -> v_servoManualDeg = u8()
            }
        }

        fun toDeviceSettingsOrNull(): DeviceSettings? {
            if (v_fsrPin == null || v_flexPin == null || v_vibroPin == null || v_servoPin == null) return null
            return DeviceSettings(
                fsrPin = v_fsrPin ?: 34,
                fsrPullupOhm = v_fsrPull ?: 4700,
                fsrStartOhm = v_fsrStart ?: 100000,
                fsrMaxOhm = v_fsrMax ?: 20000,
                flexPin = v_flexPin ?: 35,
                flexFlatOhm = v_flexFlat ?: 45000,
                flexBendOhm = v_flexBend ?: 33400,
                vibroPin = v_vibroPin ?: 25,
                vibroPulseFreqHz = v_vibroFreq ?: 10,
                vibroThreshold = v_vibroThresh ?: 50,
                vibroPowerPct = v_vibroPower ?: 60,
                servoPin = v_servoPin ?: 18,
                servoMinDeg = v_servoMin ?: 40,
                servoMaxDeg = v_servoMax ?: 180,
                servoManualMode = (v_servoManual ?: 0) != 0,
                servoManualDeg = v_servoManualDeg ?: 90
            )
        }
    }
}
