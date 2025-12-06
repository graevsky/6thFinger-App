package com.example.a6thfingercontrollapp.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.a6thfingercontrollapp.ble.EspSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.deviceSettingsDataStore by preferencesDataStore(name = "ble_device_settings")

class DeviceSettingsStore(private val context: Context) {

    private fun keyFor(address: String) =
        stringPreferencesKey("cfg_${address.lowercase()}")

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

    suspend fun set(address: String, s: EspSettings) {
        val rawJson = s.toJsonString()
        context.deviceSettingsDataStore.edit { prefs ->
            prefs[keyFor(address)] = rawJson
        }
    }
}
