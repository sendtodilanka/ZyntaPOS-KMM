package com.zyntasolutions.zyntapos.domain.usecase.multistore

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.StockTransfer
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeWarehouseRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildStockTransfer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit tests for [CommitStockTransferUseCase].
 *
 * Covers:
 * - Happy path: PENDING transfer committed, confirmedBy set
 * - Blank confirmedBy: ValidationException without any DB call
 * - Transfer not found: Result.Error propagated
 * - Transfer already COMMITTED: ValidationException
 * - Transfer already CANCELLED: ValidationException
 * - DB error: propagated as Result.Error
 */
class CommitStockTransferUseCaseTest {

    private fun makeUseCase(repo: FakeWarehouseRepository = FakeWarehouseRepository()) =
        CommitStockTransferUseCase(repo) to repo

    @Test
    fun `commit PENDING transfer - returns Success and marks committed`() = runTest {
        val (useCase, repo) = makeUseCase()
        val transfer = buildStockTransfer(id = "t-01", status = StockTransfer.Status.PENDING)
        repo.transfers.add(transfer)

        val result = useCase("t-01", confirmedBy = "user-manager")

        assertIs<Result.Success<Unit>>(result)
        assertTrue(repo.commitedTransferIds.contains("t-01"))
        val committed = repo.transfers.first()
        assertIs<StockTransfer>(committed)
        assertTrue(committed.status == StockTransfer.Status.COMMITTED)
    }

    @Test
    fun `blank confirmedBy - returns ValidationException without DB call`() = runTest {
        val (useCase, repo) = makeUseCase()
        val transfer = buildStockTransfer(id = "t-02")
        repo.transfers.add(transfer)

        val result = useCase("t-02", confirmedBy = "   ")

        assertIs<Result.Error>(result)
        assertIs<ValidationException>(result.exception)
        assertTrue(repo.commitedTransferIds.isEmpty(), "No commit should occur for blank confirmedBy")
    }

    @Test
    fun `transfer not found - returns Result Error`() = runTest {
        val (useCase, _) = makeUseCase()

        val result = useCase("non-existent", confirmedBy = "user-01")

        assertIs<Result.Error>(result)
    }

    @Test
    fun `already COMMITTED transfer - returns ValidationException`() = runTest {
        val (useCase, repo) = makeUseCase()
        repo.transfers.add(buildStockTransfer(id = "t-committed", status = StockTransfer.Status.COMMITTED))

        val result = useCase("t-committed", confirmedBy = "user-01")

        assertIs<Result.Error>(result)
        assertIs<ValidationException>(result.exception)
        assertContains(result.exception.message ?: "", "COMMITTED", ignoreCase = true)
    }

    @Test
    fun `already CANCELLED transfer - returns ValidationException`() = runTest {
        val (useCase, repo) = makeUseCase()
        repo.transfers.add(buildStockTransfer(id = "t-cancelled", status = StockTransfer.Status.CANCELLED))

        val result = useCase("t-cancelled", confirmedBy = "user-01")

        assertIs<Result.Error>(result)
        assertIs<ValidationException>(result.exception)
        assertContains(result.exception.message ?: "", "CANCELLED", ignoreCase = true)
    }

    @Test
    fun `DB error - propagated as Result Error`() = runTest {
        val repo = FakeWarehouseRepository().also {
            val t = buildStockTransfer(id = "t-db")
            it.transfers.add(t)
            it.shouldFail = true
        }
        val useCase = CommitStockTransferUseCase(repo)

        val result = useCase("t-db", confirmedBy = "user-01")

        assertIs<Result.Error>(result)
    }
}
