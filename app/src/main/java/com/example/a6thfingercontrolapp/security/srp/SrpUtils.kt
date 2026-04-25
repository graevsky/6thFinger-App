package com.example.a6thfingercontrolapp.security.srp

import java.math.BigInteger

/**
 * Small conversion helper for SRP numeric values.
 *
 * SRP exchanges most large numbers as hexadecimal strings, while calculations
 * are performed with BigInteger and proof hashes operate on byte arrays.
 */
object SrpUtils {

    /**
     * Converts BigInteger to lowercase even-length hexadecimal string.
     */
    fun hexFromInt(v: BigInteger): String {
        var s = v.toString(16).lowercase()
        if (s.length % 2 != 0) s = "0$s"
        return s
    }

    /**
     * Converts raw bytes to lowercase hexadecimal string.
     */
    fun hexFromBytes(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    /**
     * Parses hexadecimal string into BigInteger.
     */
    fun intFromHex(hex: String): BigInteger {
        val clean = hex.trim().lowercase()
        return if (clean.isEmpty()) BigInteger.ZERO else BigInteger(clean, 16)
    }

    /**
     * Converts BigInteger to bytes via normalized hexadecimal representation.
     */
    fun intToBytes(v: BigInteger): ByteArray {
        val hex = hexFromInt(v)
        val out = ByteArray(hex.length / 2)
        var i = 0
        while (i < hex.length) {
            val byte = hex.substring(i, i + 2).toInt(16).toByte()
            out[i / 2] = byte
            i += 2
        }
        return out
    }

    /**
     * Parses even-length hexadecimal string into byte array.
     */
    fun hexToBytes(hex: String): ByteArray {
        val clean = hex.trim().lowercase()
        require(clean.length % 2 == 0) { "Invalid hex length" }
        val out = ByteArray(clean.length / 2)
        var i = 0
        while (i < clean.length) {
            out[i / 2] = clean.substring(i, i + 2).toInt(16).toByte()
            i += 2
        }
        return out
    }
}
