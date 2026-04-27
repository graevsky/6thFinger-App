package com.example.a6thfingercontrolapp.ui.account

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.a6thfingercontrolapp.R
import com.example.a6thfingercontrolapp.ble.settings.EspSettings
import com.example.a6thfingercontrolapp.utils.uiErrorText
import org.json.JSONObject

/**
 * User friendly status line for device.
 */
@Composable
internal fun cloudStatusText(
    choice: CloudDeviceChoice,
    state: CloudSettingsState?
): String {
    val baseStatus = when {
        choice.device == null -> stringResource(R.string.prosthesis_local_device_not_registered)
        state?.record != null -> stringResource(
            R.string.prosthesis_server_settings_saved_version,
            state.record.version
        )

        state?.checked == true -> stringResource(R.string.prosthesis_server_settings_missing)
        !state?.errorKey.isNullOrBlank() -> stringResource(R.string.prosthesis_server_status_unknown)
        else -> stringResource(R.string.loading)
    }

    return if (choice.isConnectedDevice) {
        "${stringResource(R.string.prosthesis_connected_device)} - $baseStatus"
    } else {
        baseStatus
    }
}

@Composable
internal fun uiErrorTextOrRaw(raw: String?): String? {
    val normalized = raw?.trim()?.lowercase()?.replace("\n", "") ?: return null
    if (normalized.isBlank()) return null

    val unknown = stringResource(R.string.err_unknown)
    val mapped = when (normalized) {
        "prosthesis_no_settings_on_server" -> stringResource(R.string.prosthesis_no_settings_on_server)
        else -> uiErrorText(raw)
    }

    return if (mapped == unknown && !normalized.startsWith("http_")) raw else mapped
}

internal fun settingsToPrettyJson(s: EspSettings): String {
    return try {
        val obj = JSONObject(s.toJsonString())
        obj.put("pinSet", s.pinSet)
        obj.put("authRequired", s.authRequired)
        obj.toString(2)
    } catch (_: Exception) {
        s.toJsonString()
    }
}
