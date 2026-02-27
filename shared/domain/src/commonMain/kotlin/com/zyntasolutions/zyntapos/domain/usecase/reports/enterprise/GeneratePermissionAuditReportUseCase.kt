package com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise

import com.zyntasolutions.zyntapos.domain.model.Permission
import com.zyntasolutions.zyntapos.domain.model.Role
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Generates a permission audit report showing the full RBAC permission matrix.
 *
 * Returns a map of each [Role] to its granted set of [Permission] values.
 * This report allows administrators to audit and verify role-based access control
 * assignments across the entire system without requiring a network or database call.
 *
 * The data is sourced directly from [Permission.rolePermissions] — the single
 * source of truth for role-to-permission mappings in the domain layer.
 */
class GeneratePermissionAuditReportUseCase {
    /**
     * @return A [Flow] emitting the complete RBAC matrix as a [Map] of [Role] to [Set] of [Permission].
     */
    operator fun invoke(): Flow<Map<Role, Set<Permission>>> = flow {
        emit(Permission.rolePermissions)
    }
}
