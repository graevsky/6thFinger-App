package com.example.a6thfingercontrollapp.security.srp

object SrpLogin {

    data class LoginResult(
        val A: String,
        val M1: String
    )

    fun clientLogin(
        username: String,
        password: String,
        saltHex: String,
        BHex: String,
        primeHex: String,
        generatorHex: String
    ): LoginResult {
        val user = username.trim().lowercase()

        val ctx = SrpContext(
            username = user,
            password = password,
            primeHex = primeHex.trim(),
            generatorHex = generatorHex
        )

        val client = SrpClientSession(ctx)
        client.process(BHex, saltHex)

        val A_hex = client.publicHex
        val M1_hex = client.keyProofHex

        return LoginResult(
            A = A_hex,
            M1 = M1_hex
        )
    }
}
