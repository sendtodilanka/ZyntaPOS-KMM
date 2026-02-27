package com.zyntasolutions.zyntapos.domain.model

/**
 * Cached running balance for a single [Account] within a given [AccountingPeriod].
 *
 * Rather than summing all [JournalEntryLine] records on every report request, the
 * balance cache is maintained incrementally. It is rebuilt from scratch during
 * period-close procedures or after a sync conflict resolution.
 *
 * @property id Unique identifier (UUID v4).
 * @property accountId FK to the [Account] whose balance is recorded.
 * @property periodId FK to the [AccountingPeriod] this balance covers.
 * @property storeId FK to the store scope of this balance record.
 * @property openingBalance Balance carried forward from the previous period.
 * @property debitTotal Sum of all debit postings to [accountId] within [periodId].
 * @property creditTotal Sum of all credit postings to [accountId] within [periodId].
 * @property currentBalance Computed period-end balance: [openingBalance] adjusted by debits and credits
 *   according to the account's [NormalBalance].
 * @property lastUpdated Epoch millis of the most recent balance recalculation.
 */
data class AccountBalance(
    val id: String,
    val accountId: String,
    val periodId: String,
    val storeId: String,
    val openingBalance: Double,
    val debitTotal: Double,
    val creditTotal: Double,
    val currentBalance: Double,
    val lastUpdated: Long,
)
