package com.example.a6thfingercontrollapp.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.authDataStore by preferencesDataStore(name = "auth_store")

data class AuthStateStored(
    val username: String?,
    val accessToken: String?,
    val refreshToken: String?,
    val isGuest: Boolean
)

class AuthStore(private val context: Context) {

    private object Keys {
        val USERNAME = stringPreferencesKey("username")
        val ACCESS = stringPreferencesKey("access_token")
        val REFRESH = stringPreferencesKey("refresh_token")
        val IS_GUEST = booleanPreferencesKey("is_guest")
    }

    val authState: Flow<AuthStateStored> =
        context.authDataStore.data.map { prefs ->
            AuthStateStored(
                username = prefs[Keys.USERNAME],
                accessToken = prefs[Keys.ACCESS],
                refreshToken = prefs[Keys.REFRESH],
                isGuest = prefs[Keys.IS_GUEST] ?: false
            )
        }

    suspend fun saveTokens(username: String, access: String, refresh: String) {
        context.authDataStore.edit { prefs ->
            prefs[Keys.USERNAME] = username
            prefs[Keys.ACCESS] = access
            prefs[Keys.REFRESH] = refresh
            prefs[Keys.IS_GUEST] = false
        }
    }

    suspend fun setGuest() {
        context.authDataStore.edit { prefs ->
            prefs.remove(Keys.USERNAME)
            prefs.remove(Keys.ACCESS)
            prefs.remove(Keys.REFRESH)
            prefs[Keys.IS_GUEST] = true
        }
    }

    suspend fun logout() {
        context.authDataStore.edit { prefs ->
            prefs.remove(Keys.USERNAME)
            prefs.remove(Keys.ACCESS)
            prefs.remove(Keys.REFRESH)
            prefs[Keys.IS_GUEST] = false
        }
    }
}
