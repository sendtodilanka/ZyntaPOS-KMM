package com.zyntasolutions.zyntapos.domain.usecase.auth

import com.zyntasolutions.zyntapos.domain.model.Permission
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Evaluates whether a user is authorised to perform a specific [Permission].
 *
 * ### Business Rules
 * 1. The check is performed synchronously against the in-memory RBAC matrix
 *    defined in [Permission.rolePermissions] — no network or DB call is required.
 * 2. A user's effective permissions are the union of all permissions granted to
 *    their assigned [Role].
 * 3. The ADMIN role implicitly holds **all** permissions (wildcard grant).
 * 4. If no session is active ([currentUser] is null), all permission checks return `false`.
 *
 * ### Usage
 * ```kotlin
 * val canVoid = checkPermissionUseCase(userId = session.userId, Permission.VOID_ORDER)
 * if (!canVoid) showError("Insufficient permissions")
 * ```
 *
 * This use case operates purely in the domain layer with zero framework dependencies.
 * It is consumed by permission-gated use cases (e.g., [ApplyItemDiscountUseCase],
 * [VoidOrderUseCase]) to centralise all RBAC evaluation.
 *
 * @param sessionFlow A [Flow] of the currently active [User]. The use case keeps a
 *                    snapshot of the last emitted value for synchronous checks.
 */
class CheckPermissionUseCase(
    private val sessionFlow: Flow<User?>,
) {
    // Snapshot cache — updated each time the session changes.
    private val _sessionSnapshot = MutableStateFlow<User?>(null)

    /**
     * Updates the internal user snapshot. Call from a ViewModel / DI graph that
     * collects the session flow.
     *
     * In Koin, wire this via a single-instance [CheckPermissionUseCase] that is
     * initialised with the live session flow and calls [updateSession] on collection.
     */
    fun updateSession(user: User?) {
        _sessionSnapshot.value = user
    }

    /**
     * Checks synchronously whether the currently signed-in user holds [permission].
     *
     * @param userId     The user ID to check (must match the active session user).
     * @param permission The permission to evaluate.
     * @return `true` if the user's role grants [permission]; `false` otherwise.
     */
    operator fun invoke(userId: String, permission: Permission): Boolean {
        val user = _sessionSnapshot.value ?: return false
        if (user.id != userId) return false
        val granted = Permission.rolePermissions[user.role] ?: return false
        return permission in granted
    }

    /**
     * Overload for role-only checks (when only a [Role] is available, not a user ID).
     *
     * @param role       The [Role] to evaluate against.
     * @param permission The permission to check.
     * @return `true` if [role] includes [permission].
     */
    operator fun invoke(role: Role, permission: Permission): Boolean {
        val granted = Permission.rolePermissions[role] ?: return false
        return permission in granted
    }
}
