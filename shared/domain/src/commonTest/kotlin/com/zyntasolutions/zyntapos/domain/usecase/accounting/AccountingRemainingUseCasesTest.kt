package com.zyntasolutions.zyntapos.domain.usecase.accounting

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.Account
import com.zyntasolutions.zyntapos.domain.model.AccountBalance
import com.zyntasolutions.zyntapos.domain.model.AccountSummary
import com.zyntasolutions.zyntapos.domain.model.AccountType
import com.zyntasolutions.zyntapos.domain.model.AccountingEntry
import com.zyntasolutions.zyntapos.domain.model.AccountingPeriod
import com.zyntasolutions.zyntapos.domain.model.AccountingReferenceType
import com.zyntasolutions.zyntapos.domain.model.Budget
import com.zyntasolutions.zyntapos.domain.model.FinancialStatement
import com.zyntasolutions.zyntapos.domain.model.GeneralLedgerEntry
import com.zyntasolutions.zyntapos.domain.model.JournalEntry
import com.zyntasolutions.zyntapos.domain.model.JournalReferenceType
import com.zyntasolutions.zyntapos.domain.model.NormalBalance
import com.zyntasolutions.zyntapos.domain.model.PeriodStatus
import com.zyntasolutions.zyntapos.domain.model.StandardAccountCodes
import com.zyntasolutions.zyntapos.domain.repository.AccountRepository
import com.zyntasolutions.zyntapos.domain.repository.AccountingPeriodRepository
import com.zyntasolutions.zyntapos.domain.repository.AccountingRepository
import com.zyntasolutions.zyntapos.domain.repository.BudgetRepository
import com.zyntasolutions.zyntapos.domain.repository.FinancialStatementRepository
import com.zyntasolutions.zyntapos.domain.repository.JournalRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeExpenseRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeOrderRepository
import com.zyntasolutions.zyntapos.domain.usecase.reports.GenerateSalesReportUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// ─────────────────────────────────────────────────────────────────────────────
// Fixture Builders
// ─────────────────────────────────────────────────────────────────────────────

private fun buildAccountingPeriod(
    id: String = "period-01",
    periodName: String = "March 2026",
    startDate: String = "2026-03-01",
    endDate: String = "2026-03-31",
    status: PeriodStatus = PeriodStatus.OPEN,
    fiscalYearStart: String = "2026-01-01",
    createdAt: Long = 1_000_000L,
    updatedAt: Long = 1_000_000L,
) = AccountingPeriod(
    id = id,
    periodName = periodName,
    startDate = startDate,
    endDate = endDate,
    status = status,
    fiscalYearStart = fiscalYearStart,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun buildAccount(
    id: String = "acc-01",
    accountCode: String = StandardAccountCodes.CASH,
    accountName: String = "Cash",
    accountType: AccountType = AccountType.ASSET,
    subCategory: String = "Current Assets",
    normalBalance: NormalBalance = NormalBalance.DEBIT,
    isSystemAccount: Boolean = false,
    isActive: Boolean = true,
    createdAt: Long = 1_000_000L,
    updatedAt: Long = 1_000_000L,
) = Account(
    id = id,
    accountCode = accountCode,
    accountName = accountName,
    accountType = accountType,
    subCategory = subCategory,
    normalBalance = normalBalance,
    isSystemAccount = isSystemAccount,
    isActive = isActive,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun buildBudget(
    id: String = "budget-01",
    storeId: String = "store-01",
    periodStart: String = "2026-03-01",
    periodEnd: String = "2026-03-31",
    budgetAmount: Double = 10_000.0,
    spentAmount: Double = 0.0,
    name: String = "Q1 Budget",
    createdAt: Long = 1_000_000L,
    updatedAt: Long = 1_000_000L,
) = Budget(
    id = id,
    storeId = storeId,
    periodStart = periodStart,
    periodEnd = periodEnd,
    budgetAmount = budgetAmount,
    spentAmount = spentAmount,
    name = name,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

// ─────────────────────────────────────────────────────────────────────────────
// Inline Fakes
// ─────────────────────────────────────────────────────────────────────────────

private class AcctFakePeriodRepo : AccountingPeriodRepository {
    val periods = mutableListOf<AccountingPeriod>()

    override fun getAll(storeId: String): Flow<List<AccountingPeriod>> =
        flowOf(periods.toList())

    override suspend fun getById(id: String): Result<AccountingPeriod?> =
        Result.Success(periods.find { it.id == id })

    override suspend fun getPeriodForDate(storeId: String, date: String): Result<AccountingPeriod?> =
        Result.Success(
            periods.find { p ->
                p.status == PeriodStatus.OPEN && date >= p.startDate && date <= p.endDate
            },
        )

    override suspend fun getOpenPeriods(storeId: String): Result<List<AccountingPeriod>> =
        Result.Success(periods.filter { it.status == PeriodStatus.OPEN })

    override suspend fun create(period: AccountingPeriod): Result<Unit> {
        periods.add(period)
        return Result.Success(Unit)
    }

    override suspend fun closePeriod(id: String, updatedAt: Long): Result<Unit> {
        val idx = periods.indexOfFirst { it.id == id }
        if (idx < 0) return Result.Error(DatabaseException("Period not found: $id"))
        periods[idx] = periods[idx].copy(status = PeriodStatus.CLOSED, updatedAt = updatedAt)
        return Result.Success(Unit)
    }

    override suspend fun lockPeriod(id: String, lockedBy: String, lockedAt: Long): Result<Unit> {
        val idx = periods.indexOfFirst { it.id == id }
        if (idx < 0) return Result.Error(DatabaseException("Period not found: $id"))
        periods[idx] = periods[idx].copy(status = PeriodStatus.LOCKED, lockedAt = lockedAt, lockedBy = lockedBy, updatedAt = lockedAt)
        return Result.Success(Unit)
    }

    override suspend fun reopenPeriod(id: String, updatedAt: Long): Result<Unit> {
        val idx = periods.indexOfFirst { it.id == id }
        if (idx < 0) return Result.Error(DatabaseException("Period not found: $id"))
        periods[idx] = periods[idx].copy(status = PeriodStatus.OPEN, updatedAt = updatedAt)
        return Result.Success(Unit)
    }
}

private class AcctFakeAccountRepo : AccountRepository {
    val accounts = mutableListOf<Account>()
    private val _flow = MutableStateFlow<List<Account>>(emptyList())

    override fun getAll(storeId: String): Flow<List<Account>> = _flow
    override fun getByType(storeId: String, accountType: AccountType): Flow<List<Account>> =
        flowOf(accounts.filter { it.accountType == accountType })

    override suspend fun getById(id: String): Result<Account?> =
        Result.Success(accounts.find { it.id == id })

    override suspend fun getByCode(storeId: String, accountCode: String): Result<Account?> =
        Result.Success(accounts.find { it.accountCode == accountCode })

    override suspend fun getBalance(accountId: String, periodId: String): Result<AccountBalance?> =
        Result.Success(null)

    override fun getAllBalances(storeId: String, periodId: String): Flow<List<AccountBalance>> =
        flowOf(emptyList())

    override suspend fun create(account: Account): Result<Unit> {
        accounts.add(account)
        _flow.value = accounts.toList()
        return Result.Success(Unit)
    }

    override suspend fun update(account: Account): Result<Unit> {
        val idx = accounts.indexOfFirst { it.id == account.id }
        if (idx < 0) return Result.Error(DatabaseException("Account not found: ${account.id}"))
        accounts[idx] = account
        _flow.value = accounts.toList()
        return Result.Success(Unit)
    }

    override suspend fun deactivate(id: String, updatedAt: Long): Result<Unit> {
        val idx = accounts.indexOfFirst { it.id == id }
        if (idx < 0) return Result.Error(DatabaseException("Account not found: $id"))
        accounts[idx] = accounts[idx].copy(isActive = false, updatedAt = updatedAt)
        _flow.value = accounts.toList()
        return Result.Success(Unit)
    }

    override suspend fun isAccountCodeTaken(storeId: String, code: String, excludeId: String?): Result<Boolean> =
        Result.Success(accounts.any { it.accountCode == code && it.id != excludeId })

    override suspend fun seedDefaultAccounts(accounts: List<Account>): Result<Unit> {
        for (acc in accounts) {
            if (this.accounts.none { it.id == acc.id }) {
                this.accounts.add(acc)
            }
        }
        _flow.value = this.accounts.toList()
        return Result.Success(Unit)
    }
}

private class AcctFakeJournalRepo : JournalRepository {
    val entries = mutableListOf<JournalEntry>()

    override fun getEntriesByDateRange(storeId: String, fromDate: String, toDate: String): Flow<List<JournalEntry>> =
        flowOf(entries.filter { it.storeId == storeId && it.entryDate in fromDate..toDate })

    override fun getUnpostedEntries(storeId: String): Flow<List<JournalEntry>> =
        flowOf(entries.filter { it.storeId == storeId && !it.isPosted })

    override suspend fun getById(id: String): Result<JournalEntry?> =
        Result.Success(entries.find { it.id == id })

    override suspend fun getByReference(
        referenceType: JournalReferenceType,
        referenceId: String,
    ): Result<List<JournalEntry>> =
        Result.Success(entries.filter { it.referenceType == referenceType && it.referenceId == referenceId })

    override suspend fun getNextEntryNumber(storeId: String): Result<Int> =
        Result.Success((entries.maxOfOrNull { it.entryNumber } ?: 0) + 1)

    override suspend fun saveDraftEntry(entry: JournalEntry): Result<Unit> {
        entries.removeAll { it.id == entry.id }
        entries.add(entry)
        return Result.Success(Unit)
    }

    override suspend fun postEntry(entryId: String, postedAt: Long): Result<Unit> {
        val idx = entries.indexOfFirst { it.id == entryId }
        if (idx < 0) return Result.Error(DatabaseException("Entry not found: $entryId"))
        entries[idx] = entries[idx].copy(isPosted = true, postedAt = postedAt)
        return Result.Success(Unit)
    }

    override suspend fun unpostEntry(entryId: String): Result<Unit> {
        val idx = entries.indexOfFirst { it.id == entryId }
        if (idx < 0) return Result.Error(DatabaseException("Entry not found: $entryId"))
        entries[idx] = entries[idx].copy(isPosted = false, postedAt = null)
        return Result.Success(Unit)
    }

    override suspend fun deleteEntry(entryId: String): Result<Unit> {
        entries.removeAll { it.id == entryId }
        return Result.Success(Unit)
    }

    override suspend fun reverseEntry(
        originalEntryId: String,
        reversalDate: String,
        createdBy: String,
        now: Long,
    ): Result<JournalEntry> {
        val original = entries.find { it.id == originalEntryId }
            ?: return Result.Error(DatabaseException("Entry not found: $originalEntryId"))
        return Result.Success(original.copy(id = "reversal-$originalEntryId", isPosted = false, postedAt = null))
    }
}

private class AcctFakeStatementRepo : FinancialStatementRepository {
    override suspend fun getTrialBalance(storeId: String, asOfDate: String): Result<FinancialStatement.TrialBalance> =
        Result.Success(
            FinancialStatement.TrialBalance(
                asOfDate = asOfDate,
                lines = emptyList(),
                totalDebits = 0.0,
                totalCredits = 0.0,
                isBalanced = true,
            ),
        )

    override suspend fun getProfitAndLoss(storeId: String, fromDate: String, toDate: String): Result<FinancialStatement.PAndL> =
        Result.Success(
            FinancialStatement.PAndL(
                dateFrom = fromDate,
                dateTo = toDate,
                revenueLines = emptyList(),
                cogsLines = emptyList(),
                expenseLines = emptyList(),
                totalRevenue = 0.0,
                totalCogs = 0.0,
                grossProfit = 0.0,
                totalExpenses = 0.0,
                netProfit = 0.0,
                grossMarginPct = 0.0,
            ),
        )

    override suspend fun getBalanceSheet(storeId: String, asOfDate: String): Result<FinancialStatement.BalanceSheet> =
        Result.Success(
            FinancialStatement.BalanceSheet(
                asOfDate = asOfDate,
                assetLines = emptyList(),
                liabilityLines = emptyList(),
                equityLines = emptyList(),
                totalAssets = 0.0,
                totalLiabilities = 0.0,
                totalEquity = 0.0,
                retainedEarnings = 0.0,
            ),
        )

    override suspend fun getGeneralLedger(
        storeId: String,
        accountId: String,
        fromDate: String,
        toDate: String,
    ): Result<List<GeneralLedgerEntry>> = Result.Success(emptyList())

    override suspend fun upsertBalance(balance: AccountBalance): Result<Unit> = Result.Success(Unit)

    override suspend fun getCashFlowStatement(
        storeId: String,
        fromDate: String,
        toDate: String,
    ): Result<FinancialStatement.CashFlow> = Result.Success(
        FinancialStatement.CashFlow(
            dateFrom = fromDate,
            dateTo = toDate,
            operatingLines = emptyList(),
            investingLines = emptyList(),
            financingLines = emptyList(),
            netOperating = 0.0,
            netInvesting = 0.0,
            netFinancing = 0.0,
            netChange = 0.0,
            openingCash = 0.0,
            closingCash = 0.0,
        ),
    )

    override suspend fun rebuildAllBalances(storeId: String, periodId: String): Result<Unit> =
        Result.Success(Unit)
}

private class FakeAccountingRepository : AccountingRepository {
    val summaryResult: List<AccountSummary> = emptyList()

    override suspend fun getByStoreAndPeriod(storeId: String, fiscalPeriod: String): Result<List<AccountingEntry>> =
        Result.Success(emptyList())

    override suspend fun getByAccountAndPeriod(
        storeId: String,
        accountCode: String,
        fiscalPeriod: String,
    ): Result<List<AccountingEntry>> = Result.Success(emptyList())

    override suspend fun getByReference(
        referenceType: AccountingReferenceType,
        referenceId: String,
    ): Result<List<AccountingEntry>> = Result.Success(emptyList())

    override suspend fun getSummaryForPeriodRange(
        storeId: String,
        fromPeriod: String,
        toPeriod: String,
    ): Result<List<AccountSummary>> = Result.Success(summaryResult)

    override suspend fun insertEntries(entries: List<AccountingEntry>): Result<Unit> =
        Result.Success(Unit)
}

private class FakeBudgetRepository : BudgetRepository {
    val budgets = mutableListOf<Budget>()
    var shouldFailGetById: Boolean = false

    override suspend fun getById(id: String): Result<Budget> {
        if (shouldFailGetById) return Result.Error(DatabaseException("Budget not found: $id"))
        return budgets.find { it.id == id }
            ?.let { Result.Success(it) }
            ?: Result.Error(DatabaseException("Budget not found: $id"))
    }

    override fun getByStore(storeId: String): Flow<List<Budget>> =
        flowOf(budgets.filter { it.storeId == storeId })

    override suspend fun getByStoreAndPeriod(storeId: String, date: String): Result<List<Budget>> =
        Result.Success(budgets.filter { it.storeId == storeId && date >= it.periodStart && date <= it.periodEnd })

    override suspend fun insert(budget: Budget): Result<Unit> {
        budgets.add(budget)
        return Result.Success(Unit)
    }

    override suspend fun updateSpent(id: String, spentAmount: Double): Result<Unit> {
        val idx = budgets.indexOfFirst { it.id == id }
        if (idx < 0) return Result.Error(DatabaseException("Budget not found: $id"))
        budgets[idx] = budgets[idx].copy(spentAmount = spentAmount)
        return Result.Success(Unit)
    }

    override suspend fun delete(id: String): Result<Unit> {
        budgets.removeAll { it.id == id }
        return Result.Success(Unit)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// GetAccountsUseCase
// ─────────────────────────────────────────────────────────────────────────────

class GetAccountsUseCaseTest {

    @Test
    fun `execute_returnsFlowOfAccounts`() = runTest {
        val repo = AcctFakeAccountRepo()
        repo.create(buildAccount(id = "a1"))
        repo.create(buildAccount(id = "a2"))

        GetAccountsUseCase(repo).execute("store-01").test {
            assertEquals(2, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `executeByType_filtersCorrectly`() = runTest {
        val repo = AcctFakeAccountRepo()
        repo.accounts.add(buildAccount(id = "a1", accountType = AccountType.ASSET))
        repo.accounts.add(buildAccount(id = "a2", accountType = AccountType.LIABILITY))

        GetAccountsUseCase(repo).executeByType("store-01", AccountType.ASSET).test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("a1", list.first().id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `executeById_returnsAccountOrNull`() = runTest {
        val repo = AcctFakeAccountRepo()
        repo.accounts.add(buildAccount(id = "a1"))

        val found = GetAccountsUseCase(repo).executeById("a1")
        assertIs<Result.Success<*>>(found)
        assertEquals("a1", (found as Result.Success).data?.id)

        val notFound = GetAccountsUseCase(repo).executeById("unknown")
        assertIs<Result.Success<*>>(notFound)
        assertEquals(null, (notFound as Result.Success).data)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// GetJournalEntriesUseCase
// ─────────────────────────────────────────────────────────────────────────────

class GetJournalEntriesUseCaseTest {

    private fun buildJournalEntry(id: String, storeId: String, entryDate: String) = JournalEntry(
        id = id,
        storeId = storeId,
        entryDate = entryDate,
        entryTime = 1_000_000L,
        entryNumber = 1,
        description = "Test",
        referenceType = JournalReferenceType.MANUAL,
        isPosted = false,
        lines = emptyList(),
        createdBy = "user-01",
        createdAt = 1_000_000L,
        updatedAt = 1_000_000L,
    )

    @Test
    fun `execute_returnsEntriesInDateRange`() = runTest {
        val repo = AcctFakeJournalRepo()
        repo.entries.add(buildJournalEntry("e1", "store-01", "2026-03-10"))
        repo.entries.add(buildJournalEntry("e2", "store-01", "2026-04-01"))

        GetJournalEntriesUseCase(repo).execute("store-01", "2026-03-01", "2026-03-31").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("e1", list.first().id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `executeById_returnsEntryOrNull`() = runTest {
        val repo = AcctFakeJournalRepo()
        repo.entries.add(buildJournalEntry("e1", "store-01", "2026-03-10"))

        val found = GetJournalEntriesUseCase(repo).executeById("e1")
        assertIs<Result.Success<*>>(found)
        assertNotNull((found as Result.Success).data)

        val notFound = GetJournalEntriesUseCase(repo).executeById("unknown")
        assertIs<Result.Success<*>>(notFound)
        assertEquals(null, (notFound as Result.Success).data)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// GetAccountingPeriodsUseCase
// ─────────────────────────────────────────────────────────────────────────────

class GetAccountingPeriodsUseCaseTest {

    @Test
    fun `execute_returnsAllPeriods`() = runTest {
        val repo = AcctFakePeriodRepo()
        repo.periods.add(buildAccountingPeriod(id = "p1"))
        repo.periods.add(buildAccountingPeriod(id = "p2"))

        GetAccountingPeriodsUseCase(repo).execute("store-01").test {
            assertEquals(2, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `executeForDate_returnsOpenPeriodContainingDate`() = runTest {
        val repo = AcctFakePeriodRepo()
        repo.periods.add(buildAccountingPeriod(id = "p1", startDate = "2026-03-01", endDate = "2026-03-31", status = PeriodStatus.OPEN))
        repo.periods.add(buildAccountingPeriod(id = "p2", startDate = "2026-04-01", endDate = "2026-04-30", status = PeriodStatus.OPEN))

        val result = GetAccountingPeriodsUseCase(repo).executeForDate("store-01", "2026-03-15")
        assertIs<Result.Success<*>>(result)
        assertEquals("p1", (result as Result.Success).data?.id)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// GetPeriodSummaryUseCase
// ─────────────────────────────────────────────────────────────────────────────

class GetPeriodSummaryUseCaseTest {

    @Test
    fun `delegatesTo_accountingRepository_getSummaryForPeriodRange`() = runTest {
        val result = GetPeriodSummaryUseCase(FakeAccountingRepository())(
            storeId = "store-01",
            fromPeriod = "2026-01",
            toPeriod = "2026-03",
        )
        assertIs<Result.Success<*>>(result)
        assertTrue((result as Result.Success).data.isEmpty())
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// GetTrialBalanceUseCase
// ─────────────────────────────────────────────────────────────────────────────

class GetTrialBalanceUseCaseTest {

    @Test
    fun `execute_delegatesTo_statementRepository`() = runTest {
        val result = GetTrialBalanceUseCase(AcctFakeStatementRepo())
            .execute("store-01", "2026-03-31")
        assertIs<Result.Success<*>>(result)
        val tb = (result as Result.Success).data
        assertEquals("2026-03-31", tb.asOfDate)
        assertTrue(tb.isBalanced)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// GetGeneralLedgerUseCase
// ─────────────────────────────────────────────────────────────────────────────

class GetGeneralLedgerUseCaseTest {

    @Test
    fun `execute_delegatesTo_statementRepository`() = runTest {
        val result = GetGeneralLedgerUseCase(AcctFakeStatementRepo())
            .execute("store-01", "acc-01", "2026-03-01", "2026-03-31")
        assertIs<Result.Success<*>>(result)
        assertTrue((result as Result.Success).data.isEmpty())
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// GetBalanceSheetUseCase
// ─────────────────────────────────────────────────────────────────────────────

class GetBalanceSheetUseCaseTest {

    @Test
    fun `execute_delegatesTo_statementRepository`() = runTest {
        val result = GetBalanceSheetUseCase(AcctFakeStatementRepo())
            .execute("store-01", "2026-03-31")
        assertIs<Result.Success<*>>(result)
        val bs = (result as Result.Success).data
        assertEquals("2026-03-31", bs.asOfDate)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// GetProfitAndLossUseCase
// ─────────────────────────────────────────────────────────────────────────────

class GetProfitAndLossUseCaseTest {

    @Test
    fun `execute_delegatesTo_statementRepository`() = runTest {
        val result = GetProfitAndLossUseCase(AcctFakeStatementRepo())
            .execute("store-01", "2026-03-01", "2026-03-31")
        assertIs<Result.Success<*>>(result)
        val pl = (result as Result.Success).data
        assertEquals("2026-03-01", pl.dateFrom)
        assertEquals("2026-03-31", pl.dateTo)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// GetAccountBalancesUseCase
// ─────────────────────────────────────────────────────────────────────────────

class GetAccountBalancesUseCaseTest {

    @Test
    fun `execute_returnsFlowOfBalances`() = runTest {
        GetAccountBalancesUseCase(AcctFakeAccountRepo()).execute("store-01", "period-01").test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `executeForAccount_returnsNullWhenNotFound`() = runTest {
        val result = GetAccountBalancesUseCase(AcctFakeAccountRepo())
            .executeForAccount("acc-01", "period-01")
        assertIs<Result.Success<*>>(result)
        assertEquals(null, (result as Result.Success).data)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CreateAccountingPeriodUseCase
// ─────────────────────────────────────────────────────────────────────────────

class CreateAccountingPeriodUseCaseTest {

    @Test
    fun `success_createsNewPeriod`() = runTest {
        val repo = AcctFakePeriodRepo()
        val period = buildAccountingPeriod(id = "p1", startDate = "2026-03-01", endDate = "2026-03-31")

        val result = CreateAccountingPeriodUseCase(repo).execute(period, "store-01")
        assertIs<Result.Success<*>>(result)
        assertEquals(1, repo.periods.size)
    }

    @Test
    fun `startDateAfterEndDate_returnsValidationError`() = runTest {
        val period = buildAccountingPeriod(startDate = "2026-03-31", endDate = "2026-03-01")
        val result = CreateAccountingPeriodUseCase(AcctFakePeriodRepo()).execute(period, "store-01")
        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("startDate", ex.field)
        assertEquals("INVALID_DATE_RANGE", ex.rule)
    }

    @Test
    fun `overlappingOpenPeriod_returnsValidationError`() = runTest {
        val repo = AcctFakePeriodRepo()
        repo.periods.add(buildAccountingPeriod(id = "existing", startDate = "2026-03-01", endDate = "2026-03-31", status = PeriodStatus.OPEN))

        val overlapping = buildAccountingPeriod(id = "new", startDate = "2026-03-15", endDate = "2026-04-15")
        val result = CreateAccountingPeriodUseCase(repo).execute(overlapping, "store-01")
        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("startDate", ex.field)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DeactivateAccountUseCase
// ─────────────────────────────────────────────────────────────────────────────

class DeactivateAccountUseCaseTest {

    @Test
    fun `success_deactivatesAccount`() = runTest {
        val repo = AcctFakeAccountRepo()
        repo.accounts.add(buildAccount(id = "a1", isSystemAccount = false))

        val result = DeactivateAccountUseCase(repo).execute("a1", 2_000_000L)
        assertIs<Result.Success<*>>(result)
        assertIs<Boolean>(false == repo.accounts.first().isActive)
    }

    @Test
    fun `notFound_returnsValidationError`() = runTest {
        val result = DeactivateAccountUseCase(AcctFakeAccountRepo()).execute("unknown", 2_000_000L)
        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("accountId", ex.field)
        assertEquals("NOT_FOUND", ex.rule)
    }

    @Test
    fun `systemAccount_returnsSystemAccountError`() = runTest {
        val repo = AcctFakeAccountRepo()
        repo.accounts.add(buildAccount(id = "a1", isSystemAccount = true))

        val result = DeactivateAccountUseCase(repo).execute("a1", 2_000_000L)
        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("isSystemAccount", ex.field)
        assertEquals("SYSTEM_ACCOUNT", ex.rule)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LockAccountingPeriodUseCase
// ─────────────────────────────────────────────────────────────────────────────

class LockAccountingPeriodUseCaseTest {

    @Test
    fun `success_locksClosedPeriod`() = runTest {
        val repo = AcctFakePeriodRepo()
        repo.periods.add(buildAccountingPeriod(id = "p1", status = PeriodStatus.CLOSED))

        val result = LockAccountingPeriodUseCase(repo).execute("p1", "admin-01", 2_000_000L)
        assertIs<Result.Success<*>>(result)
        assertEquals(PeriodStatus.LOCKED, repo.periods.first().status)
    }

    @Test
    fun `notFound_returnsValidationError`() = runTest {
        val result = LockAccountingPeriodUseCase(AcctFakePeriodRepo()).execute("unknown", "admin", 2_000_000L)
        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("periodId", ex.field)
        assertEquals("NOT_FOUND", ex.rule)
    }

    @Test
    fun `openPeriod_cannotBeLocked`() = runTest {
        val repo = AcctFakePeriodRepo()
        repo.periods.add(buildAccountingPeriod(id = "p1", status = PeriodStatus.OPEN))

        val result = LockAccountingPeriodUseCase(repo).execute("p1", "admin-01", 2_000_000L)
        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("status", ex.field)
        assertEquals("INVALID_STATUS_TRANSITION", ex.rule)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ReopenAccountingPeriodUseCase
// ─────────────────────────────────────────────────────────────────────────────

class ReopenAccountingPeriodUseCaseTest {

    @Test
    fun `success_reopensClosedPeriod`() = runTest {
        val repo = AcctFakePeriodRepo()
        repo.periods.add(buildAccountingPeriod(id = "p1", status = PeriodStatus.CLOSED))

        val result = ReopenAccountingPeriodUseCase(repo).execute("p1", 2_000_000L)
        assertIs<Result.Success<*>>(result)
        assertEquals(PeriodStatus.OPEN, repo.periods.first().status)
    }

    @Test
    fun `lockedPeriod_cannotBeReopened`() = runTest {
        val repo = AcctFakePeriodRepo()
        repo.periods.add(buildAccountingPeriod(id = "p1", status = PeriodStatus.LOCKED))

        val result = ReopenAccountingPeriodUseCase(repo).execute("p1", 2_000_000L)
        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("PERIOD_LOCKED", ex.rule)
    }

    @Test
    fun `alreadyOpenPeriod_returnsError`() = runTest {
        val repo = AcctFakePeriodRepo()
        repo.periods.add(buildAccountingPeriod(id = "p1", status = PeriodStatus.OPEN))

        val result = ReopenAccountingPeriodUseCase(repo).execute("p1", 2_000_000L)
        assertIs<Result.Error>(result)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SeedDefaultChartOfAccountsUseCase
// ─────────────────────────────────────────────────────────────────────────────

class SeedDefaultChartOfAccountsUseCaseTest {

    @Test
    fun `execute_seedsAccountsIntoRepository`() = runTest {
        val repo = AcctFakeAccountRepo()
        val result = SeedDefaultChartOfAccountsUseCase(repo).execute("store-01", 1_000_000L)
        assertIs<Result.Success<*>>(result)
        assertTrue(repo.accounts.isNotEmpty())
    }

    @Test
    fun `execute_isIdempotent_noduplicates`() = runTest {
        val repo = AcctFakeAccountRepo()
        SeedDefaultChartOfAccountsUseCase(repo).execute("store-01", 1_000_000L)
        val countAfterFirst = repo.accounts.size
        SeedDefaultChartOfAccountsUseCase(repo).execute("store-01", 2_000_000L)
        assertEquals(countAfterFirst, repo.accounts.size)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TrackBudgetSpendingUseCase
// ─────────────────────────────────────────────────────────────────────────────

class TrackBudgetSpendingUseCaseTest {

    @Test
    fun `success_addsToBudgetSpentAmount`() = runTest {
        val repo = FakeBudgetRepository()
        repo.budgets.add(buildBudget(id = "b1", spentAmount = 100.0))

        val result = TrackBudgetSpendingUseCase(repo)("b1", 500.0)
        assertIs<Result.Success<*>>(result)
        assertEquals(600.0, repo.budgets.first().spentAmount, 0.001)
    }

    @Test
    fun `zeroAmount_returnsPositiveValidationError`() = runTest {
        val result = TrackBudgetSpendingUseCase(FakeBudgetRepository())("b1", 0.0)
        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("amount", ex.field)
        assertEquals("POSITIVE", ex.rule)
    }

    @Test
    fun `negativeAmount_returnsPositiveValidationError`() = runTest {
        val result = TrackBudgetSpendingUseCase(FakeBudgetRepository())("b1", -50.0)
        assertIs<Result.Error>(result)
        assertEquals("POSITIVE", ((result as Result.Error).exception as ValidationException).rule)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// GenerateProfitAndLossUseCase
// ─────────────────────────────────────────────────────────────────────────────

class GenerateProfitAndLossUseCaseTest {

    private fun makeUseCase() = GenerateProfitAndLossUseCase(
        expenseRepository = FakeExpenseRepository(),
        generateSalesReport = GenerateSalesReportUseCase(FakeOrderRepository()),
    )

    @Test
    fun `periodStartAfterPeriodEnd_returnsDateRangeError`() = runTest {
        val result = makeUseCase()(
            storeId = "store-01",
            periodStart = 2_000_000_000L,
            periodEnd = 1_000_000_000L,
        )
        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("periodStart", ex.field)
        assertEquals("DATE_RANGE", ex.rule)
    }

    @Test
    fun `noOrders_noExpenses_returnsZeroProfit`() = runTest {
        val result = makeUseCase()(
            storeId = "store-01",
            periodStart = 1_000_000_000L,
            periodEnd = 2_000_000_000L,
        )
        assertIs<Result.Success<*>>(result)
        val report = (result as Result.Success).data
        assertEquals(0.0, report.totalRevenue, 0.001)
        assertEquals(0.0, report.netProfit, 0.001)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PostSaleJournalEntryUseCase
// ─────────────────────────────────────────────────────────────────────────────

class PostSaleJournalEntryUseCaseTest {

    private fun makeUseCase(
        journalRepo: AcctFakeJournalRepo = AcctFakeJournalRepo(),
        accountRepo: AcctFakeAccountRepo = AcctFakeAccountRepo(),
        periodRepo: AcctFakePeriodRepo = AcctFakePeriodRepo(),
    ) = PostSaleJournalEntryUseCase(journalRepo, accountRepo, periodRepo)

    @Test
    fun `noPeriodOpen_returnsPeriodNotOpenError`() = runTest {
        val result = makeUseCase().execute(
            storeId = "store-01",
            orderId = "order-01",
            totalAmount = 100.0,
            subtotal = 90.0,
            taxAmount = 10.0,
            cashierId = "user-01",
            entryDate = "2026-03-15",
            now = 2_000_000L,
        )
        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("entryDate", ex.field)
        assertEquals("PERIOD_NOT_OPEN", ex.rule)
    }

    @Test
    fun `openPeriod_missingCashAccount_returnsAccountError`() = runTest {
        val periodRepo = AcctFakePeriodRepo()
        periodRepo.periods.add(buildAccountingPeriod(startDate = "2026-03-01", endDate = "2026-03-31", status = PeriodStatus.OPEN))

        val result = makeUseCase(periodRepo = periodRepo).execute(
            storeId = "store-01",
            orderId = "order-01",
            totalAmount = 100.0,
            subtotal = 90.0,
            taxAmount = 10.0,
            cashierId = "user-01",
            entryDate = "2026-03-15",
            now = 2_000_000L,
        )
        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("accountCode", ex.field)
    }

    @Test
    fun `allAccountsPresent_postsEntry`() = runTest {
        val periodRepo = AcctFakePeriodRepo()
        periodRepo.periods.add(buildAccountingPeriod(startDate = "2026-03-01", endDate = "2026-03-31", status = PeriodStatus.OPEN))

        val accountRepo = AcctFakeAccountRepo()
        accountRepo.accounts.add(buildAccount(id = "cash", accountCode = StandardAccountCodes.CASH, accountType = AccountType.ASSET, normalBalance = NormalBalance.DEBIT))
        accountRepo.accounts.add(buildAccount(id = "rev", accountCode = StandardAccountCodes.SALES_REVENUE, accountType = AccountType.INCOME, normalBalance = NormalBalance.CREDIT))
        accountRepo.accounts.add(buildAccount(id = "tax", accountCode = StandardAccountCodes.SALES_TAX_PAYABLE, accountType = AccountType.LIABILITY, normalBalance = NormalBalance.CREDIT))

        val journalRepo = AcctFakeJournalRepo()
        val result = makeUseCase(journalRepo, accountRepo, periodRepo).execute(
            storeId = "store-01",
            orderId = "order-01",
            totalAmount = 110.0,
            subtotal = 100.0,
            taxAmount = 10.0,
            cashierId = "user-01",
            entryDate = "2026-03-15",
            now = 2_000_000L,
        )
        assertIs<Result.Success<*>>(result)
        assertTrue(journalRepo.entries.isNotEmpty())
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PostExpenseJournalEntryUseCase
// ─────────────────────────────────────────────────────────────────────────────

class PostExpenseJournalEntryUseCaseTest {

    @Test
    fun `noPeriodOpen_returnsPeriodNotOpenError`() = runTest {
        val result = PostExpenseJournalEntryUseCase(
            AcctFakeJournalRepo(),
            AcctFakeAccountRepo(),
            AcctFakePeriodRepo(),
        ).execute(
            storeId = "store-01",
            expenseId = "exp-01",
            amount = 200.0,
            expenseAccountCode = "6010",
            paymentAccountCode = "1010",
            createdBy = "user-01",
            description = "Office supplies",
            entryDate = "2026-03-15",
            now = 2_000_000L,
        )
        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("PERIOD_NOT_OPEN", ex.rule)
    }

    @Test
    fun `openPeriod_withAccounts_postsEntry`() = runTest {
        val periodRepo = AcctFakePeriodRepo()
        periodRepo.periods.add(buildAccountingPeriod(startDate = "2026-03-01", endDate = "2026-03-31", status = PeriodStatus.OPEN))

        val accountRepo = AcctFakeAccountRepo()
        accountRepo.accounts.add(buildAccount(id = "exp", accountCode = "6010", accountType = AccountType.EXPENSE, normalBalance = NormalBalance.DEBIT))
        accountRepo.accounts.add(buildAccount(id = "cash", accountCode = "1010", accountType = AccountType.ASSET, normalBalance = NormalBalance.DEBIT))

        val journalRepo = AcctFakeJournalRepo()
        val result = PostExpenseJournalEntryUseCase(journalRepo, accountRepo, periodRepo).execute(
            storeId = "store-01",
            expenseId = "exp-01",
            amount = 200.0,
            expenseAccountCode = "6010",
            paymentAccountCode = "1010",
            createdBy = "user-01",
            description = "Office supplies",
            entryDate = "2026-03-15",
            now = 2_000_000L,
        )
        assertIs<Result.Success<*>>(result)
        assertTrue(journalRepo.entries.isNotEmpty())
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PostInventoryAdjustmentJournalEntryUseCase
// ─────────────────────────────────────────────────────────────────────────────

class PostInventoryAdjustmentJournalEntryUseCaseTest {

    @Test
    fun `zeroAdjustmentValue_returnsZeroAmountError`() = runTest {
        val result = PostInventoryAdjustmentJournalEntryUseCase(
            AcctFakeJournalRepo(),
            AcctFakeAccountRepo(),
            AcctFakePeriodRepo(),
        ).execute(
            storeId = "store-01",
            adjustmentId = "adj-01",
            adjustmentValue = 0.0,
            createdBy = "user-01",
            description = "Count correction",
            entryDate = "2026-03-15",
            now = 2_000_000L,
        )
        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("adjustmentValue", ex.field)
        assertEquals("ZERO_AMOUNT", ex.rule)
    }

    @Test
    fun `positiveAdjustment_withOpenPeriodAndAccounts_postsEntry`() = runTest {
        val periodRepo = AcctFakePeriodRepo()
        periodRepo.periods.add(buildAccountingPeriod(startDate = "2026-03-01", endDate = "2026-03-31", status = PeriodStatus.OPEN))

        val accountRepo = AcctFakeAccountRepo()
        accountRepo.accounts.add(buildAccount(id = "inv", accountCode = StandardAccountCodes.INVENTORY, accountType = AccountType.ASSET, normalBalance = NormalBalance.DEBIT))
        accountRepo.accounts.add(buildAccount(id = "cogs", accountCode = StandardAccountCodes.COGS, accountType = AccountType.EXPENSE, normalBalance = NormalBalance.DEBIT))

        val journalRepo = AcctFakeJournalRepo()
        val result = PostInventoryAdjustmentJournalEntryUseCase(journalRepo, accountRepo, periodRepo).execute(
            storeId = "store-01",
            adjustmentId = "adj-01",
            adjustmentValue = 500.0,
            createdBy = "user-01",
            description = "Receiving correction",
            entryDate = "2026-03-15",
            now = 2_000_000L,
        )
        assertIs<Result.Success<*>>(result)
        assertTrue(journalRepo.entries.isNotEmpty())
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PostPayrollJournalEntryUseCase
// ─────────────────────────────────────────────────────────────────────────────

class PostPayrollJournalEntryUseCaseTest {

    @Test
    fun `noPeriodOpen_returnsPeriodNotOpenError`() = runTest {
        val result = PostPayrollJournalEntryUseCase(
            AcctFakeJournalRepo(),
            AcctFakeAccountRepo(),
            AcctFakePeriodRepo(),
        ).execute(
            storeId = "store-01",
            payrollId = "pay-01",
            grossPay = 1000.0,
            netPay = 900.0,
            deductions = 100.0,
            createdBy = "admin-01",
            entryDate = "2026-03-31",
            now = 2_000_000L,
        )
        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("PERIOD_NOT_OPEN", ex.rule)
    }

    @Test
    fun `openPeriod_withRequiredAccounts_postsPayrollEntry`() = runTest {
        val periodRepo = AcctFakePeriodRepo()
        periodRepo.periods.add(buildAccountingPeriod(startDate = "2026-03-01", endDate = "2026-03-31", status = PeriodStatus.OPEN))

        val accountRepo = AcctFakeAccountRepo()
        accountRepo.accounts.add(buildAccount(id = "sal", accountCode = "6010", accountType = AccountType.EXPENSE, normalBalance = NormalBalance.DEBIT))
        accountRepo.accounts.add(buildAccount(id = "cash", accountCode = "1010", accountType = AccountType.ASSET, normalBalance = NormalBalance.DEBIT))
        accountRepo.accounts.add(buildAccount(id = "liab", accountCode = "2200", accountType = AccountType.LIABILITY, normalBalance = NormalBalance.CREDIT))

        val journalRepo = AcctFakeJournalRepo()
        val result = PostPayrollJournalEntryUseCase(journalRepo, accountRepo, periodRepo).execute(
            storeId = "store-01",
            payrollId = "pay-01",
            grossPay = 1000.0,
            netPay = 900.0,
            deductions = 100.0,
            createdBy = "admin-01",
            entryDate = "2026-03-31",
            now = 2_000_000L,
        )
        assertIs<Result.Success<*>>(result)
        assertTrue(journalRepo.entries.isNotEmpty())
    }
}
