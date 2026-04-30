package com.example.a6thfingercontrolapp.network

/**
 * Short-lived challenge returned before Android Key Attestation.
 */
data class ClientChallengeOut(
    val challenge: String,
    val expiresAt: String
)

/**
 * Client attestation payload sent after generating an Android Keystore key.
 */
data class ClientAttestationIn(
    val challenge: String,
    val publicKey: String,
    val attestationCertificateChain: List<String>,
    val appVersion: String
)

/**
 * Successful client attestation response used to start a signed client session.
 */
data class ClientAttestationOut(
    val clientKeyId: String,
    val clientSessionToken: String,
    val expiresAt: String
)
