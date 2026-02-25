package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.AccountingEntry
import com.zyntasolutions.zyntapos.domain.model.AccountSummary
import com.zyntasolutions.zyntapos.domain.model.AccountingReferenceType

/**
 * Contract for double-entry accounting ledger management.
 */
interface AccountingRepository {

    /**
     * Returns all entries for [storeId] in [fiscalPeriod] (YYYY-MM),
     * ordered by date then account code.
     */
    suspend fun getByStoreAndPeriod(storeId: String, fiscalPeriod: String): Result<List<AccountingEntry>>

    /**
     * Returns entries for a specific account code in the given period.
     */
    suspend fun getByAccountAndPeriod(
        storeId: String,
        accountCode: String,
        fiscalPeriod: String,
    ): Result<List<AccountingEntry>>

    /**
     * Returns all entries linked to a specific source document.
     */
    suspend fun getByReference(
        referenceType: AccountingReferenceType,
        referenceId: String,
    ): Result<List<AccountingEntry>>

    /**
     * Returns a summarised balance per account for a range of fiscal periods.
     *
     * @param storeId Store scope.
     * @param fromPeriod Inclusive start period (e.g., "2026-01").
     * @param toPeriod Inclusive end period (e.g., "2026-03").
     */
    suspend fun getSummaryForPeriodRange(
        storeId: String,
        fromPeriod: String,
        toPeriod: String,
    ): Result<List<AccountSummary>>

    /**
     * Inserts a balanced set of accounting entries.
     *
     * The implementation must validate that sum(DEBIT) == sum(CREDIT) before persisting.
     */
    suspend fun insertEntries(entries: List<AccountingEntry>): Result<Unit>
}
