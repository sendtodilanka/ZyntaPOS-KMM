package com.zyntasolutions.zyntapos.data.repository

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.RegionalTaxOverride
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.test.runTest

/**
 * ZyntaPOS — RegionalTaxOverrideRepositoryImpl Integration Tests (jvmTest)
 *
 * Validates [RegionalTaxOverrideRepositoryImpl] against a real in-memory SQLite database.
 * No FK constraints on regional_tax_overrides — no pre-seeding required.
 *
 * Coverage:
 *  A. upsert → getOverridesForStore round-trip via Turbine
 *  B. getOverridesForStore excludes overrides for other stores
 *  C. getOverridesForTaxGroup returns all overrides for a tax group
 *  D. getEffectiveOverride returns active override within validity window
 *  E. getEffectiveOverride returns null when no override matches
 *  F. getEffectiveOverride returns null when override is inactive
 *  G. delete removes the override
 */
class RegionalTaxOverrideRepositoryImplIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: RegionalTaxOverrideRepositoryImpl

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        repo = RegionalTaxOverrideRepositoryImpl(db, SyncEnqueuer(db))
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private val now get() = Clock.System.now().toEpochMilliseconds()

    private fun makeOverride(
        id: String = "rto-01",
        taxGroupId: String = "tg-01",
        storeId: String = "store-01",
        effectiveRate: Double = 0.12,
        jurisdictionCode: String = "WP",
        taxRegistrationNumber: String = "TRN-001",
        validFrom: Long? = null,
        validTo: Long? = null,
        isActive: Boolean = true,
        createdAt: Long = 0L,
    ) = RegionalTaxOverride(
        id = id,
        taxGroupId = taxGroupId,
        storeId = storeId,
        effectiveRate = effectiveRate,
        jurisdictionCode = jurisdictionCode,
        taxRegistrationNumber = taxRegistrationNumber,
        validFrom = validFrom,
        validTo = validTo,
        isActive = isActive,
        createdAt = createdAt,
        updatedAt = 0L,
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `A - upsert then getOverridesForStore round-trip via Turbine`() = runTest {
        repo.upsert(makeOverride(id = "rto-01", storeId = "store-01", effectiveRate = 0.12))
        repo.upsert(makeOverride(id = "rto-02", storeId = "store-01", taxGroupId = "tg-02", effectiveRate = 0.08))

        repo.getOverridesForStore("store-01").test {
            val list = awaitItem()
            assertEquals(2, list.size)
            assertTrue(list.any { it.id == "rto-01" && it.effectiveRate == 0.12 })
            assertTrue(list.any { it.id == "rto-02" && it.effectiveRate == 0.08 })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `B - getOverridesForStore excludes overrides for other stores`() = runTest {
        repo.upsert(makeOverride(id = "rto-01", storeId = "store-01"))
        repo.upsert(makeOverride(id = "rto-02", storeId = "store-02"))

        repo.getOverridesForStore("store-01").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("store-01", list.first().storeId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `C - getOverridesForTaxGroup returns all overrides for a tax group`() = runTest {
        repo.upsert(makeOverride(id = "rto-01", taxGroupId = "tg-01", storeId = "store-01"))
        repo.upsert(makeOverride(id = "rto-02", taxGroupId = "tg-01", storeId = "store-02"))
        repo.upsert(makeOverride(id = "rto-03", taxGroupId = "tg-02", storeId = "store-01"))

        repo.getOverridesForTaxGroup("tg-01").test {
            val list = awaitItem()
            assertEquals(2, list.size)
            assertTrue(list.all { it.taxGroupId == "tg-01" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `D - getEffectiveOverride returns active override within validity window`() = runTest {
        val current = now
        val past = current - 100_000L
        val future = current + 100_000L

        repo.upsert(makeOverride(
            id = "rto-01",
            taxGroupId = "tg-01",
            storeId = "store-01",
            effectiveRate = 0.15,
            validFrom = past,
            validTo = future,
            isActive = true,
        ))

        val result = repo.getEffectiveOverride("tg-01", "store-01", current)
        assertIs<Result.Success<RegionalTaxOverride?>>(result)
        assertNotNull(result.data)
        assertEquals("rto-01", result.data!!.id)
        assertEquals(0.15, result.data!!.effectiveRate)
    }

    @Test
    fun `E - getEffectiveOverride returns null when no override matches`() = runTest {
        val result = repo.getEffectiveOverride("non-existent-tg", "store-01", now)
        assertIs<Result.Success<RegionalTaxOverride?>>(result)
        assertNull(result.data)
    }

    @Test
    fun `F - getEffectiveOverride returns null for inactive override`() = runTest {
        val current = now
        val past = current - 100_000L
        val future = current + 100_000L

        repo.upsert(makeOverride(
            id = "rto-inactive",
            taxGroupId = "tg-01",
            storeId = "store-01",
            effectiveRate = 0.15,
            validFrom = past,
            validTo = future,
            isActive = false,  // inactive
        ))

        val result = repo.getEffectiveOverride("tg-01", "store-01", current)
        assertIs<Result.Success<RegionalTaxOverride?>>(result)
        assertNull(result.data)
    }

    @Test
    fun `G - delete removes the override`() = runTest {
        repo.upsert(makeOverride(id = "rto-01", storeId = "store-01"))
        repo.upsert(makeOverride(id = "rto-02", storeId = "store-01", taxGroupId = "tg-02"))

        val deleteResult = repo.delete("rto-01")
        assertIs<Result.Success<Unit>>(deleteResult)

        repo.getOverridesForStore("store-01").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("rto-02", list.first().id)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
