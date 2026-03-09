package com.zyntasolutions.zyntapos.data.sync

import com.zyntasolutions.zyntapos.domain.port.SecureStoragePort

/**
 * ZyntaPOS — InMemorySecurePreferences (Test helper — jvmTest)
 *
 * An in-memory, non-persistent implementation of [SecureStoragePort], used
 * exclusively in integration tests that need a lightweight prefs stub without
 * touching the filesystem (~/.zyntapos/secure_prefs.enc) or the Android Keystore.
 *
 * MERGED-F3 (2026-02-22): Changed from extending `SecurePreferences()` (expect class
 * in `:shared:security`) to implementing [SecureStoragePort] (interface in `:shared:domain`).
 * This removes the jvmTest compile dependency on `:shared:security` and makes the test
 * helper usable in commonTest as well.
 *
 * Previously relocated from `shared/data/commonMain` to `jvmTest` as part of
 * MERGED-D3 (2026-02-21) when the data-layer `SecurePreferences` interface was deleted.
 *
 * **⚠️ Not suitable for production.** All data is lost when the process exits.
 */
class InMemorySecurePreferences : SecureStoragePort {

    private val store = mutableMapOf<String, String>()

    override fun put(key: String, value: String) { store[key] = value }
    override fun get(key: String): String?        = store[key]
    override fun remove(key: String)              { store.remove(key) }
    override fun clear()                          { store.clear() }
    override fun contains(key: String): Boolean   = store.containsKey(key)
}
