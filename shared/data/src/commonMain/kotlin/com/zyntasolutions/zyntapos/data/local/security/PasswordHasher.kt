package com.zyntasolutions.zyntapos.data.local.security

/**
 * ZentaPOS — PasswordHasher (Security Scaffold)
 *
 * Temporary interface scaffold for Sprint 6 (Step 3.3) repository implementations.
 * Concrete implementations (BCrypt on JVM, expect/actual bridge) will be provided
 * by :shared:security in Sprint 8 (Step 5.1.4).
 *
 * The Koin binding for this interface must be registered in platform data modules
 * (AndroidDataModule / DesktopDataModule) via the security module's actual.
 */
interface PasswordHasher {

    /**
     * Verifies [plainText] against a stored BCrypt [hash].
     *
     * @param plainText The raw password string entered by the user.
     * @param hash      The stored BCrypt hash (format: `$2a$12$...`).
     * @return `true` if [plainText] matches [hash]; `false` otherwise.
     */
    fun verify(plainText: String, hash: String): Boolean

    /**
     * Hashes [plainText] using BCrypt with a cost factor of 12.
     *
     * @param plainText The raw password or PIN to hash.
     * @return A BCrypt hash string safe to persist in the database.
     */
    fun hash(plainText: String): String
}
