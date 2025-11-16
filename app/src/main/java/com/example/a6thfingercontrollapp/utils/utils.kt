package com.example.a6thfingercontrollapp.utils

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import retrofit2.HttpException

fun parseBackendError(e: Throwable): String {
    if (e is HttpException) {
        val body = e.response()?.errorBody()?.string() ?: return "unknown_error"

        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val adapter = moshi.adapter(Map::class.java)

        val root = adapter.fromJson(body) ?: return "unknown_error"

        if (root["error"] is String) {
            return toErrorKey(root["error"] as String)
        }

        val detail = root["detail"]
        if (detail is Map<*, *> && detail["error"] is String) {
            return toErrorKey(detail["error"] as String)
        }

        if (detail is String) {
            return "unknown_error"
        }
    }

    return "unknown_error"
}

private fun toErrorKey(err: String): String =
    when (err.uppercase()) {
        "USERNAME_TAKEN" -> "username_taken"
        "WRONG_PASSWORD" -> "wrong_password"
        "USER_NOT_FOUND" -> "user_not_found"
        else -> "unknown_error"
    }
