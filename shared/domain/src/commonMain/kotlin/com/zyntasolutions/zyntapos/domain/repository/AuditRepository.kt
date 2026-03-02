package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.domain.model.AuditEntry
import kotlinx.coroutines.flow.Flow

/**
 * Append-only repository for security and business audit log entries (Tier 1).
 *
 * **Contract:**
 * - [insert] is the only mutation — entries are never updated or deleted.
 * - [observeAll] and [observeByUserId] return live [Flow]s ordered by `timestamp DESC`.
 * - [getAllChronological] returns a snapshot in ascending order for hash-chain verification.
 * - [getLatestHash] returns the SHA-256 hash of the most recent entry (or null for empty log).
 * - [getRecentLoginFailureCount] supports brute-force detection at the auth layer.
 *
 * Implemented by `:shared:data` module ([AuditRepositoryImpl]) and injected via Koin
 * into [com.zyntasolutions.zyntapos.security.audit.SecurityAuditLogger].
 */
interface AuditRepository {

    // ── Write ─────────────────────────────────────────────────────────────────

    /**
     * Appends a new [AuditEntry] to the persistent audit log.
     *
     * @param entry The event record to persist. Entries are immutable once inserted.
     */
    suspend fun insert(entry: AuditEntry)

    // ── Reactive read ─────────────────────────────────────────────────────────

    /**
     * Observes all audit entries ordered by creation time descending.
     *
     * @return A hot [Flow] that emits a new list whenever a new entry is inserted.
     */
    fun observeAll(): Flow<List<AuditEntry>>

    /**
     * Observes all audit entries for a specific user, ordered by `timestamp DESC`.
     *
     * @param userId The actor user identifier to filter by.
     */
    fun observeByUserId(userId: String): Flow<List<AuditEntry>>

    // ── Integrity / verification ──────────────────────────────────────────────

    /**
     * Returns all entries in ascending chronological order.
     *
     * Used exclusively by [AuditIntegrityVerifier] to walk the hash chain from
     * genesis to the latest entry.
     */
    suspend fun getAllChronological(): List<AuditEntry>

    /**
     * Returns the SHA-256 hash of the most recently inserted audit entry, or null if empty.
     *
     * Used by [SecurityAuditLogger] to chain each new entry to its predecessor.
     */
    suspend fun getLatestHash(): String?

    // ── Aggregates / security ─────────────────────────────────────────────────

    /**
     * Returns the total number of entries in the audit log.
     */
    suspend fun countEntries(): Long

    /**
     * Returns the count of failed login attempts for [userId] since [sinceEpochMillis].
     *
     * Used by the auth layer for brute-force lockout detection.
     *
     * @param userId            User ID to check.
     * @param sinceEpochMillis  Unix epoch millisecond threshold (e.g., now - 15 minutes).
     */
    suspend fun getRecentLoginFailureCount(userId: String, sinceEpochMillis: Long): Long
}
