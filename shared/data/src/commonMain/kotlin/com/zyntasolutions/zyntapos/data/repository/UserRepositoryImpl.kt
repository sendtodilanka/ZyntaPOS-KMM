package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.data.local.mapper.UserMapper
import com.zyntasolutions.zyntapos.domain.port.PasswordHashPort
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.QuickSwitchCandidate
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.model.SyncOperation
import com.zyntasolutions.zyntapos.domain.model.User
import com.zyntasolutions.zyntapos.domain.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/**
 * Concrete implementation of [UserRepository].
 *
 * ## Responsibilities
 * Manages staff account CRUD and password lifecycle for the local SQLite store.
 * Authentication (login / session / token) remains in [AuthRepositoryImpl].
 *
 * ## Password hashing
 * Raw passwords are never persisted. [PasswordHasher] is injected so the
 * platform-specific algorithm (BCrypt on JVM, Argon2 on Android) is used
 * without coupling this class to a concrete hash library.
 *
 * ## Reactivity
 * [getAll] wraps SQLDelight's `asFlow()` on the `getAllUsers` query; any
 * local mutation automatically re-emits to all active collectors.
 *
 * ## Thread-safety
 * All suspend functions switch to [Dispatchers.IO]. [getAll] emits on
 * [Dispatchers.IO] via `mapToList(Dispatchers.IO)`.
 *
 * @param db             Encrypted [ZyntaDatabase] singleton, provided by Koin.
 * @param syncEnqueuer   Writes a `pending_operations` row after every mutation.
 * @param passwordHasher Domain port for BCrypt hash operations (injected; adapter lives in :shared:security).
 */
class UserRepositoryImpl(
    private val db: ZyntaDatabase,
    private val syncEnqueuer: SyncEnqueuer,
    private val passwordHasher: PasswordHashPort,
) : UserRepository {

    private val q get() = db.usersQueries

    // ── Reactive query ────────────────────────────────────────────────

    override fun getAll(storeId: String?): Flow<List<User>> =
        q.getAllUsers()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows ->
                rows.map(UserMapper::toDomain)
                    .filter { storeId == null || it.storeId == storeId }
            }

    // ── One-shot read ─────────────────────────────────────────────────

    override suspend fun getById(id: String): Result<User> = withContext(Dispatchers.IO) {
        runCatching {
            q.getUserById(id).executeAsOneOrNull()
                ?: return@withContext Result.Error(
                    DatabaseException("User not found: $id", operation = "getUserById")
                )
        }.fold(
            onSuccess = { row -> Result.Success(UserMapper.toDomain(row)) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    // ── Write operations ──────────────────────────────────────────────

    override suspend fun create(user: User, plainPassword: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                // Guard: only one ADMIN account is permitted per installation (TODO-001).
                if (user.role == com.zyntasolutions.zyntapos.domain.model.Role.ADMIN) {
                    val existingAdmin = q.getSystemAdmin().executeAsOneOrNull()
                    if (existingAdmin != null) {
                        return@withContext Result.Error(
                            ValidationException("Only one admin account is allowed", field = "role")
                        )
                    }
                }
                val existingEmail = q.getUserByEmail(user.email).executeAsOneOrNull()
                if (existingEmail != null) {
                    return@withContext Result.Error(
                        ValidationException("Email already in use: ${user.email}", field = "email")
                    )
                }
                val passwordHash = passwordHasher.hash(plainPassword)
                val p = UserMapper.toInsertParams(user, passwordHash)
                val now = Clock.System.now().toEpochMilliseconds()
                db.transaction {
                    q.insertUser(
                        id              = p.id,
                        name            = p.name,
                        email           = p.email,
                        password_hash   = p.passwordHash,
                        role            = p.role,
                        pin_hash        = p.pinHash,
                        store_id        = p.storeId,
                        is_active       = p.isActive,
                        created_at      = now,
                        updated_at      = now,
                        sync_status     = p.syncStatus,
                        custom_role_id  = p.customRoleId,
                        is_system_admin = p.isSystemAdmin,
                    )
                    syncEnqueuer.enqueue(SyncOperation.EntityType.USER, p.id, SyncOperation.Operation.INSERT)
                }
            }.fold(
                onSuccess = { Result.Success(Unit) },
                onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Create failed", cause = t)) },
            )
        }

    override suspend fun update(user: User): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            db.transaction {
                // updateUserProfile targets only non-sensitive profile fields (name, role, active status).
                // Email and password_hash are never changed through this path — use dedicated use-cases.
                q.updateUserProfile(
                    name           = user.name,
                    role           = user.role.name,
                    custom_role_id = user.customRoleId,
                    is_active      = if (user.isActive) 1L else 0L,
                    updated_at     = now,
                    sync_status    = "PENDING",
                    id             = user.id,
                )
                syncEnqueuer.enqueue(SyncOperation.EntityType.USER, user.id, SyncOperation.Operation.UPDATE)
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Update failed", cause = t)) },
        )
    }

    override suspend fun updatePassword(userId: String, newPlainPassword: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val newHash = passwordHasher.hash(newPlainPassword)
                val now = Clock.System.now().toEpochMilliseconds()
                db.transaction {
                    q.updateUserPassword(
                        password_hash = newHash,
                        updated_at    = now,
                        sync_status   = "PENDING",
                        id            = userId,
                    )
                    syncEnqueuer.enqueue(SyncOperation.EntityType.USER, userId, SyncOperation.Operation.UPDATE)
                }
            }.fold(
                onSuccess = { Result.Success(Unit) },
                onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Password update failed", cause = t)) },
            )
        }

    override suspend fun deactivate(userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // Verify user exists before deactivating
            q.getUserById(userId).executeAsOneOrNull()
                ?: return@withContext Result.Error(
                    DatabaseException("User not found: $userId", operation = "deactivateUser")
                )
            val now = Clock.System.now().toEpochMilliseconds()
            db.transaction {
                // setUserActive targets only is_active flag — no risk of accidental field overwrites.
                q.setUserActive(
                    is_active   = 0L,
                    updated_at  = now,
                    sync_status = "PENDING",
                    id          = userId,
                )
                syncEnqueuer.enqueue(SyncOperation.EntityType.USER, userId, SyncOperation.Operation.UPDATE)
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Deactivate failed", cause = t)) },
        )
    }

    // ── System Admin ──────────────────────────────────────────────────────────

    override suspend fun getSystemAdmin(): Result<User?> = withContext(Dispatchers.IO) {
        runCatching {
            q.getSystemAdmin().executeAsOneOrNull()?.let { UserMapper.toDomain(it) }
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    override suspend fun adminExists(): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            q.getSystemAdmin().executeAsOneOrNull() != null
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    override suspend fun transferSystemAdmin(fromUserId: String, toUserId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val now = Clock.System.now().toEpochMilliseconds()
                db.transaction {
                    q.clearAllSystemAdmin(now)
                    q.setSystemAdmin(now, toUserId)
                    syncEnqueuer.enqueue(SyncOperation.EntityType.USER, fromUserId, SyncOperation.Operation.UPDATE)
                    syncEnqueuer.enqueue(SyncOperation.EntityType.USER, toUserId, SyncOperation.Operation.UPDATE)
                }
            }.fold(
                onSuccess = { Result.Success(Unit) },
                onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Transfer failed", cause = t)) },
            )
        }

    override suspend fun getQuickSwitchCandidates(storeId: String): Result<List<QuickSwitchCandidate>> =
        withContext(Dispatchers.IO) {
            runCatching {
                q.getQuickSwitchCandidates(storeId).executeAsList().map { row ->
                    QuickSwitchCandidate(
                        id = row.id,
                        name = row.name,
                        role = runCatching { Role.valueOf(row.role) }.getOrDefault(Role.CASHIER),
                    )
                }
            }.fold(
                onSuccess = { Result.Success(it) },
                onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
            )
        }
}
