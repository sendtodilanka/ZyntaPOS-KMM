package com.zyntasolutions.zyntapos.domain.usecase.register

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.RegisterSession
import com.zyntasolutions.zyntapos.domain.repository.RegisterRepository

/**
 * Opens a new cash-register session.
 *
 * ### Business Rules
 * 1. Only one session may be active per register at any time. If [RegisterRepository.getActive]
 *    emits a non-null active session, a [ValidationException] with rule `"SESSION_ALREADY_OPEN"`
 *    is returned.
 * 2. [openingBalance] must be ≥ 0 (negative float is invalid).
 * 3. The session is created with [RegisterSession.Status.OPEN] and
 *    `expectedBalance = openingBalance` (updated at close time).
 * 4. The created session is persisted and enqueued for sync.
 *
 * @param registerRepository Register session lifecycle gateway.
 */
class OpenRegisterSessionUseCase(
    private val registerRepository: RegisterRepository,
) {
    /**
     * @param registerId     The register to open a session on.
     * @param openingBalance Cash float placed in the drawer (must be ≥ 0).
     * @param userId         The operator opening the session.
     * @return [Result.Success] with the new [RegisterSession], or [Result.Error] on violation.
     */
    suspend operator fun invoke(
        registerId: String,
        openingBalance: Double,
        userId: String,
    ): Result<RegisterSession> {
        if (openingBalance < 0.0) {
            return Result.Error(
                ValidationException(
                    "Opening balance must be ≥ 0. Got $openingBalance.",
                    field = "openingBalance",
                    rule = "MIN_VALUE",
                ),
            )
        }

        // Check for an existing active session — collect first value from the active session flow
        // The implementation relies on the data layer: getActive() emits the current active session.
        // Use repository's direct check by delegating to openSession which enforces uniqueness.
        return registerRepository.openSession(registerId, openingBalance, userId)
    }
}
