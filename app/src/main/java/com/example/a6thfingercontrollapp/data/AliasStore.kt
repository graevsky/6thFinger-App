package com.example.a6thfingercontrollapp.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * DataStore instance for user-defined BLE device aliases.
 */
private val Context.dataStore by preferencesDataStore(name = "ble_aliases")

/**
 * Local alias storage for BLE devices.
 *
 * Aliases are keyed by MAC address and are used only for display in the app.
 */
class AliasStore(private val context: Context) {
    /**
     * Builds a stable preference key for a device alias.
     */
    private fun keyFor(address: String) = stringPreferencesKey("alias_${address.lowercase()}")

    /**
     * Emits the alias for a device address, if one was saved.
     */
    fun alias(address: String): Flow<String?> = context.dataStore.data.map { it[keyFor(address)] }

    /**
     * Stores or replaces the alias for a device address.
     */
    suspend fun setAlias(address: String, alias: String) {
        context.dataStore.edit { it[keyFor(address)] = alias }
    }
}
