package com.example.a6thfingercontrolapp.security

import android.content.Context
import com.example.a6thfingercontrolapp.BuildConfig
import com.example.a6thfingercontrolapp.data.ClientIdentitySession
import com.example.a6thfingercontrolapp.data.ClientIdentityStore
import com.example.a6thfingercontrolapp.network.ClientAttestationIn
import com.example.a6thfingercontrolapp.network.ClientIdentityApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Coordinates Android Key Attestation bootstrap and storage of the resulting client session.
 */
class ClientAttestationManager(
    context: Context,
    private val store: ClientIdentityStore = ClientIdentityStore(context.applicationContext),
    private val keyStore: ClientIdentityKeyStore = ClientIdentityKeyStore()
) {
    private val attestationMutex = Mutex()

    /**
     * Returns a valid client session or performs a fresh attestation bootstrap when needed.
     */
    suspend fun ensureClientSession(): ClientIdentitySession {
        check(BuildConfig.CLIENT_ATTESTATION_REQUIRED) {
            "Client attestation is disabled for this build"
        }

        return attestationMutex.withLock {
            val cached = store.getClientSession()
            if (cached != null && !cached.isExpiringSoon()) {
                return@withLock cached
            }

            store.clearClientSession()

            val api = ClientIdentityApi.create()
            val challenge = api.getClientChallenge()

            keyStore.recreateAttestedKey(challenge.challenge.toByteArray(Charsets.UTF_8))

            val attestation = api.attestClient(
                ClientAttestationIn(
                    challenge = challenge.challenge,
                    publicKey = keyStore.getPublicKeyBase64(),
                    attestationCertificateChain = keyStore.getCertificateChainBase64(),
                    appVersion = BuildConfig.VERSION_NAME
                )
            )

            store.saveClientSession(
                clientKeyId = attestation.clientKeyId,
                clientSessionToken = attestation.clientSessionToken,
                expiresAt = attestation.expiresAt
            )

            ClientIdentitySession(
                clientKeyId = attestation.clientKeyId,
                clientSessionToken = attestation.clientSessionToken,
                expiresAt = attestation.expiresAt
            )
        }
    }

    /**
     * Clears the stored session and removes the attested key material from Android Keystore.
     */
    suspend fun clearClientIdentity() {
        store.clearClientSession()
        keyStore.deleteKey()
    }
}
