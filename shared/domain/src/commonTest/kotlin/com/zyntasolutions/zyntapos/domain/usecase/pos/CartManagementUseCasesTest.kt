package com.zyntasolutions.zyntapos.domain.usecase.pos

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.DiscountType
import com.zyntasolutions.zyntapos.domain.model.OrderStatus
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeOrderRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeProductRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildCartItem
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildProduct
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for remaining POS cart management use cases:
 * [RemoveItemFromCartUseCase], [UpdateCartItemQuantityUseCase],
 * [HoldOrderUseCase], [RetrieveHeldOrderUseCase].
 */
class CartManagementUseCasesTest {

    // ─── RemoveItemFromCartUseCase ────────────────────────────────────────────

    private val removeUseCase = RemoveItemFromCartUseCase()

    @Test
    fun `remove - existing item is removed from cart`() {
        val cart = listOf(
            buildCartItem(productId = "p1"),
            buildCartItem(productId = "p2"),
        )
        val result = removeUseCase(cart, "p1") as Result.Success
        assertEquals(1, result.data.size)
        assertFalse(result.data.any { it.productId == "p1" })
    }

    @Test
    fun `remove - non-existent productId returns unchanged cart (idempotent)`() {
        val cart = listOf(buildCartItem(productId = "p1"))
        val result = removeUseCase(cart, "does-not-exist") as Result.Success
        assertEquals(1, result.data.size)
    }

    @Test
    fun `remove - blank productId returns REQUIRED error`() {
        val result = removeUseCase(listOf(buildCartItem(productId = "p1")), "")
        assertIs<Result.Error>(result)
        assertEquals("REQUIRED", ((result as Result.Error).exception as ValidationException).rule)
    }

    @Test
    fun `remove - last item leaves empty cart`() {
        val cart = listOf(buildCartItem(productId = "p1"))
        val result = removeUseCase(cart, "p1") as Result.Success
        assertTrue(result.data.isEmpty())
    }

    // ─── UpdateCartItemQuantityUseCase ────────────────────────────────────────

    private fun updateUseCase(repo: FakeProductRepository = FakeProductRepository()) =
        UpdateCartItemQuantityUseCase(repo)

    @Test
    fun `update - valid new quantity updates item`() = runTest {
        val repo = FakeProductRepository().also {
            it.addProduct(buildProduct(id = "p1", stockQty = 20.0))
        }
        val cart = listOf(buildCartItem(productId = "p1", quantity = 3.0))
        val result = updateUseCase(repo)(cart, "p1", 10.0) as Result.Success
        assertEquals(10.0, result.data.first { it.productId == "p1" }.quantity, 0.001)
    }

    @Test
    fun `update - other items in cart are unchanged`() = runTest {
        val repo = FakeProductRepository().also {
            it.addProduct(buildProduct(id = "p1", stockQty = 20.0))
        }
        val cart = listOf(
            buildCartItem(productId = "p1", quantity = 2.0),
            buildCartItem(productId = "p2", quantity = 5.0),
        )
        val result = updateUseCase(repo)(cart, "p1", 8.0) as Result.Success
        assertEquals(5.0, result.data.first { it.productId == "p2" }.quantity, 0.001)
    }

    @Test
    fun `update - quantity below 1 returns MIN_QTY error`() = runTest {
        val cart = listOf(buildCartItem(productId = "p1"))
        val result = updateUseCase()(cart, "p1", 0.5)
        assertIs<Result.Error>(result)
        assertEquals("MIN_QTY", ((result as Result.Error).exception as ValidationException).rule)
    }

    @Test
    fun `update - product not in cart returns NOT_IN_CART error`() = runTest {
        val result = updateUseCase()(emptyList(), "nonexistent", 2.0)
        assertIs<Result.Error>(result)
        assertEquals("NOT_IN_CART", ((result as Result.Error).exception as ValidationException).rule)
    }

    @Test
    fun `update - quantity exceeds stock returns EXCEEDS_STOCK error`() = runTest {
        val repo = FakeProductRepository().also {
            it.addProduct(buildProduct(id = "p1", stockQty = 3.0))
        }
        val cart = listOf(buildCartItem(productId = "p1", quantity = 1.0))
        val result = updateUseCase(repo)(cart, "p1", 10.0)
        assertIs<Result.Error>(result)
        assertEquals("EXCEEDS_STOCK", ((result as Result.Error).exception as ValidationException).rule)
    }

    // ─── HoldOrderUseCase ────────────────────────────────────────────────────

    private fun holdUseCase(repo: FakeOrderRepository = FakeOrderRepository()) =
        HoldOrderUseCase(repo)

    @Test
    fun `hold - non-empty cart creates held order and returns hold ID`() = runTest {
        val repo = FakeOrderRepository()
        val cart = listOf(
            buildCartItem(productId = "p1", unitPrice = 10.0),
            buildCartItem(productId = "p2", unitPrice = 20.0),
        )
        val result = holdUseCase(repo)(cart) as Result.Success
        assertTrue(result.data.isNotEmpty())
        assertEquals(1, repo.orders.size)
        assertEquals(OrderStatus.HELD, repo.orders[0].status)
    }

    @Test
    fun `hold - empty cart returns EMPTY_CART error`() = runTest {
        val result = holdUseCase()(emptyList())
        assertIs<Result.Error>(result)
        assertEquals("EMPTY_CART", ((result as Result.Error).exception as ValidationException).rule)
    }

    // ─── RetrieveHeldOrderUseCase ─────────────────────────────────────────────

    private fun retrieveUseCase(repo: FakeOrderRepository = FakeOrderRepository()) =
        RetrieveHeldOrderUseCase(repo)

    @Test
    fun `retrieve - valid hold ID restores cart items`() = runTest {
        val repo = FakeOrderRepository()
        val originalCart = listOf(
            buildCartItem(productId = "p1", unitPrice = 15.0, quantity = 2.0),
        )
        val holdId = (HoldOrderUseCase(repo)(originalCart) as Result.Success).data
        val result = retrieveUseCase(repo)(holdId) as Result.Success
        assertEquals(1, result.data.size)
        assertEquals("p1", result.data[0].productId)
        assertEquals(15.0, result.data[0].unitPrice, 0.001)
        assertEquals(2.0, result.data[0].quantity, 0.001)
    }

    @Test
    fun `retrieve - invalid hold ID returns error`() = runTest {
        val result = retrieveUseCase()("nonexistent-hold")
        assertIs<Result.Error>(result)
    }

    @Test
    fun `retrieve - item discount is preserved from hold`() = runTest {
        val repo = FakeOrderRepository()
        val originalCart = listOf(
            buildCartItem(productId = "p1", discount = 5.0, discountType = DiscountType.FIXED),
        )
        val holdId = (HoldOrderUseCase(repo)(originalCart) as Result.Success).data
        val result = retrieveUseCase(repo)(holdId) as Result.Success
        assertEquals(5.0, result.data[0].discount, 0.001)
        assertEquals(DiscountType.FIXED, result.data[0].discountType)
    }
}
