package com.zyntasolutions.zyntapos.data.repository

import app.cash.turbine.test
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
import kotlin.time.Clock
import kotlinx.coroutines.test.runTest

/**
 * ZyntaPOS — FeatureRegistryRepositoryImpl Integration Tests (jvmTest)
 *
 * Validates [FeatureRegistryRepositoryImpl] against a real in-memory SQLite database.
 * No FK constraints on feature_config — no pre-seeding required.
 *
 * Coverage:
 *  A. initDefaults seeds all ZyntaFeature entries
 *  B. initDefaults is idempotent (safe to call twice)
 *  C. setEnabled enables a feature, isEnabled returns true
 *  D. setEnabled disables a feature, isEnabled returns false
 *  E. observeAll emits all feature configs via Turbine
 *  F. observe emits a single feature config via Turbine
 *  G. STANDARD edition features enabled by default after initDefaults
 */
class FeatureRegistryRepositoryImplIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: FeatureRegistryRepositoryImpl

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        repo = FeatureRegistryRepositoryImpl(db, SyncEnqueuer(db))
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private val now get() = Clock.System.now().toEpochMilliseconds()

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `A - initDefaults seeds all ZyntaFeature entries`() = runTest {
        val result = repo.initDefaults(now)
        assertIs<Result.Success<Unit>>(result)

        repo.observeAll().test {
            val list = awaitItem()
            assertEquals(ZyntaFeature.entries.size, list.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `B - initDefaults is idempotent when called twice`() = runTest {
        repo.initDefaults(now)
        repo.initDefaults(now)

        repo.observeAll().test {
            val list = awaitItem()
            // Should still be exactly ZyntaFeature.entries.size, not doubled
            assertEquals(ZyntaFeature.entries.size, list.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `C - setEnabled enables a feature and isEnabled returns true`() = runTest {
        repo.initDefaults(now)

        // Find an ENTERPRISE feature (disabled by default)
        val enterpriseFeature = ZyntaFeature.entries.firstOrNull { it.edition == ZyntaEdition.ENTERPRISE }
            ?: return@runTest // skip if none exist

        val result = repo.setEnabled(enterpriseFeature, enabled = true, activatedAt = now, expiresAt = null)
        assertIs<Result.Success<Unit>>(result)

        assertTrue(repo.isEnabled(enterpriseFeature))
    }

    @Test
    fun `D - setEnabled disables a feature and isEnabled returns false`() = runTest {
        repo.initDefaults(now)

        // Find a STANDARD feature (enabled by default)
        val standardFeature = ZyntaFeature.entries.firstOrNull { it.edition == ZyntaEdition.STANDARD }
            ?: return@runTest // skip if none exist

        // First verify it's enabled
        assertTrue(repo.isEnabled(standardFeature))

        val result = repo.setEnabled(standardFeature, enabled = false, activatedAt = now, expiresAt = null)
        assertIs<Result.Success<Unit>>(result)

        assertFalse(repo.isEnabled(standardFeature))
    }

    @Test
    fun `E - observeAll emits feature configs via Turbine`() = runTest {
        repo.initDefaults(now)

        repo.observeAll().test {
            val list = awaitItem()
            assertTrue(list.isNotEmpty())
            // All features should map to a valid ZyntaFeature enum
            assertTrue(list.all { config ->
                ZyntaFeature.entries.any { it == config.feature }
            })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `F - observe emits single feature config via Turbine`() = runTest {
        repo.initDefaults(now)

        val targetFeature = ZyntaFeature.entries.first()

        repo.observe(targetFeature).test {
            val config = awaitItem()
            assertEquals(targetFeature, config.feature)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `G - STANDARD features are enabled by default after initDefaults`() = runTest {
        repo.initDefaults(now)

        val standardFeatures = ZyntaFeature.entries.filter { it.edition == ZyntaEdition.STANDARD }
        for (feature in standardFeatures) {
            assertTrue(repo.isEnabled(feature), "Expected STANDARD feature ${feature.name} to be enabled by default")
        }
    }
}
