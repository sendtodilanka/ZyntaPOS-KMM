package com.zyntasolutions.zyntapos.debug.actions

import com.zyntasolutions.zyntapos.core.result.NetworkException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.SyncOperation
import com.zyntasolutions.zyntapos.domain.repository.SyncRepository

/**
 * Abstracts network/sync-related debug operations for the Network tab.
 */
interface NetworkActionHandler {
    /** Returns all [SyncOperation]s currently in the PENDING/IN_FLIGHT queue. */
    suspend fun getPendingOperations(): Result<List<SyncOperation>>

    /**
     * Marks all pending operations as SYNCED, effectively clearing the queue.
     * IRREVERSIBLE — only callable after typed-word confirmation.
     */
    suspend fun clearSyncQueue(): Result<Unit>

    /** Triggers an immediate sync cycle regardless of connectivity state. */
    suspend fun forceSyncNow(): Result<Unit>
}

/**
 * Default implementation backed by [SyncRepository].
 *
 * Note: "force offline mode" is a UI-only toggle stored in [DebugState].
 * True network interception requires a platform-level mock — deferred to Phase 2.
 */
class NetworkActionHandlerImpl(
    private val syncRepository: SyncRepository,
) : NetworkActionHandler {

    override suspend fun getPendingOperations(): Result<List<SyncOperation>> {
        return try {
            val ops = syncRepository.getPendingOperations()
            Result.Success(ops)
        } catch (e: Exception) {
            Result.Error(NetworkException("Failed to load sync queue: ${e.message}"))
        }
    }

    override suspend fun clearSyncQueue(): Result<Unit> {
        return try {
            val ops = syncRepository.getPendingOperations()
            if (ops.isEmpty()) return Result.Success(Unit)
            syncRepository.markSynced(ops.map { it.id })
        } catch (e: Exception) {
            Result.Error(NetworkException("Failed to clear sync queue: ${e.message}"))
        }
    }

    override suspend fun forceSyncNow(): Result<Unit> {
        return try {
            val ops = syncRepository.getPendingOperations()
            if (ops.isEmpty()) return Result.Success(Unit)
            syncRepository.pushToServer(ops)
        } catch (e: Exception) {
            Result.Error(NetworkException("Sync failed: ${e.message}"))
        }
    }
}
