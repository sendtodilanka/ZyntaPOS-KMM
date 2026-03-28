package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.db.User_store_access
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.model.SyncOperation
import com.zyntasolutions.zyntapos.domain.model.UserStoreAccess
import com.zyntasolutions.zyntapos.domain.repository.UserStoreAccessRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNames
import kotlin.time.Clock

private val syncJson = Json { ignoreUnknownKeys = true; isLenient = true }

@Serializable
private data class UserStoreAccessSyncPayload(
    val id: String,
    @JsonNames("userId", "user_id")       val userId: String = "",
    @JsonNames("storeId", "store_id")     val storeId: String = "",
    @JsonNames("roleAtStore", "role_at_store") val roleAtStore: String? = null,
    @JsonNames("isActive", "is_active")   val isActive: Boolean = true,
    @JsonNames("grantedBy", "granted_by") val grantedBy: String? = null,
    @JsonNames("updatedAt", "updated_at") val updatedAt: Long = 0L,
    @JsonNames("createdAt", "created_at") val createdAt: Long = 0L,
)

/**
 * SQLDelight-backed implementation of [UserStoreAccessRepository] (C3.2).
 *
 * Manages multi-store user access grants. All writes enqueue a sync operation
 * so changes are replicated to the backend.
 */
class UserStoreAccessRepositoryImpl(
    private val db: ZyntaDatabase,
    private val syncEnqueuer: SyncEnqueuer,
) : UserStoreAccessRepository {

    private val q get() = db.user_store_accessQueries

    override fun getAccessibleStores(userId: String): Flow<List<UserStoreAccess>> =
        q.getAccessibleStores(userId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { it.toDomain() } }

    override fun getUsersForStore(storeId: String): Flow<List<UserStoreAccess>> =
        q.getUsersForStore(storeId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun getById(id: String): Result<UserStoreAccess> =
        withContext(Dispatchers.IO) {
            runCatching {
                q.getById(id).executeAsOneOrNull()?.toDomain()
                    ?: error("UserStoreAccess $id not found")
            }.fold(
                onSuccess = { Result.Success(it) },
                onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
            )
        }

    override suspend fun getByUserAndStore(
        userId: String,
        storeId: String,
    ): Result<UserStoreAccess?> = withContext(Dispatchers.IO) {
        runCatching {
            q.getByUserAndStore(userId, storeId).executeAsOneOrNull()?.toDomain()
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    override suspend fun grantAccess(access: UserStoreAccess): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val now = Clock.System.now().toEpochMilliseconds()
                db.transaction {
                    q.insertAccess(
                        id = access.id,
                        user_id = access.userId,
                        store_id = access.storeId,
                        role_at_store = access.roleAtStore?.name,
                        is_active = if (access.isActive) 1L else 0L,
                        granted_by = access.grantedBy,
                        created_at = access.createdAt.toEpochMilliseconds(),
                        updated_at = now,
                        sync_status = "PENDING",
                    )
                    syncEnqueuer.enqueue(
                        entityType = SyncOperation.EntityType.USER_STORE_ACCESS,
                        entityId = access.id,
                        operation = SyncOperation.Operation.INSERT,
                    )
                }
            }.fold(
                onSuccess = { Result.Success(Unit) },
                onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
            )
        }

    override suspend fun revokeAccess(userId: String, storeId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val now = Clock.System.now().toEpochMilliseconds()
                val existing = q.getByUserAndStore(userId, storeId).executeAsOneOrNull()
                db.transaction {
                    q.revokeAccess(
                        updated_at = now,
                        user_id = userId,
                        store_id = storeId,
                    )
                    if (existing != null) {
                        syncEnqueuer.enqueue(
                            entityType = SyncOperation.EntityType.USER_STORE_ACCESS,
                            entityId = existing.id,
                            operation = SyncOperation.Operation.INSERT,
                        )
                    }
                }
            }.fold(
                onSuccess = { Result.Success(Unit) },
                onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
            )
        }

    override suspend fun hasAccess(userId: String, storeId: String): Boolean =
        withContext(Dispatchers.IO) {
            val count = q.hasAccess(userId, storeId).executeAsOne()
            count > 0
        }

    /**
     * Upserts from a raw JSON sync payload (called by SyncEngine).
     */
    suspend fun upsertFromSync(payload: String) = withContext(Dispatchers.IO) {
        val dto = syncJson.decodeFromString<UserStoreAccessSyncPayload>(payload)
        val now = Clock.System.now().toEpochMilliseconds()
        q.insertAccess(
            id = dto.id,
            user_id = dto.userId,
            store_id = dto.storeId,
            role_at_store = dto.roleAtStore,
            is_active = if (dto.isActive) 1L else 0L,
            granted_by = dto.grantedBy,
            created_at = dto.createdAt.takeIf { it > 0 } ?: now,
            updated_at = dto.updatedAt.takeIf { it > 0 } ?: now,
            sync_status = "SYNCED",
        )
    }

    override suspend fun upsertFromSync(access: UserStoreAccess): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                q.insertAccess(
                    id = access.id,
                    user_id = access.userId,
                    store_id = access.storeId,
                    role_at_store = access.roleAtStore?.name,
                    is_active = if (access.isActive) 1L else 0L,
                    granted_by = access.grantedBy,
                    created_at = access.createdAt.toEpochMilliseconds(),
                    updated_at = access.updatedAt.toEpochMilliseconds(),
                    sync_status = "SYNCED",
                )
            }.fold(
                onSuccess = { Result.Success(Unit) },
                onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
            )
        }
}

/**
 * Maps a SQLDelight-generated row to the domain model.
 */
private fun User_store_access.toDomain(): UserStoreAccess = UserStoreAccess(
    id = id,
    userId = user_id,
    storeId = store_id,
    roleAtStore = role_at_store?.let { roleName ->
        runCatching { Role.valueOf(roleName) }.getOrNull()
    },
    isActive = is_active == 1L,
    grantedBy = granted_by,
    createdAt = Instant.fromEpochMilliseconds(created_at),
    updatedAt = Instant.fromEpochMilliseconds(updated_at),
)
