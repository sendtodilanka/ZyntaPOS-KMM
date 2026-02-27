package com.zyntasolutions.zyntapos.domain.usecase.accounting

import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.Account
import com.zyntasolutions.zyntapos.domain.model.AccountBalance
import com.zyntasolutions.zyntapos.domain.model.AccountType
import com.zyntasolutions.zyntapos.domain.model.AccountingPeriod
import com.zyntasolutions.zyntapos.domain.model.FinancialStatement
import com.zyntasolutions.zyntapos.domain.model.GeneralLedgerEntry
import com.zyntasolutions.zyntapos.domain.model.JournalEntry
import com.zyntasolutions.zyntapos.domain.model.JournalEntryLine
import com.zyntasolutions.zyntapos.domain.model.JournalReferenceType
import com.zyntasolutions.zyntapos.domain.model.NormalBalance
import com.zyntasolutions.zyntapos.domain.model.PeriodStatus
import com.zyntasolutions.zyntapos.domain.repository.AccountRepository
import com.zyntasolutions.zyntapos.domain.repository.AccountingPeriodRepository
import com.zyntasolutions.zyntapos.domain.repository.FinancialStatementRepository
import com.zyntasolutions.zyntapos.domain.repository.JournalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

// ─────────────────────────────────────────────────────────────────────────────
// Fixtures
// ─────────────────────────────────────────────────────────────────────────────

private const val STORE_ID = "store-01"
private const val NOW = 1_700_000_000_000L

/** Builds a test [JournalEntry] with sensible defaults. */
private fun buildJournalEntry(
    id: String = "je-01",
    entryNumber: Int = 1,
    storeId: String = STORE_ID,
    entryDate: String = "2026-02-01",
    isPosted: Boolean = false,
    lines: List<JournalEntryLine> = emptyList(),
) = JournalEntry(
    id = id,
    entryNumber = entryNumber,
    storeId = storeId,
    entryDate = entryDate,
    entryTime = NOW,
    description = "Test journal entry",
    referenceType = JournalReferenceType.MANUAL,
    referenceId = null,
    isPosted = isPosted,
    createdBy = "user-01",
    createdAt = NOW,
    updatedAt = NOW,
    postedAt = if (isPosted) NOW else null,
    lines = lines,
)

/** Builds a balanced pair of debit/credit [JournalEntryLine] records. */
private fun buildBalancedLines(
    entryId: String = "je-01",
    amount: Double = 100.0,
    debitAccountId: String = "acc-cash",
    creditAccountId: String = "acc-revenue",
): List<JournalEntryLine> = listOf(
    JournalEntryLine(
        id = "line-dr",
        journalEntryId = entryId,
        accountId = debitAccountId,
        debitAmount = amount,
        creditAmount = 0.0,
        lineOrder = 1,
        createdAt = NOW,
    ),
    JournalEntryLine(
        id = "line-cr",
        journalEntryId = entryId,
        accountId = creditAccountId,
        debitAmount = 0.0,
        creditAmount = amount,
        lineOrder = 2,
        createdAt = NOW,
    ),
)

/** Builds an [Account] with sensible defaults. */
private fun buildAccount(
    id: String = "acc-01",
    accountCode: String = "1010",
    accountName: String = "Cash",
    isSystemAccount: Boolean = false,
    isActive: Boolean = true,
    accountType: AccountType = AccountType.ASSET,
) = Account(
    id = id,
    accountCode = accountCode,
    accountName = accountName,
    accountType = accountType,
    subCategory = "Current Assets",
    normalBalance = NormalBalance.DEBIT,
    isSystemAccount = isSystemAccount,
    isActive = isActive,
    createdAt = NOW,
    updatedAt = NOW,
)

/** Builds an [AccountingPeriod] with sensible defaults. */
private fun buildPeriod(
    id: String = "period-01",
    periodName: String = "February 2026",
    status: PeriodStatus = PeriodStatus.OPEN,
) = AccountingPeriod(
    id = id,
    periodName = periodName,
    startDate = "2026-02-01",
    endDate = "2026-02-28",
    status = status,
    fiscalYearStart = "2026-01-01",
    createdAt = NOW,
    updatedAt = NOW,
    lockedAt = if (status == PeriodStatus.LOCKED) NOW else null,
    lockedBy = if (status == PeriodStatus.LOCKED) "user-01" else null,
)

// ─────────────────────────────────────────────────────────────────────────────
// Fake JournalRepository
// ─────────────────────────────────────────────────────────────────────────────

private class FakeJournalRepository : JournalRepository {
    val entries = mutableListOf<JournalEntry>()

    override fun getEntriesByDateRange(
        storeId: String,
        fromDate: String,
        toDate: String,
    ): Flow<List<JournalEntry>> = MutableStateFlow(
        entries.filter { it.storeId == storeId && it.entryDate in fromDate..toDate },
    )

    override fun getUnpostedEntries(storeId: String): Flow<List<JournalEntry>> =
        MutableStateFlow(entries.filter { it.storeId == storeId && !it.isPosted })

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
        val reversed = original.copy(
            id = "reversal-$originalEntryId",
            isPosted = false,
            postedAt = null,
            entryDate = reversalDate,
            createdAt = now,
            updatedAt = now,
            lines = original.lines.map { line ->
                line.copy(debitAmount = line.creditAmount, creditAmount = line.debitAmount)
            },
        )
        return Result.Success(reversed)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Fake AccountingPeriodRepository
// ─────────────────────────────────────────────────────────────────────────────

private class FakeAccountingPeriodRepository : AccountingPeriodRepository {
    val periods = mutableListOf<AccountingPeriod>()

    override fun getAll(storeId: String): Flow<List<AccountingPeriod>> =
        MutableStateFlow(periods.filter { true })

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
        periods[idx] = periods[idx].copy(
            status = PeriodStatus.LOCKED,
            lockedAt = lockedAt,
            lockedBy = lockedBy,
            updatedAt = lockedAt,
        )
        return Result.Success(Unit)
    }

    override suspend fun reopenPeriod(id: String, updatedAt: Long): Result<Unit> {
        val idx = periods.indexOfFirst { it.id == id }
        if (idx < 0) return Result.Error(DatabaseException("Period not found: $id"))
        periods[idx] = periods[idx].copy(status = PeriodStatus.OPEN, updatedAt = updatedAt)
        return Result.Success(Unit)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Fake AccountRepository
// ─────────────────────────────────────────────────────────────────────────────

private class FakeAccountRepository : AccountRepository {
    val accounts = mutableListOf<Account>()
    private val _flow = MutableStateFlow<List<Account>>(emptyList())

    override fun getAll(storeId: String): Flow<List<Account>> = _flow

    override fun getByType(storeId: String, accountType: AccountType): Flow<List<Account>> =
        MutableStateFlow(accounts.filter { it.accountType == accountType })

    override suspend fun getById(id: String): Result<Account?> =
        Result.Success(accounts.find { it.id == id })

    override suspend fun getByCode(storeId: String, accountCode: String): Result<Account?> =
        Result.Success(accounts.find { it.accountCode == accountCode })

    override suspend fun getBalance(accountId: String, periodId: String): Result<AccountBalance?> =
        Result.Success(null)

    override fun getAllBalances(storeId: String, periodId: String): Flow<List<AccountBalance>> =
        MutableStateFlow(emptyList())

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

    override suspend fun isAccountCodeTaken(
        storeId: String,
        code: String,
        excludeId: String?,
    ): Result<Boolean> {
        val taken = accounts.any { it.accountCode == code && it.id != excludeId }
        return Result.Success(taken)
    }

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

// ─────────────────────────────────────────────────────────────────────────────
// Fake FinancialStatementRepository (stub — just returns Success for rebuild)
// ─────────────────────────────────────────────────────────────────────────────

private class FakeFinancialStatementRepository : FinancialStatementRepository {
    var shouldFailRebuild: Boolean = false

    override suspend fun getTrialBalance(
        storeId: String,
        asOfDate: String,
    ): Result<FinancialStatement.TrialBalance> = Result.Success(
        FinancialStatement.TrialBalance(
            asOfDate = asOfDate,
            lines = emptyList(),
            totalDebits = 0.0,
            totalCredits = 0.0,
            isBalanced = true,
        ),
    )

    override suspend fun getProfitAndLoss(
        storeId: String,
        fromDate: String,
        toDate: String,
    ): Result<FinancialStatement.PAndL> = Result.Success(
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

    override suspend fun getBalanceSheet(
        storeId: String,
        asOfDate: String,
    ): Result<FinancialStatement.BalanceSheet> = Result.Success(
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

    override suspend fun rebuildAllBalances(storeId: String, periodId: String): Result<Unit> {
        if (shouldFailRebuild) return Result.Error(DatabaseException("Rebuild failed"))
        return Result.Success(Unit)
    }

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
}

// ─────────────────────────────────────────────────────────────────────────────
// PostJournalEntryUseCaseTest
// ─────────────────────────────────────────────────────────────────────────────

class PostJournalEntryUseCaseTest {

    private fun makeUseCase(
        journalRepo: FakeJournalRepository = FakeJournalRepository(),
        periodRepo: FakeAccountingPeriodRepository = FakeAccountingPeriodRepository(),
    ): Triple<PostJournalEntryUseCase, FakeJournalRepository, FakeAccountingPeriodRepository> {
        val useCase = PostJournalEntryUseCase(journalRepo, periodRepo)
        return Triple(useCase, journalRepo, periodRepo)
    }

    @Test
    fun test_post_valid_balanced_entry_succeeds() = runTest {
        val (useCase, journalRepo, periodRepo) = makeUseCase()

        val entry = buildJournalEntry(
            id = "je-01",
            entryDate = "2026-02-15",
            lines = buildBalancedLines(entryId = "je-01", amount = 100.0),
        )
        journalRepo.entries.add(entry)
        periodRepo.periods.add(buildPeriod(status = PeriodStatus.OPEN))

        val result = useCase.execute("je-01", NOW)

        assertIs<Result.Success<Unit>>(result)
        assertTrue(journalRepo.entries.first { it.id == "je-01" }.isPosted)
    }

    @Test
    fun test_post_already_posted_entry_returns_error() = runTest {
        val (useCase, journalRepo, periodRepo) = makeUseCase()

        val entry = buildJournalEntry(
            id = "je-02",
            entryDate = "2026-02-15",
            isPosted = true,
            lines = buildBalancedLines(entryId = "je-02"),
        )
        journalRepo.entries.add(entry)
        periodRepo.periods.add(buildPeriod(status = PeriodStatus.OPEN))

        val result = useCase.execute("je-02", NOW)

        assertIs<Result.Error>(result)
        assertIs<ValidationException>(result.exception)
        assertEquals("ALREADY_POSTED", (result.exception as ValidationException).rule)
    }

    @Test
    fun test_post_unbalanced_entry_returns_error() = runTest {
        val (useCase, journalRepo, periodRepo) = makeUseCase()

        // DR 100 / CR 90 — imbalanced
        val lines = listOf(
            JournalEntryLine(
                id = "line-dr",
                journalEntryId = "je-03",
                accountId = "acc-cash",
                debitAmount = 100.0,
                creditAmount = 0.0,
                lineOrder = 1,
                createdAt = NOW,
            ),
            JournalEntryLine(
                id = "line-cr",
                journalEntryId = "je-03",
                accountId = "acc-revenue",
                debitAmount = 0.0,
                creditAmount = 90.0,
                lineOrder = 2,
                createdAt = NOW,
            ),
        )
        val entry = buildJournalEntry(id = "je-03", entryDate = "2026-02-15", lines = lines)
        journalRepo.entries.add(entry)
        periodRepo.periods.add(buildPeriod(status = PeriodStatus.OPEN))

        val result = useCase.execute("je-03", NOW)

        assertIs<Result.Error>(result)
        assertIs<ValidationException>(result.exception)
        assertEquals("UNBALANCED", (result.exception as ValidationException).rule)
    }

    @Test
    fun test_post_empty_lines_returns_error() = runTest {
        val (useCase, journalRepo, periodRepo) = makeUseCase()

        val entry = buildJournalEntry(id = "je-04", entryDate = "2026-02-15", lines = emptyList())
        journalRepo.entries.add(entry)
        periodRepo.periods.add(buildPeriod(status = PeriodStatus.OPEN))

        val result = useCase.execute("je-04", NOW)

        assertIs<Result.Error>(result)
        assertIs<ValidationException>(result.exception)
        assertEquals("NO_LINES", (result.exception as ValidationException).rule)
    }

    @Test
    fun test_post_zero_line_returns_error() = runTest {
        val (useCase, journalRepo, periodRepo) = makeUseCase()

        // A line where BOTH debit and credit are 0
        val lines = listOf(
            JournalEntryLine(
                id = "line-zero",
                journalEntryId = "je-05",
                accountId = "acc-cash",
                debitAmount = 0.0,
                creditAmount = 0.0,
                lineOrder = 1,
                createdAt = NOW,
            ),
            JournalEntryLine(
                id = "line-cr",
                journalEntryId = "je-05",
                accountId = "acc-revenue",
                debitAmount = 0.0,
                creditAmount = 0.0,
                lineOrder = 2,
                createdAt = NOW,
            ),
        )
        val entry = buildJournalEntry(id = "je-05", entryDate = "2026-02-15", lines = lines)
        journalRepo.entries.add(entry)
        periodRepo.periods.add(buildPeriod(status = PeriodStatus.OPEN))

        val result = useCase.execute("je-05", NOW)

        assertIs<Result.Error>(result)
        assertIs<ValidationException>(result.exception)
        assertEquals("ZERO_LINE", (result.exception as ValidationException).rule)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SaveAccountUseCaseTest
// ─────────────────────────────────────────────────────────────────────────────

class SaveAccountUseCaseTest {

    private fun makeUseCase(
        accountRepo: FakeAccountRepository = FakeAccountRepository(),
    ): Pair<SaveAccountUseCase, FakeAccountRepository> {
        val useCase = SaveAccountUseCase(accountRepo)
        return useCase to accountRepo
    }

    @Test
    fun test_save_new_account_with_unique_code_succeeds() = runTest {
        val (useCase, repo) = makeUseCase()
        val account = buildAccount(id = "acc-new", accountCode = "1010", accountName = "Cash")

        val result = useCase.execute(account, STORE_ID)

        assertIs<Result.Success<Unit>>(result)
        assertEquals(1, repo.accounts.size)
        assertEquals("1010", repo.accounts.first().accountCode)
    }

    @Test
    fun test_save_account_with_duplicate_code_returns_error() = runTest {
        val (useCase, repo) = makeUseCase()
        // Pre-populate with an existing account using the same code
        val existing = buildAccount(id = "acc-existing", accountCode = "1010")
        repo.accounts.add(existing)

        // Attempt to save a new account with the same code
        val newAccount = buildAccount(id = "acc-new", accountCode = "1010")
        val result = useCase.execute(newAccount, STORE_ID)

        assertIs<Result.Error>(result)
        assertIs<ValidationException>(result.exception)
        assertEquals("DUPLICATE_CODE", (result.exception as ValidationException).rule)
    }

    @Test
    fun test_save_system_account_returns_error() = runTest {
        val (useCase, repo) = makeUseCase()
        // Pre-populate the repo with a system account
        val systemAccount = buildAccount(id = "sys-001", accountCode = "9999", isSystemAccount = true)
        repo.accounts.add(systemAccount)

        // Try to update the system account (same id, but different code)
        val updatedAccount = systemAccount.copy(accountName = "Renamed System Account")
        val result = useCase.execute(updatedAccount, STORE_ID)

        assertIs<Result.Error>(result)
        assertIs<ValidationException>(result.exception)
        assertEquals("SYSTEM_ACCOUNT", (result.exception as ValidationException).rule)
    }

    @Test
    fun test_save_account_with_blank_name_returns_error() = runTest {
        val (useCase, _) = makeUseCase()
        val account = buildAccount(id = "acc-blank", accountCode = "2020", accountName = "  ")

        val result = useCase.execute(account, STORE_ID)

        assertIs<Result.Error>(result)
        assertIs<ValidationException>(result.exception)
        assertEquals("accountName", (result.exception as ValidationException).field)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CloseAccountingPeriodUseCaseTest
// ─────────────────────────────────────────────────────────────────────────────

class CloseAccountingPeriodUseCaseTest {

    private fun makeUseCase(
        periodRepo: FakeAccountingPeriodRepository = FakeAccountingPeriodRepository(),
        statementRepo: FakeFinancialStatementRepository = FakeFinancialStatementRepository(),
    ): Triple<CloseAccountingPeriodUseCase, FakeAccountingPeriodRepository, FakeFinancialStatementRepository> {
        val useCase = CloseAccountingPeriodUseCase(periodRepo, statementRepo)
        return Triple(useCase, periodRepo, statementRepo)
    }

    @Test
    fun test_close_open_period_succeeds() = runTest {
        val (useCase, periodRepo, _) = makeUseCase()
        val period = buildPeriod(id = "period-01", status = PeriodStatus.OPEN)
        periodRepo.periods.add(period)

        val result = useCase.execute("period-01", STORE_ID, NOW)

        assertIs<Result.Success<Unit>>(result)
        assertEquals(PeriodStatus.CLOSED, periodRepo.periods.first { it.id == "period-01" }.status)
    }

    @Test
    fun test_close_already_closed_period_returns_error() = runTest {
        val (useCase, periodRepo, _) = makeUseCase()
        val period = buildPeriod(id = "period-02", status = PeriodStatus.CLOSED)
        periodRepo.periods.add(period)

        val result = useCase.execute("period-02", STORE_ID, NOW)

        assertIs<Result.Error>(result)
        assertIs<ValidationException>(result.exception)
        assertEquals("INVALID_STATUS_TRANSITION", (result.exception as ValidationException).rule)
    }

    @Test
    fun test_close_locked_period_returns_error() = runTest {
        val (useCase, periodRepo, _) = makeUseCase()
        val period = buildPeriod(id = "period-03", status = PeriodStatus.LOCKED)
        periodRepo.periods.add(period)

        val result = useCase.execute("period-03", STORE_ID, NOW)

        assertIs<Result.Error>(result)
        assertIs<ValidationException>(result.exception)
        assertEquals("INVALID_STATUS_TRANSITION", (result.exception as ValidationException).rule)
    }
}
