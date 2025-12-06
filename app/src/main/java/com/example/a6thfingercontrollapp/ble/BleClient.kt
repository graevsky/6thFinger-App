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
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

class BleClient(private val context: Context) {
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

    private val scope = CoroutineScope(Dispatchers.IO)

    private val _telemetry = MutableStateFlow(Telemetry())
    val telemetry: StateFlow<Telemetry> = _telemetry.asStateFlow()

    val state: StateFlow<Telemetry> = telemetry

    private val _settings = MutableStateFlow<EspSettings?>(null)
    val settings: StateFlow<EspSettings?> = _settings.asStateFlow()

    private val _status = MutableStateFlow("Idle")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _devices = MutableStateFlow<List<BleDeviceUi>>(emptyList())
    val devices: StateFlow<List<BleDeviceUi>> = _devices.asStateFlow()

    private val scanning = AtomicBoolean(false)
    private val connecting = AtomicBoolean(false)

    private val cfgParser = ChunkParser()
    private val teleParser = ChunkParser()

    fun start() {
        // stub
    }

    fun stop() {
        stopScan()
        disconnectNow()
    }

    fun disconnectNow() {
        disconnect()
    }

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

    fun requestConfig() {
        scope.launch {
            writeJsonChunked("{}")
        }
    }


    fun applySettings(settings: EspSettings): Boolean {
        val json = settings.toJsonString()
        return writeJsonChunked(json)
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
            val uuids = rec.serviceUuids?.map { it.uuid } ?: emptyList()

            if (!uuids.contains(NewBleConstants.SERVICE_UUID)) return

            val dev = result.device
            val list = _devices.value

            if (list.none { it.address == dev.address }) {
                val name = dev.name ?: rec.deviceName ?: "ESP32"
                _devices.value = list + BleDeviceUi(
                    name = name,
                    address = dev.address
                )
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
            if (hasScanPermission()) {
                scanner?.stopScan(scanCallback)
            }
        } catch (_: SecurityException) {
            updateStatus("Scan stop denied")
        } catch (_: Throwable) {
            updateStatus("Scan stop error")
        }
    }


    @SuppressLint("MissingPermission")
    private fun connect(dev: BluetoothDevice) {
        if (connecting.getAndSet(true)) return

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

        try {
            if (hasConnPermission()) {
                gatt?.disconnect()
            }
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

        updateStatus("Disconnected")
    }

    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS &&
                newState == BluetoothProfile.STATE_CONNECTED
            ) {
                updateStatus("Discovering")
                try {
                    g.discoverServices()
                } catch (_: Throwable) {
                    updateStatus("Service discovery error")
                    disconnect()
                }
            } else {
                disconnect()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
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

            if (chTele == null) {
                updateStatus("Telemetry char missing")
                disconnect()
                return
            }

            enableNotify(g, chTele!!)
            updateStatus("Subscribed (tele only)")

            requestConfig()
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            ch: BluetoothGattCharacteristic
        ) {
            val bytes = ch.value ?: return

            val hex = bytes.joinToString(" ") { "%02X".format(it) }
            //Log.d("BLE_HEX", "uuid=${ch.uuid} hex=$hex")

            val text = decodeAscii(bytes)
            //Log.d("BLE_RAW", "notif uuid=${ch.uuid} ascii='$text'")

            when (ch.uuid) {
                NewBleConstants.CFG_OUT_UUID -> handleCfgChunk(text)
                NewBleConstants.ACK_UUID -> handleAck(text)
                NewBleConstants.TELE_UUID -> handleTeleChunk(text)
            }
        }


        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableNotify(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
        try {
            //Log.d("BLE_DBG", "enableNotify uuid=${ch.uuid}")
            g.setCharacteristicNotification(ch, true)

            val ccc = ch.getDescriptor(NewBleConstants.CCC_UUID)
            if (ccc != null) {
                ccc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                g.writeDescriptor(ccc)
            } else {
                updateStatus("No CCC for ${ch.uuid}")
            }
        } catch (_: SecurityException) {
            updateStatus("Notify denied")
        } catch (_: Throwable) {
            updateStatus("Notify error")
        }
    }


    private fun handleAck(s: String) {
        // {"ack":true} или {"ack":false}
        val text = s.trim()
        val label = try {
            val obj = JSONObject(text)
            val ok = obj.optBoolean("ack", false)
            if (ok) "ACK OK" else "ACK FAIL"
        } catch (_: Throwable) {
            "ACK: $text"
        }
        updateStatus(label)
    }

    private fun handleCfgChunk(s: String) {
        if (!cfgParser.push(s)) return
        val json = cfgParser.jsonOrNull() ?: return

        val cfg = try {
            EspSettings.fromJson(json)
        } catch (_: Throwable) {
            updateStatus("Config parse error")
            return
        }

        _settings.value = cfg
        updateStatus("Config updated")
    }


    private fun handleTeleChunk(s: String) {
        //Log.d("BLE_TEL", "chunk='$s'")

        if (!teleParser.push(s)) return
        val json = teleParser.jsonOrNull() ?: return

        //Log.d("BLE_TEL", "full json=$json")

        val t = try {
            Telemetry.fromJson(json)
        } catch (e: Throwable) {
            //Log.e("BLE_TEL", "parse error", e)
            updateStatus("Tele parse error")
            return
        }

        val prev = _telemetry.value
        _telemetry.value = t.copy(status = prev.status)
    }


    @SuppressLint("MissingPermission")
    private fun writeJsonChunked(json: String): Boolean {
        val ch = chCfgIn ?: return false
        val g = gatt ?: return false

        if (!hasConnPermission()) {
            updateStatus("No write permission")
            return false
        }



        return try {
            ch.value = "[BEGIN]".toByteArray()
            g.writeCharacteristic(ch)

            val payload = json
            val chunkSize = 80

            var i = 0
            while (i < payload.length) {
                val end = (i + chunkSize).coerceAtMost(payload.length)
                val part = payload.substring(i, end)
                ch.value = part.toByteArray()
                g.writeCharacteristic(ch)
                i = end
            }

            ch.value = "[END]".toByteArray()
            g.writeCharacteristic(ch)

            true
        } catch (_: Throwable) {
            updateStatus("Write failed")
            false
        }
    }

    private fun updateStatus(text: String) {
        scope.launch {
            val tPrev = _telemetry.value
            _status.value = text
            _telemetry.value = tPrev.copy(status = text)
        }
    }

    private fun decodeAscii(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            if (v in 32..126) {
                sb.append(v.toChar())
            }
        }
        return sb.toString().trim()
    }

}
