package com.zyntasolutions.zyntapos.domain.usecase.register

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.RegisterSession
import com.zyntasolutions.zyntapos.domain.repository.RegisterRepository
import kotlin.math.abs

/**
 * Closes the currently active register session and generates Z-report data.
 *
 * ### Business Rules
 * 1. The session identified by [sessionId] must exist and be in [RegisterSession.Status.OPEN] state.
 *    If not found or already closed, a [ValidationException] with rule `"SESSION_NOT_OPEN"` is returned.
 * 2. [actualBalance] must be ≥ 0 (negative counted cash is invalid).
 * 3. The data layer computes `expectedBalance = openingBalance + ∑(cashIn) − ∑(cashOut) + cashSales`
 *    and stores both the expected and actual values on the session.
 * 4. The **discrepancy** is `actualBalance − expectedBalance`:
 *    - Positive = cash over (operator counted more than expected).
 *    - Negative = cash short (potential loss).
 * 5. The session status transitions to [RegisterSession.Status.CLOSED] and
 *    `closedAt` / `closedBy` fields are populated.
 * 6. The closed session is persisted locally and enqueued for cloud sync.
 *
 * ### Z-Report Data
 * The returned [CloseResult] wraps the closed [RegisterSession] along with computed
 * discrepancy information necessary for the Z-report and reconciliation UI.
 *
 * @param registerRepository Register session lifecycle gateway.
 */
class CloseRegisterSessionUseCase(
    private val registerRepository: RegisterRepository,
) {

    /**
     * Result of a successful close operation.
     *
     * @property session     The closed [RegisterSession] with all fields populated.
     * @property discrepancy `actualBalance − expectedBalance`. Negative = short, positive = over.
     * @property isBalanced  `true` when the absolute discrepancy is within the tolerance threshold.
     */
    data class CloseResult(
        val session: RegisterSession,
        val discrepancy: Double,
        val isBalanced: Boolean,
    )

    /**
     * @param sessionId     UUID of the OPEN session to close.
     * @param actualBalance Physically counted cash in the drawer (must be ≥ 0).
     * @param userId        UUID of the operator performing the close.
     * @param tolerance     Maximum acceptable discrepancy in absolute value (default 10.0).
     * @return [Result.Success] with [CloseResult] containing the closed session and
     *         computed discrepancy, or [Result.Error] on violation.
     */
    suspend operator fun invoke(
        sessionId: String,
        actualBalance: Double,
        userId: String,
        tolerance: Double = DEFAULT_TOLERANCE,
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

        val repoResult = registerRepository.closeSession(
            sessionId = sessionId,
            actualBalance = actualBalance,
            userId = userId,
        )

        return when (repoResult) {
            is Result.Success -> {
                val closedSession = repoResult.data
                val discrepancy = actualBalance - closedSession.expectedBalance
                val isBalanced = abs(discrepancy) <= tolerance
                Result.Success(
                    CloseResult(
                        session = closedSession,
                        discrepancy = discrepancy,
                        isBalanced = isBalanced,
                    ),
                )
            }
            is Result.Error -> repoResult
            is Result.Loading -> Result.Loading
        }
    }

    companion object {
        /** Default tolerance for discrepancy detection (absolute value in store currency). */
        const val DEFAULT_TOLERANCE = 10.0
    }
}
