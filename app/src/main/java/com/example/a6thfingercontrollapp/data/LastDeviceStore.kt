package com.example.a6thfingercontrollapp.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "ble_prefs")

data class LastDevice(val name: String, val address: String)

class LastDeviceStore(private val context: Context) {
    private object Keys {
        val NAME = stringPreferencesKey("last_name")
        val ADDRESS = stringPreferencesKey("last_addr")
    }

    val lastDevice: Flow<LastDevice?> =
        context.dataStore.data.map { prefs: Preferences ->
            val addr = prefs[Keys.ADDRESS]
            val name = prefs[Keys.NAME]
            if (addr.isNullOrBlank()) null else LastDevice(name.orEmpty(), addr)
        }

    suspend fun save(name: String, address: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.NAME] = name
            prefs[Keys.ADDRESS] = address
        }
    }
}
