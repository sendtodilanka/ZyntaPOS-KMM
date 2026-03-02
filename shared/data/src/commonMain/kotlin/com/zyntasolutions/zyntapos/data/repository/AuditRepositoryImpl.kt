package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.AuditEntry
import com.zyntasolutions.zyntapos.domain.model.AuditEventType
import com.zyntasolutions.zyntapos.domain.model.Role
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
 * ## Column naming
 * The DB schema stores the payload in the `details` column (retained for backward
 * compatibility); this maps to [AuditEntry.payload] in the domain model.
 * The `timestamp` column stores Unix epoch milliseconds and maps to [AuditEntry.createdAt].
 *
 * ## Success flag
 * As of Migration 8, `success` is a persisted column (INTEGER 0/1). Rows created
 * before the migration default to 1 (success).
 *
 * ## Thread-safety
 * All DB operations run on [Dispatchers.IO].
 *
 * @param db  Encrypted [ZyntaDatabase] singleton, provided by Koin.
 */
class AuditRepositoryImpl(
    private val db: ZyntaDatabase,
) : AuditRepository {

    private val q get() = db.audit_logQueries

    // ── Write ─────────────────────────────────────────────────────────────────

    override suspend fun insert(entry: AuditEntry): Unit = withContext(Dispatchers.IO) {
        q.insertAuditEntry(
            id = entry.id,
            event_type = entry.eventType.name,
            user_id = entry.userId,
            user_name = entry.userName,
            user_role = entry.userRole?.name ?: "",
            entity_type = entry.entityType,
            entity_id = entry.entityId,
            details = entry.payload,
            previous_value = entry.previousValue,
            new_value = entry.newValue,
            success = if (entry.success) 1L else 0L,
            ip_address = entry.ipAddress,
            hash = entry.hash,
            previous_hash = entry.previousHash,
            timestamp = entry.createdAt.toEpochMilliseconds(),
            device_id = entry.deviceId,
        )
    }

    // ── Reactive queries ──────────────────────────────────────────────────────

    override fun observeAll(): Flow<List<AuditEntry>> =
        q.getEntriesByDateRange(Long.MIN_VALUE, Long.MAX_VALUE)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.sortedByDescending { it.timestamp }.map { it.toDomain() } }

    override fun observeByUserId(userId: String): Flow<List<AuditEntry>> =
        q.getEntriesByUser(userId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { it.toDomain() } }

    // ── Integrity / verification ──────────────────────────────────────────────

    override suspend fun getAllChronological(): List<AuditEntry> = withContext(Dispatchers.IO) {
        // getAllEntriesChronological returns a minimal projection for chain walking;
        // inflate to AuditEntry via toDomain() using the full row query approach.
        q.getEntriesByDateRange(Long.MIN_VALUE, Long.MAX_VALUE).executeAsList()
            .sortedBy { it.timestamp }
            .map { it.toDomain() }
    }

    override suspend fun getLatestHash(): String? = withContext(Dispatchers.IO) {
        q.getLatestHash().executeAsOneOrNull()
    }

    // ── Aggregates / security ─────────────────────────────────────────────────

    override suspend fun countEntries(): Long = withContext(Dispatchers.IO) {
        q.countEntries().executeAsOne()
    }

    override suspend fun getRecentLoginFailureCount(
        userId: String,
        sinceEpochMillis: Long,
    ): Long = withContext(Dispatchers.IO) {
        q.getRecentLoginFailures(user_id = userId, timestamp = sinceEpochMillis).executeAsOne()
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private fun com.zyntasolutions.zyntapos.db.Audit_entries.toDomain(): AuditEntry {
        val type = runCatching { AuditEventType.valueOf(event_type) }
            .getOrDefault(AuditEventType.SETTINGS_CHANGED)
        val role = user_role.takeIf { it.isNotBlank() }?.let { name ->
            runCatching { Role.valueOf(name) }.getOrNull()
        }
        return AuditEntry(
            id = id,
            eventType = type,
            userId = user_id,
            userName = user_name,
            userRole = role,
            deviceId = device_id,
            entityType = entity_type,
            entityId = entity_id,
            payload = details,
            previousValue = previous_value,
            newValue = new_value,
            success = success != 0L,
            ipAddress = ip_address,
            hash = hash,
            previousHash = previous_hash,
            createdAt = Instant.fromEpochMilliseconds(timestamp),
        )
    }
}
