package com.example.a6thfingercontrollapp.security.srp

import java.math.BigInteger

object SrpUtils {

    fun hexFromInt(v: BigInteger): String {
        var s = v.toString(16).lowercase()
        if (s.length % 2 != 0) s = "0$s"
        return s
    }

    fun hexFromBytes(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    fun intFromHex(hex: String): BigInteger {
        val clean = hex.trim().lowercase()
        return if (clean.isEmpty()) BigInteger.ZERO else BigInteger(clean, 16)
    }

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
