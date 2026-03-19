package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.utils.IdGenerator
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.SyncConflict
import com.zyntasolutions.zyntapos.domain.repository.ConflictLogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * SQLDelight-backed implementation of [ConflictLogRepository].
 *
 * Maps between the [SyncConflict] domain model and the `conflict_log` table.
 * All reactive queries use [asFlow] + [mapToList] for automatic re-emission on change.
 */
class ConflictLogRepositoryImpl(
    private val db: ZyntaDatabase,
) : ConflictLogRepository {

    override fun getUnresolved(): Flow<List<SyncConflict>> =
        db.conflict_logQueries.getUnresolvedConflicts()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { it.toDomain() } }

    override fun getByEntity(entityType: String, entityId: String): Flow<List<SyncConflict>> =
        db.conflict_logQueries.getConflictsByEntity(entityType, entityId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun getUnresolvedCount(): Result<Int> = try {
        val count = db.conflict_logQueries.getUnresolvedCount().executeAsOne()
        Result.Success(count.toInt())
    } catch (e: Exception) {
        Result.Error(DatabaseException(e.message ?: "getUnresolvedCount failed", "SELECT conflict_log", e))
    }

    override suspend fun insert(conflict: SyncConflict): Result<Unit> = try {
        db.conflict_logQueries.insertConflict(
            id          = conflict.id.ifBlank { IdGenerator.newId() },
            entity_type = conflict.entityType,
            entity_id   = conflict.entityId,
            field_name  = conflict.fieldName,
            local_value = conflict.localValue,
            server_value = conflict.serverValue,
            created_at  = conflict.createdAt,
        )
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(DatabaseException(e.message ?: "insert conflict_log failed", "INSERT conflict_log", e))
    }

    override suspend fun resolve(
        id: String,
        resolvedBy: SyncConflict.Resolution,
        resolution: String,
        resolvedAt: Long,
    ): Result<Unit> = try {
        db.conflict_logQueries.resolveConflict(
            resolved_by = resolvedBy.name,
            resolution  = resolution,
            resolved_at = resolvedAt,
            id          = id,
        )
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(DatabaseException(e.message ?: "resolve conflict_log failed", "UPDATE conflict_log", e))
    }

    override suspend fun pruneOld(beforeEpochMillis: Long): Result<Unit> = try {
        db.conflict_logQueries.pruneOldResolved(beforeEpochMillis)
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(DatabaseException(e.message ?: "pruneOld conflict_log failed", "DELETE conflict_log", e))
    }
}

/** Maps a `conflict_log` SQLDelight row to the [SyncConflict] domain model. */
private fun com.zyntasolutions.zyntapos.db.Conflict_log.toDomain(): SyncConflict = SyncConflict(
    id          = id,
    entityType  = entity_type,
    entityId    = entity_id,
    fieldName   = field_name,
    localValue  = local_value,
    serverValue = server_value,
    resolvedBy  = resolved_by?.let { runCatching { SyncConflict.Resolution.valueOf(it) }.getOrNull() },
    resolution  = resolution,
    resolvedAt  = resolved_at,
    createdAt   = created_at,
)
