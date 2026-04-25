package com.example.a6thfingercontrolapp.data

import android.content.Context
import com.example.a6thfingercontrolapp.ble.EmgSettings
import com.example.a6thfingercontrolapp.ble.EspSettings
import com.example.a6thfingercontrolapp.ble.FlexSettings
import com.example.a6thfingercontrolapp.ble.PairInputSettings
import com.example.a6thfingercontrolapp.ble.ServoSettings
import com.example.a6thfingercontrolapp.network.AppSettingsIn
import com.example.a6thfingercontrolapp.network.BackendApi
import com.example.a6thfingercontrolapp.network.DeviceCreate
import com.example.a6thfingercontrolapp.network.DeviceOut
import com.example.a6thfingercontrolapp.network.DeviceSettingsIn
import com.example.a6thfingercontrolapp.network.DeviceUpdate
import com.example.a6thfingercontrolapp.network.EmailConfirmIn
import com.example.a6thfingercontrolapp.network.EmailRemoveConfirmIn
import com.example.a6thfingercontrolapp.network.EmailStartAddIn
import com.example.a6thfingercontrolapp.network.LoginFinishIn
import com.example.a6thfingercontrolapp.network.LoginStartIn
import com.example.a6thfingercontrolapp.network.MeOut
import com.example.a6thfingercontrolapp.network.PasswordResetEmailSendIn
import com.example.a6thfingercontrolapp.network.PasswordResetEmailVerifyIn
import com.example.a6thfingercontrolapp.network.PasswordResetFinishIn
import com.example.a6thfingercontrolapp.network.PasswordResetRecoveryVerifyIn
import com.example.a6thfingercontrolapp.network.PasswordResetStartIn
import com.example.a6thfingercontrolapp.network.PasswordResetStartOut
import com.example.a6thfingercontrolapp.network.RefreshTokenIn
import com.example.a6thfingercontrolapp.network.RegisterIn
import com.example.a6thfingercontrolapp.security.srp.SrpLogin
import com.example.a6thfingercontrolapp.security.srp.SrpRegister
import com.example.a6thfingercontrolapp.utils.parseBackendError
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import retrofit2.HttpException
import retrofit2.Response
import java.io.File

/**
 * Cloud-stored settings snapshot for one prosthesis device.
 */
data class DeviceSettingsRecord(
    val settings: EspSettings,
    val version: Int,
    val updatedAt: String
)

/**
 * Persistent authentication state used by the repository layer.
 */
sealed class AuthState {
    object Unauthenticated : AuthState()
    object Guest : AuthState()
    data class LoggedIn(val username: String, val accessToken: String, val refreshToken: String) :
        AuthState()
}

/**
 * Internal signal used when stored credentials can no longer be refreshed.
 */
private class SessionExpiredException : Exception("session_expired")

/**
 * Main data layer for authentication, cloud settings, devices and avatar sync.
 *
 * Responsibilities:
 * - perform registration/login against the backend
 * - store and refresh access/refresh tokens
 * - wrap authorized backend calls with automatic token refresh
 * - synchronize app settings, device settings and avatars with the cloud
 * - expose account email and password reset operations
 */
class AuthRepository(context: Context) {

    private val api = BackendApi.create()
    private val store = AuthStore(context.applicationContext)
    private val appContext = context.applicationContext

    /**
     * Serializes token refresh requests to avoid overlapping refresh calls.
     */
    private val tokenRefreshMutex = Mutex()

    /**
     * Raw stored auth flow from DataStore.
     */
    val stored = store.authState

    /**
     * Resolves the app startup auth state from locally stored credentials.
     *
     * Stored logged-in sessions are validated through the backend. Invalid or
     * expired sessions are cleared.
     */
    suspend fun resolveInitialState(): AuthState {
        val s = stored.first()
        return when {
            s.isGuest -> AuthState.Guest
            s.accessToken != null && s.username != null && s.refreshToken != null -> {
                try {
                    validateStoredSession()
                } catch (_: Exception) {
                    store.logout()
                    AuthState.Unauthenticated
                }
            }

            else -> AuthState.Unauthenticated
        }
    }

    /**
     * Validates the currently stored tokens by requesting the current user.
     */
    private suspend fun validateStoredSession(): AuthState {
        val storedState = stored.first()
        val refreshToken = storedState.refreshToken ?: throw SessionExpiredException()

        val me = getMeWithRefreshFallback()
        val currentState = stored.first()
        val accessToken = currentState.accessToken ?: throw SessionExpiredException()
        val username = me.username.trim().lowercase()

        store.saveTokens(username, accessToken, refreshToken)
        return AuthState.LoggedIn(username, accessToken, refreshToken)
    }

    /**
     * Reads the current backend user and refreshes the access token if needed.
     */
    private suspend fun getMeWithRefreshFallback(): MeOut {
        val s = stored.first()
        val accessToken = s.accessToken ?: throw SessionExpiredException()

        return try {
            api.getMe("Bearer $accessToken")
        } catch (e: Exception) {
            if (e is HttpException && e.code() == 401) {
                val refreshedAccess = refreshAccessToken(failedAccessToken = accessToken)
                api.getMe("Bearer $refreshedAccess")
            } else {
                throw e
            }
        }
    }

    /**
     * Refreshes access token using the stored refresh token.
     *
     * If another coroutine already refreshed the same failed token, the new
     * stored access token is reused instead of issuing another refresh request.
     */
    private suspend fun refreshAccessToken(failedAccessToken: String? = null): String =
        tokenRefreshMutex.withLock {
            val currentState = stored.first()
            val username = currentState.username ?: throw SessionExpiredException()
            val refreshToken = currentState.refreshToken ?: throw SessionExpiredException()

            if (
                failedAccessToken != null &&
                !currentState.accessToken.isNullOrBlank() &&
                currentState.accessToken != failedAccessToken
            ) {
                return currentState.accessToken
            }

            return try {
                val out = api.refreshToken(RefreshTokenIn(refreshToken))
                store.saveTokens(username, out.access_token, refreshToken)
                out.access_token
            } catch (_: Exception) {
                store.logout()
                throw SessionExpiredException()
            }
        }

    /**
     * Executes an authorized API call and retries once after access-token refresh.
     */
    private suspend fun <T> withAuthorizedRequest(
        block: suspend (authHeader: String) -> T
    ): T {
        val currentState = stored.first()
        val accessToken = currentState.accessToken ?: throw SessionExpiredException()

        return try {
            block("Bearer $accessToken")
        } catch (e: Exception) {
            if (e is HttpException && e.code() == 401) {
                val refreshedAccess = refreshAccessToken(failedAccessToken = accessToken)
                try {
                    block("Bearer $refreshedAccess")
                } catch (retry: Exception) {
                    if (retry is HttpException && retry.code() == 401) {
                        store.logout()
                        throw SessionExpiredException()
                    }
                    throw retry
                }
            } else {
                throw e
            }
        }
    }

    /**
     * Used by endpoints where non-2xx statuses such as 404 are meaningful.
     */
    private suspend fun <T> withAuthorizedResponse(
        block: suspend (authHeader: String) -> Response<T>
    ): Response<T> {
        val currentState = stored.first()
        val accessToken = currentState.accessToken ?: throw SessionExpiredException()

        val firstResponse = block("Bearer $accessToken")
        if (firstResponse.code() != 401) {
            return firstResponse
        }

        val refreshedAccess = refreshAccessToken(failedAccessToken = accessToken)
        val secondResponse = block("Bearer $refreshedAccess")
        if (secondResponse.code() == 401) {
            store.logout()
            throw SessionExpiredException()
        }

        return secondResponse
    }

    /**
     * Stores guest mode locally and returns the corresponding state.
     */
    suspend fun continueAsGuest(): AuthState {
        store.setGuest()
        return AuthState.Guest
    }

    /**
     * Registers a new user.
     *
     * The raw password is never sent to the backend; only salt and verifier are
     * submitted. Backend recovery codes are returned for the post-registration flow.
     */
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

    /**
     * Logs in with SRP challenge-response and persists received tokens.
     */
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

    /**
     * Logs out remotely when possible, then clears local auth state.
     */
    suspend fun logout(): AuthState {
        val currentState = stored.first()

        if (
            currentState.username != null &&
            currentState.accessToken != null &&
            currentState.refreshToken != null
        ) {
            runCatching {
                withAuthorizedRequest { auth ->
                    api.logout(auth)
                }
            }
        }

        store.logout()
        return AuthState.Unauthenticated
    }

    /**
     * Downloads user-level app settings payload from the backend.
     */
    suspend fun pullAppSettings(): Map<String, Any?>? {
        return try {
            val res = withAuthorizedRequest { auth ->
                api.getAppSettings(auth)
            }
            res.payload
        } catch (e: Exception) {
            throw Exception(parseBackendError(e))
        }
    }

    /**
     * Uploads user-level app settings payload to the backend.
     */
    suspend fun pushAppSettings(payload: Map<String, Any?>) {
        try {
            withAuthorizedRequest { auth ->
                api.putAppSettings(auth, AppSettingsIn(payload))
            }
        } catch (e: Exception) {
            throw Exception(parseBackendError(e))
        }
    }

    /**
     * Lists all prosthesis devices registered for the current account.
     */
    suspend fun listDevices(): List<DeviceOut> {
        return try {
            withAuthorizedRequest { auth ->
                api.listDevices(auth)
            }
        } catch (e: Exception) {
            throw Exception(parseBackendError(e))
        }
    }

    /**
     * Converts ESP settings to backend payload and stores a new server version.
     */
    suspend fun pushDeviceSettings(deviceId: String, settings: EspSettings): DeviceSettingsRecord {
        val payload = espToPayload(settings)

        try {
            val res = withAuthorizedRequest { auth ->
                api.postDeviceSettings(
                    auth = auth,
                    deviceId = deviceId,
                    body = DeviceSettingsIn(payload = payload)
                )
            }

            return DeviceSettingsRecord(
                settings = payloadToEsp(res.payload) ?: settings,
                version = res.version,
                updatedAt = res.updated_at
            )
        } catch (e: Exception) {
            throw Exception(parseBackendError(e))
        }
    }

    /**
     * Fetches the latest cloud settings record for a device.
     */
    suspend fun getDeviceSettingsRecord(deviceId: String): DeviceSettingsRecord? {
        return try {
            val resp = withAuthorizedResponse { auth ->
                api.getDeviceSettingsResponse(auth = auth, deviceId = deviceId)
            }

            if (resp.code() == 404) return null
            if (!resp.isSuccessful) throw HttpException(resp)

            val body = resp.body() ?: return null
            val settings = payloadToEsp(body.payload) ?: return null

            DeviceSettingsRecord(
                settings = settings,
                version = body.version,
                updatedAt = body.updated_at
            )
        } catch (e: Exception) {
            throw Exception(parseBackendError(e))
        }
    }

    /**
     * Convenience wrapper that returns only parsed settings.
     */
    suspend fun pullDeviceSettings(deviceId: String): EspSettings? {
        return getDeviceSettingsRecord(deviceId)?.settings
    }

    /**
     * Uploads local avatar file as multipart data.
     */
    suspend fun uploadAvatar(localPath: String) {
        val f = File(localPath)
        if (!f.exists()) throw Exception("Avatar file not found")

        val mediaType = "image/jpeg".toMediaType()
        val body = f.asRequestBody(mediaType)
        val part = MultipartBody.Part.createFormData("file", f.name, body)

        try {
            withAuthorizedRequest { auth ->
                api.uploadAvatar(auth, part)
            }
        } catch (e: Exception) {
            throw Exception(parseBackendError(e))
        }
    }

    /**
     * Downloads account avatar into the app's private local storage.
     *
     * Missing remote avatar is treated as a valid null result.
     */
    suspend fun downloadAvatarToLocal(): String? {
        val resp = try {
            withAuthorizedResponse { auth ->
                api.downloadAvatar(auth)
            }
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

    /**
     * Deletes remote avatar.
     */
    suspend fun deleteAvatarRemote() {
        try {
            val resp = withAuthorizedResponse { auth ->
                api.deleteAvatar(auth)
            }

            if (!resp.isSuccessful && resp.code() != 404) {
                throw Exception("Avatar delete failed (${resp.code()})")
            }
        } catch (e: Exception) {
            throw Exception(parseBackendError(e))
        }
    }

    /**
     * Starts account email addition by asking the backend to send a code.
     */
    suspend fun emailStartAdd(email: String) {
        try {
            withAuthorizedRequest { auth ->
                api.emailStartAdd(auth, EmailStartAddIn(email.trim()))
            }
        } catch (e: Exception) {
            throw Exception(parseBackendError(e))
        }
    }

    /**
     * Confirms account email addition using a verification code.
     */
    suspend fun emailConfirmAdd(email: String, code: String) {
        try {
            withAuthorizedRequest { auth ->
                api.emailConfirmAdd(auth, EmailConfirmIn(email.trim(), code.trim()))
            }
        } catch (e: Exception) {
            throw Exception(parseBackendError(e))
        }
    }

    /**
     * Starts account email removal by sending a confirmation code.
     */
    suspend fun emailStartRemove() {
        try {
            withAuthorizedRequest { auth ->
                api.emailStartRemove(auth)
            }
        } catch (e: Exception) {
            throw Exception(parseBackendError(e))
        }
    }

    /**
     * Confirms email removal using either email code or recovery code.
     */
    suspend fun emailConfirmRemove(code: String?, recoveryCode: String?) {
        try {
            withAuthorizedRequest { auth ->
                api.emailConfirmRemove(
                    auth,
                    EmailRemoveConfirmIn(
                        code = code?.trim()?.takeIf { it.isNotBlank() },
                        recovery_code = recoveryCode?.trim()?.takeIf { it.isNotBlank() }
                    )
                )
            }
        } catch (e: Exception) {
            throw Exception(parseBackendError(e))
        }
    }

    /**
     * Retrieves password reset capabilities for the requested username.
     */
    suspend fun passwordResetStart(username: String): PasswordResetStartOut {
        try {
            return api.passwordResetStart(PasswordResetStartIn(username.trim().lowercase()))
        } catch (e: Exception) {
            throw Exception(parseBackendError(e))
        }
    }

    /**
     * Sends a password reset code to the provided account email.
     */
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

    /**
     * Verifies password reset email code and returns a reset session id.
     */
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

    /**
     * Verifies a recovery code and returns a reset session id.
     */
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

    /**
     * Finishes password reset by generating a new SRP salt/verifier pair.
     */
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

    /**
     * Converts one flex settings object to a backend-friendly map.
     */
    private fun flexToMap(f: FlexSettings): Map<String, Any?> = mapOf(
        "flexPin" to f.flexPin,
        "flexPullupOhm" to f.flexPullupOhm,
        "flexStraightOhm" to f.flexStraightOhm,
        "flexBendOhm" to f.flexBendOhm,
        "flexTolerancePct" to f.flexTolerancePct
    )

    /**
     * Converts one servo settings object to a backend-friendly map.
     */
    private fun servoToMap(s: ServoSettings): Map<String, Any?> = mapOf(
        "servoPin" to s.servoPin,
        "servoMinDeg" to s.servoMinDeg,
        "servoMaxDeg" to s.servoMaxDeg,
        "servoManual" to s.servoManual,
        "servoManualDeg" to s.servoManualDeg,
        "servoMaxSpeedDegPerSec" to s.servoMaxSpeedDegPerSec.toDouble()
    )

    /**
     * Converts pair input source settings to a backend-friendly map.
     */
    private fun pairInputToMap(p: PairInputSettings): Map<String, Any?> = mapOf(
        "inputSource" to p.inputSource
    )

    /**
     * Converts one EMG settings object to a backend-friendly map.
     */
    private fun emgToMap(e: EmgSettings): Map<String, Any?> = mapOf(
        "channels" to e.channels,
        "pins" to listOf(e.pin0, e.pin1, e.pin2),
        "mode" to e.mode,
        "bendFullMoves" to e.bendFullMoves,
        "unfoldFullMoves" to e.unfoldFullMoves,
        "minSwitchDelaySec" to e.minSwitchDelaySec,
        "reverseDirection" to e.reverseDirection
    )

    /**
     * Converts the full ESP32 settings snapshot into backend payload format.
     */
    private fun espToPayload(settings: EspSettings): Map<String, Any?> {
        return mapOf(
            "fsrPin" to settings.fsrPin,
            "fsrPullupOhm" to settings.fsrPullupOhm,
            "fsrSoftThresholdN" to settings.fsrSoftThresholdN.toDouble(),
            "fsrHardMaxN" to settings.fsrHardMaxN.toDouble(),

            "flexSettings" to settings.flexSettings.map { flexToMap(it) },
            "servoSettings" to settings.servoSettings.map { servoToMap(it) },
            "pairInputSettings" to settings.pairInputSettings.map { pairInputToMap(it) },
            "emgSettings" to settings.emgSettings.map { emgToMap(it) },

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

    /**
     * Parses backend payload back into the app's ESP32 settings model.
     */
    private fun payloadToEsp(payload: Map<String, Any?>): EspSettings? {
        return try {
            val json = JSONObject(payload)
            EspSettings.fromJson(json)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Ensures that a BLE device exists in the user's cloud device list.
     *
     * Existing devices are matched by MAC address. If alias changed locally,
     * the backend record is updated before returning it.
     */
    suspend fun ensureDevice(address: String, alias: String?): DeviceOut {
        return try {
            withAuthorizedRequest { auth ->
                val existing =
                    api.listDevices(auth).firstOrNull {
                        it.address.equals(address, ignoreCase = true)
                    }

                if (existing != null) {
                    val desiredAlias = alias?.takeIf { it.isNotBlank() }
                    if (desiredAlias != null && existing.alias != desiredAlias) {
                        api.updateDevice(
                            auth = auth,
                            deviceId = existing.id,
                            body = DeviceUpdate(alias = desiredAlias)
                        )
                    } else {
                        existing
                    }
                } else {
                    api.createDevice(
                        auth = auth,
                        body = DeviceCreate(address = address, alias = alias)
                    )
                }
            }
        } catch (e: Exception) {
            throw Exception(parseBackendError(e))
        }
    }
}