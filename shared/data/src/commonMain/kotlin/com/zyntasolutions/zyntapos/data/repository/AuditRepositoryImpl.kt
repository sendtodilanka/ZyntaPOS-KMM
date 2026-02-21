package com.zyntasolutions.zyntapos.data.repository

import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.AuditEntry
import com.zyntasolutions.zyntapos.domain.repository.AuditRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Concrete implementation of [AuditRepository].
 *
 * ## Storage model
 * Audit entries are persisted in the `audit_logs` SQLite table (append-only).
 * No update or delete operations are ever issued; the schema enforces immutability
 * at the application level.
 *
 * ## Reactivity
 * [observeAll] and [observeByUserId] will wrap SQLDelight `asFlow()` on their
 * respective queries once the `audit_logs.sq` schema file is generated.
 * Until then they use the standard TODO convention tracked in MERGED-D2.
 *
 * ## Thread-safety
 * [insert] switches to [Dispatchers.IO] to keep the calling coroutine off the
 * main thread.
 *
 * @param db  Encrypted [ZyntaDatabase] singleton, provided by Koin.
 */
class AuditRepositoryImpl(
    private val db: ZyntaDatabase,
) : AuditRepository {

    // ── Write ─────────────────────────────────────────────────────────

    /**
     * Appends [entry] to the `audit_logs` table.
     *
     * AuditLog rows are never synced to a remote server in Phase 1; the
     * [SyncEnqueuer] is intentionally omitted here.
     */
    override suspend fun insert(entry: AuditEntry): Unit = withContext(Dispatchers.IO) {
        TODO("Requires audit_logs SQLDelight schema — tracked in MERGED-D2")
    }

    // ── Reactive queries ──────────────────────────────────────────────

    /**
     * Emits all audit entries ordered by `created_at DESC`.
     *
     * Re-emits whenever a new entry is inserted via [insert].
     */
    override fun observeAll(): Flow<List<AuditEntry>> {
        TODO("Requires audit_logs SQLDelight schema — tracked in MERGED-D2")
    }

    /**
     * Emits audit entries for actor [userId], ordered by `created_at DESC`.
     *
     * Re-emits whenever a new matching entry is inserted.
     */
    override fun observeByUserId(userId: String): Flow<List<AuditEntry>> {
        TODO("Requires audit_logs SQLDelight schema — tracked in MERGED-D2")
    }
}
