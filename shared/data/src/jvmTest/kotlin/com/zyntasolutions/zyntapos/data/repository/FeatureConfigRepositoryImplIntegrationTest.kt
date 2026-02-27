package com.zyntasolutions.zyntapos.data.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.ZyntaEdition
import com.zyntasolutions.zyntapos.domain.model.ZyntaFeature
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * ZyntaPOS — FeatureRegistryRepositoryImpl Integration Tests (jvmTest)
 *
 * Validates [FeatureRegistryRepositoryImpl] against a real in-memory SQLite database
 * ([createTestDatabase]) — no mocking. Exercises the full SQL round-trip for
 * feature flag CRUD, initDefaults seeding, idempotency, and enable/disable toggling.
 *
 * Coverage:
 *  1. initDefaults populates all 23 ZyntaFeature entries
 *  2. STANDARD features are enabled by default after initDefaults
 *  3. PREMIUM features are enabled by default after initDefaults
 *  4. ENTERPRISE features are disabled by default after initDefaults
 *  5. setEnabled toggles a feature from disabled to enabled
 *  6. setEnabled toggles a feature from enabled to disabled
 *  7. initDefaults is idempotent — calling it twice does not duplicate rows
 *  8. initDefaults does not overwrite rows modified by setEnabled
 *  9. isEnabled returns false for unknown features before initDefaults
 * 10. setEnabled returns Result.Success on success
 */
class FeatureConfigRepositoryImplIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: FeatureRegistryRepositoryImpl

    @BeforeTest
    fun setUp() {
        db   = createTestDatabase()
        repo = FeatureRegistryRepositoryImpl(
            db           = db,
            syncEnqueuer = SyncEnqueuer(db),
        )
    }

    // ── 1. initDefaults populates all 23 ZyntaFeature entries ────────────────

    @Test
    fun initDefaults_populates_all_ZyntaFeature_entries() = runTest {
        val result = repo.initDefaults(1000L)
        assertIs<Result.Success<Unit>>(result)

        val configs = db.featureConfigQueries.getAll().executeAsList()
        assertEquals(
            ZyntaFeature.entries.size,
            configs.size,
            "Expected ${ZyntaFeature.entries.size} rows but found ${configs.size}",
        )
    }

    // ── 2. STANDARD features enabled by default ───────────────────────────────

    @Test
    fun STANDARD_features_are_enabled_by_default_after_initDefaults() = runTest {
        repo.initDefaults(1000L)

        val standardFeatures = ZyntaFeature.entries.filter { it.edition == ZyntaEdition.STANDARD }
        assertTrue(standardFeatures.isNotEmpty(), "There should be at least one STANDARD feature")

        standardFeatures.forEach { feature ->
            assertTrue(
                repo.isEnabled(feature),
                "STANDARD feature ${feature.name} should be enabled after initDefaults",
            )
        }
    }

    // ── 3. PREMIUM features enabled by default ────────────────────────────────

    @Test
    fun PREMIUM_features_are_enabled_by_default_after_initDefaults() = runTest {
        repo.initDefaults(1000L)

        val premiumFeatures = ZyntaFeature.entries.filter { it.edition == ZyntaEdition.PREMIUM }
        assertTrue(premiumFeatures.isNotEmpty(), "There should be at least one PREMIUM feature")

        premiumFeatures.forEach { feature ->
            assertTrue(
                repo.isEnabled(feature),
                "PREMIUM feature ${feature.name} should be enabled after initDefaults",
            )
        }
    }

    // ── 4. ENTERPRISE features disabled by default ────────────────────────────

    @Test
    fun ENTERPRISE_features_are_disabled_by_default_after_initDefaults() = runTest {
        repo.initDefaults(1000L)

        val enterpriseFeatures = ZyntaFeature.entries.filter { it.edition == ZyntaEdition.ENTERPRISE }
        assertTrue(enterpriseFeatures.isNotEmpty(), "There should be at least one ENTERPRISE feature")

        enterpriseFeatures.forEach { feature ->
            assertFalse(
                repo.isEnabled(feature),
                "ENTERPRISE feature ${feature.name} should be disabled after initDefaults",
            )
        }
    }

    // ── 5. setEnabled toggles a feature from disabled to enabled ─────────────

    @Test
    fun setEnabled_enables_previously_disabled_ENTERPRISE_feature() = runTest {
        repo.initDefaults(1000L)

        val target = ZyntaFeature.STAFF_HR
        assertFalse(repo.isEnabled(target), "STAFF_HR should be disabled before toggle")

        val result = repo.setEnabled(
            feature     = target,
            enabled     = true,
            activatedAt = 2000L,
            expiresAt   = null,
        )
        assertIs<Result.Success<Unit>>(result)
        assertTrue(repo.isEnabled(target), "STAFF_HR should be enabled after setEnabled(true)")
    }

    // ── 6. setEnabled disables a feature ─────────────────────────────────────

    @Test
    fun setEnabled_disables_a_STANDARD_feature() = runTest {
        repo.initDefaults(1000L)

        val target = ZyntaFeature.POS_CORE
        assertTrue(repo.isEnabled(target), "POS_CORE should be enabled after initDefaults")

        val result = repo.setEnabled(
            feature     = target,
            enabled     = false,
            activatedAt = 3000L,
            expiresAt   = null,
        )
        assertIs<Result.Success<Unit>>(result)
        assertFalse(repo.isEnabled(target), "POS_CORE should be disabled after setEnabled(false)")
    }

    // ── 7. initDefaults is idempotent ─────────────────────────────────────────

    @Test
    fun initDefaults_is_idempotent_calling_twice_does_not_duplicate_rows() = runTest {
        repo.initDefaults(1000L)
        repo.initDefaults(2000L)

        val count = db.featureConfigQueries.getAll().executeAsList().size
        assertEquals(
            ZyntaFeature.entries.size,
            count,
            "Row count should equal feature count even after two initDefaults calls",
        )
    }

    // ── 8. initDefaults does not overwrite rows set by setEnabled ─────────────

    @Test
    fun initDefaults_does_not_overwrite_row_modified_by_setEnabled() = runTest {
        repo.initDefaults(1000L)

        // Activate an ENTERPRISE feature (normally disabled)
        repo.setEnabled(ZyntaFeature.ADMIN, enabled = true, activatedAt = 5000L, expiresAt = null)
        assertTrue(repo.isEnabled(ZyntaFeature.ADMIN), "ADMIN should be enabled after setEnabled(true)")

        // Second initDefaults should NOT reset the row to disabled (INSERT OR IGNORE)
        repo.initDefaults(9000L)
        assertTrue(
            repo.isEnabled(ZyntaFeature.ADMIN),
            "ADMIN should still be enabled after second initDefaults (INSERT OR IGNORE guards it)",
        )
    }

    // ── 9. isEnabled returns false before initDefaults ────────────────────────

    @Test
    fun isEnabled_returns_false_for_feature_row_that_does_not_exist_yet() = runTest {
        // Nothing seeded — all calls should return false rather than throw
        ZyntaFeature.entries.forEach { feature ->
            assertFalse(
                repo.isEnabled(feature),
                "isEnabled should return false when no row exists for ${feature.name}",
            )
        }
    }

    // ── 10. setEnabled returns Result.Success ─────────────────────────────────

    @Test
    fun setEnabled_returns_Result_Success_on_successful_write() = runTest {
        repo.initDefaults(1000L)
        val result = repo.setEnabled(
            feature     = ZyntaFeature.MULTISTORE,
            enabled     = true,
            activatedAt = 4000L,
            expiresAt   = 99999L,
        )
        assertIs<Result.Success<Unit>>(result)

        // Verify expiresAt was persisted
        val row = db.featureConfigQueries.getById(ZyntaFeature.MULTISTORE.name).executeAsOneOrNull()
        assertEquals(99999L, row?.expires_at, "expiresAt should be persisted as 99999L")
    }
}
