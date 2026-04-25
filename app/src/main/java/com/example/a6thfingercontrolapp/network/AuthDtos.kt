package com.example.a6thfingercontrolapp.network

/**
 * SRP parameters returned by the backend.
 *
 * N is the large safe prime and g is the generator used by the SRP protocol.
 */
data class RegisterParamsOut(val N: String, val g: String)

/**
 * Registration payload.
 *
 * The raw password is never sent to the backend. Instead, the app sends
 * a salt and verifier generated on the device.
 */
data class RegisterIn(val username: String, val salt: String, val verifier: String)

/**
 * Registration response containing one-time recovery codes list.
 */
data class RegisterOut(
    val detail: String,
    val recovery_codes: List<String>
)

/**
 * First step of SRP login: identify the account.
 */
data class LoginStartIn(val username: String)

/**
 * Server challenge for SRP login.
 *
 * Contains the user's salt, server public value B and SRP parameters.
 */
data class LoginStartOut(val salt: String, val B: String, val N: String, val g: String)

/**
 * Final SRP login request.
 *
 * A is the client public value and M1 is the client proof.
 */
data class LoginFinishIn(
    val username: String,
    val A: String,
    val M1: String,
    val salt: String,
    val device_label: String? = null
)

/**
 * Successful login response.
 *
 * M2 is the server proof. Access token is used for API calls, refresh token
 * is used to renew the session.
 */
data class LoginFinishOut(val M2: String, val access_token: String, val refresh_token: String)

/**
 * Refresh token request body.
 */
data class RefreshTokenIn(val refresh_token: String)

/**
 * Access token returned after refresh.
 */
data class RefreshTokenOut(val access_token: String)

/**
 * Basic profile data for the authenticated user.
 */
data class MeOut(
    val id: String,
    val username: String
)

/**
 * Cloud-stored application settings for a user.
 */
data class AppSettingsOut(
    val id: String,
    val user_id: String,
    val payload: Map<String, Any?>,
    val updated_at: String
)

/**
 * Application settings payload sent to the backend.
 */
data class AppSettingsIn(val payload: Map<String, Any?>)

/**
 * Request body for registering a BLE device in the cloud.
 */
data class DeviceCreate(val address: String, val alias: String?)

/**
 * Request body for changing device metadata.
 */
data class DeviceUpdate(val alias: String?)

/**
 * Device record returned by the backend.
 */
data class DeviceOut(
    val id: String,
    val owner_id: String,
    val address: String,
    val alias: String?,
    val created_at: String
)

/**
 * Device settings payload sent to the backend.
 */
data class DeviceSettingsIn(val payload: Map<String, Any?>)

/**
 * Versioned device settings snapshot returned by the backend.
 */
data class DeviceSettingsOut(
    val id: String,
    val device_id: String,
    val version: Int,
    val payload: Map<String, Any?>,
    val updated_at: String
)

/**
 * Avatar metadata returned after successful upload.
 */
data class AvatarOut(
    val key: String,
    val content_type: String
)

/**
 * Starts adding an email to the account.
 */
data class EmailStartAddIn(val email: String)

/**
 * Confirms email ownership with a verification code.
 */
data class EmailConfirmIn(val email: String, val code: String)

/**
 * Confirms email removal.
 *
 * Either email code or recovery code can be used.
 */
data class EmailRemoveConfirmIn(
    val code: String? = null,
    val recovery_code: String? = null
)

/**
 * Starts password reset for a username.
 */
data class PasswordResetStartIn(val username: String)

/**
 * Password reset method information returned by the backend.
 */
data class PasswordResetStartOut(
    val has_email: Boolean,
    val email: String? = null,
    val has_recovery: Boolean
)

/**
 * Sends password reset code to an email.
 */
data class PasswordResetEmailSendIn(val username: String, val email: String)

/**
 * Verifies password reset email code.
 */
data class PasswordResetEmailVerifyIn(val username: String, val email: String, val code: String)

/**
 * Verifies password reset recovery code.
 */
data class PasswordResetRecoveryVerifyIn(val username: String, val recovery_code: String)

/**
 * Backend-issued reset session id used to finish password reset.
 */
data class PasswordResetVerifyOut(val reset_session_id: String)

/**
 * Final password reset payload.
 *
 * The app sends a new SRP salt and verifier.
 */
data class PasswordResetFinishIn(
    val reset_session_id: String,
    val new_salt: String,
    val new_verifier: String
)

/**
 * Generic success response used by simple backend endpoints.
 */
data class GenericOk(val detail: String)