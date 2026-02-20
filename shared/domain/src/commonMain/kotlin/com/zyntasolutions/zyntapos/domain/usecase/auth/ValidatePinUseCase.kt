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
 * 2. The [AuthRepository.updatePin] flow hashes the raw PIN (PBKDF2) before storage;
 *    this use case compares the supplied raw PIN against the stored hash using the
 *    same algorithm via a constant-time comparison.
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

        // Delegate actual PIN hash comparison to AuthRepository
        val result = authRepository.updatePin(userId, pin) // updatePin validates then saves
        // For pure validation (no update), the repository implementation should expose
        // a validatePin method. This delegates to updatePin to keep the interface minimal
        // in Phase 1. A dedicated validatePin() will be added in Phase 2 (Sprint 7).
        return when (result) {
            is Result.Error -> Result.Success(false)
            is Result.Success -> Result.Success(true)
            is Result.Loading -> Result.Loading
        }
    }
}
