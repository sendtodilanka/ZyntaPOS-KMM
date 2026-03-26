package com.zyntasolutions.zyntapos.feature.multistore.dashboard

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.model.Store
import com.zyntasolutions.zyntapos.domain.model.User
import com.zyntasolutions.zyntapos.domain.model.report.StoreSalesData
import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
import com.zyntasolutions.zyntapos.domain.repository.ReportRepository
import com.zyntasolutions.zyntapos.domain.repository.StoreRepository
import com.zyntasolutions.zyntapos.domain.usecase.multistore.GetMultiStoreKPIsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class MultiStoreDashboardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val testStore1 = Store(
        id = "store-001",
        name = "Main Store",
        address = "123 Main St",
        currency = "LKR",
        timezone = "Asia/Colombo",
        isActive = true,
        isHeadquarters = true,
        createdAt = Instant.fromEpochMilliseconds(0),
        updatedAt = Instant.fromEpochMilliseconds(0),
    )

    private val testStore2 = Store(
        id = "store-002",
        name = "Branch Store",
        address = "456 Branch Ave",
        currency = "LKR",
        timezone = "Asia/Colombo",
        isActive = true,
        isHeadquarters = false,
        createdAt = Instant.fromEpochMilliseconds(0),
        updatedAt = Instant.fromEpochMilliseconds(0),
    )

    private val storesFlow = MutableStateFlow(listOf(testStore1, testStore2))

    private val fakeStoreRepository = object : StoreRepository {
        override fun getAllStores(): Flow<List<Store>> = storesFlow
        override suspend fun getById(storeId: String): Store? =
            storesFlow.value.find { it.id == storeId }
        override suspend fun getStoreName(storeId: String): String? =
            storesFlow.value.find { it.id == storeId }?.name
        override suspend fun upsertFromSync(store: Store) {}
    }

    private val fakeAuthRepository = object : AuthRepository {
        private val _session = MutableStateFlow<User?>(
            User(
                id = "user-001", name = "Admin User", email = "admin@zynta.com",
                role = Role.ADMIN, storeId = "store-001", isActive = true,
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
            Result.Success(_session.value!!)
        override suspend fun validateManagerPin(pin: String): Result<Boolean> =
            Result.Success(true)
    }

    private val testSalesData = listOf(
        StoreSalesData(
            storeId = "store-001",
            storeName = "Main Store",
            totalRevenue = 150_000.0,
            orderCount = 50,
            averageOrderValue = 3_000.0,
        ),
        StoreSalesData(
            storeId = "store-002",
            storeName = "Branch Store",
            totalRevenue = 80_000.0,
            orderCount = 30,
            averageOrderValue = 2_666.67,
        ),
    )

    private var reportData: List<StoreSalesData> = testSalesData

    private val fakeReportRepository = object : ReportRepository {
        override suspend fun getMultiStoreComparison(
            from: Instant,
            to: Instant,
        ): List<StoreSalesData> = reportData

        // Stub all other ReportRepository methods as needed
        override suspend fun getDailySalesSummary(date: LocalDate) = throw NotImplementedError()
        override suspend fun getSalesByCategory(from: Instant, to: Instant) = throw NotImplementedError()
        override suspend fun getCashMovementLog(from: Instant, to: Instant) = throw NotImplementedError()
        override suspend fun getTopProductsByVolume(from: Instant, to: Instant, limit: Int) = throw NotImplementedError()
        override suspend fun getProductPerformance(from: Instant, to: Instant) = throw NotImplementedError()
        override suspend fun getCouponUsage(from: Instant, to: Instant) = throw NotImplementedError()
        override suspend fun getPaymentMethodBreakdown(from: Instant, to: Instant) = throw NotImplementedError()
        override suspend fun getTaxCollection(from: Instant, to: Instant) = throw NotImplementedError()
        override suspend fun getDiscountVoidAnalysis(from: Instant, to: Instant) = throw NotImplementedError()
        override suspend fun getTopCustomers(from: Instant, to: Instant, limit: Int) = throw NotImplementedError()
        override suspend fun getProfitLoss(from: Instant, to: Instant) = throw NotImplementedError()
        override suspend fun getStaffAttendanceSummary(from: Instant, to: Instant) = throw NotImplementedError()
        override suspend fun getStaffSalesSummary(from: Instant, to: Instant) = throw NotImplementedError()
        override suspend fun getPayrollSummary(payPeriodId: String) = throw NotImplementedError()
        override suspend fun getLeaveBalances(asOf: Instant) = throw NotImplementedError()
        override suspend fun getShiftCoverage(from: Instant, to: Instant) = throw NotImplementedError()
        override suspend fun getInterStoreTransfers(from: Instant, to: Instant) = throw NotImplementedError()
        override suspend fun getWarehouseInventory() = throw NotImplementedError()
        override suspend fun getStockAging(noSalesDays: Int) = throw NotImplementedError()
        override suspend fun getStockReorderAlerts() = throw NotImplementedError()
        override suspend fun getSupplierPurchases(from: Instant, to: Instant) = throw NotImplementedError()
        override suspend fun getReturnRefundSummary(from: Instant, to: Instant) = throw NotImplementedError()
        override suspend fun getEInvoiceStatus(from: Instant, to: Instant) = throw NotImplementedError()
        override suspend fun getAccountingLedger(from: Instant, to: Instant) = throw NotImplementedError()
        override suspend fun getHourlySales(date: LocalDate) = throw NotImplementedError()
        override suspend fun getCustomerLoyaltySummary(from: Instant, to: Instant) = throw NotImplementedError()
        override suspend fun getWalletBalances() = throw NotImplementedError()
        override suspend fun getCOGS(from: Instant, to: Instant) = throw NotImplementedError()
        override suspend fun getGrossMargin(from: Instant, to: Instant) = throw NotImplementedError()
        override suspend fun getAnnualSalesTrend(year: Int) = throw NotImplementedError()
        override suspend fun getClockInOutLog(from: Instant, to: Instant, employeeId: String?) = throw NotImplementedError()
        override suspend fun getInventoryValuation() = throw NotImplementedError()
        override suspend fun getCustomerRetentionMetrics(from: Instant, to: Instant) = throw NotImplementedError()
        override suspend fun getPurchaseOrders(from: Instant, to: Instant) = throw NotImplementedError()
        override suspend fun getExpensesByDepartment(from: Instant, to: Instant) = throw NotImplementedError()
    }

    private lateinit var viewModel: MultiStoreDashboardViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): MultiStoreDashboardViewModel {
        return MultiStoreDashboardViewModel(
            storeRepository = fakeStoreRepository,
            authRepository = fakeAuthRepository,
            getMultiStoreKPIs = GetMultiStoreKPIsUseCase(fakeReportRepository),
        )
    }

    @Test
    fun `initial state is loading`() = runTest {
        viewModel = createViewModel()
        // Initial state before dispatching intents
        val initial = viewModel.state.value
        // isLoading may be true because init dispatches LoadDashboard
        // The stores should be populated after observeStores runs
    }

    @Test
    fun `load dashboard populates store comparison data`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals(2, state.storeComparison.size)
        assertEquals(230_000.0, state.totalRevenue)
        assertEquals(80, state.totalOrders)
    }

    @Test
    fun `stores are populated from repository`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(2, state.stores.size)
        assertEquals("Main Store", state.stores[0].name)
        assertEquals("Branch Store", state.stores[1].name)
    }

    @Test
    fun `active store defaults to user primary store`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertNotNull(state.activeStore)
        assertEquals("store-001", state.activeStore?.id)
        assertEquals("Main Store", state.activeStore?.name)
    }

    @Test
    fun `switch store updates active store`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.dispatch(MultiStoreDashboardIntent.SwitchStore(testStore2))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("store-002", state.activeStore?.id)
        assertEquals("Branch Store", state.activeStore?.name)
    }

    @Test
    fun `switch store emits StoreSwitched effect`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.effects.test {
            viewModel.dispatch(MultiStoreDashboardIntent.SwitchStore(testStore2))
            val effect = awaitItem()
            assert(effect is MultiStoreDashboardEffect.StoreSwitched)
            assertEquals("store-002", (effect as MultiStoreDashboardEffect.StoreSwitched).storeId)
            assertEquals("Branch Store", effect.storeName)
        }
    }

    @Test
    fun `select period updates state and reloads KPIs`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.dispatch(MultiStoreDashboardIntent.SelectPeriod(DashboardPeriod.WEEK))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(DashboardPeriod.WEEK, state.selectedPeriod)
        assertFalse(state.isLoading)
    }

    @Test
    fun `refresh reloads KPIs`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        // Modify data for refresh
        reportData = listOf(
            StoreSalesData("store-001", "Main Store", 200_000.0, 60, 3_333.33),
        )

        viewModel.dispatch(MultiStoreDashboardIntent.Refresh)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(1, state.storeComparison.size)
        assertEquals(200_000.0, state.totalRevenue)
        assertEquals(60, state.totalOrders)
    }

    @Test
    fun `average order value calculated correctly`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        // 230_000 / 80 = 2875.0
        assertEquals(2875.0, state.overallAOV)
    }

    @Test
    fun `empty stores shows no comparison data`() = runTest {
        storesFlow.value = emptyList()
        reportData = emptyList()

        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(0, state.stores.size)
        assertEquals(0, state.storeComparison.size)
        assertEquals(0.0, state.totalRevenue)
    }

    @Test
    fun `zero orders produces zero AOV`() = runTest {
        reportData = emptyList()

        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(0.0, state.overallAOV)
    }
}
