package com.example.a6thfingercontrollapp.security.srp

/**
 * High-level SRP login helper used by AuthRepository.
 *
 * It takes the server challenge from /auth/login/start and returns the values
 * required by /auth/login/finish.
 */
object SrpLogin {

    /**
     * Client-side login values sent to the backend.
     */
    data class LoginResult(val A: String, val M1: String)

    /**
     * Performs the client side of SRP authentication.
     *
     * The password is used locally to derive M1, but is never sent over network.
     */
    fun clientLogin(
        username: String,
        password: String,
        saltHex: String,
        BHex: String,
        primeHex: String,
        generatorHex: String
    ): LoginResult {
        val user = username.trim().lowercase()

        val ctx =
            SrpContext(
                username = user,
                password = password,
                primeHex = primeHex.trim(),
                generatorHex = generatorHex
            )

        val client = SrpClientSession(ctx)
        client.process(BHex, saltHex)

        val A_hex = client.publicHex
        val M1_hex = client.keyProofHex

        return LoginResult(A = A_hex, M1 = M1_hex)
    }
}
