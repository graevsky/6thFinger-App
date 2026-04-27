package com.example.a6thfingercontrolapp.ui.passwordreset

/** Steps of the password reset/change controller. */
enum class ResetStep {
    EnterUsername,
    ChooseMethod,
    EnterRecoveryCode,
    EnterEmail,
    EnterEmailCode,
    NewPassword,
    Done
}

private val RECOVERY_CODE_SINGLE_TOKEN = Regex("[\\s,;]+")
private val RECOVERY_CODE_STRICT_FORMAT = Regex("^[A-Z0-9]{4,}(?:-[A-Z0-9]+)*$")

/** Returns whether the primary action button should be enabled for the current step. */
fun isPasswordResetNextEnabled(
    step: ResetStep,
    loading: Boolean,
    username: String,
    recoveryCode: String,
    selectedEmail: String,
    emailCode: String,
    newPass1: String,
    newPass2: String,
    rulesOk: Boolean
): Boolean {
    if (loading) return false

    return when (step) {
        ResetStep.EnterUsername -> username.isNotBlank()
        ResetStep.EnterRecoveryCode -> isValidRecoveryCodeFormat(recoveryCode)
        ResetStep.EnterEmail -> selectedEmail.isNotBlank()
        ResetStep.EnterEmailCode -> emailCode.isNotBlank()
        ResetStep.NewPassword -> newPass1.isNotBlank() && newPass2.isNotBlank() && rulesOk
        ResetStep.Done -> true
        ResetStep.ChooseMethod -> false
    }
}

/** Keeps only one recovery code and strips unsupported characters. */
fun sanitizeRecoveryCodeInput(raw: String): String {
    val firstToken = raw
        .uppercase()
        .replace('—', '-')
        .replace('–', '-')
        .replace('‑', '-')
        .split(RECOVERY_CODE_SINGLE_TOKEN)
        .firstOrNull()
        .orEmpty()

    return firstToken.filter { it.isLetterOrDigit() || it == '-' }
}

/** Recovery code validation. */
fun isValidRecoveryCodeFormat(raw: String): Boolean {
    val normalized = sanitizeRecoveryCodeInput(raw)
    return normalized.isNotBlank() && normalized.matches(RECOVERY_CODE_STRICT_FORMAT)
}
