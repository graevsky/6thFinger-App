package com.example.a6thfingercontrolapp.ble.comms

import android.bluetooth.BluetoothGatt
import java.util.UUID

/**
 * Tracks one in-flight BLE characteristic write.
 */
internal class BleWriteTracker {
    @Volatile
    var writeInProgress: Boolean = false

    @Volatile
    var lastWriteOk: Boolean = true

    @Volatile
    var pendingWriteUuid: UUID? = null

    fun reset() {
        writeInProgress = false
        lastWriteOk = true
        pendingWriteUuid = null
    }

    fun onWriteStarted(uuid: UUID) {
        writeInProgress = true
        lastWriteOk = true
        pendingWriteUuid = uuid
    }

    fun onWriteStartFailed() {
        pendingWriteUuid = null
        writeInProgress = false
    }

    fun onWriteFinished(characteristicUuid: UUID, status: Int) {
        val pending = pendingWriteUuid
        if (!writeInProgress || pending == null || characteristicUuid != pending) {
            return
        }

        lastWriteOk = (status == BluetoothGatt.GATT_SUCCESS)
        writeInProgress = false
        pendingWriteUuid = null
    }
}