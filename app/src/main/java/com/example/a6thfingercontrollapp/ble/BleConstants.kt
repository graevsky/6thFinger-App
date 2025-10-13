package com.example.a6thfingercontrollapp.ble

import java.util.UUID

object BleConstants {
    val SERVICE_UUID: UUID = UUID.fromString("6f1a0001-7e03-4a5a-9c5a-8b1f9c1a0001")
    val FLEX_UUID: UUID = UUID.fromString("6f1a0002-7e03-4a5a-9c5a-8b1f9c1a0002")
    val SERVO_UUID: UUID = UUID.fromString("6f1a0003-7e03-4a5a-9c5a-8b1f9c1a0003")
    val FSR_UUID: UUID = UUID.fromString("6f1a0004-7e03-4a5a-9c5a-8b1f9c1a0004")

    val CFG_SERVICE_UUID: UUID = UUID.fromString("6f1a1000-7e03-4a5a-9c5a-8b1f9c1a1000")

    val CFG_FSR_PIN: UUID = UUID.fromString("6f1a1001-7e03-4a5a-9c5a-8b1f9c1a1001")
    val CFG_FSR_PULLUP: UUID = UUID.fromString("6f1a1002-7e03-4a5a-9c5a-8b1f9c1a1002")
    val CFG_FSR_START: UUID = UUID.fromString("6f1a1003-7e03-4a5a-9c5a-8b1f9c1a1003")
    val CFG_FSR_MAX: UUID = UUID.fromString("6f1a1004-7e03-4a5a-9c5a-8b1f9c1a1004")

    val CFG_FLEX_PIN: UUID = UUID.fromString("6f1a1011-7e03-4a5a-9c5a-8b1f9c1a1011")
    val CFG_FLEX_FLAT: UUID = UUID.fromString("6f1a1012-7e03-4a5a-9c5a-8b1f9c1a1012")
    val CFG_FLEX_BEND: UUID = UUID.fromString("6f1a1013-7e03-4a5a-9c5a-8b1f9c1a1013")

    val CFG_VIBRO_PIN: UUID = UUID.fromString("6f1a1021-7e03-4a5a-9c5a-8b1f9c1a1021")
    val CFG_VIBRO_FREQ: UUID = UUID.fromString("6f1a1022-7e03-4a5a-9c5a-8b1f9c1a1022")
    val CFG_VIBRO_THRESH: UUID = UUID.fromString("6f1a1023-7e03-4a5a-9c5a-8b1f9c1a1023")
    val CFG_VIBRO_POWER: UUID = UUID.fromString("6f1a1024-7e03-4a5a-9c5a-8b1f9c1a1024")

    val CFG_SERVO_PIN: UUID = UUID.fromString("6f1a1031-7e03-4a5a-9c5a-8b1f9c1a1031")
    val CFG_SERVO_MIN: UUID = UUID.fromString("6f1a1032-7e03-4a5a-9c5a-8b1f9c1a1032")
    val CFG_SERVO_MAX: UUID = UUID.fromString("6f1a1033-7e03-4a5a-9c5a-8b1f9c1a1033")
    val CFG_SERVO_MANUAL: UUID = UUID.fromString("6f1a1034-7e03-4a5a-9c5a-8b1f9c1a1034")
    val CFG_SERVO_MANUAL_DEG: UUID = UUID.fromString("6f1a1035-7e03-4a5a-9c5a-8b1f9c1a1035")

    val CFG_APPLY: UUID = UUID.fromString("6f1a10ff-7e03-4a5a-9c5a-8b1f9c1a10ff")

    val CCC_DESCRIPTOR_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}
