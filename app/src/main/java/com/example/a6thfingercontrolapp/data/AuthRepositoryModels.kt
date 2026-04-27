package com.example.a6thfingercontrolapp.data

import com.example.a6thfingercontrolapp.ble.settings.EspSettings

/**
 * Cloud settings entry for one prosthesis device.
 */
data class DeviceSettingsRecord(
    val settings: EspSettings,
    val version: Int,
    val updatedAt: String
)

/**
 * Authentication state used by the repository layer.
 */
sealed class AuthState {
    object Unauthenticated : AuthState()
    object Guest : AuthState()
    data class LoggedIn(val username: String, val accessToken: String, val refreshToken: String) :
        AuthState()
}
