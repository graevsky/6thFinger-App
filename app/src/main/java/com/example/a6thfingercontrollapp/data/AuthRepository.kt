package com.example.a6thfingercontrollapp.data

import android.content.Context
import com.example.a6thfingercontrollapp.ble.EspSettings
import com.example.a6thfingercontrollapp.ble.FlexSettings
import com.example.a6thfingercontrollapp.ble.ServoSettings
import com.example.a6thfingercontrollapp.network.AppSettingsIn
import com.example.a6thfingercontrollapp.network.BackendApi
import com.example.a6thfingercontrollapp.network.DeviceCreate
import com.example.a6thfingercontrollapp.network.DeviceOut
import com.example.a6thfingercontrollapp.network.DeviceSettingsIn
import com.example.a6thfingercontrollapp.network.DeviceUpdate
import com.example.a6thfingercontrollapp.network.EmailConfirmIn
import com.example.a6thfingercontrollapp.network.EmailRemoveConfirmIn
import com.example.a6thfingercontrollapp.network.EmailStartAddIn
import com.example.a6thfingercontrollapp.network.LoginFinishIn
import com.example.a6thfingercontrollapp.network.LoginStartIn
import com.example.a6thfingercontrollapp.network.PasswordResetEmailSendIn
import com.example.a6thfingercontrollapp.network.PasswordResetEmailVerifyIn
import com.example.a6thfingercontrollapp.network.PasswordResetFinishIn
import com.example.a6thfingercontrollapp.network.PasswordResetRecoveryVerifyIn
import com.example.a6thfingercontrollapp.network.PasswordResetStartIn
import com.example.a6thfingercontrollapp.network.PasswordResetStartOut
import com.example.a6thfingercontrollapp.network.RegisterIn
import com.example.a6thfingercontrollapp.security.srp.SrpLogin
import com.example.a6thfingercontrollapp.security.srp.SrpRegister
import com.example.a6thfingercontrollapp.utils.parseBackendError
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File

sealed class AuthState {
    object Unauthenticated : AuthState()
    object Guest : AuthState()
    data class LoggedIn(val username: String, val accessToken: String, val refreshToken: String) :
        AuthState()
}

class AuthRepository(context: Context) {

    private val api = BackendApi.create()
    private val store = AuthStore(context.applicationContext)
    private val appContext = context.applicationContext

    val stored = store.authState

    suspend fun resolveInitialState(): AuthState {
        val s = stored.first()
        return when {
            s.isGuest -> AuthState.Guest
            s.accessToken != null && s.username != null && s.refreshToken != null ->
                AuthState.LoggedIn(s.username, s.accessToken, s.refreshToken)

            else -> AuthState.Unauthenticated
        }
    }

    suspend fun continueAsGuest(): AuthState {
        store.setGuest()
        return AuthState.Guest
    }

    suspend fun register(username: String, password: String): List<String> {
        try {
            val normalized = username.trim().lowercase()

            val params = api.getSrpParams()
            val Nhex = params.N.replace("\\s+".toRegex(), "")
            val ghex = params.g.trim()

            val reg =
                SrpRegister.generateVerifier(
                    username = normalized,
                    password = password,
                    primeHex = Nhex,
                    generatorHex = ghex
                )

            val out = api.register(
                RegisterIn(
                    username = normalized,
                    salt = reg.saltHex,
                    verifier = reg.verifierHex
                )
            )

            return out.recovery_codes
        } catch (e: Exception) {
            throw Exception(parseBackendError(e))
        }
    }

    suspend fun login(username: String, password: String): AuthState {
        try {
            val normalized = username.trim().lowercase()

            val start = api.loginStart(LoginStartIn(normalized))

            val Nhex = start.N.replace("\\s+".toRegex(), "")
            val ghex = start.g.trim()

            val loginRes =
                SrpLogin.clientLogin(
                    username = normalized,
                    password = password,
                    saltHex = start.salt,
                    BHex = start.B,
                    primeHex = Nhex,
                    generatorHex = ghex
                )

            val finish =
                api.loginFinish(
                    LoginFinishIn(
                        username = normalized,
                        A = loginRes.A,
                        M1 = loginRes.M1,
                        salt = start.salt
                    )
                )

            store.saveTokens(normalized, finish.access_token, finish.refresh_token)
            return AuthState.LoggedIn(normalized, finish.access_token, finish.refresh_token)
        } catch (e: Exception) {
            throw Exception(parseBackendError(e))
        }
    }

    suspend fun logout(): AuthState {
        store.logout()
        return AuthState.Unauthenticated
    }

    suspend fun pullAppSettings(): Map<String, Any?>? {
        val s = stored.first()
        val token = s.accessToken ?: return null
        val res = api.getAppSettings("Bearer $token")
        return res.payload
    }

    suspend fun pushAppSettings(payload: Map<String, Any?>) {
        val s = stored.first()
        val token = s.accessToken ?: return
        api.putAppSettings("Bearer $token", AppSettingsIn(payload))
    }

    suspend fun listDevices(): List<DeviceOut> {
        val s = stored.first()
        val token = s.accessToken ?: throw Exception("Not authenticated")
        return try {
            api.listDevices("Bearer $token")
        } catch (e: Exception) {
            throw Exception(parseBackendError(e))
        }
    }

    suspend fun pushDeviceSettings(deviceId: String, settings: EspSettings) {
        val s = stored.first()
        val token = s.accessToken ?: throw Exception("Not authenticated")

        val payload = espToPayload(settings)

        try {
            api.postDeviceSettings(
                auth = "Bearer $token",
                deviceId = deviceId,
                body = DeviceSettingsIn(payload = payload)
            )
        } catch (e: Exception) {
            throw Exception(parseBackendError(e))
        }
    }

    suspend fun pullDeviceSettings(deviceId: String): EspSettings? {
        val s = stored.first()
        val token = s.accessToken ?: throw Exception("Not authenticated")

        return try {
            val res = api.getDeviceSettings(auth = "Bearer $token", deviceId = deviceId)
            payloadToEsp(res.payload)
        } catch (e: Exception) {
            throw Exception(parseBackendError(e))
        }
    }

    suspend fun uploadAvatar(localPath: String) {
        val s = stored.first()
        val token = s.accessToken ?: throw Exception("Not authenticated")

        val f = File(localPath)
        if (!f.exists()) throw Exception("Avatar file not found")

        val mediaType = "image/jpeg".toMediaType()
        val body = f.asRequestBody(mediaType)
        val part = MultipartBody.Part.createFormData("file", f.name, body)

        try {
            api.uploadAvatar("Bearer $token", part)
        } catch (e: Exception) {
            throw Exception(parseBackendError(e))
        }
    }

    suspend fun downloadAvatarToLocal(): String? {
        val s = stored.first()
        val token = s.accessToken ?: throw Exception("Not authenticated")

        val resp = try {
            api.downloadAvatar("Bearer $token")
        } catch (e: Exception) {
            throw Exception(parseBackendError(e))
        }

        if (resp.code() == 404) return null
        if (!resp.isSuccessful) {
            throw Exception("Avatar download failed (${resp.code()})")
        }

        val bytes = resp.body()?.bytes() ?: return null

        val outFile = avatarFile(appContext)
        outFile.parentFile?.mkdirs()
        outFile.writeBytes(bytes)

        return outFile.absolutePath
    }

    suspend fun deleteAvatarRemote() {
        val s = stored.first()
        val token = s.accessToken ?: throw Exception("Not authenticated")
        try {
            api.deleteAvatar("Bearer $token")
        } catch (e: Exception) {
            throw Exception(parseBackendError(e))
        }
    }

    suspend fun emailStartAdd(email: String) {
        val s = stored.first()
        val token = s.accessToken ?: throw Exception("Not authenticated")
        try {
            api.emailStartAdd("Bearer $token", EmailStartAddIn(email.trim()))
        } catch (e: Exception) {
            throw Exception(parseBackendError(e))
        }
    }

    suspend fun emailConfirmAdd(email: String, code: String) {
        val s = stored.first()
        val token = s.accessToken ?: throw Exception("Not authenticated")
        try {
            api.emailConfirmAdd("Bearer $token", EmailConfirmIn(email.trim(), code.trim()))
        } catch (e: Exception) {
            throw Exception(parseBackendError(e))
        }
    }

    suspend fun emailStartRemove() {
        val s = stored.first()
        val token = s.accessToken ?: throw Exception("Not authenticated")
        try {
            api.emailStartRemove("Bearer $token")
        } catch (e: Exception) {
            throw Exception(parseBackendError(e))
        }
    }

    suspend fun emailConfirmRemove(code: String?, recoveryCode: String?) {
        val s = stored.first()
        val token = s.accessToken ?: throw Exception("Not authenticated")
        try {
            api.emailConfirmRemove(
                "Bearer $token",
                EmailRemoveConfirmIn(
                    code = code?.trim()?.takeIf { it.isNotBlank() },
                    recovery_code = recoveryCode?.trim()?.takeIf { it.isNotBlank() }
                )
            )
        } catch (e: Exception) {
            throw Exception(parseBackendError(e))
        }
    }

    suspend fun passwordResetStart(username: String): PasswordResetStartOut {
        try {
            return api.passwordResetStart(PasswordResetStartIn(username.trim().lowercase()))
        } catch (e: Exception) {
            throw Exception(parseBackendError(e))
        }
    }

    suspend fun passwordResetEmailSend(username: String, email: String) {
        try {
            api.passwordResetEmailSend(
                PasswordResetEmailSendIn(
                    username = username.trim().lowercase(),
                    email = email.trim().lowercase()
                )
            )
        } catch (e: Exception) {
            throw Exception(parseBackendError(e))
        }
    }

    suspend fun passwordResetEmailVerify(username: String, email: String, code: String): String {
        try {
            val out = api.passwordResetEmailVerify(
                PasswordResetEmailVerifyIn(
                    username = username.trim().lowercase(),
                    email = email.trim().lowercase(),
                    code = code.trim()
                )
            )
            return out.reset_session_id
        } catch (e: Exception) {
            throw Exception(parseBackendError(e))
        }
    }

    suspend fun passwordResetRecoveryVerify(username: String, recoveryCode: String): String {
        try {
            val out = api.passwordResetRecoveryVerify(
                PasswordResetRecoveryVerifyIn(
                    username = username.trim().lowercase(),
                    recovery_code = recoveryCode.trim()
                )
            )
            return out.reset_session_id
        } catch (e: Exception) {
            throw Exception(parseBackendError(e))
        }
    }

    suspend fun passwordResetFinish(resetSessionId: String, username: String, newPassword: String) {
        try {
            val params = api.getSrpParams()
            val Nhex = params.N.replace("\\s+".toRegex(), "")
            val ghex = params.g.trim()

            val reg =
                SrpRegister.generateVerifier(
                    username = username.trim().lowercase(),
                    password = newPassword,
                    primeHex = Nhex,
                    generatorHex = ghex
                )

            api.passwordResetFinish(
                PasswordResetFinishIn(
                    reset_session_id = resetSessionId,
                    new_salt = reg.saltHex,
                    new_verifier = reg.verifierHex
                )
            )
        } catch (e: Exception) {
            throw Exception(parseBackendError(e))
        }
    }

    private fun flexToMap(f: FlexSettings): Map<String, Any?> = mapOf(
        "flexPin" to f.flexPin,
        "flexPullupOhm" to f.flexPullupOhm,
        "flexStraightOhm" to f.flexStraightOhm,
        "flexBendOhm" to f.flexBendOhm
    )

    private fun servoToMap(s: ServoSettings): Map<String, Any?> = mapOf(
        "servoPin" to s.servoPin,
        "servoMinDeg" to s.servoMinDeg,
        "servoMaxDeg" to s.servoMaxDeg,
        "servoManual" to s.servoManual,
        "servoManualDeg" to s.servoManualDeg,
        "servoMaxSpeedDegPerSec" to s.servoMaxSpeedDegPerSec.toDouble()
    )

    private fun espToPayload(settings: EspSettings): Map<String, Any?> {
        return mapOf(
            "fsrPin" to settings.fsrPin,
            "fsrPullupOhm" to settings.fsrPullupOhm,
            "fsrSoftThresholdN" to settings.fsrSoftThresholdN.toDouble(),
            "fsrHardMaxN" to settings.fsrHardMaxN.toDouble(),

            "flexSettings" to settings.flexSettings.map { flexToMap(it) },
            "servoSettings" to settings.servoSettings.map { servoToMap(it) },

            "vibroPin" to settings.vibroPin,
            "vibroMode" to settings.vibroMode,
            "vibroFreqHz" to settings.vibroFreqHz,
            "vibroMaxDuty" to settings.vibroMaxDuty,
            "vibroMinDuty" to settings.vibroMinDuty,
            "vibroSoftPower" to settings.vibroSoftPower,
            "vibroPulseBase" to settings.vibroPulseBase,

            "pinCode" to settings.pinCode,

            "pinSet" to settings.pinSet,
            "authRequired" to settings.authRequired,

            "settingsVersion" to settings.settingsVersion
        )
    }

    private fun payloadToEsp(payload: Map<String, Any?>): EspSettings? {
        return try {
            val json = JSONObject(payload)
            EspSettings.fromJson(json)
        } catch (_: Throwable) {
            null
        }
    }

    suspend fun ensureDevice(address: String, alias: String?): DeviceOut {
        val s = stored.first()
        val token = s.accessToken ?: throw Exception("Not authenticated")
        val auth = "Bearer $token"

        try {
            val existing =
                api.listDevices(auth).firstOrNull {
                    it.address.equals(address, ignoreCase = true)
                }

            if (existing != null) {
                val desiredAlias = alias?.takeIf { it.isNotBlank() }
                return if (desiredAlias != null && existing.alias != desiredAlias) {
                    api.updateDevice(
                        auth = auth,
                        deviceId = existing.id,
                        body = DeviceUpdate(alias = desiredAlias)
                    )
                } else {
                    existing
                }
            }
        } catch (e: Exception) {
            throw Exception(parseBackendError(e))
        }

        return try {
            api.createDevice(auth = auth, body = DeviceCreate(address = address, alias = alias))
        } catch (e: Exception) {
            throw Exception(parseBackendError(e))
        }
    }
}