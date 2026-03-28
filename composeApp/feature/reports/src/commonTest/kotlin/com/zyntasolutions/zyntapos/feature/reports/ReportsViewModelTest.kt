package com.zyntasolutions.zyntapos.feature.reports

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.Customer
import com.zyntasolutions.zyntapos.domain.model.Expense
import com.zyntasolutions.zyntapos.domain.model.ExpenseCategory
import com.zyntasolutions.zyntapos.domain.model.Order
import com.zyntasolutions.zyntapos.domain.model.OrderItem
import com.zyntasolutions.zyntapos.domain.model.OrderStatus
import com.zyntasolutions.zyntapos.domain.model.OrderType
import com.zyntasolutions.zyntapos.domain.model.PaymentMethod
import com.zyntasolutions.zyntapos.domain.model.SyncStatus
import com.zyntasolutions.zyntapos.domain.model.Product
import com.zyntasolutions.zyntapos.domain.model.RecurringExpense
import com.zyntasolutions.zyntapos.domain.model.StockAdjustment
import com.zyntasolutions.zyntapos.domain.printer.ReportPrinterPort
import com.zyntasolutions.zyntapos.domain.repository.CustomerRepository
import com.zyntasolutions.zyntapos.domain.repository.ExpenseRepository
import com.zyntasolutions.zyntapos.domain.repository.OrderRepository
import com.zyntasolutions.zyntapos.domain.repository.ProductRepository
import com.zyntasolutions.zyntapos.domain.repository.StockRepository
import com.zyntasolutions.zyntapos.domain.usecase.reports.GenerateCustomerReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.reports.GenerateExpenseReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.reports.GenerateSalesReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.reports.GenerateStockReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.reports.PrintReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise.GenerateMultiStoreComparisonReportUseCase
import com.zyntasolutions.zyntapos.domain.model.report.StoreSalesData
import com.zyntasolutions.zyntapos.domain.repository.ReportRepository
import kotlinx.datetime.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.time.Clock
import kotlinx.datetime.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import com.zyntasolutions.zyntapos.core.analytics.AnalyticsTracker
import com.zyntasolutions.zyntapos.domain.port.SyncStatusPort
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.test.assertTrue

// ─────────────────────────────────────────────────────────────────────────────
// ReportsViewModelTest
// Tests ReportsViewModel MVI state transitions using hand-rolled fakes.
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val noOpAnalytics = object : AnalyticsTracker {
        override fun logEvent(name: String, params: Map<String, String>) = Unit
        override fun logScreenView(screenName: String, screenClass: String) = Unit
        override fun setUserId(userId: String?) = Unit
        override fun setUserProperty(name: String, value: String) = Unit
    }

    private val fakeSyncStatusPort = object : SyncStatusPort {
        private val _isSyncing = MutableStateFlow(false)
        private val _isNetworkConnected = MutableStateFlow(true)
        private val _lastSyncFailed = MutableStateFlow(false)
        private val _pendingCount = MutableStateFlow(0)
        private val _newConflictCount = MutableSharedFlow<Int>()
        private val _onSyncComplete = MutableSharedFlow<Unit>()
        override val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()
        override val isNetworkConnected: StateFlow<Boolean> = _isNetworkConnected.asStateFlow()
        override val lastSyncFailed: StateFlow<Boolean> = _lastSyncFailed.asStateFlow()
        override val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()
        override val newConflictCount: SharedFlow<Int> = _newConflictCount.asSharedFlow()
        override val onSyncComplete: SharedFlow<Unit> = _onSyncComplete.asSharedFlow()
    }

    // ── Test fixtures ─────────────────────────────────────────────────────────

    private val now = Clock.System.now()

    private val testOrder = Order(
        id                = "order-001",
        orderNumber       = "ORD-001",
        type              = OrderType.SALE,
        status            = OrderStatus.COMPLETED,
        items             = listOf(
            OrderItem(
                id          = "item-001",
                orderId     = "order-001",
                productId   = "prod-001",
                productName = "Coffee",
                quantity    = 2.0,
                unitPrice   = 5.0,
                lineTotal   = 10.0,
            )
        ),
        subtotal          = 10.0,
        taxAmount         = 0.0,
        discountAmount    = 0.0,
        total             = 10.0,
        paymentMethod     = PaymentMethod.CASH,
        amountTendered    = 10.0,
        changeAmount      = 0.0,
        cashierId         = "user-001",
        storeId           = "store-001",
        registerSessionId = "sess-001",
        createdAt         = now,
        updatedAt         = now,
        syncStatus        = SyncStatus.synced(),
    )

    private val testProduct = Product(
        id         = "prod-001",
        name       = "Coffee",
        categoryId = "cat-001",
        unitId     = "unit-001",
        price      = 5.0,
        stockQty   = 50.0,
        minStockQty = 10.0,
        isActive   = true,
        createdAt  = now,
        updatedAt  = now,
    )

    private val lowStockProduct = Product(
        id         = "prod-002",
        name       = "Milk",
        categoryId = "cat-001",
        unitId     = "unit-001",
        price      = 2.0,
        stockQty   = 2.0,
        minStockQty = 10.0,
        isActive   = true,
        createdAt  = now,
        updatedAt  = now,
    )

    private val testCustomer = Customer(
        id           = "cust-001",
        name         = "Alice",
        phone        = "+94771234567",
        loyaltyPoints = 100,
        isWalkIn     = false,
        creditEnabled = true,
    )

    private val walkInCustomer = Customer(
        id    = "cust-002",
        name  = "Walk-In",
        phone = "+94770000000",
        isWalkIn = true,
    )

    private val testExpense = Expense(
        id          = "exp-001",
        amount      = 150.0,
        description = "Office supplies",
        expenseDate = now.toEpochMilliseconds(),
        status      = Expense.Status.APPROVED,
    )

    // ── Mutable flows (test control) ──────────────────────────────────────────

    private val ordersFlow    = MutableStateFlow<List<Order>>(emptyList())
    private val productsFlow  = MutableStateFlow<List<Product>>(emptyList())
    private val customersFlow = MutableStateFlow<List<Customer>>(emptyList())
    private val expensesFlow  = MutableStateFlow<List<Expense>>(emptyList())

    // ── Fake flags ────────────────────────────────────────────────────────────

    private var shouldFailPrint = false
    private var shouldFailExport = false
    private var exportedPath = "/dev/null/report.csv"

    // ── Fake OrderRepository ──────────────────────────────────────────────────

    private val fakeOrderRepository = object : OrderRepository {
        override fun getByDateRange(from: Instant, to: Instant): Flow<List<Order>> = ordersFlow
        override fun getAll(filters: Map<String, String>): Flow<List<Order>> = ordersFlow
        override suspend fun create(order: Order): Result<Order> = error("not used in test")
        override suspend fun getById(id: String): Result<Order> = error("not used in test")
        override suspend fun update(order: Order): Result<Unit> = error("not used in test")
        override suspend fun void(id: String, reason: String): Result<Unit> = error("not used in test")
        override suspend fun holdOrder(cart: List<com.zyntasolutions.zyntapos.domain.model.CartItem>): Result<String> = error("not used in test")
        override suspend fun retrieveHeld(holdId: String): Result<Order> = error("not used in test")
        override suspend fun getPage(pageRequest: com.zyntasolutions.zyntapos.core.pagination.PageRequest, from: kotlinx.datetime.Instant?, to: kotlinx.datetime.Instant?, customerId: String?): com.zyntasolutions.zyntapos.core.pagination.PaginatedResult<Order> =
            com.zyntasolutions.zyntapos.core.pagination.PaginatedResult(items = emptyList(), totalCount = 0L, hasMore = false)
    }

    // ── Fake ProductRepository ────────────────────────────────────────────────

    private val fakeProductRepository = object : ProductRepository {
        override fun getAll(): Flow<List<Product>> = productsFlow
        override suspend fun getById(id: String): Result<Product> = error("not used in test")
        override fun search(query: String, categoryId: String?): Flow<List<Product>> = productsFlow
        override suspend fun insert(product: Product): Result<Unit> = error("not used in test")
        override suspend fun update(product: Product): Result<Unit> = error("not used in test")
        override suspend fun delete(id: String): Result<Unit> = error("not used in test")
        override suspend fun getByBarcode(barcode: String): Result<Product> = error("not used in test")
        override suspend fun getCount(): Int = 0
        override suspend fun getPage(pageRequest: com.zyntasolutions.zyntapos.core.pagination.PageRequest, categoryId: String?, searchQuery: String?): com.zyntasolutions.zyntapos.core.pagination.PaginatedResult<Product> =
            com.zyntasolutions.zyntapos.core.pagination.PaginatedResult(items = emptyList(), totalCount = 0L, hasMore = false)
    }

    // ── Fake StockRepository ──────────────────────────────────────────────────

    private val fakeStockRepository = object : StockRepository {
        override suspend fun adjustStock(adjustment: StockAdjustment): Result<Unit> = error("not used in test")
        override fun getMovements(productId: String): Flow<List<StockAdjustment>> = flowOf(emptyList())
        override fun getAlerts(threshold: Double?): Flow<List<Product>> = productsFlow
    }

    // ── Fake CustomerRepository ───────────────────────────────────────────────

    private val fakeCustomerRepository = object : CustomerRepository {
        override fun getAll(): Flow<List<Customer>> = customersFlow
        override suspend fun getById(id: String): Result<Customer> = error("not used in test")
        override suspend fun insert(customer: Customer): Result<Unit> = error("not used in test")
        override suspend fun update(customer: Customer): Result<Unit> = error("not used in test")
        override suspend fun delete(id: String): Result<Unit> = error("not used in test")
        override fun search(query: String): Flow<List<Customer>> = flowOf(emptyList())
        override fun searchGlobal(query: String): Flow<List<Customer>> = flowOf(emptyList())
        override fun getByStore(storeId: String): Flow<List<Customer>> = flowOf(emptyList())
        override fun getGlobalCustomers(): Flow<List<Customer>> = flowOf(emptyList())
        override suspend fun makeGlobal(customerId: String): Result<Unit> = error("not used in test")
        override suspend fun updateLoyaltyPoints(customerId: String, points: Int): Result<Unit> = error("not used in test")
        override suspend fun getPage(pageRequest: com.zyntasolutions.zyntapos.core.pagination.PageRequest, searchQuery: String?): com.zyntasolutions.zyntapos.core.pagination.PaginatedResult<Customer> =
            com.zyntasolutions.zyntapos.core.pagination.PaginatedResult(items = emptyList(), totalCount = 0L, hasMore = false)
    }

    // ── Fake ExpenseRepository ────────────────────────────────────────────────

    private val fakeExpenseRepository = object : ExpenseRepository {
        override fun getAll(): Flow<List<Expense>> = expensesFlow
        override fun getByStatus(status: Expense.Status): Flow<List<Expense>> = flowOf(emptyList())
        override fun getByDateRange(from: Long, to: Long): Flow<List<Expense>> = expensesFlow
        override suspend fun getById(id: String): Result<Expense> = error("not used in test")
        override suspend fun getTotalByPeriod(from: Long, to: Long): Result<Double> = error("not used in test")
        override suspend fun insert(expense: Expense): Result<Unit> = error("not used in test")
        override suspend fun update(expense: Expense): Result<Unit> = error("not used in test")
        override suspend fun approve(id: String, approvedBy: String): Result<Unit> = error("not used in test")
        override suspend fun reject(id: String, rejectedBy: String, reason: String?): Result<Unit> = error("not used in test")
        override suspend fun delete(id: String): Result<Unit> = error("not used in test")
        override fun getAllCategories(): Flow<List<ExpenseCategory>> = flowOf(emptyList())
        override suspend fun getCategoryById(id: String): Result<ExpenseCategory> = error("not used in test")
        override suspend fun saveCategory(category: ExpenseCategory): Result<Unit> = error("not used in test")
        override suspend fun deleteCategory(id: String): Result<Unit> = error("not used in test")
        override fun getAllRecurring(): Flow<List<RecurringExpense>> = flowOf(emptyList())
        override suspend fun getActiveRecurring(): Result<List<RecurringExpense>> = error("not used in test")
        override suspend fun saveRecurring(recurring: RecurringExpense): Result<Unit> = error("not used in test")
        override suspend fun updateLastRun(id: String, lastRunMillis: Long): Result<Unit> = error("not used in test")
        override suspend fun deleteRecurring(id: String): Result<Unit> = error("not used in test")
    }

    // ── Fake ReportPrinterPort ────────────────────────────────────────────────

    private val fakePrinterPort = object : ReportPrinterPort {
        override suspend fun printSalesSummary(
            report: GenerateSalesReportUseCase.SalesReport,
        ): kotlin.Result<Unit> =
            if (shouldFailPrint) kotlin.Result.failure(Exception("Printer offline"))
            else kotlin.Result.success(Unit)
    }

    // ── Fake ReportRepository (for store comparison) ──────────────────────────

    private val fakeReportRepository = object : ReportRepository {
        override suspend fun getMultiStoreComparison(from: Instant, to: Instant) = emptyList<StoreSalesData>()
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

    // ── Fake ReportExporter ───────────────────────────────────────────────────

    private val fakeReportExporter = object : ReportExporter {
        override suspend fun exportSalesCsv(report: GenerateSalesReportUseCase.SalesReport): String {
            if (shouldFailExport) throw Exception("Export failed")
            return exportedPath
        }
        override suspend fun exportSalesPdf(report: GenerateSalesReportUseCase.SalesReport): String {
            if (shouldFailExport) throw Exception("Export failed")
            return exportedPath
        }
        override suspend fun exportStockCsv(report: GenerateStockReportUseCase.StockReport): String {
            if (shouldFailExport) throw Exception("Export failed")
            return exportedPath
        }
        override suspend fun exportStockPdf(report: GenerateStockReportUseCase.StockReport): String {
            if (shouldFailExport) throw Exception("Export failed")
            return exportedPath
        }
        override suspend fun exportCustomerCsv(report: GenerateCustomerReportUseCase.CustomerReport): String {
            if (shouldFailExport) throw Exception("Export failed")
            return exportedPath
        }
        override suspend fun exportExpenseCsv(report: GenerateExpenseReportUseCase.ExpenseReport): String {
            if (shouldFailExport) throw Exception("Export failed")
            return exportedPath
        }
        override suspend fun exportStoreComparisonCsv(stores: List<com.zyntasolutions.zyntapos.domain.model.report.StoreSalesData>): String = exportedPath
        override suspend fun exportGdprJson(customerId: String, json: String): String = exportedPath
        override suspend fun exportConsolidatedRevenueCsv(rows: List<StoreRevenueInBase>): String = exportedPath
    }

    // ── Fake StoreRepository ───────────────────────────────────────────────

    private val fakeStoreRepository = object : com.zyntasolutions.zyntapos.domain.repository.StoreRepository {
        override fun getAllStores(): Flow<List<com.zyntasolutions.zyntapos.domain.model.Store>> = flowOf(emptyList())
        override suspend fun getById(storeId: String): com.zyntasolutions.zyntapos.domain.model.Store? = null
        override suspend fun getStoreName(storeId: String): String? = null
        override suspend fun upsertFromSync(store: com.zyntasolutions.zyntapos.domain.model.Store) = Unit
    }

    // ── Fake SettingsRepository ─────────────────────────────────────────────

    private val fakeSettingsRepository = object : com.zyntasolutions.zyntapos.domain.repository.SettingsRepository {
        override suspend fun get(key: String): String? = null
        override suspend fun set(key: String, value: String): Result<Unit> = Result.Success(Unit)
        override suspend fun getAll(): Map<String, String> = emptyMap()
        override fun observe(key: String): Flow<String?> = flowOf(null)
    }

    // ── ViewModel construction ────────────────────────────────────────────────

    private lateinit var viewModel: ReportsViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        ordersFlow.value    = emptyList()
        productsFlow.value  = emptyList()
        customersFlow.value = emptyList()
        expensesFlow.value  = emptyList()
        shouldFailPrint     = false
        shouldFailExport    = false

        viewModel = ReportsViewModel(
            generateSalesReport    = GenerateSalesReportUseCase(fakeOrderRepository),
            generateStockReport    = GenerateStockReportUseCase(fakeProductRepository, fakeStockRepository),
            generateCustomerReport = GenerateCustomerReportUseCase(fakeCustomerRepository),
            generateExpenseReport  = GenerateExpenseReportUseCase(fakeExpenseRepository),
            printReport            = PrintReportUseCase(fakePrinterPort),
            reportExporter         = fakeReportExporter,
            generateStoreComparison = GenerateMultiStoreComparisonReportUseCase(fakeReportRepository),
            analytics              = noOpAnalytics,
            syncStatusPort         = fakeSyncStatusPort,
            storeRepository        = fakeStoreRepository,
            settingsRepository     = fakeSettingsRepository,
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    fun `initial state has no loaded reports and is not loading`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.state.value

        assertNull(state.salesReport.report)
        assertNull(state.customerReport.report)
        assertFalse(state.salesReport.isLoading)
        assertFalse(state.stockReport.isLoading)
        assertFalse(state.customerReport.isLoading)
        assertFalse(state.expenseReport.isLoading)
    }

    // ── Sales Report ──────────────────────────────────────────────────────────

    @Test
    fun `LoadSalesReport with empty orders produces zero-total report`() = runTest {
        ordersFlow.value = emptyList()

        viewModel.dispatch(ReportsIntent.LoadSalesReport)
        testDispatcher.scheduler.advanceUntilIdle()

        val salesState = viewModel.state.value.salesReport
        assertFalse(salesState.isLoading)
        assertNotNull(salesState.report)
        assertEquals(0.0, salesState.report!!.totalSales)
        assertEquals(0, salesState.report!!.orderCount)
        assertNull(salesState.error)
    }

    @Test
    fun `LoadSalesReport with completed orders calculates correct totals`() = runTest {
        ordersFlow.value = listOf(testOrder)

        viewModel.dispatch(ReportsIntent.LoadSalesReport)
        testDispatcher.scheduler.advanceUntilIdle()

        val report = viewModel.state.value.salesReport.report
        assertNotNull(report)
        assertEquals(10.0, report.totalSales)
        assertEquals(1, report.orderCount)
        assertEquals(10.0, report.avgOrderValue)
        assertTrue(report.topProducts.containsKey("prod-001"))
    }

    @Test
    fun `LoadSalesReport excludes non-completed orders`() = runTest {
        val voidedOrder = testOrder.copy(id = "order-002", status = OrderStatus.VOIDED)
        ordersFlow.value = listOf(testOrder, voidedOrder)

        viewModel.dispatch(ReportsIntent.LoadSalesReport)
        testDispatcher.scheduler.advanceUntilIdle()

        val report = viewModel.state.value.salesReport.report
        assertNotNull(report)
        assertEquals(1, report.orderCount)
    }

    @Test
    fun `SelectSalesRange updates selected range`() = runTest {
        viewModel.dispatch(ReportsIntent.SelectSalesRange(DateRange.THIS_WEEK))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(DateRange.THIS_WEEK, viewModel.state.value.salesReport.selectedRange)
    }

    @Test
    fun `DismissSalesError clears sales error state`() = runTest {
        viewModel.dispatch(ReportsIntent.DismissSalesError)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.salesReport.error)
    }

    // ── Stock Report ──────────────────────────────────────────────────────────

    @Test
    fun `LoadStockReport with products populates allProducts`() = runTest {
        productsFlow.value = listOf(testProduct, lowStockProduct)

        viewModel.dispatch(ReportsIntent.LoadStockReport)
        testDispatcher.scheduler.advanceUntilIdle()

        val stockState = viewModel.state.value.stockReport
        assertFalse(stockState.isLoading)
        assertEquals(2, stockState.allProducts.size)
    }

    @Test
    fun `LoadStockReport identifies low stock items`() = runTest {
        productsFlow.value = listOf(testProduct, lowStockProduct)

        viewModel.dispatch(ReportsIntent.LoadStockReport)
        testDispatcher.scheduler.advanceUntilIdle()

        val stockState = viewModel.state.value.stockReport
        assertEquals(1, stockState.lowStockItems.size)
        assertEquals("prod-002", stockState.lowStockItems.first().id)
    }

    @Test
    fun `LoadStockReport excludes inactive products`() = runTest {
        val inactiveProduct = testProduct.copy(id = "prod-003", isActive = false)
        productsFlow.value = listOf(testProduct, inactiveProduct)

        viewModel.dispatch(ReportsIntent.LoadStockReport)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.state.value.stockReport.allProducts.size)
    }

    @Test
    fun `DismissStockError clears stock error state`() = runTest {
        viewModel.dispatch(ReportsIntent.DismissStockError)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.stockReport.error)
    }

    @Test
    fun `FilterStockByCategory updates selected category`() = runTest {
        viewModel.dispatch(ReportsIntent.FilterStockByCategory("cat-001"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("cat-001", viewModel.state.value.stockReport.selectedCategory)
    }

    @Test
    fun `FilterStockByCategory with null clears selection`() = runTest {
        viewModel.dispatch(ReportsIntent.FilterStockByCategory("cat-001"))
        viewModel.dispatch(ReportsIntent.FilterStockByCategory(null))
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.stockReport.selectedCategory)
    }

    // ── Customer Report ───────────────────────────────────────────────────────

    @Test
    fun `LoadCustomerReport with customers populates report`() = runTest {
        customersFlow.value = listOf(testCustomer, walkInCustomer)

        viewModel.dispatch(ReportsIntent.LoadCustomerReport)
        testDispatcher.scheduler.advanceUntilIdle()

        val report = viewModel.state.value.customerReport.report
        assertNotNull(report)
        assertEquals(2, report.totalCustomers)
        assertEquals(1, report.registeredCustomers)
        assertEquals(1, report.walkInCustomers)
        assertEquals(1, report.creditEnabledCustomers)
        assertEquals(100L, report.totalLoyaltyPoints)
    }

    @Test
    fun `LoadCustomerReport with empty list produces zero report`() = runTest {
        customersFlow.value = emptyList()

        viewModel.dispatch(ReportsIntent.LoadCustomerReport)
        testDispatcher.scheduler.advanceUntilIdle()

        val report = viewModel.state.value.customerReport.report
        assertNotNull(report)
        assertEquals(0, report.totalCustomers)
        assertEquals(0L, report.totalLoyaltyPoints)
    }

    @Test
    fun `DismissCustomerError clears customer error state`() = runTest {
        viewModel.dispatch(ReportsIntent.DismissCustomerError)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.customerReport.error)
    }

    // ── Expense Report ────────────────────────────────────────────────────────

    @Test
    fun `LoadExpenseReport with approved expenses calculates correct totals`() = runTest {
        expensesFlow.value = listOf(testExpense)

        viewModel.dispatch(ReportsIntent.LoadExpenseReport)
        testDispatcher.scheduler.advanceUntilIdle()

        val report = viewModel.state.value.expenseReport.report
        assertNotNull(report)
        assertEquals(150.0, report.totalApproved)
        assertEquals(0.0, report.totalPending)
        assertEquals(1, report.approvedCount)
    }

    @Test
    fun `LoadExpenseReport separates approved, pending, and rejected expenses`() = runTest {
        val pending  = Expense(id = "exp-002", amount = 50.0, description = "Pending",  expenseDate = now.toEpochMilliseconds(), status = Expense.Status.PENDING)
        val rejected = Expense(id = "exp-003", amount = 30.0, description = "Rejected", expenseDate = now.toEpochMilliseconds(), status = Expense.Status.REJECTED)
        expensesFlow.value = listOf(testExpense, pending, rejected)

        viewModel.dispatch(ReportsIntent.LoadExpenseReport)
        testDispatcher.scheduler.advanceUntilIdle()

        val report = viewModel.state.value.expenseReport.report!!
        assertEquals(150.0, report.totalApproved)
        assertEquals(50.0,  report.totalPending)
        assertEquals(30.0,  report.totalRejected)
        assertEquals(1, report.approvedCount)
        assertEquals(1, report.pendingCount)
        assertEquals(1, report.rejectedCount)
    }

    @Test
    fun `SelectExpenseRange updates selected range`() = runTest {
        viewModel.dispatch(ReportsIntent.SelectExpenseRange(DateRange.THIS_WEEK))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(DateRange.THIS_WEEK, viewModel.state.value.expenseReport.selectedRange)
    }

    @Test
    fun `DismissExpenseError clears expense error state`() = runTest {
        viewModel.dispatch(ReportsIntent.DismissExpenseError)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.expenseReport.error)
    }

    // ── Export effects ────────────────────────────────────────────────────────

    @Test
    fun `ExportSalesReportCsv with loaded report emits ExportComplete effect`() = runTest {
        ordersFlow.value = listOf(testOrder)
        viewModel.dispatch(ReportsIntent.LoadSalesReport)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            viewModel.dispatch(ReportsIntent.ExportSalesReportCsv)
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is ReportsEffect.ExportComplete)
            assertEquals(exportedPath, (effect as ReportsEffect.ExportComplete).filePath)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ExportSalesReportPdf with loaded report emits ExportComplete effect`() = runTest {
        ordersFlow.value = listOf(testOrder)
        viewModel.dispatch(ReportsIntent.LoadSalesReport)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            viewModel.dispatch(ReportsIntent.ExportSalesReportPdf)
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is ReportsEffect.ExportComplete)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ExportSalesReportCsv on export failure emits ShowSnackbar effect`() = runTest {
        ordersFlow.value = listOf(testOrder)
        viewModel.dispatch(ReportsIntent.LoadSalesReport)
        testDispatcher.scheduler.advanceUntilIdle()
        shouldFailExport = true

        viewModel.effects.test {
            viewModel.dispatch(ReportsIntent.ExportSalesReportCsv)
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is ReportsEffect.ShowSnackbar)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Print ─────────────────────────────────────────────────────────────────

    @Test
    fun `PrintSalesReport with loaded report emits PrintJobSent effect`() = runTest {
        ordersFlow.value = listOf(testOrder)
        viewModel.dispatch(ReportsIntent.LoadSalesReport)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            viewModel.dispatch(ReportsIntent.PrintSalesReport)
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is ReportsEffect.PrintJobSent)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `PrintSalesReport on printer failure emits ShowSnackbar effect`() = runTest {
        ordersFlow.value = listOf(testOrder)
        viewModel.dispatch(ReportsIntent.LoadSalesReport)
        testDispatcher.scheduler.advanceUntilIdle()
        shouldFailPrint = true

        viewModel.effects.test {
            viewModel.dispatch(ReportsIntent.PrintSalesReport)
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is ReportsEffect.ShowSnackbar)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Live flow reactivity ───────────────────────────────────────────────────

    @Test
    fun `stock report re-emits when products flow updates`() = runTest {
        productsFlow.value = listOf(testProduct)
        viewModel.dispatch(ReportsIntent.LoadStockReport)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.state.value.stockReport.allProducts.size)

        // Add a second product to the flow
        productsFlow.value = listOf(testProduct, lowStockProduct)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, viewModel.state.value.stockReport.allProducts.size)
    }

    // ── SortStock ──────────────────────────────────────────────────────────────

    @Test
    fun `SortStock updates sortColumn and sortAscending`() = runTest {
        viewModel.dispatch(ReportsIntent.SortStock(StockSortColumn.QTY, ascending = false))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(StockSortColumn.QTY, viewModel.state.value.stockReport.sortColumn)
        assertFalse(viewModel.state.value.stockReport.sortAscending)
    }

    // ── SetReportTimezone ──────────────────────────────────────────────────────

    @Test
    fun `SetReportTimezone updates reportTimezone`() = runTest {
        viewModel.dispatch(ReportsIntent.SetReportTimezone("Asia/Colombo"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Asia/Colombo", viewModel.state.value.reportTimezone)
    }

    // ── Report Scheduling ──────────────────────────────────────────────────────

    @Test
    fun `SetScheduleReportType updates scheduling reportType`() = runTest {
        viewModel.dispatch(ReportsIntent.SetScheduleReportType(ScheduledReportType.EXPENSE))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(ScheduledReportType.EXPENSE, viewModel.state.value.scheduling.reportType)
    }

    @Test
    fun `SetScheduleFrequency updates scheduling frequency`() = runTest {
        viewModel.dispatch(ReportsIntent.SetScheduleFrequency(ReportScheduleFrequency.WEEKLY))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(ReportScheduleFrequency.WEEKLY, viewModel.state.value.scheduling.frequency)
    }

    @Test
    fun `SetScheduleEmailRecipient updates scheduling emailRecipient`() = runTest {
        viewModel.dispatch(ReportsIntent.SetScheduleEmailRecipient("manager@store.com"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("manager@store.com", viewModel.state.value.scheduling.emailRecipient)
    }

    @Test
    fun `SetScheduleHour updates scheduling scheduleHour`() = runTest {
        viewModel.dispatch(ReportsIntent.SetScheduleHour(9))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(9, viewModel.state.value.scheduling.scheduleHour)
    }

    // ── SetCustomSalesRange ────────────────────────────────────────────────────

    @Test
    fun `SetCustomSalesRange sets customFrom and customTo on salesReport`() = runTest {
        val from = Instant.fromEpochMilliseconds(1_000_000_000L)
        val to = Instant.fromEpochMilliseconds(2_000_000_000L)
        viewModel.dispatch(ReportsIntent.SetCustomSalesRange(from, to))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(from, viewModel.state.value.salesReport.customFrom)
        assertEquals(to, viewModel.state.value.salesReport.customTo)
    }

    // ── SetCustomExpenseRange ─────────────────────────────────────────────────

    @Test
    fun `SetCustomExpenseRange sets customFrom and customTo on expenseReport`() = runTest {
        val from = Instant.fromEpochMilliseconds(1_000_000_000L)
        val to = Instant.fromEpochMilliseconds(2_000_000_000L)
        viewModel.dispatch(ReportsIntent.SetCustomExpenseRange(from, to))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(from, viewModel.state.value.expenseReport.customFrom)
        assertEquals(to, viewModel.state.value.expenseReport.customTo)
    }
}
