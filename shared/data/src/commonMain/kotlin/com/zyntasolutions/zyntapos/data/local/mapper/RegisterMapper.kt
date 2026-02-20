package com.zyntasolutions.zyntapos.data.local.mapper

import com.zyntasolutions.zyntapos.db.Cash_movements
import com.zyntasolutions.zyntapos.db.Register_sessions
import com.zyntasolutions.zyntapos.domain.model.CashMovement
import com.zyntasolutions.zyntapos.domain.model.RegisterSession
import kotlinx.datetime.Instant

/**
 * Maps between SQLDelight [Register_sessions] / [Cash_movements] entities and
 * domain [RegisterSession] / [CashMovement] models.
 */
object RegisterMapper {

    fun sessionToDomain(row: Register_sessions): RegisterSession = RegisterSession(
        id              = row.id,
        registerId      = row.register_id,
        openedBy        = row.opened_by,
        closedBy        = row.closed_by,
        openingBalance  = row.opening_balance,
        closingBalance  = row.closing_balance,
        expectedBalance = row.expected_balance ?: 0.0,
        actualBalance   = row.actual_balance,
        openedAt        = Instant.fromEpochMilliseconds(row.opened_at),
        closedAt        = row.closed_at?.let { Instant.fromEpochMilliseconds(it) },
        status          = RegisterSession.Status.valueOf(row.status),
    )

    fun movementToDomain(row: Cash_movements): CashMovement = CashMovement(
        id         = row.id,
        sessionId  = row.session_id,
        type       = CashMovement.Type.valueOf(row.type),
        amount     = row.amount,
        reason     = row.reason,
        recordedBy = row.recorded_by,
        timestamp  = Instant.fromEpochMilliseconds(row.timestamp),
    )
}
