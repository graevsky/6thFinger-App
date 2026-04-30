package com.example.a6thfingercontrolapp.network

import android.content.Context
import android.util.Base64
import com.example.a6thfingercontrolapp.BuildConfig
import com.example.a6thfingercontrolapp.data.ClientIdentityStore
import com.example.a6thfingercontrolapp.security.ClientRequestSigner
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import okio.Buffer
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant

/**
 * Retrofit interface for the backend API.
 *
 * Responsibilities:
 * - expose SRP authentication endpoints
 * - manage access/refresh token based sessions
 * - sync app settings and device settings with the cloud
 * - upload, download and delete user avatars
 * - support email management and password reset flows
 */
interface BackendApi {
    /**
     * Loads SRP parameters shared by registration and login.
     */
    @GET("/auth/params")
    suspend fun getSrpParams(): RegisterParamsOut

    /**
     * Creates a new account using an SRP verifier.
     */
    @POST("/auth/register")
    suspend fun register(@Body body: RegisterIn): RegisterOut

    /**
     * Starts SRP login and receives salt, server public value and SRP parameters.
     */
    @POST("/auth/login/start")
    suspend fun loginStart(@Body body: LoginStartIn): LoginStartOut

    /**
     * Finishes SRP login by sending client proof and receiving session tokens.
     */
    @POST("/auth/login/finish")
    suspend fun loginFinish(@Body body: LoginFinishIn): LoginFinishOut

    /**
     * Exchanges a refresh token for a new access token.
     */
    @POST("/auth/refresh")
    suspend fun refreshToken(@Body body: RefreshTokenIn): RefreshTokenOut

    /**
     * Invalidates the current authenticated session on the backend.
     */
    @POST("/auth/logout")
    suspend fun logout(@Header("Authorization") auth: String): GenericOk

    /**
     * Returns information about the currently authenticated user.
     */
    @GET("/auth/me")
    suspend fun getMe(@Header("Authorization") auth: String): MeOut

    /**
     * Loads user-level app settings.
     */
    @GET("/settings/")
    suspend fun getAppSettings(@Header("Authorization") auth: String): AppSettingsOut

    /**
     * Saves user-level app settings to the backend.
     */
    @PUT("/settings/")
    suspend fun putAppSettings(
        @Header("Authorization") auth: String,
        @Body body: AppSettingsIn
    ): AppSettingsOut

    /**
     * Lists BLE devices registered for the current user.
     */
    @GET("/device/")
    suspend fun listDevices(@Header("Authorization") auth: String): List<DeviceOut>

    /**
     * Registers a new BLE device in the user cloud account.
     */
    @POST("/device/")
    suspend fun createDevice(
        @Header("Authorization") auth: String,
        @Body body: DeviceCreate
    ): DeviceOut

    /**
     * Updates data for an existing device.
     */
    @PUT("/device/{id}")
    suspend fun updateDevice(
        @Header("Authorization") auth: String,
        @Path("id") deviceId: String,
        @Body body: DeviceUpdate
    ): DeviceOut

    /**
     * Loads device settings as a raw Retrofit response.
     */
    @GET("/device/{id}/settings")
    suspend fun getDeviceSettingsResponse(
        @Header("Authorization") auth: String,
        @Path("id") deviceId: String
    ): Response<DeviceSettingsOut>

    /**
     * Stores a new version of device settings in the cloud.
     */
    @POST("/device/{id}/settings")
    suspend fun postDeviceSettings(
        @Header("Authorization") auth: String,
        @Path("id") deviceId: String,
        @Body body: DeviceSettingsIn
    ): DeviceSettingsOut

    /**
     * Uploads the current user avatar as image data.
     */
    @Multipart
    @POST("/avatar/")
    suspend fun uploadAvatar(
        @Header("Authorization") auth: String,
        @Part file: MultipartBody.Part
    ): AvatarOut

    /**
     * Downloads the current user avatar as raw response bytes.
     */
    @GET("/avatar/")
    suspend fun downloadAvatar(
        @Header("Authorization") auth: String
    ): Response<ResponseBody>

    /**
     * Deletes the current user avatar from the backend.
     */
    @DELETE("/avatar/")
    suspend fun deleteAvatar(
        @Header("Authorization") auth: String
    ): Response<Unit>

    /**
     * Starts email attachment flow by sending a verification code.
     */
    @POST("/auth/email/start-add")
    suspend fun emailStartAdd(
        @Header("Authorization") auth: String,
        @Body body: EmailStartAddIn
    ): GenericOk

    /**
     * Confirms a new account email using the received verification code.
     */
    @POST("/auth/email/confirm-add")
    suspend fun emailConfirmAdd(
        @Header("Authorization") auth: String,
        @Body body: EmailConfirmIn
    ): GenericOk

    /**
     * Starts email removal flow by sending a verification code to the current email.
     */
    @POST("/auth/email/start-remove")
    suspend fun emailStartRemove(
        @Header("Authorization") auth: String
    ): GenericOk

    /**
     * Confirms email removal using either an email code or recovery code.
     */
    @POST("/auth/email/confirm-remove")
    suspend fun emailConfirmRemove(
        @Header("Authorization") auth: String,
        @Body body: EmailRemoveConfirmIn
    ): GenericOk

    /**
     * Starts password reset and returns available recovery methods.
     */
    @POST("/auth/password-reset/start")
    suspend fun passwordResetStart(
        @Body body: PasswordResetStartIn
    ): PasswordResetStartOut

    /**
     * Sends password reset code to the provided email.
     */
    @POST("/auth/password-reset/email/send")
    suspend fun passwordResetEmailSend(
        @Body body: PasswordResetEmailSendIn
    ): GenericOk

    /**
     * Verifies email reset code and returns a reset session id.
     */
    @POST("/auth/password-reset/email/verify")
    suspend fun passwordResetEmailVerify(
        @Body body: PasswordResetEmailVerifyIn
    ): PasswordResetVerifyOut

    /**
     * Verifies recovery code and returns a reset session id.
     */
    @POST("/auth/password-reset/recovery/verify")
    suspend fun passwordResetRecoveryVerify(
        @Body body: PasswordResetRecoveryVerifyIn
    ): PasswordResetVerifyOut

    /**
     * Finishes password reset by storing a new SRP salt and verifier.
     */
    @POST("/auth/password-reset/finish")
    suspend fun passwordResetFinish(
        @Body body: PasswordResetFinishIn
    ): GenericOk

    companion object {
        private val nonceRandom = SecureRandom()

        /**
         * Canonical request signing for official builds that already hold a client session.
         */
        private fun clientSigningInterceptor(context: Context): Interceptor {
            val store = ClientIdentityStore(context.applicationContext)
            val signer = ClientRequestSigner()

            return Interceptor { chain ->
                val original = chain.request()

                if (!BuildConfig.CLIENT_ATTESTATION_REQUIRED) {
                    return@Interceptor chain.proceed(original)
                }

                val session = runBlocking { store.getClientSession() }
                    ?: return@Interceptor chain.proceed(original)

                if (session.isExpiringSoon(leewaySeconds = 0)) {
                    return@Interceptor chain.proceed(original)
                }

                val bodyBytes = original.body?.let { body ->
                    val buffer = Buffer()
                    body.writeTo(buffer)
                    buffer.readByteArray()
                } ?: ByteArray(0)

                val bodyHash = sha256Base64Url(bodyBytes)
                val timestamp = Instant.now().toString()
                val nonce = randomBase64Url(16)
                val pathWithQuery = buildPathWithQuery(original)

                val canonical = listOf(
                    original.method,
                    pathWithQuery,
                    bodyHash,
                    timestamp,
                    nonce,
                    session.clientSessionToken
                ).joinToString("\n")

                val requestWithClientSignature = original.newBuilder()
                    .header("X-Client-Key-Id", session.clientKeyId)
                    .header("X-Client-Session", session.clientSessionToken)
                    .header("X-Client-Timestamp", timestamp)
                    .header("X-Client-Nonce", nonce)
                    .header("X-Client-Body-SHA256", bodyHash)
                    .header("X-Client-Signature", signer.signBase64Url(canonical))
                    .build()

                chain.proceed(requestWithClientSignature)
            }
        }

        /**
         * Produces the exact path and query component used by backend request verification.
         */
        private fun buildPathWithQuery(request: okhttp3.Request): String {
            val query = request.url.encodedQuery
            return if (query.isNullOrBlank()) {
                request.url.encodedPath
            } else {
                request.url.encodedPath + "?" + query
            }
        }

        /**
         * Returns a base64url-encoded SHA-256 body hash compatible with the backend verifier.
         */
        private fun sha256Base64Url(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            return Base64.encodeToString(
                digest,
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )
        }

        /**
         * Generates a URL-safe random nonce for replay protection.
         */
        private fun randomBase64Url(lengthBytes: Int): String {
            val bytes = ByteArray(lengthBytes)
            nonceRandom.nextBytes(bytes)
            return Base64.encodeToString(
                bytes,
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )
        }

        /**
         * Builds a Retrofit API instance with Moshi serialization and OkHttp.
         */
        fun create(context: Context): BackendApi {
            val logging = HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BASIC
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(clientSigningInterceptor(context))
                .addInterceptor(logging)
                .build()

            val moshi = Moshi.Builder()
                .add(KotlinJsonAdapterFactory())
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(BuildConfig.BACKEND_BASE_URL)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()

            return retrofit.create(BackendApi::class.java)
        }
    }
}
