package com.zyntasolutions.zyntapos.data.sync

import com.zyntasolutions.zyntapos.security.prefs.SecurePreferences

/**
 * ZyntaPOS — InMemorySecurePreferences (Test helper — jvmTest)
 *
 * An in-memory, non-persistent subclass of the canonical [SecurePreferences] expect class,
 * used exclusively in integration tests that need a lightweight prefs stub without
 * touching the filesystem (~/.zentapos/secure_prefs.enc).
 *
 * Relocated from `shared/data/commonMain` to `jvmTest` as part of MERGED-D3 (2026-02-21)
 * when the data-layer [SecurePreferences] interface was deleted and all consumers were
 * migrated to the canonical `security.prefs.SecurePreferences` expect class.
 *
 * **⚠️ Not suitable for production.** All data is lost when the process exits.
 */
class InMemorySecurePreferences : SecurePreferences() {

    private val store = mutableMapOf<String, String>()

    override fun put(key: String, value: String) { store[key] = value }
    override fun get(key: String): String? = store[key]
    override fun remove(key: String) { store.remove(key) }
    override fun clear() { store.clear() }
    override fun contains(key: String): Boolean = store.containsKey(key)
}
