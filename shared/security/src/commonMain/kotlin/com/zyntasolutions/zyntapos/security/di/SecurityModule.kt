package com.zyntasolutions.zyntapos.security.di

/**
 * ZentaPOS — :shared:security Koin DI Module
 *
 * Placeholder for Sprint 8 (Step 5.1.9) where real bindings are added:
 * - EncryptionManager (expect/actual — AES-256-GCM)
 * - DatabaseKeyManager (expect/actual — Android Keystore / JCE PKCS12)
 * - SecurePreferences (expect/actual — EncryptedSharedPreferences / encrypted Properties)
 * - PasswordHasher (BCrypt)
 * - JwtManager
 * - PinManager
 * - SecurityAuditLogger
 * - RbacEngine (stateless, pure computation)
 *
 * Registered in the root Koin graph via `startKoin { modules(securityModule) }`.
 */
internal object SecurityModule
