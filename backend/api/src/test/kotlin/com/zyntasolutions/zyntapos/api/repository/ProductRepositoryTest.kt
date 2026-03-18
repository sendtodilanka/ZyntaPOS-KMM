package com.zyntasolutions.zyntapos.api.repository

import com.zyntasolutions.zyntapos.api.test.AbstractIntegrationTest
import com.zyntasolutions.zyntapos.api.test.TestFixtures
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests for [ProductRepositoryImpl] against a real PostgreSQL database.
 *
 * Uses Testcontainers + Flyway migrations for a production-like schema.
 */
class ProductRepositoryTest : AbstractIntegrationTest() {

    private val repo: ProductRepository = ProductRepositoryImpl()

    @Nested
    inner class List {

        @Test
        fun `list_emptyStore_returnsZeroItems`() = runTest {
            val storeId = TestFixtures.insertStore()

            val result = repo.list(storeId, page = 0, size = 10, updatedSince = null)

            assertEquals(0L, result.total)
            assertTrue(result.items.isEmpty())
        }

        @Test
        fun `list_withProducts_returnsCorrectCount`() = runTest {
            val storeId = TestFixtures.insertStore()
            repeat(5) { i ->
                TestFixtures.insertProduct(storeId = storeId, name = "Product $i")
            }

            val result = repo.list(storeId, page = 0, size = 10, updatedSince = null)

            assertEquals(5L, result.total)
            assertEquals(5, result.items.size)
        }

        @Test
        fun `list_mapsAllFieldsCorrectly`() = runTest {
            val storeId = TestFixtures.insertStore()
            val productId = TestFixtures.insertProduct(
                storeId = storeId,
                name = "Widget",
                sku = "SKU-001",
                barcode = "1234567890123",
                price = BigDecimal("19.9900"),
                costPrice = BigDecimal("10.5000"),
                stockQty = BigDecimal("42.0000"),
                categoryId = "cat-1",
                unitId = "unit-1",
                taxGroupId = "tax-1",
                minStockQty = BigDecimal("5.0000"),
                imageUrl = "https://example.com/img.png",
                description = "A fine widget",
                isActive = true,
            )

            val result = repo.list(storeId, page = 0, size = 10, updatedSince = null)
            val item = result.items.single()

            assertEquals(productId, item.id)
            assertEquals("Widget", item.name)
            assertEquals("SKU-001", item.sku)
            assertEquals("1234567890123", item.barcode)
            assertEquals(19.99, item.price, 0.01)
            assertEquals(10.50, item.costPrice, 0.01)
            assertEquals(42.0, item.stockQty, 0.01)
            assertEquals("cat-1", item.categoryId)
            assertEquals("unit-1", item.unitId)
            assertEquals("tax-1", item.taxGroupId)
            assertEquals(5.0, item.minStockQty, 0.01)
            assertEquals("https://example.com/img.png", item.imageUrl)
            assertEquals("A fine widget", item.description)
            assertEquals(true, item.isActive)
            assertEquals("SYNCED", item.syncStatus)
        }
    }

    @Nested
    inner class StoreScoping {

        @Test
        fun `list_storeA_doesNotReturnStoreBProducts`() = runTest {
            val storeA = TestFixtures.insertStore(id = "store-a", name = "Store A")
            val storeB = TestFixtures.insertStore(id = "store-b", name = "Store B")
            TestFixtures.insertProduct(storeId = storeA, name = "Product A")
            TestFixtures.insertProduct(storeId = storeB, name = "Product B")

            val resultA = repo.list(storeA, page = 0, size = 10, updatedSince = null)
            val resultB = repo.list(storeB, page = 0, size = 10, updatedSince = null)

            assertEquals(1L, resultA.total)
            assertEquals("Product A", resultA.items.single().name)
            assertEquals(1L, resultB.total)
            assertEquals("Product B", resultB.items.single().name)
        }

        @Test
        fun `list_nonExistentStore_returnsEmpty`() = runTest {
            val result = repo.list("non-existent-store", page = 0, size = 10, updatedSince = null)

            assertEquals(0L, result.total)
            assertTrue(result.items.isEmpty())
        }
    }

    @Nested
    inner class Pagination {

        @Test
        fun `list_pagination_firstPage`() = runTest {
            val storeId = TestFixtures.insertStore()
            val baseTime = OffsetDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
            repeat(10) { i ->
                TestFixtures.insertProduct(
                    storeId = storeId,
                    name = "Product $i",
                    updatedAt = baseTime.plusMinutes(i.toLong()),
                )
            }

            val result = repo.list(storeId, page = 0, size = 3, updatedSince = null)

            assertEquals(10L, result.total)
            assertEquals(3, result.items.size)
        }

        @Test
        fun `list_pagination_secondPage`() = runTest {
            val storeId = TestFixtures.insertStore()
            val baseTime = OffsetDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
            repeat(10) { i ->
                TestFixtures.insertProduct(
                    storeId = storeId,
                    name = "Product $i",
                    updatedAt = baseTime.plusMinutes(i.toLong()),
                )
            }

            val page1 = repo.list(storeId, page = 0, size = 5, updatedSince = null)
            val page2 = repo.list(storeId, page = 1, size = 5, updatedSince = null)

            assertEquals(5, page1.items.size)
            assertEquals(5, page2.items.size)
            // Pages should have different items
            val allIds = (page1.items.map { it.id } + page2.items.map { it.id }).toSet()
            assertEquals(10, allIds.size)
        }

        @Test
        fun `list_pagination_beyondLastPage_returnsEmpty`() = runTest {
            val storeId = TestFixtures.insertStore()
            repeat(3) { i ->
                TestFixtures.insertProduct(storeId = storeId, name = "Product $i")
            }

            val result = repo.list(storeId, page = 10, size = 5, updatedSince = null)

            assertEquals(3L, result.total)
            assertTrue(result.items.isEmpty())
        }

        @Test
        fun `list_orderedByUpdatedAtAscending`() = runTest {
            val storeId = TestFixtures.insertStore()
            val baseTime = OffsetDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
            TestFixtures.insertProduct(storeId = storeId, name = "Newest", updatedAt = baseTime.plusHours(3))
            TestFixtures.insertProduct(storeId = storeId, name = "Oldest", updatedAt = baseTime.plusHours(1))
            TestFixtures.insertProduct(storeId = storeId, name = "Middle", updatedAt = baseTime.plusHours(2))

            val result = repo.list(storeId, page = 0, size = 10, updatedSince = null)

            assertEquals("Oldest", result.items[0].name)
            assertEquals("Middle", result.items[1].name)
            assertEquals("Newest", result.items[2].name)
        }
    }

    @Nested
    inner class UpdatedSinceFilter {

        @Test
        fun `list_updatedSince_filtersOlderProducts`() = runTest {
            val storeId = TestFixtures.insertStore()
            val baseTime = OffsetDateTime.of(2025, 6, 1, 12, 0, 0, 0, ZoneOffset.UTC)
            TestFixtures.insertProduct(storeId = storeId, name = "Old", updatedAt = baseTime.minusDays(1))
            TestFixtures.insertProduct(storeId = storeId, name = "New", updatedAt = baseTime.plusDays(1))

            val sinceMs = baseTime.toInstant().toEpochMilli()
            val result = repo.list(storeId, page = 0, size = 10, updatedSince = sinceMs)

            assertEquals(1L, result.total)
            assertEquals("New", result.items.single().name)
        }

        @Test
        fun `list_updatedSince_null_returnsAllProducts`() = runTest {
            val storeId = TestFixtures.insertStore()
            repeat(3) { TestFixtures.insertProduct(storeId = storeId) }

            val result = repo.list(storeId, page = 0, size = 10, updatedSince = null)

            assertEquals(3L, result.total)
        }
    }

    @Nested
    inner class NullableFields {

        @Test
        fun `list_productWithAllNullOptionalFields`() = runTest {
            val storeId = TestFixtures.insertStore()
            TestFixtures.insertProduct(
                storeId = storeId,
                sku = null,
                barcode = null,
                categoryId = null,
                unitId = null,
                taxGroupId = null,
                minStockQty = null,
                imageUrl = null,
                description = null,
            )

            val result = repo.list(storeId, page = 0, size = 10, updatedSince = null)
            val item = result.items.single()

            assertEquals(null, item.sku)
            assertEquals(null, item.barcode)
            assertEquals(null, item.categoryId)
            assertEquals(null, item.unitId)
            assertEquals(null, item.taxGroupId)
            assertEquals(0.0, item.minStockQty, 0.01) // nullable → defaults to 0.0 in impl
            assertEquals(null, item.imageUrl)
            assertEquals(null, item.description)
        }
    }

    /**
     * Helper to run suspend functions in test context with the shared DB connection.
     */
    private fun runTest(block: suspend () -> Unit) {
        kotlinx.coroutines.test.runTest {
            // Ensure Exposed uses our test database
            org.jetbrains.exposed.sql.transactions.TransactionManager.defaultDatabase = database
            block()
        }
    }
}
