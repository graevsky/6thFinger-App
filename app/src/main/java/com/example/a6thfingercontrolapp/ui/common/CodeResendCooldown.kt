package com.example.a6thfingercontrolapp.ui.common

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlin.math.ceil

internal const val DEFAULT_CODE_RESEND_COOLDOWN_SECONDS = 60

/**
 * Email codes cooldown dialog.
 */
@Composable
internal fun rememberCooldownRemainingSeconds(cooldownUntilElapsedMs: Long): Int {
    var remainingSeconds by remember(cooldownUntilElapsedMs) {
        mutableStateOf(calculateRemainingSeconds(cooldownUntilElapsedMs))
    }

    LaunchedEffect(cooldownUntilElapsedMs) {
        while (true) {
            val next = calculateRemainingSeconds(cooldownUntilElapsedMs)
            remainingSeconds = next
            if (next <= 0) break
            delay(250)
        }
    }

    return remainingSeconds
}

internal fun nextCooldownDeadline(
    seconds: Int = DEFAULT_CODE_RESEND_COOLDOWN_SECONDS
): Long = SystemClock.elapsedRealtime() + seconds * 1_000L

private fun calculateRemainingSeconds(cooldownUntilElapsedMs: Long): Int {
    val remainingMs = cooldownUntilElapsedMs - SystemClock.elapsedRealtime()
    if (remainingMs <= 0L) return 0
    return ceil(remainingMs / 1_000.0).toInt()
}
