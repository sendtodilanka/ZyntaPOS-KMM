package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.AccountBalance
import com.zyntasolutions.zyntapos.domain.model.FinancialStatement
import com.zyntasolutions.zyntapos.domain.model.GeneralLedgerEntry

/**
 * Contract for computing and caching financial statements from the double-entry ledger.
 *
 * All statement computation methods aggregate posted [com.zyntasolutions.zyntapos.domain.model.JournalEntry]
 * and [com.zyntasolutions.zyntapos.domain.model.JournalEntryLine] records on demand. Results are
 * not persisted as statements — only [AccountBalance] cache records are stored.
 *
 * Implementations should prefer reading from the [AccountBalance] cache where possible and
 * fall back to full re-aggregation when the cache is stale or absent.
 */
interface FinancialStatementRepository {

    /**
     * Computes the Trial Balance as of [asOfDate] for [storeId].
     *
     * The trial balance lists every active account with its cumulative debit total, credit total,
     * and net balance from all posted [com.zyntasolutions.zyntapos.domain.model.JournalEntry]
     * records up to and including [asOfDate].
     *
     * [FinancialStatement.TrialBalance.isBalanced] will be `true` when total debits equal total
     * credits — a necessary condition for a correct ledger.
     *
     * @param storeId Scopes the computation to a specific store.
     * @param asOfDate The reporting date (ISO: YYYY-MM-DD); all entries on or before this date are included.
     * @return [Result] wrapping a [FinancialStatement.TrialBalance] computed from posted entries.
     */
    suspend fun getTrialBalance(storeId: String, asOfDate: String): Result<FinancialStatement.TrialBalance>

    /**
     * Computes the Profit and Loss (Income) Statement for [storeId] over a date range.
     *
     * Aggregates all posted [com.zyntasolutions.zyntapos.domain.model.JournalEntry] records
     * between [fromDate] and [toDate] (both inclusive) and categorises lines into revenue,
     * COGS, and expense sections. Computes gross profit, total expenses, and net profit.
     *
     * @param storeId Scopes the computation to a specific store.
     * @param fromDate Inclusive start of the reporting period (ISO: YYYY-MM-DD).
     * @param toDate Inclusive end of the reporting period (ISO: YYYY-MM-DD).
     * @return [Result] wrapping the computed [FinancialStatement.PAndL].
     */
    suspend fun getProfitAndLoss(
        storeId: String,
        fromDate: String,
        toDate: String,
    ): Result<FinancialStatement.PAndL>

    /**
     * Computes the Balance Sheet as of [asOfDate] for [storeId].
     *
     * Aggregates cumulative balances for all asset, liability, and equity accounts
     * from all posted entries up to and including [asOfDate]. Retained earnings are
     * computed from net profit/loss accumulated across all periods prior to [asOfDate].
     *
     * @param storeId Scopes the computation to a specific store.
     * @param asOfDate The reporting date (ISO: YYYY-MM-DD); cumulative balances as of this date are used.
     * @return [Result] wrapping the computed [FinancialStatement.BalanceSheet].
     */
    suspend fun getBalanceSheet(storeId: String, asOfDate: String): Result<FinancialStatement.BalanceSheet>

    /**
     * Computes the General Ledger report for a specific account over a date range.
     *
     * Returns a chronologically ordered list of [GeneralLedgerEntry] records — one per
     * [com.zyntasolutions.zyntapos.domain.model.JournalEntryLine] that affected [accountId]
     * between [fromDate] and [toDate] (inclusive). Each entry includes a [GeneralLedgerEntry.runningBalance]
     * computed cumulatively in the application layer.
     *
     * @param storeId Scopes the query to a specific store.
     * @param accountId FK to the [com.zyntasolutions.zyntapos.domain.model.Account] to report on.
     * @param fromDate Inclusive start of the reporting period (ISO: YYYY-MM-DD).
     * @param toDate Inclusive end of the reporting period (ISO: YYYY-MM-DD).
     * @return [Result] wrapping the ordered list of [GeneralLedgerEntry] records (may be empty).
     */
    suspend fun getGeneralLedger(
        storeId: String,
        accountId: String,
        fromDate: String,
        toDate: String,
    ): Result<List<GeneralLedgerEntry>>

    /**
     * Inserts or updates a single [AccountBalance] cache record.
     *
     * Used by the posting pipeline to keep the balance cache current after each journal entry
     * is posted. Implementations should use upsert semantics (INSERT OR REPLACE / ON CONFLICT UPDATE).
     *
     * @param balance The [AccountBalance] record to persist or overwrite.
     * @return [Result.Success] on success, [Result.Failure] on DB error.
     */
    suspend fun upsertBalance(balance: AccountBalance): Result<Unit>

    /**
     * Rebuilds all [AccountBalance] cache records for every account within [periodId].
     *
     * This is a full recalculation — existing balance rows for the period are deleted and
     * recomputed from scratch by summing all posted [com.zyntasolutions.zyntapos.domain.model.JournalEntryLine]
     * records within the period. Intended for use after sync conflict resolution or administrative
     * balance correction.
     *
     * @param storeId Scopes the rebuild to a specific store.
     * @param periodId FK to the [com.zyntasolutions.zyntapos.domain.model.AccountingPeriod] whose balances are rebuilt.
     * @return [Result.Success] when the rebuild completes, [Result.Failure] on DB error.
     */
    suspend fun rebuildAllBalances(storeId: String, periodId: String): Result<Unit>
}
