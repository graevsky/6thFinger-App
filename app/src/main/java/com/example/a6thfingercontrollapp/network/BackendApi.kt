package com.example.a6thfingercontrollapp.network

import com.example.a6thfingercontrollapp.BuildConfig
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

interface BackendApi {
    @GET("/auth/params")
    suspend fun getSrpParams(): RegisterParamsOut

    @POST("/auth/register")
    suspend fun register(@Body body: RegisterIn): RegisterOut

    @POST("/auth/login/start")
    suspend fun loginStart(@Body body: LoginStartIn): LoginStartOut

    @POST("/auth/login/finish")
    suspend fun loginFinish(@Body body: LoginFinishIn): LoginFinishOut

    @POST("/auth/refresh")
    suspend fun refreshToken(@Body body: RefreshTokenIn): RefreshTokenOut

    @POST("/auth/logout")
    suspend fun logout(@Header("Authorization") auth: String): GenericOk

    @GET("/auth/me")
    suspend fun getMe(@Header("Authorization") auth: String): MeOut

    @GET("/settings/")
    suspend fun getAppSettings(@Header("Authorization") auth: String): AppSettingsOut

    @PUT("/settings/")
    suspend fun putAppSettings(
        @Header("Authorization") auth: String,
        @Body body: AppSettingsIn
    ): AppSettingsOut

    @GET("/device/")
    suspend fun listDevices(@Header("Authorization") auth: String): List<DeviceOut>

    @POST("/device/")
    suspend fun createDevice(
        @Header("Authorization") auth: String,
        @Body body: DeviceCreate
    ): DeviceOut

    @PUT("/device/{id}")
    suspend fun updateDevice(
        @Header("Authorization") auth: String,
        @Path("id") deviceId: String,
        @Body body: DeviceUpdate
    ): DeviceOut

    @GET("/device/{id}/settings")
    suspend fun getDeviceSettings(
        @Header("Authorization") auth: String,
        @Path("id") deviceId: String
    ): DeviceSettingsOut

    @GET("/device/{id}/settings")
    suspend fun getDeviceSettingsResponse(
        @Header("Authorization") auth: String,
        @Path("id") deviceId: String
    ): Response<DeviceSettingsOut>

    @POST("/device/{id}/settings")
    suspend fun postDeviceSettings(
        @Header("Authorization") auth: String,
        @Path("id") deviceId: String,
        @Body body: DeviceSettingsIn
    ): DeviceSettingsOut

    @Multipart
    @POST("/avatar/")
    suspend fun uploadAvatar(
        @Header("Authorization") auth: String,
        @Part file: MultipartBody.Part
    ): AvatarOut

    @GET("/avatar/")
    suspend fun downloadAvatar(
        @Header("Authorization") auth: String
    ): Response<ResponseBody>

    @DELETE("/avatar/")
    suspend fun deleteAvatar(
        @Header("Authorization") auth: String
    ): Response<Unit>

    @POST("/auth/email/start-add")
    suspend fun emailStartAdd(
        @Header("Authorization") auth: String,
        @Body body: EmailStartAddIn
    ): GenericOk

    @POST("/auth/email/confirm-add")
    suspend fun emailConfirmAdd(
        @Header("Authorization") auth: String,
        @Body body: EmailConfirmIn
    ): GenericOk

    @POST("/auth/email/start-remove")
    suspend fun emailStartRemove(
        @Header("Authorization") auth: String
    ): GenericOk

    @POST("/auth/email/confirm-remove")
    suspend fun emailConfirmRemove(
        @Header("Authorization") auth: String,
        @Body body: EmailRemoveConfirmIn
    ): GenericOk

    @POST("/auth/password-reset/start")
    suspend fun passwordResetStart(
        @Body body: PasswordResetStartIn
    ): PasswordResetStartOut

    @POST("/auth/password-reset/email/send")
    suspend fun passwordResetEmailSend(
        @Body body: PasswordResetEmailSendIn
    ): GenericOk

    @POST("/auth/password-reset/email/verify")
    suspend fun passwordResetEmailVerify(
        @Body body: PasswordResetEmailVerifyIn
    ): PasswordResetVerifyOut

    @POST("/auth/password-reset/recovery/verify")
    suspend fun passwordResetRecoveryVerify(
        @Body body: PasswordResetRecoveryVerifyIn
    ): PasswordResetVerifyOut

    @POST("/auth/password-reset/finish")
    suspend fun passwordResetFinish(
        @Body body: PasswordResetFinishIn
    ): GenericOk

    companion object {
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