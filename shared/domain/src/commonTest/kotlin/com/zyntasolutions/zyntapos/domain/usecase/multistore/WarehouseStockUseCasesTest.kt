package com.zyntasolutions.zyntapos.domain.usecase.multistore

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeWarehouseStockRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildWarehouseStock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WarehouseStockUseCasesTest {

    // ── GetWarehouseStockUseCase ───────────────────────────────────────────

    @Test
    fun `GetWarehouseStockUseCase emits entries filtered by warehouseId`() = runTestBlock {
        val repo = FakeWarehouseStockRepository()
        val useCase = GetWarehouseStockUseCase(repo)

        repo.seed(
            buildWarehouseStock(id = "ws-1", warehouseId = "wh-01", productId = "p1"),
            buildWarehouseStock(id = "ws-2", warehouseId = "wh-02", productId = "p2"),
        )

        useCase("wh-01").test {
            val items = awaitItem()
            assertEquals(1, items.size)
            assertEquals("p1", items.first().productId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── SetWarehouseStockUseCase ───────────────────────────────────────────

    @Test
    fun `SetWarehouseStockUseCase inserts new entry`() = runTestBlock {
        val repo = FakeWarehouseStockRepository()
        val useCase = SetWarehouseStockUseCase(repo)

        val result = useCase(
            warehouseId = "wh-01",
            productId = "prod-01",
            quantity = 50.0,
            minQuantity = 5.0,
        )

        assertIs<Result.Success<Unit>>(result)
        assertEquals(1, repo.entries.size)
        assertEquals(50.0, repo.entries.first().quantity)
        assertEquals(5.0, repo.entries.first().minQuantity)
    }

    @Test
    fun `SetWarehouseStockUseCase updates existing entry`() = runTestBlock {
        val repo = FakeWarehouseStockRepository()
        repo.seed(buildWarehouseStock(warehouseId = "wh-01", productId = "prod-01", quantity = 10.0))
        val useCase = SetWarehouseStockUseCase(repo)

        val result = useCase(
            warehouseId = "wh-01",
            productId = "prod-01",
            quantity = 99.0,
        )

        assertIs<Result.Success<Unit>>(result)
        assertEquals(1, repo.entries.size)
        assertEquals(99.0, repo.entries.first().quantity)
    }

    @Test
    fun `SetWarehouseStockUseCase rejects negative quantity`() = runTestBlock {
        val repo = FakeWarehouseStockRepository()
        val useCase = SetWarehouseStockUseCase(repo)

        val result = useCase(
            warehouseId = "wh-01",
            productId = "prod-01",
            quantity = -1.0,
        )

        assertIs<Result.Error>(result)
        assertTrue(result.exception.message?.contains("negative") == true)
    }

    @Test
    fun `SetWarehouseStockUseCase rejects blank warehouseId`() = runTestBlock {
        val repo = FakeWarehouseStockRepository()
        val useCase = SetWarehouseStockUseCase(repo)

        val result = useCase(warehouseId = "", productId = "p1", quantity = 10.0)

        assertIs<Result.Error>(result)
    }

    // ── AdjustWarehouseStockUseCase ───────────────────────────────────────

    @Test
    fun `AdjustWarehouseStockUseCase rejects zero delta`() = runTestBlock {
        val repo = FakeWarehouseStockRepository()
        val useCase = AdjustWarehouseStockUseCase(repo)

        val result = useCase("wh-01", "p1", 0.0)

        assertIs<Result.Error>(result)
        assertTrue(result.exception.message?.contains("zero") == true)
    }

    @Test
    fun `AdjustWarehouseStockUseCase rejects stock out below zero`() = runTestBlock {
        val repo = FakeWarehouseStockRepository()
        repo.seed(buildWarehouseStock(warehouseId = "wh-01", productId = "p1", quantity = 5.0))
        val useCase = AdjustWarehouseStockUseCase(repo)

        val result = useCase("wh-01", "p1", -10.0)

        assertIs<Result.Error>(result)
        assertTrue(result.exception.message?.contains("Insufficient") == true)
    }

    @Test
    fun `AdjustWarehouseStockUseCase allows positive adjustment`() = runTestBlock {
        val repo = FakeWarehouseStockRepository()
        repo.seed(buildWarehouseStock(warehouseId = "wh-01", productId = "p1", quantity = 10.0))
        val useCase = AdjustWarehouseStockUseCase(repo)

        val result = useCase("wh-01", "p1", 5.0)

        assertIs<Result.Success<Unit>>(result)
        assertEquals(15.0, repo.entries.first().quantity)
    }

    // ── GetLowStockByWarehouseUseCase ────────────────────────────────────

    @Test
    fun `GetLowStockByWarehouseUseCase emits only low-stock entries`() = runTestBlock {
        val repo = FakeWarehouseStockRepository()
        val useCase = GetLowStockByWarehouseUseCase(repo)

        repo.seed(
            buildWarehouseStock(id = "ws-1", warehouseId = "wh-01", quantity = 3.0, minQuantity = 10.0),
            buildWarehouseStock(id = "ws-2", warehouseId = "wh-01", quantity = 50.0, minQuantity = 10.0),
            buildWarehouseStock(id = "ws-3", warehouseId = "wh-01", quantity = 10.0, minQuantity = 10.0), // exactly at threshold
        )

        useCase("wh-01").test {
            val items = awaitItem()
            // ws-1 (3 <= 10) and ws-3 (10 <= 10) are low-stock
            assertEquals(2, items.size)
            assertTrue(items.all { it.isLowStock })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `GetLowStockByWarehouseUseCase allWarehouses emits cross-warehouse low-stock`() = runTestBlock {
        val repo = FakeWarehouseStockRepository()
        val useCase = GetLowStockByWarehouseUseCase(repo)

        repo.seed(
            buildWarehouseStock(id = "ws-1", warehouseId = "wh-01", quantity = 2.0, minQuantity = 5.0),
            buildWarehouseStock(id = "ws-2", warehouseId = "wh-02", quantity = 1.0, minQuantity = 5.0),
            buildWarehouseStock(id = "ws-3", warehouseId = "wh-01", quantity = 100.0, minQuantity = 5.0),
        )

        useCase.allWarehouses().test {
            val items = awaitItem()
            assertEquals(2, items.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── GetStockByProductUseCase ──────────────────────────────────────────

    @Test
    fun `GetStockByProductUseCase totalStock sums across warehouses`() = runTestBlock {
        val repo = FakeWarehouseStockRepository()
        val useCase = GetStockByProductUseCase(repo)

        repo.seed(
            buildWarehouseStock(id = "ws-1", warehouseId = "wh-01", productId = "p1", quantity = 30.0),
            buildWarehouseStock(id = "ws-2", warehouseId = "wh-02", productId = "p1", quantity = 70.0),
        )

        val result = useCase.totalStock("p1")
        assertIs<Result.Success<Double>>(result)
        assertEquals(100.0, result.data)
    }

    @Test
    fun `GetStockByProductUseCase totalStock returns zero for unknown product`() = runTestBlock {
        val repo = FakeWarehouseStockRepository()
        val useCase = GetStockByProductUseCase(repo)

        val result = useCase.totalStock("unknown-product")
        assertIs<Result.Success<Double>>(result)
        assertEquals(0.0, result.data)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Multiplatform coroutine test helper (avoids requiring runTest import)
// ─────────────────────────────────────────────────────────────────────────────

private fun runTestBlock(block: suspend () -> Unit) {
    kotlinx.coroutines.test.runTest { block() }
}
