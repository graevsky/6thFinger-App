package com.example.a6thfingercontrolapp.auth

import com.example.a6thfingercontrolapp.data.AuthState
import com.example.a6thfingercontrolapp.network.BackendApi
import com.example.a6thfingercontrolapp.network.LoginFinishIn
import com.example.a6thfingercontrolapp.network.LoginStartIn
import com.example.a6thfingercontrolapp.network.RegisterIn
import com.example.a6thfingercontrolapp.security.srp.SrpLogin
import com.example.a6thfingercontrolapp.security.srp.SrpRegister
import com.example.a6thfingercontrolapp.utils.wrapAuthErrors

/**
 * Registration, login and logout flows controller.
 */
internal class AuthCredentialsService(
    private val api: BackendApi,
    private val sessionGateway: AuthSessionGateway
) {
    suspend fun register(username: String, password: String): List<String> = wrapAuthErrors {
        val normalized = username.trim().lowercase()

        val params = api.getSrpParams()
        val primeHex = params.N.replace("\\s+".toRegex(), "")
        val generatorHex = params.g.trim()

        val registerPayload = SrpRegister.generateVerifier(
            username = normalized,
            password = password,
            primeHex = primeHex,
            generatorHex = generatorHex
        )

        val result = api.register(
            RegisterIn(
                username = normalized,
                salt = registerPayload.saltHex,
                verifier = registerPayload.verifierHex
            )
        )

        result.recovery_codes
    }

    suspend fun login(username: String, password: String): AuthState = wrapAuthErrors {
        val normalized = username.trim().lowercase()
        val start = api.loginStart(LoginStartIn(normalized))

        val primeHex = start.N.replace("\\s+".toRegex(), "")
        val generatorHex = start.g.trim()

        val loginProof = SrpLogin.clientLogin(
            username = normalized,
            password = password,
            saltHex = start.salt,
            BHex = start.B,
            primeHex = primeHex,
            generatorHex = generatorHex
        )

        val finish = api.loginFinish(
            LoginFinishIn(
                username = normalized,
                A = loginProof.A,
                M1 = loginProof.M1,
                salt = start.salt
            )
        )

        sessionGateway.saveTokens(normalized, finish.access_token, finish.refresh_token)
        AuthState.LoggedIn(normalized, finish.access_token, finish.refresh_token)
    }

    suspend fun logout(): AuthState {
        val currentState = sessionGateway.currentStoredState()

        if (
            currentState.username != null &&
            currentState.accessToken != null &&
            currentState.refreshToken != null
        ) {
            runCatching {
                sessionGateway.withAuthorizedRequest { auth ->
                    api.logout(auth)
                }
            }
        }

        sessionGateway.clearLocalSession()
        return AuthState.Unauthenticated
    }
}
