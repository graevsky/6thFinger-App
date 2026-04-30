package com.example.a6thfingercontrolapp.data.repositories

import android.content.Context
import com.example.a6thfingercontrolapp.BuildConfig
import com.example.a6thfingercontrolapp.auth.AuthAccountService
import com.example.a6thfingercontrolapp.auth.AuthCloudService
import com.example.a6thfingercontrolapp.auth.AuthCredentialsService
import com.example.a6thfingercontrolapp.auth.AuthPasswordResetService
import com.example.a6thfingercontrolapp.auth.AuthSessionGateway
import com.example.a6thfingercontrolapp.ble.settings.EspSettings
import com.example.a6thfingercontrolapp.data.AuthState
import com.example.a6thfingercontrolapp.data.AuthStore
import com.example.a6thfingercontrolapp.data.DeviceSettingsRecord
import com.example.a6thfingercontrolapp.network.BackendApi
import com.example.a6thfingercontrolapp.network.DeviceOut
import com.example.a6thfingercontrolapp.network.PasswordResetStartOut
import com.example.a6thfingercontrolapp.security.ClientAttestationManager
import kotlinx.coroutines.sync.Mutex

/**
 * App wide auth, account and cloud sync repo.
 */
class AuthRepository private constructor(context: Context) {
    companion object {
        @Volatile
        private var instance: AuthRepository? = null

        fun get(context: Context): AuthRepository {
            return instance ?: synchronized(this) {
                instance ?: AuthRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    private val api = BackendApi.create(context.applicationContext)
    private val store = AuthStore(context.applicationContext)
    private val appContext = context.applicationContext
    private val clientAttestationManager = ClientAttestationManager(appContext)
    private val tokenRefreshMutex = Mutex()

    val stored = store.authState

    private suspend fun ensureClientSessionIfRequired() {
        if (BuildConfig.CLIENT_ATTESTATION_REQUIRED) {
            clientAttestationManager.ensureClientSession()
        }
    }

    private val sessionGateway = AuthSessionGateway(
        api = api,
        store = store,
        stored = stored,
        tokenRefreshMutex = tokenRefreshMutex,
        ensureClientSession = ::ensureClientSessionIfRequired
    )
    private val credentialsService = AuthCredentialsService(
        api = api,
        sessionGateway = sessionGateway,
        ensureClientSession = ::ensureClientSessionIfRequired
    )
    private val passwordResetService = AuthPasswordResetService(
        api = api,
        ensureClientSession = ::ensureClientSessionIfRequired
    )
    private val cloudService = AuthCloudService(api, sessionGateway)
    private val accountService = AuthAccountService(api, sessionGateway, appContext)

    suspend fun resolveInitialState(): AuthState =
        sessionGateway.resolveInitialState()

    suspend fun continueAsGuest(): AuthState =
        sessionGateway.continueAsGuest()

    suspend fun register(username: String, password: String): List<String> =
        credentialsService.register(username, password)

    suspend fun login(username: String, password: String): AuthState =
        credentialsService.login(username, password)

    suspend fun logout(): AuthState =
        credentialsService.logout()

    suspend fun pullAppSettings(): Map<String, Any?>? =
        cloudService.pullAppSettings()

    suspend fun pushAppSettings(payload: Map<String, Any?>) =
        cloudService.pushAppSettings(payload)

    suspend fun listDevices(): List<DeviceOut> =
        cloudService.listDevices()

    suspend fun pushDeviceSettings(
        deviceId: String,
        settings: EspSettings
    ): DeviceSettingsRecord =
        cloudService.pushDeviceSettings(deviceId, settings)

    suspend fun getDeviceSettingsRecord(deviceId: String): DeviceSettingsRecord? =
        cloudService.getDeviceSettingsRecord(deviceId)

    suspend fun ensureDevice(address: String, alias: String?): DeviceOut =
        cloudService.ensureDevice(address, alias)

    suspend fun uploadAvatar(localPath: String) =
        accountService.uploadAvatar(localPath)

    suspend fun downloadAvatarToLocal(): String? =
        accountService.downloadAvatarToLocal()

    suspend fun deleteAvatarRemote() =
        accountService.deleteAvatarRemote()

    suspend fun emailStartAdd(email: String) =
        accountService.emailStartAdd(email)

    suspend fun emailConfirmAdd(email: String, code: String) =
        accountService.emailConfirmAdd(email, code)

    suspend fun emailStartRemove() =
        accountService.emailStartRemove()

    suspend fun emailConfirmRemove(code: String?, recoveryCode: String?) =
        accountService.emailConfirmRemove(code, recoveryCode)

    suspend fun passwordResetStart(username: String): PasswordResetStartOut =
        passwordResetService.passwordResetStart(username)

    suspend fun passwordResetEmailSend(username: String, email: String) =
        passwordResetService.passwordResetEmailSend(username, email)

    suspend fun passwordResetEmailVerify(username: String, email: String, code: String): String =
        passwordResetService.passwordResetEmailVerify(username, email, code)

    suspend fun passwordResetRecoveryVerify(username: String, recoveryCode: String): String =
        passwordResetService.passwordResetRecoveryVerify(username, recoveryCode)

    suspend fun passwordResetFinish(
        resetSessionId: String,
        username: String,
        newPassword: String
    ) = passwordResetService.passwordResetFinish(resetSessionId, username, newPassword)
}
