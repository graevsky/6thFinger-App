package com.example.a6thfingercontrolapp.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.a6thfingercontrolapp.security.SecureToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * DataStore instance dedicated to authentication credentials.
 */
private val Context.authDataStore by preferencesDataStore(name = "auth_store")

/**
 * Raw auth state restored from local storage.
 */
data class AuthStateStored(
    val username: String?,
    val accessToken: String?,
    val refreshToken: String?,
    val isGuest: Boolean
)

/**
 * Local persistence for auth tokens and guest mode.
 */
class AuthStore(private val context: Context) {

    /** Keystore-backed helper used to encrypt/decrypt token values. */
    private val tokenCipher = SecureToken()

    /**
     * Preference keys used by the auth DataStore.
     */
    private object Keys {
        val USERNAME = stringPreferencesKey("username")
        val ACCESS_ENCRYPTED = stringPreferencesKey("access_token_encrypted")
        val REFRESH_ENCRYPTED = stringPreferencesKey("refresh_token_encrypted")
        val IS_GUEST = booleanPreferencesKey("is_guest")
    }

    /**
     * Emits the latest stored auth state whenever DataStore changes.
     *
     * Access and refresh tokens are decrypted in memory only when the repository
     * needs to build the current auth state or perform an authorized request.
     */
    val authState: Flow<AuthStateStored> =
        context.authDataStore.data.map { prefs ->
            AuthStateStored(
                username = prefs[Keys.USERNAME],
                accessToken = tokenCipher.decrypt(prefs[Keys.ACCESS_ENCRYPTED]),
                refreshToken = tokenCipher.decrypt(prefs[Keys.REFRESH_ENCRYPTED]),
                isGuest = prefs[Keys.IS_GUEST] ?: false
            )
        }

    /**
     * Stores a logged-in session and clears guest mode.
     */
    suspend fun saveTokens(username: String, access: String, refresh: String) {
        context.authDataStore.edit { prefs ->
            prefs[Keys.USERNAME] = username
            prefs[Keys.ACCESS_ENCRYPTED] = tokenCipher.encrypt(access)
            prefs[Keys.REFRESH_ENCRYPTED] = tokenCipher.encrypt(refresh)
            prefs[Keys.IS_GUEST] = false
        }
    }

    /**
     * Enables guest mode and removes any previously stored credentials.
     */
    suspend fun setGuest() {
        context.authDataStore.edit { prefs ->
            prefs.remove(Keys.USERNAME)
            prefs.remove(Keys.ACCESS_ENCRYPTED)
            prefs.remove(Keys.REFRESH_ENCRYPTED)
            prefs[Keys.IS_GUEST] = true
        }
    }

    /**
     * Clears all authentication data and exits guest mode.
     */
    suspend fun logout() {
        context.authDataStore.edit { prefs ->
            prefs.remove(Keys.USERNAME)
            prefs.remove(Keys.ACCESS_ENCRYPTED)
            prefs.remove(Keys.REFRESH_ENCRYPTED)
            prefs[Keys.IS_GUEST] = false
        }
    }
}
