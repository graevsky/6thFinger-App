package com.example.a6thfingercontrolapp.ble.comms

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import com.example.a6thfingercontrolapp.ble.NewBleConstants

/**
 * Serialized CCC writes for telemetry, config, ack subscription controller.
 */
internal class BleNotifySubscriptionController(
    private val updateStatus: (String) -> Unit,
    private val onAllSubscribed: () -> Unit
) {
    private val queue = ArrayDeque<BluetoothGattCharacteristic>()

    fun reset() {
        queue.clear()
    }

    fun setTargets(
        tele: BluetoothGattCharacteristic,
        cfgOut: BluetoothGattCharacteristic,
        ack: BluetoothGattCharacteristic
    ) {
        queue.clear()
        queue.addLast(tele)
        queue.addLast(cfgOut)
        queue.addLast(ack)
    }

    @SuppressLint("MissingPermission")
    fun enableNext(gatt: BluetoothGatt) {
        val characteristic = queue.firstOrNull() ?: return
        enableNotifyOrIndicate(gatt, characteristic)
    }

    @SuppressLint("MissingPermission")
    fun onDescriptorWrite(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor
    ) {
        if (descriptor.uuid != NewBleConstants.CCC_UUID) return

        if (queue.isNotEmpty()) queue.removeFirst()
        if (queue.isNotEmpty()) {
            enableNext(gatt)
        } else {
            updateStatus("Subscribed (tele/cfg/ack)")
            onAllSubscribed()
        }
    }

    /**
     * Notifications or indications controller depending on the channel type.
     */
    @SuppressLint("MissingPermission")
    private fun enableNotifyOrIndicate(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ) {
        try {
            gatt.setCharacteristicNotification(characteristic, true)
            val ccc = characteristic.getDescriptor(NewBleConstants.CCC_UUID)

            if (ccc != null) {
                val isIndicate =
                    characteristic.uuid == NewBleConstants.CFG_OUT_UUID ||
                            characteristic.uuid == NewBleConstants.ACK_UUID

                ccc.value = if (isIndicate) {
                    BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                } else {
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                }

                val ok = gatt.writeDescriptor(ccc)
                if (!ok) {
                    if (queue.isNotEmpty() && queue.first() == characteristic) queue.removeFirst()
                    enableNext(gatt)
                }
            } else {
                if (queue.isNotEmpty() && queue.first() == characteristic) queue.removeFirst()
                enableNext(gatt)
            }
        } catch (_: Throwable) {
            updateStatus("Notify error")
        }
    }
}