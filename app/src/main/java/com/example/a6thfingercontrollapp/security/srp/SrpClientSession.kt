package com.example.a6thfingercontrollapp.security.srp

import java.math.BigInteger

class SrpClientSession(
    private val context: SrpContext,
    privateHex: String? = null
) {

    private var saltBytes: ByteArray? = null
    private var saltInt: BigInteger? = null

    private var serverPublic: BigInteger? = null
    private var clientPrivate: BigInteger
    private var clientPublic: BigInteger

    private var commonSecret: BigInteger? = null

    private var sessionKey: ByteArray? = null
    private var sessionKeyProof: ByteArray? = null
    private var sessionKeyProofHash: ByteArray? = null

    init {
        clientPrivate = if (privateHex != null) {
            SrpUtils.intFromHex(privateHex)
        } else {
            context.generateClientPrivate()
        }

        clientPublic = context.getClientPublic(clientPrivate)
    }

    val publicHex: String
        get() = SrpUtils.hexFromInt(clientPublic)

    val keyProofHex: String
        get() = SrpUtils.hexFromBytes(sessionKeyProof ?: error("Session not initialized"))

    val keyProofHashHex: String
        get() = SrpUtils.hexFromBytes(sessionKeyProofHash ?: error("Session not initialized"))

    fun process(serverPublicHex: String, saltHex: String) {
        initBase(saltHex)
        initCommonSecret(serverPublicHex)
        initSessionKey()
        initSessionKeyProof()
    }

    private fun initBase(saltHex: String) {
        saltBytes = SrpUtils.hexToBytes(saltHex)
        saltInt = SrpUtils.intFromHex(saltHex)
    }

    private fun initCommonSecret(serverPublicHex: String) {
        val sp = SrpUtils.intFromHex(serverPublicHex)
        if (sp.mod(context.prime) == BigInteger.ZERO) {
            error("Wrong public provided for SRPClientSession.")
        }
        serverPublic = sp
        commonSecret = context.getCommonSecret(
            serverPublic = sp,
            clientPublic = clientPublic
        )
    }

    private fun initSessionKey() {
        val saltIntLocal = saltInt ?: error("Salt not initialized")
        val serverPublicLocal = serverPublic ?: error("Server public not initialized")
        val commonSecretLocal = commonSecret ?: error("Common secret not initialized")

        val passwordHash = context.getCommonPasswordHash(saltIntLocal)
        val premaster = context.getClientPremasterSecret(
            passwordHash = passwordHash,
            serverPublic = serverPublicLocal,
            clientPrivate = clientPrivate,
            commonSecret = commonSecretLocal
        )
        sessionKey = context.getCommonSessionKey(premaster)
    }

    private fun initSessionKeyProof() {
        val sk = sessionKey ?: error("Session key not initialized")
        val saltBytesLocal = saltBytes ?: error("Salt bytes not initialized")
        val serverPublicLocal = serverPublic ?: error("Server public not initialized")

        val proof = context.getCommonSessionKeyProof(
            sessionKey = sk,
            saltBytes = saltBytesLocal,
            serverPublic = serverPublicLocal,
            clientPublic = clientPublic
        )
        sessionKeyProof = proof

        val proofHash = context.getCommonSessionKeyProofHash(
            sessionKey = sk,
            sessionKeyProof = proof,
            clientPublic = clientPublic
        )
        sessionKeyProofHash = proofHash
    }
}
