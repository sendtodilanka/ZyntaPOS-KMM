package com.zyntasolutions.zyntapos.security.auth

/**
 * ZyntaPOS — PlaceholderPasswordHasher (Debug / Test Utility)
 *
 * A **non-secure** in-memory hasher for use in unit tests and debug builds ONLY.
 * Provides the same API surface as the canonical [PasswordHasher] expect object so
 * test code can substitute it without depending on jBCrypt or the platform actuals.
 *
 * ## Hashing strategy
 * - [hashPassword] prefixes the plaintext with `"PLAIN:"` — NOT cryptographically hashed.
 * - [verifyPassword] accepts both the prefixed form and the raw plaintext, allowing
 *   tests seeded with either format to pass.
 *
 * ## Usage in tests
 * ```kotlin
 * val hasher = PlaceholderPasswordHasher()
 * val hash = hasher.hashPassword("secret")          // "PLAIN:secret"
 * hasher.verifyPassword("secret", hash)             // true
 * hasher.verifyPassword("wrong",  hash)             // false
 * ```
 *
 * **⚠️ DO NOT USE IN PRODUCTION.**
 * Production code calls [PasswordHasher.hashPassword] / [PasswordHasher.verifyPassword]
 * directly via the platform actual (Android: jBCrypt; Desktop JVM: jBCrypt).
 *
 * Moved from `shared/data/.../local/security/PlaceholderPasswordHasher.kt` to this
 * module as part of the PasswordHasher deduplication hotfix (Sprint 8 post-fix).
 */
class PlaceholderPasswordHasher {

    /**
     * Returns the plaintext prefixed with `"PLAIN:"` — not a real hash.
     *
     * @param plain  Raw plaintext password.
     * @return       Fake "hash" string of the form `"PLAIN:<plain>"`.
     */
    fun hashPassword(plain: String): String = "PLAIN:$plain"

    /**
     * Returns `true` if [plain] matches [hash] using the placeholder convention.
     *
     * Accepts both `"PLAIN:<plain>"` (hashed via [hashPassword]) and the raw
     * plaintext itself to ease test setup that seeds the DB with plain strings.
     *
     * @param plain  Candidate plaintext password.
     * @param hash   Hash string to verify against.
     * @return       `true` if [plain] matches [hash] by placeholder convention.
     */
    fun verifyPassword(plain: String, hash: String): Boolean =
        hash == "PLAIN:$plain" || hash == plain
}
