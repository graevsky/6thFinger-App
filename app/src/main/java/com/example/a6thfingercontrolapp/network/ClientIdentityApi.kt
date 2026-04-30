package com.example.a6thfingercontrolapp.network

import com.example.a6thfingercontrolapp.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Retrofit API dedicated only to client identity bootstrap.
 *
 * These endpoints are intentionally separated from the main authenticated API,
 * because attestation happens before request signing and before user login.
 */
interface ClientIdentityApi {
    /**
     * Requests a short-lived challenge used as Android Key Attestation input.
     */
    @GET("/client/challenge")
    suspend fun getClientChallenge(): ClientChallengeOut

    /**
     * Sends Android Key Attestation materials and receives a signed client session.
     */
    @POST("/client/attest")
    suspend fun attestClient(@Body body: ClientAttestationIn): ClientAttestationOut

    companion object {
        /**
         * Builds a Retrofit API instance for client identity bootstrap endpoints.
         */
        fun create(baseUrl: String = BuildConfig.BACKEND_BASE_URL): ClientIdentityApi {
            val logging = HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BASIC
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .build()

            val moshi = Moshi.Builder()
                .add(KotlinJsonAdapterFactory())
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()

            return retrofit.create(ClientIdentityApi::class.java)
        }
    }
}
