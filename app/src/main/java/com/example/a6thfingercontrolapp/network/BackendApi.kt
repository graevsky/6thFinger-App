package com.example.a6thfingercontrolapp.network

import com.example.a6thfingercontrolapp.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
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
     * Loads the latest settings snapshot for a device.
     */
    @GET("/device/{id}/settings")
    suspend fun getDeviceSettings(
        @Header("Authorization") auth: String,
        @Path("id") deviceId: String
    ): DeviceSettingsOut

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
        /**
         * App level client token to every backend request.
         *
         * The header is controlled by BuildConfig so it can be disabled for builds
         * that do not require extra client authentication.
         */
        private fun clientTokenInterceptor(): Interceptor {
            return Interceptor { chain ->
                val original = chain.request()

                if (!BuildConfig.APP_CLIENT_TOKEN_ENABLED) {
                    return@Interceptor chain.proceed(original)
                }

                val headerName = BuildConfig.APP_CLIENT_HEADER_NAME.trim()
                val token = BuildConfig.APP_CLIENT_TOKEN.trim()

                if (headerName.isBlank() || token.isBlank()) {
                    return@Interceptor chain.proceed(original)
                }

                val requestWithClientToken = original.newBuilder()
                    .header(headerName, token)
                    .build()

                chain.proceed(requestWithClientToken)
            }
        }

        /**
         * Builds a Retrofit API instance with Moshi serialization and OkHttp.
         */
        fun create(): BackendApi {
            val logging = HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BODY
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(clientTokenInterceptor())
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