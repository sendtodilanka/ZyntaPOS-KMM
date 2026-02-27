package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.JournalEntry
import com.zyntasolutions.zyntapos.domain.model.JournalReferenceType
import kotlinx.coroutines.flow.Flow

/**
 * Contract for double-entry journal management.
 *
 * A [JournalEntry] is the atomic unit of the accounting ledger. This repository covers the
 * full lifecycle of journal entries: draft creation, posting, unposting, reversal, and deletion.
 *
 * Reactive [Flow] observers are provided for list views that must stay in sync with background
 * sync operations. [suspend] methods handle all one-shot reads and mutations.
 */
interface JournalRepository {

    /**
     * Observes all [JournalEntry] headers for [storeId] whose [JournalEntry.entryDate] falls
     * within [fromDate]..[toDate] (both dates inclusive, ISO format: YYYY-MM-DD).
     *
     * Returns header records only — [JournalEntry.lines] will be empty. Use [getById] to load
     * a full entry with its lines.
     *
     * @param storeId Scopes the query to a specific store.
     * @param fromDate Inclusive start date (ISO: YYYY-MM-DD).
     * @param toDate Inclusive end date (ISO: YYYY-MM-DD).
     * @return A [Flow] that emits the matching entries ordered by date then entry number.
     */
    fun getEntriesByDateRange(storeId: String, fromDate: String, toDate: String): Flow<List<JournalEntry>>

    /**
     * Observes all unposted (draft) [JournalEntry] records for [storeId].
     *
     * Draft entries have [JournalEntry.isPosted] = false and may still be edited or deleted.
     * Emits a new list whenever the set of unposted entries changes.
     *
     * @param storeId Scopes the query to a specific store.
     * @return A [Flow] that emits unposted entries ordered by [JournalEntry.entryDate] descending.
     */
    fun getUnpostedEntries(storeId: String): Flow<List<JournalEntry>>

    /**
     * Loads a single [JournalEntry] by its UUID, including its associated [JournalEntry.lines].
     *
     * Returns `null` inside [Result] when no entry with the given [id] exists.
     *
     * @param id The UUID of the journal entry to retrieve.
     * @return [Result] wrapping the fully-populated [JournalEntry] (with lines), or `null` if not found.
     */
    suspend fun getById(id: String): Result<JournalEntry?>

    /**
     * Finds all [JournalEntry] records linked to a specific source document.
     *
     * Used to display the accounting impact of a business event (e.g. which journal entries
     * were created for order ID "XYZ").
     *
     * @param referenceType Category of the source document. See [JournalReferenceType].
     * @param referenceId The UUID of the specific source document.
     * @return [Result] wrapping the list of matching entries (may be empty).
     */
    suspend fun getByReference(referenceType: JournalReferenceType, referenceId: String): Result<List<JournalEntry>>

    /**
     * Returns the next sequential entry number for [storeId].
     *
     * Implementations must generate this value atomically to prevent duplicate entry numbers
     * in concurrent environments (e.g. use a DB sequence or MAX(entryNumber)+1 within a transaction).
     *
     * @param storeId Scopes the sequence to a specific store.
     * @return [Result] wrapping the next integer entry number to assign.
     */
    suspend fun getNextEntryNumber(storeId: String): Result<Int>

    /**
     * Inserts or updates a draft [JournalEntry] and its [JournalEntry.lines].
     *
     * The entry is saved with [JournalEntry.isPosted] = false. The implementation must
     * validate that [JournalEntry.lines] are balanced (sum of debits == sum of credits) before
     * persisting. Returns a failure [Result] on validation or DB errors.
     *
     * @param entry The draft entry to persist. Must have [JournalEntry.isPosted] = false.
     * @return [Result.Success] on success, [Result.Failure] on imbalance or DB error.
     */
    suspend fun saveDraftEntry(entry: JournalEntry): Result<Unit>

    /**
     * Marks a draft entry as posted, locking it against further edits.
     *
     * Once posted, an entry's lines cannot be modified. Use [reverseEntry] to correct a
     * posted entry. Returns a failure [Result] if the entry does not exist or is already posted.
     *
     * @param entryId The UUID of the entry to post.
     * @param postedAt Epoch millis to record as the posting timestamp.
     * @return [Result.Success] on success, [Result.Failure] if not found or already posted.
     */
    suspend fun postEntry(entryId: String, postedAt: Long): Result<Unit>

    /**
     * Reverts a posted entry back to draft status.
     *
     * Intended for administrative correction workflows where an entry was posted prematurely.
     * Implementations must ensure the accounting period is not [com.zyntasolutions.zyntapos.domain.model.PeriodStatus.LOCKED]
     * before allowing an unpost. Returns a failure [Result] if the entry does not exist or
     * is already unposted.
     *
     * @param entryId The UUID of the entry to revert to draft.
     * @return [Result.Success] on success, [Result.Failure] if not found, already a draft, or period is locked.
     */
    suspend fun unpostEntry(entryId: String): Result<Unit>

    /**
     * Permanently deletes an unposted draft [JournalEntry] and all its lines.
     *
     * This operation is irreversible. Posted entries must not be deleted; use [reverseEntry]
     * instead. Returns a failure [Result] if the entry does not exist or is already posted.
     *
     * @param entryId The UUID of the draft entry to delete.
     * @return [Result.Success] on success, [Result.Failure] if not found or entry is posted.
     */
    suspend fun deleteEntry(entryId: String): Result<Unit>

    /**
     * Creates a reversal [JournalEntry] that negates the effect of [originalEntryId].
     *
     * The reversal entry contains the same lines as the original but with debit and credit
     * amounts swapped. It is saved as a draft (not yet posted) so the user can review before
     * committing. The original entry's [JournalEntry.memo] is prefixed with "REVERSAL OF #NNN".
     *
     * Returns a failure [Result] if the original entry does not exist or has not been posted.
     *
     * @param originalEntryId The UUID of the posted entry to reverse.
     * @param reversalDate Accounting date for the new reversal entry (ISO: YYYY-MM-DD).
     * @param createdBy FK to the [com.zyntasolutions.zyntapos.domain.model.User] creating the reversal.
     * @param now Epoch millis used as the creation/update timestamp for the new entry.
     * @return [Result] wrapping the newly-created (unsaved draft) reversal [JournalEntry].
     */
    suspend fun reverseEntry(
        originalEntryId: String,
        reversalDate: String,
        createdBy: String,
        now: Long,
    ): Result<JournalEntry>
}
