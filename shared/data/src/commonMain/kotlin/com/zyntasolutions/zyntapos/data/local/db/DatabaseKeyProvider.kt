package com.zyntasolutions.zyntapos.data.local.db

/**
 * ZentaPOS — DatabaseKeyProvider (commonMain expect)
 *
 * Platform-agnostic contract for secure AES-256 database key management.
 *
 * ## Responsibilities
 * 1. **First launch:** Generate a cryptographically-secure 256-bit (32-byte) AES key
 *    using `SecureRandom` and persist it in the platform's secure key store.
 * 2. **Subsequent launches:** Retrieve the existing key from the secure key store.
 * 3. **Key integrity:** If the stored key is missing, corrupted, or of wrong length,
 *    surface a recoverable [DatabaseKeyException] (subclass of [ZentaException]).
 *
 * ## Platform Implementations
 * | Platform | Storage | Key Type |
 * |----------|---------|----------|
 * | Android  | Android Keystore (hardware-backed) | AES-256 KeyStore entry |
 * | Desktop  | JCE PKCS12 KeyStore file (`~/.zyntapos/.keystore.p12`) | AES SecretKey |
 *
 * ## Security Invariants
 * - The raw key bytes MUST NEVER be logged, serialized to disk in plaintext, or
 *   transmitted over any channel.
 * - [getOrCreateKey] may only be called from a background coroutine dispatcher (IO).
 * - Key rotation (future §6.3) will call [rotateKey] which re-encrypts the database.
 *
 * @see DatabaseDriverFactory — consumer of the returned key
 * @see DatabaseFactory — orchestrates KeyProvider → DriverFactory lifecycle
 */
expect class DatabaseKeyProvider {

    /**
     * Retrieves the existing AES-256 database key, or generates and persists a new one
     * if this is the first launch.
     *
     * @return a 32-byte raw AES key, ready to be passed to [DatabaseDriverFactory.createEncryptedDriver]
     * @throws com.zyntasolutions.zyntapos.core.result.ZentaException.DatabaseException
     *   if the key cannot be retrieved or generated (e.g. hardware security module failure)
     */
    fun getOrCreateKey(): ByteArray

    /**
     * Returns `true` if a persisted key already exists in the secure store.
     * Used by [DatabaseFactory] to distinguish first-launch from subsequent opens.
     */
    fun hasPersistedKey(): Boolean
}
