package com.zyntasolutions.zyntapos.data.repository

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.CashMovement
import com.zyntasolutions.zyntapos.domain.model.RegisterSession
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant

/**
 * ZyntaPOS — RegisterRepositoryImpl Integration Tests (jvmTest)
 *
 * Validates [RegisterRepositoryImpl] against a real in-memory SQLite database
 * ([createTestDatabase]) — no mocking. Exercises the full SQL round-trip for
 * register session lifecycle and cash movement management.
 *
 * Coverage:
 *  1. openSession creates a session with status = OPEN
 *  2. getActive emits the open session via Flow (Turbine)
 *  3. closeSession changes status to CLOSED and sets actualBalance
 *  4. addCashMovement inserts a movement; getMovements retrieves it
 *  5. Second openSession while one is OPEN returns ValidationException (SESSION_ALREADY_OPEN)
 */
class RegisterRepositoryImplIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: RegisterRepositoryImpl

    private val now get() = Clock.System.now().toEpochMilliseconds()

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        val syncEnqueuer = SyncEnqueuer(db)
        repo = RegisterRepositoryImpl(db, syncEnqueuer)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Inserts a minimal register row so sessions can reference it via FK.
     */
    private fun insertRegister(id: String, name: String = "Counter 1") {
        db.registersQueries.insertRegister(
            id                 = id,
            name               = name,
            current_session_id = null,
            is_active          = 1L,
            created_at         = now,
            updated_at         = now,
        )
    }

    // ── 1. openSession creates a session with status = OPEN ───────────────────

    @Test
    fun openSession_creates_session_with_status_OPEN() = runTest {
        insertRegister("reg-1")

        val result = repo.openSession(
            registerId     = "reg-1",
            openingBalance = 100.00,
            userId         = "user-1",
        )

        assertIs<Result.Success<RegisterSession>>(result)
        val session = result.data
        assertEquals("reg-1",                    session.registerId)
        assertEquals(100.00,                     session.openingBalance)
        assertEquals("user-1",                   session.openedBy)
        assertEquals(RegisterSession.Status.OPEN, session.status)
        assertNull(session.closedBy,     "closedBy must be null for an open session")
        assertNull(session.closedAt,     "closedAt must be null for an open session")
        assertNull(session.closingBalance, "closingBalance must be null for an open session")
    }

    // ── 2. getActive emits the open session via Flow ──────────────────────────

    @Test
    fun getActive_emits_open_session_after_openSession() = runTest {
        insertRegister("reg-2")

        repo.getActive().test {
            // Initial emission — no session open yet
            val initial = awaitItem()
            assertNull(initial, "No active session should exist before openSession is called")

            // Open a session — should trigger new emission
            repo.openSession(registerId = "reg-2", openingBalance = 200.00, userId = "user-2")

            val active = awaitItem()
            assertNotNull(active, "getActive should emit the newly opened session")
            assertEquals("reg-2",                    active.registerId)
            assertEquals(RegisterSession.Status.OPEN, active.status)

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── 3. closeSession changes status to CLOSED and persists actualBalance ───

    @Test
    fun closeSession_changes_status_to_CLOSED_and_sets_actualBalance() = runTest {
        insertRegister("reg-3")

        val openResult = repo.openSession(
            registerId     = "reg-3",
            openingBalance = 50.00,
            userId         = "user-3",
        )
        assertIs<Result.Success<RegisterSession>>(openResult)
        val sessionId = openResult.data.id

        val closeResult = repo.closeSession(
            sessionId     = sessionId,
            actualBalance = 47.50,
            userId        = "user-3",
        )

        assertIs<Result.Success<RegisterSession>>(closeResult)
        val closed = closeResult.data
        assertEquals(RegisterSession.Status.CLOSED, closed.status)
        assertEquals(47.50,   closed.actualBalance)
        assertEquals("user-3", closed.closedBy)
        assertNotNull(closed.closedAt, "closedAt must be set after closeSession")
    }

    // ── 4. addCashMovement inserts a movement; getMovements retrieves it ───────

    @Test
    fun addCashMovement_inserts_movement_and_getMovements_retrieves_it() = runTest {
        insertRegister("reg-4")

        val openResult = repo.openSession(
            registerId     = "reg-4",
            openingBalance = 100.00,
            userId         = "user-4",
        )
        assertIs<Result.Success<RegisterSession>>(openResult)
        val sessionId = openResult.data.id

        val movement = CashMovement(
            id         = "mov-1",
            sessionId  = sessionId,
            type       = CashMovement.Type.IN,
            amount     = 25.00,
            reason     = "Float top-up",
            recordedBy = "user-4",
            timestamp  = Instant.fromEpochMilliseconds(now),
        )

        val addResult = repo.addCashMovement(movement)
        assertIs<Result.Success<Unit>>(addResult)

        repo.getMovements(sessionId).test {
            val movements = awaitItem()
            assertEquals(1, movements.size)
            assertEquals("mov-1",          movements[0].id)
            assertEquals(sessionId,        movements[0].sessionId)
            assertEquals(CashMovement.Type.IN, movements[0].type)
            assertEquals(25.00,            movements[0].amount)
            assertEquals("Float top-up",   movements[0].reason)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── 5. Second openSession on same register → ValidationException ──────────

    @Test
    fun openSession_when_session_already_open_returns_SESSION_ALREADY_OPEN_error() = runTest {
        insertRegister("reg-5")

        // Open first session — must succeed
        val firstResult = repo.openSession(
            registerId     = "reg-5",
            openingBalance = 100.00,
            userId         = "user-5",
        )
        assertIs<Result.Success<RegisterSession>>(firstResult)

        // Attempt to open a second session on the same register
        val secondResult = repo.openSession(
            registerId     = "reg-5",
            openingBalance = 200.00,
            userId         = "user-5",
        )

        assertIs<Result.Error>(secondResult, "Expected Result.Error but got $secondResult")
        val exception = secondResult.exception
        assertIs<ValidationException>(exception)
        assertEquals("SESSION_ALREADY_OPEN", exception.rule,
            "Error rule should be SESSION_ALREADY_OPEN but was '${exception.rule}'")
    }
}
