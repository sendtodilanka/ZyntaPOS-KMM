package com.zyntasolutions.zyntapos.domain.usecase.auth

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.repository.AuthRepository

/**
 * Sets or changes the quick-switch PIN for a user.
 *
 * ### Business Rules
 * 1. [newPin] must be 4–6 numeric digits only.
 * 2. [newPin] and [confirmPin] must match exactly.
 * 3. Delegates hashing and storage to [AuthRepository.updatePin].
 *
 * ### Usage
 * Invoked by the Settings UI when an admin sets a PIN for any user, or when a user
 * changes their own PIN via the Security Settings screen.
 *
 * @param authRepository  Provides PIN hash + storage via [AuthRepository.updatePin].
 */
class SetPinUseCase(private val authRepository: AuthRepository) {

    /**
     * Sets or replaces the PIN for [userId].
     *
     * @param userId      UUID of the target user.
     * @param newPin      The raw PIN (4–6 numeric digits) chosen by the user.
     * @param confirmPin  Must equal [newPin]; prevents typo-induced lockouts.
     * @return [Result.Success] with [Unit] on success; [Result.Error] wrapping a
     *         [ValidationException] on format or mismatch failures, or a
     *         [com.zyntasolutions.zyntapos.core.result.DatabaseException] on storage failure.
     */
    suspend operator fun invoke(
        userId: String,
        newPin: String,
        confirmPin: String,
    ): Result<Unit> {
        if (newPin.length !in 4..6 || !newPin.all { it.isDigit() }) {
            return Result.Error(
                ValidationException(
                    "PIN must be 4–6 numeric digits.",
                    field = "newPin",
                    rule = "INVALID_PIN_FORMAT",
                ),
            )
        }
        if (newPin != confirmPin) {
            return Result.Error(
                ValidationException(
                    "PINs do not match.",
                    field = "confirmPin",
                    rule = "PIN_MISMATCH",
                ),
            )
        }
        return authRepository.updatePin(userId, newPin)
    }
}
