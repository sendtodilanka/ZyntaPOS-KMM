package com.zyntasolutions.zyntapos.data.local

import com.zyntasolutions.zyntapos.core.utils.IdGenerator
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.SyncOperation
import kotlin.time.Clock

/**
 * Lightweight helper that enqueues a [SyncOperation] into the local `pending_operations`
 * table atomically with the caller's database transaction.
 *
 * Each repository receives this helper via constructor injection so sync enqueuing
 * is decoupled from the [SyncRepository] interface (which is for the SyncEngine to read).
 *
 * @param db The encrypted [ZyntaDatabase] instance.
 */
class SyncEnqueuer(
    private val db: ZyntaDatabase,
    private val localDeviceId: String = "",
    private val storeId: String = "",
) {

    /**
     * Inserts a row into `pending_operations` and increments the version vector
     * for the affected entity on this device (C6.1 CRDT support).
     *
     * This method is idempotent via `INSERT OR IGNORE` — duplicate entity+operation
     * combinations within the same millisecond are silently ignored.
     *
     * @param entityType  One of [SyncOperation.EntityType] string constants.
     * @param entityId    UUID of the affected domain record.
     * @param operation   The write action: [SyncOperation.Operation].
     * @param payload     JSON-serialised snapshot of the entity (use `"{}"` for DELETE).
     */
    fun enqueue(
        entityType: String,
        entityId: String,
        operation: SyncOperation.Operation,
        payload: String = "{}",
    ) {
        val now = Clock.System.now().toEpochMilliseconds()
        db.sync_queueQueries.enqueueOperation(
            id          = IdGenerator.newId(),
            entity_type = entityType,
            entity_id   = entityId,
            operation   = operation.toSqlString(),
            payload     = payload,
            created_at  = now,
            store_id    = storeId,
        )

        // Increment version vector for CRDT causal ordering (C6.1)
        if (localDeviceId.isNotBlank()) {
            db.version_vectorsQueries.upsert(
                entity_type = entityType,
                entity_id   = entityId,
                device_id   = localDeviceId,
                version     = 1,
                updated_at  = now,
            )
            db.version_vectorsQueries.incrementVersion(
                updated_at  = now,
                entity_type = entityType,
                entity_id   = entityId,
                device_id   = localDeviceId,
            )
        }
    }

    private fun SyncOperation.Operation.toSqlString(): String = when (this) {
        SyncOperation.Operation.INSERT -> "CREATE"
        SyncOperation.Operation.UPDATE -> "UPDATE"
        SyncOperation.Operation.DELETE -> "DELETE"
    }
}
