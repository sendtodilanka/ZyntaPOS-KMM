package com.zyntasolutions.zyntapos.domain.model

/**
 * A single line in the double-entry accounting ledger.
 *
 * **DEPRECATED** — Use [JournalEntry] and [JournalEntryLine] instead.
 * The full double-entry accounting engine (Chart of Accounts, General Ledger, P&L,
 * Balance Sheet, Trial Balance, Cash Flow) is implemented in [JournalEntry],
 * [JournalEntryLine], and the 23 accounting use cases introduced in Wave 4.
 *
 * This model is retained for backward compatibility while legacy data is migrated.
 *
 * @property id Unique identifier (UUID v4).
 * @property storeId Store the entry belongs to.
 * @property accountCode Chart of accounts code (e.g., '4000', '5100').
 * @property accountName Human-readable account name (e.g., 'Sales Revenue', 'Wages Expense').
 * @property entryType Whether this line is a debit or credit.
 * @property amount Absolute amount of this entry.
 * @property referenceType Type of source document.
 * @property referenceId ID of the source document.
 * @property description Optional narrative for this entry.
 * @property entryDate ISO date: YYYY-MM-DD.
 * @property fiscalPeriod 'YYYY-MM' for monthly P&L aggregation.
 * @property createdBy User ID who created this entry.
 * @property createdAt Epoch millis of record creation.
 */
@Deprecated(
    message = "Use JournalEntry and JournalEntryLine instead. " +
        "CreateAccountingEntryUseCase → PostJournalEntryUseCase. " +
        "GetPeriodSummaryUseCase → GetAccountBalancesUseCase.",
    level = DeprecationLevel.WARNING,
)
data class AccountingEntry(
    val id: String,
    val storeId: String,
    val accountCode: String,
    val accountName: String,
    val entryType: AccountingEntryType,
    val amount: Double,
    val referenceType: AccountingReferenceType,
    val referenceId: String,
    val description: String? = null,
    val entryDate: String,
    val fiscalPeriod: String,
    val createdBy: String,
    val createdAt: Long,
) {
    init {
        require(amount > 0.0) { "Accounting entry amount must be positive" }
    }
}

/** Direction of an accounting entry. */
enum class AccountingEntryType {
    DEBIT,
    CREDIT,
}

/** Source document type for an accounting entry. */
enum class AccountingReferenceType {
    ORDER,
    EXPENSE,
    PAYMENT,
    CASH_MOVEMENT,
    ADJUSTMENT,
    PAYROLL,
}

/**
 * Aggregated balance for a single account over a fiscal period.
 *
 * @property accountCode Chart of accounts code.
 * @property accountName Human-readable account name.
 * @property entryType DEBIT or CREDIT.
 * @property total Sum of all entries for this account in the period.
 */
data class AccountSummary(
    val accountCode: String,
    val accountName: String,
    val entryType: AccountingEntryType,
    val total: Double,
)
