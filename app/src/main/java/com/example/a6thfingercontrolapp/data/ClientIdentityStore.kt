package com.example.a6thfingercontrolapp.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.a6thfingercontrolapp.security.SecureToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * DataStore instance dedicated to official client identity session.
 */
private val Context.clientIdentityDataStore by preferencesDataStore(name = "client_identity_store")

/**
 * Raw client identity state restored from local storage.
 */
data class ClientIdentityStored(
    val clientKeyId: String?,
    val clientSessionToken: String?,
    val clientSessionExpiresAt: String?
)

/**
 * Valid client session used to authorize request signing.
 */
data class ClientIdentitySession(
    val clientKeyId: String,
    val clientSessionToken: String,
    val expiresAt: String
) {
    /**
     * Whether the stored session is missing a safe validity margin and should be refreshed.
     */
    fun isExpiringSoon(now: Instant = Instant.now(), leewaySeconds: Long = 60): Boolean {
        val expiry = runCatching { Instant.parse(expiresAt) }.getOrNull() ?: return true
        return !expiry.isAfter(now.plusSeconds(leewaySeconds))
    }
}

/**
 * Local persistence for the attested client session.
 */
class ClientIdentityStore(private val context: Context) {

    /** Keystore-backed helper used to encrypt the client session token. */
    private val tokenCipher = SecureToken("sixth_finger_client_identity_tokens_v1")

    /**
     * Preference keys used by the client identity DataStore.
     */
    private object Keys {
        val CLIENT_KEY_ID = stringPreferencesKey("client_key_id")
        val CLIENT_SESSION_ENCRYPTED = stringPreferencesKey("client_session_encrypted")
        val CLIENT_SESSION_EXPIRES_AT = stringPreferencesKey("client_session_expires_at")
    }

    /**
     * Emits the latest stored client identity state whenever DataStore changes.
     */
    val clientIdentity: Flow<ClientIdentityStored> =
        context.clientIdentityDataStore.data.map { prefs ->
            ClientIdentityStored(
                clientKeyId = prefs[Keys.CLIENT_KEY_ID],
                clientSessionToken = tokenCipher.decrypt(prefs[Keys.CLIENT_SESSION_ENCRYPTED]),
                clientSessionExpiresAt = prefs[Keys.CLIENT_SESSION_EXPIRES_AT]
            )
        }

    /**
     * Returns the latest stored client identity snapshot.
     */
    suspend fun read(): ClientIdentityStored =
        clientIdentity.first()

    /**
     * Stores the attested client key id, short-lived session token and expiration timestamp.
     */
    suspend fun saveClientSession(
        clientKeyId: String,
        clientSessionToken: String,
        expiresAt: String
    ) {
        context.clientIdentityDataStore.edit { prefs ->
            prefs[Keys.CLIENT_KEY_ID] = clientKeyId
            prefs[Keys.CLIENT_SESSION_ENCRYPTED] = tokenCipher.encrypt(clientSessionToken)
            prefs[Keys.CLIENT_SESSION_EXPIRES_AT] = expiresAt
        }
    }

    /**
     * Returns the stored client session when all required fields are present.
     */
    suspend fun getClientSession(): ClientIdentitySession? {
        val current = read()
        val keyId = current.clientKeyId?.trim().orEmpty()
        val sessionToken = current.clientSessionToken?.trim().orEmpty()
        val expiresAt = current.clientSessionExpiresAt?.trim().orEmpty()

        if (keyId.isBlank() || sessionToken.isBlank() || expiresAt.isBlank()) {
            return null
        }

        return ClientIdentitySession(
            clientKeyId = keyId,
            clientSessionToken = sessionToken,
            expiresAt = expiresAt
        )
    }

    /**
     * Clears only attestation-derived client session values.
     */
    suspend fun clearClientSession() {
        context.clientIdentityDataStore.edit { prefs ->
            prefs.remove(Keys.CLIENT_KEY_ID)
            prefs.remove(Keys.CLIENT_SESSION_ENCRYPTED)
            prefs.remove(Keys.CLIENT_SESSION_EXPIRES_AT)
        }
    }
}
