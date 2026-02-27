package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.AccountingPeriod
import kotlinx.coroutines.flow.Flow

/**
 * Contract for fiscal accounting period lifecycle management.
 *
 * [AccountingPeriod] records define the time boundaries within which [com.zyntasolutions.zyntapos.domain.model.JournalEntry]
 * records are allocated. This repository governs the full lifecycle of a period — creation,
 * closing, locking, and administrative reopening — as well as reactive observation of the
 * period list for UI screens.
 *
 * Period state transitions follow a strict one-way flow:
 * OPEN → CLOSED → LOCKED
 * Reopening (CLOSED → OPEN) is permitted only by an administrator and is tracked via [reopenPeriod].
 */
interface AccountingPeriodRepository {

    /**
     * Observes all [AccountingPeriod] records for [storeId], ordered by [AccountingPeriod.startDate]
     * descending (most recent period first).
     *
     * Emits a new list whenever any period is created, closed, locked, or reopened.
     *
     * @param storeId Scopes the query to a specific store.
     * @return A [Flow] that emits the full ordered period list on every change.
     */
    fun getAll(storeId: String): Flow<List<AccountingPeriod>>

    /**
     * Looks up a single [AccountingPeriod] by its UUID.
     *
     * Returns `null` inside [Result] when no period with the given [id] exists.
     *
     * @param id The unique identifier of the accounting period.
     * @return [Result] wrapping the [AccountingPeriod] if found, or `null` if no record exists.
     */
    suspend fun getById(id: String): Result<AccountingPeriod?>

    /**
     * Finds the [AccountingPeriod] that covers [date] and is in the OPEN state.
     *
     * Used by the journal posting pipeline to validate that the entry date falls within an
     * open period before committing. Returns `null` inside [Result] when no open period
     * contains the given date (e.g. the period is closed or has not been created yet).
     *
     * @param storeId Scopes the query to a specific store.
     * @param date The accounting date to look up (ISO format: YYYY-MM-DD).
     * @return [Result] wrapping the matching open [AccountingPeriod], or `null` if none found.
     */
    suspend fun getPeriodForDate(storeId: String, date: String): Result<AccountingPeriod?>

    /**
     * Returns a snapshot of all [AccountingPeriod] records that are currently in the OPEN state
     * for [storeId].
     *
     * Typically at most one period is open at a time, but the contract allows for adjustment
     * periods that may overlap with a regular period. The list is ordered by
     * [AccountingPeriod.startDate] ascending.
     *
     * @param storeId Scopes the query to a specific store.
     * @return [Result] wrapping the list of open periods (may be empty if none are open).
     */
    suspend fun getOpenPeriods(storeId: String): Result<List<AccountingPeriod>>

    /**
     * Inserts a new [AccountingPeriod] into the database.
     *
     * The implementation should validate that the new period's date range does not overlap
     * with an existing period for the same store (excluding adjustment periods). Returns a
     * failure [Result] on overlap, constraint violations, or DB errors.
     *
     * @param period The fully-populated period record to insert (must have [AccountingPeriod.status] = OPEN).
     * @return [Result.Success] on success, [Result.Failure] on overlap or DB error.
     */
    suspend fun create(period: AccountingPeriod): Result<Unit>

    /**
     * Transitions an [AccountingPeriod] from OPEN to CLOSED.
     *
     * A closed period no longer accepts new [com.zyntasolutions.zyntapos.domain.model.JournalEntry]
     * postings. The close can be reversed by an administrator using [reopenPeriod] as long as the
     * period has not yet been locked. Returns a failure [Result] if the period does not exist, is
     * already closed or locked, or if the DB update fails.
     *
     * @param id The UUID of the period to close.
     * @param updatedAt Epoch millis to record as the modification timestamp.
     * @return [Result.Success] on success, [Result.Failure] if not found, not open, or DB error.
     */
    suspend fun closePeriod(id: String, updatedAt: Long): Result<Unit>

    /**
     * Transitions an [AccountingPeriod] from CLOSED to LOCKED.
     *
     * A locked period is permanently sealed — no postings, corrections, or reopening are
     * permitted under any circumstance. Locking records the identity of the user who performed
     * the lock and the exact timestamp for audit purposes. Returns a failure [Result] if the
     * period does not exist, is not in the CLOSED state, or if the DB update fails.
     *
     * @param id The UUID of the period to lock.
     * @param lockedBy FK to the [com.zyntasolutions.zyntapos.domain.model.User] performing the lock.
     * @param lockedAt Epoch millis to record as the lock timestamp.
     * @return [Result.Success] on success, [Result.Failure] if not found, not closed, or DB error.
     */
    suspend fun lockPeriod(id: String, lockedBy: String, lockedAt: Long): Result<Unit>

    /**
     * Transitions an [AccountingPeriod] from CLOSED back to OPEN (admin-only operation).
     *
     * Reopening allows corrections to a period that was closed prematurely. This operation is
     * not permitted on LOCKED periods. The implementation should record [updatedAt] as the
     * modification timestamp and may optionally emit an audit event. Returns a failure [Result]
     * if the period does not exist, is already open, is locked, or if the DB update fails.
     *
     * @param id The UUID of the closed period to reopen.
     * @param updatedAt Epoch millis to record as the modification timestamp.
     * @return [Result.Success] on success, [Result.Failure] if not found, not closed, locked, or DB error.
     */
    suspend fun reopenPeriod(id: String, updatedAt: Long): Result<Unit>
}
