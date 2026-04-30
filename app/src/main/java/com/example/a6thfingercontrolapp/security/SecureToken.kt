package com.example.a6thfingercontrolapp.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Small Android Keystore based cipher for locally stored auth tokens.
 */
class SecureToken(
    /** Keystore alias dedicated to encrypted local token blobs. */
    private val keyAlias: String = "sixth_finger_auth_tokens_v1"
) {

    /** Android Keystore provider name. */
    private val keyStoreName = "AndroidKeyStore"

    /** Stable prefix used to recognize encrypted token values. */
    private val valuePrefix = "v1:"

    /** AES-GCM authentication tag length in bits. */
    private val gcmTagBits = 128

    /** AES-GCM standard IV length in bytes. */
    private val ivLengthBytes = 12

    /** Synchronizes lazy key creation. */
    private val keyLock = Any()

    /**
     * Encrypts a non-empty token string for DataStore persistence.
     */
    fun encrypt(plainText: String): String {
        val key = getOrCreateSecretKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)

        val iv = cipher.iv
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        val packed = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, packed, 0, iv.size)
        System.arraycopy(encrypted, 0, packed, iv.size, encrypted.size)

        return valuePrefix + Base64.encodeToString(packed, Base64.NO_WRAP)
    }

    /**
     * Decrypts a token blob previously produced by [encrypt].
     */
    fun decrypt(storedValue: String?): String? {
        val raw = storedValue?.trim()?.takeIf { it.isNotBlank() } ?: return null
        if (!raw.startsWith(valuePrefix)) return null

        return try {
            val packed = Base64.decode(raw.removePrefix(valuePrefix), Base64.NO_WRAP)
            if (packed.size <= ivLengthBytes) return null

            val iv = packed.copyOfRange(0, ivLengthBytes)
            val encrypted = packed.copyOfRange(ivLengthBytes, packed.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateSecretKey(),
                GCMParameterSpec(gcmTagBits, iv)
            )

            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Loads the existing secret key or creates a new one in Android Keystore.
     */
    private fun getOrCreateSecretKey(): SecretKey = synchronized(keyLock) {
        val keyStore = KeyStore.getInstance(keyStoreName).apply { load(null) }
        val existing = keyStore.getKey(keyAlias, null) as? SecretKey
        if (existing != null) return@synchronized existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, keyStoreName)
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()

        generator.init(spec)
        generator.generateKey()
    }
}
