package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.SyncOperation

/**
 * Contract for managing the offline-first sync queue.
 *
 * The sync queue stores [SyncOperation] records representing every local write that
 * has not yet been acknowledged by the remote server. [SyncEngine] consumes this
 * repository to drive the push-pull cycle.
 *
 * All methods are suspend — the sync engine runs entirely on a background dispatcher.
 */
interface SyncRepository {

    /**
     * Returns all [SyncOperation] records with status [SyncOperation.Status.PENDING]
     * or [SyncOperation.Status.IN_FLIGHT], ordered by [SyncOperation.createdAt] ascending.
     *
     * Used by [SyncEngine] to determine what to push in the next batch.
     * Returns an empty list (never throws) when the queue is empty.
     */
    suspend fun getPendingOperations(): List<SyncOperation>

    /**
     * Transitions the [SyncOperation] records identified by [ids] to
     * [SyncOperation.Status.SYNCED] and records the current timestamp.
     *
     * Typically called after the server returns a successful acknowledgement for a batch push.
     *
     * @param ids Set of [SyncOperation.id] values to mark as synced.
     */
    suspend fun markSynced(ids: List<String>): Result<Unit>

    /**
     * Pushes the supplied [operations] to the remote server in a single batch call.
     *
     * The method delegates network I/O to the [ApiService]. On a partial failure,
     * the data layer must:
     * 1. Mark successfully-acknowledged operations as SYNCED.
     * 2. Increment [SyncOperation.retryCount] for failed operations.
     * 3. Permanently mark as FAILED any operation whose retryCount reaches the max.
     *
     * @param ops The batch of pending [SyncOperation]s to push.
     * @return [Result.Success] with [Unit] when ALL operations were accepted by the server,
     *         or [Result.Error] with [com.zyntasolutions.zyntapos.core.result.ZentaException.NetworkException]
     *         on a total connectivity failure.
     */
    suspend fun pushToServer(ops: List<SyncOperation>): Result<Unit>

    /**
     * Fetches delta changes from the remote server since [lastSyncTimestamp].
     *
     * Received records are applied to the local database by the [SyncEngine] after
     * CRDT conflict resolution.
     *
     * @param lastSyncTimestamp Epoch-millis of the last successful pull. Pass `0` for
     *                          initial full sync.
     * @return [Result.Success] with the list of remote [SyncOperation] deltas, or
     *         [Result.Error] with [com.zyntasolutions.zyntapos.core.result.ZentaException.NetworkException].
     */
    suspend fun pullFromServer(lastSyncTimestamp: Long): Result<List<SyncOperation>>
}
