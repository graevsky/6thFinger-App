package com.example.a6thfingercontrolapp.ui.account

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.a6thfingercontrolapp.R
import com.example.a6thfingercontrolapp.ble.settings.EspSettings
import com.example.a6thfingercontrolapp.network.DeviceOut
import com.example.a6thfingercontrolapp.utils.uiErrorText
import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * User friendly status line for device.
 */
@Composable
internal fun cloudStatusText(
    choice: CloudDeviceChoice,
    state: CloudSettingsState?,
    includeVersion: Boolean = true
): String {
    val baseStatus = when {
        choice.device == null -> stringResource(R.string.prosthesis_local_device_not_registered)
        state?.record != null && includeVersion -> stringResource(
            R.string.prosthesis_server_settings_saved_version,
            state.record.version
        )

        state?.record != null -> stringResource(R.string.prosthesis_server_settings_saved)

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

internal fun formatCloudUpdatedAt(raw: String): String {
    if (raw.isBlank()) return raw

    val formatter = DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.MEDIUM)
        .withLocale(Locale.getDefault())

    return runCatching {
        Instant.parse(raw)
            .atZone(ZoneId.systemDefault())
            .format(formatter)
    }.recoverCatching {
        OffsetDateTime.parse(raw)
            .atZoneSameInstant(ZoneId.systemDefault())
            .format(formatter)
    }.getOrDefault(raw)
}

internal fun formatLocalUpdatedAt(updatedAtMillis: Long?): String {
    if (updatedAtMillis == null) return ""

    return Instant.ofEpochMilli(updatedAtMillis)
        .atZone(ZoneId.systemDefault())
        .format(
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                .withLocale(Locale.getDefault())
        )
}

@Composable
internal fun connectedServerStatusText(
    isLoggedIn: Boolean,
    matchedDevice: DeviceOut?,
    matchedState: CloudSettingsState?,
    settingsInSync: Boolean,
    isLoading: Boolean,
    hasServerError: Boolean
): String {
    return when {
        !isLoggedIn -> stringResource(R.string.prosthesis_server_status_sign_in)
        hasServerError -> stringResource(R.string.prosthesis_server_status_unknown)
        isLoading && matchedState == null -> stringResource(R.string.loading)
        matchedDevice == null -> stringResource(R.string.prosthesis_server_status_not_saved)
        matchedState?.record != null && settingsInSync ->
            stringResource(R.string.prosthesis_server_settings_saved)

        matchedState?.record != null -> stringResource(R.string.prosthesis_server_status_outdated)
        matchedState?.checked == true -> stringResource(R.string.prosthesis_server_status_device_only)
        !matchedState?.errorKey.isNullOrBlank() -> stringResource(R.string.prosthesis_server_status_unknown)
        else -> stringResource(R.string.loading)
    }
}

@Composable
internal fun currentDeviceVersionText(device: ConnectedDeviceSummary): String {
    return when {
        device.serverSettingsVersion != null && device.serverSettingsInSync ->
            device.serverSettingsVersion.toString()

        device.serverSettingsVersion != null ->
            stringResource(R.string.prosthesis_version_outdated)

        else -> stringResource(R.string.prosthesis_version_missing)
    }
}

@Composable
internal fun serverDeviceVersionText(
    state: CloudSettingsState?,
    isLoading: Boolean
): String {
    return when {
        state?.record != null -> state.record.version.toString()
        state?.checked == true -> stringResource(R.string.prosthesis_version_missing)
        !state?.errorKey.isNullOrBlank() -> stringResource(R.string.prosthesis_server_status_unknown)
        isLoading -> stringResource(R.string.loading)
        else -> stringResource(R.string.prosthesis_version_missing)
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
