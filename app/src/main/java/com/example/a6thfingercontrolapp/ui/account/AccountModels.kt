package com.example.a6thfingercontrolapp.ui.account

import com.example.a6thfingercontrolapp.auth.AuthCloudMapper
import com.example.a6thfingercontrolapp.ble.settings.EspSettings
import com.example.a6thfingercontrolapp.data.DeviceSettingsRecord
import com.example.a6thfingercontrolapp.network.DeviceOut
import java.time.Instant
import java.time.OffsetDateTime

internal const val CURRENT_DEVICE_DIALOG_KEY = "__current_device__"

/**
 * Dialog-step models shared by the account email management flow.
 */
internal enum class EmailDialogMode { None, Add, Remove, Change }

internal enum class AddStep { EnterEmail, EnterCode }

internal enum class RemoveStep { ChooseMethod, EnterEmailCode, EnterRecoveryCode }

internal enum class ChangeStep {
    ChooseOldMethod,
    EnterOldEmailCode,
    EnterOldRecoveryCode,
    EnterNewEmail,
    EnterNewEmailCode
}

/**
 * One selectable device entry used by the account cloud-settings UI.
 */
internal data class CloudDeviceChoice(
    val device: DeviceOut?,
    val address: String,
    val alias: String?,
    val isConnectedDevice: Boolean
) {
    val key: String = device?.id ?: "local:${address.lowercase()}"
    val title: String = alias?.takeIf { it.isNotBlank() } ?: address
}

internal data class CloudSettingsState(
    val checked: Boolean = false,
    val record: DeviceSettingsRecord? = null,
    val errorKey: String? = null
)

internal data class ConnectedDeviceSummary(
    val displayName: String,
    val cloudAlias: String?,
    val address: String,
    val updatedAtMillis: Long?,
    val serverSettingsVersion: Int?,
    val serverSettingsInSync: Boolean,
    val matchedServerDevice: DeviceOut?,
    val matchedServerState: CloudSettingsState?
)

internal fun normalizeDeviceAlias(alias: String?): String? =
    alias?.trim()?.takeIf { it.isNotEmpty() }

internal fun areSettingsEquivalent(
    currentSettings: EspSettings,
    serverSettings: EspSettings
): Boolean {
    return AuthCloudMapper.espToPayload(currentSettings) ==
            AuthCloudMapper.espToPayload(serverSettings)
}

internal fun findExactServerDevice(
    devices: List<DeviceOut>,
    address: String,
    alias: String?
): DeviceOut? {
    val normalizedAlias = normalizeDeviceAlias(alias)
    return devices
        .asSequence()
        .filter {
            it.address.equals(address, ignoreCase = true) &&
                    normalizeDeviceAlias(it.alias) == normalizedAlias
        }
        .maxByOrNull { parseDeviceCreatedAtMillis(it.created_at) ?: Long.MIN_VALUE }
}

private fun parseDeviceCreatedAtMillis(raw: String): Long? {
    if (raw.isBlank()) return null

    return runCatching { Instant.parse(raw).toEpochMilli() }
        .recoverCatching { OffsetDateTime.parse(raw).toInstant().toEpochMilli() }
        .getOrNull()
}
