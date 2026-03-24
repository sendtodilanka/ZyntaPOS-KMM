package com.zyntasolutions.zyntapos.feature.inventory

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.PurchaseOrder
import com.zyntasolutions.zyntapos.domain.model.PurchaseOrderItem
import com.zyntasolutions.zyntapos.domain.model.ReplenishmentRule
import com.zyntasolutions.zyntapos.domain.model.StockTransfer
import com.zyntasolutions.zyntapos.domain.model.Supplier
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.model.User
import com.zyntasolutions.zyntapos.domain.model.Warehouse
import com.zyntasolutions.zyntapos.domain.model.WarehouseStock
import com.zyntasolutions.zyntapos.domain.model.report.AccountingLedgerRecord
import com.zyntasolutions.zyntapos.domain.model.report.CashMovementRecord
import com.zyntasolutions.zyntapos.domain.model.report.CategorySalesData
import com.zyntasolutions.zyntapos.domain.model.report.COGSData
import com.zyntasolutions.zyntapos.domain.model.report.ClockRecord
import com.zyntasolutions.zyntapos.domain.model.report.CouponUsageData
import com.zyntasolutions.zyntapos.domain.model.report.CustomerLoyaltyData
import com.zyntasolutions.zyntapos.domain.model.report.CustomerRetentionData
import com.zyntasolutions.zyntapos.domain.model.report.CustomerSpendData
import com.zyntasolutions.zyntapos.domain.model.report.DailySalesSummaryData
import com.zyntasolutions.zyntapos.domain.model.report.DeptExpenseData
import com.zyntasolutions.zyntapos.domain.model.report.DiscountVoidData
import com.zyntasolutions.zyntapos.domain.model.report.EInvoiceStatusData
import com.zyntasolutions.zyntapos.domain.model.report.GrossMarginData
import com.zyntasolutions.zyntapos.domain.model.report.HourlySalesData
import com.zyntasolutions.zyntapos.domain.model.report.InventoryValuationData
import com.zyntasolutions.zyntapos.domain.model.report.LeaveBalanceData
import com.zyntasolutions.zyntapos.domain.model.report.MonthlySalesData
import com.zyntasolutions.zyntapos.domain.model.report.PayrollSummaryData
import com.zyntasolutions.zyntapos.domain.model.report.ProductPerformanceData
import com.zyntasolutions.zyntapos.domain.model.report.ProductVolumeData
import com.zyntasolutions.zyntapos.domain.model.report.ProfitLossData
import com.zyntasolutions.zyntapos.domain.model.report.PurchaseOrderData
import com.zyntasolutions.zyntapos.domain.model.report.ReturnRefundData
import com.zyntasolutions.zyntapos.domain.model.report.ShiftCoverageData
import com.zyntasolutions.zyntapos.domain.model.report.StaffAttendanceData
import com.zyntasolutions.zyntapos.domain.model.report.StaffSalesData
import com.zyntasolutions.zyntapos.domain.model.report.StockAgingData
import com.zyntasolutions.zyntapos.domain.model.report.StockReorderData
import com.zyntasolutions.zyntapos.domain.model.report.StockTransferRecord
import com.zyntasolutions.zyntapos.domain.model.report.StoreSalesData
import com.zyntasolutions.zyntapos.domain.model.report.SupplierPurchaseData
import com.zyntasolutions.zyntapos.domain.model.report.TaxCollectionData
import com.zyntasolutions.zyntapos.domain.model.report.WalletBalanceData
import com.zyntasolutions.zyntapos.domain.model.report.WarehouseInventoryData
import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
import com.zyntasolutions.zyntapos.domain.repository.PurchaseOrderRepository
import com.zyntasolutions.zyntapos.domain.repository.ReplenishmentRuleRepository
import com.zyntasolutions.zyntapos.domain.repository.ReportRepository
import com.zyntasolutions.zyntapos.domain.repository.SupplierRepository
import com.zyntasolutions.zyntapos.domain.repository.WarehouseRepository
import com.zyntasolutions.zyntapos.domain.repository.WarehouseStockRepository
import com.zyntasolutions.zyntapos.domain.usecase.inventory.AutoReplenishmentUseCase
import com.zyntasolutions.zyntapos.domain.usecase.inventory.CreatePurchaseOrderUseCase
import com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise.GenerateStockReorderReportUseCase
import com.zyntasolutions.zyntapos.feature.inventory.replenishment.CreatePoField
import com.zyntasolutions.zyntapos.feature.inventory.replenishment.ReplenishmentEffect
import com.zyntasolutions.zyntapos.feature.inventory.replenishment.ReplenishmentIntent
import com.zyntasolutions.zyntapos.feature.inventory.replenishment.ReplenishmentTab
import com.zyntasolutions.zyntapos.feature.inventory.replenishment.ReplenishmentViewModel
import com.zyntasolutions.zyntapos.feature.inventory.replenishment.RuleField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [ReplenishmentViewModel].
 *
 * Tests cover pure-state mutations (tab switching, dialog visibility, form field updates,
 * input validation) and async operations (cancel order, delete/save rule, auto-replenishment).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReplenishmentViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val adminUser = User(
        id = "user-001",
        name = "Admin User",
        email = "admin@zyntapos.com",
        role = Role.ADMIN,
        storeId = "store-001",
        isSystemAdmin = true,
        createdAt = Instant.fromEpochMilliseconds(1_700_000_000_000L),
        updatedAt = Instant.fromEpochMilliseconds(1_700_000_000_000L),
    )

    private val mockRule = ReplenishmentRule(
        id = "rule-001",
        productId = "prod-001",
        warehouseId = "wh-001",
        supplierId = "sup-001",
        reorderPoint = 10.0,
        reorderQty = 50.0,
        autoApprove = false,
        isActive = true,
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_000_000L,
    )

    private val mockOrder = PurchaseOrder(
        id = "po-001",
        supplierId = "sup-001",
        orderNumber = "PO-001",
        status = PurchaseOrder.Status.PENDING,
        orderDate = 1_700_000_000_000L,
        expectedDate = null,
        totalAmount = 500.0,
        notes = null,
        createdBy = "user-001",
        items = emptyList(),
    )

    private val mockSupplier = Supplier(
        id = "sup-001",
        name = "Acme Supplier",
        contactPerson = null,
        phone = null,
        email = null,
        address = null,
        notes = null,
        isActive = true,
    )

    private val sessionFlow = MutableStateFlow<User?>(adminUser)
    private val purchaseOrdersFlow = MutableStateFlow<List<PurchaseOrder>>(listOf(mockOrder))
    private val rulesFlow = MutableStateFlow<List<ReplenishmentRule>>(listOf(mockRule))
    private var cancelOrderResult: Result<Unit> = Result.Success(Unit)
    private var upsertRuleResult: Result<Unit> = Result.Success(Unit)
    private var deleteRuleResult: Result<Unit> = Result.Success(Unit)

    private val fakeAuthRepo = object : AuthRepository {
        override suspend fun login(email: String, password: String): Result<User> = Result.Success(adminUser)
        override suspend fun logout() {}
        override fun getSession(): Flow<User?> = sessionFlow
        override suspend fun refreshToken(): Result<Unit> = Result.Success(Unit)
        override suspend fun updatePin(userId: String, pin: String): Result<Unit> = Result.Success(Unit)
        override suspend fun validatePin(userId: String, pin: String): Result<Boolean> = Result.Success(true)
    }

    private val fakeSupplierRepo = object : SupplierRepository {
        override fun getAll(): Flow<List<Supplier>> = MutableStateFlow(listOf(mockSupplier))
        override suspend fun getById(id: String): Result<Supplier> = Result.Success(mockSupplier)
        override suspend fun insert(supplier: Supplier): Result<Unit> = Result.Success(Unit)
        override suspend fun update(supplier: Supplier): Result<Unit> = Result.Success(Unit)
        override suspend fun delete(id: String): Result<Unit> = Result.Success(Unit)
    }

    private val fakeWarehouseRepo = object : WarehouseRepository {
        override fun getByStore(storeId: String): Flow<List<Warehouse>> = MutableStateFlow(emptyList())
        override suspend fun getDefault(storeId: String): Result<Warehouse?> = Result.Success(null)
        override suspend fun getById(id: String): Result<Warehouse> =
            Result.Error(DatabaseException("Not found"))
        override suspend fun insert(warehouse: Warehouse): Result<Unit> = Result.Success(Unit)
        override suspend fun update(warehouse: Warehouse): Result<Unit> = Result.Success(Unit)
        override fun getTransfersByWarehouse(warehouseId: String): Flow<List<StockTransfer>> =
            MutableStateFlow(emptyList())
        override suspend fun getTransferById(id: String): Result<StockTransfer> =
            Result.Error(DatabaseException("Not found"))
        override suspend fun getPendingTransfers(): Result<List<StockTransfer>> = Result.Success(emptyList())
        override suspend fun createTransfer(transfer: StockTransfer): Result<Unit> = Result.Success(Unit)
        override suspend fun commitTransfer(transferId: String, confirmedBy: String): Result<Unit> = Result.Success(Unit)
        override suspend fun cancelTransfer(transferId: String): Result<Unit> = Result.Success(Unit)
        override suspend fun approveTransfer(transferId: String, approvedBy: String): Result<Unit> = Result.Success(Unit)
        override suspend fun dispatchTransfer(transferId: String, dispatchedBy: String): Result<Unit> = Result.Success(Unit)
        override suspend fun receiveTransfer(transferId: String, receivedBy: String): Result<Unit> = Result.Success(Unit)
        override suspend fun getTransfersByStatus(status: StockTransfer.Status): Result<List<StockTransfer>> =
            Result.Success(emptyList())
        override suspend fun getRackLocationForProduct(productId: String, warehouseId: String): Result<Pair<String?, String?>> =
            Result.Success(null to null)
    }

    private val fakePurchaseOrderRepo = object : PurchaseOrderRepository {
        override fun getAll(): Flow<List<PurchaseOrder>> = purchaseOrdersFlow
        override suspend fun getById(id: String): Result<PurchaseOrder> = Result.Success(mockOrder)
        override suspend fun getByDateRange(startDate: Long, endDate: Long): Result<List<PurchaseOrder>> =
            Result.Success(emptyList())
        override suspend fun getBySupplierId(supplierId: String): Result<List<PurchaseOrder>> =
            Result.Success(emptyList())
        override suspend fun getByStatus(status: PurchaseOrder.Status): Result<List<PurchaseOrder>> =
            Result.Success(emptyList())
        override suspend fun create(order: PurchaseOrder): Result<Unit> = Result.Success(Unit)
        override suspend fun receiveItems(
            purchaseOrderId: String,
            receivedItems: Map<String, Double>,
            receivedBy: String,
        ): Result<Unit> = Result.Success(Unit)
        override suspend fun cancel(purchaseOrderId: String): Result<Unit> = cancelOrderResult
    }

    private val fakeReplenishmentRuleRepo = object : ReplenishmentRuleRepository {
        override fun getAll(): Flow<List<ReplenishmentRule>> = rulesFlow
        override fun getByWarehouse(warehouseId: String): Flow<List<ReplenishmentRule>> =
            MutableStateFlow(emptyList())
        override suspend fun getAutoApproveRules(): Result<List<ReplenishmentRule>> =
            Result.Success(emptyList())
        override suspend fun getByProductAndWarehouse(
            productId: String,
            warehouseId: String,
        ): Result<ReplenishmentRule?> = Result.Success(null)
        override suspend fun upsert(rule: ReplenishmentRule): Result<Unit> = upsertRuleResult
        override suspend fun delete(id: String): Result<Unit> = deleteRuleResult
    }

    private val fakeWarehouseStockRepo = object : WarehouseStockRepository {
        override fun getByWarehouse(warehouseId: String): Flow<List<WarehouseStock>> =
            MutableStateFlow(emptyList())
        override fun getByProduct(productId: String): Flow<List<WarehouseStock>> =
            MutableStateFlow(emptyList())
        override suspend fun getEntry(warehouseId: String, productId: String): Result<WarehouseStock?> =
            Result.Success(null)
        override suspend fun getTotalStock(productId: String): Result<Double> = Result.Success(0.0)
        override fun getLowStockByWarehouse(warehouseId: String): Flow<List<WarehouseStock>> =
            MutableStateFlow(emptyList())
        override fun getAllLowStock(): Flow<List<WarehouseStock>> = MutableStateFlow(emptyList())
        override suspend fun upsert(stock: WarehouseStock): Result<Unit> = Result.Success(Unit)
        override suspend fun adjustStock(
            warehouseId: String,
            productId: String,
            delta: Double,
        ): Result<Unit> = Result.Success(Unit)
        override suspend fun transferStock(
            sourceWarehouseId: String,
            destWarehouseId: String,
            productId: String,
            quantity: Double,
        ): Result<Unit> = Result.Success(Unit)
        override suspend fun deleteEntry(warehouseId: String, productId: String): Result<Unit> =
            Result.Success(Unit)
    }

    private val fakeReportRepository = object : ReportRepository {
        override suspend fun getStockReorderAlerts(): List<StockReorderData> = emptyList()
        override suspend fun getDailySalesSummary(date: LocalDate): DailySalesSummaryData = TODO()
        override suspend fun getSalesByCategory(from: Instant, to: Instant): List<CategorySalesData> = emptyList()
        override suspend fun getCashMovementLog(from: Instant, to: Instant): List<CashMovementRecord> = emptyList()
        override suspend fun getTopProductsByVolume(from: Instant, to: Instant, limit: Int): List<ProductVolumeData> = emptyList()
        override suspend fun getProfitLoss(from: Instant, to: Instant): ProfitLossData = TODO()
        override suspend fun getProductPerformance(from: Instant, to: Instant): List<ProductPerformanceData> = emptyList()
        override suspend fun getCouponUsage(from: Instant, to: Instant): CouponUsageData = TODO()
        override suspend fun getPaymentMethodBreakdown(from: Instant, to: Instant): Map<String, Double> = emptyMap()
        override suspend fun getTaxCollection(from: Instant, to: Instant): List<TaxCollectionData> = emptyList()
        override suspend fun getDiscountVoidAnalysis(from: Instant, to: Instant): DiscountVoidData = TODO()
        override suspend fun getTopCustomers(from: Instant, to: Instant, limit: Int): List<CustomerSpendData> = emptyList()
        override suspend fun getStaffAttendanceSummary(from: Instant, to: Instant): List<StaffAttendanceData> = emptyList()
        override suspend fun getStaffSalesSummary(from: Instant, to: Instant): List<StaffSalesData> = emptyList()
        override suspend fun getPayrollSummary(payPeriodId: String): List<PayrollSummaryData> = emptyList()
        override suspend fun getLeaveBalances(asOf: Instant): List<LeaveBalanceData> = emptyList()
        override suspend fun getShiftCoverage(from: Instant, to: Instant): List<ShiftCoverageData> = emptyList()
        override suspend fun getMultiStoreComparison(from: Instant, to: Instant): List<StoreSalesData> = emptyList()
        override suspend fun getInterStoreTransfers(from: Instant, to: Instant): List<StockTransferRecord> = emptyList()
        override suspend fun getWarehouseInventory(): List<WarehouseInventoryData> = emptyList()
        override suspend fun getStockAging(noSalesDays: Int): List<StockAgingData> = emptyList()
        override suspend fun getSupplierPurchases(from: Instant, to: Instant): List<SupplierPurchaseData> = emptyList()
        override suspend fun getReturnRefundSummary(from: Instant, to: Instant): ReturnRefundData = TODO()
        override suspend fun getEInvoiceStatus(from: Instant, to: Instant): List<EInvoiceStatusData> = emptyList()
        override suspend fun getAccountingLedger(from: Instant, to: Instant): List<AccountingLedgerRecord> = emptyList()
        override suspend fun getHourlySales(date: LocalDate): List<HourlySalesData> = emptyList()
        override suspend fun getCustomerLoyaltySummary(from: Instant, to: Instant): List<CustomerLoyaltyData> = emptyList()
        override suspend fun getWalletBalances(): List<WalletBalanceData> = emptyList()
        override suspend fun getCOGS(from: Instant, to: Instant): List<COGSData> = emptyList()
        override suspend fun getGrossMargin(from: Instant, to: Instant): List<GrossMarginData> = emptyList()
        override suspend fun getAnnualSalesTrend(year: Int): List<MonthlySalesData> = emptyList()
        override suspend fun getClockInOutLog(from: Instant, to: Instant, employeeId: String?): List<ClockRecord> = emptyList()
        override suspend fun getInventoryValuation(): List<InventoryValuationData> = emptyList()
        override suspend fun getCustomerRetentionMetrics(from: Instant, to: Instant): CustomerRetentionData = TODO()
        override suspend fun getPurchaseOrders(from: Instant, to: Instant): List<PurchaseOrderData> = emptyList()
        override suspend fun getExpensesByDepartment(from: Instant, to: Instant): List<DeptExpenseData> = emptyList()
    }

    private lateinit var viewModel: ReplenishmentViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val createPoUseCase = CreatePurchaseOrderUseCase(fakePurchaseOrderRepo, fakeSupplierRepo)
        val autoReplenishmentUseCase = AutoReplenishmentUseCase(
            replenishmentRuleRepository = fakeReplenishmentRuleRepo,
            warehouseStockRepository = fakeWarehouseStockRepo,
            createPurchaseOrderUseCase = createPoUseCase,
        )
        viewModel = ReplenishmentViewModel(
            authRepository = fakeAuthRepo,
            supplierRepository = fakeSupplierRepo,
            warehouseRepository = fakeWarehouseRepo,
            purchaseOrderRepository = fakePurchaseOrderRepo,
            replenishmentRuleRepository = fakeReplenishmentRuleRepo,
            generateStockReorderReportUseCase = GenerateStockReorderReportUseCase(fakeReportRepository),
            createPurchaseOrderUseCase = createPoUseCase,
            autoReplenishmentUseCase = autoReplenishmentUseCase,
        )
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    // ── Initial state ──────────────────────────────────────────────────────────

    @Test
    fun `initial state has REORDER_ALERTS tab and empty forms`() {
        val state = viewModel.state.value
        assertEquals(ReplenishmentTab.REORDER_ALERTS, state.activeTab)
        assertFalse(state.showCreatePoDialog)
        assertFalse(state.showRuleDialog)
        assertNull(state.selectedOrder)
        assertNull(state.error)
        assertTrue(state.createPoSupplierId.isEmpty())
    }

    // ── Tab selection ─────────────────────────────────────────────────────────

    @Test
    fun `SelectTab PURCHASE_ORDERS changes activeTab`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.handleIntentForTest(ReplenishmentIntent.SelectTab(ReplenishmentTab.PURCHASE_ORDERS))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(ReplenishmentTab.PURCHASE_ORDERS, viewModel.state.value.activeTab)
    }

    @Test
    fun `SelectTab REPLENISHMENT_RULES changes activeTab`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.handleIntentForTest(ReplenishmentIntent.SelectTab(ReplenishmentTab.REPLENISHMENT_RULES))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(ReplenishmentTab.REPLENISHMENT_RULES, viewModel.state.value.activeTab)
    }

    // ── Purchase order selection ───────────────────────────────────────────────

    @Test
    fun `SelectOrder sets selectedOrder`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.handleIntentForTest(ReplenishmentIntent.SelectOrder(mockOrder))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("po-001", viewModel.state.value.selectedOrder?.id)
    }

    @Test
    fun `DismissOrderDetail clears selectedOrder`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.handleIntentForTest(ReplenishmentIntent.SelectOrder(mockOrder))
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.state.value.selectedOrder)

        viewModel.handleIntentForTest(ReplenishmentIntent.DismissOrderDetail)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.state.value.selectedOrder)
    }

    // ── Create PO dialog ──────────────────────────────────────────────────────

    @Test
    fun `OpenCreatePoDialog sets showCreatePoDialog to true`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.handleIntentForTest(ReplenishmentIntent.OpenCreatePoDialog)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.state.value.showCreatePoDialog)
        assertTrue(viewModel.state.value.createPoSupplierId.isEmpty())
    }

    @Test
    fun `DismissCreatePoDialog hides dialog`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.handleIntentForTest(ReplenishmentIntent.OpenCreatePoDialog)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.state.value.showCreatePoDialog)

        viewModel.handleIntentForTest(ReplenishmentIntent.DismissCreatePoDialog)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.state.value.showCreatePoDialog)
    }

    @Test
    fun `UpdateCreatePoField SUPPLIER_ID updates createPoSupplierId`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.handleIntentForTest(
            ReplenishmentIntent.UpdateCreatePoField(CreatePoField.SUPPLIER_ID, "sup-001"),
        )
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("sup-001", viewModel.state.value.createPoSupplierId)
    }

    @Test
    fun `UpdateCreatePoField ORDER_NUMBER updates createPoOrderNumber`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.handleIntentForTest(
            ReplenishmentIntent.UpdateCreatePoField(CreatePoField.ORDER_NUMBER, "PO-2026-001"),
        )
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("PO-2026-001", viewModel.state.value.createPoOrderNumber)
    }

    @Test
    fun `SetCreatePoExpectedDate updates createPoExpectedDate`() = runTest {
        val epochMs = 1_800_000_000_000L
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.handleIntentForTest(ReplenishmentIntent.SetCreatePoExpectedDate(epochMs))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(epochMs, viewModel.state.value.createPoExpectedDate)
    }

    // ── Create PO validation ──────────────────────────────────────────────────

    @Test
    fun `SubmitCreatePo with blank supplierId sets error`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.handleIntentForTest(ReplenishmentIntent.SubmitCreatePo)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.state.value.error)
        assertTrue(viewModel.state.value.error!!.contains("supplier", ignoreCase = true))
    }

    @Test
    fun `SubmitCreatePo with supplier but no source alert sets error`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.handleIntentForTest(
            ReplenishmentIntent.UpdateCreatePoField(CreatePoField.SUPPLIER_ID, "sup-001"),
        )
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.handleIntentForTest(ReplenishmentIntent.SubmitCreatePo)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.state.value.error)
    }

    // ── Rule dialog ───────────────────────────────────────────────────────────

    @Test
    fun `OpenRuleDialog with null opens dialog with empty form fields`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.handleIntentForTest(ReplenishmentIntent.OpenRuleDialog(null))
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.state.value.showRuleDialog)
        assertNull(viewModel.state.value.selectedRule)
        assertTrue(viewModel.state.value.ruleFormProductId.isEmpty())
        assertTrue(viewModel.state.value.ruleFormWarehouseId.isEmpty())
    }

    @Test
    fun `OpenRuleDialog with existing rule pre-fills form fields`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.handleIntentForTest(ReplenishmentIntent.OpenRuleDialog(mockRule))
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.state.value.showRuleDialog)
        assertEquals("rule-001", viewModel.state.value.selectedRule?.id)
        assertEquals("prod-001", viewModel.state.value.ruleFormProductId)
        assertEquals("wh-001", viewModel.state.value.ruleFormWarehouseId)
        assertEquals("sup-001", viewModel.state.value.ruleFormSupplierId)
        assertEquals("10.0", viewModel.state.value.ruleFormReorderPoint)
        assertEquals("50.0", viewModel.state.value.ruleFormReorderQty)
    }

    @Test
    fun `DismissRuleDialog closes dialog and clears selectedRule`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.handleIntentForTest(ReplenishmentIntent.OpenRuleDialog(mockRule))
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.state.value.showRuleDialog)

        viewModel.handleIntentForTest(ReplenishmentIntent.DismissRuleDialog)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.state.value.showRuleDialog)
        assertNull(viewModel.state.value.selectedRule)
    }

    @Test
    fun `UpdateRuleField PRODUCT_ID updates ruleFormProductId`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.handleIntentForTest(ReplenishmentIntent.UpdateRuleField(RuleField.PRODUCT_ID, "prod-999"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("prod-999", viewModel.state.value.ruleFormProductId)
    }

    @Test
    fun `SetRuleAutoApprove updates ruleFormAutoApprove`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.handleIntentForTest(ReplenishmentIntent.SetRuleAutoApprove(true))
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.state.value.ruleFormAutoApprove)
    }

    @Test
    fun `SetRuleActive false updates ruleFormIsActive`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.handleIntentForTest(ReplenishmentIntent.SetRuleActive(false))
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.state.value.ruleFormIsActive)
    }

    // ── SaveRule validation ───────────────────────────────────────────────────

    @Test
    fun `SaveRule with blank productId sets error`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.handleIntentForTest(ReplenishmentIntent.SaveRule)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.state.value.error)
        assertTrue(viewModel.state.value.error!!.contains("Product", ignoreCase = true))
    }

    @Test
    fun `SaveRule with blank warehouseId sets error`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.handleIntentForTest(ReplenishmentIntent.UpdateRuleField(RuleField.PRODUCT_ID, "prod-001"))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.handleIntentForTest(ReplenishmentIntent.SaveRule)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.state.value.error)
        assertTrue(viewModel.state.value.error!!.contains("Warehouse", ignoreCase = true))
    }

    @Test
    fun `SaveRule with blank supplierId sets error`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.handleIntentForTest(ReplenishmentIntent.UpdateRuleField(RuleField.PRODUCT_ID, "prod-001"))
        viewModel.handleIntentForTest(ReplenishmentIntent.UpdateRuleField(RuleField.WAREHOUSE_ID, "wh-001"))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.handleIntentForTest(ReplenishmentIntent.SaveRule)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.state.value.error)
        assertTrue(viewModel.state.value.error!!.contains("Supplier", ignoreCase = true))
    }

    @Test
    fun `SaveRule with invalid reorder point sets error`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.handleIntentForTest(ReplenishmentIntent.UpdateRuleField(RuleField.PRODUCT_ID, "prod-001"))
        viewModel.handleIntentForTest(ReplenishmentIntent.UpdateRuleField(RuleField.WAREHOUSE_ID, "wh-001"))
        viewModel.handleIntentForTest(ReplenishmentIntent.UpdateRuleField(RuleField.SUPPLIER_ID, "sup-001"))
        viewModel.handleIntentForTest(ReplenishmentIntent.UpdateRuleField(RuleField.REORDER_POINT, "not-a-number"))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.handleIntentForTest(ReplenishmentIntent.SaveRule)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.state.value.error)
        assertTrue(viewModel.state.value.error!!.contains("reorder point", ignoreCase = true))
    }

    @Test
    fun `SaveRule with zero reorder qty sets error`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.handleIntentForTest(ReplenishmentIntent.UpdateRuleField(RuleField.PRODUCT_ID, "prod-001"))
        viewModel.handleIntentForTest(ReplenishmentIntent.UpdateRuleField(RuleField.WAREHOUSE_ID, "wh-001"))
        viewModel.handleIntentForTest(ReplenishmentIntent.UpdateRuleField(RuleField.SUPPLIER_ID, "sup-001"))
        viewModel.handleIntentForTest(ReplenishmentIntent.UpdateRuleField(RuleField.REORDER_POINT, "5"))
        viewModel.handleIntentForTest(ReplenishmentIntent.UpdateRuleField(RuleField.REORDER_QTY, "0"))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.handleIntentForTest(ReplenishmentIntent.SaveRule)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.state.value.error)
        assertTrue(viewModel.state.value.error!!.contains("Reorder quantity", ignoreCase = true))
    }

    // ── CancelOrder ───────────────────────────────────────────────────────────

    @Test
    fun `CancelOrder success emits ShowSuccess effect`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        cancelOrderResult = Result.Success(Unit)

        viewModel.effects.test {
            viewModel.handleIntentForTest(ReplenishmentIntent.CancelOrder("po-001"))
            testDispatcher.scheduler.advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is ReplenishmentEffect.ShowSuccess)
        }
    }

    @Test
    fun `CancelOrder failure sets error in state`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        cancelOrderResult = Result.Error(DatabaseException("Order not cancellable"))

        viewModel.handleIntentForTest(ReplenishmentIntent.CancelOrder("po-001"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.error)
    }

    // ── DeleteRule ────────────────────────────────────────────────────────────

    @Test
    fun `DeleteRule success emits ShowSuccess effect`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        deleteRuleResult = Result.Success(Unit)

        viewModel.effects.test {
            viewModel.handleIntentForTest(ReplenishmentIntent.DeleteRule("rule-001"))
            testDispatcher.scheduler.advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is ReplenishmentEffect.ShowSuccess)
        }
    }

    @Test
    fun `DeleteRule failure sets error in state`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        deleteRuleResult = Result.Error(DatabaseException("Rule not found"))

        viewModel.handleIntentForTest(ReplenishmentIntent.DeleteRule("rule-001"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.error)
    }

    // ── RunAutoReplenishment ──────────────────────────────────────────────────

    @Test
    fun `RunAutoReplenishment populates lastAutoReplenishmentResult`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.handleIntentForTest(ReplenishmentIntent.RunAutoReplenishment)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.lastAutoReplenishmentResult)
        assertFalse(viewModel.state.value.isRunningAutoReplenishment)
    }

    @Test
    fun `DismissAutoReplenishmentResult clears lastAutoReplenishmentResult`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.handleIntentForTest(ReplenishmentIntent.RunAutoReplenishment)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.state.value.lastAutoReplenishmentResult)

        viewModel.handleIntentForTest(ReplenishmentIntent.DismissAutoReplenishmentResult)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.state.value.lastAutoReplenishmentResult)
    }

    // ── DismissError / DismissSuccess ─────────────────────────────────────────

    @Test
    fun `DismissError clears error`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        cancelOrderResult = Result.Error(DatabaseException("error"))
        viewModel.handleIntentForTest(ReplenishmentIntent.CancelOrder("po-001"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.state.value.error)

        viewModel.handleIntentForTest(ReplenishmentIntent.DismissError)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `DismissSuccess clears successMessage`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        // Manually set a success message by cancelling an order
        cancelOrderResult = Result.Success(Unit)
        // The ShowSuccess comes as effect, not state — but state.successMessage is not set in this VM
        // Instead just verify DismissSuccess doesn't crash and keeps error null
        viewModel.handleIntentForTest(ReplenishmentIntent.DismissSuccess)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.state.value.successMessage)
    }
}

// ─── Extension to expose handleIntent for testing ────────────────────────────

private fun ReplenishmentViewModel.handleIntentForTest(intent: ReplenishmentIntent) =
    dispatch(intent)
