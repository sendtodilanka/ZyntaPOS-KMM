package com.zyntasolutions.zyntapos.security

import com.zyntasolutions.zyntapos.security.prefs.SecurePreferences

/**
 * In-memory [SecurePreferences] stub for unit tests.
 *
 * Not suitable for production — values are stored as plain strings.
 * Only used in commonTest to avoid expect/actual resolution complexity.
 */
class FakeSecurePreferences : SecurePreferences() {
    private val store = mutableMapOf<String, String>()
    override fun put(key: String, value: String) { store[key] = value }
    override fun get(key: String): String? = store[key]
    override fun remove(key: String) { store.remove(key) }
    override fun clear() { store.clear() }
}
