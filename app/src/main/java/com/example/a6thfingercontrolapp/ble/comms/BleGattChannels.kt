package com.example.a6thfingercontrolapp.ble.comms

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import com.example.a6thfingercontrolapp.ble.NewBleConstants

/**
 * GATT characteristics for one connected prosthesis session.
 */
internal data class BleGattChannels(
    val cfgIn: BluetoothGattCharacteristic,
    val cfgOut: BluetoothGattCharacteristic,
    val ack: BluetoothGattCharacteristic,
    val tele: BluetoothGattCharacteristic,
    val servoLive: BluetoothGattCharacteristic
)

internal fun resolveBleGattChannels(service: BluetoothGattService): BleGattChannels? {
    val cfgIn = service.getCharacteristic(NewBleConstants.CFG_IN_UUID) ?: return null
    val cfgOut = service.getCharacteristic(NewBleConstants.CFG_OUT_UUID) ?: return null
    val ack = service.getCharacteristic(NewBleConstants.ACK_UUID) ?: return null
    val tele = service.getCharacteristic(NewBleConstants.TELE_UUID) ?: return null
    val servoLive = service.getCharacteristic(NewBleConstants.SERVO_LIVE_UUID) ?: return null

    return BleGattChannels(
        cfgIn = cfgIn,
        cfgOut = cfgOut,
        ack = ack,
        tele = tele,
        servoLive = servoLive
    )
}
