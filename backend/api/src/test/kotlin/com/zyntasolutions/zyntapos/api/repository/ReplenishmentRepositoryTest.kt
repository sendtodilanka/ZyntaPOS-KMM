package com.zyntasolutions.zyntapos.api.repository

import com.zyntasolutions.zyntapos.api.test.AbstractIntegrationTest
import com.zyntasolutions.zyntapos.api.test.TestFixtures
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests for [ReplenishmentRepository] against a real PostgreSQL database.
 *
 * Covers: getRules, getRuleById, upsertRule (insert + update), deleteRule, getSuggestions.
 */
class ReplenishmentRepositoryTest : AbstractIntegrationTest() {

    private val repo = ReplenishmentRepository()

    @Nested
    inner class GetRules {

        @Test
        fun `getRules_noRows_returnsEmpty`() = runTest {
            val result = repo.getRules()
            assertTrue(result.isEmpty())
        }

        @Test
        fun `getRules_returnsAllRows`() = runTest {
            TestFixtures.insertReplenishmentRule(id = "rr-1", warehouseId = "wh-a")
            TestFixtures.insertReplenishmentRule(id = "rr-2", warehouseId = "wh-b")

            val result = repo.getRules()

            assertEquals(2, result.size)
        }

        @Test
        fun `getRules_withWarehouseFilter_returnsOnlyMatching`() = runTest {
            TestFixtures.insertReplenishmentRule(id = "rr-1", warehouseId = "wh-a")
            TestFixtures.insertReplenishmentRule(id = "rr-2", warehouseId = "wh-b")

            val result = repo.getRules(warehouseId = "wh-a")

            assertEquals(1, result.size)
            assertEquals("wh-a", result.single().warehouseId)
        }

        @Test
        fun `getRules_withNonMatchingWarehouse_returnsEmpty`() = runTest {
            TestFixtures.insertReplenishmentRule(warehouseId = "wh-x")

            val result = repo.getRules(warehouseId = "wh-none")

            assertTrue(result.isEmpty())
        }
    }

    @Nested
    inner class GetRuleById {

        @Test
        fun `getRuleById_existingId_returnsRow`() = runTest {
            val id = TestFixtures.insertReplenishmentRule(productId = "p1", warehouseId = "wh-1")

            val result = repo.getRuleById(id)

            assertNotNull(result)
            assertEquals(id, result.id)
            assertEquals("p1", result.productId)
        }

        @Test
        fun `getRuleById_unknownId_returnsNull`() = runTest {
            val result = repo.getRuleById("rr-not-exist")

            assertNull(result)
        }
    }

    @Nested
    inner class UpsertRule {

        @Test
        fun `upsertRule_newRule_insertsRow`() = runTest {
            val row = ReplenishmentRuleRow(
                id           = "rr-new",
                productId    = "p1",
                warehouseId  = "wh-1",
                supplierId   = "sup-1",
                reorderPoint = 10.0,
                reorderQty   = 50.0,
                autoApprove  = false,
                isActive     = true,
                createdBy    = "admin-1",
                updatedAt    = System.currentTimeMillis(),
            )

            repo.upsertRule(row)

            val saved = repo.getRuleById("rr-new")
            assertNotNull(saved)
            assertEquals("p1", saved.productId)
            assertEquals("wh-1", saved.warehouseId)
            assertEquals(10.0, saved.reorderPoint)
            assertEquals(50.0, saved.reorderQty)
        }

        @Test
        fun `upsertRule_existingRule_updatesRow`() = runTest {
            TestFixtures.insertReplenishmentRule(
                id           = "rr-existing",
                productId    = "p1",
                warehouseId  = "wh-1",
                supplierId   = "sup-original",
                reorderPoint = BigDecimal("10.0000"),
                reorderQty   = BigDecimal("50.0000"),
            )

            val updated = ReplenishmentRuleRow(
                id           = "rr-existing",
                productId    = "p1",
                warehouseId  = "wh-1",
                supplierId   = "sup-new",
                reorderPoint = 20.0,
                reorderQty   = 100.0,
                autoApprove  = true,
                isActive     = false,
                createdBy    = null,
                updatedAt    = System.currentTimeMillis(),
            )
            repo.upsertRule(updated)

            val saved = repo.getRuleById("rr-existing")
            assertNotNull(saved)
            assertEquals("sup-new", saved.supplierId)
            assertEquals(20.0, saved.reorderPoint)
            assertEquals(100.0, saved.reorderQty)
            assertEquals(true, saved.autoApprove)
            assertEquals(false, saved.isActive)
        }
    }

    @Nested
    inner class DeleteRule {

        @Test
        fun `deleteRule_existingId_deletesAndReturns1`() = runTest {
            val id = TestFixtures.insertReplenishmentRule()

            val count = repo.deleteRule(id)

            assertEquals(1, count)
            assertNull(repo.getRuleById(id))
        }

        @Test
        fun `deleteRule_nonExistingId_returns0`() = runTest {
            val count = repo.deleteRule("rr-ghost")

            assertEquals(0, count)
        }
    }

    @Nested
    inner class GetSuggestions {

        @Test
        fun `getSuggestions_noMatchingStock_returnsEmpty`() = runTest {
            // Rule exists but no matching warehouse_stock entry
            TestFixtures.insertReplenishmentRule(
                productId   = "p1",
                warehouseId = "wh-1",
                reorderPoint = BigDecimal("10.0000"),
                isActive    = true,
            )

            val result = repo.getSuggestions()

            assertTrue(result.isEmpty())
        }

        @Test
        fun `getSuggestions_stockBelowReorderPoint_returnsSuggestion`() = runTest {
            TestFixtures.insertReplenishmentRule(
                id           = "rr-trig",
                productId    = "p1",
                warehouseId  = "wh-1",
                supplierId   = "sup-1",
                reorderPoint = BigDecimal("10.0000"),
                reorderQty   = BigDecimal("50.0000"),
                autoApprove  = true,
                isActive     = true,
            )
            // Stock qty = 5 which is below reorder point 10
            TestFixtures.insertWarehouseStock(
                warehouseId = "wh-1",
                productId   = "p1",
                storeId     = "s1",
                quantity    = BigDecimal("5.0000"),
            )

            val result = repo.getSuggestions()

            assertEquals(1, result.size)
            val suggestion = result.single()
            assertEquals("rr-trig", suggestion.ruleId)
            assertEquals("p1", suggestion.productId)
            assertEquals(5.0, suggestion.currentStock)
            assertEquals(10.0, suggestion.reorderPoint)
            assertEquals(50.0, suggestion.reorderQty)
        }

        @Test
        fun `getSuggestions_stockAboveReorderPoint_returnsEmpty`() = runTest {
            TestFixtures.insertReplenishmentRule(
                productId    = "p2",
                warehouseId  = "wh-2",
                reorderPoint = BigDecimal("10.0000"),
                isActive     = true,
            )
            // Stock qty = 15 which is above reorder point 10
            TestFixtures.insertWarehouseStock(
                warehouseId = "wh-2",
                productId   = "p2",
                storeId     = "s1",
                quantity    = BigDecimal("15.0000"),
            )

            val result = repo.getSuggestions()

            assertTrue(result.isEmpty())
        }

        @Test
        fun `getSuggestions_inactiveRule_notIncluded`() = runTest {
            TestFixtures.insertReplenishmentRule(
                productId    = "p3",
                warehouseId  = "wh-3",
                reorderPoint = BigDecimal("10.0000"),
                isActive     = false,  // inactive
            )
            TestFixtures.insertWarehouseStock(
                warehouseId = "wh-3",
                productId   = "p3",
                storeId     = "s1",
                quantity    = BigDecimal("2.0000"),
            )

            val result = repo.getSuggestions()

            assertTrue(result.isEmpty())
        }

        @Test
        fun `getSuggestions_withWarehouseFilter_returnsOnlyMatchingWarehouse`() = runTest {
            TestFixtures.insertReplenishmentRule(
                id           = "rr-a",
                productId    = "p1",
                warehouseId  = "wh-a",
                reorderPoint = BigDecimal("10.0000"),
                isActive     = true,
            )
            TestFixtures.insertReplenishmentRule(
                id           = "rr-b",
                productId    = "p2",
                warehouseId  = "wh-b",
                reorderPoint = BigDecimal("10.0000"),
                isActive     = true,
            )
            TestFixtures.insertWarehouseStock(warehouseId = "wh-a", productId = "p1", storeId = "s1", quantity = BigDecimal("5.0000"))
            TestFixtures.insertWarehouseStock(warehouseId = "wh-b", productId = "p2", storeId = "s1", quantity = BigDecimal("3.0000"))

            val result = repo.getSuggestions(warehouseId = "wh-a")

            assertEquals(1, result.size)
            assertEquals("wh-a", result.single().warehouseId)
        }
    }

    private fun runTest(block: suspend () -> Unit) {
        kotlinx.coroutines.test.runTest { block() }
    }
}
