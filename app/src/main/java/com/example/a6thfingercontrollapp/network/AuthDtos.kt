package com.example.a6thfingercontrollapp.network

data class RegisterParamsOut(val N: String, val g: String)

data class RegisterIn(val username: String, val salt: String, val verifier: String)

data class LoginStartIn(val username: String)

data class LoginStartOut(val salt: String, val B: String, val N: String, val g: String)

data class LoginFinishIn(
        val username: String,
        val A: String,
        val M1: String,
        val salt: String,
        val device_label: String? = null
)

data class LoginFinishOut(val M2: String, val access_token: String, val refresh_token: String)

data class AppSettingsOut(
        val id: String,
        val user_id: String,
        val payload: Map<String, Any?>,
        val updated_at: String
)

data class AppSettingsIn(val payload: Map<String, Any?>)

data class DeviceCreate(val address: String, val alias: String?)

data class DeviceUpdate(val alias: String?)

data class DeviceOut(
        val id: String,
        val owner_id: String,
        val address: String,
        val alias: String?,
        val created_at: String
)

data class DeviceSettingsIn(val payload: Map<String, Any?>)

data class DeviceSettingsOut(
        val id: String,
        val device_id: String,
        val version: Int,
        val payload: Map<String, Any?>,
        val updated_at: String
)
