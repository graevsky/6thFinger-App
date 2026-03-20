package com.example.a6thfingercontrollapp.utils

fun maskEmail(email: String): String {
    val e = email.trim()
    val at = e.indexOf('@')
    if (at <= 0 || at == e.length - 1) return "********"
    val local = e.substring(0, at)
    val domain = e.substring(at + 1)

    val first = local.first().toString()
    val starsCount = (local.length - 1).coerceAtLeast(7)
    val stars = "*".repeat(starsCount)

    return "$first$stars@$domain"
}