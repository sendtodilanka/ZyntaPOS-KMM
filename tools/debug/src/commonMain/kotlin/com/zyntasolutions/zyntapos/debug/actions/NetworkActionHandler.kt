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

    /**
     * Triggers an immediate sync cycle unless offline mode is forced.
     *
     * When [setOfflineMode] has been called with `true`, this call returns a
     * human-readable "blocked" result without touching the sync queue so that
     * testers can verify the app's offline behaviour.
     */
    suspend fun forceSyncNow(): Result<Unit>

    /**
     * Enables or disables the debug offline-mode flag.
     *
     * When `forced = true`:
     * - [forceSyncNow] is blocked (returns a "offline mode enabled" message).
     * - The Network Tab UI shows the device as offline.
     *
     * Note: this does not intercept OS-level network traffic. It is a
     * debug-console-level gate that prevents the manual sync trigger from
     * running. Full network interception (blocking Ktor calls) requires a
     * platform-level mock and is planned for Phase 2.
     */
    fun setOfflineMode(forced: Boolean)
}

/**
 * Default implementation backed by [SyncRepository].
 */
class NetworkActionHandlerImpl(
    private val syncRepository: SyncRepository,
) : NetworkActionHandler {

    @Volatile private var isOfflineModeForced: Boolean = false

    override fun setOfflineMode(forced: Boolean) {
        isOfflineModeForced = forced
    }

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
        if (isOfflineModeForced) {
            return Result.Error(NetworkException("Offline mode is enabled — sync blocked by debug console"))
        }
        return try {
            val ops = syncRepository.getPendingOperations()
            if (ops.isEmpty()) return Result.Success(Unit)
            syncRepository.pushToServer(ops)
        } catch (e: Exception) {
            Result.Error(NetworkException("Sync failed: ${e.message}"))
        }
    }
}
