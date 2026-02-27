package com.zyntasolutions.zyntapos.domain.model

/**
 * Sealed wrapper for the three primary financial statement types produced by the accounting module.
 *
 * All statements are immutable value objects computed by use-case layer logic from
 * [JournalEntry] / [AccountBalance] data and are never persisted directly.
 */
sealed class FinancialStatement {

    /**
     * Profit and Loss Statement (Income Statement) for a date range.
     *
     * @property dateFrom Start of the reporting period (ISO: YYYY-MM-DD).
     * @property dateTo End of the reporting period (ISO: YYYY-MM-DD).
     * @property revenueLines Individual [FinancialStatementLine] entries for income accounts.
     * @property cogsLines Individual [FinancialStatementLine] entries for COGS accounts.
     * @property expenseLines Individual [FinancialStatementLine] entries for expense accounts.
     * @property totalRevenue Sum of all [revenueLines] amounts.
     * @property totalCogs Sum of all [cogsLines] amounts.
     * @property grossProfit [totalRevenue] minus [totalCogs].
     * @property totalExpenses Sum of all [expenseLines] amounts.
     * @property netProfit [grossProfit] minus [totalExpenses].
     * @property grossMarginPct Gross margin as a percentage: ([grossProfit] / [totalRevenue]) * 100.
     */
    data class PAndL(
        val dateFrom: String,
        val dateTo: String,
        val revenueLines: List<FinancialStatementLine>,
        val cogsLines: List<FinancialStatementLine>,
        val expenseLines: List<FinancialStatementLine>,
        val totalRevenue: Double,
        val totalCogs: Double,
        val grossProfit: Double,
        val totalExpenses: Double,
        val netProfit: Double,
        val grossMarginPct: Double,
    ) : FinancialStatement()

    /**
     * Balance Sheet as of a specific date.
     *
     * @property asOfDate Reporting date (ISO: YYYY-MM-DD).
     * @property assetLines Individual [FinancialStatementLine] entries for asset accounts.
     * @property liabilityLines Individual [FinancialStatementLine] entries for liability accounts.
     * @property equityLines Individual [FinancialStatementLine] entries for equity accounts.
     * @property totalAssets Sum of all [assetLines] amounts.
     * @property totalLiabilities Sum of all [liabilityLines] amounts.
     * @property totalEquity Sum of all [equityLines] amounts plus [retainedEarnings].
     * @property retainedEarnings Accumulated net profit/loss carried into equity from prior periods.
     */
    data class BalanceSheet(
        val asOfDate: String,
        val assetLines: List<FinancialStatementLine>,
        val liabilityLines: List<FinancialStatementLine>,
        val equityLines: List<FinancialStatementLine>,
        val totalAssets: Double,
        val totalLiabilities: Double,
        val totalEquity: Double,
        val retainedEarnings: Double,
    ) : FinancialStatement()

    /**
     * Trial Balance as of a specific date.
     *
     * The trial balance lists every active account with its total debits and credits.
     * [isBalanced] must be true ([totalDebits] == [totalCredits]) for the ledger to be correct.
     *
     * @property asOfDate Reporting date (ISO: YYYY-MM-DD).
     * @property lines One [TrialBalanceLine] per active account.
     * @property totalDebits Sum of all debit-side balances across [lines].
     * @property totalCredits Sum of all credit-side balances across [lines].
     * @property isBalanced True when [totalDebits] equals [totalCredits] (within floating-point epsilon).
     */
    data class TrialBalance(
        val asOfDate: String,
        val lines: List<TrialBalanceLine>,
        val totalDebits: Double,
        val totalCredits: Double,
        val isBalanced: Boolean,
    ) : FinancialStatement()
}

/**
 * A single account line within a [FinancialStatement.PAndL] or [FinancialStatement.BalanceSheet].
 *
 * @property accountId FK to the source [Account].
 * @property accountCode [Account.accountCode] — denormalized for display.
 * @property accountName [Account.accountName] — denormalized for display.
 * @property accountType Classification of the account. See [AccountType].
 * @property subCategory Grouping label within the statement section.
 * @property amount Net amount for this account (positive = normal balance side).
 */
data class FinancialStatementLine(
    val accountId: String,
    val accountCode: String,
    val accountName: String,
    val accountType: AccountType,
    val subCategory: String,
    val amount: Double,
)

/**
 * A single account line within a [FinancialStatement.TrialBalance].
 *
 * @property accountId FK to the source [Account].
 * @property accountCode [Account.accountCode] — denormalized for display.
 * @property accountName [Account.accountName] — denormalized for display.
 * @property accountType Classification of the account. See [AccountType].
 * @property normalBalance The side that increases this account. See [NormalBalance].
 * @property totalDebits Gross total of all debit postings to this account in the period.
 * @property totalCredits Gross total of all credit postings to this account in the period.
 * @property balance Net balance expressed on the [normalBalance] side.
 */
data class TrialBalanceLine(
    val accountId: String,
    val accountCode: String,
    val accountName: String,
    val accountType: AccountType,
    val normalBalance: NormalBalance,
    val totalDebits: Double,
    val totalCredits: Double,
    val balance: Double,
)
