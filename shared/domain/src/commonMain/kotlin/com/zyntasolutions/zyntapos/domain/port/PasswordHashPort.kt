package com.zyntasolutions.zyntapos.domain.port

/**
 * Output port for password hashing and verification.
 *
 * Defined in `:shared:domain` so that repository implementations (`:shared:data`)
 * depend only on the domain contract — never on the concrete cryptographic library
 * in `:shared:security`. This eliminates the `:shared:data → :shared:security` coupling
 * introduced during the PHF hotfix (MERGED-F3).
 *
 * ## Hexagonal Architecture placement
 * - **Port** (this interface): `:shared:domain` — declares *what* the domain needs.
 * - **Adapter** (implementation): `:shared:security` — `PasswordHasherAdapter` delegates
 *   to the platform `expect object PasswordHasher` (BCrypt, work factor 12).
 *
 * ## Security contract
 * - [hash] MUST produce a self-contained hash string that embeds its own salt
 *   (e.g. a full BCrypt string `$2a$12$…`). The caller stores the returned value
 *   as-is and never constructs or parses it.
 * - [verify] MUST be constant-time (or BCrypt-equivalent) to prevent timing attacks.
 * - Neither function may log or retain the [plain] argument beyond its invocation.
 *
 * ## Thread safety
 * Implementations MUST be stateless and safe to call from any coroutine dispatcher,
 * including [kotlinx.coroutines.Dispatchers.IO] used by repository implementations.
 *
 * @see com.zyntasolutions.zyntapos.security.auth.PasswordHasherAdapter
 */
interface PasswordHashPort {

    /**
     * Hashes [plain] with the configured algorithm and returns the full hash string
     * (including embedded salt) safe for persistence in the `users` table.
     *
     * @param plain  Raw plaintext password or PIN. Must not be blank.
     * @return       Hash string (e.g. BCrypt `$2a$12$<salt><hash>`).
     * @throws IllegalArgumentException if [plain] is blank.
     */
    fun hash(plain: String): String

    /**
     * Verifies [plain] against a previously generated [hashed] value.
     *
     * @param plain   Candidate plaintext credential.
     * @param hashed  Stored hash string produced by [hash].
     * @return        `true` if [plain] matches [hashed]; `false` otherwise.
     */
    fun verify(plain: String, hashed: String): Boolean
}
