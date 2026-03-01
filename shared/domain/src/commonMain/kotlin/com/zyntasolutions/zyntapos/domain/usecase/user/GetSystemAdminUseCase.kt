package com.zyntasolutions.zyntapos.domain.usecase.user

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.User
import com.zyntasolutions.zyntapos.domain.repository.UserRepository

/**
 * Returns the single designated system admin for this installation, or `null` if none exists.
 *
 * There is at most one system admin per installation. The system admin is the superuser
 * created during the first-run onboarding wizard. The system admin designation can be
 * transferred to another ADMIN user via [TransferSystemAdminUseCase].
 *
 * ## Usage
 * ```kotlin
 * val result = getSystemAdminUseCase.execute()
 * when (result) {
 *     is Result.Success -> result.data // User? — null if no system admin set yet
 *     is Result.Error   -> // handle database failure
 *     else -> Unit
 * }
 * ```
 */
class GetSystemAdminUseCase(
    private val userRepository: UserRepository,
) {
    suspend fun execute(): Result<User?> = userRepository.getSystemAdmin()
}
