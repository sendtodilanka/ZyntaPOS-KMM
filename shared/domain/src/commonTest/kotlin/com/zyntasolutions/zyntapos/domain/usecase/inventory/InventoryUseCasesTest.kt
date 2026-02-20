package com.zyntasolutions.zyntapos.domain.usecase.inventory

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.StockAdjustment
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeProductRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeStockRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildProduct
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit tests for Inventory use cases:
 * [CreateProductUseCase], [UpdateProductUseCase],
 * [AdjustStockUseCase], [SearchProductsUseCase].
 *
 * 95% coverage target per PLAN_PHASE1.md §2.3.27.
 */
class InventoryUseCasesTest {

    // ─── CreateProductUseCase ─────────────────────────────────────────────────

    @Test
    fun `create product with valid data - succeeds and persists`() = runTest {
        val repo = FakeProductRepository()
        val useCase = CreateProductUseCase(repo)
        val product = buildProduct()

        val result = useCase(product)

        assertIs<Result.Success<Unit>>(result)
        assertEquals(1, repo.products.size)
        assertEquals(product.id, repo.products.first().id)
    }

    @Test
    fun `create product with blank name - returns REQUIRED error`() = runTest {
        val repo = FakeProductRepository()
        val useCase = CreateProductUseCase(repo)
        val product = buildProduct(name = "")

        val result = useCase(product)

        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("name", ex.field)
        assertEquals("REQUIRED", ex.rule)
        assertTrue(repo.products.isEmpty())
    }

    @Test
    fun `create product with negative price - returns MIN_VALUE error`() = runTest {
        val repo = FakeProductRepository()
        val useCase = CreateProductUseCase(repo)
        val product = buildProduct(price = -1.0)

        val result = useCase(product)

        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("price", ex.field)
        assertEquals("MIN_VALUE", ex.rule)
    }

    @Test
    fun `create product with negative cost price - returns MIN_VALUE error`() = runTest {
        val repo = FakeProductRepository()
        val useCase = CreateProductUseCase(repo)
        val product = buildProduct(costPrice = -5.0)

        val result = useCase(product)

        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("costPrice", ex.field)
        assertEquals("MIN_VALUE", ex.rule)
    }

    @Test
    fun `create product with duplicate barcode - returns BARCODE_DUPLICATE error`() = runTest {
        val repo = FakeProductRepository()
        repo.addProduct(buildProduct(id = "prod-existing", barcode = "1234567890"))
        val useCase = CreateProductUseCase(repo)
        val newProduct = buildProduct(id = "prod-new", barcode = "1234567890")

        val result = useCase(newProduct)

        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("barcode", ex.field)
        assertEquals("BARCODE_DUPLICATE", ex.rule)
        assertEquals(1, repo.products.size) // original product still only one
    }

    @Test
    fun `create product with zero stock - succeeds (valid initial stock)`() = runTest {
        val repo = FakeProductRepository()
        val useCase = CreateProductUseCase(repo)
        val product = buildProduct(stockQty = 0.0)

        val result = useCase(product)

        assertIs<Result.Success<Unit>>(result)
        assertEquals(0.0, repo.products.first().stockQty)
    }

    @Test
    fun `create product with negative stock - returns NEGATIVE_STOCK error`() = runTest {
        val repo = FakeProductRepository()
        val useCase = CreateProductUseCase(repo)
        val product = buildProduct(stockQty = -10.0)

        val result = useCase(product)

        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("stockQty", ex.field)
        assertEquals("NEGATIVE_STOCK", ex.rule)
    }

    // ─── UpdateProductUseCase ─────────────────────────────────────────────────

    @Test
    fun `update product with valid changes - succeeds`() = runTest {
        val repo = FakeProductRepository()
        val original = buildProduct(id = "prod-01", price = 10.0)
        repo.addProduct(original)
        val useCase = UpdateProductUseCase(repo)
        val updated = original.copy(price = 15.0, name = "Updated Product")

        val result = useCase(updated)

        assertIs<Result.Success<Unit>>(result)
        assertEquals(15.0, repo.products.first().price)
        assertEquals("Updated Product", repo.products.first().name)
    }

    @Test
    fun `update product with blank name - returns REQUIRED error`() = runTest {
        val repo = FakeProductRepository()
        repo.addProduct(buildProduct())
        val useCase = UpdateProductUseCase(repo)
        val updated = buildProduct(name = "")

        val result = useCase(updated)

        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("name", ex.field)
        assertEquals("REQUIRED", ex.rule)
    }

    @Test
    fun `update product with negative price - returns MIN_VALUE error`() = runTest {
        val repo = FakeProductRepository()
        repo.addProduct(buildProduct())
        val useCase = UpdateProductUseCase(repo)

        val result = useCase(buildProduct(price = -1.0))

        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("price", ex.field)
    }

    // ─── AdjustStockUseCase ───────────────────────────────────────────────────

    @Test
    fun `increase stock - records adjustment successfully`() = runTest {
        val stockRepo = FakeStockRepository()
        val useCase = AdjustStockUseCase(stockRepo)

        val result = useCase(
            productId = "prod-01",
            type = StockAdjustment.Type.INCREASE,
            quantity = 20.0,
            reason = "Purchase order received",
            adjustedBy = "user-01",
            currentStock = 50.0,
        )

        assertIs<Result.Success<Unit>>(result)
        assertEquals(1, stockRepo.adjustments.size)
        val adj = stockRepo.adjustments.first()
        assertEquals("prod-01", adj.productId)
        assertEquals(StockAdjustment.Type.INCREASE, adj.type)
        assertEquals(20.0, adj.quantity)
    }

    @Test
    fun `decrease stock within available qty - records adjustment`() = runTest {
        val stockRepo = FakeStockRepository()
        val useCase = AdjustStockUseCase(stockRepo)

        val result = useCase(
            productId = "prod-01",
            type = StockAdjustment.Type.DECREASE,
            quantity = 10.0,
            reason = "Damaged goods write-off",
            adjustedBy = "user-01",
            currentStock = 50.0,
        )

        assertIs<Result.Success<Unit>>(result)
        assertEquals(StockAdjustment.Type.DECREASE, stockRepo.adjustments.first().type)
    }

    @Test
    fun `decrease stock below zero - returns NEGATIVE_STOCK error`() = runTest {
        val stockRepo = FakeStockRepository()
        val useCase = AdjustStockUseCase(stockRepo)

        val result = useCase(
            productId = "prod-01",
            type = StockAdjustment.Type.DECREASE,
            quantity = 60.0,
            reason = "Correction",
            adjustedBy = "user-01",
            currentStock = 50.0,
        )

        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("NEGATIVE_STOCK", ex.rule)
        assertTrue(stockRepo.adjustments.isEmpty())
    }

    @Test
    fun `adjust stock with blank reason - returns REQUIRED error`() = runTest {
        val stockRepo = FakeStockRepository()
        val useCase = AdjustStockUseCase(stockRepo)

        val result = useCase(
            productId = "prod-01",
            type = StockAdjustment.Type.INCREASE,
            quantity = 5.0,
            reason = "   ",
            adjustedBy = "user-01",
            currentStock = 50.0,
        )

        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("reason", ex.field)
        assertEquals("REQUIRED", ex.rule)
    }

    @Test
    fun `adjust stock with zero quantity - returns MIN_VALUE error`() = runTest {
        val stockRepo = FakeStockRepository()
        val useCase = AdjustStockUseCase(stockRepo)

        val result = useCase(
            productId = "prod-01",
            type = StockAdjustment.Type.INCREASE,
            quantity = 0.0,
            reason = "Test",
            adjustedBy = "user-01",
            currentStock = 50.0,
        )

        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("quantity", ex.field)
        assertEquals("MIN_VALUE", ex.rule)
    }

    @Test
    fun `transfer adjustment with insufficient stock - returns NEGATIVE_STOCK error`() = runTest {
        val stockRepo = FakeStockRepository()
        val useCase = AdjustStockUseCase(stockRepo)

        val result = useCase(
            productId = "prod-01",
            type = StockAdjustment.Type.TRANSFER,
            quantity = 100.0,
            reason = "Transfer to Store B",
            adjustedBy = "user-01",
            currentStock = 30.0,
        )

        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("NEGATIVE_STOCK", ex.rule)
    }

    // ─── SearchProductsUseCase ────────────────────────────────────────────────

    @Test
    fun `search with matching name - returns filtered results`() = runTest {
        val repo = FakeProductRepository()
        repo.addProduct(buildProduct(id = "p1", name = "Apple Juice", barcode = "111"))
        repo.addProduct(buildProduct(id = "p2", name = "Orange Juice", barcode = "222", sku = "SKU-002"))
        repo.addProduct(buildProduct(id = "p3", name = "Bread", barcode = "333", sku = "SKU-003"))
        val useCase = SearchProductsUseCase(repo)

        // Use 0ms debounce via flow collection (debounce requires test dispatcher)
        val results = repo.search("Juice", null).first()

        assertEquals(2, results.size)
        assertTrue(results.all { it.name.contains("Juice", ignoreCase = true) })
    }

    @Test
    fun `search with barcode - returns matching product`() = runTest {
        val repo = FakeProductRepository()
        repo.addProduct(buildProduct(id = "p1", name = "Milk", barcode = "9876543210"))
        repo.addProduct(buildProduct(id = "p2", name = "Butter", barcode = "1111111111", sku = "SKU-002"))

        val results = repo.search("9876543210", null).first()

        assertEquals(1, results.size)
        assertEquals("p1", results.first().id)
    }

    @Test
    fun `search with empty query - returns all products`() = runTest {
        val repo = FakeProductRepository()
        repo.addProduct(buildProduct(id = "p1", barcode = "111"))
        repo.addProduct(buildProduct(id = "p2", barcode = "222", sku = "SKU-002"))
        repo.addProduct(buildProduct(id = "p3", barcode = "333", sku = "SKU-003"))

        val results = repo.search("", null).first()

        assertEquals(3, results.size)
    }

    @Test
    fun `search with category filter - narrows results`() = runTest {
        val repo = FakeProductRepository()
        repo.addProduct(buildProduct(id = "p1", name = "Product A", barcode = "111", categoryId = "cat-beverages"))
        repo.addProduct(buildProduct(id = "p2", name = "Product B", barcode = "222", sku = "SKU-002", categoryId = "cat-snacks"))
        repo.addProduct(buildProduct(id = "p3", name = "Product C", barcode = "333", sku = "SKU-003", categoryId = "cat-beverages"))

        val results = repo.search("", "cat-beverages").first()

        assertEquals(2, results.size)
        assertTrue(results.all { it.categoryId == "cat-beverages" })
    }
}
