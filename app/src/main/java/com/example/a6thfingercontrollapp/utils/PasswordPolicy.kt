package com.example.a6thfingercontrollapp.utils

object PasswordPolicy {
    const val MIN_LEN = 8

    data class Result(
        val minLen: Boolean,
        val hasUpper: Boolean,
        val hasLower: Boolean,
        val hasDigit: Boolean,
        val hasSpecial: Boolean
    ) {
        val ok: Boolean get() = minLen && hasUpper && hasLower && hasDigit && hasSpecial
    }

    fun check(pw: String): Result {
        val s = pw
        val minLen = s.length >= MIN_LEN
        val hasUpper = s.any { it.isLetter() && it.isUpperCase() }
        val hasLower = s.any { it.isLetter() && it.isLowerCase() }
        val hasDigit = s.any { it.isDigit() }
        val hasSpecial = s.any { !it.isLetterOrDigit() }
        return Result(minLen, hasUpper, hasLower, hasDigit, hasSpecial)
    }
}