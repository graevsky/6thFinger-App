package com.example.a6thfingercontrollapp.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface BackendApi {
    @GET("/auth/params")
    suspend fun getSrpParams(): RegisterParamsOut

    @POST("/auth/register")
    suspend fun register(@Body body: RegisterIn)

    @POST("/auth/login/start")
    suspend fun loginStart(@Body body: LoginStartIn): LoginStartOut

    @POST("/auth/login/finish")
    suspend fun loginFinish(@Body body: LoginFinishIn): LoginFinishOut

    @GET("/settings/")
    suspend fun getAppSettings(
        @Header("Authorization") auth: String
    ): AppSettingsOut

    @PUT("/settings/")
    suspend fun putAppSettings(
        @Header("Authorization") auth: String,
        @Body body: AppSettingsIn
    ): AppSettingsOut

    @GET("/device/")
    suspend fun listDevices(
        @Header("Authorization") auth: String
    ): List<DeviceOut>

    @POST("/device/")
    suspend fun createDevice(
        @Header("Authorization") auth: String,
        @Body body: DeviceCreate
    ): DeviceOut

    @GET("/device/{id}/settings")
    suspend fun getDeviceSettings(
        @Header("Authorization") auth: String,
        @Path("id") deviceId: String
    ): DeviceSettingsOut

    @POST("/device/{id}/settings")
    suspend fun postDeviceSettings(
        @Header("Authorization") auth: String,
        @Path("id") deviceId: String,
        @Body body: DeviceSettingsIn
    ): DeviceSettingsOut

    companion object {
        private const val BASE_URL = "http://10.0.2.2:8000" // temp stub "http://10.0.2.2:8000" or pc ip "http://192.168.31.210:8000"

        fun create(): BackendApi {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .build()

            val moshi = Moshi.Builder()
                .add(KotlinJsonAdapterFactory())
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()

            return retrofit.create(BackendApi::class.java)
        }
    }
}
