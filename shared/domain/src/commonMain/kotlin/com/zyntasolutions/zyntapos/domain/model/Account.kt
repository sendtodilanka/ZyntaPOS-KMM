package com.zyntasolutions.zyntapos.domain.model

/**
 * A single entry in the Chart of Accounts.
 *
 * Accounts are the fundamental building blocks of the double-entry ledger.
 * Every [JournalEntryLine] references an account by [id].
 *
 * @property id Unique identifier (UUID v4).
 * @property accountCode Numeric code following the chart-of-accounts numbering scheme (e.g., "1010", "4010").
 * @property accountName Human-readable name displayed in reports (e.g., "Cash", "Sales Revenue").
 * @property accountType Broad classification that determines normal balance and statement placement.
 * @property subCategory Narrative grouping within the type (e.g., "Current Assets", "Revenue").
 * @property description Optional extended description shown in account setup screens.
 * @property normalBalance Side of the ledger that increases this account ([NormalBalance.DEBIT] or [NormalBalance.CREDIT]).
 * @property parentAccountId FK to a header [Account] for hierarchical chart structures. Null for top-level accounts.
 * @property isSystemAccount System-reserved accounts cannot be deleted or renamed.
 * @property isActive Inactive accounts are hidden from transaction entry but preserved for historical reports.
 * @property isHeaderAccount Header accounts group sub-accounts and cannot receive transactions directly.
 * @property allowTransactions True if journal lines may be posted to this account.
 * @property createdAt Epoch millis of record creation.
 * @property updatedAt Epoch millis of the last modification.
 */
data class Account(
    val id: String,
    val accountCode: String,
    val accountName: String,
    val accountType: AccountType,
    val subCategory: String,
    val description: String? = null,
    val normalBalance: NormalBalance,
    val parentAccountId: String? = null,
    val isSystemAccount: Boolean = false,
    val isActive: Boolean = true,
    val isHeaderAccount: Boolean = false,
    val allowTransactions: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Broad classification for a [Account] that determines its placement in financial statements. */
enum class AccountType {
    /** Cash, receivables, inventory, and other resources owned by the business. */
    ASSET,
    /** Loans, payables, and other obligations owed by the business. */
    LIABILITY,
    /** Owner's stake: retained earnings, capital contributions. */
    EQUITY,
    /** Revenue generated from core trading activities. */
    INCOME,
    /** Cost of Goods Sold — direct costs attributable to goods sold. */
    COGS,
    /** Operating and non-operating expenses (wages, rent, utilities, etc.). */
    EXPENSE,
}

/** The side of the ledger that increases a given [Account]. */
enum class NormalBalance {
    /** Assets, COGS, and expense accounts increase with debits. */
    DEBIT,
    /** Liabilities, equity, and income accounts increase with credits. */
    CREDIT,
}
