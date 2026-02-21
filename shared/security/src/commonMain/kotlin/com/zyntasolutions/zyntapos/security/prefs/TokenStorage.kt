package com.zyntasolutions.zyntapos.security.prefs

/**
 * Minimal interface for key-value token storage.
 *
 * Introduced so [com.zyntasolutions.zyntapos.security.auth.JwtManager] can be
 * tested in `commonTest` without depending on the `expect class` [SecurePreferences].
 *
 * [SecurePreferences] implements this interface on all platforms.
 */
interface TokenStorage {
    /** Stores [value] under [key]. */
    fun put(key: String, value: String)
    /** Returns the stored value for [key], or `null` if absent. */
    fun get(key: String): String?
    /** Removes the entry for [key]. No-op if absent. */
    fun remove(key: String)
}
