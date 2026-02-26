package com.zyntasolutions.zyntapos.domain.usecase.auth

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.repository.AuthRepository

/**
 * Validates a cashier's quick-switch PIN for fast operator changeover at the POS.
 *
 * ### Business Rules
 * 1. PINs must be 4–6 digits (numeric only). Input outside this range is rejected
 *    immediately without a repository call.
 * 2. Delegates hash comparison to [AuthRepository.validatePin] — the raw PIN is never
 *    persisted or transmitted; only compared against the stored hash.
 * 3. This use case does NOT start a full session — it is used to identify which
 *    cashier is currently at the terminal. Full session management is handled by
 *    [LoginUseCase].
 *
 * @param authRepository Provides PIN validation (delegates to local hash comparison).
 */
class ValidatePinUseCase(
    private val authRepository: AuthRepository,
) {
    /**
     * @param userId The user ID whose PIN is being validated.
     * @param pin    The raw PIN entered by the cashier (4–6 digits, numeric).
     * @return [Result.Success] with `true` if valid, or [Result.Error] on format violation.
     */
    suspend operator fun invoke(userId: String, pin: String): Result<Boolean> {
        if (pin.length !in 4..6 || !pin.all { it.isDigit() }) {
            return Result.Error(
                ValidationException(
                    "PIN must be 4–6 numeric digits.",
                    field = "pin",
                    rule = "INVALID_PIN_FORMAT",
                ),
            )
        }

        return authRepository.validatePin(userId, pin)
    }
}
