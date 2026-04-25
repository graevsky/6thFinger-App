package com.example.a6thfingercontrollapp.utils

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException


/**
 * Converts low-level Retrofit, HTTP and network exceptions into stable error keys.
 *
 * UI code should not depend on raw exception messages, so this mapper normalizes
 * backend failures into short string identifiers used by UiErrorMapper.
 */
fun parseBackendError(e: Throwable): String {
    // Network-related exceptions are grouped into one key for offline/retry UI.
    if (e is UnknownHostException || e is ConnectException || e is SocketTimeoutException) {
        return "network_error"
    }
    if (e is IOException) {
        return "network_error"
    }

    if (e is HttpException) {
        val code = e.code()
        val body = runCatching { e.response()?.errorBody()?.string().orEmpty() }.getOrDefault("")
        val lc = body.lowercase()

        // Prefer structured backend error keys when the response body contains them.
        parseJsonErrorKey(body)?.let { jsonKey ->
            return jsonKey
        }

        // Fallback mapping for older/plain-text backend responses.
        if (code == 409) {
            if ("email already in use" in lc || ("email" in lc && "use" in lc)) return "email_in_use"
            if ("username" in lc && ("taken" in lc || "exists" in lc)) return "username_taken"
            return "conflict"
        }

        if (code == 429) return "too_many_requests"

        if (code == 400) {
            if ("no pending code" in lc) return "no_pending_code"
            if ("code expired" in lc) return "code_expired"
            if ("wrong code" in lc) return "wrong_code"
            if ("email mismatch" in lc) return "email_mismatch"
            if ("email not set" in lc) return "email_not_set"
            return "bad_request"
        }

        if (code == 401) return "wrong_password"

        if (code == 404) {
            if ("user not found" in lc) return "user_not_found"
            return "not_found"
        }

        return "http_$code"
    }

    return e.message?.takeIf { it.isNotBlank() } ?: "unknown_error"
}

/**
 * Extracts backend-provided error fields from JSON response bodies.
 */
private fun parseJsonErrorKey(body: String): String? {
    if (body.isBlank()) return null

    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    val adapter = moshi.adapter(Map::class.java)
    val root = runCatching { adapter.fromJson(body) as? Map<*, *> }.getOrNull() ?: return null

    val direct = root["error"] as? String
    if (!direct.isNullOrBlank()) return toErrorKey(direct)

    val detail = root["detail"]
    if (detail is Map<*, *>) {
        val inner = detail["error"] as? String
        if (!inner.isNullOrBlank()) return toErrorKey(inner)
    }

    return null
}

/**
 * Maps backend enum-style error names to app-level lowercase keys.
 */
private fun toErrorKey(err: String): String =
    when (err.trim().uppercase()) {
        "USERNAME_TAKEN" -> "username_taken"
        "WRONG_PASSWORD" -> "wrong_password"
        "USER_NOT_FOUND" -> "user_not_found"

        "EMAIL_IN_USE", "EMAIL_ALREADY_IN_USE" -> "email_in_use"
        "TOO_MANY_REQUESTS" -> "too_many_requests"
        "CODE_EXPIRED" -> "code_expired"
        "WRONG_CODE" -> "wrong_code"
        else -> "unknown_error"
    }