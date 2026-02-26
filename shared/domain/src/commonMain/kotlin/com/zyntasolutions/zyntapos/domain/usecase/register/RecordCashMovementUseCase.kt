package com.zyntasolutions.zyntapos.domain.usecase.register

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.core.utils.IdGenerator
import com.zyntasolutions.zyntapos.domain.model.CashMovement
import com.zyntasolutions.zyntapos.domain.repository.RegisterRepository
import kotlin.time.Clock

/**
 * Records a petty-cash movement (IN or OUT) within the active register session.
 *
 * ### Business Rules
 * 1. [amount] must be > 0.
 * 2. [reason] must not be blank — it is required for audit trail purposes.
 * 3. [type] must be [CashMovement.Type.IN] (cash added to drawer) or
 *    [CashMovement.Type.OUT] (cash removed from drawer).
 * 4. The running balance on the active session is updated by the repository
 *    implementation (reflected in the next `expectedBalance` computation).
 * 5. The movement is persisted and enqueued for sync.
 *
 * @param registerRepository Cash movement persistence.
 */
class RecordCashMovementUseCase(
    private val registerRepository: RegisterRepository,
) {
    /**
     * @param sessionId  The active register session ID.
     * @param type       Direction of the movement ([CashMovement.Type.IN] or [CashMovement.Type.OUT]).
     * @param amount     The cash amount (must be > 0).
     * @param reason     Mandatory explanation (e.g., "Petty cash - office supplies").
     * @param recordedBy FK to the user recording this movement.
     * @return [Result.Success] with [Unit] on success, or [Result.Error] on violation.
     */
    suspend operator fun invoke(
        sessionId: String,
        type: CashMovement.Type,
        amount: Double,
        reason: String,
        recordedBy: String,
    ): Result<Unit> {
        if (amount <= 0.0) {
            return Result.Error(
                ValidationException(
                    "Cash movement amount must be > 0. Got $amount.",
                    field = "amount",
                    rule = "MIN_VALUE",
                ),
            )
        }

        if (reason.isBlank()) {
            return Result.Error(
                ValidationException("Movement reason must not be blank.", field = "reason", rule = "REQUIRED"),
            )
        }

        val movement = CashMovement(
            id = IdGenerator.newId(),
            sessionId = sessionId,
            type = type,
            amount = amount,
            reason = reason,
            recordedBy = recordedBy,
            timestamp = Clock.System.now(),
        )

        return registerRepository.addCashMovement(movement)
    }
}
