package com.zyntasolutions.zyntapos.feature.accounting

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.Account
import com.zyntasolutions.zyntapos.domain.model.AccountBalance
import com.zyntasolutions.zyntapos.domain.model.AccountType
import com.zyntasolutions.zyntapos.domain.model.NormalBalance
import com.zyntasolutions.zyntapos.domain.repository.AccountRepository
import com.zyntasolutions.zyntapos.domain.usecase.accounting.DeactivateAccountUseCase
import com.zyntasolutions.zyntapos.domain.usecase.accounting.GetAccountsUseCase
import com.zyntasolutions.zyntapos.domain.usecase.accounting.SeedDefaultChartOfAccountsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [ChartOfAccountsViewModel].
 *
 * Uses a hand-rolled fake [AccountRepository] to control flow emissions and mutation results.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChartOfAccountsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val cashAccount = Account(
        id = "acct-001",
        accountCode = "1010",
        accountName = "Cash",
        accountType = AccountType.ASSET,
        subCategory = "Current Assets",
        normalBalance = NormalBalance.DEBIT,
        isSystemAccount = true,
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_000_000L,
    )

    private val revenueAccount = Account(
        id = "acct-002",
        accountCode = "4010",
        accountName = "Sales Revenue",
        accountType = AccountType.INCOME,
        subCategory = "Revenue",
        normalBalance = NormalBalance.CREDIT,
        isSystemAccount = false,
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_000_000L,
    )

    private val accountsFlow = MutableStateFlow(listOf(cashAccount, revenueAccount))
    private var deactivateResult: Result<Unit> = Result.Success(Unit)
    private var seedResult: Result<Unit> = Result.Success(Unit)

    private val fakeAccountRepo = object : AccountRepository {
        override fun getAll(storeId: String): Flow<List<Account>> = accountsFlow
        override fun getByType(storeId: String, accountType: AccountType): Flow<List<Account>> =
            MutableStateFlow(accountsFlow.value.filter { it.accountType == accountType })

        override suspend fun getById(id: String): Result<Account?> =
            Result.Success(accountsFlow.value.firstOrNull { it.id == id })

        override suspend fun getByCode(storeId: String, accountCode: String): Result<Account?> =
            Result.Success(accountsFlow.value.firstOrNull { it.accountCode == accountCode })

        override suspend fun getBalance(accountId: String, periodId: String): Result<AccountBalance?> =
            Result.Success(null)

        override fun getAllBalances(storeId: String, periodId: String): Flow<List<AccountBalance>> =
            MutableStateFlow(emptyList())

        override suspend fun create(account: Account): Result<Unit> = Result.Success(Unit)
        override suspend fun update(account: Account): Result<Unit> = Result.Success(Unit)
        override suspend fun deactivate(id: String, updatedAt: Long): Result<Unit> = deactivateResult
        override suspend fun isAccountCodeTaken(storeId: String, code: String, excludeId: String?): Result<Boolean> =
            Result.Success(false)
        override suspend fun seedDefaultAccounts(accounts: List<Account>): Result<Unit> = seedResult
    }

    private lateinit var viewModel: ChartOfAccountsViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = ChartOfAccountsViewModel(
            getAccountsUseCase = GetAccountsUseCase(fakeAccountRepo),
            deactivateAccountUseCase = DeactivateAccountUseCase(fakeAccountRepo),
            seedDefaultChartOfAccountsUseCase = SeedDefaultChartOfAccountsUseCase(fakeAccountRepo),
        )
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    // ── Initial state ──────────────────────────────────────────────────────────

    @Test
    fun `initial state has empty search and no filter`() {
        val state = viewModel.state.value
        assertEquals("", state.searchQuery)
        assertNull(state.selectedType)
        assertNull(state.error)
        assertFalse(state.isLoading)
    }

    // ── Account loading ────────────────────────────────────────────────────────

    @Test
    fun `accounts are loaded reactively on init`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, viewModel.state.value.accounts.size)
    }

    @Test
    fun `new account emitted by flow updates state`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        val newAccount = cashAccount.copy(id = "acct-003", accountCode = "2010", accountName = "Accounts Payable",
            accountType = AccountType.LIABILITY, normalBalance = NormalBalance.CREDIT)

        viewModel.state.test {
            awaitItem() // current
            accountsFlow.value = listOf(cashAccount, revenueAccount, newAccount)
            val updated = awaitItem()
            assertEquals(3, updated.accounts.size)
        }
    }

    // ── SearchAccounts ─────────────────────────────────────────────────────────

    @Test
    fun `SearchAccounts updates searchQuery in state`() = runTest {
        viewModel.state.test {
            awaitItem()
            viewModel.handleIntentForTest(ChartOfAccountsIntent.SearchAccounts("Cash"))
            val updated = awaitItem()
            assertEquals("Cash", updated.searchQuery)
        }
    }

    @Test
    fun `SearchAccounts filters accounts by name`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.handleIntentForTest(ChartOfAccountsIntent.SearchAccounts("Cash"))
        testDispatcher.scheduler.advanceUntilIdle()
        // Re-trigger observeAccounts so the filter applies to the replayed flow value
        viewModel.handleIntentForTest(ChartOfAccountsIntent.LoadAccounts)
        testDispatcher.scheduler.advanceUntilIdle()

        val accounts = viewModel.state.value.accounts
        assertTrue(accounts.all { it.accountName.contains("Cash", ignoreCase = true) })
    }

    @Test
    fun `SearchAccounts filters accounts by account code`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.handleIntentForTest(ChartOfAccountsIntent.SearchAccounts("4010"))
        testDispatcher.scheduler.advanceUntilIdle()
        // Re-trigger observeAccounts so the filter applies to the replayed flow value
        viewModel.handleIntentForTest(ChartOfAccountsIntent.LoadAccounts)
        testDispatcher.scheduler.advanceUntilIdle()

        val accounts = viewModel.state.value.accounts
        assertTrue(accounts.any { it.accountCode == "4010" })
        assertEquals(1, accounts.size)
    }

    @Test
    fun `SearchAccounts with blank query shows all accounts`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.handleIntentForTest(ChartOfAccountsIntent.SearchAccounts("Cash"))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.handleIntentForTest(ChartOfAccountsIntent.SearchAccounts(""))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, viewModel.state.value.accounts.size)
    }

    // ── FilterByType ───────────────────────────────────────────────────────────

    @Test
    fun `FilterByType updates selectedType in state`() = runTest {
        viewModel.state.test {
            awaitItem()
            viewModel.handleIntentForTest(ChartOfAccountsIntent.FilterByType(AccountType.ASSET))
            val updated = awaitItem()
            assertEquals(AccountType.ASSET, updated.selectedType)
        }
    }

    @Test
    fun `FilterByType with null shows all accounts`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.handleIntentForTest(ChartOfAccountsIntent.FilterByType(AccountType.INCOME))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.handleIntentForTest(ChartOfAccountsIntent.FilterByType(null))
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.selectedType)
        assertEquals(2, viewModel.state.value.accounts.size)
    }

    // ── DeactivateAccount ──────────────────────────────────────────────────────

    @Test
    fun `DeactivateAccount success emits ShowSuccess effect`() = runTest {
        deactivateResult = Result.Success(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            viewModel.handleIntentForTest(ChartOfAccountsIntent.DeactivateAccount("acct-002"))
            testDispatcher.scheduler.advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is ChartOfAccountsEffect.ShowSuccess)
            assertTrue((effect as ChartOfAccountsEffect.ShowSuccess).message.contains("deactivated", ignoreCase = true))
        }
    }

    @Test
    fun `DeactivateAccount system account emits ShowError effect`() = runTest {
        // cashAccount is a system account — DeactivateAccountUseCase rejects it
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            viewModel.handleIntentForTest(ChartOfAccountsIntent.DeactivateAccount("acct-001"))
            testDispatcher.scheduler.advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is ChartOfAccountsEffect.ShowError)
        }
    }

    // ── SeedDefaultAccounts ────────────────────────────────────────────────────

    @Test
    fun `SeedDefaultAccounts clears loading after success`() = runTest {
        seedResult = Result.Success(Unit)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.handleIntentForTest(ChartOfAccountsIntent.SeedDefaultAccounts)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `SeedDefaultAccounts success emits ShowSuccess effect`() = runTest {
        seedResult = Result.Success(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            viewModel.handleIntentForTest(ChartOfAccountsIntent.SeedDefaultAccounts)
            testDispatcher.scheduler.advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is ChartOfAccountsEffect.ShowSuccess)
        }
    }

    @Test
    fun `SeedDefaultAccounts failure emits ShowError effect`() = runTest {
        seedResult = Result.Error(DatabaseException("DB error"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            viewModel.handleIntentForTest(ChartOfAccountsIntent.SeedDefaultAccounts)
            testDispatcher.scheduler.advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is ChartOfAccountsEffect.ShowError)
            assertTrue((effect as ChartOfAccountsEffect.ShowError).message.contains("DB error"))
        }
    }
}

// ─── Extension to expose handleIntent for testing ────────────────────────────

private fun ChartOfAccountsViewModel.handleIntentForTest(intent: ChartOfAccountsIntent) =
    dispatch(intent)
