package com.zyntasolutions.zyntapos.domain.usecase.inventory

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.model.StocktakeStatus
import com.zyntasolutions.zyntapos.domain.usecase.auth.CheckPermissionUseCase
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeAuthRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeProductRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeStocktakeRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeStockRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildProduct
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildUser
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit tests for stocktake use cases:
 * [StartStocktakeUseCase], [ScanStocktakeItemUseCase], [CompleteStocktakeUseCase].
 */
class StocktakeUseCasesTest {

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun makeCheckPermission(role: Role): CheckPermissionUseCase {
        val authRepo = FakeAuthRepository()
        val useCase = CheckPermissionUseCase(authRepo.getSession())
        useCase.updateSession(buildUser(id = "user-01", role = role))
        return useCase
    }

    // ─── StartStocktakeUseCase ─────────────────────────────────────────────────

    @Test
    fun `start stocktake - user with MANAGE_STOCKTAKE - creates session`() = runTest {
        val repo = FakeStocktakeRepository()
        val checkPermission = makeCheckPermission(Role.STOCK_MANAGER)
        val useCase = StartStocktakeUseCase(repo, checkPermission)

        val result = useCase.execute("user-01")

        assertIs<Result.Success<*>>(result)
        val session = (result as Result.Success).data
        assertEquals("user-01", session.startedBy)
        assertEquals(StocktakeStatus.IN_PROGRESS, session.status)
        assertEquals(1, repo.sessions.size)
    }

    @Test
    fun `start stocktake - user without MANAGE_STOCKTAKE - returns auth error`() = runTest {
        val repo = FakeStocktakeRepository()
        // CASHIER role does NOT have MANAGE_STOCKTAKE
        val checkPermission = makeCheckPermission(Role.CASHIER)
        val useCase = StartStocktakeUseCase(repo, checkPermission)

        val result = useCase.execute("user-01")

        assertIs<Result.Error>(result)
        assertTrue(repo.sessions.isEmpty())
    }

    @Test
    fun `start stocktake - repository fails - returns error`() = runTest {
        val repo = FakeStocktakeRepository().apply { shouldFailStart = true }
        val checkPermission = makeCheckPermission(Role.STOCK_MANAGER)
        val useCase = StartStocktakeUseCase(repo, checkPermission)

        val result = useCase.execute("user-01")

        assertIs<Result.Error>(result)
    }

    // ─── ScanStocktakeItemUseCase ──────────────────────────────────────────────

    @Test
    fun `scan item - valid barcode - increments count and returns count record`() = runTest {
        val productRepo = FakeProductRepository()
        val stocktakeRepo = FakeStocktakeRepository()
        productRepo.addProduct(buildProduct(id = "p1", barcode = "1234567890"))
        val session = stocktakeRepo.startSession("user-01")
        val sessionId = (session as Result.Success).data.id
        val useCase = ScanStocktakeItemUseCase(productRepo, stocktakeRepo)

        val result = useCase.execute(sessionId, "1234567890")

        assertIs<Result.Success<*>>(result)
        val count = (result as Result.Success).data
        assertEquals("1234567890", count.barcode)
        assertEquals(1, count.countedQty)
    }

    @Test
    fun `scan item twice - count increments to 2`() = runTest {
        val productRepo = FakeProductRepository()
        val stocktakeRepo = FakeStocktakeRepository()
        productRepo.addProduct(buildProduct(id = "p1", barcode = "1234567890"))
        val session = stocktakeRepo.startSession("user-01")
        val sessionId = (session as Result.Success).data.id
        val useCase = ScanStocktakeItemUseCase(productRepo, stocktakeRepo)

        useCase.execute(sessionId, "1234567890")
        val result = useCase.execute(sessionId, "1234567890")

        assertIs<Result.Success<*>>(result)
        val count = (result as Result.Success).data
        assertEquals(2, count.countedQty)
    }

    @Test
    fun `scan item - blank barcode - returns validation error`() = runTest {
        val productRepo = FakeProductRepository()
        val stocktakeRepo = FakeStocktakeRepository()
        val useCase = ScanStocktakeItemUseCase(productRepo, stocktakeRepo)

        val result = useCase.execute("session-01", "")

        assertIs<Result.Error>(result)
    }

    @Test
    fun `scan item - barcode not found in product catalog - returns error`() = runTest {
        val productRepo = FakeProductRepository() // empty
        val stocktakeRepo = FakeStocktakeRepository()
        val useCase = ScanStocktakeItemUseCase(productRepo, stocktakeRepo)

        val result = useCase.execute("session-01", "unknown-barcode")

        assertIs<Result.Error>(result)
    }

    // ─── CompleteStocktakeUseCase ──────────────────────────────────────────────

    @Test
    fun `complete stocktake - user with permission - returns variance map`() = runTest {
        val stocktakeRepo = FakeStocktakeRepository()
        val stockRepo = FakeStockRepository()
        val checkPermission = makeCheckPermission(Role.STOCK_MANAGER)
        val useCase = CompleteStocktakeUseCase(stocktakeRepo, stockRepo, checkPermission)

        val sessionResult = stocktakeRepo.startSession("user-01")
        val sessionId = (sessionResult as Result.Success).data.id
        // Count 12 units (system has 10) → variance = +2
        stocktakeRepo.updateCount(sessionId, "barcode-A", 12)

        val result = useCase.execute(sessionId, "user-01")

        assertIs<Result.Success<*>>(result)
        val variances = (result as Result.Success).data
        // FakeStocktakeRepository uses system qty=10, so variance=12-10=+2
        assertTrue(variances.isNotEmpty())
    }

    @Test
    fun `complete stocktake - user without permission - returns auth error`() = runTest {
        val stocktakeRepo = FakeStocktakeRepository()
        val stockRepo = FakeStockRepository()
        // CASHIER role does NOT have MANAGE_STOCKTAKE
        val checkPermission = makeCheckPermission(Role.CASHIER)
        val useCase = CompleteStocktakeUseCase(stocktakeRepo, stockRepo, checkPermission)

        val result = useCase.execute("session-01", "user-01")

        assertIs<Result.Error>(result)
    }

    @Test
    fun `complete stocktake - session not found - returns error`() = runTest {
        val stocktakeRepo = FakeStocktakeRepository()
        val stockRepo = FakeStockRepository()
        val checkPermission = makeCheckPermission(Role.STOCK_MANAGER)
        val useCase = CompleteStocktakeUseCase(stocktakeRepo, stockRepo, checkPermission)

        val result = useCase.execute("non-existent-session", "user-01")

        assertIs<Result.Error>(result)
    }

    @Test
    fun `complete stocktake - zero variance items - no stock adjustments created`() = runTest {
        val stocktakeRepo = FakeStocktakeRepository()
        val stockRepo = FakeStockRepository()
        val checkPermission = makeCheckPermission(Role.STOCK_MANAGER)
        val useCase = CompleteStocktakeUseCase(stocktakeRepo, stockRepo, checkPermission)

        val sessionResult = stocktakeRepo.startSession("user-01")
        val sessionId = (sessionResult as Result.Success).data.id
        // FakeStocktakeRepository: system qty=10, count=10 → variance=0
        stocktakeRepo.updateCount(sessionId, "barcode-A", 10)

        useCase.execute(sessionId, "user-01")

        assertTrue(stockRepo.adjustments.isEmpty(), "No adjustment should be created for zero variance")
    }
}
