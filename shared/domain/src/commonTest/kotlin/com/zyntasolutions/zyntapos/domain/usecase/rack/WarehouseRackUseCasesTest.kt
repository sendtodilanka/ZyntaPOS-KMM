package com.zyntasolutions.zyntapos.domain.usecase.rack

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeWarehouseRackRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildWarehouseRack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * ZyntaPOS — SaveWarehouseRackUseCase / DeleteWarehouseRackUseCase Unit Tests (commonTest)
 *
 * SaveWarehouseRackUseCase coverage:
 *  A.  Blank name returns REQUIRED error, no repository call
 *  B.  Whitespace-only name returns REQUIRED error
 *  C.  Blank warehouseId returns REQUIRED error
 *  D.  Zero capacity returns MIN_VALUE error
 *  E.  Negative capacity returns MIN_VALUE error
 *  F.  Null capacity is valid (unconstrained rack)
 *  G.  Positive capacity is valid
 *  H.  isUpdate=false routes to insert
 *  I.  isUpdate=true routes to update
 *  J.  Repository failure propagates
 *
 * DeleteWarehouseRackUseCase coverage:
 *  K.  Valid deletion delegates to repository and records deletedAt
 *  L.  Repository failure propagates
 *  M.  Non-existent rack returns error from repository
 */
class WarehouseRackUseCasesTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun makeSaveUseCase(shouldFail: Boolean = false): Pair<SaveWarehouseRackUseCase, FakeWarehouseRackRepository> {
        val repo = FakeWarehouseRackRepository().also { it.shouldFail = shouldFail }
        return SaveWarehouseRackUseCase(repo) to repo
    }

    private fun makeDeleteUseCase(shouldFail: Boolean = false): Pair<DeleteWarehouseRackUseCase, FakeWarehouseRackRepository> {
        val repo = FakeWarehouseRackRepository().also { it.shouldFail = shouldFail }
        return DeleteWarehouseRackUseCase(repo) to repo
    }

    // ── SaveWarehouseRackUseCase ──────────────────────────────────────────────

    @Test
    fun `A - blank name returns REQUIRED error`() = runTest {
        val (useCase, repo) = makeSaveUseCase()
        val result = useCase(buildWarehouseRack(name = ""), isUpdate = false)
        assertIs<Result.Error>(result)
        val ex = result.exception as ValidationException
        assertEquals("REQUIRED", ex.rule)
        assertEquals("name", ex.field)
        assertFalse(repo.insertCalled)
    }

    @Test
    fun `B - whitespace-only name returns REQUIRED error`() = runTest {
        val (useCase, _) = makeSaveUseCase()
        val result = useCase(buildWarehouseRack(name = "   "), isUpdate = false)
        assertIs<Result.Error>(result)
        assertEquals("REQUIRED", (result.exception as ValidationException).rule)
    }

    @Test
    fun `C - blank warehouseId returns REQUIRED error`() = runTest {
        val (useCase, repo) = makeSaveUseCase()
        val result = useCase(buildWarehouseRack(warehouseId = ""), isUpdate = false)
        assertIs<Result.Error>(result)
        val ex = result.exception as ValidationException
        assertEquals("REQUIRED", ex.rule)
        assertEquals("warehouseId", ex.field)
        assertFalse(repo.insertCalled)
    }

    @Test
    fun `D - zero capacity returns MIN_VALUE error`() = runTest {
        val (useCase, _) = makeSaveUseCase()
        val result = useCase(buildWarehouseRack(capacity = 0), isUpdate = false)
        assertIs<Result.Error>(result)
        val ex = result.exception as ValidationException
        assertEquals("MIN_VALUE", ex.rule)
        assertEquals("capacity", ex.field)
    }

    @Test
    fun `E - negative capacity returns MIN_VALUE error`() = runTest {
        val (useCase, _) = makeSaveUseCase()
        val result = useCase(buildWarehouseRack(capacity = -5), isUpdate = false)
        assertIs<Result.Error>(result)
        assertEquals("MIN_VALUE", (result.exception as ValidationException).rule)
    }

    @Test
    fun `F - null capacity is valid`() = runTest {
        val (useCase, repo) = makeSaveUseCase()
        val result = useCase(buildWarehouseRack(capacity = null), isUpdate = false)
        assertIs<Result.Success<Unit>>(result)
        assertTrue(repo.insertCalled)
    }

    @Test
    fun `G - positive capacity is valid`() = runTest {
        val (useCase, repo) = makeSaveUseCase()
        val result = useCase(buildWarehouseRack(capacity = 100), isUpdate = false)
        assertIs<Result.Success<Unit>>(result)
        assertTrue(repo.insertCalled)
    }

    @Test
    fun `H - isUpdate false routes to insert`() = runTest {
        val (useCase, repo) = makeSaveUseCase()
        useCase(buildWarehouseRack(), isUpdate = false)
        assertTrue(repo.insertCalled)
        assertFalse(repo.updateCalled)
    }

    @Test
    fun `I - isUpdate true routes to update`() = runTest {
        val (useCase, repo) = makeSaveUseCase()
        repo.racks.add(buildWarehouseRack(id = "rack-01"))
        useCase(buildWarehouseRack(id = "rack-01", name = "Rack B1"), isUpdate = true)
        assertFalse(repo.insertCalled)
        assertTrue(repo.updateCalled)
        assertEquals("Rack B1", repo.racks.first { it.id == "rack-01" }.name)
    }

    @Test
    fun `J - repository failure propagates`() = runTest {
        val (useCase, _) = makeSaveUseCase(shouldFail = true)
        val result = useCase(buildWarehouseRack(), isUpdate = false)
        assertIs<Result.Error>(result)
    }

    // ── DeleteWarehouseRackUseCase ────────────────────────────────────────────

    @Test
    fun `K - valid deletion delegates to repository with correct timestamps`() = runTest {
        val (useCase, repo) = makeDeleteUseCase()
        repo.racks.add(buildWarehouseRack(id = "rack-01"))
        val result = useCase(id = "rack-01", deletedAt = 5000L, updatedAt = 5001L)
        assertIs<Result.Success<Unit>>(result)
        assertEquals(0, repo.racks.size)
        assertEquals("rack-01", repo.lastDeletedId)
        assertEquals(5000L, repo.lastDeletedAt)
    }

    @Test
    fun `L - repository failure propagates`() = runTest {
        val (useCase, repo) = makeDeleteUseCase(shouldFail = true)
        repo.racks.add(buildWarehouseRack(id = "rack-01"))
        val result = useCase(id = "rack-01", deletedAt = 0L, updatedAt = 0L)
        assertIs<Result.Error>(result)
    }

    @Test
    fun `M - non-existent rack id returns error from repository`() = runTest {
        val (useCase, _) = makeDeleteUseCase()
        // repo is empty — no rack with this id
        val result = useCase(id = "rack-ghost", deletedAt = 0L, updatedAt = 0L)
        assertIs<Result.Error>(result)
    }
}
