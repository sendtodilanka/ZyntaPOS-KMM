package com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.domain.model.AuditEntry
import com.zyntasolutions.zyntapos.domain.model.AuditEventType
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.repository.AuditRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeBackupRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeReportRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildAccountingLedgerRecord
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildBackupInfo
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildCOGSData
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildClockRecord
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildCustomerLoyaltyData
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildCustomerRetentionData
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildDeptExpenseData
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildGrossMarginData
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildHourlySalesData
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildInventoryValuationData
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildLeaveBalanceData
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildMonthlySalesData
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildPayrollSummaryData
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildPurchaseOrderData
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildReturnRefundData
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildShiftCoverageData
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildStaffAttendanceData
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildStaffSalesData
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildStockAgingData
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildStockReorderData
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildStockTransferRecord
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildStoreSalesData
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildSupplierPurchaseData
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildWalletBalanceData
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildWarehouseInventoryData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for all 30 enterprise report use cases.
 *
 * Uses [FakeReportRepository], [FakeBackupRepository], and a minimal inline
 * [FakeAuditRepository] — no database or network access.
 *
 * Test strategy: each use case is a thin delegation wrapper, so tests verify:
 *   1. The use case emits the stub data from the repository (delegation test).
 *   2. Any parameter passed to the use case is forwarded to the repository (spy test).
 *   3. Empty repository returns an empty result (edge case).
 */
class EnterpriseReportUseCasesTest {

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private val now: Instant = Clock.System.now()
    private val from: Instant = now
    private val to: Instant = now

    // ─── Inline FakeAuditRepository ───────────────────────────────────────────

    private class FakeAuditRepository(
        private val entries: List<AuditEntry> = emptyList(),
    ) : AuditRepository {
        private val _flow = MutableStateFlow(entries)

        override fun observeAll(): Flow<List<AuditEntry>> = _flow
        override fun observeByUserId(userId: String): Flow<List<AuditEntry>> =
            flowOf(entries.filter { it.userId == userId })

        override suspend fun insert(entry: AuditEntry) { _flow.value = _flow.value + entry }
        override suspend fun getAllChronological(): List<AuditEntry> = entries.sortedBy { it.createdAt }
        override suspend fun getLatestHash(): String? = entries.lastOrNull()?.hash
        override suspend fun countEntries(): Long = entries.size.toLong()
        override suspend fun getRecentLoginFailureCount(userId: String, sinceEpochMillis: Long): Long = 0L
    }

    private fun buildAuditEntry(
        id: String = "ae-01",
        createdAt: Instant = Clock.System.now(),
    ) = AuditEntry(
        id = id,
        eventType = AuditEventType.LOGIN_ATTEMPT,
        userId = "user-01",
        userName = "Alice",
        userRole = Role.CASHIER,
        deviceId = "device-01",
        entityType = "USER",
        entityId = "user-01",
        payload = "{}",
        previousValue = null,
        newValue = null,
        success = true,
        ipAddress = null,
        hash = "abc123",
        previousHash = "GENESIS",
        createdAt = createdAt,
    )

    // ─── Staff Reports ────────────────────────────────────────────────────────

    @Test
    fun `staffAttendance_returns_data_from_repository`() = runTest {
        val repo = FakeReportRepository().apply {
            stubStaffAttendance = listOf(buildStaffAttendanceData("emp-01"), buildStaffAttendanceData("emp-02"))
        }

        GenerateStaffAttendanceReportUseCase(repo)(from, to).test {
            val list = awaitItem()
            assertEquals(2, list.size)
            assertEquals("emp-01", list.first().employeeId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `staffAttendance_empty_repository_returns_empty_list`() = runTest {
        val repo = FakeReportRepository()

        GenerateStaffAttendanceReportUseCase(repo)(from, to).test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `salesByCashier_returns_staff_sales_data`() = runTest {
        val repo = FakeReportRepository().apply {
            stubStaffSales = listOf(buildStaffSalesData("emp-cashier-01"))
        }

        GenerateSalesByCashierReportUseCase(repo)(from, to).test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("emp-cashier-01", list.first().employeeId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `staffPerformance_delegates_to_staff_sales_summary`() = runTest {
        val repo = FakeReportRepository().apply {
            stubStaffSales = listOf(buildStaffSalesData(totalSales = 1500.0))
        }

        GenerateStaffPerformanceReportUseCase(repo)(from, to).test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals(1500.0, list.first().totalSales)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `payrollSummary_forwards_payPeriodId_to_repository`() = runTest {
        val repo = FakeReportRepository().apply {
            stubPayrollSummary = listOf(buildPayrollSummaryData(payPeriodId = "pp-Q1-2026"))
        }

        GeneratePayrollSummaryReportUseCase(repo)("pp-Q1-2026").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("pp-Q1-2026", list.first().payPeriodId)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("pp-Q1-2026", repo.lastPayrollPeriodId)
    }

    @Test
    fun `leaveBalance_returns_employee_leave_data`() = runTest {
        val repo = FakeReportRepository().apply {
            stubLeaveBalances = listOf(buildLeaveBalanceData("emp-01"), buildLeaveBalanceData("emp-02"))
        }

        GenerateLeaveBalanceReportUseCase(repo)(now).test {
            assertEquals(2, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `shiftCoverage_returns_coverage_data`() = runTest {
        val repo = FakeReportRepository().apply {
            stubShiftCoverage = listOf(buildShiftCoverageData("shift-morning"))
        }

        GenerateShiftCoverageReportUseCase(repo)(from, to).test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("shift-morning", list.first().shiftId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clockInOut_forwards_employeeId_filter_to_repository`() = runTest {
        val repo = FakeReportRepository().apply {
            stubClockInOut = listOf(buildClockRecord("emp-42"))
        }

        GenerateStaffClockInOutReportUseCase(repo)(from, to, employeeId = "emp-42").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("emp-42", list.first().employeeId)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("emp-42", repo.lastClockEmployeeId)
    }

    @Test
    fun `clockInOut_null_employeeId_returns_all_staff`() = runTest {
        val repo = FakeReportRepository().apply {
            stubClockInOut = listOf(buildClockRecord("emp-01"), buildClockRecord("emp-02"))
        }

        GenerateStaffClockInOutReportUseCase(repo)(from, to, employeeId = null).test {
            assertEquals(2, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(null, repo.lastClockEmployeeId)
    }

    // ─── Multi-Store Reports ──────────────────────────────────────────────────

    @Test
    fun `multiStoreComparison_returns_per_store_data`() = runTest {
        val repo = FakeReportRepository().apply {
            stubMultiStoreComparison = listOf(buildStoreSalesData("store-01"), buildStoreSalesData("store-02"))
        }

        GenerateMultiStoreComparisonReportUseCase(repo)(from, to).test {
            assertEquals(2, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `interStoreTransfer_returns_transfer_records`() = runTest {
        val repo = FakeReportRepository().apply {
            stubInterStoreTransfers = listOf(buildStockTransferRecord("tr-01"))
        }

        GenerateInterStoreTransferReportUseCase(repo)(from, to).test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("tr-01", list.first().transferId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `warehouseInventory_returns_rack_level_inventory`() = runTest {
        val repo = FakeReportRepository().apply {
            stubWarehouseInventory = listOf(buildWarehouseInventoryData("wh-01"), buildWarehouseInventoryData("wh-02"))
        }

        GenerateWarehouseInventoryReportUseCase(repo)().test {
            assertEquals(2, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─── Inventory Reports ────────────────────────────────────────────────────

    @Test
    fun `stockAging_forwards_noSalesDays_to_repository`() = runTest {
        val repo = FakeReportRepository().apply {
            stubStockAging = listOf(buildStockAgingData(daysSinceLastSale = 90))
        }

        GenerateStockAgingReportUseCase(repo)(noSalesDays = 60).test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals(90, list.first().daysSinceLastSale)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(60, repo.lastStockAgingNoSalesDays)
    }

    @Test
    fun `stockAging_default_threshold_is_30_days`() = runTest {
        val repo = FakeReportRepository()

        GenerateStockAgingReportUseCase(repo)().test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(30, repo.lastStockAgingNoSalesDays)
    }

    @Test
    fun `lowStockAlert_returns_products_requiring_reorder`() = runTest {
        val repo = FakeReportRepository().apply {
            stubStockReorderAlerts = listOf(buildStockReorderData("prod-low-01"), buildStockReorderData("prod-low-02"))
        }

        GenerateLowStockAlertReportUseCase(repo)().test {
            assertEquals(2, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `stockReorder_returns_same_reorder_alert_data`() = runTest {
        val repo = FakeReportRepository().apply {
            stubStockReorderAlerts = listOf(buildStockReorderData("prod-01", currentStock = 3, reorderPoint = 10))
        }

        GenerateStockReorderReportUseCase(repo)().test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals(3, list.first().currentStock)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `supplierPurchase_returns_supplier_data`() = runTest {
        val repo = FakeReportRepository().apply {
            stubSupplierPurchases = listOf(buildSupplierPurchaseData("sup-01"), buildSupplierPurchaseData("sup-02"))
        }

        GenerateSupplierPurchaseReportUseCase(repo)(from, to).test {
            assertEquals(2, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `returnRefund_returns_aggregate_return_data`() = runTest {
        val repo = FakeReportRepository().apply {
            stubReturnRefund = buildReturnRefundData().copy(totalReturns = 12, totalRefundAmount = 1200.0)
        }

        GenerateReturnRefundReportUseCase(repo)(from, to).test {
            val data = awaitItem()
            assertEquals(12, data.totalReturns)
            assertEquals(1200.0, data.totalRefundAmount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─── Finance Reports ──────────────────────────────────────────────────────

    @Test
    fun `accountingLedger_returns_ledger_entries`() = runTest {
        val repo = FakeReportRepository().apply {
            stubAccountingLedger = listOf(buildAccountingLedgerRecord("entry-01"))
        }

        GenerateAccountingLedgerReportUseCase(repo)(from, to).test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("entry-01", list.first().entryId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `hourlySales_returns_24_hour_breakdown`() = runTest {
        val hourlyData = (0..23).map { buildHourlySalesData(hour = it) }
        val repo = FakeReportRepository().apply { stubHourlySales = hourlyData }
        val date = LocalDate(2026, 3, 1)

        GenerateHourlySalesReportUseCase(repo)(date).test {
            val list = awaitItem()
            assertEquals(24, list.size)
            assertEquals(0, list.first().hour)
            assertEquals(23, list.last().hour)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `customerLoyalty_returns_loyalty_point_data`() = runTest {
        val repo = FakeReportRepository().apply {
            stubCustomerLoyalty = listOf(buildCustomerLoyaltyData("cust-01"), buildCustomerLoyaltyData("cust-02"))
        }

        GenerateCustomerLoyaltyReportUseCase(repo)(from, to).test {
            assertEquals(2, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `walletBalance_returns_active_wallet_balances`() = runTest {
        val repo = FakeReportRepository().apply {
            stubWalletBalances = listOf(buildWalletBalanceData("cust-01"), buildWalletBalanceData("cust-02"))
        }

        GenerateWalletBalanceReportUseCase(repo)().test {
            assertEquals(2, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cogs_returns_per_product_cost_data`() = runTest {
        val repo = FakeReportRepository().apply {
            stubCOGS = listOf(buildCOGSData("prod-01"), buildCOGSData("prod-02"))
        }

        GenerateCOGSReportUseCase(repo)(from, to).test {
            assertEquals(2, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `grossMargin_returns_margin_data_per_product`() = runTest {
        val repo = FakeReportRepository().apply {
            stubGrossMargin = listOf(buildGrossMarginData("prod-01"))
        }

        GenerateGrossMarginReportUseCase(repo)(from, to).test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("prod-01", list.first().productId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `annualSalesTrend_forwards_year_to_repository`() = runTest {
        val repo = FakeReportRepository().apply {
            stubAnnualSalesTrend = listOf(buildMonthlySalesData(year = 2026, month = 1))
        }

        GenerateAnnualSalesTrendReportUseCase(repo)(2026).test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals(2026, list.first().year)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(2026, repo.lastAnnualTrendYear)
    }

    @Test
    fun `inventoryValuation_returns_all_active_products`() = runTest {
        val repo = FakeReportRepository().apply {
            stubInventoryValuation = listOf(buildInventoryValuationData("prod-01"), buildInventoryValuationData("prod-02"))
        }

        GenerateInventoryValuationReportUseCase(repo)().test {
            assertEquals(2, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `customerRetention_returns_retention_metrics`() = runTest {
        val repo = FakeReportRepository().apply {
            stubCustomerRetention = buildCustomerRetentionData().copy(retentionRate = 75.0, churnRate = 25.0)
        }

        GenerateCustomerRetentionReportUseCase(repo)(from, to).test {
            val data = awaitItem()
            assertEquals(75.0, data.retentionRate)
            assertEquals(25.0, data.churnRate)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `purchaseOrder_returns_order_records`() = runTest {
        val repo = FakeReportRepository().apply {
            stubPurchaseOrders = listOf(buildPurchaseOrderData("po-01"), buildPurchaseOrderData("po-02"))
        }

        GeneratePurchaseOrderReportUseCase(repo)(from, to).test {
            assertEquals(2, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `expensesByDept_returns_departmental_expense_totals`() = runTest {
        val repo = FakeReportRepository().apply {
            stubExpensesByDept = listOf(buildDeptExpenseData("Operations"), buildDeptExpenseData("HR"))
        }

        GenerateExpenseSummaryByDeptReportUseCase(repo)(from, to).test {
            assertEquals(2, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─── Special-Repository Reports ───────────────────────────────────────────

    @Test
    fun `backupHistory_returns_backup_list_from_BackupRepository`() = runTest {
        val backupRepo = FakeBackupRepository()
        backupRepo.createBackup("backup-01", timestamp = 1_700_000_000_000L)
        backupRepo.createBackup("backup-02", timestamp = 1_700_000_001_000L)

        GenerateBackupHistoryReportUseCase(backupRepo)().test {
            // FakeBackupRepository._backups emits after createBackup updates the StateFlow
            val list = awaitItem()
            assertEquals(2, list.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `backupHistory_empty_repository_emits_empty_list`() = runTest {
        val backupRepo = FakeBackupRepository()

        GenerateBackupHistoryReportUseCase(backupRepo)().test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `permissionAudit_emits_full_RBAC_matrix_from_Permission_rolePermissions`() = runTest {
        GeneratePermissionAuditReportUseCase()().test {
            val matrix = awaitItem()
            assertTrue(matrix.isNotEmpty(), "RBAC matrix should contain at least one role")
            // Every known Role must appear in the matrix
            com.zyntasolutions.zyntapos.domain.model.Role.entries.forEach { role ->
                assertNotNull(matrix[role], "Matrix must contain an entry for $role")
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `systemAuditLog_filters_entries_to_date_window`() = runTest {
        val inWindow = buildAuditEntry("ae-in", createdAt = now)
        val outOfWindow = buildAuditEntry("ae-out", createdAt = now - kotlin.time.Duration.parse("PT1H"))
        val auditRepo = FakeAuditRepository(listOf(outOfWindow, inWindow))

        val windowFrom = now - kotlin.time.Duration.parse("PT30M")
        val windowTo = now + kotlin.time.Duration.parse("PT30M")

        GenerateSystemAuditLogReportUseCase(auditRepo)(windowFrom, windowTo).test {
            val list = awaitItem()
            assertEquals(1, list.size, "Only the in-window entry should be returned")
            assertEquals("ae-in", list.first().id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `systemAuditLog_applies_limit_cap_to_result_size`() = runTest {
        val entries = (1..10).map { buildAuditEntry("ae-$it", createdAt = now) }
        val auditRepo = FakeAuditRepository(entries)

        GenerateSystemAuditLogReportUseCase(auditRepo)(
            from = now - kotlin.time.Duration.parse("PT1H"),
            to = now + kotlin.time.Duration.parse("PT1H"),
            limit = 3,
        ).test {
            val list = awaitItem()
            assertEquals(3, list.size, "Result should be capped at limit=3")
            cancelAndIgnoreRemainingEvents()
        }
    }
}
