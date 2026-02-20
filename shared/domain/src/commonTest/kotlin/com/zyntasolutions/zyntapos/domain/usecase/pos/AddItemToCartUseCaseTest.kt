package com.zyntasolutions.zyntapos.domain.usecase.pos

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.DiscountType
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeProductRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildCartItem
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildProduct
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit tests for [AddItemToCartUseCase] — stock validation and cart management.
 */
class AddItemToCartUseCaseTest {

    private fun makeUseCase(repo: FakeProductRepository = FakeProductRepository()) =
        AddItemToCartUseCase(repo)

    // ─── Happy Paths ──────────────────────────────────────────────────────────

    @Test
    fun `add new item to empty cart - item appears in cart`() = runTest {
        val repo = FakeProductRepository().also {
            it.addProduct(buildProduct(id = "p1", price = 9.99, stockQty = 50.0))
        }
        val result = makeUseCase(repo)(currentCart = emptyList(), productId = "p1")
        assertIs<Result.Success<*>>(result)
        val cart = (result as Result.Success).data
        assertEquals(1, cart.size)
        assertEquals("p1", cart[0].productId)
        assertEquals(9.99, cart[0].unitPrice, 0.001)
        assertEquals(1.0, cart[0].quantity, 0.001)
    }

    @Test
    fun `add existing item - quantities are summed`() = runTest {
        val repo = FakeProductRepository().also {
            it.addProduct(buildProduct(id = "p1", stockQty = 50.0))
        }
        val existingCart = listOf(buildCartItem(productId = "p1", quantity = 3.0))
        val result = makeUseCase(repo)(currentCart = existingCart, productId = "p1", quantity = 2.0)
        assertIs<Result.Success<*>>(result)
        val cart = (result as Result.Success).data
        assertEquals(1, cart.size)
        assertEquals(5.0, cart[0].quantity, 0.001)
    }

    @Test
    fun `add multiple different items - cart contains all items`() = runTest {
        val repo = FakeProductRepository().also {
            it.addProduct(buildProduct(id = "p1", stockQty = 50.0))
            it.addProduct(buildProduct(id = "p2", stockQty = 50.0))
        }
        var cart = (makeUseCase(repo)(emptyList(), "p1") as Result.Success).data
        cart = (makeUseCase(repo)(cart, "p2") as Result.Success).data
        assertEquals(2, cart.size)
    }

    @Test
    fun `add item with quantity 5 when stock is exactly 5 - succeeds`() = runTest {
        val repo = FakeProductRepository().also {
            it.addProduct(buildProduct(id = "p1", stockQty = 5.0))
        }
        val result = makeUseCase(repo)(currentCart = emptyList(), productId = "p1", quantity = 5.0)
        assertIs<Result.Success<*>>(result)
    }

    // ─── Stock Limit Scenarios ────────────────────────────────────────────────

    @Test
    fun `add quantity exceeding stock - returns OUT_OF_STOCK error`() = runTest {
        val repo = FakeProductRepository().also {
            it.addProduct(buildProduct(id = "p1", stockQty = 3.0))
        }
        val result = makeUseCase(repo)(currentCart = emptyList(), productId = "p1", quantity = 4.0)
        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("OUT_OF_STOCK", ex.rule)
    }

    @Test
    fun `combined cart + new quantity exceeds stock - returns OUT_OF_STOCK error`() = runTest {
        val repo = FakeProductRepository().also {
            it.addProduct(buildProduct(id = "p1", stockQty = 5.0))
        }
        // 3 already in cart + 3 new = 6 > 5 stock
        val existingCart = listOf(buildCartItem(productId = "p1", quantity = 3.0))
        val result = makeUseCase(repo)(currentCart = existingCart, productId = "p1", quantity = 3.0)
        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("OUT_OF_STOCK", ex.rule)
    }

    @Test
    fun `zero stock product - returns OUT_OF_STOCK error`() = runTest {
        val repo = FakeProductRepository().also {
            it.addProduct(buildProduct(id = "p1", stockQty = 0.0))
        }
        val result = makeUseCase(repo)(currentCart = emptyList(), productId = "p1", quantity = 1.0)
        assertIs<Result.Error>(result)
        assertEquals("OUT_OF_STOCK", ((result as Result.Error).exception as ValidationException).rule)
    }

    // ─── Validation Errors ────────────────────────────────────────────────────

    @Test
    fun `zero quantity - returns MIN_VALUE error`() = runTest {
        val repo = FakeProductRepository().also {
            it.addProduct(buildProduct(id = "p1"))
        }
        val result = makeUseCase(repo)(currentCart = emptyList(), productId = "p1", quantity = 0.0)
        assertIs<Result.Error>(result)
        assertEquals("MIN_VALUE", ((result as Result.Error).exception as ValidationException).rule)
    }

    @Test
    fun `negative quantity - returns MIN_VALUE error`() = runTest {
        val repo = FakeProductRepository().also {
            it.addProduct(buildProduct(id = "p1"))
        }
        val result = makeUseCase(repo)(currentCart = emptyList(), productId = "p1", quantity = -1.0)
        assertIs<Result.Error>(result)
        assertEquals("MIN_VALUE", ((result as Result.Error).exception as ValidationException).rule)
    }

    @Test
    fun `inactive product - returns PRODUCT_INACTIVE error`() = runTest {
        val repo = FakeProductRepository().also {
            it.addProduct(buildProduct(id = "p1", isActive = false, stockQty = 50.0))
        }
        val result = makeUseCase(repo)(currentCart = emptyList(), productId = "p1")
        assertIs<Result.Error>(result)
        assertEquals("PRODUCT_INACTIVE", ((result as Result.Error).exception as ValidationException).rule)
    }

    @Test
    fun `product not found - returns database error`() = runTest {
        val result = makeUseCase()(currentCart = emptyList(), productId = "nonexistent")
        assertIs<Result.Error>(result)
    }

    @Test
    fun `existing cart items are preserved when adding new product`() = runTest {
        val repo = FakeProductRepository().also {
            it.addProduct(buildProduct(id = "p1", stockQty = 50.0))
            it.addProduct(buildProduct(id = "p2", stockQty = 50.0))
        }
        val existingCart = listOf(buildCartItem(productId = "p2", quantity = 1.0))
        val result = makeUseCase(repo)(currentCart = existingCart, productId = "p1") as Result.Success
        assertEquals(2, result.data.size)
        assertTrue(result.data.any { it.productId == "p2" })
    }
}
