package com.example.a6thfingercontrollapp.ble

import java.util.UUID

object BleConstants {
    const val DEVICE_NAME = "ESP32-Flex6"

    val SERVICE_UUID: UUID = UUID.fromString("6f1a0001-7e03-4a5a-9c5a-8b1f9c1a0001")
    val FLEX_UUID: UUID = UUID.fromString("6f1a0002-7e03-4a5a-9c5a-8b1f9c1a0002")
    val SERVO_UUID: UUID = UUID.fromString("6f1a0003-7e03-4a5a-9c5a-8b1f9c1a0003")

    val CCC_DESCRIPTOR_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}
