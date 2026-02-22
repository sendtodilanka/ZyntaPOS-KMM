package com.zyntasolutions.zyntapos.data.local.db

import com.zyntasolutions.zyntapos.core.logger.ZyntaLogger
import com.zyntasolutions.zyntapos.domain.port.SecureStorageKeys
import com.zyntasolutions.zyntapos.domain.port.SecureStoragePort

// ZENTA-FINAL-AUDIT MERGED-F1 | MERGED-F3 (2026-02-22)
/**
 * One-time migration utility that rewrites secure-preference entries stored under
 * legacy (bare) key strings into the canonical dotted-namespace keys defined in
 * [SecureStorageKeys].
 *
 * ## Background
 * Prior to Sprint 8 / ZENTA-FINAL-AUDIT MERGED-F1, the `:shared:security` actual
 * implementations used bare key literals (`"access_token"`, `"refresh_token"`, …)
 * while `:shared:data` used dotted-namespace literals (`"auth.access_token"`, …).
 * Any user who stored data under the old bare keys would be silently force-logged-out
 * after the canonical-key upgrade because reads would target the new keys and return
 * `null`.
 *
 * ## MERGED-F3 (2026-02-22)
 * Constructor type changed from `SecurePreferences` (`:shared:security`) to
 * [SecureStoragePort] (`:shared:domain`) so `:shared:data` holds no compile-time
 * dependency on `:shared:security`. Key constants migrated from `SecurePreferencesKeys`
 * to [SecureStorageKeys].
 *
 * ## When to run
 * Call [migrate] **once** during application startup, before any auth operation, on
 * the first launch after upgrading to the canonical-key build.  A safe pattern:
 *
 * ```kotlin
 * // In Application.onCreate() or the Koin app-start module
 * SecurePreferencesKeyMigration(secureStorage).migrate()
 * ```
 *
 * [migrate] is idempotent — it checks whether the legacy key still holds a value and
 * skips the migration for keys that have already been moved (or were never written).
 *
 * ## Scope
 * Only the five keys that diverged between the two old implementations are handled.
 * The `KEY_DEVICE_ID` key (`"auth.device_id"`) was new in the canonical set and has
 * no legacy counterpart — it requires no migration.
 *
 * @param prefs The [SecureStoragePort] instance (injected via Koin).
 */
class SecurePreferencesKeyMigration(
    private val prefs: SecureStoragePort,
) {

    /**
     * Mapping of `legacyKey → canonicalKey` for each entry that must be migrated.
     *
     * Bare-key format (old `:shared:security` actuals) → dotted-namespace (canonical).
     */
    private val migrations: List<Pair<String, String>> = listOf(
        "access_token"  to SecureStorageKeys.KEY_ACCESS_TOKEN,   // auth.access_token
        "refresh_token" to SecureStorageKeys.KEY_REFRESH_TOKEN,   // auth.refresh_token
        "device_id"     to SecureStorageKeys.KEY_DEVICE_ID,       // auth.device_id
        "last_user_id"  to SecureStorageKeys.KEY_USER_ID,         // auth.user_id
    )

    /**
     * Executes the key migration.
     *
     * For each legacy→canonical pair:
     * 1. Reads the value stored under the legacy key.
     * 2. If a value is found AND the canonical key is not yet populated, writes the
     *    value under the canonical key.
     * 3. Removes the legacy key regardless (even if the canonical key was already set,
     *    to avoid stale data lingering under the old key name).
     *
     * This method is synchronous and must be called from a coroutine dispatcher or
     * background thread if the [SecureStoragePort] implementation performs I/O.
     */
    fun migrate() {
        var migratedCount = 0
        migrations.forEach { (legacyKey, canonicalKey) ->
            val legacyValue = prefs.get(legacyKey)
            if (legacyValue != null) {
                // Only write to canonical if not already set (don't overwrite a newer token)
                if (!prefs.contains(canonicalKey)) {
                    prefs.put(canonicalKey, legacyValue)
                    ZyntaLogger.i(TAG, "Migrated key: \"$legacyKey\" → \"$canonicalKey\"")
                    migratedCount++
                } else {
                    ZyntaLogger.d(TAG, "Canonical key \"$canonicalKey\" already populated; skipping migration of \"$legacyKey\"")
                }
                // Always remove the stale legacy key
                prefs.remove(legacyKey)
            }
        }
        ZyntaLogger.i(TAG, "Migration complete. Keys migrated: $migratedCount / ${migrations.size}")
    }

    private companion object {
        private const val TAG = "SecurePreferencesKeyMigration"
    }
}
