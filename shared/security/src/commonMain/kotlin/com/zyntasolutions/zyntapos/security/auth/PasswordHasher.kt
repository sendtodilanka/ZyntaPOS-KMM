package com.zyntasolutions.zyntapos.security.auth

/**
 * Platform-agnostic BCrypt password hashing bridge.
 *
 * Both Android and Desktop (JVM) targets use the jBCrypt library, which ships as a
 * standard JVM dependency available on both platforms. An expect/actual is used here
 * to keep the API in commonMain while allowing future native/JS platform substitution.
 *
 * BCrypt parameters:
 * - Work factor: 12 (≈ 300 ms on modern hardware — adequate for interactive login without
 *   degrading high-speed POS cashier PIN operations).
 * - Salt: 16-byte cryptographic random via `BCrypt.gensalt(12)`.
 *
 * ## Usage
 * ```kotlin
 * val hash = PasswordHasher.hashPassword("MyP@ssw0rd")   // "$2a$12$..."
 * val valid = PasswordHasher.verifyPassword("MyP@ssw0rd", hash) // true
 * ```
 *
 * **Note:** Never store or log the plaintext password. Pass it directly to these functions
 * and discard it immediately after use.
 */
expect object PasswordHasher {

    /**
     * Hashes [plain] with BCrypt (work factor 12) and returns the full BCrypt hash string
     * including the embedded salt (`$2a$12$<22-char-salt><31-char-hash>`).
     *
     * @param plain  Raw plaintext password (must not be blank).
     * @return       BCrypt hash string, safe to store in the database.
     */
    fun hashPassword(plain: String): String

    /**
     * Verifies [plain] against a previously generated [hash].
     *
     * @param plain  Candidate plaintext password.
     * @param hash   BCrypt hash string from [hashPassword].
     * @return       `true` if [plain] matches [hash]; `false` otherwise.
     */
    fun verifyPassword(plain: String, hash: String): Boolean
}
