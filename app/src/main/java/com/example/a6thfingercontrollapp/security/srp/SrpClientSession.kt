package com.example.a6thfingercontrollapp.security.srp

import java.math.BigInteger

/**
 * One SRP login attempt on the client side.
 *
 * Responsibilities:
 * - create client ephemeral pair (a, A)
 * - accept server challenge values (salt and B)
 * - derive shared session key K
 * - expose client proof M1 for login/finish request
 */
class SrpClientSession(private val context: SrpContext, privateHex: String? = null) {

    /** Salt as bytes, preserved for proof construction. */
    private var saltBytes: ByteArray? = null

    /** Salt as integer, used for password hash calculation. */
    private var saltInt: BigInteger? = null

    /** Server public ephemeral value B. */
    private var serverPublic: BigInteger? = null

    /** Client private ephemeral value a. */
    private var clientPrivate: BigInteger

    /** Client public ephemeral value A. */
    private var clientPublic: BigInteger

    /** Scrambling parameter u. */
    private var commonSecret: BigInteger? = null

    /** Shared session key K. */
    private var sessionKey: ByteArray? = null

    /** Client proof M1. */
    private var sessionKeyProof: ByteArray? = null

    /** Expected server proof M2, useful if server verification is needed. */
    private var sessionKeyProofHash: ByteArray? = null

    init {
        clientPrivate =
            if (privateHex != null) {
                SrpUtils.intFromHex(privateHex)
            } else {
                context.generateClientPrivate()
            }

        clientPublic = context.getClientPublic(clientPrivate)
    }

    /**
     * Client public value A encoded as hex.
     */
    val publicHex: String
        get() = SrpUtils.hexFromInt(clientPublic)

    /**
     * Client proof M1 encoded as hex.
     */
    val keyProofHex: String
        get() = SrpUtils.hexFromBytes(sessionKeyProof ?: error("Session not initialized"))

    /**
     * Expected server proof M2 encoded as hex.
     */
    val keyProofHashHex: String
        get() = SrpUtils.hexFromBytes(sessionKeyProofHash ?: error("Session not initialized"))

    /**
     * Processes backend challenge and fully initializes the SRP session.
     */
    fun process(serverPublicHex: String, saltHex: String) {
        initBase(saltHex)
        initCommonSecret(serverPublicHex)
        initSessionKey()
        initSessionKeyProof()
    }

    /**
     * Stores salt in both binary and integer forms.
     */
    private fun initBase(saltHex: String) {
        saltBytes = SrpUtils.hexToBytes(saltHex)
        saltInt = SrpUtils.intFromHex(saltHex)
    }

    /**
     * Validates server public value B and calculates u.
     */
    private fun initCommonSecret(serverPublicHex: String) {
        val sp = SrpUtils.intFromHex(serverPublicHex)
        if (sp.mod(context.prime) == BigInteger.ZERO) {
            error("Wrong public provided for SRPClientSession.")
        }
        serverPublic = sp
        commonSecret = context.getCommonSecret(serverPublic = sp, clientPublic = clientPublic)
    }

    /**
     * Calculates password hash, premaster secret and final session key.
     */
    private fun initSessionKey() {
        val saltIntLocal = saltInt ?: error("Salt not initialized")
        val serverPublicLocal = serverPublic ?: error("Server public not initialized")
        val commonSecretLocal = commonSecret ?: error("Common secret not initialized")

        val passwordHash = context.getCommonPasswordHash(saltIntLocal)
        val premaster =
            context.getClientPremasterSecret(
                passwordHash = passwordHash,
                serverPublic = serverPublicLocal,
                clientPrivate = clientPrivate,
                commonSecret = commonSecretLocal
            )
        sessionKey = context.getCommonSessionKey(premaster)
    }

    /**
     * Builds M1 for the client request and M2 for optional server proof checking.
     */
    private fun initSessionKeyProof() {
        val sk = sessionKey ?: error("Session key not initialized")
        val saltBytesLocal = saltBytes ?: error("Salt bytes not initialized")
        val serverPublicLocal = serverPublic ?: error("Server public not initialized")

        val proof =
            context.getCommonSessionKeyProof(
                sessionKey = sk,
                saltBytes = saltBytesLocal,
                serverPublic = serverPublicLocal,
                clientPublic = clientPublic
            )
        sessionKeyProof = proof

        val proofHash =
            context.getCommonSessionKeyProofHash(
                sessionKey = sk,
                sessionKeyProof = proof,
                clientPublic = clientPublic
            )
        sessionKeyProofHash = proofHash
    }
}
