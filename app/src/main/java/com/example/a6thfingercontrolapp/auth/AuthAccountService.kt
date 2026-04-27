package com.example.a6thfingercontrolapp.auth

import android.content.Context
import com.example.a6thfingercontrolapp.data.avatarFile
import com.example.a6thfingercontrolapp.network.BackendApi
import com.example.a6thfingercontrolapp.network.EmailConfirmIn
import com.example.a6thfingercontrolapp.network.EmailRemoveConfirmIn
import com.example.a6thfingercontrolapp.network.EmailStartAddIn
import com.example.a6thfingercontrolapp.utils.wrapAuthErrors
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

/**
 * Avatar management, email coordination.
 */
internal class AuthAccountService(
    private val api: BackendApi,
    private val sessionGateway: AuthSessionGateway,
    private val appContext: Context
) {
    suspend fun uploadAvatar(localPath: String) = wrapAuthErrors {
        val avatar = File(localPath)
        if (!avatar.exists()) throw Exception("Avatar file not found")

        val body = avatar.asRequestBody("image/jpeg".toMediaType())
        val part = MultipartBody.Part.createFormData("file", avatar.name, body)

        sessionGateway.withAuthorizedRequest { auth ->
            api.uploadAvatar(auth, part)
        }
    }

    suspend fun downloadAvatarToLocal(): String? = wrapAuthErrors {
        val response = sessionGateway.withAuthorizedResponse { auth ->
            api.downloadAvatar(auth)
        }

        if (response.code() == 404) return@wrapAuthErrors null
        if (!response.isSuccessful) {
            throw Exception("Avatar download failed (${response.code()})")
        }

        val bytes = response.body()?.bytes() ?: return@wrapAuthErrors null
        val outFile = avatarFile(appContext)
        outFile.parentFile?.mkdirs()
        outFile.writeBytes(bytes)
        outFile.absolutePath
    }

    suspend fun deleteAvatarRemote() = wrapAuthErrors {
        val response = sessionGateway.withAuthorizedResponse { auth ->
            api.deleteAvatar(auth)
        }

        if (!response.isSuccessful && response.code() != 404) {
            throw Exception("Avatar delete failed (${response.code()})")
        }
    }

    suspend fun emailStartAdd(email: String) = wrapAuthErrors {
        sessionGateway.withAuthorizedRequest { auth ->
            api.emailStartAdd(auth, EmailStartAddIn(email.trim()))
        }
    }

    suspend fun emailConfirmAdd(email: String, code: String) = wrapAuthErrors {
        sessionGateway.withAuthorizedRequest { auth ->
            api.emailConfirmAdd(auth, EmailConfirmIn(email.trim(), code.trim()))
        }
    }

    suspend fun emailStartRemove() = wrapAuthErrors {
        sessionGateway.withAuthorizedRequest { auth ->
            api.emailStartRemove(auth)
        }
    }

    suspend fun emailConfirmRemove(code: String?, recoveryCode: String?) = wrapAuthErrors {
        sessionGateway.withAuthorizedRequest { auth ->
            api.emailConfirmRemove(
                auth,
                EmailRemoveConfirmIn(
                    code = code?.trim()?.takeIf { it.isNotBlank() },
                    recovery_code = recoveryCode?.trim()?.takeIf { it.isNotBlank() }
                )
            )
        }
    }
}
