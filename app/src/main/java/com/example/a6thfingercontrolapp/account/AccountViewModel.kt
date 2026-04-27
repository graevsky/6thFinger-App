package com.example.a6thfingercontrolapp.account

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.a6thfingercontrolapp.ble.settings.EspSettings
import com.example.a6thfingercontrolapp.data.repositories.AccountSyncRepository
import com.example.a6thfingercontrolapp.data.DeviceSettingsRecord
import com.example.a6thfingercontrolapp.network.DeviceOut

/**
 * Account view model for sync.
 */
class AccountViewModel(app: Application) : AndroidViewModel(app) {
    private val accountSync = AccountSyncRepository.get(app)

    fun scheduleAvatarUpload(localPath: String) {
        accountSync.scheduleAvatarUpload(localPath)
    }

    fun deleteAvatarRemote() {
        accountSync.deleteAvatarRemote()
    }

    suspend fun fetchDevices(): List<DeviceOut> =
        accountSync.fetchDevices()

    suspend fun fetchDeviceSettingsRecord(deviceId: String): DeviceSettingsRecord? =
        accountSync.fetchDeviceSettingsRecord(deviceId)

    suspend fun pushDeviceSettings(
        deviceId: String,
        settings: EspSettings
    ): DeviceSettingsRecord = accountSync.pushDeviceSettings(deviceId, settings)

    suspend fun ensureDevice(address: String, alias: String?): DeviceOut =
        accountSync.ensureDevice(address, alias)

    fun updateLanguageRemote(newLang: String) {
        accountSync.updateLanguageRemote(newLang)
    }
}
