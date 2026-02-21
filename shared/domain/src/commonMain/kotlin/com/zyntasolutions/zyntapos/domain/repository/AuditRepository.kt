package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.domain.model.AuditEntry
import kotlinx.coroutines.flow.Flow

/**
 * Append-only repository for security audit log entries.
 *
 * **Contract:**
 * - [insert] is the only mutation — entries are never updated or deleted.
 * - [observeAll] returns a live [Flow] ordered by `createdAt DESC`.
 * - [observeByUserId] returns events for a specific actor.
 *
 * Implemented by `:shared:data` module and injected via Koin into
 * [com.zyntasolutions.zyntapos.security.audit.SecurityAuditLogger].
 */
interface AuditRepository {

    /**
     * Appends a new [AuditEntry] to the persistent audit log.
     *
     * @param entry The event record to persist.
     */
    suspend fun insert(entry: AuditEntry)

    /**
     * Observes all audit entries ordered by creation time descending.
     *
     * @return A hot [Flow] that emits a new list whenever the log changes.
     */
    fun observeAll(): Flow<List<AuditEntry>>

    /**
     * Observes all audit entries for a specific user, ordered by `createdAt DESC`.
     *
     * @param userId The actor user identifier to filter by.
     */
    fun observeByUserId(userId: String): Flow<List<AuditEntry>>
}
