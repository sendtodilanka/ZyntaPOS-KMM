package com.zyntasolutions.zyntapos.api.service

import com.zyntasolutions.zyntapos.api.models.ProductDto
import com.zyntasolutions.zyntapos.api.repository.ProductPageResult
import com.zyntasolutions.zyntapos.api.repository.ProductRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for ProductService — delegates to ProductRepository and computes pagination.
 */
class ProductServiceTest {

    private val productRepo = mockk<ProductRepository>()
    private val service = ProductService(productRepo)

    private fun sampleProduct(id: String = "p1") = ProductDto(
        id = id, name = "Widget", price = 9.99, costPrice = 5.0,
        stockQty = 100.0, isActive = true, createdAt = 1000L, updatedAt = 2000L,
    )

    // ── list() delegation ────────────────────────────────────────────────

    @Test
    fun `list delegates to repository with correct parameters`() = runTest {
        coEvery { productRepo.list("store1", 0, 20, null) } returns ProductPageResult(emptyList(), 0)

        service.list("store1", 0, 20, null)

        coVerify(exactly = 1) { productRepo.list("store1", 0, 20, null) }
    }

    @Test
    fun `list passes updatedSince to repository`() = runTest {
        val since = 1710000000000L
        coEvery { productRepo.list("s1", 0, 10, since) } returns ProductPageResult(emptyList(), 0)

        service.list("s1", 0, 10, since)

        coVerify(exactly = 1) { productRepo.list("s1", 0, 10, since) }
    }

    // ── PagedResponse construction ───────────────────────────────────────

    @Test
    fun `list returns correct page metadata`() = runTest {
        val products = listOf(sampleProduct("p1"), sampleProduct("p2"))
        coEvery { productRepo.list("s1", 0, 10, null) } returns ProductPageResult(products, 2)

        val result = service.list("s1", 0, 10, null)

        assertEquals(0, result.page)
        assertEquals(10, result.size)
        assertEquals(2, result.total)
        assertEquals(2, result.data.size)
    }

    @Test
    fun `hasMore is true when more items exist beyond current page`() = runTest {
        val products = listOf(sampleProduct("p1"))
        coEvery { productRepo.list("s1", 0, 1, null) } returns ProductPageResult(products, 5)

        val result = service.list("s1", 0, 1, null)

        assertTrue(result.hasMore, "hasMore should be true when total > page*size + size")
    }

    @Test
    fun `hasMore is false when on last page`() = runTest {
        val products = listOf(sampleProduct("p1"))
        coEvery { productRepo.list("s1", 0, 10, null) } returns ProductPageResult(products, 1)

        val result = service.list("s1", 0, 10, null)

        assertFalse(result.hasMore, "hasMore should be false when all items fit in current page")
    }

    @Test
    fun `hasMore is false when exactly filling the page`() = runTest {
        val products = (1..10).map { sampleProduct("p$it") }
        coEvery { productRepo.list("s1", 0, 10, null) } returns ProductPageResult(products, 10)

        val result = service.list("s1", 0, 10, null)

        assertFalse(result.hasMore, "hasMore should be false when total == page*size + size")
    }

    @Test
    fun `hasMore calculation works for non-zero page`() = runTest {
        val products = listOf(sampleProduct("p1"))
        // page=2, size=5 -> offset 10..14, total=20 -> hasMore = (2*5+5=15) < 20 = true
        coEvery { productRepo.list("s1", 2, 5, null) } returns ProductPageResult(products, 20)

        val result = service.list("s1", 2, 5, null)

        assertTrue(result.hasMore)
    }

    @Test
    fun `hasMore is false on last non-zero page`() = runTest {
        val products = listOf(sampleProduct("p1"))
        // page=3, size=5 -> (3*5+5=20) < 20 = false
        coEvery { productRepo.list("s1", 3, 5, null) } returns ProductPageResult(products, 20)

        val result = service.list("s1", 3, 5, null)

        assertFalse(result.hasMore)
    }

    // ── Empty results ────────────────────────────────────────────────────

    @Test
    fun `list returns empty data when repository returns no items`() = runTest {
        coEvery { productRepo.list("s1", 0, 10, null) } returns ProductPageResult(emptyList(), 0)

        val result = service.list("s1", 0, 10, null)

        assertEquals(0, result.data.size)
        assertEquals(0, result.total)
        assertFalse(result.hasMore)
    }
}
