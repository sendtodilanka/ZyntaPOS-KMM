package com.zyntasolutions.zyntapos.domain.usecase.register

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.RegisterSession
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeRegisterRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildRegisterSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlinx.coroutines.test.runTest

/**
 * ZyntaPOS — CloseRegisterSessionUseCase Unit Tests (commonTest)
 *
 * Validates the cash-drawer close workflow using [FakeRegisterRepository].
 *
 * Coverage:
 *  A. negative actualBalance returns MIN_VALUE validation error
 *  B. session not found in repository returns Result.Error
 *  C. already-closed session returns SESSION_ALREADY_CLOSED error
 *  D. successful close returns CloseResult with correct session
 *  E. discrepancy = actualBalance - expectedBalance (positive when over)
 *  F. discrepancy negative when actualBalance < expectedBalance (short)
 *  G. isBalanced true when discrepancy within default tolerance (10.0)
 *  H. isBalanced false when discrepancy exceeds default tolerance
 *  I. custom tolerance respected — discrepancy within custom tolerance returns isBalanced=true
 *  J. closed session has CLOSED status in CloseResult
 *  K. zero actualBalance is valid (e.g. cash pulled before close)
 */
class CloseRegisterSessionUseCaseTest {

    private fun makeUseCase(): Pair<CloseRegisterSessionUseCase, FakeRegisterRepository> {
        val repo = FakeRegisterRepository()
        return CloseRegisterSessionUseCase(repo) to repo
    }

    // ── A — Negative balance ──────────────────────────────────────────────────

    @Test
    fun `A - negative actualBalance returns MIN_VALUE validation error`() = runTest {
        val (useCase, _) = makeUseCase()
        val result = useCase(
            sessionId = "session-01",
            actualBalance = -1.0,
            userId = "user-01",
        )
        assertIs<Result.Error>(result)
        val ex = result.exception
        assertIs<ValidationException>(ex)
        assertEquals("actualBalance", ex.field)
        assertEquals("MIN_VALUE", ex.rule)
    }

    // ── B — Session not found ─────────────────────────────────────────────────

    @Test
    fun `B - session not found in repository returns Result Error`() = runTest {
        val (useCase, _) = makeUseCase()
        // No sessions added to repo
        val result = useCase(
            sessionId = "non-existent",
            actualBalance = 500.0,
            userId = "user-01",
        )
        assertIs<Result.Error>(result)
    }

    // ── C — Already closed ────────────────────────────────────────────────────

    @Test
    fun `C - already-closed session returns SESSION_ALREADY_CLOSED error`() = runTest {
        val (useCase, repo) = makeUseCase()
        val closedSession = buildRegisterSession(
            id = "session-01",
            status = RegisterSession.Status.CLOSED,
        )
        repo.sessions.add(closedSession)

        val result = useCase(
            sessionId = "session-01",
            actualBalance = 500.0,
            userId = "user-01",
        )
        assertIs<Result.Error>(result)
        val ex = result.exception
        assertIs<ValidationException>(ex)
        assertEquals("SESSION_ALREADY_CLOSED", ex.rule)
    }

    // ── D — Successful close ──────────────────────────────────────────────────

    @Test
    fun `D - successful close returns CloseResult with closed session`() = runTest {
        val (useCase, repo) = makeUseCase()
        val session = buildRegisterSession(
            id = "session-01",
            openingBalance = 500.0,
            expectedBalance = 500.0,
            status = RegisterSession.Status.OPEN,
        )
        repo.sessions.add(session)

        val result = useCase(
            sessionId = "session-01",
            actualBalance = 500.0,
            userId = "user-01",
        )
        assertIs<Result.Success<CloseRegisterSessionUseCase.CloseResult>>(result)
        assertEquals(RegisterSession.Status.CLOSED, result.data.session.status)
    }

    // ── E — Positive discrepancy (cash over) ──────────────────────────────────

    @Test
    fun `E - discrepancy is positive when actualBalance exceeds expectedBalance`() = runTest {
        val (useCase, repo) = makeUseCase()
        // expectedBalance = openingBalance + 0 cash movements = 500
        val session = buildRegisterSession(
            id = "session-01",
            openingBalance = 500.0,
            expectedBalance = 500.0,
        )
        repo.sessions.add(session)

        val result = useCase("session-01", actualBalance = 510.0, userId = "u")
        assertIs<Result.Success<CloseRegisterSessionUseCase.CloseResult>>(result)
        assertEquals(10.0, result.data.discrepancy, 0.001)
    }

    // ── F — Negative discrepancy (cash short) ────────────────────────────────

    @Test
    fun `F - discrepancy is negative when actualBalance is less than expectedBalance`() = runTest {
        val (useCase, repo) = makeUseCase()
        val session = buildRegisterSession(
            id = "session-01",
            openingBalance = 500.0,
            expectedBalance = 500.0,
        )
        repo.sessions.add(session)

        val result = useCase("session-01", actualBalance = 480.0, userId = "u")
        assertIs<Result.Success<CloseRegisterSessionUseCase.CloseResult>>(result)
        assertEquals(-20.0, result.data.discrepancy, 0.001)
    }

    // ── G — isBalanced within default tolerance ───────────────────────────────

    @Test
    fun `G - isBalanced true when discrepancy within default tolerance of 10`() = runTest {
        val (useCase, repo) = makeUseCase()
        val session = buildRegisterSession(
            id = "session-01",
            openingBalance = 500.0,
            expectedBalance = 500.0,
        )
        repo.sessions.add(session)

        // 5.0 discrepancy — within 10.0 default tolerance
        val result = useCase("session-01", actualBalance = 505.0, userId = "u")
        assertIs<Result.Success<CloseRegisterSessionUseCase.CloseResult>>(result)
        assertTrue(result.data.isBalanced)
    }

    @Test
    fun `G2 - isBalanced true when discrepancy is exactly at default tolerance boundary`() = runTest {
        val (useCase, repo) = makeUseCase()
        val session = buildRegisterSession(id = "session-01", openingBalance = 500.0, expectedBalance = 500.0)
        repo.sessions.add(session)

        // Exactly 10.0 discrepancy — at boundary of tolerance
        val result = useCase("session-01", actualBalance = 510.0, userId = "u")
        assertIs<Result.Success<CloseRegisterSessionUseCase.CloseResult>>(result)
        assertTrue(result.data.isBalanced)
    }

    // ── H — isBalanced exceeds default tolerance ──────────────────────────────

    @Test
    fun `H - isBalanced false when discrepancy exceeds default tolerance of 10`() = runTest {
        val (useCase, repo) = makeUseCase()
        val session = buildRegisterSession(
            id = "session-01",
            openingBalance = 500.0,
            expectedBalance = 500.0,
        )
        repo.sessions.add(session)

        // 50.0 discrepancy — beyond default 10.0 tolerance
        val result = useCase("session-01", actualBalance = 550.0, userId = "u")
        assertIs<Result.Success<CloseRegisterSessionUseCase.CloseResult>>(result)
        assertFalse(result.data.isBalanced)
    }

    // ── I — Custom tolerance ──────────────────────────────────────────────────

    @Test
    fun `I - custom tolerance 50 allows a 30 discrepancy to be balanced`() = runTest {
        val (useCase, repo) = makeUseCase()
        val session = buildRegisterSession(id = "session-01", openingBalance = 500.0, expectedBalance = 500.0)
        repo.sessions.add(session)

        // 30.0 discrepancy — beyond default 10.0 but within custom 50.0
        val result = useCase("session-01", actualBalance = 530.0, userId = "u", tolerance = 50.0)
        assertIs<Result.Success<CloseRegisterSessionUseCase.CloseResult>>(result)
        assertTrue(result.data.isBalanced)
    }

    // ── J — Closed status in result ───────────────────────────────────────────

    @Test
    fun `J - closed session in result has CLOSED status`() = runTest {
        val (useCase, repo) = makeUseCase()
        val session = buildRegisterSession(id = "session-01", openingBalance = 500.0, expectedBalance = 500.0)
        repo.sessions.add(session)

        val result = useCase("session-01", actualBalance = 500.0, userId = "u")
        assertIs<Result.Success<CloseRegisterSessionUseCase.CloseResult>>(result)
        assertEquals(RegisterSession.Status.CLOSED, result.data.session.status)
    }

    // ── K — Zero actualBalance ────────────────────────────────────────────────

    @Test
    fun `K - zero actualBalance is valid and returns success`() = runTest {
        val (useCase, repo) = makeUseCase()
        val session = buildRegisterSession(id = "session-01", openingBalance = 500.0, expectedBalance = 500.0)
        repo.sessions.add(session)

        val result = useCase("session-01", actualBalance = 0.0, userId = "u")
        assertIs<Result.Success<CloseRegisterSessionUseCase.CloseResult>>(result)
        assertEquals(-500.0, result.data.discrepancy, 0.001) // 0 - 500 = -500
        assertFalse(result.data.isBalanced)
    }
}
