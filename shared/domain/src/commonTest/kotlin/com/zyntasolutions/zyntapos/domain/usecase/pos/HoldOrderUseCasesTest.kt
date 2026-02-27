package com.zyntasolutions.zyntapos.domain.usecase.pos

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeOrderRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildCartItem
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for [HoldOrderUseCase] and [RetrieveHeldOrderUseCase].
 *
 * Covers:
 * - HoldOrderUseCase: empty cart returns EMPTY_CART ValidationException without DB write
 * - HoldOrderUseCase: non-empty cart delegates to OrderRepository.holdOrder and returns holdId
 * - HoldOrderUseCase: multiple items preserved correctly in held order
 * - RetrieveHeldOrderUseCase: valid holdId returns CartItems matching the held order's items
 * - RetrieveHeldOrderUseCase: cart item fields (price, quantity, discount) are faithfully restored
 * - RetrieveHeldOrderUseCase: invalid holdId propagates Result.Error
 */
class HoldOrderUseCasesTest {

    private fun makeHoldUseCase(repo: FakeOrderRepository = FakeOrderRepository()) =
        HoldOrderUseCase(repo) to repo

    private fun makeRetrieveUseCase(repo: FakeOrderRepository = FakeOrderRepository()) =
        RetrieveHeldOrderUseCase(repo) to repo

    private val singleItem = listOf(
        buildCartItem(productId = "p1", productName = "Widget", unitPrice = 15.0, quantity = 2.0),
    )

    private val multipleItems = listOf(
        buildCartItem(productId = "p1", productName = "Widget", unitPrice = 15.0, quantity = 2.0),
        buildCartItem(productId = "p2", productName = "Gadget", unitPrice = 9.99, quantity = 3.0),
    )

    // ─── HoldOrderUseCase ─────────────────────────────────────────────────────

    @Test
    fun `empty cart returns EMPTY_CART ValidationException`() = runTest {
        val (useCase, repo) = makeHoldUseCase()

        val result = useCase(emptyList())

        assertIs<Result.Error>(result)
        val ex = result.exception
        assertIs<ValidationException>(ex)
        assertEquals("EMPTY_CART", ex.rule)
        assertEquals("items", ex.field)
        assertTrue(repo.orders.isEmpty(), "No order should be persisted for an empty cart")
    }

    @Test
    fun `non-empty cart delegates to repository and returns holdId`() = runTest {
        val (useCase, repo) = makeHoldUseCase()

        val result = useCase(singleItem)

        assertIs<Result.Success<String>>(result)
        val holdId = result.data
        assertNotNull(holdId)
        assertTrue(holdId.isNotBlank(), "Hold ID must not be blank")
        assertEquals(1, repo.orders.size, "One held order must be persisted")
    }

    @Test
    fun `hold id returned matches the id of the persisted held order`() = runTest {
        val (useCase, repo) = makeHoldUseCase()

        val result = useCase(singleItem) as Result.Success<String>
        val holdId = result.data

        val persistedOrder = repo.orders.first()
        assertEquals(holdId, persistedOrder.id, "Returned holdId must match the persisted order id")
    }

    @Test
    fun `multiple items all preserved in held order`() = runTest {
        val (useCase, repo) = makeHoldUseCase()

        useCase(multipleItems)

        val heldOrder = repo.orders.first()
        assertEquals(2, heldOrder.items.size, "All cart items must be included in the held order")
        val productIds = heldOrder.items.map { it.productId }
        assertTrue("p1" in productIds)
        assertTrue("p2" in productIds)
    }

    @Test
    fun `second hold creates a second distinct held order`() = runTest {
        val repo = FakeOrderRepository()
        val useCase = HoldOrderUseCase(repo)

        val result1 = useCase(singleItem) as Result.Success<String>
        val result2 = useCase(multipleItems) as Result.Success<String>

        assertEquals(2, repo.orders.size)
        assertTrue(result1.data != result2.data, "Each hold must produce a unique hold ID")
    }

    // ─── RetrieveHeldOrderUseCase ─────────────────────────────────────────────

    @Test
    fun `valid holdId returns CartItems matching held order items`() = runTest {
        val repo = FakeOrderRepository()
        val holdUseCase = HoldOrderUseCase(repo)
        val retrieveUseCase = RetrieveHeldOrderUseCase(repo)

        val holdResult = holdUseCase(singleItem) as Result.Success<String>
        val holdId = holdResult.data

        val retrieveResult = retrieveUseCase(holdId)

        assertIs<Result.Success<List<*>>>(retrieveResult)
        val cartItems = (retrieveResult as Result.Success).data
        assertEquals(1, cartItems.size)
    }

    @Test
    fun `retrieved cart item fields match original cart item`() = runTest {
        val repo = FakeOrderRepository()
        val holdUseCase = HoldOrderUseCase(repo)
        val retrieveUseCase = RetrieveHeldOrderUseCase(repo)

        val originalItem = buildCartItem(
            productId = "p1",
            productName = "Widget",
            unitPrice = 15.0,
            quantity = 2.0,
            discount = 1.5,
        )
        val holdId = (holdUseCase(listOf(originalItem)) as Result.Success<String>).data

        val cartItems = (retrieveUseCase(holdId) as Result.Success).data

        val restored = cartItems.first()
        assertEquals("p1", restored.productId)
        assertEquals("Widget", restored.productName)
        assertEquals(15.0, restored.unitPrice, 0.001)
        assertEquals(2.0, restored.quantity, 0.001)
        assertEquals(1.5, restored.discount, 0.001)
    }

    @Test
    fun `multiple items retrieved in same order as held`() = runTest {
        val repo = FakeOrderRepository()
        val holdUseCase = HoldOrderUseCase(repo)
        val retrieveUseCase = RetrieveHeldOrderUseCase(repo)

        val holdId = (holdUseCase(multipleItems) as Result.Success<String>).data

        val cartItems = (retrieveUseCase(holdId) as Result.Success).data

        assertEquals(2, cartItems.size)
        assertEquals("p1", cartItems[0].productId)
        assertEquals("p2", cartItems[1].productId)
    }

    @Test
    fun `invalid holdId propagates Result Error`() = runTest {
        val (useCase, _) = makeRetrieveUseCase()

        val result = useCase("non-existent-hold-id")

        assertIs<Result.Error>(result)
    }

    @Test
    fun `retrieve with blank holdId returns error`() = runTest {
        val (useCase, _) = makeRetrieveUseCase()

        val result = useCase("")

        assertIs<Result.Error>(result)
    }
}
