package com.zyntasolutions.zyntapos.domain.usecase.pos

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.OrderStatus
import com.zyntasolutions.zyntapos.domain.model.Permission
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.usecase.auth.CheckPermissionUseCase
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeOrderRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeStockRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildCartItem
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildOrder
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildUser
import com.zyntasolutions.zyntapos.domain.usecase.fakes.toOrderItem
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Unit tests for [VoidOrderUseCase] — permission gate, state validation, stock reversal.
 */
class VoidOrderUseCaseTest {

    private fun makeCheckPermission(role: Role = Role.CASHIER, userId: String = "user-01"): CheckPermissionUseCase {
        val uc = CheckPermissionUseCase(flowOf(buildUser(id = userId, role = role)))
        uc.updateSession(buildUser(id = userId, role = role))
        return uc
    }

    private fun makeUseCase(
        orderRepo: FakeOrderRepository = FakeOrderRepository(),
        stockRepo: FakeStockRepository = FakeStockRepository(),
        role: Role = Role.CASHIER,
        userId: String = "user-01",
    ) = VoidOrderUseCase(orderRepo, stockRepo, makeCheckPermission(role, userId))

    // ─── Happy Paths ──────────────────────────────────────────────────────────

    @Test
    fun `void completed order - order status changes to VOIDED`() = runTest {
        val orderRepo = FakeOrderRepository()
        val item = buildCartItem(productId = "p1", quantity = 2.0)
        val order = buildOrder(id = "ord-1", status = OrderStatus.COMPLETED,
            items = listOf(item.toOrderItem("ord-1")))
        orderRepo.orders.add(order)

        val result = makeUseCase(orderRepo = orderRepo)(
            orderId = "ord-1", reason = "Customer changed mind", userId = "user-01"
        )
        assertIs<Result.Success<*>>(result)
        assertEquals(OrderStatus.VOIDED, orderRepo.orders.first { it.id == "ord-1" }.status)
    }

    @Test
    fun `void order - stock is reversed for each item`() = runTest {
        val orderRepo = FakeOrderRepository()
        val stockRepo = FakeStockRepository()
        val items = listOf(
            buildCartItem(productId = "p1", quantity = 3.0).toOrderItem("ord-1"),
            buildCartItem(productId = "p2", quantity = 1.0).toOrderItem("ord-1"),
        )
        orderRepo.orders.add(buildOrder(id = "ord-1", items = items))

        makeUseCase(orderRepo, stockRepo)("ord-1", "Damaged goods", "user-01")
        assertEquals(2, stockRepo.adjustments.size)
        // All adjustments should be INCREASE (reversals)
        stockRepo.adjustments.forEach { adj ->
            assertEquals(com.zyntasolutions.zyntapos.domain.model.StockAdjustment.Type.INCREASE, adj.type)
        }
    }

    // ─── Permission Gate ──────────────────────────────────────────────────────

    @Test
    fun `void by ACCOUNTANT role - returns PERMISSION_DENIED`() = runTest {
        val orderRepo = FakeOrderRepository()
        orderRepo.orders.add(buildOrder(id = "ord-1"))

        val result = makeUseCase(orderRepo = orderRepo, role = Role.ACCOUNTANT, userId = "acct-01")(
            orderId = "ord-1", reason = "Reason", userId = "acct-01"
        )
        assertIs<Result.Error>(result)
        assertEquals("PERMISSION_DENIED", ((result as Result.Error).exception as ValidationException).rule)
    }

    @Test
    fun `void by ADMIN role - succeeds`() = runTest {
        val orderRepo = FakeOrderRepository()
        orderRepo.orders.add(buildOrder(id = "ord-1", status = OrderStatus.COMPLETED))

        val result = makeUseCase(orderRepo = orderRepo, role = Role.ADMIN, userId = "admin-01")(
            orderId = "ord-1", reason = "Admin override", userId = "admin-01"
        )
        assertIs<Result.Success<*>>(result)
    }

    // ─── State Validation ─────────────────────────────────────────────────────

    @Test
    fun `void already voided order - returns ALREADY_VOIDED error`() = runTest {
        val orderRepo = FakeOrderRepository()
        orderRepo.orders.add(buildOrder(id = "ord-1", status = OrderStatus.VOIDED))

        val result = makeUseCase(orderRepo = orderRepo)(
            "ord-1", "Re-void attempt", "user-01"
        )
        assertIs<Result.Error>(result)
        assertEquals("ALREADY_VOIDED", ((result as Result.Error).exception as ValidationException).rule)
    }

    @Test
    fun `void held order - returns INVALID_STATUS_FOR_VOID error`() = runTest {
        val orderRepo = FakeOrderRepository()
        orderRepo.orders.add(buildOrder(id = "ord-1", status = OrderStatus.HELD))

        val result = makeUseCase(orderRepo = orderRepo)("ord-1", "Bad status", "user-01")
        assertIs<Result.Error>(result)
        assertEquals("INVALID_STATUS_FOR_VOID", ((result as Result.Error).exception as ValidationException).rule)
    }

    @Test
    fun `void with blank reason - returns REQUIRED error`() = runTest {
        val orderRepo = FakeOrderRepository()
        orderRepo.orders.add(buildOrder(id = "ord-1", status = OrderStatus.COMPLETED))

        val result = makeUseCase(orderRepo = orderRepo)("ord-1", "  ", "user-01")
        assertIs<Result.Error>(result)
        assertEquals("REQUIRED", ((result as Result.Error).exception as ValidationException).rule)
    }

    @Test
    fun `void nonexistent order - returns database error`() = runTest {
        val result = makeUseCase()("does-not-exist", "reason", "user-01")
        assertIs<Result.Error>(result)
    }
}
