package com.zyntasolutions.zyntapos.feature.customers

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.Customer
import com.zyntasolutions.zyntapos.domain.model.CustomerGroup
import com.zyntasolutions.zyntapos.domain.model.CustomerWallet
import com.zyntasolutions.zyntapos.domain.model.DiscountType
import com.zyntasolutions.zyntapos.domain.model.LoyaltyTier
import com.zyntasolutions.zyntapos.domain.model.RewardPoints
import com.zyntasolutions.zyntapos.domain.model.WalletTransaction
import com.zyntasolutions.zyntapos.domain.repository.CustomerGroupRepository
import com.zyntasolutions.zyntapos.domain.repository.CustomerRepository
import com.zyntasolutions.zyntapos.domain.repository.CustomerWalletRepository
import com.zyntasolutions.zyntapos.domain.repository.LoyaltyRepository
import com.zyntasolutions.zyntapos.domain.model.Order
import com.zyntasolutions.zyntapos.domain.repository.OrderRepository
import com.zyntasolutions.zyntapos.domain.usecase.crm.ExportCustomerDataUseCase
import com.zyntasolutions.zyntapos.domain.usecase.crm.GetCustomerPurchaseHistoryUseCase
import com.zyntasolutions.zyntapos.domain.usecase.crm.MergeCustomersUseCase
import com.zyntasolutions.zyntapos.domain.usecase.crm.SaveCustomerGroupUseCase
import com.zyntasolutions.zyntapos.domain.usecase.crm.WalletTopUpUseCase
import kotlinx.coroutines.flow.flowOf
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
import com.zyntasolutions.zyntapos.core.analytics.AnalyticsTracker
import kotlinx.datetime.Instant

// ─────────────────────────────────────────────────────────────────────────────
// CustomerViewModelTest
// Tests CustomerViewModel MVI state transitions using hand-rolled fakes.
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class CustomerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val currentUserId = "user-001"

    private val noOpAnalytics = object : AnalyticsTracker {
        override fun logEvent(name: String, params: Map<String, String>) = Unit
        override fun logScreenView(screenName: String, screenClass: String) = Unit
        override fun setUserId(userId: String?) = Unit
        override fun setUserProperty(name: String, value: String) = Unit
    }

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
    }

    // ── Fake CustomerRepository ───────────────────────────────────────────────

    private val customersFlow = MutableStateFlow<List<Customer>>(emptyList())
    private var shouldFailInsert = false
    private var shouldFailDelete = false

    private val fakeCustomerRepository = object : CustomerRepository {
        override fun getAll(): Flow<List<Customer>> = customersFlow

        override suspend fun getById(id: String): Result<Customer> {
            val customer = customersFlow.value.firstOrNull { it.id == id }
                ?: return Result.Error(DatabaseException("Customer '$id' not found"))
            return Result.Success(customer)
        }

        override fun search(query: String): Flow<List<Customer>> =
            customersFlow.map { list ->
                list.filter {
                    it.name.contains(query, ignoreCase = true) ||
                        it.phone.contains(query)
                }
            }

        override suspend fun insert(customer: Customer): Result<Unit> {
            if (shouldFailInsert) return Result.Error(DatabaseException("Insert failed"))
            customersFlow.value = customersFlow.value + customer
            return Result.Success(Unit)
        }

        override suspend fun update(customer: Customer): Result<Unit> {
            val idx = customersFlow.value.indexOfFirst { it.id == customer.id }
            if (idx == -1) return Result.Error(DatabaseException("Not found"))
            val updated = customersFlow.value.toMutableList().also { it[idx] = customer }
            customersFlow.value = updated
            return Result.Success(Unit)
        }

        override suspend fun delete(id: String): Result<Unit> {
            if (shouldFailDelete) return Result.Error(DatabaseException("Delete failed"))
            customersFlow.value = customersFlow.value.filter { it.id != id }
            return Result.Success(Unit)
        }
        override fun searchGlobal(query: String): Flow<List<Customer>> = search(query)
        override fun getByStore(storeId: String): Flow<List<Customer>> =
            customersFlow.map { list -> list.filter { it.storeId == storeId } }
        override fun getGlobalCustomers(): Flow<List<Customer>> =
            customersFlow.map { list -> list.filter { it.storeId == null } }
        override suspend fun makeGlobal(customerId: String): Result<Unit> {
            val idx = customersFlow.value.indexOfFirst { it.id == customerId }
            if (idx == -1) return Result.Error(DatabaseException("Not found"))
            val updated = customersFlow.value.toMutableList().also { it[idx] = it[idx].copy(storeId = null) }
            customersFlow.value = updated
            return Result.Success(Unit)
        }
        override suspend fun updateLoyaltyPoints(customerId: String, points: Int): Result<Unit> {
            val idx = customersFlow.value.indexOfFirst { it.id == customerId }
            if (idx == -1) return Result.Error(DatabaseException("Not found"))
            val updated = customersFlow.value.toMutableList().also { it[idx] = it[idx].copy(loyaltyPoints = points) }
            customersFlow.value = updated
            return Result.Success(Unit)
        }
    }

    // ── Fake CustomerGroupRepository ──────────────────────────────────────────

    private val groupsFlow = MutableStateFlow<List<CustomerGroup>>(emptyList())

    private val fakeGroupRepository = object : CustomerGroupRepository {
        override fun getAll(): Flow<List<CustomerGroup>> = groupsFlow

        override suspend fun getById(id: String): Result<CustomerGroup> {
            val group = groupsFlow.value.firstOrNull { it.id == id }
                ?: return Result.Error(DatabaseException("Group not found"))
            return Result.Success(group)
        }

        override suspend fun insert(group: CustomerGroup): Result<Unit> {
            groupsFlow.value = groupsFlow.value + group
            return Result.Success(Unit)
        }

        override suspend fun update(group: CustomerGroup): Result<Unit> = Result.Success(Unit)

        override suspend fun delete(id: String): Result<Unit> {
            groupsFlow.value = groupsFlow.value.filter { it.id != id }
            return Result.Success(Unit)
        }
    }

    // ── Fake CustomerWalletRepository ─────────────────────────────────────────

    private val fakeWalletRepository = object : CustomerWalletRepository {
        private val wallets = mutableMapOf<String, CustomerWallet>()

        override suspend fun getOrCreate(customerId: String): Result<CustomerWallet> {
            val wallet = wallets.getOrPut(customerId) {
                CustomerWallet(id = "wallet-$customerId", customerId = customerId, balance = 0.0)
            }
            return Result.Success(wallet)
        }

        override fun observeWallet(customerId: String): Flow<CustomerWallet?> =
            MutableStateFlow(wallets[customerId])

        override fun getTransactions(walletId: String): Flow<List<WalletTransaction>> =
            MutableStateFlow(emptyList())

        override suspend fun credit(
            walletId: String,
            amount: Double,
            referenceType: String?,
            referenceId: String?,
            note: String?,
        ): Result<Unit> = Result.Success(Unit)

        override suspend fun debit(
            walletId: String,
            amount: Double,
            referenceType: String?,
            referenceId: String?,
            note: String?,
        ): Result<Unit> = Result.Success(Unit)
    }

    // ── Fake LoyaltyRepository ────────────────────────────────────────────────

    private var fakePointsBalance = 0
    private var fakeTierForPoints: LoyaltyTier? = null

    private val fakeLoyaltyRepository = object : LoyaltyRepository {
        override fun getPointsHistory(customerId: String): Flow<List<RewardPoints>> =
            MutableStateFlow(emptyList())

        override suspend fun getBalance(customerId: String): Result<Int> =
            Result.Success(fakePointsBalance)

        override suspend fun recordPoints(entry: RewardPoints): Result<Unit> =
            Result.Success(Unit)

        override fun getAllTiers(): Flow<List<LoyaltyTier>> =
            MutableStateFlow(emptyList())

        override suspend fun getTierForPoints(points: Int): Result<LoyaltyTier?> =
            Result.Success(fakeTierForPoints)

        override suspend fun saveTier(tier: LoyaltyTier): Result<Unit> =
            Result.Success(Unit)

        override suspend fun deleteTier(id: String): Result<Unit> =
            Result.Success(Unit)
    }

    private val saveGroupUseCase = SaveCustomerGroupUseCase(fakeGroupRepository)
    private val walletTopUpUseCase = WalletTopUpUseCase(fakeWalletRepository)
    private val fakeOrderRepository = object : OrderRepository {
        override suspend fun create(order: Order): Result<Order> = error("not needed")
        override suspend fun getById(id: String): Result<Order> = error("not needed")
        override fun getAll(filters: Map<String, String>): Flow<List<Order>> = flowOf(emptyList())
        override suspend fun update(order: Order): Result<Unit> = error("not needed")
        override suspend fun void(id: String, reason: String): Result<Unit> = error("not needed")
        override fun getByDateRange(from: kotlinx.datetime.Instant, to: kotlinx.datetime.Instant): Flow<List<Order>> = flowOf(emptyList())
        override suspend fun holdOrder(cart: List<com.zyntasolutions.zyntapos.domain.model.CartItem>): Result<String> = error("not needed")
        override suspend fun retrieveHeld(holdId: String): Result<Order> = error("not needed")
    }
    private val exportCustomerDataUseCase = ExportCustomerDataUseCase(fakeCustomerRepository, fakeOrderRepository)
    private val mergeCustomersUseCase = MergeCustomersUseCase(fakeCustomerRepository, fakeWalletRepository, fakeLoyaltyRepository)
    private val getPurchaseHistoryUseCase = GetCustomerPurchaseHistoryUseCase(fakeOrderRepository)

    private lateinit var viewModel: CustomerViewModel

    private val testCustomer = Customer(
        id = "cust-001",
        name = "Alice Smith",
        phone = "0771234567",
        email = "alice@example.com",
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        customersFlow.value = emptyList()
        groupsFlow.value = emptyList()
        shouldFailInsert = false
        shouldFailDelete = false
        fakePointsBalance = 0
        fakeTierForPoints = null
        viewModel = CustomerViewModel(
            customerRepository = fakeCustomerRepository,
            groupRepository = fakeGroupRepository,
            walletRepository = fakeWalletRepository,
            loyaltyRepository = fakeLoyaltyRepository,
            saveGroupUseCase = saveGroupUseCase,
            walletTopUpUseCase = walletTopUpUseCase,
            exportCustomerDataUseCase = exportCustomerDataUseCase,
            mergeCustomersUseCase = mergeCustomersUseCase,
            getPurchaseHistoryUseCase = getPurchaseHistoryUseCase,
            authRepository = fakeAuthRepository,
            analytics = noOpAnalytics,
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    fun `initial state has empty customer list and not loading`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.state.value
        assertTrue(state.customers.isEmpty())
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertNull(state.currentLoyaltyTier)
    }

    // ── Observable list ───────────────────────────────────────────────────────

    @Test
    fun `adding customers to repository reflects in state`() = runTest {
        customersFlow.value = listOf(testCustomer)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.state.value.customers.size)
        assertEquals(testCustomer.name, viewModel.state.value.customers.first().name)
    }

    // ── SaveCustomer — validation ─────────────────────────────────────────────

    @Test
    fun `SaveCustomer with blank name sets validation error and does not persist`() = runTest {
        // phone set, name blank
        viewModel.dispatch(CustomerIntent.UpdateFormField("phone", "0771111111"))
        viewModel.dispatch(CustomerIntent.SaveCustomer)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.editFormState.validationErrors["name"])
        assertTrue(customersFlow.value.isEmpty())
    }

    @Test
    fun `SaveCustomer with blank phone sets validation error`() = runTest {
        viewModel.dispatch(CustomerIntent.UpdateFormField("name", "Bob Jones"))
        // phone left blank
        viewModel.dispatch(CustomerIntent.SaveCustomer)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.editFormState.validationErrors["phone"])
    }

    // ── SaveCustomer — success ────────────────────────────────────────────────

    @Test
    fun `SaveCustomer with valid form creates customer and emits ShowSuccess`() = runTest {
        viewModel.dispatch(CustomerIntent.UpdateFormField("name", "Bob Jones"))
        viewModel.dispatch(CustomerIntent.UpdateFormField("phone", "0779876543"))
        viewModel.dispatch(CustomerIntent.UpdateFormField("creditLimit", "0"))

        viewModel.effects.test {
            viewModel.dispatch(CustomerIntent.SaveCustomer)
            testDispatcher.scheduler.advanceUntilIdle()

            val effect1 = awaitItem()
            assertTrue(effect1 is CustomerEffect.ShowSuccess)
            val effect2 = awaitItem()
            assertTrue(effect2 is CustomerEffect.NavigateToList)
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(1, customersFlow.value.size)
        assertEquals("Bob Jones", customersFlow.value.first().name)
    }

    // ── DeleteCustomer ────────────────────────────────────────────────────────

    @Test
    fun `DeleteCustomer removes customer and emits ShowSuccess`() = runTest {
        customersFlow.value = listOf(testCustomer)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            viewModel.dispatch(CustomerIntent.DeleteCustomer(testCustomer.id))
            testDispatcher.scheduler.advanceUntilIdle()

            val effect1 = awaitItem()
            assertTrue(effect1 is CustomerEffect.ShowSuccess)
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(customersFlow.value.isEmpty())
    }

    @Test
    fun `DeleteCustomer on failure emits ShowError`() = runTest {
        shouldFailDelete = true
        customersFlow.value = listOf(testCustomer)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            viewModel.dispatch(CustomerIntent.DeleteCustomer(testCustomer.id))
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is CustomerEffect.ShowError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── SearchQueryChanged ────────────────────────────────────────────────────

    @Test
    fun `SearchQueryChanged updates searchQuery in state`() = runTest {
        viewModel.dispatch(CustomerIntent.SearchQueryChanged("Alice"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Alice", viewModel.state.value.searchQuery)
    }

    // ── DismissMessage ────────────────────────────────────────────────────────

    @Test
    fun `DismissMessage clears error in state`() = runTest {
        viewModel.dispatch(CustomerIntent.DismissMessage)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.error)
        assertNull(viewModel.state.value.successMessage)
    }

    // ── LoadWallet — loyalty tier ──────────────────────────────────────────────

    @Test
    fun `LoadWallet sets currentLoyaltyTier when tier exists for points balance`() = runTest {
        val goldTier = LoyaltyTier(
            id = "tier-gold",
            name = "Gold",
            minPoints = 500,
            discountPercent = 10.0,
            pointsMultiplier = 2.0,
        )
        fakePointsBalance = 600
        fakeTierForPoints = goldTier

        viewModel.dispatch(CustomerIntent.LoadWallet("cust-001"))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(600, state.pointsBalance)
        assertNotNull(state.currentLoyaltyTier)
        assertEquals("Gold", state.currentLoyaltyTier?.name)
        assertEquals(goldTier, state.currentLoyaltyTier)
    }

    @Test
    fun `LoadWallet sets currentLoyaltyTier to null when no tier qualifies`() = runTest {
        fakePointsBalance = 0
        fakeTierForPoints = null

        viewModel.dispatch(CustomerIntent.LoadWallet("cust-001"))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(0, state.pointsBalance)
        assertNull(state.currentLoyaltyTier)
    }
}
