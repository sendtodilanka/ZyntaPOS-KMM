package com.zyntasolutions.zyntapos.domain.usecase.register

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.CashMovement
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeRegisterRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildRegisterSession
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit tests for Register use cases:
 * [OpenRegisterSessionUseCase], [CloseRegisterSessionUseCase], [RecordCashMovementUseCase].
 *
 * Covers the Z-report discrepancy detection scenario per PLAN_PHASE1.md §2.3.27.
 */
class RegisterUseCasesTest {

    // ─── OpenRegisterSessionUseCase ───────────────────────────────────────────

    @Test
    fun `open session with valid opening balance - creates OPEN session`() = runTest {
        val repo = FakeRegisterRepository()
        val useCase = OpenRegisterSessionUseCase(repo)

        val result = useCase("register-01", 500.0, "user-01")

        assertIs<Result.Success<*>>(result)
        val session = (result as Result.Success).data
        assertEquals("register-01", session.registerId)
        assertEquals(500.0, session.openingBalance)
        assertEquals(com.zyntasolutions.zyntapos.domain.model.RegisterSession.Status.OPEN, session.status)
    }

    @Test
    fun `open session with negative opening balance - returns MIN_VALUE error`() = runTest {
        val repo = FakeRegisterRepository()
        val useCase = OpenRegisterSessionUseCase(repo)

        val result = useCase("register-01", -100.0, "user-01")

        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("openingBalance", ex.field)
        assertEquals("MIN_VALUE", ex.rule)
    }

    @Test
    fun `open session with zero opening balance - succeeds`() = runTest {
        val repo = FakeRegisterRepository()
        val useCase = OpenRegisterSessionUseCase(repo)

        val result = useCase("register-01", 0.0, "user-01")

        assertIs<Result.Success<*>>(result)
        assertEquals(0.0, (result as Result.Success).data.openingBalance)
    }

    @Test
    fun `open session when active session exists - returns SESSION_ALREADY_OPEN error`() = runTest {
        val repo = FakeRegisterRepository().also { it.activeSessionAlreadyExists = true }
        val useCase = OpenRegisterSessionUseCase(repo)

        val result = useCase("register-01", 500.0, "user-01")

        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("SESSION_ALREADY_OPEN", ex.rule)
    }

    // ─── CloseRegisterSessionUseCase ──────────────────────────────────────────

    @Test
    fun `close session with balanced cash count - discrepancy = 0, isBalanced = true`() = runTest {
        val repo = FakeRegisterRepository()
        // Open session with 500.0 opening balance
        repo.openSession("register-01", 500.0, "user-01")
        val sessionId = repo.sessions.first().id

        val useCase = CloseRegisterSessionUseCase(repo)
        // No cash movements → expected = 500.0, actual = 500.0
        val result = useCase(sessionId, 500.0, "user-01")

        assertIs<Result.Success<*>>(result)
        val closeResult = (result as Result.Success).data as CloseRegisterSessionUseCase.CloseResult
        assertTrue(abs(closeResult.discrepancy) < 0.01, "Expected discrepancy ≈ 0 but was ${closeResult.discrepancy}")
        assertTrue(closeResult.isBalanced)
        assertEquals(com.zyntasolutions.zyntapos.domain.model.RegisterSession.Status.CLOSED, closeResult.session.status)
    }

    @Test
    fun `close session with cash shortage - negative discrepancy, not balanced`() = runTest {
        val repo = FakeRegisterRepository()
        repo.openSession("register-01", 500.0, "user-01")
        val sessionId = repo.sessions.first().id

        val useCase = CloseRegisterSessionUseCase(repo)
        // Expected = 500.0, actual = 450.0 → shortage of 50.0
        val result = useCase(sessionId, 450.0, "user-01")

        assertIs<Result.Success<*>>(result)
        val closeResult = (result as Result.Success).data as CloseRegisterSessionUseCase.CloseResult
        assertTrue(closeResult.discrepancy < -0.01, "Expected negative discrepancy but was ${closeResult.discrepancy}")
        assertTrue(!closeResult.isBalanced)
    }

    @Test
    fun `close session with cash overage - positive discrepancy, not balanced`() = runTest {
        val repo = FakeRegisterRepository()
        repo.openSession("register-01", 500.0, "user-01")
        val sessionId = repo.sessions.first().id

        val useCase = CloseRegisterSessionUseCase(repo)
        // Expected = 500.0, actual = 600.0 → overage of 100.0
        val result = useCase(sessionId, 600.0, "user-01")

        assertIs<Result.Success<*>>(result)
        val closeResult = (result as Result.Success).data as CloseRegisterSessionUseCase.CloseResult
        assertTrue(closeResult.discrepancy > 0.01, "Expected positive discrepancy but was ${closeResult.discrepancy}")
        assertTrue(!closeResult.isBalanced)
    }

    @Test
    fun `close session with cash movements included in expected balance`() = runTest {
        val repo = FakeRegisterRepository()
        repo.openSession("register-01", 500.0, "user-01")
        val sessionId = repo.sessions.first().id

        // Add cash movements: +200 IN, -50 OUT
        RecordCashMovementUseCase(repo)(sessionId, CashMovement.Type.IN, 200.0, "Sale refund", "user-01")
        RecordCashMovementUseCase(repo)(sessionId, CashMovement.Type.OUT, 50.0, "Petty cash withdrawal", "user-01")
        // expectedBalance = 500 + 200 - 50 = 650

        val useCase = CloseRegisterSessionUseCase(repo)
        val result = useCase(sessionId, 650.0, "user-01")

        assertIs<Result.Success<*>>(result)
        val closeResult = (result as Result.Success).data as CloseRegisterSessionUseCase.CloseResult
        assertTrue(abs(closeResult.discrepancy) < 0.01, "Expected balanced session but discrepancy was ${closeResult.discrepancy}")
        assertTrue(closeResult.isBalanced)
    }

    @Test
    fun `close session with negative actual balance - returns MIN_VALUE error`() = runTest {
        val repo = FakeRegisterRepository()
        repo.openSession("register-01", 500.0, "user-01")
        val sessionId = repo.sessions.first().id

        val useCase = CloseRegisterSessionUseCase(repo)
        val result = useCase(sessionId, -10.0, "user-01")

        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("actualBalance", ex.field)
        assertEquals("MIN_VALUE", ex.rule)
    }

    @Test
    fun `close already closed session - returns SESSION_ALREADY_CLOSED error`() = runTest {
        val repo = FakeRegisterRepository()
        val closedSession = buildRegisterSession(
            id = "session-closed",
            status = com.zyntasolutions.zyntapos.domain.model.RegisterSession.Status.CLOSED
        )
        repo.sessions.add(closedSession)

        val useCase = CloseRegisterSessionUseCase(repo)
        val result = useCase("session-closed", 500.0, "user-01")

        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("SESSION_ALREADY_CLOSED", ex.rule)
    }

    // ─── RecordCashMovementUseCase ────────────────────────────────────────────

    @Test
    fun `record cash IN movement - persisted to repository`() = runTest {
        val repo = FakeRegisterRepository()
        val useCase = RecordCashMovementUseCase(repo)

        val result = useCase("session-01", CashMovement.Type.IN, 100.0, "Safe drop", "user-01")

        assertIs<Result.Success<Unit>>(result)
        assertEquals(1, repo.movements.size)
        val movement = repo.movements.first()
        assertEquals(CashMovement.Type.IN, movement.type)
        assertEquals(100.0, movement.amount)
        assertEquals("Safe drop", movement.reason)
        assertEquals("session-01", movement.sessionId)
    }

    @Test
    fun `record cash OUT movement - persisted to repository`() = runTest {
        val repo = FakeRegisterRepository()
        val useCase = RecordCashMovementUseCase(repo)

        val result = useCase("session-01", CashMovement.Type.OUT, 25.0, "Petty cash - office supplies", "user-01")

        assertIs<Result.Success<Unit>>(result)
        assertEquals(CashMovement.Type.OUT, repo.movements.first().type)
        assertEquals(25.0, repo.movements.first().amount)
    }

    @Test
    fun `record movement with amount = 0 - returns MIN_VALUE error`() = runTest {
        val repo = FakeRegisterRepository()
        val useCase = RecordCashMovementUseCase(repo)

        val result = useCase("session-01", CashMovement.Type.IN, 0.0, "Test", "user-01")

        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("amount", ex.field)
        assertEquals("MIN_VALUE", ex.rule)
        assertTrue(repo.movements.isEmpty())
    }

    @Test
    fun `record movement with negative amount - returns MIN_VALUE error`() = runTest {
        val repo = FakeRegisterRepository()
        val useCase = RecordCashMovementUseCase(repo)

        val result = useCase("session-01", CashMovement.Type.OUT, -50.0, "Test", "user-01")

        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("amount", ex.field)
        assertEquals("MIN_VALUE", ex.rule)
    }

    @Test
    fun `record movement with blank reason - returns REQUIRED error`() = runTest {
        val repo = FakeRegisterRepository()
        val useCase = RecordCashMovementUseCase(repo)

        val result = useCase("session-01", CashMovement.Type.IN, 100.0, "  ", "user-01")

        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("reason", ex.field)
        assertEquals("REQUIRED", ex.rule)
        assertTrue(repo.movements.isEmpty())
    }

    @Test
    fun `record multiple movements - all persisted correctly`() = runTest {
        val repo = FakeRegisterRepository()
        val useCase = RecordCashMovementUseCase(repo)

        useCase("session-01", CashMovement.Type.IN, 100.0, "Float top-up", "user-01")
        useCase("session-01", CashMovement.Type.OUT, 30.0, "Petty cash", "user-01")
        useCase("session-01", CashMovement.Type.IN, 50.0, "Customer refund reversal", "user-01")

        assertEquals(3, repo.movements.size)
        val totalIn = repo.movements.filter { it.type == CashMovement.Type.IN }.sumOf { it.amount }
        val totalOut = repo.movements.filter { it.type == CashMovement.Type.OUT }.sumOf { it.amount }
        assertEquals(150.0, totalIn)
        assertEquals(30.0, totalOut)
    }
}
