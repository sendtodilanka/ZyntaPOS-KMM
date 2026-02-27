package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.CustomRole
import com.zyntasolutions.zyntapos.domain.model.Permission
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.repository.RoleRepository
import com.zyntasolutions.zyntapos.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlin.time.Clock

/**
 * Concrete implementation of [RoleRepository].
 *
 * **Custom roles** are persisted in the `custom_roles` SQLite table via [db].
 * Permissions are serialised as a simple JSON string array stored in the `permissions`
 * TEXT column (e.g. `["PROCESS_SALE","VIEW_REPORTS"]`).
 *
 * **Built-in role overrides** are persisted in [settingsRepository] under the
 * `rbac.override.*` keys defined in [SettingsKeys]. An absent key means no override
 * exists and [Permission.rolePermissions] defaults apply.
 *
 * [Role.ADMIN] is always protected — its permissions cannot be overridden.
 */
class RoleRepositoryImpl(
    private val db: ZyntaDatabase,
    private val settingsRepository: SettingsRepository,
) : RoleRepository {

    private val q get() = db.custom_rolesQueries

    // ── Custom Roles ─────────────────────────────────────────────────────────

    override fun getAllCustomRoles(): Flow<List<CustomRole>> =
        q.getAllCustomRoles()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { row -> row.toDomain() } }

    override suspend fun getCustomRoleById(id: String): Result<CustomRole> = withContext(Dispatchers.IO) {
        runCatching {
            q.getCustomRoleById(id).executeAsOneOrNull()
                ?: return@withContext Result.Error(
                    DatabaseException("Custom role '$id' not found.", operation = "getCustomRoleById"),
                )
        }.fold(
            onSuccess = { Result.Success(it.toDomain()) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Fetch failed", cause = t)) },
        )
    }

    override suspend fun createCustomRole(role: CustomRole): Result<Unit> = withContext(Dispatchers.IO) {
        if (role.name.isBlank()) {
            return@withContext Result.Error(
                ValidationException("Role name must not be blank.", field = "name", rule = "REQUIRED"),
            )
        }
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            q.insertCustomRole(
                id          = role.id,
                name        = role.name,
                description = role.description,
                permissions = role.permissions.toJson(),
                created_at  = now,
                updated_at  = now,
            )
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Insert failed", cause = t)) },
        )
    }

    override suspend fun updateCustomRole(role: CustomRole): Result<Unit> = withContext(Dispatchers.IO) {
        if (role.name.isBlank()) {
            return@withContext Result.Error(
                ValidationException("Role name must not be blank.", field = "name", rule = "REQUIRED"),
            )
        }
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            q.updateCustomRole(
                name        = role.name,
                description = role.description,
                permissions = role.permissions.toJson(),
                updated_at  = now,
                id          = role.id,
            )
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Update failed", cause = t)) },
        )
    }

    override suspend fun deleteCustomRole(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            q.deleteCustomRole(id)
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Delete failed", cause = t)) },
        )
    }

    // ── Built-in Role Overrides ───────────────────────────────────────────────

    override suspend fun getBuiltInRolePermissions(role: Role): Set<Permission>? {
        if (role == Role.ADMIN) return null
        val json = settingsRepository.get(role.overrideKey()) ?: return null
        if (json.isBlank()) return null
        return json.toPermissionSet()
    }

    override suspend fun setBuiltInRolePermissions(
        role: Role,
        permissions: Set<Permission>,
    ): Result<Unit> {
        if (role == Role.ADMIN) {
            return Result.Error(
                ValidationException(
                    "ADMIN role permissions cannot be overridden.",
                    field = "role",
                    rule = "ADMIN_PROTECTED",
                ),
            )
        }
        return settingsRepository.set(role.overrideKey(), permissions.toJson())
    }

    override suspend fun resetBuiltInRolePermissions(role: Role): Result<Unit> {
        if (role == Role.ADMIN) return Result.Success(Unit) // no-op for ADMIN
        return settingsRepository.set(role.overrideKey(), "")
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun Role.overrideKey(): String = when (this) {
        Role.ADMIN         -> ""  // unused — ADMIN is protected
        Role.STORE_MANAGER -> KEY_OVERRIDE_STORE_MANAGER
        Role.CASHIER       -> KEY_OVERRIDE_CASHIER
        Role.ACCOUNTANT    -> KEY_OVERRIDE_ACCOUNTANT
        Role.STOCK_MANAGER -> KEY_OVERRIDE_STOCK_MANAGER
    }

    private companion object {
        // Settings keys for built-in role permission overrides.
        // Must match SettingsKeys constants in :composeApp:feature:settings.
        const val KEY_OVERRIDE_STORE_MANAGER = "rbac.override.STORE_MANAGER"
        const val KEY_OVERRIDE_CASHIER       = "rbac.override.CASHIER"
        const val KEY_OVERRIDE_ACCOUNTANT    = "rbac.override.ACCOUNTANT"
        const val KEY_OVERRIDE_STOCK_MANAGER = "rbac.override.STOCK_MANAGER"
    }

    private fun com.zyntasolutions.zyntapos.db.Custom_roles.toDomain(): CustomRole = CustomRole(
        id          = id,
        name        = name,
        description = description,
        permissions = permissions.toPermissionSet(),
        createdAt   = Instant.fromEpochMilliseconds(created_at),
        updatedAt   = Instant.fromEpochMilliseconds(updated_at),
    )
}

// ── JSON serialisation helpers (no external library needed) ──────────────────

/** Serialises a [Set] of [Permission]s to a compact JSON string array. */
private fun Set<Permission>.toJson(): String =
    "[" + joinToString(",") { "\"${it.name}\"" } + "]"

/** Parses a JSON string array back to a [Set] of [Permission]s.
 *  Unknown permission names are silently ignored for forward-compatibility. */
private fun String.toPermissionSet(): Set<Permission> {
    if (isBlank() || this == "[]") return emptySet()
    return removePrefix("[")
        .removeSuffix("]")
        .split(",")
        .mapNotNull { token ->
            val name = token.trim().removeSurrounding("\"")
            Permission.entries.find { it.name == name }
        }
        .toSet()
}
