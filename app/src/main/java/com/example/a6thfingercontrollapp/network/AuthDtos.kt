package com.example.a6thfingercontrollapp.network

data class RegisterParamsOut(val N: String, val g: String)

data class RegisterIn(val username: String, val salt: String, val verifier: String)

data class RegisterOut(
    val detail: String,
    val recovery_codes: List<String>
)

data class LoginStartIn(val username: String)

data class LoginStartOut(val salt: String, val B: String, val N: String, val g: String)

data class LoginFinishIn(
    val username: String,
    val A: String,
    val M1: String,
    val salt: String,
    val device_label: String? = null
)

data class LoginFinishOut(val M2: String, val access_token: String, val refresh_token: String)

data class RefreshTokenIn(val refresh_token: String)

data class RefreshTokenOut(val access_token: String)

data class MeOut(
    val id: String,
    val username: String
)

data class AppSettingsOut(
    val id: String,
    val user_id: String,
    val payload: Map<String, Any?>,
    val updated_at: String
)

data class AppSettingsIn(val payload: Map<String, Any?>)

data class DeviceCreate(val address: String, val alias: String?)

data class DeviceUpdate(val alias: String?)

data class DeviceOut(
    val id: String,
    val owner_id: String,
    val address: String,
    val alias: String?,
    val created_at: String
)

data class DeviceSettingsIn(val payload: Map<String, Any?>)

data class DeviceSettingsOut(
    val id: String,
    val device_id: String,
    val version: Int,
    val payload: Map<String, Any?>,
    val updated_at: String
)

data class AvatarOut(
    val key: String,
    val content_type: String
)

data class EmailStartAddIn(val email: String)
data class EmailConfirmIn(val email: String, val code: String)

data class EmailRemoveConfirmIn(
    val code: String? = null,
    val recovery_code: String? = null
)

data class PasswordResetStartIn(val username: String)

data class PasswordResetStartOut(
    val has_email: Boolean,
    val email: String? = null,
    val has_recovery: Boolean
)

data class PasswordResetEmailSendIn(val username: String, val email: String)

data class PasswordResetEmailVerifyIn(val username: String, val email: String, val code: String)

data class PasswordResetRecoveryVerifyIn(val username: String, val recovery_code: String)

data class PasswordResetVerifyOut(val reset_session_id: String)

data class PasswordResetFinishIn(
    val reset_session_id: String,
    val new_salt: String,
    val new_verifier: String
)

data class GenericOk(val detail: String)