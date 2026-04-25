package com.example.a6thfingercontrolapp.ble

/**
 * Normalized BLE connection/control interpretation shared by UI screens.
 */
data class BleConnectionStatus(
    val key: String,
    val transportConnected: Boolean,
    val controlUnlocked: Boolean,
    val controlReady: Boolean,
    val scanning: Boolean,
    val connecting: Boolean,
    val disconnected: Boolean
)

/** Converts raw/debug BLE text into a stable lowercase key. */
fun bleStatusKey(raw: String): String {
    val t = raw.trim().lowercase()
    if (t.isBlank()) return "idle"

    return when {
        t.startsWith("disconnected") -> "disconnected"
        t.startsWith("connecting") -> "connecting"
        t.startsWith("discovering") -> "discovering"
        t.startsWith("scanning") -> "scanning"
        t.startsWith("bluetooth off") -> "bluetooth_off"
        t.startsWith("no scan permission") -> "no_scan_permission"
        t.startsWith("scan denied") -> "scan_denied"
        t.startsWith("scan error") -> "scan_error"
        t.startsWith("scan failed") -> "scan_failed"
        t.startsWith("invalid device") -> "invalid_device"
        t.startsWith("connect failed") -> "connect_failed"
        t.startsWith("no connect permission") -> "no_connect_permission"
        t.startsWith("service discovery failed") -> "service_discovery_failed"
        t.startsWith("service discovery error") -> "service_discovery_error"
        t.startsWith("service not found") -> "service_not_found"
        t.startsWith("characteristics missing") -> "characteristics_missing"
        t.startsWith("notify error") -> "notify_error"
        t.startsWith("subscribed") -> "subscribed"
        t.startsWith("auth ok") -> "auth_ok"
        t.startsWith("auth fail") -> "auth_fail"
        t.startsWith("config updated") -> "config_updated"
        t.startsWith("config parse error") -> "config_parse_error"
        t.startsWith("tele parse error") -> "tele_parse_error"
        t.startsWith("ack ok") -> "ack_ok"
        t.startsWith("ack fail") -> "ack_fail"
        t.startsWith("telemetry ack timeout") -> "telemetry_ack_timeout"
        t.startsWith("telemetry ack fail") -> "telemetry_ack_fail"
        t.startsWith("telemetry send failed") -> "telemetry_send_failed"
        t.startsWith("telemetry enable failed") -> "telemetry_enable_failed"
        t.startsWith("telemetry disable failed") -> "telemetry_disable_failed"
        t.startsWith("cfg ok failed") -> "cfg_ok_failed"
        t.startsWith("live write failed") -> "live_write_failed"
        t.startsWith("live stop failed") -> "live_stop_failed"
        t.startsWith("write") -> "write_failed"
        else -> t.replace(Regex("[^a-z0-9]+"), "_").trim('_').ifBlank { "idle" }
    }
}

private val transportConnectedKeys = setOf(
    "subscribed",
    "config_updated",
    "config_parse_error",
    "tele_parse_error",
    "auth_ok",
    "auth_fail",
    "ack_ok",
    "ack_fail",
    "tele",
    "telemetry",
    "telemetry_ack_timeout",
    "telemetry_ack_fail",
    "telemetry_send_failed",
    "telemetry_enable_failed",
    "telemetry_disable_failed",
    "cfg_ok_failed",
    "write_failed",
    "live_write_failed",
    "live_stop_failed"
)

private val disconnectedKeys = setOf(
    "idle",
    "disconnected",
    "bluetooth_off",
    "no_scan_permission",
    "scan_denied",
    "scan_error",
    "scan_failed",
    "invalid_device",
    "connect_failed",
    "no_connect_permission",
    "service_discovery_failed",
    "service_discovery_error",
    "service_not_found",
    "characteristics_missing",
    "notify_error"
)

/** True when a GATT session is established or likely still established. */
fun isBleTransportConnected(statusKeyRaw: String): Boolean {
    val key = bleStatusKey(statusKeyRaw)
    return key in transportConnectedKeys || key.startsWith("tele_")
}

/** True only when the device is connected and the PIN/config gate is unlocked. */
fun isBleControlReady(statusKeyRaw: String, controlUnlocked: Boolean): Boolean {
    val key = bleStatusKey(statusKeyRaw)
    return isBleTransportConnected(key) && controlUnlocked && key != "auth_fail"
}

/** True while Android is in scan/connect/service-discovery stage. */
fun isBleConnecting(statusKeyRaw: String): Boolean {
    val key = bleStatusKey(statusKeyRaw)
    return key == "connecting" || key == "discovering"
}

/** Full shared classification used by Compose screens. */
fun classifyBleStatus(
    statusKeyRaw: String,
    controlUnlocked: Boolean = false
): BleConnectionStatus {
    val key = bleStatusKey(statusKeyRaw)
    val transportConnected = isBleTransportConnected(key)
    val scanning = key == "scanning"
    val connecting = isBleConnecting(key)
    val disconnected = key in disconnectedKeys

    return BleConnectionStatus(
        key = key,
        transportConnected = transportConnected,
        controlUnlocked = controlUnlocked,
        controlReady = transportConnected && controlUnlocked && key != "auth_fail",
        scanning = scanning,
        connecting = connecting,
        disconnected = disconnected
    )
}
