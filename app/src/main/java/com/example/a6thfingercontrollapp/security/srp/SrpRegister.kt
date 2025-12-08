package com.example.a6thfingercontrollapp.security.srp

import java.math.BigInteger
import java.security.SecureRandom

object SrpRegister {

    data class RegistrationResult(val saltHex: String, val verifierHex: String)

    private val random = SecureRandom()

    fun generateSaltHex(): String {
        val buf = ByteArray(16)
        random.nextBytes(buf)
        return SrpUtils.hexFromBytes(buf)
    }

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
