package com.zyntasolutions.zyntapos.data.local.security

/**
 * ZentaPOS — PlaceholderPasswordHasher (Sprint 6 Development Scaffold)
 *
 * A **non-secure** implementation of [PasswordHasher] for Sprint 6 development and
 * in-memory integration testing ONLY.
 *
 * - `hash(plain)` stores the plain text prefixed with `"PLAIN:"` — not hashed.
 * - `verify(plain, hash)` accepts both the prefixed form and the raw plain text,
 *   allowing tests seeded with either format to pass.
 *
 * **⚠️ DO NOT USE IN PRODUCTION.**
 * Replace in Sprint 8 (Step 5.1.4) with BCrypt-backed [PasswordHasher] actual:
 * - JVM/Android: jBCrypt (`BCrypt.hashpw`, `BCrypt.checkpw`)
 *
 * Koin registration (platform modules):
 * ```kotlin
 * // Temporary — Sprint 6 only
 * single<PasswordHasher> { PlaceholderPasswordHasher() }
 * ```
 */
class PlaceholderPasswordHasher : PasswordHasher {

    override fun hash(plainText: String): String = "PLAIN:$plainText"

    override fun verify(plainText: String, hash: String): Boolean =
        hash == "PLAIN:$plainText" || hash == plainText
}
