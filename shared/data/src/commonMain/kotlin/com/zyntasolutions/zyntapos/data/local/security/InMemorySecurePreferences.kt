package com.zyntasolutions.zyntapos.data.local.security

/**
 * ZyntaPOS — InMemorySecurePreferences (Sprint 6 Development Scaffold)
 *
 * A non-persistent, in-memory implementation of [SecurePreferences] used during
 * Sprint 6 testing and development. All data is lost when the process exits.
 *
 * **⚠️ DO NOT USE IN PRODUCTION.**
 * Replace with the encrypted platform actuals in Sprint 8 (Step 5.1.3):
 * - Android: `EncryptedSharedPreferences` (Jetpack Security Crypto)
 * - Desktop: AES-256-GCM encrypted Properties file via EncryptionManager
 *
 * Koin registration (platform modules):
 * ```kotlin
 * // Temporary — Sprint 6 only
 * single<SecurePreferences> { InMemorySecurePreferences() }
 * ```
 */
class InMemorySecurePreferences : SecurePreferences {

    private val store = mutableMapOf<String, String>()

    override fun put(key: String, value: String) {
        store[key] = value
    }

    override fun get(key: String): String? = store[key]

    override fun remove(key: String) {
        store.remove(key)
    }

    override fun clear() {
        store.clear()
    }

    override fun contains(key: String): Boolean = store.containsKey(key)
}
