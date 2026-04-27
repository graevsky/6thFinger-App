package com.example.a6thfingercontrolapp.ble.comms

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic

/**
 * Low latency live servo commands sender.
 * Dedicated characteristic is used here.
 */
@SuppressLint("MissingPermission")
internal fun writeBleLiveCommand(
    gatt: BluetoothGatt?,
    characteristic: BluetoothGattCharacteristic?,
    hasConnPermission: () -> Boolean,
    updateStatus: (String) -> Unit,
    payload: String,
    failureStatus: String,
    missingGattStatus: String? = null,
    missingCharacteristicStatus: String? = null
): Boolean {
    val targetGatt = gatt ?: run {
        if (missingGattStatus != null) updateStatus(missingGattStatus)
        return false
    }
    val targetCharacteristic = characteristic ?: run {
        if (missingCharacteristicStatus != null) updateStatus(missingCharacteristicStatus)
        return false
    }
    if (!hasConnPermission()) return false

    val props = targetCharacteristic.properties
    val supportsNoResponse =
        (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0

    targetCharacteristic.writeType =
        if (supportsNoResponse) BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        else BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

    targetCharacteristic.value = payload.toByteArray()
    val ok = targetGatt.writeCharacteristic(targetCharacteristic)
    if (!ok) updateStatus(failureStatus)
    return ok
}
