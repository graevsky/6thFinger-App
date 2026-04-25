package com.example.a6thfingercontrollapp.security.srp

import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Shared SRP-6a math context used during registration and login.
 *
 * Responsibilities:
 * - N and g parameters
 * - generate random salts/private values
 * - calculate password hash/verifier values
 * - calculate client-side session secrets and proofs
 * - keep all byte/int/hex conversions in one protocol-aware place
 */
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

    /** Hash algorithm used by both client and backend for SRP calculations. */
    private val hashAlgName = hashAlg

    /** Secure source for salts and private ephemeral values. */
    private val random = SecureRandom()

    /** Normalized username participating in SRP hash formulas. */
    private val user: String = username

    /** Plain password is kept only inside this short-lived SRP calculation context. */
    private val password: String? = password

    /** SRP generator value g. */
    val gen: BigInteger = SrpUtils.intFromHex(generatorHex)

    /** SRP safe prime value N. */
    val prime: BigInteger = SrpUtils.intFromHex(primeHex)

    /**
     * SRP multiplier k.
     *
     * Either backend valuer or H(N | PAD(g)) is used.
     */
    private val mult: BigInteger = run {
        if (multiplierHex != null) {
            SrpUtils.intFromHex(multiplierHex)
        } else {
            hashToInt(prime, pad(gen))
        }
    }

    /** Number of random bits used for ephemeral private values. */
    private val bitsRandomInternal = bitsRandom

    /** Number of random bits used for salt generation. */
    private val bitsSaltInternal = bitsSalt

    /**
     * Converts supported protocol values to bytes before hashing.
     */
    private fun conv(arg: Any): ByteArray =
        when (arg) {
            is BigInteger -> SrpUtils.intToBytes(arg)
            is ByteArray -> arg
            is String -> arg.toByteArray(Charsets.UTF_8)
            else -> error("Unsupported type in hash: ${arg::class}")
        }

    /**
     * Calculates digest bytes using the configured hash algorithm.
     */
    private fun digestBytes(data: ByteArray): ByteArray {
        val md = MessageDigest.getInstance(hashAlgName)
        md.update(data)
        return md.digest()
    }

    /**
     * Hashes multiple values, optionally inserting a string separator between them.
     */
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

    /**
     * Hashes values and returns the digest interpreted as a positive big integer.
     */
    private fun hashToInt(vararg args: Any, joiner: String = ""): BigInteger {
        val bytes = hashBytes(*args, joiner = joiner)
        val hex = SrpUtils.hexFromBytes(bytes)
        return SrpUtils.intFromHex(hex)
    }

    /**
     * Left-pads a number to the same byte length as N.
     */
    fun pad(value: BigInteger): ByteArray {
        val primeBytes = SrpUtils.intToBytes(prime)
        val valBytes = SrpUtils.intToBytes(value)
        if (valBytes.size >= primeBytes.size) return valBytes
        val out = ByteArray(primeBytes.size)
        System.arraycopy(valBytes, 0, out, out.size - valBytes.size, valBytes.size)
        return out
    }

    /**
     * Generates a random positive integer with the requested bit length.
     */
    fun generateRandom(bitsLen: Int = bitsRandomInternal): BigInteger {
        return BigInteger(bitsLen, random).abs()
    }

    /**
     * Generates a random SRP salt.
     */
    fun generateSalt(): BigInteger = generateRandom(bitsSaltInternal)

    /**
     * Calculates private password hash x.
     *
     * Formula:
     * x = H(s | H(I | ":" | P))
     */
    fun getCommonPasswordHash(salt: BigInteger): BigInteger {
        val pwd = password ?: error("Password must be set in context")
        val inner = hashBytes(user, pwd, joiner = ":")
        return hashToInt(salt, inner)
    }

    /**
     * Calculates verifier v stored by the backend.
     *
     * Formula:
     * v = g^x mod N
     */
    fun getCommonPasswordVerifier(passwordHash: BigInteger): BigInteger {
        return gen.modPow(passwordHash, prime)
    }

    /**
     * Calculates scrambling parameter u from client and server public values.
     *
     * Formula:
     * u = H(PAD(A) | PAD(B))
     */
    fun getCommonSecret(serverPublic: BigInteger, clientPublic: BigInteger): BigInteger {
        return hashToInt(pad(clientPublic), pad(serverPublic))
    }

    /**
     * Calculates the client premaster secret S.
     *
     * Formula:
     * S = (B - k * g^x) ^ (a + u * x) mod N
     */
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

    /**
     * Derives the common session key K from premaster secret S.
     *
     * Formula:
     * K = H(S)
     */
    fun getCommonSessionKey(premasterSecret: BigInteger): ByteArray {
        return hashBytes(premasterSecret)
    }

    /**
     * Builds the client proof M1 sent to the backend.
     *
     * Formula:
     * M1 = H(H(N) XOR H(g) | H(I) | s | A | B | K)
     */
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

    /**
     * Builds expected server proof M2.
     *
     * Formula:
     * M2 = H(A | M1 | K)
     */
    fun getCommonSessionKeyProofHash(
        sessionKey: ByteArray,
        sessionKeyProof: ByteArray,
        clientPublic: BigInteger
    ): ByteArray {
        return hashBytes(clientPublic, sessionKeyProof, sessionKey)
    }

    /**
     * Generates the client private ephemeral value a.
     */
    fun generateClientPrivate(): BigInteger = generateRandom()

    /**
     * Calculates the client public ephemeral value A.
     *
     * Formula:
     * A = g^a mod N
     */
    fun getClientPublic(clientPrivate: BigInteger): BigInteger = gen.modPow(clientPrivate, prime)
}
