package com.example.a6thfingercontrolapp.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * DataStore instance for the last selected BLE device.
 */
private val Context.dataStore by preferencesDataStore(name = "ble_prefs")

/**
 * Simple persisted reference to the last BLE device used by the app.
 */
data class LastDevice(val name: String, val address: String)

/**
 * Stores and restores the last connected/selected BLE device.
 */
class LastDeviceStore(private val context: Context) {
    /**
     * Preference keys for last device name and MAC address.
     */
    private object Keys {
        val NAME = stringPreferencesKey("last_name")
        val ADDRESS = stringPreferencesKey("last_addr")
    }

    /**
     * Emits the saved last device, or null if no address was stored yet.
     */
    val lastDevice: Flow<LastDevice?> =
        context.dataStore.data.map { prefs: Preferences ->
            val addr = prefs[Keys.ADDRESS]
            val name = prefs[Keys.NAME]
            if (addr.isNullOrBlank()) null else LastDevice(name.orEmpty(), addr)
        }

    /**
     * Saves the BLE device shown as the latest known target.
     */
    suspend fun save(name: String, address: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.NAME] = name
            prefs[Keys.ADDRESS] = address
        }
    }
}
