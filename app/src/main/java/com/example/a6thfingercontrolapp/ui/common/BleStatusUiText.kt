package com.example.a6thfingercontrolapp.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.a6thfingercontrolapp.R
import com.example.a6thfingercontrolapp.ble.bleStatusKey

/**
 * BLE status keys converter for user friendly display.
 */
@Composable
fun bleStatusUiText(statusKeyRaw: String): String {
    val key = bleStatusKey(statusKeyRaw)

    return when (key) {
        "idle" -> stringResource(R.string.ble_status_idle)
        "disconnected" -> stringResource(R.string.disconnected)

        "connecting" -> stringResource(R.string.ble_connecting)
        "discovering" -> stringResource(R.string.ble_status_discovering)
        "scanning" -> stringResource(R.string.ble_status_scanning)

        "subscribed" -> stringResource(R.string.ble_connected)

        "bluetooth_off" -> stringResource(R.string.ble_status_bluetooth_off)
        "no_scan_permission" -> stringResource(R.string.ble_status_no_scan_permission)
        "scan_denied" -> stringResource(R.string.ble_status_scan_denied)
        "scan_error" -> stringResource(R.string.ble_status_scan_error)
        "scan_failed" -> stringResource(R.string.ble_status_scan_failed)

        "invalid_device" -> stringResource(R.string.ble_status_invalid_device)
        "connect_failed" -> stringResource(R.string.ble_status_connect_failed)
        "no_connect_permission" -> stringResource(R.string.ble_status_no_connect_permission)

        "service_discovery_failed" -> stringResource(R.string.ble_status_service_discovery_failed)
        "service_discovery_error" -> stringResource(R.string.ble_status_service_discovery_error)
        "service_not_found" -> stringResource(R.string.ble_status_service_not_found)
        "characteristics_missing" -> stringResource(R.string.ble_status_characteristics_missing)
        "notify_error" -> stringResource(R.string.ble_status_notify_error)

        "config_updated" -> stringResource(R.string.ble_status_config_updated)
        "config_parse_error" -> stringResource(R.string.ble_status_config_parse_error)
        "tele_parse_error" -> stringResource(R.string.ble_status_tele_parse_error)

        "auth_ok" -> stringResource(R.string.ble_status_auth_ok)
        "auth_fail" -> stringResource(R.string.ble_status_auth_fail)

        "ack_ok" -> stringResource(R.string.ble_status_ack_ok)
        "ack_fail" -> stringResource(R.string.ble_status_ack_fail)

        "write_failed",
        "telemetry_send_failed",
        "cfg_ok_failed",
        "live_write_failed",
        "live_stop_failed" -> stringResource(R.string.ble_status_write_failed)

        "telemetry_ack_timeout",
        "telemetry_ack_fail",
        "telemetry_enable_failed",
        "telemetry_disable_failed" -> stringResource(R.string.ble_status_ack_fail)

        else -> statusKeyRaw
    }
}
