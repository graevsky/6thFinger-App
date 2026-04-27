package com.example.a6thfingercontrolapp.auth

import com.example.a6thfingercontrolapp.network.BackendApi
import com.example.a6thfingercontrolapp.network.PasswordResetEmailSendIn
import com.example.a6thfingercontrolapp.network.PasswordResetEmailVerifyIn
import com.example.a6thfingercontrolapp.network.PasswordResetFinishIn
import com.example.a6thfingercontrolapp.network.PasswordResetRecoveryVerifyIn
import com.example.a6thfingercontrolapp.network.PasswordResetStartIn
import com.example.a6thfingercontrolapp.network.PasswordResetStartOut
import com.example.a6thfingercontrolapp.security.srp.SrpRegister
import com.example.a6thfingercontrolapp.utils.wrapAuthErrors

/**
 * Password reset and recovery controller.
 */
internal class AuthPasswordResetService(
    private val api: BackendApi
) {
    suspend fun passwordResetStart(username: String): PasswordResetStartOut = wrapAuthErrors {
        api.passwordResetStart(PasswordResetStartIn(username.trim().lowercase()))
    }

    suspend fun passwordResetEmailSend(username: String, email: String) = wrapAuthErrors {
        api.passwordResetEmailSend(
            PasswordResetEmailSendIn(
                username = username.trim().lowercase(),
                email = email.trim().lowercase()
            )
        )
    }

    suspend fun passwordResetEmailVerify(username: String, email: String, code: String): String =
        wrapAuthErrors {
            val result = api.passwordResetEmailVerify(
                PasswordResetEmailVerifyIn(
                    username = username.trim().lowercase(),
                    email = email.trim().lowercase(),
                    code = code.trim()
                )
            )
            result.reset_session_id
        }

    suspend fun passwordResetRecoveryVerify(username: String, recoveryCode: String): String =
        wrapAuthErrors {
            val result = api.passwordResetRecoveryVerify(
                PasswordResetRecoveryVerifyIn(
                    username = username.trim().lowercase(),
                    recovery_code = recoveryCode.trim()
                )
            )
            result.reset_session_id
        }

    suspend fun passwordResetFinish(
        resetSessionId: String,
        username: String,
        newPassword: String
    ) = wrapAuthErrors {
        val params = api.getSrpParams()
        val primeHex = params.N.replace("\\s+".toRegex(), "")
        val generatorHex = params.g.trim()

        val registerPayload = SrpRegister.generateVerifier(
            username = username.trim().lowercase(),
            password = newPassword,
            primeHex = primeHex,
            generatorHex = generatorHex
        )

        api.passwordResetFinish(
            PasswordResetFinishIn(
                reset_session_id = resetSessionId,
                new_salt = registerPayload.saltHex,
                new_verifier = registerPayload.verifierHex
            )
        )
    }
}
