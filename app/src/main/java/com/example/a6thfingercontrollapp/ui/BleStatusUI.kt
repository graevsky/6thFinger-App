package com.example.a6thfingercontrollapp.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.a6thfingercontrollapp.R

/**
 * Converts normalized BLE status keys from the transport layer into localized
 * text displayed on the connection screen.
 */
@Composable
fun bleStatusUiText(statusKeyRaw: String): String {
    val key = statusKeyRaw.trim().lowercase()

    return when {
        key.isBlank() || key == "idle" -> stringResource(R.string.ble_status_idle)

        key == "disconnected" -> stringResource(R.string.disconnected)

        key == "connecting" -> stringResource(R.string.ble_connecting)
        key == "discovering" -> stringResource(R.string.ble_status_discovering)
        key == "scanning" -> stringResource(R.string.ble_status_scanning)

        key == "subscribed" -> stringResource(R.string.ble_connected)

        key == "bluetooth_off" -> stringResource(R.string.ble_status_bluetooth_off)
        key == "no_scan_permission" -> stringResource(R.string.ble_status_no_scan_permission)
        key == "scan_denied" -> stringResource(R.string.ble_status_scan_denied)
        key == "scan_error" -> stringResource(R.string.ble_status_scan_error)
        key == "scan_failed" -> stringResource(R.string.ble_status_scan_failed)

        key == "invalid_device" -> stringResource(R.string.ble_status_invalid_device)
        key == "connect_failed" -> stringResource(R.string.ble_status_connect_failed)
        key == "no_connect_permission" -> stringResource(R.string.ble_status_no_connect_permission)

        key == "service_discovery_failed" -> stringResource(R.string.ble_status_service_discovery_failed)
        key == "service_discovery_error" -> stringResource(R.string.ble_status_service_discovery_error)
        key == "service_not_found" -> stringResource(R.string.ble_status_service_not_found)
        key == "characteristics_missing" -> stringResource(R.string.ble_status_characteristics_missing)
        key == "notify_error" -> stringResource(R.string.ble_status_notify_error)

        key == "config_updated" -> stringResource(R.string.ble_status_config_updated)
        key == "config_parse_error" -> stringResource(R.string.ble_status_config_parse_error)
        key == "tele_parse_error" -> stringResource(R.string.ble_status_tele_parse_error)

        key == "auth_ok" -> stringResource(R.string.ble_status_auth_ok)
        key == "auth_fail" -> stringResource(R.string.ble_status_auth_fail)

        key == "ack_ok" -> stringResource(R.string.ble_status_ack_ok)
        key == "ack_fail" -> stringResource(R.string.ble_status_ack_fail)

        key == "write_failed" -> stringResource(R.string.ble_status_write_failed)

        else -> statusKeyRaw
    }
}
