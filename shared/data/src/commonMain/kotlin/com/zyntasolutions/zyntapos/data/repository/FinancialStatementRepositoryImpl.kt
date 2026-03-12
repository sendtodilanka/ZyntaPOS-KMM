package com.zyntasolutions.zyntapos.data.repository

import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.AccountBalance
import com.zyntasolutions.zyntapos.domain.model.AccountType
import com.zyntasolutions.zyntapos.domain.model.CashFlowLine
import com.zyntasolutions.zyntapos.domain.model.FinancialStatement
import com.zyntasolutions.zyntapos.domain.model.FinancialStatementLine
import com.zyntasolutions.zyntapos.domain.model.GeneralLedgerEntry
import com.zyntasolutions.zyntapos.domain.model.NormalBalance
import com.zyntasolutions.zyntapos.domain.model.TrialBalanceLine
import com.zyntasolutions.zyntapos.domain.repository.FinancialStatementRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.time.Clock

/**
 * SQLDelight-backed implementation of [FinancialStatementRepository].
 *
 * All statement computations aggregate posted [JournalEntry] / [JournalEntryLine] records
 * on demand using pre-built SQL aggregation queries from `journal_entry_lines.sq`.
 *
 * The [rebuildAllBalances] operation is a Phase 2 placeholder — CRDT merge logic is backlog.
 *
 * NOTE: The trial balance, P&L, and balance sheet SQL queries do not filter by `store_id`
 * because the underlying `chart_of_accounts` and `journal_entries` schemas join on account ID.
 * The `storeId` parameter is retained in the interface for future multi-store partitioning.
 */
class FinancialStatementRepositoryImpl(
    private val db: ZyntaDatabase,
) : FinancialStatementRepository {

    private val lq get() = db.journal_entry_linesQueries
    private val bq get() = db.account_balancesQueries

    // ── Trial Balance ─────────────────────────────────────────────────────────

    override suspend fun getTrialBalance(
        storeId: String,
        asOfDate: String,
    ): Result<FinancialStatement.TrialBalance> = withContext(Dispatchers.IO) {
        runCatching {
            val rows = lq.getTrialBalance(as_of_date = asOfDate).executeAsList()

            val lines = rows.map { row ->
                val normalBalance = runCatching { NormalBalance.valueOf(row.normal_balance) }
                    .getOrDefault(NormalBalance.DEBIT)
                val balance = when (normalBalance) {
                    NormalBalance.DEBIT -> row.total_debits - row.total_credits
                    NormalBalance.CREDIT -> row.total_credits - row.total_debits
                }
                TrialBalanceLine(
                    accountId = row.account_id,
                    accountCode = row.account_code,
                    accountName = row.account_name,
                    accountType = runCatching { AccountType.valueOf(row.account_type) }
                        .getOrDefault(AccountType.EXPENSE),
                    normalBalance = normalBalance,
                    totalDebits = row.total_debits,
                    totalCredits = row.total_credits,
                    balance = balance,
                )
            }

            val totalDebits = lines.sumOf { it.totalDebits }
            val totalCredits = lines.sumOf { it.totalCredits }
            val isBalanced = abs(totalDebits - totalCredits) < 0.005

            FinancialStatement.TrialBalance(
                asOfDate = asOfDate,
                lines = lines,
                totalDebits = totalDebits,
                totalCredits = totalCredits,
                isBalanced = isBalanced,
            )
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Trial balance failed", cause = t)) },
        )
    }

    // ── Profit & Loss ─────────────────────────────────────────────────────────

    override suspend fun getProfitAndLoss(
        storeId: String,
        fromDate: String,
        toDate: String,
    ): Result<FinancialStatement.PAndL> = withContext(Dispatchers.IO) {
        runCatching {
            val rows = lq.getPnLByAccountType(date_from = fromDate, date_to = toDate).executeAsList()

            val revenueLines = mutableListOf<FinancialStatementLine>()
            val cogsLines = mutableListOf<FinancialStatementLine>()
            val expenseLines = mutableListOf<FinancialStatementLine>()

            rows.forEach { row ->
                val accountType = runCatching { AccountType.valueOf(row.account_type) }
                    .getOrDefault(AccountType.EXPENSE)
                // For INCOME accounts (credit-normal), net = credits - debits
                // For COGS/EXPENSE accounts (debit-normal), net = debits - credits
                val amount = when (accountType) {
                    AccountType.INCOME -> row.total_credits - row.total_debits
                    AccountType.COGS, AccountType.EXPENSE -> row.total_debits - row.total_credits
                    else -> 0.0
                }
                val line = FinancialStatementLine(
                    accountId = "",   // not returned by this query — use accountCode as key
                    accountCode = row.account_code,
                    accountName = row.account_name,
                    accountType = accountType,
                    subCategory = row.sub_category,
                    amount = amount,
                )
                when (accountType) {
                    AccountType.INCOME -> revenueLines.add(line)
                    AccountType.COGS -> cogsLines.add(line)
                    AccountType.EXPENSE -> expenseLines.add(line)
                    else -> { /* ignore */ }
                }
            }

            val totalRevenue = revenueLines.sumOf { it.amount }
            val totalCogs = cogsLines.sumOf { it.amount }
            val grossProfit = totalRevenue - totalCogs
            val totalExpenses = expenseLines.sumOf { it.amount }
            val netProfit = grossProfit - totalExpenses
            val grossMarginPct = if (totalRevenue != 0.0) (grossProfit / totalRevenue) * 100.0 else 0.0

            FinancialStatement.PAndL(
                dateFrom = fromDate,
                dateTo = toDate,
                revenueLines = revenueLines,
                cogsLines = cogsLines,
                expenseLines = expenseLines,
                totalRevenue = totalRevenue,
                totalCogs = totalCogs,
                grossProfit = grossProfit,
                totalExpenses = totalExpenses,
                netProfit = netProfit,
                grossMarginPct = grossMarginPct,
            )
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "P&L failed", cause = t)) },
        )
    }

    // ── Balance Sheet ─────────────────────────────────────────────────────────

    override suspend fun getBalanceSheet(
        storeId: String,
        asOfDate: String,
    ): Result<FinancialStatement.BalanceSheet> = withContext(Dispatchers.IO) {
        runCatching {
            val rows = lq.getBalanceSheetByAccountType(as_of_date = asOfDate).executeAsList()

            val assetLines = mutableListOf<FinancialStatementLine>()
            val liabilityLines = mutableListOf<FinancialStatementLine>()
            val equityLines = mutableListOf<FinancialStatementLine>()

            rows.forEach { row ->
                val accountType = runCatching { AccountType.valueOf(row.account_type) }
                    .getOrDefault(AccountType.ASSET)
                val normalBalance = runCatching { NormalBalance.valueOf(row.normal_balance) }
                    .getOrDefault(NormalBalance.DEBIT)
                // Net amount expressed on normal balance side
                val amount = when (normalBalance) {
                    NormalBalance.DEBIT -> row.total_debits - row.total_credits
                    NormalBalance.CREDIT -> row.total_credits - row.total_debits
                }
                val line = FinancialStatementLine(
                    accountId = "",   // not returned by this query
                    accountCode = row.account_code,
                    accountName = row.account_name,
                    accountType = accountType,
                    subCategory = row.sub_category,
                    amount = amount,
                )
                when (accountType) {
                    AccountType.ASSET -> assetLines.add(line)
                    AccountType.LIABILITY -> liabilityLines.add(line)
                    AccountType.EQUITY -> equityLines.add(line)
                    else -> { /* ignore */ }
                }
            }

            val totalAssets = assetLines.sumOf { it.amount }
            val totalLiabilities = liabilityLines.sumOf { it.amount }
            val totalEquityDirect = equityLines.sumOf { it.amount }
            // Retained earnings = accounting equation: Assets = Liabilities + Equity
            val retainedEarnings = totalAssets - totalLiabilities - totalEquityDirect

            FinancialStatement.BalanceSheet(
                asOfDate = asOfDate,
                assetLines = assetLines,
                liabilityLines = liabilityLines,
                equityLines = equityLines,
                totalAssets = totalAssets,
                totalLiabilities = totalLiabilities,
                totalEquity = totalEquityDirect + retainedEarnings,
                retainedEarnings = retainedEarnings,
            )
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Balance sheet failed", cause = t)) },
        )
    }

    // ── General Ledger ────────────────────────────────────────────────────────

    override suspend fun getGeneralLedger(
        storeId: String,
        accountId: String,
        fromDate: String,
        toDate: String,
    ): Result<List<GeneralLedgerEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            val rows = lq.getLinesByAccountAndDateRange(
                account_id = accountId,
                date_from = fromDate,
                date_to = toDate,
            ).executeAsList()

            var runningBalance = 0.0
            rows.map { row ->
                // Running balance: accumulate debit - credit
                runningBalance += row.debit_amount - row.credit_amount
                GeneralLedgerEntry(
                    lineId = row.id,
                    journalEntryId = row.journal_entry_id,
                    entryDate = row.entry_date,
                    description = row.entry_description,
                    referenceType = row.reference_type,
                    referenceId = row.reference_id,
                    debit = row.debit_amount,
                    credit = row.credit_amount,
                    runningBalance = runningBalance,
                    isPosted = row.is_posted == 1L,
                )
            }
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "General ledger failed", cause = t)) },
        )
    }

    // ── Cash Flow Statement ───────────────────────────────────────────────────

    override suspend fun getCashFlowStatement(
        storeId: String,
        fromDate: String,
        toDate: String,
    ): Result<FinancialStatement.CashFlow> = withContext(Dispatchers.IO) {
        runCatching {
            // IAS 7 section classification by journal reference_type
            val operatingTypes = setOf("SALE", "EXPENSE", "CASH_IN", "CASH_OUT", "DISCOUNT", "TAX_PAYMENT", "REFUND")
            val investingTypes = setOf("PURCHASE", "RETURN", "STOCK_ADJUST", "DEPRECIATION")
            // Everything else (PAYROLL, PERIOD_CLOSE, MANUAL, etc.) → Financing

            fun labelFor(refType: String): String = when (refType) {
                "SALE"         -> "Sales receipts"
                "EXPENSE"      -> "Expense payments"
                "CASH_IN"      -> "Cash register float in"
                "CASH_OUT"     -> "Cash register float out"
                "DISCOUNT"     -> "Discount adjustments"
                "TAX_PAYMENT"  -> "Tax payments"
                "REFUND"       -> "Customer refunds"
                "PURCHASE"     -> "Inventory purchases"
                "RETURN"       -> "Supplier returns"
                "STOCK_ADJUST" -> "Stock adjustments"
                "DEPRECIATION" -> "Depreciation charges"
                "PAYROLL"      -> "Payroll disbursements"
                "PERIOD_CLOSE" -> "Period closing adjustments"
                "MANUAL"       -> "Manual entries"
                else           -> refType.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
            }

            val rows = lq.getCashFlowByReferenceType(
                date_from = fromDate,
                date_to = toDate,
            ).executeAsList()

            val operatingLines = mutableListOf<CashFlowLine>()
            val investingLines = mutableListOf<CashFlowLine>()
            val financingLines = mutableListOf<CashFlowLine>()

            rows.forEach { row ->
                val refType = row.reference_type
                val line = CashFlowLine(
                    label = labelFor(refType),
                    inflow = row.cash_inflow,
                    outflow = row.cash_outflow,
                    net = row.cash_inflow - row.cash_outflow,
                )
                when {
                    operatingTypes.contains(refType) -> operatingLines.add(line)
                    investingTypes.contains(refType) -> investingLines.add(line)
                    else                             -> financingLines.add(line)
                }
            }

            val netOperating = operatingLines.sumOf { it.net }
            val netInvesting = investingLines.sumOf { it.net }
            val netFinancing = financingLines.sumOf { it.net }
            val netChange = netOperating + netInvesting + netFinancing

            // closingCash = cumulative net cash in all cash accounts up to toDate (all-time)
            // openingCash = closingCash - period net change (mathematically equivalent to pre-period balance)
            val closingCash = lq.getCashBalanceAsOf(as_of_date = toDate).executeAsOne()
            val openingCash = closingCash - netChange

            FinancialStatement.CashFlow(
                dateFrom = fromDate,
                dateTo = toDate,
                operatingLines = operatingLines,
                investingLines = investingLines,
                financingLines = financingLines,
                netOperating = netOperating,
                netInvesting = netInvesting,
                netFinancing = netFinancing,
                netChange = netChange,
                openingCash = openingCash,
                closingCash = closingCash,
            )
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Cash flow statement failed", cause = t)) },
        )
    }

    // ── Balance Cache ─────────────────────────────────────────────────────────

    override suspend fun upsertBalance(balance: AccountBalance): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                bq.upsertBalance(
                    id = balance.id,
                    account_id = balance.accountId,
                    period_id = balance.periodId,
                    store_id = balance.storeId,
                    opening_balance = balance.openingBalance,
                    debit_total = balance.debitTotal,
                    credit_total = balance.creditTotal,
                    current_balance = balance.currentBalance,
                    last_updated = balance.lastUpdated,
                )
            }.fold(
                onSuccess = { Result.Success(Unit) },
                onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Upsert balance failed", cause = t)) },
            )
        }

    override suspend fun rebuildAllBalances(storeId: String, periodId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val period = db.accounting_periodsQueries.getPeriodById(periodId).executeAsOneOrNull()
                    ?: return@withContext Result.Error(
                        DatabaseException("Accounting period not found: $periodId")
                    )

                val dateFrom = period.start_date
                val dateTo = period.end_date
                val now = Clock.System.now().toEpochMilliseconds()

                // Get all accounts with posted entries up to period end
                val rows = lq.getTrialBalance(as_of_date = dateTo).executeAsList()

                db.transaction {
                    rows.forEach { row ->
                        val periodDebits = lq.getAccountDebitTotalForPeriod(
                            account_id = row.account_id,
                            date_from = dateFrom,
                            date_to = dateTo,
                        ).executeAsOne()
                        val periodCredits = lq.getAccountCreditTotalForPeriod(
                            account_id = row.account_id,
                            date_from = dateFrom,
                            date_to = dateTo,
                        ).executeAsOne()

                        val normalBalance = runCatching { NormalBalance.valueOf(row.normal_balance) }
                            .getOrDefault(NormalBalance.DEBIT)
                        val currentBalance = when (normalBalance) {
                            NormalBalance.DEBIT -> periodDebits - periodCredits
                            NormalBalance.CREDIT -> periodCredits - periodDebits
                        }

                        bq.upsertBalance(
                            id = "${row.account_id}_${periodId}_${storeId}",
                            account_id = row.account_id,
                            period_id = periodId,
                            store_id = storeId,
                            opening_balance = 0.0,
                            debit_total = periodDebits,
                            credit_total = periodCredits,
                            current_balance = currentBalance,
                            last_updated = now,
                        )
                    }
                }
            }.fold(
                onSuccess = { Result.Success(Unit) },
                onFailure = { t ->
                    Result.Error(DatabaseException(t.message ?: "rebuildAllBalances failed", cause = t))
                },
            )
        }
}
