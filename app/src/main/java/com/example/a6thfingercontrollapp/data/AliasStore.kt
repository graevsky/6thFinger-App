package com.example.a6thfingercontrollapp.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "ble_aliases")

class AliasStore(private val context: Context) {
    private fun keyFor(address: String) = stringPreferencesKey("alias_${address.lowercase()}")

    fun alias(address: String): Flow<String?> =
        context.dataStore.data.map { it[keyFor(address)] }

    suspend fun setAlias(address: String, alias: String) {
        context.dataStore.edit { it[keyFor(address)] = alias }
    }
}
