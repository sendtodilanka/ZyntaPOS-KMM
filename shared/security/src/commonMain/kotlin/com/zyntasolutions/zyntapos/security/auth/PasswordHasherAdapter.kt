package com.zyntasolutions.zyntapos.security.auth

import com.zyntasolutions.zyntapos.domain.port.PasswordHashPort

/**
 * Adapter that bridges the [PasswordHashPort] domain contract to the platform
 * `expect object PasswordHasher` (BCrypt, work factor 12).
 *
 * ## Hexagonal Architecture placement
 * - Lives in `:shared:security` (infrastructure layer) — NOT in `:shared:domain`.
 * - Registered in `DataModule` as `single<PasswordHashPort> { PasswordHasherAdapter() }`.
 * - `:shared:data` imports only [PasswordHashPort] from `:shared:domain`; it has zero
 *   compile-time knowledge of BCrypt or this adapter class.
 *
 * ## Why a class rather than an object?
 * An instantiatable class allows Koin to manage its lifecycle and simplifies
 * mocking in unit tests — callers inject `PasswordHashPort` and can swap in a
 * test double without touching the BCrypt library.
 *
 * @see PasswordHashPort
 * @see PasswordHasher
 */
class PasswordHasherAdapter : PasswordHashPort {

    /**
     * Delegates to [PasswordHasher.hashPassword].
     * BCrypt with work factor 12; ~300 ms on modern hardware.
     *
     * @param plain  Raw plaintext credential. Must not be blank.
     * @return       Full BCrypt hash string including embedded 16-byte salt.
     */
    override fun hash(plain: String): String = PasswordHasher.hashPassword(plain)

    /**
     * Delegates to [PasswordHasher.verifyPassword].
     * BCrypt constant-time comparison — safe against timing attacks.
     *
     * @param plain   Candidate plaintext credential.
     * @param hashed  BCrypt hash string from [hash].
     * @return        `true` if [plain] matches [hashed].
     */
    override fun verify(plain: String, hashed: String): Boolean =
        PasswordHasher.verifyPassword(plain, hashed)
}
