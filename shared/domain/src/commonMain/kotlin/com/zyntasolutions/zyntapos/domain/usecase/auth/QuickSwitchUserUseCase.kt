package com.zyntasolutions.zyntapos.domain.usecase.auth

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.User
import com.zyntasolutions.zyntapos.domain.repository.AuthRepository

/**
 * Switches the active POS session to a different user via PIN authentication.
 *
 * ### Business Rules
 * 1. PIN must be 4–6 numeric digits.
 * 2. Target user must be active and have a PIN set.
 * 3. On success the session is atomically switched — no full logout/login cycle.
 * 4. On failure the original session remains intact.
 *
 * @param authRepository Provides [AuthRepository.quickSwitch] for atomic session swap.
 */
class QuickSwitchUserUseCase(
    private val authRepository: AuthRepository,
) {
    /**
     * @param userId The ID of the user to switch to.
     * @param pin    The raw PIN entered by the operator.
     * @return [Result.Success] with the switched-to [User], or [Result.Error].
     */
    suspend operator fun invoke(userId: String, pin: String): Result<User> {
        if (pin.length !in 4..6 || !pin.all { it.isDigit() }) {
            return Result.Error(
                ValidationException(
                    "PIN must be 4–6 numeric digits.",
                    field = "pin",
                    rule = "INVALID_PIN_FORMAT",
                ),
            )
        }
        return authRepository.quickSwitch(userId, pin)
    }
}
