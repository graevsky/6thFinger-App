package com.example.a6thfingercontrolapp.ble

import java.util.UUID

/**
 * BLE UUIDs used by the ESP32 custom GATT service.
 *
 * The protocol is split by purpose:
 * - CFG_IN: app -> board configuration commands
 * - CFG_OUT: board -> app configuration payload
 * - ACK: acknowledgements for commands
 * - TELE: telemetry stream
 * - SERVO_LIVE: low-latency live servo control channel
 */
object NewBleConstants {

    val SERVICE_UUID: UUID = UUID.fromString("6F1A0000-0000-4A4A-AA00-001122334400")

    val CFG_IN_UUID: UUID = UUID.fromString("6F1A0001-0000-4A4A-AA00-001122334400")

    val CFG_OUT_UUID: UUID = UUID.fromString("6F1A0002-0000-4A4A-AA00-001122334400")

    val ACK_UUID: UUID = UUID.fromString("6F1A0003-0000-4A4A-AA00-001122334400")

    val TELE_UUID: UUID = UUID.fromString("6F1A0004-0000-4A4A-AA00-001122334400")

    val SERVO_LIVE_UUID: UUID = UUID.fromString("6F1A0005-0000-4A4A-AA00-001122334400")

    val CCC_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}