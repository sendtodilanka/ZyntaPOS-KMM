package com.zyntasolutions.zyntapos.domain.usecase.fakes

import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.CustomRole
import com.zyntasolutions.zyntapos.domain.model.Permission
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.repository.RoleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

// ─────────────────────────────────────────────────────────────────────────────
// FakeRoleRepository
// ─────────────────────────────────────────────────────────────────────────────

/**
 * In-memory test double for [RoleRepository].
 *
 * Tracks all mutations so that unit tests can assert delegation behaviour:
 * - [createdRoles]    — ordered list of roles passed to [createCustomRole]
 * - [updatedRoles]    — ordered list of roles passed to [updateCustomRole]
 * - [deletedRoleIds]  — ordered list of IDs passed to [deleteCustomRole]
 * - [builtInOverrides]— per-role permission overrides (excluding ADMIN)
 *
 * Set [shouldFail] to `true` to make every mutating call return [Result.Error].
 */
class FakeRoleRepository : RoleRepository {

    /** Toggle to force all mutating operations to fail. */
    var shouldFail: Boolean = false

    private val _customRoles = MutableStateFlow<List<CustomRole>>(emptyList())

    /** Mutating call trackers */
    val createdRoles   = mutableListOf<CustomRole>()
    val updatedRoles   = mutableListOf<CustomRole>()
    val deletedRoleIds = mutableListOf<String>()
    val builtInOverrides = mutableMapOf<Role, Set<Permission>>()

    // ── Seeding helpers ───────────────────────────────────────────────────────

    /** Pre-populate custom roles without going through [createCustomRole]. */
    fun seedRoles(vararg roles: CustomRole) {
        _customRoles.value = roles.toList()
    }

    // ── RoleRepository implementation ─────────────────────────────────────────

    override fun getAllCustomRoles(): Flow<List<CustomRole>> = _customRoles

    override suspend fun getCustomRoleById(id: String): Result<CustomRole> =
        _customRoles.value.firstOrNull { it.id == id }
            ?.let { Result.Success(it) }
            ?: Result.Error(DatabaseException("Custom role '$id' not found."))

    override suspend fun createCustomRole(role: CustomRole): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("Forced failure"))
        createdRoles.add(role)
        _customRoles.value = _customRoles.value + role
        return Result.Success(Unit)
    }

    override suspend fun updateCustomRole(role: CustomRole): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("Forced failure"))
        updatedRoles.add(role)
        _customRoles.value = _customRoles.value.map { if (it.id == role.id) role else it }
        return Result.Success(Unit)
    }

    override suspend fun deleteCustomRole(id: String): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("Forced failure"))
        deletedRoleIds.add(id)
        _customRoles.value = _customRoles.value.filter { it.id != id }
        return Result.Success(Unit)
    }

    override suspend fun getBuiltInRolePermissions(role: Role): Set<Permission>? =
        builtInOverrides[role]

    override suspend fun setBuiltInRolePermissions(
        role: Role,
        permissions: Set<Permission>,
    ): Result<Unit> {
        if (role == Role.ADMIN) return Result.Error(DatabaseException("ADMIN is protected"))
        if (shouldFail) return Result.Error(DatabaseException("Forced failure"))
        builtInOverrides[role] = permissions
        return Result.Success(Unit)
    }

    override suspend fun resetBuiltInRolePermissions(role: Role): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("Forced failure"))
        builtInOverrides.remove(role)
        return Result.Success(Unit)
    }
}
