package com.example.a6thfingercontrolapp.security

import android.util.Base64

/**
 * Request-signing facade used by the network layer once a client session exists.
 */
class ClientRequestSigner(
    private val keyStore: ClientIdentityKeyStore = ClientIdentityKeyStore()
) {
    /**
     * Signs the canonical string and returns a base64url-encoded ECDSA signature.
     */
    fun signBase64Url(canonical: String): String {
        val signature = keyStore.signCanonicalString(canonical)
        return Base64.encodeToString(
            signature,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
    }
}
