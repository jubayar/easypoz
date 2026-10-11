package net.redngreen.easypos.core.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PIN hashing (design D9): PBKDF2-HMAC-SHA256 with a per-user random salt.
 * PINs are 4–6 digits, so the work factor is the throttle that matters —
 * 150k iterations keeps unlock comfortably under ~100ms on a counter tablet
 * while making offline brute-force expensive. Argon2id can replace this
 * behind the same verify() contract later; the stored format (salt + hash
 * hex columns on User) does not change.
 */
@Singleton
class PinHasher @Inject constructor() {

    fun generateSalt(): String {
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        return salt.toHex()
    }

    fun hash(pin: String, saltHex: String): String {
        val spec = PBEKeySpec(
            pin.toCharArray(),
            saltHex.hexToBytes(),
            ITERATIONS,
            KEY_LENGTH_BITS,
        )
        val factory = SecretKeyFactory.getInstance(ALGORITHM)
        return factory.generateSecret(spec).encoded.toHex()
    }

    /** Constant-time comparison — never early-exit on a matching prefix. */
    fun verify(pin: String, saltHex: String, expectedHashHex: String): Boolean =
        MessageDigest.isEqual(
            hash(pin, saltHex).toByteArray(Charsets.US_ASCII),
            expectedHashHex.toByteArray(Charsets.US_ASCII),
        )

    fun isValidPinFormat(pin: String): Boolean =
        pin.length in PIN_MIN_LENGTH..PIN_MAX_LENGTH && pin.all { it in '0'..'9' }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    companion object {
        private const val ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val ITERATIONS = 150_000
        private const val KEY_LENGTH_BITS = 256
        private const val SALT_BYTES = 16
        const val PIN_MIN_LENGTH = 4
        const val PIN_MAX_LENGTH = 6
    }
}
