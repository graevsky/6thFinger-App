package com.example.a6thfingercontrolapp.ble.comms

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic

/**
 * Sends JSON in small BLE-friendly fragments.
 *
 * Protocol:
 * [BEGIN]
 * chunk1
 * chunk2
 * ...
 * [END]
 */
@SuppressLint("MissingPermission")
internal fun writeBleJsonChunked(
    json: String,
    gatt: BluetoothGatt?,
    characteristic: BluetoothGattCharacteristic?,
    hasConnPermission: () -> Boolean,
    updateStatus: (String) -> Unit,
    writeTracker: BleWriteTracker
): Boolean {
    val targetCharacteristic = characteristic ?: run {
        updateStatus("No CFG_IN")
        return false
    }
    val targetGatt = gatt ?: run {
        updateStatus("No GATT")
        return false
    }

    if (!hasConnPermission()) {
        updateStatus("No write permission")
        return false
    }

    targetCharacteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

    fun safeWrite(chunk: String): Boolean {
        writeTracker.onWriteStarted(targetCharacteristic.uuid)
        targetCharacteristic.value = chunk.toByteArray()

        val started = targetGatt.writeCharacteristic(targetCharacteristic)
        if (!started) {
            writeTracker.onWriteStartFailed()
            return false
        }

        var waitedMs = 0
        while (writeTracker.writeInProgress && waitedMs < 6000) {
            try {
                Thread.sleep(20)
            } catch (_: InterruptedException) {
            }
            waitedMs += 20
        }

        val ok = !writeTracker.writeInProgress && writeTracker.lastWriteOk
        if (!ok) writeTracker.pendingWriteUuid = null
        return ok
    }

    return try {
        val chunkSize = 18

        if (!safeWrite("[BEGIN]")) {
            updateStatus("Write BEGIN failed")
            return false
        }

        var index = 0
        while (index < json.length) {
            val end = (index + chunkSize).coerceAtMost(json.length)
            val part = json.substring(index, end)
            if (!safeWrite(part)) {
                updateStatus("Write chunk failed")
                return false
            }
            index = end
        }

        if (!safeWrite("[END]")) {
            updateStatus("Write END failed")
            return false
        }

        true
    } catch (_: Throwable) {
        updateStatus("Write failed")
        false
    }
}
