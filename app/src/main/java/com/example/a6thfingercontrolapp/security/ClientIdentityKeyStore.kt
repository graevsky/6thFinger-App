package com.example.a6thfingercontrolapp.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec

/**
 * Android Keystore helper dedicated to official client identity attestation and request signing.
 */
class ClientIdentityKeyStore(
    private val keyAlias: String = DEFAULT_KEY_ALIAS
) {
    companion object {
        const val DEFAULT_KEY_ALIAS = "sixthfinger_client_identity_v1"
    }

    private val keyStoreName = "AndroidKeyStore"
    private val ecCurveName = "secp256r1"
    private val signAlgorithm = "SHA256withECDSA"

    /**
     * Deletes a previously generated client identity key, if one exists.
     */
    fun deleteKey() {
        val keyStore = KeyStore.getInstance(keyStoreName).apply { load(null) }
        if (keyStore.containsAlias(keyAlias)) {
            keyStore.deleteEntry(keyAlias)
        }
    }

    /**
     * Recreates the attested EC signing key for a new server challenge.
     */
    fun recreateAttestedKey(challenge: ByteArray) {
        deleteKey()

        val generator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            keyStoreName
        )

        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec(ecCurveName))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setAttestationChallenge(challenge)
            .setUserAuthenticationRequired(false)
            .build()

        generator.initialize(spec)
        generator.generateKeyPair()
    }

    /**
     * Returns the certificate chain produced by Android Key Attestation as base64 DER blobs.
     */
    fun getCertificateChainBase64(): List<String> {
        val keyStore = KeyStore.getInstance(keyStoreName).apply { load(null) }
        val chain = keyStore.getCertificateChain(keyAlias)
            ?.mapNotNull { it as? X509Certificate }
            .orEmpty()

        if (chain.isEmpty()) {
            throw IllegalStateException("Client attestation certificate chain is missing")
        }

        return chain.map { cert ->
            Base64.encodeToString(cert.encoded, Base64.NO_WRAP)
        }
    }

    /**
     * Returns the generated public key encoded as DER base64.
     */
    fun getPublicKeyBase64(): String {
        val keyStore = KeyStore.getInstance(keyStoreName).apply { load(null) }
        val cert = keyStore.getCertificate(keyAlias)
            ?: throw IllegalStateException("Client attestation certificate is missing")

        return Base64.encodeToString(cert.publicKey.encoded, Base64.NO_WRAP)
    }

    /**
     * Signs a canonical request string with the attested private key.
     */
    fun signCanonicalString(canonical: String): ByteArray {
        val keyStore = KeyStore.getInstance(keyStoreName).apply { load(null) }
        val privateKey = keyStore.getKey(keyAlias, null) as? PrivateKey
            ?: throw IllegalStateException("Client attestation private key is missing")

        return Signature.getInstance(signAlgorithm).run {
            initSign(privateKey)
            update(canonical.toByteArray(Charsets.UTF_8))
            sign()
        }
    }
}
