package org.qosp.notes.ui.lock

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * App lock PIN: 4 to 6 digits, never stored as such. What is saved is "pbkdf2:<algorithm>:<iterations>:<salt>:<hash>"
 * (hex), so the PIN cannot be read from the app's files.
 */
object PinHasher {
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 6
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256

    // PBKDF2 with SHA-256 needs Android 8; older versions (and old stored PINs) use SHA-1
    private val algorithms = listOf("PBKDF2WithHmacSHA256", "PBKDF2WithHmacSHA1")

    fun isValid(pin: String) = pin.length in MIN_LENGTH..MAX_LENGTH && pin.all { it in '0'..'9' }

    fun hash(pin: String): String {
        require(isValid(pin)) { "PIN must be $MIN_LENGTH-$MAX_LENGTH digits" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val algorithm = algorithms.first { runCatching { SecretKeyFactory.getInstance(it) }.isSuccess }
        return listOf("pbkdf2", algorithm, ITERATIONS, hex(salt), hex(derive(pin, salt, ITERATIONS, algorithm))).joinToString(":")
    }

    fun verify(pin: String, stored: String?): Boolean {
        val parts = stored?.split(':') ?: return false
        if (parts.size != 5 || parts[0] != "pbkdf2" || !isValid(pin)) return false
        return runCatching {
            val expected = unhex(parts[4])
            val actual = derive(pin, unhex(parts[3]), parts[2].toInt(), parts[1])
            MessageDigest.isEqual(expected, actual) // constant time
        }.getOrDefault(false)
    }

    private fun derive(pin: String, salt: ByteArray, iterations: Int, algorithm: String): ByteArray =
        SecretKeyFactory.getInstance(algorithm).generateSecret(PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_BITS)).encoded

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    private fun unhex(text: String) = ByteArray(text.length / 2) { text.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
