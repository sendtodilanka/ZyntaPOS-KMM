package com.zyntasolutions.zyntapos.domain.usecase.settings

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.User
import com.zyntasolutions.zyntapos.domain.repository.UserRepository

/**
 * Validates and persists a [User] account (create or update).
 *
 * **Validation rules:**
 * - [User.name] must not be blank.
 * - [User.email] must contain `@` and a domain segment.
 * - For new users [plainPassword] must be at least 8 characters.
 *
 * @param userRepository Persistence layer for user accounts.
 */
class SaveUserUseCase(private val userRepository: UserRepository) {

    /**
     * Creates or updates [user].
     *
     * @param user          The populated [User] model.
     * @param isUpdate      When `true`, [UserRepository.update] is called; otherwise
     *                      [UserRepository.create] is used and [plainPassword] is required.
     * @param plainPassword Raw password for new-user creation. Ignored on updates
     *                      (use [UserRepository.updatePassword] for password changes).
     * @return [Result.Success] on success; [Result.Error] wrapping a [ValidationException]
     *         if any validation rule is violated.
     */
    suspend operator fun invoke(
        user: User,
        isUpdate: Boolean,
        plainPassword: String = "",
    ): Result<Unit> {
        if (user.name.isBlank()) {
            return Result.Error(ValidationException("Name is required.", field = "name", rule = "REQUIRED"))
        }

        val emailRegex = Regex("^[^@]+@[^@]+\\.[^@]+$")
        if (!emailRegex.matches(user.email)) {
            return Result.Error(
                ValidationException("A valid email address is required.", field = "email", rule = "EMAIL_FORMAT")
            )
        }

        if (!isUpdate && plainPassword.length < 8) {
            return Result.Error(
                ValidationException(
                    "Password must be at least 8 characters.",
                    field = "password",
                    rule = "MIN_LENGTH",
                )
            )
        }

        return if (isUpdate) {
            userRepository.update(user)
        } else {
            userRepository.create(user, plainPassword)
        }
    }
}
