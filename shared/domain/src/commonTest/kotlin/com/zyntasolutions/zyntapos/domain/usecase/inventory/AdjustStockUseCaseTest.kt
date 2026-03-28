package com.zyntasolutions.zyntapos.domain.usecase.inventory

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.Product
import com.zyntasolutions.zyntapos.domain.model.StockAdjustment
import com.zyntasolutions.zyntapos.domain.repository.StockRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * ZyntaPOS — AdjustStockUseCaseTest Unit Tests (commonTest)
 *
 * Validates stock adjustment business rules in [AdjustStockUseCase.invoke].
 *
 * Coverage:
 *  A. INCREASE with valid quantity and non-blank reason succeeds
 *  B. DECREASE within available stock succeeds
 *  C. blank reason returns REQUIRED validation error
 *  D. DECREASE exceeding stock returns NEGATIVE_STOCK error
 *  E. zero quantity returns MIN_VALUE error
 *  F. repository error propagates as Result.Error
 *  G. adjustment is forwarded to repository on success
 */
class AdjustStockUseCaseTest {

    private class FakeStockRepository(
        private val adjustResult: Result<Unit> = Result.Success(Unit),
    ) : StockRepository {
        val receivedAdjustments = mutableListOf<StockAdjustment>()

        override suspend fun adjustStock(adjustment: StockAdjustment): Result<Unit> {
            receivedAdjustments.add(adjustment)
            return adjustResult
        }

        override fun getMovements(productId: String): Flow<List<StockAdjustment>> = flowOf(emptyList())
        override fun getAlerts(threshold: Double?): Flow<List<Product>> = flowOf(emptyList())
    }

    private fun buildProduct(
        id: String = "prod-1",
        stockQty: Double = 100.0,
        minStockQty: Double = 10.0,
    ) = Product(
        id = id,
        name = "Espresso",
        categoryId = "cat-1",
        unitId = "unit-1",
        price = 3.50,
        stockQty = stockQty,
        minStockQty = minStockQty,
        createdAt = Instant.fromEpochMilliseconds(1_000_000L),
        updatedAt = Instant.fromEpochMilliseconds(2_000_000L),
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `A - INCREASE with valid quantity and non-blank reason succeeds`() = runTest {
        val repo = FakeStockRepository()
        val useCase = AdjustStockUseCase(repo)

        val result = useCase(
            productId = "prod-1",
            type = StockAdjustment.Type.INCREASE,
            quantity = 50.0,
            reason = "Delivery received",
            adjustedBy = "user-1",
            currentStock = 100.0,
        )

        assertIs<Result.Success<Unit>>(result)
        assertEquals(1, repo.receivedAdjustments.size)
    }

    @Test
    fun `B - DECREASE within available stock succeeds`() = runTest {
        val repo = FakeStockRepository()
        val useCase = AdjustStockUseCase(repo)

        val result = useCase(
            productId = "prod-1",
            type = StockAdjustment.Type.DECREASE,
            quantity = 20.0,
            reason = "Damaged goods",
            adjustedBy = "user-1",
            currentStock = 100.0,
        )

        assertIs<Result.Success<Unit>>(result)
    }

    @Test
    fun `C - blank reason returns REQUIRED validation error`() = runTest {
        val repo = FakeStockRepository()
        val useCase = AdjustStockUseCase(repo)

        val result = useCase(
            productId = "prod-1",
            type = StockAdjustment.Type.INCREASE,
            quantity = 10.0,
            reason = "   ",
            adjustedBy = "user-1",
            currentStock = 100.0,
        )

        assertIs<Result.Error>(result)
        val error = result.exception as? ValidationException
        assertEquals("REQUIRED", error?.rule)
        assertEquals("reason", error?.field)
        // No call to repository on validation failure
        assertEquals(0, repo.receivedAdjustments.size)
    }

    @Test
    fun `D - DECREASE exceeding available stock returns NEGATIVE_STOCK error`() = runTest {
        val repo = FakeStockRepository()
        val useCase = AdjustStockUseCase(repo)

        val result = useCase(
            productId = "prod-1",
            type = StockAdjustment.Type.DECREASE,
            quantity = 150.0, // exceeds currentStock=100
            reason = "Write-off",
            adjustedBy = "user-1",
            currentStock = 100.0,
        )

        assertIs<Result.Error>(result)
        val error = result.exception as? ValidationException
        assertEquals("NEGATIVE_STOCK", error?.rule)
        assertEquals(0, repo.receivedAdjustments.size)
    }

    @Test
    fun `E - zero quantity returns MIN_VALUE error from StockValidator`() = runTest {
        val repo = FakeStockRepository()
        val useCase = AdjustStockUseCase(repo)

        val result = useCase(
            productId = "prod-1",
            type = StockAdjustment.Type.INCREASE,
            quantity = 0.0,
            reason = "Test",
            adjustedBy = "user-1",
            currentStock = 100.0,
        )

        assertIs<Result.Error>(result)
        val error = result.exception as? ValidationException
        assertEquals("MIN_VALUE", error?.rule)
        assertEquals(0, repo.receivedAdjustments.size)
    }

    @Test
    fun `F - repository error propagates as Result Error`() = runTest {
        val dbError = com.zyntasolutions.zyntapos.core.result.DatabaseException("DB error")
        val repo = FakeStockRepository(adjustResult = Result.Error(dbError))
        val useCase = AdjustStockUseCase(repo)

        val result = useCase(
            productId = "prod-1",
            type = StockAdjustment.Type.INCREASE,
            quantity = 10.0,
            reason = "Test",
            adjustedBy = "user-1",
            currentStock = 100.0,
        )

        assertIs<Result.Error>(result)
    }

    @Test
    fun `G - successful adjustment is forwarded to repository with correct fields`() = runTest {
        val repo = FakeStockRepository()
        val useCase = AdjustStockUseCase(repo)

        useCase(
            productId = "prod-42",
            type = StockAdjustment.Type.DECREASE,
            quantity = 5.0,
            reason = "Expired stock",
            adjustedBy = "mgr-1",
            currentStock = 50.0,
        )

        assertEquals(1, repo.receivedAdjustments.size)
        val adj = repo.receivedAdjustments[0]
        assertEquals("prod-42", adj.productId)
        assertEquals(StockAdjustment.Type.DECREASE, adj.type)
        assertEquals(5.0, adj.quantity)
        assertEquals("Expired stock", adj.reason)
        assertEquals("mgr-1", adj.adjustedBy)
    }
}
