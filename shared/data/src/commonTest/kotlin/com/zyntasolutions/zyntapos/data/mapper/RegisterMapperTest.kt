package com.zyntasolutions.zyntapos.data.mapper

import com.zyntasolutions.zyntapos.data.local.mapper.RegisterMapper
import com.zyntasolutions.zyntapos.db.Cash_movements
import com.zyntasolutions.zyntapos.db.Register_sessions
import com.zyntasolutions.zyntapos.domain.model.CashMovement
import com.zyntasolutions.zyntapos.domain.model.RegisterSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.Instant

/**
 * ZyntaPOS — RegisterMapper Unit Tests (commonTest)
 *
 * Coverage (sessionToDomain):
 *  A. all required session fields mapped correctly
 *  B. null closed_by → null closedBy
 *  C. null closing_balance → null closingBalance
 *  D. null expected_balance → 0.0 default
 *  E. non-null expected_balance preserved
 *  F. null actual_balance → null actualBalance
 *  G. null closed_at → null closedAt
 *  H. non-null closed_at → Instant
 *  I. status string maps to RegisterSession.Status enum
 *
 * Coverage (movementToDomain):
 *  J. all movement fields mapped correctly
 *  K. type IN maps correctly
 *  L. type OUT maps correctly
 *  M. timestamp Long converts to Instant
 */
class RegisterMapperTest {

    private fun buildSessionRow(
        id: String = "sess-1",
        registerId: String = "reg-1",
        openedBy: String = "user-1",
        closedBy: String? = null,
        openingBalance: Double = 100.0,
        closingBalance: Double? = null,
        expectedBalance: Double? = null,
        actualBalance: Double? = null,
        openedAt: Long = 1_000_000L,
        closedAt: Long? = null,
        status: String = "OPEN",
        totalSales: Double? = null,
        totalRefunds: Double? = null,
        notes: String? = null,
    ) = Register_sessions(
        id = id,
        register_id = registerId,
        opened_by = openedBy,
        closed_by = closedBy,
        opening_balance = openingBalance,
        closing_balance = closingBalance,
        expected_balance = expectedBalance,
        actual_balance = actualBalance,
        opened_at = openedAt,
        closed_at = closedAt,
        status = status,
        total_sales = totalSales,
        total_refunds = totalRefunds,
        notes = notes,
    )

    private fun buildMovementRow(
        id: String = "mov-1",
        sessionId: String = "sess-1",
        type: String = "IN",
        amount: Double = 50.0,
        reason: String = "Float top-up",
        recordedBy: String = "user-1",
        timestamp: Long = 1_000_000L,
    ) = Cash_movements(
        id = id,
        session_id = sessionId,
        type = type,
        amount = amount,
        reason = reason,
        recorded_by = recordedBy,
        timestamp = timestamp,
    )

    // ── sessionToDomain ────────────────────────────────────────────────────────

    @Test
    fun `A - sessionToDomain maps all required fields correctly`() {
        val domain = RegisterMapper.sessionToDomain(
            buildSessionRow(id = "sess-99", registerId = "reg-5", openedBy = "user-7")
        )
        assertEquals("sess-99", domain.id)
        assertEquals("reg-5", domain.registerId)
        assertEquals("user-7", domain.openedBy)
        assertEquals(100.0, domain.openingBalance)
        assertEquals(Instant.fromEpochMilliseconds(1_000_000L), domain.openedAt)
    }

    @Test
    fun `B - sessionToDomain maps null closed_by to null closedBy`() {
        assertNull(RegisterMapper.sessionToDomain(buildSessionRow(closedBy = null)).closedBy)
    }

    @Test
    fun `C - sessionToDomain maps null closing_balance to null closingBalance`() {
        assertNull(RegisterMapper.sessionToDomain(buildSessionRow(closingBalance = null)).closingBalance)
    }

    @Test
    fun `D - sessionToDomain maps null expected_balance to 0 0`() {
        assertEquals(0.0, RegisterMapper.sessionToDomain(buildSessionRow(expectedBalance = null)).expectedBalance)
    }

    @Test
    fun `E - sessionToDomain preserves non-null expectedBalance`() {
        assertEquals(250.0, RegisterMapper.sessionToDomain(buildSessionRow(expectedBalance = 250.0)).expectedBalance)
    }

    @Test
    fun `F - sessionToDomain maps null actual_balance to null actualBalance`() {
        assertNull(RegisterMapper.sessionToDomain(buildSessionRow(actualBalance = null)).actualBalance)
    }

    @Test
    fun `G - sessionToDomain maps null closed_at to null closedAt`() {
        assertNull(RegisterMapper.sessionToDomain(buildSessionRow(closedAt = null)).closedAt)
    }

    @Test
    fun `H - sessionToDomain converts non-null closed_at to Instant`() {
        val domain = RegisterMapper.sessionToDomain(buildSessionRow(closedAt = 2_000_000L))
        assertEquals(Instant.fromEpochMilliseconds(2_000_000L), domain.closedAt)
    }

    @Test
    fun `I - sessionToDomain maps status string to RegisterSession Status enum`() {
        assertEquals(RegisterSession.Status.OPEN, RegisterMapper.sessionToDomain(buildSessionRow(status = "OPEN")).status)
        assertEquals(RegisterSession.Status.CLOSED, RegisterMapper.sessionToDomain(buildSessionRow(status = "CLOSED")).status)
    }

    // ── movementToDomain ──────────────────────────────────────────────────────

    @Test
    fun `J - movementToDomain maps all fields correctly`() {
        val domain = RegisterMapper.movementToDomain(
            buildMovementRow(id = "mov-99", sessionId = "sess-5", amount = 75.0, reason = "Bank drop", recordedBy = "mgr-1")
        )
        assertEquals("mov-99", domain.id)
        assertEquals("sess-5", domain.sessionId)
        assertEquals(75.0, domain.amount)
        assertEquals("Bank drop", domain.reason)
        assertEquals("mgr-1", domain.recordedBy)
    }

    @Test
    fun `K - movementToDomain maps type IN correctly`() {
        assertEquals(CashMovement.Type.IN, RegisterMapper.movementToDomain(buildMovementRow(type = "IN")).type)
    }

    @Test
    fun `L - movementToDomain maps type OUT correctly`() {
        assertEquals(CashMovement.Type.OUT, RegisterMapper.movementToDomain(buildMovementRow(type = "OUT")).type)
    }

    @Test
    fun `M - movementToDomain converts timestamp Long to Instant`() {
        val domain = RegisterMapper.movementToDomain(buildMovementRow(timestamp = 1_700_000_000_000L))
        assertEquals(Instant.fromEpochMilliseconds(1_700_000_000_000L), domain.timestamp)
    }
}
