package com.zyntasolutions.zyntapos.feature.expenses

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.AuditEntry
import com.zyntasolutions.zyntapos.domain.model.Expense
import com.zyntasolutions.zyntapos.domain.model.ExpenseCategory
import com.zyntasolutions.zyntapos.domain.model.RecurringExpense
import com.zyntasolutions.zyntapos.domain.repository.AuditRepository
import com.zyntasolutions.zyntapos.domain.repository.ExpenseRepository
import com.zyntasolutions.zyntapos.domain.usecase.expenses.ApproveExpenseUseCase
import com.zyntasolutions.zyntapos.domain.usecase.expenses.SaveExpenseUseCase
import com.zyntasolutions.zyntapos.security.audit.SecurityAuditLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
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
import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
import com.zyntasolutions.zyntapos.domain.model.User
import com.zyntasolutions.zyntapos.domain.model.Role
import kotlinx.datetime.Instant
import com.zyntasolutions.zyntapos.domain.model.Account
import com.zyntasolutions.zyntapos.domain.model.AccountBalance
import com.zyntasolutions.zyntapos.domain.model.AccountType
import com.zyntasolutions.zyntapos.domain.model.AccountingPeriod
import com.zyntasolutions.zyntapos.domain.model.JournalEntry
import com.zyntasolutions.zyntapos.domain.model.JournalReferenceType
import com.zyntasolutions.zyntapos.domain.repository.AccountRepository
import com.zyntasolutions.zyntapos.domain.repository.AccountingPeriodRepository
import com.zyntasolutions.zyntapos.domain.repository.JournalRepository
import com.zyntasolutions.zyntapos.core.analytics.AnalyticsTracker
import com.zyntasolutions.zyntapos.domain.usecase.accounting.PostExpenseJournalEntryUseCase

// ─────────────────────────────────────────────────────────────────────────────
// ExpenseViewModelTest
// Tests ExpenseViewModel MVI state transitions using hand-rolled fakes.
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class ExpenseViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val currentUserId = "user-001"

    private val noOpAnalytics = object : AnalyticsTracker {
        override fun logEvent(name: String, params: Map<String, String>) = Unit
        override fun logScreenView(screenName: String, screenClass: String) = Unit
        override fun setUserId(userId: String?) = Unit
        override fun setUserProperty(name: String, value: String) = Unit
    }

    // ── No-op AuditRepository + SecurityAuditLogger ───────────────────────────

    private val noOpAuditRepository = object : AuditRepository {
        override suspend fun insert(entry: AuditEntry) = Unit
        override fun observeAll(): Flow<List<AuditEntry>> = MutableStateFlow(emptyList())
        override fun observeByUserId(userId: String): Flow<List<AuditEntry>> = MutableStateFlow(emptyList())
        override suspend fun getAllChronological(): List<AuditEntry> = emptyList()
        override suspend fun getLatestHash(): String? = null
        override suspend fun countEntries(): Long = 0L
        override suspend fun getRecentLoginFailureCount(userId: String, sinceEpochMillis: Long): Long = 0L
    }
    private val testAuditLogger = SecurityAuditLogger(noOpAuditRepository, "test-device")

    private val fakeAuthRepository = object : AuthRepository {
        private val _session = MutableStateFlow<User?>(
            User(
                id = "user-001", name = "Test User", email = "test@zynta.com",
                role = Role.CASHIER, storeId = "store-001", isActive = true,
                pinHash = null, createdAt = Instant.fromEpochMilliseconds(0),
                updatedAt = Instant.fromEpochMilliseconds(0),
            )
        )
        override fun getSession(): Flow<User?> = _session
        override suspend fun login(email: String, password: String): Result<User> =
            Result.Success(_session.value!!)
        override suspend fun logout() { _session.value = null }
        override suspend fun refreshToken(): Result<Unit> = Result.Success(Unit)
        override suspend fun updatePin(userId: String, pin: String): Result<Unit> =
            Result.Success(Unit)
        override suspend fun validatePin(userId: String, pin: String): Result<Boolean> =
            Result.Success(true)
        override suspend fun quickSwitch(userId: String, pin: String): Result<User> =
            Result.Error(com.zyntasolutions.zyntapos.core.result.DatabaseException("not used"))
        override suspend fun validateManagerPin(pin: String): Result<Boolean> =
            Result.Success(false)
    }

    private val fakeSettingsRepository = object : com.zyntasolutions.zyntapos.domain.repository.SettingsRepository {
        private val store = mutableMapOf<String, String>()
        override suspend fun get(key: String): String? = store[key]
        override suspend fun set(key: String, value: String): Result<Unit> {
            store[key] = value
            return Result.Success(Unit)
        }
        override suspend fun getAll(): Map<String, String> = store.toMap()
        override fun observe(key: String): Flow<String?> = MutableStateFlow(store[key])
    }

    // ── Fake ExpenseRepository ────────────────────────────────────────────────

    private val expensesFlow = MutableStateFlow<List<Expense>>(emptyList())
    private val categoriesFlow = MutableStateFlow<List<ExpenseCategory>>(emptyList())
    private var shouldFailInsert = false
    private var shouldFailDelete = false
    private var shouldFailApprove = false

    private val fakeExpenseRepository = object : ExpenseRepository {
        override fun getAll(): Flow<List<Expense>> = expensesFlow

        override fun getByStatus(status: Expense.Status): Flow<List<Expense>> =
            expensesFlow.map { list -> list.filter { it.status == status } }

        override fun getByDateRange(from: Long, to: Long): Flow<List<Expense>> =
            expensesFlow.map { list ->
                list.filter { it.expenseDate in from..to }
            }

        override suspend fun getById(id: String): Result<Expense> {
            val expense = expensesFlow.value.firstOrNull { it.id == id }
                ?: return Result.Error(DatabaseException("Expense '$id' not found"))
            return Result.Success(expense)
        }

        override suspend fun getTotalByPeriod(from: Long, to: Long): Result<Double> =
            Result.Success(
                expensesFlow.value
                    .filter { it.status == Expense.Status.APPROVED && it.expenseDate in from..to }
                    .sumOf { it.amount },
            )

        override suspend fun insert(expense: Expense): Result<Unit> {
            if (shouldFailInsert) return Result.Error(DatabaseException("Insert failed"))
            expensesFlow.value = expensesFlow.value + expense
            return Result.Success(Unit)
        }

        override suspend fun update(expense: Expense): Result<Unit> {
            val idx = expensesFlow.value.indexOfFirst { it.id == expense.id }
            if (idx == -1) return Result.Error(DatabaseException("Not found"))
            val updated = expensesFlow.value.toMutableList().also { it[idx] = expense }
            expensesFlow.value = updated
            return Result.Success(Unit)
        }

        override suspend fun approve(id: String, approvedBy: String): Result<Unit> {
            if (shouldFailApprove) return Result.Error(DatabaseException("Approve failed"))
            val idx = expensesFlow.value.indexOfFirst { it.id == id }
            if (idx == -1) return Result.Error(DatabaseException("Not found"))
            val updated = expensesFlow.value.toMutableList()
            updated[idx] = updated[idx].copy(status = Expense.Status.APPROVED, approvedBy = approvedBy)
            expensesFlow.value = updated
            return Result.Success(Unit)
        }

        override suspend fun reject(
            id: String,
            rejectedBy: String,
            reason: String?,
        ): Result<Unit> {
            val idx = expensesFlow.value.indexOfFirst { it.id == id }
            if (idx == -1) return Result.Error(DatabaseException("Not found"))
            val updated = expensesFlow.value.toMutableList()
            updated[idx] = updated[idx].copy(
                status = Expense.Status.REJECTED,
                approvedBy = rejectedBy,
                rejectReason = reason,
            )
            expensesFlow.value = updated
            return Result.Success(Unit)
        }

        override suspend fun delete(id: String): Result<Unit> {
            if (shouldFailDelete) return Result.Error(DatabaseException("Delete failed"))
            expensesFlow.value = expensesFlow.value.filter { it.id != id }
            return Result.Success(Unit)
        }

        override fun getAllCategories(): Flow<List<ExpenseCategory>> = categoriesFlow

        override suspend fun getCategoryById(id: String): Result<ExpenseCategory> {
            val category = categoriesFlow.value.firstOrNull { it.id == id }
                ?: return Result.Error(DatabaseException("Category '$id' not found"))
            return Result.Success(category)
        }

        override suspend fun saveCategory(category: ExpenseCategory): Result<Unit> {
            val idx = categoriesFlow.value.indexOfFirst { it.id == category.id }
            categoriesFlow.value = if (idx == -1) {
                categoriesFlow.value + category
            } else {
                categoriesFlow.value.toMutableList().also { it[idx] = category }
            }
            return Result.Success(Unit)
        }

        override suspend fun deleteCategory(id: String): Result<Unit> {
            categoriesFlow.value = categoriesFlow.value.filter { it.id != id }
            return Result.Success(Unit)
        }

        override fun getAllRecurring(): Flow<List<RecurringExpense>> =
            MutableStateFlow(emptyList())

        override suspend fun getActiveRecurring(): Result<List<RecurringExpense>> =
            Result.Success(emptyList())

        override suspend fun saveRecurring(recurring: RecurringExpense): Result<Unit> =
            Result.Success(Unit)

        override suspend fun updateLastRun(id: String, lastRunMillis: Long): Result<Unit> =
            Result.Success(Unit)

        override suspend fun deleteRecurring(id: String): Result<Unit> =
            Result.Success(Unit)
    }

    private val saveExpenseUseCase = SaveExpenseUseCase(fakeExpenseRepository)
    private val approveExpenseUseCase = ApproveExpenseUseCase(fakeExpenseRepository)

    // ── Fake accounting repositories ──────────────────────────────────────────

    private val fakeAccountRepository = object : AccountRepository {
        override fun getAll(storeId: String): Flow<List<Account>> = MutableStateFlow(emptyList())
        override fun getByType(storeId: String, accountType: AccountType): Flow<List<Account>> = MutableStateFlow(emptyList())
        override suspend fun getById(id: String): Result<Account?> = Result.Success(null)
        override suspend fun getByCode(storeId: String, accountCode: String): Result<Account?> = Result.Success(null)
        override suspend fun getBalance(accountId: String, periodId: String): Result<AccountBalance?> = Result.Success(null)
        override fun getAllBalances(storeId: String, periodId: String): Flow<List<AccountBalance>> = MutableStateFlow(emptyList())
        override suspend fun create(account: Account): Result<Unit> = Result.Success(Unit)
        override suspend fun update(account: Account): Result<Unit> = Result.Success(Unit)
        override suspend fun deactivate(id: String, updatedAt: Long): Result<Unit> = Result.Success(Unit)
        override suspend fun isAccountCodeTaken(storeId: String, code: String, excludeId: String?): Result<Boolean> = Result.Success(false)
        override suspend fun seedDefaultAccounts(accounts: List<Account>): Result<Unit> = Result.Success(Unit)
    }

    private val fakeAccountingPeriodRepository = object : AccountingPeriodRepository {
        override fun getAll(storeId: String): Flow<List<AccountingPeriod>> = MutableStateFlow(emptyList())
        override suspend fun getById(id: String): Result<AccountingPeriod?> = Result.Success(null)
        override suspend fun getPeriodForDate(storeId: String, date: String): Result<AccountingPeriod?> = Result.Success(null)
        override suspend fun getOpenPeriods(storeId: String): Result<List<AccountingPeriod>> = Result.Success(emptyList())
        override suspend fun create(period: AccountingPeriod): Result<Unit> = Result.Success(Unit)
        override suspend fun closePeriod(id: String, updatedAt: Long): Result<Unit> = Result.Success(Unit)
        override suspend fun lockPeriod(id: String, lockedBy: String, lockedAt: Long): Result<Unit> = Result.Success(Unit)
        override suspend fun reopenPeriod(id: String, updatedAt: Long): Result<Unit> = Result.Success(Unit)
    }

    private val fakeJournalRepository = object : JournalRepository {
        override fun getEntriesByDateRange(storeId: String, fromDate: String, toDate: String): Flow<List<JournalEntry>> = MutableStateFlow(emptyList())
        override fun getUnpostedEntries(storeId: String): Flow<List<JournalEntry>> = MutableStateFlow(emptyList())
        override suspend fun getById(id: String): Result<JournalEntry?> = Result.Success(null)
        override suspend fun getByReference(referenceType: JournalReferenceType, referenceId: String): Result<List<JournalEntry>> = Result.Success(emptyList())
        override suspend fun getNextEntryNumber(storeId: String): Result<Int> = Result.Success(1)
        override suspend fun saveDraftEntry(entry: JournalEntry): Result<Unit> = Result.Success(Unit)
        override suspend fun postEntry(entryId: String, postedAt: Long): Result<Unit> = Result.Success(Unit)
        override suspend fun unpostEntry(entryId: String): Result<Unit> = Result.Success(Unit)
        override suspend fun deleteEntry(entryId: String): Result<Unit> = Result.Success(Unit)
        override suspend fun reverseEntry(originalEntryId: String, reversalDate: String, createdBy: String, now: Long): Result<JournalEntry> =
            Result.Error(DatabaseException("Not supported"))
    }

    private val postExpenseJournalEntryUseCase = PostExpenseJournalEntryUseCase(
        journalRepository = fakeJournalRepository,
        accountRepository = fakeAccountRepository,
        periodRepository = fakeAccountingPeriodRepository,
    )

    private lateinit var viewModel: ExpenseViewModel

    private val now = System.currentTimeMillis()
    private val testExpense = Expense(
        id = "exp-001",
        description = "Office supplies",
        amount = 1500.0,
        expenseDate = now - 86_400_000L,
        createdBy = currentUserId,
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        expensesFlow.value = emptyList()
        categoriesFlow.value = emptyList()
        shouldFailInsert = false
        shouldFailDelete = false
        shouldFailApprove = false
        viewModel = ExpenseViewModel(
            expenseRepository = fakeExpenseRepository,
            saveExpenseUseCase = saveExpenseUseCase,
            approveExpenseUseCase = approveExpenseUseCase,
            authRepository = fakeAuthRepository,
            postExpenseJournalEntryUseCase = postExpenseJournalEntryUseCase,
            settingsRepository = fakeSettingsRepository,
            auditLogger = testAuditLogger,
            analytics = noOpAnalytics,
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    fun `initial state has empty expense list and not loading`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.state.value
        assertTrue(state.expenses.isEmpty())
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    // ── Observable list ───────────────────────────────────────────────────────

    @Test
    fun `adding expenses to repository reflects in state`() = runTest {
        expensesFlow.value = listOf(testExpense)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.state.value.expenses.size)
        assertEquals(testExpense.description, viewModel.state.value.expenses.first().description)
    }

    // ── SaveExpense — validation ───────────────────────────────────────────────

    @Test
    fun `SaveExpense with blank description sets validation error and does not persist`() = runTest {
        viewModel.dispatch(ExpenseIntent.UpdateFormField("amount", "500"))
        viewModel.dispatch(ExpenseIntent.UpdateFormField("expenseDate", now.toString()))
        // description left blank
        viewModel.dispatch(ExpenseIntent.SaveExpense)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.expenseForm.validationErrors["description"])
        assertTrue(expensesFlow.value.isEmpty())
    }

    @Test
    fun `SaveExpense with zero amount sets validation error`() = runTest {
        viewModel.dispatch(ExpenseIntent.UpdateFormField("description", "Test expense"))
        viewModel.dispatch(ExpenseIntent.UpdateFormField("amount", "0"))
        viewModel.dispatch(ExpenseIntent.UpdateFormField("expenseDate", now.toString()))
        viewModel.dispatch(ExpenseIntent.SaveExpense)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.expenseForm.validationErrors["amount"])
    }

    @Test
    fun `SaveExpense with missing expenseDate sets validation error`() = runTest {
        viewModel.dispatch(ExpenseIntent.UpdateFormField("description", "Test expense"))
        viewModel.dispatch(ExpenseIntent.UpdateFormField("amount", "500"))
        // expenseDate left blank — toLongOrNull() will return null
        viewModel.dispatch(ExpenseIntent.SaveExpense)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.expenseForm.validationErrors["expenseDate"])
    }

    // ── SaveExpense — success ──────────────────────────────────────────────────

    @Test
    fun `SaveExpense with valid form emits ShowSuccess and NavigateToList effects`() = runTest {
        viewModel.dispatch(ExpenseIntent.UpdateFormField("description", "Printer ink"))
        viewModel.dispatch(ExpenseIntent.UpdateFormField("amount", "750.0"))
        viewModel.dispatch(ExpenseIntent.UpdateFormField("expenseDate", now.toString()))

        viewModel.effects.test {
            viewModel.dispatch(ExpenseIntent.SaveExpense)
            testDispatcher.scheduler.advanceUntilIdle()

            val effect1 = awaitItem()
            assertTrue(effect1 is ExpenseEffect.ShowSuccess)
            val effect2 = awaitItem()
            assertTrue(effect2 is ExpenseEffect.NavigateToList)
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(1, expensesFlow.value.size)
        assertEquals("Printer ink", expensesFlow.value.first().description)
    }

    // ── DeleteExpense ──────────────────────────────────────────────────────────

    @Test
    fun `DeleteExpense removes expense and emits ShowSuccess then NavigateToList`() = runTest {
        expensesFlow.value = listOf(testExpense)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            viewModel.dispatch(ExpenseIntent.DeleteExpense(testExpense.id))
            testDispatcher.scheduler.advanceUntilIdle()

            val effect1 = awaitItem()
            assertTrue(effect1 is ExpenseEffect.ShowSuccess)
            val effect2 = awaitItem()
            assertTrue(effect2 is ExpenseEffect.NavigateToList)
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(expensesFlow.value.isEmpty())
    }

    @Test
    fun `DeleteExpense on repository failure emits ShowError`() = runTest {
        shouldFailDelete = true
        expensesFlow.value = listOf(testExpense)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            viewModel.dispatch(ExpenseIntent.DeleteExpense(testExpense.id))
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is ExpenseEffect.ShowError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── ApproveExpense ─────────────────────────────────────────────────────────

    @Test
    fun `ApproveExpense on PENDING expense emits ShowSuccess`() = runTest {
        expensesFlow.value = listOf(testExpense) // status = PENDING by default
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            viewModel.dispatch(ExpenseIntent.ApproveExpense(testExpense.id))
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is ExpenseEffect.ShowSuccess)
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(Expense.Status.APPROVED, expensesFlow.value.first().status)
    }

    @Test
    fun `ApproveExpense on repository failure emits ShowError`() = runTest {
        shouldFailApprove = true
        expensesFlow.value = listOf(testExpense)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            viewModel.dispatch(ExpenseIntent.ApproveExpense(testExpense.id))
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is ExpenseEffect.ShowError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── RejectExpense ──────────────────────────────────────────────────────────

    @Test
    fun `RejectExpense on PENDING expense emits ShowSuccess`() = runTest {
        expensesFlow.value = listOf(testExpense)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            viewModel.dispatch(ExpenseIntent.RejectExpense(testExpense.id, "Not authorized"))
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is ExpenseEffect.ShowSuccess)
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(Expense.Status.REJECTED, expensesFlow.value.first().status)
    }

    // ── FilterByStatus ────────────────────────────────────────────────────────

    @Test
    fun `FilterByStatus updates statusFilter in state`() = runTest {
        assertNull(viewModel.state.value.statusFilter)

        viewModel.dispatch(ExpenseIntent.FilterByStatus(Expense.Status.PENDING))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(Expense.Status.PENDING, viewModel.state.value.statusFilter)
    }

    // ── Category management ───────────────────────────────────────────────────

    @Test
    fun `SaveCategory with blank name sets validation error`() = runTest {
        viewModel.dispatch(ExpenseIntent.SelectCategory(null))
        testDispatcher.scheduler.advanceUntilIdle()
        // name left blank
        viewModel.dispatch(ExpenseIntent.SaveCategory)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.categoryForm.validationErrors["name"])
        assertTrue(categoriesFlow.value.isEmpty())
    }

    @Test
    fun `SaveCategory with valid name creates category and emits ShowSuccess`() = runTest {
        viewModel.dispatch(ExpenseIntent.SelectCategory(null))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.dispatch(ExpenseIntent.UpdateCategoryField("name", "Utilities"))

        viewModel.effects.test {
            viewModel.dispatch(ExpenseIntent.SaveCategory)
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is ExpenseEffect.ShowSuccess)
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(1, categoriesFlow.value.size)
        assertEquals("Utilities", categoriesFlow.value.first().name)
    }

    // ── DismissMessage ────────────────────────────────────────────────────────

    @Test
    fun `DismissMessage clears error and successMessage`() = runTest {
        viewModel.dispatch(ExpenseIntent.DismissMessage)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.error)
        assertNull(viewModel.state.value.successMessage)
    }

    // ── SelectExpense ─────────────────────────────────────────────────────────

    @Test
    fun `SelectExpense null resets form and emits NavigateToDetail with null`() = runTest {
        viewModel.effects.test {
            viewModel.dispatch(ExpenseIntent.SelectExpense(null))
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is ExpenseEffect.NavigateToDetail)
            assertNull((effect as ExpenseEffect.NavigateToDetail).expenseId)
            cancelAndIgnoreRemainingEvents()
        }

        assertFalse(viewModel.state.value.expenseForm.isEditing)
        assertNull(viewModel.state.value.selectedExpense)
    }

    @Test
    fun `SelectExpense with existing id loads expense into form`() = runTest {
        expensesFlow.value = listOf(testExpense)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            viewModel.dispatch(ExpenseIntent.SelectExpense(testExpense.id))
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is ExpenseEffect.NavigateToDetail)
            assertEquals(testExpense.id, (effect as ExpenseEffect.NavigateToDetail).expenseId)
            cancelAndIgnoreRemainingEvents()
        }

        val form = viewModel.state.value.expenseForm
        assertTrue(form.isEditing)
        assertEquals(testExpense.description, form.description)
    }

    @Test
    fun `SelectExpense with non-existent id emits ShowError`() = runTest {
        viewModel.effects.test {
            viewModel.dispatch(ExpenseIntent.SelectExpense("does-not-exist"))
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is ExpenseEffect.ShowError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── DeleteCategory ────────────────────────────────────────────────────────

    @Test
    fun `DeleteCategory removes category from repository`() = runTest {
        val cat = ExpenseCategory(id = "cat-001", name = "Transport")
        categoriesFlow.value = listOf(cat)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dispatch(ExpenseIntent.DeleteCategory(cat.id))
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(categoriesFlow.value.isEmpty())
    }

    // ── DismissCategoryDetail ─────────────────────────────────────────────────

    @Test
    fun `DismissCategoryDetail clears showCategoryDetail and selected category`() = runTest {
        viewModel.dispatch(ExpenseIntent.DismissCategoryDetail)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.showCategoryDetail)
        assertNull(viewModel.state.value.selectedCategory)
    }

    // ── FilterByStatus(null) ──────────────────────────────────────────────────

    @Test
    fun `FilterByStatus null clears statusFilter`() = runTest {
        viewModel.dispatch(ExpenseIntent.FilterByStatus(Expense.Status.PENDING))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.dispatch(ExpenseIntent.FilterByStatus(null))
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.statusFilter)
    }

    // ── Budget — SetCategoryBudget ────────────────────────────────────────────

    @Test
    fun `SetCategoryBudget updates categoryBudgets and emits ShowSuccess`() = runTest {
        viewModel.effects.test {
            viewModel.dispatch(ExpenseIntent.SetCategoryBudget("cat-001", 500.0))
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is ExpenseEffect.ShowSuccess)
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(500.0, viewModel.state.value.categoryBudgets["cat-001"])
    }

    // ── Budget — UpdateApprovalThreshold ──────────────────────────────────────

    @Test
    fun `UpdateApprovalThreshold sets approvalThreshold and emits ShowSuccess`() = runTest {
        viewModel.effects.test {
            viewModel.dispatch(ExpenseIntent.UpdateApprovalThreshold(2000.0))
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is ExpenseEffect.ShowSuccess)
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(2000.0, viewModel.state.value.approvalThreshold)
    }

    // ── Recurring Expenses ────────────────────────────────────────────────────

    @Test
    fun `ShowRecurringDialog sets showRecurringDialog to true`() = runTest {
        viewModel.dispatch(ExpenseIntent.ShowRecurringDialog)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value.showRecurringDialog)
    }

    @Test
    fun `DismissRecurringDialog hides dialog and resets recurringForm`() = runTest {
        viewModel.dispatch(ExpenseIntent.ShowRecurringDialog)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.dispatch(ExpenseIntent.DismissRecurringDialog)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.showRecurringDialog)
    }

    @Test
    fun `UpdateRecurringField sets description in recurringForm`() = runTest {
        viewModel.dispatch(ExpenseIntent.UpdateRecurringField("description", "Monthly rent"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Monthly rent", viewModel.state.value.recurringForm.description)
    }

    @Test
    fun `SetRecurringFrequency updates frequency in recurringForm`() = runTest {
        viewModel.dispatch(ExpenseIntent.SetRecurringFrequency(RecurringFrequency.WEEKLY))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(RecurringFrequency.WEEKLY, viewModel.state.value.recurringForm.frequency)
    }

    @Test
    fun `SaveRecurringExpense with blank description sets validationError`() = runTest {
        // description left blank — should fail validation
        viewModel.dispatch(ExpenseIntent.UpdateRecurringField("amount", "500"))
        viewModel.dispatch(ExpenseIntent.SaveRecurringExpense)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.recurringForm.validationErrors["description"])
        assertFalse(viewModel.state.value.successMessage != null)
    }

    @Test
    fun `SaveRecurringExpense with zero amount sets validationError`() = runTest {
        viewModel.dispatch(ExpenseIntent.UpdateRecurringField("description", "Office cleaning"))
        viewModel.dispatch(ExpenseIntent.UpdateRecurringField("amount", "0"))
        viewModel.dispatch(ExpenseIntent.SaveRecurringExpense)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.recurringForm.validationErrors["amount"])
    }

    @Test
    fun `SaveRecurringExpense with valid fields sets successMessage and hides dialog`() = runTest {
        viewModel.dispatch(ExpenseIntent.ShowRecurringDialog)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.dispatch(ExpenseIntent.UpdateRecurringField("description", "Office cleaning"))
        viewModel.dispatch(ExpenseIntent.UpdateRecurringField("amount", "200.0"))
        viewModel.dispatch(ExpenseIntent.SaveRecurringExpense)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.showRecurringDialog)
        assertNotNull(viewModel.state.value.successMessage)
    }
}
