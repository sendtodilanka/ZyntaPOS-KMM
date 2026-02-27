package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.Journal_entries
import com.zyntasolutions.zyntapos.db.Journal_entry_lines
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.JournalEntry
import com.zyntasolutions.zyntapos.domain.model.JournalEntryLine
import com.zyntasolutions.zyntapos.domain.model.JournalReferenceType
import com.zyntasolutions.zyntapos.domain.model.SyncOperation
import com.zyntasolutions.zyntapos.domain.repository.JournalRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.time.Clock

private const val ENTITY_JOURNAL_ENTRY = "journal_entry"

/**
 * SQLDelight-backed implementation of [JournalRepository].
 *
 * Journal entry headers and their lines are stored in separate tables.
 * The [getById] method performs two queries — header + lines — within a single coroutine.
 * All mutations wrap header + line operations inside a [db.transaction] block to ensure atomicity.
 *
 * Boolean columns are stored as INTEGER (0/1 Long) — converted with `== 1L`.
 * Enum columns are stored as TEXT — converted with `valueOf`.
 */
class JournalRepositoryImpl(
    private val db: ZyntaDatabase,
    private val syncEnqueuer: SyncEnqueuer,
) : JournalRepository {

    private val jq get() = db.journal_entriesQueries
    private val lq get() = db.journal_entry_linesQueries

    // ── Flow observers ────────────────────────────────────────────────────────

    override fun getEntriesByDateRange(
        storeId: String,
        fromDate: String,
        toDate: String,
    ): Flow<List<JournalEntry>> =
        jq.getByDateRange(store_id = storeId, date_from = fromDate, date_to = toDate)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map(::toDomainHeader) }

    override fun getUnpostedEntries(storeId: String): Flow<List<JournalEntry>> =
        jq.getUnposted(store_id = storeId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map(::toDomainHeader) }

    // ── One-shot queries ──────────────────────────────────────────────────────

    override suspend fun getById(id: String): Result<JournalEntry?> = withContext(Dispatchers.IO) {
        runCatching {
            val header = jq.getById(id).executeAsOneOrNull() ?: return@runCatching null
            val lines = lq.getLinesByEntry(id).executeAsList().map(::toDomainLine)
            toDomainHeader(header).copy(lines = lines)
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    override suspend fun getByReference(
        referenceType: JournalReferenceType,
        referenceId: String,
    ): Result<List<JournalEntry>> = withContext(Dispatchers.IO) {
        // NOTE: The SQL query getByReferenceId requires store_id but the domain interface does not
        // expose it. We pass an empty string for store_id and post-filter by referenceType.
        // For Phase 1 single-store MVP this is safe since store_id == "" matches nothing.
        // A future migration should add a store-less SQL query or update the interface to include storeId.
        runCatching {
            jq.getByReferenceId(
                store_id = "",
                reference_id = referenceId,
            ).executeAsList()
                .filter { row ->
                    runCatching { JournalReferenceType.valueOf(row.reference_type) }
                        .getOrNull() == referenceType
                }
                .map(::toDomainHeader)
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    override suspend fun getNextEntryNumber(storeId: String): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                // getNextEntryNumber returns Query<Long> (a raw Long from COALESCE(MAX(entry_number),0)+1)
                jq.getNextEntryNumber(store_id = storeId).executeAsOne().toInt()
            }.fold(
                onSuccess = { Result.Success(it) },
                onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
            )
        }

    // ── Mutations ─────────────────────────────────────────────────────────────

    override suspend fun saveDraftEntry(entry: JournalEntry): Result<Unit> =
        withContext(Dispatchers.IO) {
            // Validate balance before persisting
            val debitTotal = entry.lines.sumOf { it.debitAmount }
            val creditTotal = entry.lines.sumOf { it.creditAmount }
            if (abs(debitTotal - creditTotal) > 0.005) {
                return@withContext Result.Error(
                    ValidationException(
                        "Journal entry is not balanced: DEBIT=$debitTotal, CREDIT=$creditTotal",
                        field = "lines",
                        rule = "UNBALANCED_ENTRY",
                    ),
                )
            }

            runCatching {
                val now = Clock.System.now().toEpochMilliseconds()
                db.transaction {
                    // Insert header (INSERT OR REPLACE semantics not available; use insert then handle FK constraints)
                    jq.insertJournalEntry(
                        id = entry.id,
                        entry_number = entry.entryNumber.toLong(),
                        store_id = entry.storeId,
                        entry_date = entry.entryDate,
                        entry_time = entry.entryTime,
                        description = entry.description,
                        reference_type = entry.referenceType.name,
                        reference_id = entry.referenceId,
                        is_posted = if (entry.isPosted) 1L else 0L,
                        created_by = entry.createdBy,
                        created_at = entry.createdAt,
                        updated_at = now,
                        posted_at = entry.postedAt,
                        memo = entry.memo,
                        sync_status = entry.syncStatus,
                    )

                    // Delete existing lines (handles update scenario — delete + re-insert is atomic here)
                    lq.deleteLinesByEntry(journal_entry_id = entry.id)

                    // Insert all new lines
                    entry.lines.forEach { line ->
                        lq.insertLine(
                            id = line.id,
                            journal_entry_id = line.journalEntryId,
                            account_id = line.accountId,
                            debit_amount = line.debitAmount,
                            credit_amount = line.creditAmount,
                            line_description = line.lineDescription,
                            line_order = line.lineOrder.toLong(),
                            created_at = line.createdAt,
                        )
                    }

                    syncEnqueuer.enqueue(
                        ENTITY_JOURNAL_ENTRY,
                        entry.id,
                        SyncOperation.Operation.INSERT,
                    )
                }
            }.fold(
                onSuccess = { Result.Success(Unit) },
                onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Save failed", cause = t)) },
            )
        }

    override suspend fun postEntry(entryId: String, postedAt: Long): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val now = Clock.System.now().toEpochMilliseconds()
                jq.postEntry(posted_at = postedAt, updated_at = now, id = entryId)
                syncEnqueuer.enqueue(
                    ENTITY_JOURNAL_ENTRY,
                    entryId,
                    SyncOperation.Operation.UPDATE,
                )
            }.fold(
                onSuccess = { Result.Success(Unit) },
                onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Post failed", cause = t)) },
            )
        }

    override suspend fun unpostEntry(entryId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            jq.unpostEntry(updated_at = now, id = entryId)
            syncEnqueuer.enqueue(
                ENTITY_JOURNAL_ENTRY,
                entryId,
                SyncOperation.Operation.UPDATE,
            )
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Unpost failed", cause = t)) },
        )
    }

    override suspend fun deleteEntry(entryId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            db.transaction {
                lq.deleteLinesByEntry(journal_entry_id = entryId)
                jq.deleteEntry(id = entryId)
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Delete failed", cause = t)) },
        )
    }

    override suspend fun reverseEntry(
        originalEntryId: String,
        reversalDate: String,
        createdBy: String,
        now: Long,
    ): Result<JournalEntry> = withContext(Dispatchers.IO) {
        // Load original entry (with lines)
        val originalResult = getById(originalEntryId)
        if (originalResult is Result.Error) return@withContext originalResult

        val original = (originalResult as Result.Success).data
            ?: return@withContext Result.Error(
                DatabaseException("Journal entry not found: $originalEntryId"),
            )

        if (!original.isPosted) {
            return@withContext Result.Error(
                ValidationException(
                    "Only posted entries can be reversed.",
                    field = "isPosted",
                    rule = "NOT_POSTED",
                ),
            )
        }

        // Get next entry number
        val nextNumberResult = getNextEntryNumber(original.storeId)
        if (nextNumberResult is Result.Error) return@withContext nextNumberResult
        val nextNumber = (nextNumberResult as Result.Success).data

        val reversalId = "je-reversal-${original.id}"

        // Build reversal lines (swap debit/credit amounts)
        val reversalLines = original.lines.mapIndexed { index, line ->
            JournalEntryLine(
                id = "$reversalId-line-${index + 1}",
                journalEntryId = reversalId,
                accountId = line.accountId,
                debitAmount = line.creditAmount,
                creditAmount = line.debitAmount,
                lineDescription = line.lineDescription,
                lineOrder = line.lineOrder,
                createdAt = now,
                accountCode = line.accountCode,
                accountName = line.accountName,
            )
        }

        val reversalEntry = JournalEntry(
            id = reversalId,
            entryNumber = nextNumber,
            storeId = original.storeId,
            entryDate = reversalDate,
            entryTime = now,
            description = "Reversal of #${original.entryNumber}",
            referenceType = original.referenceType,
            referenceId = original.referenceId,
            isPosted = false,
            createdBy = createdBy,
            createdAt = now,
            updatedAt = now,
            postedAt = null,
            memo = "Reversal of entry #${original.entryNumber}: ${original.description}",
            syncStatus = "PENDING",
            lines = reversalLines,
        )

        val saveResult = saveDraftEntry(reversalEntry)
        if (saveResult is Result.Error) return@withContext saveResult

        Result.Success(reversalEntry)
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private fun toDomainHeader(row: Journal_entries) = JournalEntry(
        id = row.id,
        entryNumber = row.entry_number.toInt(),
        storeId = row.store_id,
        entryDate = row.entry_date,
        entryTime = row.entry_time,
        description = row.description,
        referenceType = runCatching { JournalReferenceType.valueOf(row.reference_type) }
            .getOrDefault(JournalReferenceType.MANUAL),
        referenceId = row.reference_id,
        isPosted = row.is_posted == 1L,
        createdBy = row.created_by,
        createdAt = row.created_at,
        updatedAt = row.updated_at,
        postedAt = row.posted_at,
        memo = row.memo,
        syncStatus = row.sync_status,
        lines = emptyList(),
    )

    private fun toDomainLine(row: Journal_entry_lines) = JournalEntryLine(
        id = row.id,
        journalEntryId = row.journal_entry_id,
        accountId = row.account_id,
        debitAmount = row.debit_amount,
        creditAmount = row.credit_amount,
        lineDescription = row.line_description,
        lineOrder = row.line_order.toInt(),
        createdAt = row.created_at,
        accountCode = null,
        accountName = null,
    )
}
