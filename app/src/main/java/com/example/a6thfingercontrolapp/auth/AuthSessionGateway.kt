package com.example.a6thfingercontrolapp.auth

import com.example.a6thfingercontrolapp.data.AuthStateStored
import com.example.a6thfingercontrolapp.data.AuthStore
import com.example.a6thfingercontrolapp.data.AuthState
import com.example.a6thfingercontrolapp.network.BackendApi
import com.example.a6thfingercontrolapp.network.MeOut
import com.example.a6thfingercontrolapp.network.RefreshTokenIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import retrofit2.Response

/**
 * Internal exception, that is used when stored credentials are dead.
 */
internal class SessionExpiredException : Exception("session_expired")

/**
 * Stored auth state, session validation and authorized request retries controller.
 */
internal class AuthSessionGateway(
    private val api: BackendApi,
    private val store: AuthStore,
    private val stored: Flow<AuthStateStored>,
    private val tokenRefreshMutex: Mutex
) {
    suspend fun resolveInitialState(): AuthState {
        val state = stored.first()
        return when {
            state.isGuest -> AuthState.Guest
            state.accessToken != null && state.username != null && state.refreshToken != null -> {
                try {
                    validateStoredSession()
                } catch (_: Exception) {
                    store.logout()
                    AuthState.Unauthenticated
                }
            }

            else -> AuthState.Unauthenticated
        }
    }

    suspend fun continueAsGuest(): AuthState {
        store.setGuest()
        return AuthState.Guest
    }

    suspend fun currentStoredState(): AuthStateStored = stored.first()

    suspend fun saveTokens(username: String, accessToken: String, refreshToken: String) {
        store.saveTokens(username, accessToken, refreshToken)
    }

    suspend fun clearLocalSession() {
        store.logout()
    }

    suspend fun <T> withAuthorizedRequest(
        block: suspend (authHeader: String) -> T
    ): T {
        val currentState = stored.first()
        val accessToken = currentState.accessToken ?: throw SessionExpiredException()

        return try {
            block("Bearer $accessToken")
        } catch (e: Exception) {
            if (e is HttpException && e.code() == 401) {
                val refreshedAccess = refreshAccessToken(failedAccessToken = accessToken)
                try {
                    block("Bearer $refreshedAccess")
                } catch (retry: Exception) {
                    if (retry is HttpException && retry.code() == 401) {
                        store.logout()
                        throw SessionExpiredException()
                    }
                    throw retry
                }
            } else {
                throw e
            }
        }
    }

    suspend fun <T> withAuthorizedResponse(
        block: suspend (authHeader: String) -> Response<T>
    ): Response<T> {
        val currentState = stored.first()
        val accessToken = currentState.accessToken ?: throw SessionExpiredException()

        val firstResponse = block("Bearer $accessToken")
        if (firstResponse.code() != 401) {
            return firstResponse
        }

        val refreshedAccess = refreshAccessToken(failedAccessToken = accessToken)
        val secondResponse = block("Bearer $refreshedAccess")
        if (secondResponse.code() == 401) {
            store.logout()
            throw SessionExpiredException()
        }

        return secondResponse
    }

    private suspend fun validateStoredSession(): AuthState {
        val storedState = stored.first()
        val refreshToken = storedState.refreshToken ?: throw SessionExpiredException()

        val me = getMeWithRefreshFallback()
        val currentState = stored.first()
        val accessToken = currentState.accessToken ?: throw SessionExpiredException()
        val username = me.username.trim().lowercase()

        store.saveTokens(username, accessToken, refreshToken)
        return AuthState.LoggedIn(username, accessToken, refreshToken)
    }

    private suspend fun getMeWithRefreshFallback(): MeOut {
        val state = stored.first()
        val accessToken = state.accessToken ?: throw SessionExpiredException()

        return try {
            api.getMe("Bearer $accessToken")
        } catch (e: Exception) {
            if (e is HttpException && e.code() == 401) {
                val refreshedAccess = refreshAccessToken(failedAccessToken = accessToken)
                api.getMe("Bearer $refreshedAccess")
            } else {
                throw e
            }
        }
    }

    private suspend fun refreshAccessToken(failedAccessToken: String? = null): String =
        tokenRefreshMutex.withLock {
            val currentState = stored.first()
            val username = currentState.username ?: throw SessionExpiredException()
            val refreshToken = currentState.refreshToken ?: throw SessionExpiredException()

            if (
                failedAccessToken != null &&
                !currentState.accessToken.isNullOrBlank() &&
                currentState.accessToken != failedAccessToken
            ) {
                return currentState.accessToken
            }

            return try {
                val out = api.refreshToken(RefreshTokenIn(refreshToken))
                store.saveTokens(username, out.access_token, refreshToken)
                out.access_token
            } catch (_: Exception) {
                store.logout()
                throw SessionExpiredException()
            }
        }
}
