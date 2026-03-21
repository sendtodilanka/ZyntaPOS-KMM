package com.zyntasolutions.zyntapos.feature.accounting

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.Account
import com.zyntasolutions.zyntapos.domain.model.AccountBalance
import com.zyntasolutions.zyntapos.domain.model.AccountType
import com.zyntasolutions.zyntapos.domain.model.NormalBalance
import com.zyntasolutions.zyntapos.domain.repository.AccountRepository
import com.zyntasolutions.zyntapos.domain.usecase.accounting.GetAccountsUseCase
import com.zyntasolutions.zyntapos.domain.usecase.accounting.SaveAccountUseCase
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
 * Unit tests for [AccountDetailViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val existingAccount = Account(
        id = "acct-001",
        accountCode = "1010",
        accountName = "Cash",
        accountType = AccountType.ASSET,
        subCategory = "Current Assets",
        normalBalance = NormalBalance.DEBIT,
        isSystemAccount = false,
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_000_000L,
    )

    private var getByIdResult: Result<Account?> = Result.Success(existingAccount)
    private var isCodeTakenResult: Result<Boolean> = Result.Success(false)
    private var createResult: Result<Unit> = Result.Success(Unit)
    private var updateResult: Result<Unit> = Result.Success(Unit)

    private val fakeAccountRepo = object : AccountRepository {
        override fun getAll(storeId: String): Flow<List<Account>> =
            MutableStateFlow(listOf(existingAccount))
        override fun getByType(storeId: String, accountType: AccountType): Flow<List<Account>> =
            MutableStateFlow(emptyList())
        override suspend fun getById(id: String): Result<Account?> = getByIdResult
        override suspend fun getByCode(storeId: String, accountCode: String): Result<Account?> =
            Result.Success(null)
        override suspend fun getBalance(accountId: String, periodId: String): Result<AccountBalance?> =
            Result.Success(null)
        override fun getAllBalances(storeId: String, periodId: String): Flow<List<AccountBalance>> =
            MutableStateFlow(emptyList())
        override suspend fun create(account: Account): Result<Unit> = createResult
        override suspend fun update(account: Account): Result<Unit> = updateResult
        override suspend fun deactivate(id: String, updatedAt: Long): Result<Unit> = Result.Success(Unit)
        override suspend fun isAccountCodeTaken(storeId: String, code: String, excludeId: String?): Result<Boolean> =
            isCodeTakenResult
        override suspend fun seedDefaultAccounts(accounts: List<Account>): Result<Unit> = Result.Success(Unit)
    }

    private lateinit var viewModel: AccountDetailViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = AccountDetailViewModel(
            getAccountsUseCase = GetAccountsUseCase(fakeAccountRepo),
            saveAccountUseCase = SaveAccountUseCase(fakeAccountRepo),
        )
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    // ── Initial state ──────────────────────────────────────────────────────────

    @Test
    fun `initial state is blank`() {
        val state = viewModel.state.value
        assertNull(state.account)
        assertFalse(state.isLoading)
        assertFalse(state.isSaving)
        assertEquals("", state.accountCode)
        assertEquals("", state.accountName)
        assertNull(state.error)
    }

    // ── Load ───────────────────────────────────────────────────────────────────

    @Test
    fun `Load populates form fields from existing account`() = runTest {
        viewModel.handleIntentForTest(AccountDetailIntent.Load("acct-001"))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("1010", state.accountCode)
        assertEquals("Cash", state.accountName)
        assertEquals(AccountType.ASSET, state.accountType)
        assertEquals("Current Assets", state.subCategory)
        assertNull(state.error)
    }

    @Test
    fun `Load clears isLoading after success`() = runTest {
        viewModel.handleIntentForTest(AccountDetailIntent.Load("acct-001"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `Load sets error when account not found`() = runTest {
        getByIdResult = Result.Success(null)
        viewModel.handleIntentForTest(AccountDetailIntent.Load("acct-999"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.error)
    }

    @Test
    fun `Load emits ShowError effect when account not found`() = runTest {
        getByIdResult = Result.Success(null)

        viewModel.effects.test {
            viewModel.handleIntentForTest(AccountDetailIntent.Load("acct-999"))
            testDispatcher.scheduler.advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is AccountDetailEffect.ShowError)
        }
    }

    @Test
    fun `Load emits ShowError on repository error`() = runTest {
        getByIdResult = Result.Error(DatabaseException("DB error"))

        viewModel.effects.test {
            viewModel.handleIntentForTest(AccountDetailIntent.Load("acct-001"))
            testDispatcher.scheduler.advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is AccountDetailEffect.ShowError)
        }
    }

    // ── StartNew ───────────────────────────────────────────────────────────────

    @Test
    fun `StartNew resets state to blank`() = runTest {
        // First load an account
        viewModel.handleIntentForTest(AccountDetailIntent.Load("acct-001"))
        testDispatcher.scheduler.advanceUntilIdle()

        // Then start new
        viewModel.handleIntentForTest(AccountDetailIntent.StartNew("store-001"))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertNull(state.account)
        assertEquals("", state.accountCode)
        assertEquals("", state.accountName)
    }

    // ── Field update intents ───────────────────────────────────────────────────

    @Test
    fun `UpdateCode updates accountCode and clears error`() = runTest {
        viewModel.state.test {
            awaitItem()
            viewModel.handleIntentForTest(AccountDetailIntent.UpdateCode("5010"))
            val updated = awaitItem()
            assertEquals("5010", updated.accountCode)
            assertNull(updated.error)
        }
    }

    @Test
    fun `UpdateName updates accountName and clears error`() = runTest {
        viewModel.state.test {
            awaitItem()
            viewModel.handleIntentForTest(AccountDetailIntent.UpdateName("Petty Cash"))
            val updated = awaitItem()
            assertEquals("Petty Cash", updated.accountName)
            assertNull(updated.error)
        }
    }

    @Test
    fun `UpdateType updates accountType`() = runTest {
        viewModel.state.test {
            awaitItem()
            viewModel.handleIntentForTest(AccountDetailIntent.UpdateType(AccountType.INCOME))
            val updated = awaitItem()
            assertEquals(AccountType.INCOME, updated.accountType)
        }
    }

    @Test
    fun `UpdateSubCategory updates subCategory`() = runTest {
        viewModel.state.test {
            awaitItem()
            viewModel.handleIntentForTest(AccountDetailIntent.UpdateSubCategory("Operating Revenue"))
            val updated = awaitItem()
            assertEquals("Operating Revenue", updated.subCategory)
        }
    }

    @Test
    fun `UpdateDescription updates description`() = runTest {
        viewModel.state.test {
            awaitItem()
            viewModel.handleIntentForTest(AccountDetailIntent.UpdateDescription("Cash on hand"))
            val updated = awaitItem()
            assertEquals("Cash on hand", updated.description)
        }
    }

    // ── Save ───────────────────────────────────────────────────────────────────

    @Test
    fun `Save new account emits SavedSuccessfully effect`() = runTest {
        // No existing account (creates new)
        getByIdResult = Result.Success(null)
        createResult = Result.Success(Unit)
        isCodeTakenResult = Result.Success(false)

        viewModel.handleIntentForTest(AccountDetailIntent.UpdateCode("5010"))
        viewModel.handleIntentForTest(AccountDetailIntent.UpdateName("Utilities Expense"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            viewModel.handleIntentForTest(AccountDetailIntent.Save("store-001"))
            testDispatcher.scheduler.advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is AccountDetailEffect.SavedSuccessfully)
        }
    }

    @Test
    fun `Save emits ShowError when code is blank`() = runTest {
        // accountCode is blank by default
        viewModel.effects.test {
            viewModel.handleIntentForTest(AccountDetailIntent.Save("store-001"))
            testDispatcher.scheduler.advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is AccountDetailEffect.ShowError)
        }
    }

    @Test
    fun `Save emits ShowError when code is duplicate`() = runTest {
        getByIdResult = Result.Success(null)
        isCodeTakenResult = Result.Success(true) // code already taken

        viewModel.handleIntentForTest(AccountDetailIntent.UpdateCode("1010"))
        viewModel.handleIntentForTest(AccountDetailIntent.UpdateName("Duplicate Account"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            viewModel.handleIntentForTest(AccountDetailIntent.Save("store-001"))
            testDispatcher.scheduler.advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is AccountDetailEffect.ShowError)
        }
    }

    @Test
    fun `Save clears isSaving after completion`() = runTest {
        getByIdResult = Result.Success(null)
        isCodeTakenResult = Result.Success(false)
        createResult = Result.Success(Unit)

        viewModel.handleIntentForTest(AccountDetailIntent.UpdateCode("5010"))
        viewModel.handleIntentForTest(AccountDetailIntent.UpdateName("New Account"))
        viewModel.handleIntentForTest(AccountDetailIntent.Save("store-001"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.isSaving)
    }
}

// ─── Extension to expose handleIntent for testing ────────────────────────────

private fun AccountDetailViewModel.handleIntentForTest(intent: AccountDetailIntent) =
    dispatch(intent)
