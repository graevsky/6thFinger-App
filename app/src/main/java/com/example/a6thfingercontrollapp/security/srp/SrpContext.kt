package com.example.a6thfingercontrollapp.security.srp

import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom

class SrpContext(
        username: String,
        password: String?,
        primeHex: String,
        generatorHex: String,
        hashAlg: String = "SHA-1",
        multiplierHex: String? = null,
        bitsRandom: Int = 1024,
        bitsSalt: Int = 64
) {

    private val hashAlgName = hashAlg
    private val random = SecureRandom()

    private val user: String = username
    private val password: String? = password

    val gen: BigInteger = SrpUtils.intFromHex(generatorHex)
    val prime: BigInteger = SrpUtils.intFromHex(primeHex)

    private val mult: BigInteger = run {
        if (multiplierHex != null) {
            SrpUtils.intFromHex(multiplierHex)
        } else {
            hashToInt(prime, pad(gen))
        }
    }

    private val bitsRandomInternal = bitsRandom
    private val bitsSaltInternal = bitsSalt

    private fun conv(arg: Any): ByteArray =
            when (arg) {
                is BigInteger -> SrpUtils.intToBytes(arg)
                is ByteArray -> arg
                is String -> arg.toByteArray(Charsets.UTF_8)
                else -> error("Unsupported type in hash: ${arg::class}")
            }

    private fun digestBytes(data: ByteArray): ByteArray {
        val md = MessageDigest.getInstance(hashAlgName)
        md.update(data)
        return md.digest()
    }

    private fun hashBytes(vararg args: Any, joiner: String = ""): ByteArray {
        val joinerBytes = joiner.toByteArray(Charsets.UTF_8)
        val parts = args.map { conv(it) }

        val totalLen = parts.sumOf { it.size } + joinerBytes.size * (parts.size - 1)
        val buf = ByteArray(totalLen)
        var pos = 0
        for ((idx, p) in parts.withIndex()) {
            if (idx > 0 && joinerBytes.isNotEmpty()) {
                System.arraycopy(joinerBytes, 0, buf, pos, joinerBytes.size)
                pos += joinerBytes.size
            }
            System.arraycopy(p, 0, buf, pos, p.size)
            pos += p.size
        }
        return digestBytes(buf)
    }

    private fun hashToInt(vararg args: Any, joiner: String = ""): BigInteger {
        val bytes = hashBytes(*args, joiner = joiner)
        val hex = SrpUtils.hexFromBytes(bytes)
        return SrpUtils.intFromHex(hex)
    }

    fun pad(value: BigInteger): ByteArray {
        val primeBytes = SrpUtils.intToBytes(prime)
        val valBytes = SrpUtils.intToBytes(value)
        if (valBytes.size >= primeBytes.size) return valBytes
        val out = ByteArray(primeBytes.size)
        System.arraycopy(valBytes, 0, out, out.size - valBytes.size, valBytes.size)
        return out
    }

    fun generateRandom(bitsLen: Int = bitsRandomInternal): BigInteger {
        return BigInteger(bitsLen, random).abs()
    }

    fun generateSalt(): BigInteger = generateRandom(bitsSaltInternal)

    // x = H(s | H(I | ":" | P))
    fun getCommonPasswordHash(salt: BigInteger): BigInteger {
        val pwd = password ?: error("Password must be set in context")
        val inner = hashBytes(user, pwd, joiner = ":")
        return hashToInt(salt, inner)
    }

    // v = g^x % N
    fun getCommonPasswordVerifier(passwordHash: BigInteger): BigInteger {
        return gen.modPow(passwordHash, prime)
    }

    // u = H(PAD(A) | PAD(B))
    fun getCommonSecret(serverPublic: BigInteger, clientPublic: BigInteger): BigInteger {
        return hashToInt(pad(clientPublic), pad(serverPublic))
    }

    // S = (B - (k * g^x)) ^ (a + (u * x)) % N
    fun getClientPremasterSecret(
            passwordHash: BigInteger,
            serverPublic: BigInteger,
            clientPrivate: BigInteger,
            commonSecret: BigInteger
    ): BigInteger {
        val passwordVerifier = getCommonPasswordVerifier(passwordHash)
        val base = serverPublic.subtract(mult.multiply(passwordVerifier)).mod(prime)
        val exp = clientPrivate.add(commonSecret.multiply(passwordHash))
        return base.modPow(exp, prime)
    }

    // K = H(S)
    fun getCommonSessionKey(premasterSecret: BigInteger): ByteArray {
        return hashBytes(premasterSecret)
    }

    // M = H(H(N) XOR H(g) | H(U) | s | A | B | K)
    fun getCommonSessionKeyProof(
            sessionKey: ByteArray,
            saltBytes: ByteArray,
            serverPublic: BigInteger,
            clientPublic: BigInteger
    ): ByteArray {
        val hN = hashToInt(prime)
        val hG = hashToInt(gen)
        val xorNg = SrpUtils.intToBytes(hN.xor(hG))
        val hU = hashToInt(user)

        return hashBytes(
                xorNg,
                SrpUtils.intToBytes(hU),
                saltBytes,
                clientPublic,
                serverPublic,
                sessionKey
        )
    }

    // H(A | M | K)
    fun getCommonSessionKeyProofHash(
            sessionKey: ByteArray,
            sessionKeyProof: ByteArray,
            clientPublic: BigInteger
    ): ByteArray {
        return hashBytes(clientPublic, sessionKeyProof, sessionKey)
    }

    fun generateClientPrivate(): BigInteger = generateRandom()

    fun getClientPublic(clientPrivate: BigInteger): BigInteger = gen.modPow(clientPrivate, prime)
}
