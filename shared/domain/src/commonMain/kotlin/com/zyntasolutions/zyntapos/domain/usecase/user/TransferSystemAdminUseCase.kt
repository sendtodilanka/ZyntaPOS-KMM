package com.zyntasolutions.zyntapos.domain.usecase.user

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.repository.UserRepository

/**
 * Transfers the system admin designation from the current system admin to another ADMIN user.
 *
 * ## Business Rules (all enforced before the DB write)
 * 1. A system admin must already be designated ([requestingUserId] must hold the flag).
 * 2. [requestingUserId] must be the current system admin (prevents privilege escalation).
 * 3. [targetUserId] cannot be the same as [requestingUserId] (self-transfer is meaningless).
 * 4. The target user must exist in the repository.
 * 5. The target user must have [Role.ADMIN] (only admins can be system admin).
 * 6. The target user must be active (cannot transfer to a deactivated account).
 *
 * ## Atomicity
 * The actual flag swap in the database is performed atomically by
 * [UserRepository.transferSystemAdmin], which wraps both `clearAllSystemAdmin` and
 * `setSystemAdmin` SQL updates in a single transaction.
 *
 * @param requestingUserId UUID of the user requesting the transfer — must be current system admin.
 * @param targetUserId     UUID of the user who will receive the system admin designation.
 */
class TransferSystemAdminUseCase(
    private val userRepository: UserRepository,
) {

    suspend fun execute(requestingUserId: String, targetUserId: String): Result<Unit> {
        // Rule 3: self-transfer guard
        if (requestingUserId == targetUserId) {
            return Result.Error(
                ValidationException(
                    message = "Cannot transfer system admin designation to yourself.",
                    field = "targetUserId",
                    rule = "SELF_TRANSFER",
                )
            )
        }

        // Rule 1 + 2: requesting user must be the current system admin
        val systemAdminResult = userRepository.getSystemAdmin()
        if (systemAdminResult is Result.Error) return systemAdminResult
        val systemAdmin = (systemAdminResult as Result.Success).data
            ?: return Result.Error(
                ValidationException(
                    message = "No system admin is currently designated.",
                    field = "requestingUserId",
                    rule = "NO_SYSTEM_ADMIN",
                )
            )
        if (systemAdmin.id != requestingUserId) {
            return Result.Error(
                ValidationException(
                    message = "Only the system admin can transfer the system admin designation.",
                    field = "requestingUserId",
                    rule = "NOT_SYSTEM_ADMIN",
                )
            )
        }

        // Rule 4: target user must exist
        val targetResult = userRepository.getById(targetUserId)
        if (targetResult is Result.Error) return targetResult
        val targetUser = (targetResult as Result.Success).data

        // Rule 5: target must have ADMIN role
        if (targetUser.role != Role.ADMIN) {
            return Result.Error(
                ValidationException(
                    message = "Target user must have the ADMIN role to receive system admin designation.",
                    field = "targetUserId",
                    rule = "ADMIN_ROLE_REQUIRED",
                )
            )
        }

        // Rule 6: target must be active
        if (!targetUser.isActive) {
            return Result.Error(
                ValidationException(
                    message = "Cannot transfer system admin designation to an inactive user.",
                    field = "targetUserId",
                    rule = "USER_INACTIVE",
                )
            )
        }

        return userRepository.transferSystemAdmin(requestingUserId, targetUserId)
    }
}
