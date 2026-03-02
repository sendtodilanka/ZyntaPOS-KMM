package com.zyntasolutions.zyntapos.domain.usecase.user

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.repository.UserRepository

/**
 * Guard use case that enforces system admin access for privileged operations.
 *
 * Call [execute] at the start of any use case or action that should be restricted
 * to the single system admin user. Returns [Result.Success] if the requesting user
 * holds the system admin designation; returns [Result.Error] with a
 * [ValidationException] otherwise.
 *
 * ## Example usage
 * ```kotlin
 * class DeleteAllDataUseCase(
 *     private val guard: EnsureSystemAdminGuardUseCase,
 *     private val dataRepository: DataRepository,
 * ) {
 *     suspend fun execute(requestingUserId: String): Result<Unit> {
 *         guard.execute(requestingUserId).onError { return Result.Error(it.exception) }
 *         return dataRepository.deleteAll()
 *     }
 * }
 * ```
 *
 * ## Failure modes
 * - No system admin designated → [ValidationException] rule `NO_SYSTEM_ADMIN`
 * - Requesting user is not the system admin → [ValidationException] rule `INSUFFICIENT_PRIVILEGES`
 * - Database error reading system admin → propagated [Result.Error]
 */
class EnsureSystemAdminGuardUseCase(
    private val userRepository: UserRepository,
) {

    suspend fun execute(requestingUserId: String): Result<Unit> {
        val result = userRepository.getSystemAdmin()
        if (result is Result.Error) return result

        val systemAdmin = (result as Result.Success).data
            ?: return Result.Error(
                ValidationException(
                    message = "No system admin is configured for this installation.",
                    field = "requestingUserId",
                    rule = "NO_SYSTEM_ADMIN",
                )
            )

        if (systemAdmin.id != requestingUserId) {
            return Result.Error(
                ValidationException(
                    message = "This operation requires system admin privileges.",
                    field = "requestingUserId",
                    rule = "INSUFFICIENT_PRIVILEGES",
                )
            )
        }

        return Result.Success(Unit)
    }
}
