package com.example.a6thfingercontrolapp.ui.control

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.a6thfingercontrolapp.R

/** Maps live control and telemetry operation error keys to localized text. */
@Composable
fun liveControlErrorUiText(key: String?): String? {
    val normalized = key?.trim()?.lowercase() ?: return null
    if (normalized.isBlank()) return null

    return when (normalized) {
        "live_not_connected" -> stringResource(R.string.live_control_connect_required)
        "live_control_locked" -> stringResource(R.string.live_control_unlock_required)
        "live_control_active" -> stringResource(R.string.err_live_control_active)
        "live_telemetry_disable_failed" -> stringResource(R.string.err_live_telemetry_disable_failed)
        "live_telemetry_restore_failed" -> stringResource(R.string.err_live_telemetry_restore_failed)
        "live_write_failed" -> stringResource(R.string.err_live_write_failed)
        "live_stop_failed" -> stringResource(R.string.err_live_stop_failed)
        "telemetry_enable_failed" -> stringResource(R.string.err_telemetry_enable_failed)
        "telemetry_disable_failed" -> stringResource(R.string.err_telemetry_disable_failed)
        else -> key
    }
}
