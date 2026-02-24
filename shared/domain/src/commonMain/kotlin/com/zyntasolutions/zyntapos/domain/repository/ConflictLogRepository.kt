package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.SyncConflict
import kotlinx.coroutines.flow.Flow

/**
 * Contract for CRDT conflict log management.
 *
 * Records are written by the sync engine whenever a field-level conflict is detected
 * during a push/pull cycle. Admin users review and manually resolve conflicts via the
 * [com.zyntasolutions.zyntapos.feature.admin] module.
 */
interface ConflictLogRepository {

    /** Emits all unresolved conflicts ordered by [SyncConflict.createdAt] ascending. Re-emits on change. */
    fun getUnresolved(): Flow<List<SyncConflict>>

    /** Emits all conflicts (resolved + unresolved) for a specific entity. */
    fun getByEntity(entityType: String, entityId: String): Flow<List<SyncConflict>>

    /** Returns the count of unresolved conflicts. Useful for admin badge display. */
    suspend fun getUnresolvedCount(): Result<Int>

    /**
     * Inserts a new conflict entry produced by the sync engine.
     *
     * @param conflict The detected conflict with `resolvedAt = null`.
     */
    suspend fun insert(conflict: SyncConflict): Result<Unit>

    /**
     * Resolves a conflict by recording the chosen resolution strategy and final value.
     *
     * @param id         The conflict record to resolve.
     * @param resolvedBy The strategy applied.
     * @param resolution The final value chosen (serialised as string).
     * @param resolvedAt Epoch millis of resolution.
     */
    suspend fun resolve(
        id: String,
        resolvedBy: SyncConflict.Resolution,
        resolution: String,
        resolvedAt: Long,
    ): Result<Unit>

    /** Hard-deletes all resolved conflicts older than [beforeEpochMillis]. */
    suspend fun pruneOld(beforeEpochMillis: Long): Result<Unit>
}
