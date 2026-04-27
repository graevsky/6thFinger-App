package com.example.a6thfingercontrolapp.ui.account

import com.example.a6thfingercontrolapp.data.DeviceSettingsRecord
import com.example.a6thfingercontrolapp.network.DeviceOut

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
