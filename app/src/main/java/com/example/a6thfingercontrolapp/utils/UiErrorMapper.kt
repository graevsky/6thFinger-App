package com.example.a6thfingercontrolapp.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.a6thfingercontrolapp.R

/**
 * Converts internal error keys into localized text for Compose UI.
 */
@Composable
fun uiErrorText(raw: String?): String? {
    val k = raw?.trim()?.lowercase()?.replace("\n", "")
    if (k.isNullOrBlank()) return null

    return when {
        k == "username_taken" || k.startsWith("username_taken") -> stringResource(R.string.err_username_taken)
        k == "wrong_password" || k == "invalid_username_or_password" -> stringResource(R.string.err_wrong_password)
        k == "user_not_found" || k == "not_found" -> stringResource(R.string.err_user_not_found)

        k == "email_in_use" -> stringResource(R.string.err_email_in_use)
        k == "email_disabled" -> stringResource(R.string.err_email_disabled)
        k == "email_mismatch" -> stringResource(R.string.err_email_mismatch)
        k == "email_not_set" -> stringResource(R.string.err_email_not_set)

        k == "wrong_code" -> stringResource(R.string.err_wrong_code)
        k == "code_expired" -> stringResource(R.string.err_code_expired)
        k == "no_pending_code" -> stringResource(R.string.err_no_pending_code)
        k == "too_many_requests" -> stringResource(R.string.err_too_many_requests)

        k == "passwords_mismatch" -> stringResource(R.string.password_reset_passwords_mismatch)
        k == "no_reset_session" -> stringResource(R.string.password_reset_no_reset_session)
        k == "password_rules_invalid" -> stringResource(R.string.err_password_rules_invalid)

        k == "network_error" -> stringResource(R.string.err_network)

        k.startsWith("http_") -> stringResource(R.string.err_unknown)
        else -> stringResource(R.string.err_unknown)
    }
}

/**
 * Helper used by retry loops to detect errors caused by temporary connectivity loss.
 */
fun isNetworkErrorKey(raw: String?): Boolean =
    raw?.trim()?.lowercase()?.startsWith("network_error") == true
