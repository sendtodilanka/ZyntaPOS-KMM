package com.zyntasolutions.zyntapos.security.di

import com.zyntasolutions.zyntapos.security.audit.SecurityAuditLogger
import com.zyntasolutions.zyntapos.security.auth.JwtManager
import com.zyntasolutions.zyntapos.security.auth.PasswordHasher
import com.zyntasolutions.zyntapos.security.auth.PinManager
import com.zyntasolutions.zyntapos.security.crypto.DatabaseKeyManager
import com.zyntasolutions.zyntapos.security.crypto.EncryptionManager
import com.zyntasolutions.zyntapos.security.prefs.SecurePreferences
import com.zyntasolutions.zyntapos.security.rbac.RbacEngine
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Koin DI module for the `:shared:security` module.
 *
 * ### Bindings provided
 * | Binding                | Scope    | Notes                                              |
 * |------------------------|----------|----------------------------------------------------|
 * | [EncryptionManager]    | Singleton | expect/actual — AES-256-GCM                       |
 * | [DatabaseKeyManager]   | Singleton | expect/actual — Android Keystore / PKCS12          |
 * | [SecurePreferences]    | Singleton | expect/actual — EncryptedSharedPreferences / file  |
 * | [PasswordHasher]       | Singleton | BCrypt work-factor 12                              |
 * | [JwtManager]           | Singleton | Base64url JWT parse; tokens persisted in prefs     |
 * | [PinManager]           | Singleton | SHA-256 + salt; stateless object                   |
 * | [SecurityAuditLogger]  | Singleton | Writes to AuditRepository (append-only)            |
 * | [RbacEngine]           | Singleton | Stateless; evaluates Permission.rolePermissions    |
 *
 * ### Registration
 * Include `securityModule` in the root Koin graph:
 * ```kotlin
 * startKoin {
 *     modules(securityModule, dataModule, /* … */)
 * }
 * ```
 *
 * > **Note:** [SecurityAuditLogger] requires an `AuditRepository` and `deviceId` (String)
 * > to be available in the Koin graph before this module is loaded. Provide them from
 * > `:shared:data`'s `dataModule` and a platform-specific device ID binding.
 */
val securityModule = module {

    /**
     * AES-256-GCM encryption manager.
     * Android: Android Keystore-backed key; Desktop: JCE + PKCS12.
     */
    single { EncryptionManager() }

    /**
     * 256-bit database key manager for SQLCipher initialisation.
     * Android: envelope-encrypted DEK in Android Keystore.
     * Desktop: AES key in PKCS12 keystore file.
     */
    single { DatabaseKeyManager() }

    /**
     * Encrypted key-value preferences store.
     * Android: EncryptedSharedPreferences; Desktop: AES-encrypted Properties file.
     */
    single { SecurePreferences() }

    /**
     * BCrypt password hasher — work factor 12.
     * Stateless object; single instance is sufficient.
     */
    single { PasswordHasher }

    /**
     * JWT parser / token storage.
     * Reads/writes access & refresh tokens in [SecurePreferences].
     */
    single { JwtManager(prefs = get()) }

    /**
     * PIN hasher / verifier — SHA-256 + random 16-byte salt.
     * Stateless object; single instance is sufficient.
     */
    single { PinManager }

    /**
     * Security audit logger.
     * Requires [com.zyntasolutions.zyntapos.domain.repository.AuditRepository]
     * and a `String` named "deviceId" in the Koin graph.
     */
    single {
        SecurityAuditLogger(
            auditRepository = get(),
            deviceId = get(named("deviceId")),
        )
    }

    /**
     * Stateless RBAC engine.
     * Evaluates [com.zyntasolutions.zyntapos.domain.model.Permission.rolePermissions].
     */
    single { RbacEngine() }
}
