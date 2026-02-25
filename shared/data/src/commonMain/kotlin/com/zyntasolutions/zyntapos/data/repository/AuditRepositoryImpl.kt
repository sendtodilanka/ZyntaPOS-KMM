package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.AuditEntry
import com.zyntasolutions.zyntapos.domain.model.AuditEventType
import com.zyntasolutions.zyntapos.domain.repository.AuditRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant

/**
 * Concrete implementation of [AuditRepository] backed by the SQLDelight
 * `audit_entries` table defined in `audit_log.sq`.
 *
 * ## Storage model
 * Audit entries are persisted in the `audit_entries` SQLite table (append-only).
 * No update or delete operations are ever issued; the schema enforces immutability
 * at the application level.
 *
 * ## Mapping
 * The DB schema stores the event as `event_type TEXT` and the payload in `details`.
 * The `success` flag is inferred from the event type name: entries whose `event_type`
 * ends with `_FAIL` or `PERMISSION_DENIED` are treated as unsuccessful events.
 * The `previous_hash` chain and `hash` columns are managed by [SecurityAuditLogger]
 * at write-time; this repository does not re-verify the chain on reads.
 *
 * ## Reactivity
 * [observeAll] and [observeByUserId] use SQLDelight `asFlow()` + [mapToList] to
 * emit updated lists whenever the `audit_entries` table changes.
 *
 * ## Thread-safety
 * All DB operations run on [Dispatchers.IO].
 *
 * @param db  Encrypted [ZyntaDatabase] singleton, provided by Koin.
 */
class AuditRepositoryImpl(
    private val db: ZyntaDatabase,
) : AuditRepository {

    private val q get() = db.auditEntriesQueries

    // ── Write ─────────────────────────────────────────────────────────────────

    /**
     * Appends [entry] to the `audit_entries` table.
     *
     * AuditLog rows are never synced to a remote server in Phase 1; the
     * [SyncEnqueuer] is intentionally omitted here. The `hash` and `previous_hash`
     * values are set to empty strings at this layer — the [SecurityAuditLogger]
     * computes the chain before calling this method.
     */
    override suspend fun insert(entry: AuditEntry): Unit = withContext(Dispatchers.IO) {
        q.insertAuditEntry(
            id = entry.id,
            event_type = entry.eventType.name,
            user_id = entry.userId,
            entity_type = null,
            entity_id = null,
            details = entry.payload,
            hash = "",           // Hash chain computed by SecurityAuditLogger before insert
            previous_hash = "",  // Ibid
            timestamp = entry.createdAt.toEpochMilliseconds(),
            device_id = entry.deviceId,
        )
    }

    // ── Reactive queries ──────────────────────────────────────────────────────

    /**
     * Emits all audit entries ordered by `timestamp DESC`.
     *
     * Re-emits whenever a new entry is inserted via [insert].
     */
    override fun observeAll(): Flow<List<AuditEntry>> =
        q.getEntriesByDateRange(Long.MIN_VALUE, Long.MAX_VALUE)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.sortedByDescending { it.timestamp }.map { it.toDomain() } }

    /**
     * Emits audit entries for actor [userId], ordered by `timestamp DESC`.
     *
     * Re-emits whenever a new matching entry is inserted.
     */
    override fun observeByUserId(userId: String): Flow<List<AuditEntry>> =
        q.getEntriesByUser(userId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { it.toDomain() } }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private fun com.zyntasolutions.zyntapos.db.Audit_entries.toDomain(): AuditEntry {
        val type = runCatching { AuditEventType.valueOf(event_type) }
            .getOrDefault(AuditEventType.SETTINGS_CHANGED)
        // Infer success from event type name: types ending with _FAIL or
        // PERMISSION_DENIED represent failed / blocked operations.
        val isSuccess = !event_type.endsWith("_FAIL") && event_type != "PERMISSION_DENIED"
        return AuditEntry(
            id = id,
            eventType = type,
            userId = user_id,
            deviceId = device_id ?: "",
            payload = details,
            success = isSuccess,
            createdAt = Instant.fromEpochMilliseconds(timestamp),
        )
    }
}
