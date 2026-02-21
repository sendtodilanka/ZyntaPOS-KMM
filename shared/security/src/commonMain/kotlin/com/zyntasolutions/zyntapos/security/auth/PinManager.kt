package com.zyntasolutions.zyntapos.security.auth

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Manages 4–6 digit quick-switch PINs for POS cashier role-switching.
 *
 * Pins are hashed with SHA-256 + a cryptographically random 16-byte salt and stored
 * as the string `"<base64url-salt>:<hex-sha256-hash>"`. This format is referenced
 * by `User.pinHash` in the domain model.
 *
 * **Why SHA-256 instead of BCrypt for PINs?**
 * PINs are short (4–6 digits = max 10^6 combinations) so the theoretical security gain
 * of BCrypt is offset by the fact that PINs should not be the sole authentication factor
 * for high-privilege actions. PIN auth is an offline, low-latency UX feature (cashier
 * switching) — BCrypt's ~300 ms latency would degrade the checkout flow. The salt prevents
 * rainbow-table attacks. Brute-force protection is enforced at the application layer
 * (max 5 attempts then lockout).
 *
 * ## Stored format
 * `"<base64url-salt-16bytes>:<hex-sha256-hash-32bytes>"`
 *
 * @see sha256
 * @see secureRandomBytes
 */
object PinManager {

    private const val SALT_SIZE = 16
    private const val VALID_PIN_REGEX = "^[0-9]{4,6}$"

    /**
     * Hashes [pin] with a random 16-byte salt using SHA-256.
     *
     * @param pin 4–6 digit PIN string.
     * @return Stored hash in `"<base64url-salt>:<hex-hash>"` format.
     * @throws IllegalArgumentException if [pin] fails format validation.
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun hashPin(pin: String): String {
        require(validatePinFormat(pin)) { "PIN must be 4–6 digits" }
        val salt = secureRandomBytes(SALT_SIZE)
        val hash = sha256(salt + pin.encodeToByteArray())
        val saltB64 = Base64.UrlSafe.encode(salt)
        val hashHex = hash.toHex()
        return "$saltB64:$hashHex"
    }

    /**
     * Verifies [pin] against [storedHash] produced by [hashPin].
     *
     * Uses constant-time comparison to prevent timing side-channel attacks.
     *
     * @param pin        Candidate PIN (raw digits).
     * @param storedHash The stored `"<salt>:<hash>"` string from the database.
     * @return `true` if [pin] matches; `false` on any mismatch or malformed hash.
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun verifyPin(pin: String, storedHash: String): Boolean = runCatching {
        val parts = storedHash.split(":")
        require(parts.size == 2) { "Malformed PIN hash" }
        val salt = Base64.UrlSafe.decode(parts[0])
        val expectedHex = parts[1]
        val actualHex = sha256(salt + pin.encodeToByteArray()).toHex()
        constantTimeEquals(actualHex, expectedHex)
    }.getOrDefault(false)

    /**
     * Returns `true` if [pin] consists of 4–6 decimal digits and nothing else.
     *
     * @param pin PIN string to validate.
     */
    fun validatePinFormat(pin: String): Boolean = pin.matches(Regex(VALID_PIN_REGEX))

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Constant-time string comparison — prevents timing side-channel. */
    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }

    /** Converts a [ByteArray] to a lowercase hex string. */
    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
