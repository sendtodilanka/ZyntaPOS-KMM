package com.zyntasolutions.zyntapos.domain.usecase.register

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.CashMovement
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeRegisterRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

/**
 * ZyntaPOS — RecordCashMovementUseCase Unit Tests (commonTest)
 *
 * Coverage:
 *  A.  Amount zero returns MIN_VALUE error before repository call
 *  B.  Negative amount returns MIN_VALUE error before repository call
 *  C.  Blank reason returns REQUIRED error
 *  D.  Valid IN movement is persisted with correct fields
 *  E.  Valid OUT movement is persisted with correct type
 *  F.  Generated movement id is non-blank
 */
class RecordCashMovementUseCaseTest {

    private fun makeUseCase(): Pair<RecordCashMovementUseCase, FakeRegisterRepository> {
        val repo = FakeRegisterRepository()
        return RecordCashMovementUseCase(repo) to repo
    }

    @Test
    fun `A - zero amount returns MIN_VALUE error`() = runTest {
        val (useCase, repo) = makeUseCase()
        val result = useCase(
            sessionId = "sess-1",
            type = CashMovement.Type.IN,
            amount = 0.0,
            reason = "Petty cash",
            recordedBy = "user-1",
        )
        assertIs<Result.Error>(result)
        val ex = result.exception as ValidationException
        assertEquals("MIN_VALUE", ex.rule)
        assertEquals("amount", ex.field)
        assertEquals(0, repo.movements.size)
    }

    @Test
    fun `B - negative amount returns MIN_VALUE error`() = runTest {
        val (useCase, repo) = makeUseCase()
        val result = useCase(
            sessionId = "sess-1",
            type = CashMovement.Type.OUT,
            amount = -50.0,
            reason = "Petty cash",
            recordedBy = "user-1",
        )
        assertIs<Result.Error>(result)
        assertEquals("MIN_VALUE", (result.exception as ValidationException).rule)
        assertEquals(0, repo.movements.size)
    }

    @Test
    fun `C - blank reason returns REQUIRED error`() = runTest {
        val (useCase, repo) = makeUseCase()
        val result = useCase(
            sessionId = "sess-1",
            type = CashMovement.Type.IN,
            amount = 100.0,
            reason = "   ",
            recordedBy = "user-1",
        )
        assertIs<Result.Error>(result)
        val ex = result.exception as ValidationException
        assertEquals("REQUIRED", ex.rule)
        assertEquals("reason", ex.field)
        assertEquals(0, repo.movements.size)
    }

    @Test
    fun `D - valid IN movement persisted with correct fields`() = runTest {
        val (useCase, repo) = makeUseCase()
        val result = useCase(
            sessionId = "sess-42",
            type = CashMovement.Type.IN,
            amount = 500.0,
            reason = "Opening float top-up",
            recordedBy = "cashier-1",
        )
        assertIs<Result.Success<Unit>>(result)
        assertEquals(1, repo.movements.size)
        val movement = repo.movements.first()
        assertEquals("sess-42", movement.sessionId)
        assertEquals(CashMovement.Type.IN, movement.type)
        assertEquals(500.0, movement.amount)
        assertEquals("Opening float top-up", movement.reason)
        assertEquals("cashier-1", movement.recordedBy)
    }

    @Test
    fun `E - valid OUT movement persisted with OUT type`() = runTest {
        val (useCase, repo) = makeUseCase()
        val result = useCase(
            sessionId = "sess-1",
            type = CashMovement.Type.OUT,
            amount = 200.0,
            reason = "Office supplies purchase",
            recordedBy = "manager-1",
        )
        assertIs<Result.Success<Unit>>(result)
        assertEquals(CashMovement.Type.OUT, repo.movements.first().type)
    }

    @Test
    fun `F - generated movement id is non-blank`() = runTest {
        val (useCase, repo) = makeUseCase()
        useCase(
            sessionId = "sess-1",
            type = CashMovement.Type.IN,
            amount = 100.0,
            reason = "Test",
            recordedBy = "user-1",
        )
        assert(repo.movements.first().id.isNotBlank())
    }
}
