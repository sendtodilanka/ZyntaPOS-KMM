package com.zyntasolutions.zyntapos.data.local.mapper

import com.zyntasolutions.zyntapos.db.Pending_operations
import com.zyntasolutions.zyntapos.domain.model.SyncOperation
import kotlinx.datetime.Instant

/**
 * Maps between the SQLDelight-generated [Pending_operations] entity and the
 * domain [SyncOperation] model.
 *
 * Note: The SQL schema uses "CREATE"|"UPDATE"|"DELETE" while the domain model
 * uses [SyncOperation.Operation] (INSERT/UPDATE/DELETE). The mapper normalises
 * "CREATE" → INSERT on read; INSERT → "CREATE" on write for backward compatibility.
 */
object SyncOperationMapper {

    fun toDomain(row: Pending_operations): SyncOperation = SyncOperation(
        id          = row.id,
        entityType  = row.entity_type,
        entityId    = row.entity_id,
        operation   = when (row.operation.uppercase()) {
            "CREATE", "INSERT" -> SyncOperation.Operation.INSERT
            "DELETE"           -> SyncOperation.Operation.DELETE
            else               -> SyncOperation.Operation.UPDATE
        },
        payload     = row.payload,
        createdAt   = Instant.fromEpochMilliseconds(row.created_at),
        retryCount  = row.retry_count.toInt(),
        status      = when (row.status.uppercase()) {
            "SYNCED"          -> SyncOperation.Status.SYNCED
            "FAILED"          -> SyncOperation.Status.FAILED
            "SYNCING", "IN_FLIGHT" -> SyncOperation.Status.IN_FLIGHT
            else              -> SyncOperation.Status.PENDING
        },
    )

    /** Returns the SQL operation string used in [Pending_operations.operation]. */
    fun operationToSql(op: SyncOperation.Operation): String = when (op) {
        SyncOperation.Operation.INSERT -> "CREATE"
        SyncOperation.Operation.UPDATE -> "UPDATE"
        SyncOperation.Operation.DELETE -> "DELETE"
    }
}
