package com.zyntasolutions.zyntapos.data

import com.zyntasolutions.zyntapos.data.local.db.SecurePreferencesKeyMigration
import com.zyntasolutions.zyntapos.data.sync.InMemorySecurePreferences
import com.zyntasolutions.zyntapos.domain.port.SecureStorageKeys
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ZyntaPOS — SecurePreferencesKeyMigration Unit Tests (jvmTest)
 *
 * Validates the one-time key migration from legacy bare-string keys to canonical
 * dotted-namespace keys defined in [SecureStorageKeys].
 *
 * Coverage:
 *  A. legacy access_token migrated to canonical key when canonical is absent
 *  B. legacy refresh_token migrated to canonical key when canonical is absent
 *  C. legacy key removed after migration (even when canonical was already set)
 *  D. canonical key NOT overwritten when it already holds a value
 *  E. migration is a no-op when no legacy keys exist
 *  F. all 4 legacy keys are migrated in a single call
 *  G. legacy key removed even when canonical was already populated (no double-write)
 *  H. idempotent — second call has no effect
 */
class SecurePreferencesKeyMigrationTest {

    private fun makePrefs() = InMemorySecurePreferences()
    private fun makeMigration(prefs: InMemorySecurePreferences) =
        SecurePreferencesKeyMigration(prefs)

    // ── A — access_token migrated ─────────────────────────────────────────────

    @Test
    fun `A - legacy access_token is migrated to canonical key`() {
        val prefs = makePrefs()
        prefs.put("access_token", "eyJlegacy")

        makeMigration(prefs).migrate()

        assertEquals("eyJlegacy", prefs.get(SecureStorageKeys.KEY_ACCESS_TOKEN))
    }

    // ── B — refresh_token migrated ────────────────────────────────────────────

    @Test
    fun `B - legacy refresh_token is migrated to canonical key`() {
        val prefs = makePrefs()
        prefs.put("refresh_token", "refresh-legacy-token")

        makeMigration(prefs).migrate()

        assertEquals("refresh-legacy-token", prefs.get(SecureStorageKeys.KEY_REFRESH_TOKEN))
    }

    // ── C — legacy key removed after migration ────────────────────────────────

    @Test
    fun `C - legacy key is removed after successful migration`() {
        val prefs = makePrefs()
        prefs.put("access_token", "token123")

        makeMigration(prefs).migrate()

        assertFalse(prefs.contains("access_token"), "Legacy key must be removed after migration")
    }

    // ── D — canonical key not overwritten when already set ────────────────────

    @Test
    fun `D - canonical key is not overwritten when it already holds a value`() {
        val prefs = makePrefs()
        prefs.put("access_token", "old-legacy-token")
        prefs.put(SecureStorageKeys.KEY_ACCESS_TOKEN, "current-canonical-token")

        makeMigration(prefs).migrate()

        assertEquals(
            "current-canonical-token",
            prefs.get(SecureStorageKeys.KEY_ACCESS_TOKEN),
            "Canonical key must not be overwritten by migration",
        )
    }

    // ── E — no-op when no legacy keys exist ──────────────────────────────────

    @Test
    fun `E - migration is no-op when no legacy keys are present`() {
        val prefs = makePrefs()
        // No legacy keys — canonical keys also empty

        makeMigration(prefs).migrate()

        assertNull(prefs.get(SecureStorageKeys.KEY_ACCESS_TOKEN))
        assertNull(prefs.get(SecureStorageKeys.KEY_REFRESH_TOKEN))
        assertNull(prefs.get(SecureStorageKeys.KEY_USER_ID))
        assertNull(prefs.get(SecureStorageKeys.KEY_DEVICE_ID))
    }

    // ── F — all 4 legacy keys migrated ───────────────────────────────────────

    @Test
    fun `F - all 4 legacy keys are migrated in a single call`() {
        val prefs = makePrefs()
        prefs.put("access_token", "tok-access")
        prefs.put("refresh_token", "tok-refresh")
        prefs.put("device_id", "device-123")
        prefs.put("last_user_id", "user-456")

        makeMigration(prefs).migrate()

        assertEquals("tok-access", prefs.get(SecureStorageKeys.KEY_ACCESS_TOKEN))
        assertEquals("tok-refresh", prefs.get(SecureStorageKeys.KEY_REFRESH_TOKEN))
        assertEquals("device-123", prefs.get(SecureStorageKeys.KEY_DEVICE_ID))
        assertEquals("user-456", prefs.get(SecureStorageKeys.KEY_USER_ID))
    }

    // ── G — legacy key removed even when canonical already populated ──────────

    @Test
    fun `G - legacy key is removed even when canonical is already set`() {
        val prefs = makePrefs()
        prefs.put("access_token", "stale-legacy")
        prefs.put(SecureStorageKeys.KEY_ACCESS_TOKEN, "fresh-canonical")

        makeMigration(prefs).migrate()

        assertFalse(
            prefs.contains("access_token"),
            "Legacy key must be deleted even when canonical key was already populated",
        )
        assertEquals("fresh-canonical", prefs.get(SecureStorageKeys.KEY_ACCESS_TOKEN))
    }

    // ── H — idempotent ────────────────────────────────────────────────────────

    @Test
    fun `H - second migrate call has no effect`() {
        val prefs = makePrefs()
        prefs.put("access_token", "first-run")

        val migration = makeMigration(prefs)
        migration.migrate()  // first call — moves value, removes legacy
        migration.migrate()  // second call — should be no-op

        assertEquals("first-run", prefs.get(SecureStorageKeys.KEY_ACCESS_TOKEN))
        assertFalse(prefs.contains("access_token"), "Legacy key must still be absent after second call")
    }
}
