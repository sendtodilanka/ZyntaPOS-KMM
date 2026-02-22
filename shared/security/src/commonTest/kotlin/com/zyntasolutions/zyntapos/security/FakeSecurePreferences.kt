package com.zyntasolutions.zyntapos.security

import com.zyntasolutions.zyntapos.domain.port.SecureStoragePort
import com.zyntasolutions.zyntapos.security.prefs.TokenStorage

/**
 * In-memory fake implementing [SecureStoragePort] and [TokenStorage] for unit tests.
 *
 * Avoids extending the `expect class SecurePreferences` (which is final and cannot be
 * subclassed in commonTest). Implements the same port interfaces so production code
 * under test can accept this stub via dependency injection.
 *
 * Not suitable for production — values are stored as plain strings in memory only.
 */
class FakeSecurePreferences : SecureStoragePort, TokenStorage {
    private val store = mutableMapOf<String, String>()

    override fun put(key: String, value: String) { store[key] = value }
    override fun get(key: String): String? = store[key]
    override fun remove(key: String) { store.remove(key) }
    override fun clear() { store.clear() }
    override fun contains(key: String): Boolean = store.containsKey(key)
}
