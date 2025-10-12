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
    private var servoChar: BluetoothGattCharacteristic? = null

    private val scope = CoroutineScope(Dispatchers.Main)

    private val _state = MutableStateFlow(Telemetry(status = "Idle"))
    val state = _state.asStateFlow()

    private val _devices = MutableStateFlow<List<BleDeviceUi>>(emptyList())
    val devices = _devices.asStateFlow()

    private val scanning = AtomicBoolean(false)
    private val connecting = AtomicBoolean(false)

    private val subscribeQueue: ArrayDeque<BluetoothGattCharacteristic> = ArrayDeque()
    private var subscribeInProgress = false
    private var reconnectAttempted = false

    fun start() {}

    fun stop() {
        stopScan()
    }

    fun isBleReady(): Boolean = adapter?.isEnabled == true


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
            val advName = result.scanRecord?.deviceName
            val devName = result.device.name
            val name = (advName ?: devName ?: "Unnamed").ifBlank { "Unnamed" }

            val hasOurService = result.scanRecord?.serviceUuids?.any {
                it?.uuid == BleConstants.SERVICE_UUID
            } == true

            if (!hasOurService) return

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
        } catch (se: SecurityException) {
            connecting.set(false)
            _state.value = _state.value.copy(status = "Connect denied")
        }
    }

    @SuppressLint("MissingPermission")
    private fun disconnect() {
        connecting.set(false)
        subscribeQueue.clear()
        subscribeInProgress = false
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

            val service = try {
                g.getService(BleConstants.SERVICE_UUID)
            } catch (_: SecurityException) {
                null
            }
            flexChar = service?.getCharacteristic(BleConstants.FLEX_UUID)
            servoChar = service?.getCharacteristic(BleConstants.SERVO_UUID)

            if (flexChar == null || servoChar == null) {
                scope.launch {
                    _state.value = _state.value.copy(status = "Characteristics not found")
                }
                disconnect(); return
            }

            subscribeQueue.clear()
            subscribeQueue.add(flexChar)
            subscribeQueue.add(servoChar)
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
                    BleConstants.FLEX_UUID -> _state.value =
                        cur.copy(flexOhm = f, status = "Subscribed")

                    BleConstants.SERVO_UUID -> _state.value =
                        cur.copy(servoDeg = f, status = "Subscribed")
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun proceedSubscribe(gatt: BluetoothGatt) {
        if (subscribeInProgress) return
        val next = subscribeQueue.pollFirst()
        if (next == null) {
            scope.launch { _state.value = _state.value.copy(status = "Subscribed") }
            connecting.set(false)
            return
        }

        if (!hasConnectPermission()) {
            scope.launch { _state.value = _state.value.copy(status = "No connect permission") }
            disconnect(); return
        }

        subscribeInProgress = true
        val ok = enableNotificationsOnce(gatt, next)
        if (!ok) {
            subscribeInProgress = false
            scope.launch { _state.value = _state.value.copy(status = "Notify denied") }
            disconnect()
        } else {
            subscribeInProgress = false
        }
    }

    private fun enableNotificationsOnce(
        gatt: BluetoothGatt,
        ch: BluetoothGattCharacteristic
    ): Boolean {
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

    companion object {
        fun bytesToFloatLE(bytes: ByteArray): Float {
            if (bytes.size < 4) return 0f
            return ByteBuffer.wrap(bytes.copyOfRange(0, 4))
                .order(ByteOrder.LITTLE_ENDIAN)
                .float
        }
    }
}
