package com.zyntasolutions.zyntapos.data.repository

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.PricingRule
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
 * ZyntaPOS — PricingRuleRepositoryImpl Integration Tests (jvmTest)
 *
 * Validates [PricingRuleRepositoryImpl] against a real in-memory SQLite database.
 * No mocks — exercises the full SQLDelight query layer.
 * pricing_rules has no FK constraints so no pre-seeding is needed.
 *
 * Coverage:
 *  A. upsert → getAllRules round-trip via Turbine
 *  B. getActiveRulesForProduct returns only active rules matching product+store
 *  C. getActiveRulesForProduct includes global rules (storeId=null)
 *  D. getEffectiveRule returns highest-priority active rule within validity window
 *  E. getEffectiveRule returns null when no rule matches
 *  F. delete removes a rule
 *  G. getRulesForProduct returns rules for a single product
 */
class PricingRuleRepositoryImplIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: PricingRuleRepositoryImpl

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        repo = PricingRuleRepositoryImpl(db)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private val now get() = Clock.System.now().toEpochMilliseconds()

    private fun makeRule(
        id: String = "rule-01",
        productId: String = "prod-01",
        storeId: String? = "store-01",
        price: Double = 100.0,
        costPrice: Double? = 60.0,
        priority: Int = 0,
        validFrom: Long? = null,
        validTo: Long? = null,
        isActive: Boolean = true,
        description: String = "Test Rule",
    ) = PricingRule(
        id = id,
        productId = productId,
        storeId = storeId,
        price = price,
        costPrice = costPrice,
        priority = priority,
        validFrom = validFrom,
        validTo = validTo,
        isActive = isActive,
        description = description,
        createdAt = 1_000_000L,
        updatedAt = 1_000_000L,
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `A - upsert then getAllRules returns rules via Turbine`() = runTest {
        repo.upsert(makeRule(id = "rule-01", productId = "prod-01"))
        repo.upsert(makeRule(id = "rule-02", productId = "prod-02"))

        repo.getAllRules().test {
            val list = awaitItem()
            assertEquals(2, list.size)
            assertTrue(list.any { it.id == "rule-01" })
            assertTrue(list.any { it.id == "rule-02" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `B - getActiveRulesForProduct returns only active rules for product and store`() = runTest {
        repo.upsert(makeRule(id = "rule-01", productId = "prod-01", storeId = "store-01", isActive = true))
        repo.upsert(makeRule(id = "rule-02", productId = "prod-01", storeId = "store-01", isActive = false))
        repo.upsert(makeRule(id = "rule-03", productId = "prod-01", storeId = "store-02", isActive = true))

        repo.getActiveRulesForProduct("prod-01", "store-01").test {
            val list = awaitItem()
            // rule-01 (active, store-01) + global rules; rule-02 inactive; rule-03 different store
            assertEquals(1, list.size)
            assertEquals("rule-01", list.first().id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `C - getActiveRulesForProduct includes global rules with null storeId`() = runTest {
        repo.upsert(makeRule(id = "rule-01", productId = "prod-01", storeId = null, isActive = true, priority = 0))
        repo.upsert(makeRule(id = "rule-02", productId = "prod-01", storeId = "store-01", isActive = true, priority = 10))

        repo.getActiveRulesForProduct("prod-01", "store-01").test {
            val list = awaitItem()
            // Both the global rule (storeId=null) and store-specific rule match
            assertEquals(2, list.size)
            assertTrue(list.any { it.id == "rule-01" && it.storeId == null })
            assertTrue(list.any { it.id == "rule-02" && it.storeId == "store-01" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `D - getEffectiveRule returns highest-priority active rule within validity window`() = runTest {
        val current = now
        val past = current - 100_000L
        val future = current + 100_000L
        // Lower priority, but still active and within window
        repo.upsert(makeRule(id = "rule-low", productId = "prod-01", storeId = "store-01",
            price = 90.0, priority = 1, validFrom = past, validTo = future))
        // Higher priority, active, within window
        repo.upsert(makeRule(id = "rule-high", productId = "prod-01", storeId = "store-01",
            price = 80.0, priority = 10, validFrom = past, validTo = future))

        val result = repo.getEffectiveRule("prod-01", "store-01", current)
        assertIs<Result.Success<PricingRule?>>(result)
        assertNotNull(result.data)
        assertEquals("rule-high", result.data!!.id)
        assertEquals(80.0, result.data!!.price)
    }

    @Test
    fun `E - getEffectiveRule returns null when no active rule matches`() = runTest {
        val result = repo.getEffectiveRule("prod-no-rule", "store-01", now)
        assertIs<Result.Success<PricingRule?>>(result)
        assertNull(result.data)
    }

    @Test
    fun `F - delete removes the rule`() = runTest {
        repo.upsert(makeRule(id = "rule-01"))

        val deleteResult = repo.delete("rule-01")
        assertIs<Result.Success<Unit>>(deleteResult)

        repo.getAllRules().test {
            val list = awaitItem()
            assertTrue(list.none { it.id == "rule-01" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `G - getRulesForProduct returns rules for a single product`() = runTest {
        repo.upsert(makeRule(id = "rule-01", productId = "prod-01"))
        repo.upsert(makeRule(id = "rule-02", productId = "prod-01"))
        repo.upsert(makeRule(id = "rule-03", productId = "prod-02"))

        repo.getRulesForProduct("prod-01").test {
            val list = awaitItem()
            assertEquals(2, list.size)
            assertTrue(list.all { it.productId == "prod-01" })
            cancelAndIgnoreRemainingEvents()
        }
    }
}
