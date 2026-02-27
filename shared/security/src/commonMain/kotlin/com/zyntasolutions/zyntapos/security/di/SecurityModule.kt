package com.zyntasolutions.zyntapos.security.di

import com.zyntasolutions.zyntapos.security.audit.SecurityAuditLogger
import com.zyntasolutions.zyntapos.security.auth.JwtManager
import com.zyntasolutions.zyntapos.security.auth.PasswordHasherAdapter
import com.zyntasolutions.zyntapos.security.auth.PinManager
import com.zyntasolutions.zyntapos.security.crypto.DatabaseKeyManager
import com.zyntasolutions.zyntapos.security.crypto.EncryptionManager
import com.zyntasolutions.zyntapos.security.prefs.SecurePreferences
import com.zyntasolutions.zyntapos.security.license.LicenseManager
import com.zyntasolutions.zyntapos.security.rbac.RbacEngine
import com.zyntasolutions.zyntapos.domain.port.PasswordHashPort
import com.zyntasolutions.zyntapos.domain.port.SecureStoragePort
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
 * | [PasswordHashPort]     | Singleton | Domain port → [PasswordHasherAdapter] (MERGED-F3)  |
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
// ─────────────────────────────────────────────────────────────────────────────
// ADR-004 — keystore/ and token/ scaffold directories (MERGED-F2) — 2026-02-22
//
// DECISION: Both scaffold directories have been REMOVED (their .gitkeep files
// deleted). No KeystoreProvider or TokenStorage classes are needed as separate
// top-level abstractions because:
//
//   keystore/ — The Android Keystore (via AndroidKeyStore JCE provider) and the
//     Desktop PKCS12 KeyStore are accessed directly inside:
//       • EncryptionManager.android.kt / EncryptionManager.jvm.kt  (alias-keyed AES-256-GCM)
//       • DatabaseKeyManager.android.kt (envelope-encrypted DEK in Android Keystore)
//       • DatabaseKeyManager.jvm.kt     (DEK stored in ~/.zentapos/.db_keystore.p12)
//     A separate KeystoreProvider expect/actual would be a thin, redundant wrapper
//     over this already-clean abstraction. Adding it would increase indirection
//     with no architectural benefit.
//
//   token/ — Token storage is covered by the composition of two existing types:
//       • TokenStorage (interface) in security.prefs — defines put/get/remove
//       • SecurePreferences (expect/actual) — implements TokenStorage on all platforms
//       • JwtManager (commonMain)           — owns all JWT save/get/clear operations,
//         accepting any TokenStorage (injected as SecurePreferences by Koin below)
//     A separate token/ package class would duplicate JwtManager's responsibility.
//
// If future requirements demand a standalone KeystoreProvider abstraction
// (e.g., for key rotation, key export, or cross-module key sharing beyond
// EncryptionManager), create:
//   expect class KeystoreProvider  →  security/keystore/KeystoreProvider.kt
//   following the SecurePreferences expect/actual pattern in security/prefs/.
// ─────────────────────────────────────────────────────────────────────────────
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
     * Domain port binding for [SecurePreferences].
     * `:shared:data` injects [SecureStoragePort] — not [SecurePreferences] directly —
     * so the data layer holds no compile-time dependency on `:shared:security`.
     * MERGED-F3 (2026-02-22).
     */
    single<SecureStoragePort> { get<SecurePreferences>() }

    // NOTE: The bare `single { PasswordHasher }` binding was removed (audit G1.2, 2026-02-22).
    // PasswordHasher is a stateless expect object consumed only by PasswordHasherAdapter below.
    // No Koin consumer ever injected PasswordHasher directly — all callers use PasswordHashPort.

    /**
     * Domain port for password hashing — [PasswordHashPort] bound to [PasswordHasherAdapter].
     * Registered here (in :shared:security) so that :shared:data needs no direct dependency
     * on :shared:security — it only imports the PasswordHashPort interface from :shared:domain.
     * MERGED-F3 (2026-02-22).
     */
    single<PasswordHashPort> { PasswordHasherAdapter() }

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

    /**
     * License manager — bridges feature-registry state (provided by dataModule)
     * with RBAC to determine per-user feature access.
     * Requires [com.zyntasolutions.zyntapos.domain.repository.FeatureRegistryRepository]
     * from dataModule and [RbacEngine] above.
     */
    single { LicenseManager(get(), get()) }
}
