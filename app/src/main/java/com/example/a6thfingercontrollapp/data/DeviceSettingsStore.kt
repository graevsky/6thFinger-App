package com.example.a6thfingercontrollapp.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.a6thfingercontrollapp.ble.EspSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

/**
 * DataStore instance for per-device BLE configuration snapshots.
 */
private val Context.deviceSettingsDataStore by preferencesDataStore(name = "ble_device_settings")

/**
 * Local cache for ESP32 settings per BLE device address.
 */
class DeviceSettingsStore(private val context: Context) {

    /**
     * Builds a stable preference key from a BLE MAC address.
     */
    private fun keyFor(address: String) = stringPreferencesKey("cfg_${address.lowercase()}")

    /**
     * Emits parsed settings for a device, or null if nothing is cached.
     */
    fun get(address: String): Flow<EspSettings?> =
        context.deviceSettingsDataStore.data.map { prefs ->
            val raw = prefs[keyFor(address)] ?: return@map null
            try {
                val json = JSONObject(raw)
                EspSettings.fromJson(json)
            } catch (_: Throwable) {
                null
            }
        }

    /**
     * Stores the full settings snapshot as JSON for the given BLE address.
     */
    suspend fun set(address: String, s: EspSettings) {
        val rawJson = s.toJsonString()
        context.deviceSettingsDataStore.edit { prefs -> prefs[keyFor(address)] = rawJson }
    }
}
