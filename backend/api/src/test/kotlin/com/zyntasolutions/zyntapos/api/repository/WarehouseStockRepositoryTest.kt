package com.zyntasolutions.zyntapos.api.repository

import com.zyntasolutions.zyntapos.api.test.AbstractIntegrationTest
import com.zyntasolutions.zyntapos.api.test.TestFixtures
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Integration tests for [WarehouseStockRepository] against a real PostgreSQL database.
 *
 * Covers: getByWarehouse, getByProduct, getByStore, getGlobal, upsert, and isLowStock.
 */
class WarehouseStockRepositoryTest : AbstractIntegrationTest() {

    private val repo = WarehouseStockRepository()

    @Nested
    inner class GetByWarehouse {

        @Test
        fun `getByWarehouse_noRows_returnsEmpty`() = runTest {
            val result = repo.getByWarehouse("wh-none")
            assertTrue(result.isEmpty())
        }

        @Test
        fun `getByWarehouse_returnsOnlyMatchingRows`() = runTest {
            TestFixtures.insertWarehouseStock(warehouseId = "wh-a", productId = "p1", storeId = "s1")
            TestFixtures.insertWarehouseStock(warehouseId = "wh-b", productId = "p2", storeId = "s1")

            val result = repo.getByWarehouse("wh-a")

            assertEquals(1, result.size)
            assertEquals("p1", result.single().productId)
        }

        @Test
        fun `getByWarehouse_orderedByProductId`() = runTest {
            TestFixtures.insertWarehouseStock(warehouseId = "wh-x", productId = "p-z", storeId = "s1")
            TestFixtures.insertWarehouseStock(warehouseId = "wh-x", productId = "p-a", storeId = "s1")

            val result = repo.getByWarehouse("wh-x")

            assertEquals(2, result.size)
            assertEquals("p-a", result[0].productId)
            assertEquals("p-z", result[1].productId)
        }
    }

    @Nested
    inner class GetByProduct {

        @Test
        fun `getByProduct_noRows_returnsEmpty`() = runTest {
            val result = repo.getByProduct("prod-none")
            assertTrue(result.isEmpty())
        }

        @Test
        fun `getByProduct_returnsAcrossWarehouses`() = runTest {
            TestFixtures.insertWarehouseStock(warehouseId = "wh-1", productId = "shared-prod", storeId = "s1")
            TestFixtures.insertWarehouseStock(warehouseId = "wh-2", productId = "shared-prod", storeId = "s1")
            TestFixtures.insertWarehouseStock(warehouseId = "wh-1", productId = "other-prod", storeId = "s1")

            val result = repo.getByProduct("shared-prod")

            assertEquals(2, result.size)
            assertTrue(result.all { it.productId == "shared-prod" })
        }
    }

    @Nested
    inner class GetByStore {

        @Test
        fun `getByStore_noRows_returnsEmpty`() = runTest {
            val result = repo.getByStore("store-none", null)
            assertTrue(result.isEmpty())
        }

        @Test
        fun `getByStore_filtersToCorrectStore`() = runTest {
            TestFixtures.insertWarehouseStock(warehouseId = "wh-1", productId = "p1", storeId = "store-a")
            TestFixtures.insertWarehouseStock(warehouseId = "wh-2", productId = "p2", storeId = "store-b")

            val result = repo.getByStore("store-a", null)

            assertEquals(1, result.size)
            assertEquals("p1", result.single().productId)
        }

        @Test
        fun `getByStore_withProductIdFilter_returnsSubset`() = runTest {
            TestFixtures.insertWarehouseStock(warehouseId = "wh-1", productId = "p1", storeId = "store-a")
            TestFixtures.insertWarehouseStock(warehouseId = "wh-2", productId = "p2", storeId = "store-a")

            val result = repo.getByStore("store-a", "p1")

            assertEquals(1, result.size)
            assertEquals("p1", result.single().productId)
        }

        @Test
        fun `getByStore_withNullProductId_returnsAllForStore`() = runTest {
            TestFixtures.insertWarehouseStock(warehouseId = "wh-1", productId = "p1", storeId = "store-a")
            TestFixtures.insertWarehouseStock(warehouseId = "wh-2", productId = "p2", storeId = "store-a")

            val result = repo.getByStore("store-a", null)

            assertEquals(2, result.size)
        }
    }

    @Nested
    inner class GetGlobal {

        @Test
        fun `getGlobal_noRows_returnsEmpty`() = runTest {
            val result = repo.getGlobal(null)
            assertTrue(result.isEmpty())
        }

        @Test
        fun `getGlobal_noFilter_returnsAllRows`() = runTest {
            TestFixtures.insertWarehouseStock(warehouseId = "wh-1", productId = "pa", storeId = "s1")
            TestFixtures.insertWarehouseStock(warehouseId = "wh-2", productId = "pb", storeId = "s2")

            val result = repo.getGlobal(null)

            assertEquals(2, result.size)
        }

        @Test
        fun `getGlobal_withProductId_filtersCorrectly`() = runTest {
            TestFixtures.insertWarehouseStock(warehouseId = "wh-1", productId = "match", storeId = "s1")
            TestFixtures.insertWarehouseStock(warehouseId = "wh-2", productId = "other", storeId = "s1")

            val result = repo.getGlobal("match")

            assertEquals(1, result.size)
            assertEquals("match", result.single().productId)
        }

        @Test
        fun `getGlobal_orderedByStoreIdThenWarehouseId`() = runTest {
            TestFixtures.insertWarehouseStock(id = "ws-3", warehouseId = "wh-b", productId = "p1", storeId = "s2")
            TestFixtures.insertWarehouseStock(id = "ws-1", warehouseId = "wh-a", productId = "p1", storeId = "s1")
            TestFixtures.insertWarehouseStock(id = "ws-2", warehouseId = "wh-b", productId = "p1", storeId = "s1")

            val result = repo.getGlobal("p1")

            assertEquals(3, result.size)
            assertEquals("s1", result[0].storeId)
            assertEquals("wh-a", result[0].warehouseId)
            assertEquals("s1", result[1].storeId)
            assertEquals("wh-b", result[1].warehouseId)
            assertEquals("s2", result[2].storeId)
        }
    }

    @Nested
    inner class Upsert {

        @Test
        fun `upsert_insertsNewRow`() = runTest {
            val row = WarehouseStockRow(
                id = "ws-new",
                warehouseId = "wh-1",
                productId = "p1",
                storeId = "s1",
                quantity = BigDecimal("25.0000"),
                minQuantity = BigDecimal("5.0000"),
                syncVersion = 1L,
                updatedAt = System.currentTimeMillis(),
            )

            repo.upsert(row)

            val result = repo.getByWarehouse("wh-1")
            assertEquals(1, result.size)
            assertEquals(BigDecimal("25.0000"), result.single().quantity)
        }

        @Test
        fun `upsert_updatesExistingRow`() = runTest {
            TestFixtures.insertWarehouseStock(
                id = "ws-existing",
                warehouseId = "wh-1",
                productId = "p1",
                storeId = "s1",
                quantity = BigDecimal("10.0000"),
            )

            val updated = WarehouseStockRow(
                id = "ws-existing",
                warehouseId = "wh-1",
                productId = "p1",
                storeId = "s1",
                quantity = BigDecimal("50.0000"),
                minQuantity = BigDecimal("0.0000"),
                syncVersion = 2L,
                updatedAt = System.currentTimeMillis(),
            )
            repo.upsert(updated)

            val result = repo.getByWarehouse("wh-1")
            assertEquals(1, result.size)
            assertEquals(BigDecimal("50.0000"), result.single().quantity)
        }
    }

    @Nested
    inner class IsLowStock {

        @Test
        fun `isLowStock_quantityBelowMin_returnsTrue`() = runTest {
            val id = TestFixtures.insertWarehouseStock(
                warehouseId = "wh-1",
                productId = "p1",
                storeId = "s1",
                quantity = BigDecimal("3.0000"),
                minQuantity = BigDecimal("5.0000"),
            )

            val rows = repo.getByWarehouse("wh-1")
            val row = rows.single { it.id == id }

            assertTrue(row.isLowStock)
        }

        @Test
        fun `isLowStock_quantityAboveMin_returnsFalse`() = runTest {
            val id = TestFixtures.insertWarehouseStock(
                warehouseId = "wh-2",
                productId = "p2",
                storeId = "s1",
                quantity = BigDecimal("10.0000"),
                minQuantity = BigDecimal("5.0000"),
            )

            val rows = repo.getByWarehouse("wh-2")
            val row = rows.single { it.id == id }

            assertFalse(row.isLowStock)
        }

        @Test
        fun `isLowStock_zeroMinQuantity_returnsFalse`() = runTest {
            val id = TestFixtures.insertWarehouseStock(
                warehouseId = "wh-3",
                productId = "p3",
                storeId = "s1",
                quantity = BigDecimal("1.0000"),
                minQuantity = BigDecimal("0.0000"),
            )

            val rows = repo.getByWarehouse("wh-3")
            val row = rows.single { it.id == id }

            assertFalse(row.isLowStock)
        }
    }

    private fun runTest(block: suspend () -> Unit) {
        kotlinx.coroutines.test.runTest { block() }
    }
}
