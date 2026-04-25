package com.example.a6thfingercontrollapp.security.srp

import java.math.BigInteger
import java.security.SecureRandom

/**
 * SRP registration helper.
 */
object SrpRegister {

    /**
     * Registration payload produced by the client.
     */
    data class RegistrationResult(val saltHex: String, val verifierHex: String)

    /** Secure random source for salt generation. */
    private val random = SecureRandom()

    /**
     * Generates a random 128-bit salt encoded as hex.
     */
    fun generateSaltHex(): String {
        val buf = ByteArray(16)
        random.nextBytes(buf)
        return SrpUtils.hexFromBytes(buf)
    }

    /**
     * Generates SRP verifier for a new account.
     *
     * Output:
     * - saltHex is stored with the account
     * - verifierHex is stored instead of the password
     */
    fun generateVerifier(
        username: String,
        password: String,
        primeHex: String,
        generatorHex: String
    ): RegistrationResult {
        val user = username.trim().lowercase()

        val saltHex = generateSaltHex()
        val saltInt = BigInteger(saltHex, 16)

        val ctx = SrpContext(user, password, primeHex, generatorHex)

        val x = ctx.getCommonPasswordHash(saltInt)
        val v = ctx.getCommonPasswordVerifier(x)

        val verifierHex = v.toString(16).lowercase()

        return RegistrationResult(saltHex = saltHex, verifierHex = verifierHex)
    }
}
