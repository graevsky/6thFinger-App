package com.example.a6thfingercontrollapp.data

import android.content.Context
import com.example.a6thfingercontrollapp.network.AppSettingsIn
import com.example.a6thfingercontrollapp.network.BackendApi
import com.example.a6thfingercontrollapp.network.LoginFinishIn
import com.example.a6thfingercontrollapp.network.LoginStartIn
import com.example.a6thfingercontrollapp.network.RegisterIn
import com.example.a6thfingercontrollapp.security.srp.SrpLogin
import com.example.a6thfingercontrollapp.security.srp.SrpRegister
import com.example.a6thfingercontrollapp.utils.parseBackendError
import kotlinx.coroutines.flow.first

sealed class AuthState {
    object Unauthenticated : AuthState()
    object Guest : AuthState()
    data class LoggedIn(val username: String, val accessToken: String, val refreshToken: String) :
        AuthState()
}

class AuthRepository(context: Context) {

    private val api = BackendApi.create()
    private val store = AuthStore(context.applicationContext)

    val stored = store.authState

    suspend fun resolveInitialState(): AuthState {
        val s = stored.first()
        return when {
            s.isGuest -> AuthState.Guest
            s.accessToken != null && s.username != null && s.refreshToken != null ->
                AuthState.LoggedIn(s.username, s.accessToken, s.refreshToken)

            else -> AuthState.Unauthenticated
        }
    }

    suspend fun continueAsGuest(): AuthState {
        store.setGuest()
        return AuthState.Guest
    }

    suspend fun register(username: String, password: String) {
        try {
            val normalized = username.trim().lowercase()

            val params = api.getSrpParams()
            val Nhex = params.N.replace("\\s+".toRegex(), "")
            val ghex = params.g.trim()

            val reg = SrpRegister.generateVerifier(
                username = normalized,
                password = password,
                primeHex = Nhex,
                generatorHex = ghex
            )

            api.register(
                RegisterIn(
                    username = normalized,
                    salt = reg.saltHex,
                    verifier = reg.verifierHex
                )
            )
        } catch (e: Exception) {
            throw Exception(parseBackendError(e))
        }
    }


    suspend fun login(username: String, password: String): AuthState {
        try {
            val normalized = username.trim().lowercase()

            val start = api.loginStart(LoginStartIn(normalized))

            val Nhex = start.N.replace("\\s+".toRegex(), "")
            val ghex = start.g.trim()

            val loginRes = SrpLogin.clientLogin(
                username = normalized,
                password = password,
                saltHex = start.salt,
                BHex = start.B,
                primeHex = Nhex,
                generatorHex = ghex
            )

            val finish = api.loginFinish(
                LoginFinishIn(
                    username = normalized,
                    A = loginRes.A,
                    M1 = loginRes.M1,
                    salt = start.salt
                )
            )

            store.saveTokens(normalized, finish.access_token, finish.refresh_token)
            return AuthState.LoggedIn(normalized, finish.access_token, finish.refresh_token)
        } catch (e: Exception) {
            throw Exception(parseBackendError(e))
        }
    }

    suspend fun logout(): AuthState {
        store.logout()
        return AuthState.Unauthenticated
    }


    suspend fun pullAppSettings(): Map<String, Any?>? {
        val s = stored.first()
        val token = s.accessToken ?: return null
        val res = api.getAppSettings("Bearer $token")
        return res.payload
    }

    suspend fun pushAppSettings(payload: Map<String, Any?>) {
        val s = stored.first()
        val token = s.accessToken ?: return
        api.putAppSettings("Bearer $token", AppSettingsIn(payload))
    }
}
