package com.zyntasolutions.zyntapos.domain.usecase.register

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.RegisterSession
import com.zyntasolutions.zyntapos.domain.repository.RegisterRepository

/**
 * Closes the active register session and generates Z-report data.
 *
 * ### Business Rules — Expected vs Actual Balance
 *
 * ```
 * expectedBalance = openingBalance
 *                 + sum(CashMovement.IN)
 *                 - sum(CashMovement.OUT)
 *                 + sum(cashSalesInSession)
 * discrepancy     = actualBalance - expectedBalance
 * ```
 *
 * - A positive discrepancy = cash overage (more cash than expected).
 * - A negative discrepancy = cash shortage (less cash than expected).
 * - A discrepancy of 0 = balanced session.
 *
 * The discrepancy is recorded on [RegisterSession.actualBalance] and is visible
 * in the Z-report. No automated alerts are raised here; alert thresholds are a
 * Phase 2 feature.
 *
 * ### Additional Business Rules
 * 1. [actualBalance] must be ≥ 0.
 * 2. Only OPEN sessions may be closed. Attempting to close a CLOSED session
 *    returns [ValidationException] with rule `"SESSION_ALREADY_CLOSED"`.
 * 3. After closing, the session status transitions to [RegisterSession.Status.CLOSED].
 *
 * @param registerRepository Session lifecycle and balance management.
 */
class CloseRegisterSessionUseCase(
    private val registerRepository: RegisterRepository,
) {
    /**
     * Holds the computed Z-report summary after a session close.
     *
     * @property session        The updated (CLOSED) [RegisterSession].
     * @property discrepancy    `actualBalance - expectedBalance`. Negative = shortage.
     * @property isBalanced     `true` when `|discrepancy| < 0.01` (within rounding tolerance).
     */
    data class CloseResult(
        val session: RegisterSession,
        val discrepancy: Double,
        val isBalanced: Boolean,
    )

    /**
     * @param sessionId     The UUID of the session to close.
     * @param actualBalance The physical cash count entered by the operator (must be ≥ 0).
     * @param userId        The operator closing the session.
     * @return [Result.Success] with [CloseResult], or [Result.Error] on violation.
     */
    suspend operator fun invoke(
        sessionId: String,
        actualBalance: Double,
        userId: String,
    ): Result<CloseResult> {
        if (actualBalance < 0.0) {
            return Result.Error(
                ValidationException(
                    "Actual balance must be ≥ 0. Got $actualBalance.",
                    field = "actualBalance",
                    rule = "MIN_VALUE",
                ),
            )
        }

        val closeResult = registerRepository.closeSession(sessionId, actualBalance, userId)
        if (closeResult is Result.Error) return closeResult

        val closedSession = (closeResult as Result.Success).data
        val discrepancy = (closedSession.actualBalance ?: 0.0) - closedSession.expectedBalance

        return Result.Success(
            CloseResult(
                session = closedSession,
                discrepancy = discrepancy,
                isBalanced = kotlin.math.abs(discrepancy) < 0.01,
            ),
        )
    }
}
