package com.zyntasolutions.zyntapos.domain.usecase.fakes

import com.zyntasolutions.zyntapos.domain.model.report.AccountingLedgerRecord
import com.zyntasolutions.zyntapos.domain.model.report.COGSData
import com.zyntasolutions.zyntapos.domain.model.report.CashMovementRecord
import com.zyntasolutions.zyntapos.domain.model.report.CategorySalesData
import com.zyntasolutions.zyntapos.domain.model.report.ClockRecord
import com.zyntasolutions.zyntapos.domain.model.report.CouponUsageData
import com.zyntasolutions.zyntapos.domain.model.report.CustomerLoyaltyData
import com.zyntasolutions.zyntapos.domain.model.report.CustomerRetentionData
import com.zyntasolutions.zyntapos.domain.model.report.CustomerSpendData
import com.zyntasolutions.zyntapos.domain.model.report.DailySalesSummaryData
import com.zyntasolutions.zyntapos.domain.model.report.DeptExpenseData
import com.zyntasolutions.zyntapos.domain.model.report.DiscountVoidData
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
import com.zyntasolutions.zyntapos.domain.repository.ReportRepository
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.time.Clock

// ─────────────────────────────────────────────────────────────────────────────
// Stub builders — minimal valid instances for each report data class
// ─────────────────────────────────────────────────────────────────────────────

private val now: Instant get() = Clock.System.now()

fun buildStaffSalesData(
    employeeId: String = "emp-01",
    employeeName: String = "Alice",
    totalSales: Double = 500.0,
    orderCount: Int = 10,
    averageBasket: Double = 50.0,
    voidCount: Int = 0,
) = StaffSalesData(employeeId, employeeName, totalSales, orderCount, averageBasket, voidCount)

fun buildStockReorderData(
    productId: String = "prod-01",
    productName: String = "Widget",
    currentStock: Int = 5,
    reorderPoint: Int = 10,
    suggestedReorderQty: Int = 20,
) = StockReorderData(productId, productName, currentStock, reorderPoint, suggestedReorderQty)

fun buildStockAgingData(
    productId: String = "prod-01",
    productName: String = "Widget",
    daysSinceLastSale: Int = 60,
    currentStock: Int = 100,
    stockValue: Double = 250.0,
) = StockAgingData(productId, productName, daysSinceLastSale, currentStock, stockValue)

fun buildHourlySalesData(hour: Int = 12, revenue: Double = 200.0, orderCount: Int = 5) =
    HourlySalesData(hour, revenue, orderCount)

fun buildMonthlySalesData(year: Int = 2026, month: Int = 1, revenue: Double = 10_000.0, orderCount: Int = 100) =
    MonthlySalesData(year, month, revenue, orderCount, momGrowthPct = 5.0)

fun buildStoreSalesData(storeId: String = "store-01", storeName: String = "Main") =
    StoreSalesData(storeId, storeName, totalRevenue = 5_000.0, orderCount = 50, averageOrderValue = 100.0)

fun buildInventoryValuationData(productId: String = "prod-01", productName: String = "Widget") =
    InventoryValuationData(productId, productName, quantityOnHand = 50, unitCost = 10.0, totalValue = 500.0)

fun buildLeaveBalanceData(employeeId: String = "emp-01", employeeName: String = "Alice") =
    LeaveBalanceData(employeeId, employeeName, leaveType = "ANNUAL", allottedDays = 14, takenDays = 3, remainingDays = 11)

fun buildPayrollSummaryData(employeeId: String = "emp-01", payPeriodId: String = "pp-2026-01") =
    PayrollSummaryData(employeeId, "Alice", payPeriodId, baseSalary = 50_000.0, overtimePay = 0.0, deductions = 5_000.0, netPay = 45_000.0)

fun buildShiftCoverageData(shiftId: String = "shift-01") =
    ShiftCoverageData(shiftId, "Morning", scheduledHours = 8.0, actualHours = 7.5, coveragePct = 93.75)

fun buildGrossMarginData(productId: String = "prod-01") =
    GrossMarginData(productId, "Widget", revenue = 200.0, cogs = 100.0, grossMargin = 100.0, marginPct = 50.0)

fun buildCOGSData(productId: String = "prod-01") =
    COGSData(productId, "Widget", unitsSold = 20, costPerUnit = 5.0, totalCOGS = 100.0, revenue = 200.0)

fun buildAccountingLedgerRecord(entryId: String = "entry-01") =
    AccountingLedgerRecord(entryId, entryType = "CREDIT", accountName = "Sales", amount = 100.0, description = "Sale", recordedAt = now)

fun buildWalletBalanceData(customerId: String = "cust-01") =
    WalletBalanceData(customerId, "Bob", balance = 150.0, lastTransactionAt = now)

fun buildCustomerLoyaltyData(customerId: String = "cust-01") =
    CustomerLoyaltyData(customerId, "Bob", pointsEarned = 500, pointsRedeemed = 100, pointsBalance = 400)

fun buildSupplierPurchaseData(supplierId: String = "sup-01") =
    SupplierPurchaseData(supplierId, "Acme Corp", totalPurchaseAmount = 10_000.0, orderCount = 5)

fun buildPurchaseOrderData(orderId: String = "po-01") =
    PurchaseOrderData(orderId, "sup-01", "Acme Corp", totalAmount = 2_000.0, status = "RECEIVED", createdAt = now)

fun buildDeptExpenseData(department: String = "Operations") =
    DeptExpenseData(department, totalAmount = 5_000.0, expenseCount = 10)

fun buildCustomerRetentionData() =
    CustomerRetentionData(totalCustomers = 100, newCustomers = 30, returningCustomers = 70, retentionRate = 70.0, churnRate = 30.0)

fun buildReturnRefundData() =
    ReturnRefundData(totalReturns = 5, totalRefundAmount = 500.0, byProduct = emptyList())

fun buildWarehouseInventoryData(warehouseId: String = "wh-01") =
    WarehouseInventoryData(warehouseId, "Warehouse A", "rack-01", "A1", "prod-01", "Widget", quantity = 50)

fun buildClockRecord(employeeId: String = "emp-01") =
    ClockRecord(employeeId, "Alice", clockIn = now, clockOut = null, totalHours = 0.0)

fun buildStockTransferRecord(transferId: String = "tr-01") =
    StockTransferRecord(transferId, "store-01", "store-02", "prod-01", "Widget", quantity = 10, transferredAt = now)

fun buildStaffAttendanceData(employeeId: String = "emp-01") =
    StaffAttendanceData(employeeId, "Alice", daysPresent = 20, daysAbsent = 2, daysLate = 1, totalHours = 160.0)

// ─────────────────────────────────────────────────────────────────────────────
// FakeReportRepository
// ─────────────────────────────────────────────────────────────────────────────

/**
 * In-memory fake for [ReportRepository].
 *
 * Each `stubXxx` property can be pre-loaded before invoking a use case.
 * Defaults to empty lists / zeroed aggregate objects so tests that only
 * care about delegation remain concise.
 */
class FakeReportRepository : ReportRepository {

    var stubDailySummary = DailySalesSummaryData(
        date = LocalDate(2026, 3, 1),
        totalRevenue = 0.0, totalOrders = 0, averageOrderValue = 0.0,
        totalItemsSold = 0, openingCash = 0.0, closingCash = 0.0,
        cashRevenue = 0.0, cardRevenue = 0.0,
    )
    var stubCategoryBreakdown: List<CategorySalesData> = emptyList()
    var stubCashMovements: List<CashMovementRecord> = emptyList()
    var stubTopProducts: List<ProductVolumeData> = emptyList()
    var stubProfitLoss = ProfitLossData(now, now, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
    var stubProductPerformance: List<ProductPerformanceData> = emptyList()
    var stubCouponUsage = CouponUsageData(0, 0.0, emptyList())
    var stubPaymentMethods: Map<String, Double> = emptyMap()
    var stubTaxCollection: List<TaxCollectionData> = emptyList()
    var stubDiscountVoid = DiscountVoidData(0.0, 0, 0.0, emptyList())
    var stubTopCustomers: List<CustomerSpendData> = emptyList()

    var stubStaffAttendance: List<StaffAttendanceData> = emptyList()
    var stubStaffSales: List<StaffSalesData> = emptyList()
    var stubPayrollSummary: List<PayrollSummaryData> = emptyList()
    var stubLeaveBalances: List<LeaveBalanceData> = emptyList()
    var stubShiftCoverage: List<ShiftCoverageData> = emptyList()

    var stubMultiStoreComparison: List<StoreSalesData> = emptyList()
    var stubInterStoreTransfers: List<StockTransferRecord> = emptyList()
    var stubWarehouseInventory: List<WarehouseInventoryData> = emptyList()

    var stubStockAging: List<StockAgingData> = emptyList()
    var stubStockReorderAlerts: List<StockReorderData> = emptyList()
    var stubSupplierPurchases: List<SupplierPurchaseData> = emptyList()
    var stubReturnRefund = ReturnRefundData(0, 0.0, emptyList())

    var stubAccountingLedger: List<AccountingLedgerRecord> = emptyList()
    var stubHourlySales: List<HourlySalesData> = emptyList()
    var stubCustomerLoyalty: List<CustomerLoyaltyData> = emptyList()
    var stubWalletBalances: List<WalletBalanceData> = emptyList()
    var stubCOGS: List<COGSData> = emptyList()
    var stubGrossMargin: List<GrossMarginData> = emptyList()
    var stubAnnualSalesTrend: List<MonthlySalesData> = emptyList()
    var stubClockInOut: List<ClockRecord> = emptyList()
    var stubInventoryValuation: List<InventoryValuationData> = emptyList()
    var stubCustomerRetention = buildCustomerRetentionData()
    var stubPurchaseOrders: List<PurchaseOrderData> = emptyList()
    var stubExpensesByDept: List<DeptExpenseData> = emptyList()

    // ── Spy tracking ────────────────────────────────────────────────────────

    var lastStockAgingNoSalesDays: Int? = null
    var lastPayrollPeriodId: String? = null
    var lastAnnualTrendYear: Int? = null
    var lastClockEmployeeId: String? = null
    var lastTopProductsLimit: Int? = null
    var lastTopCustomersLimit: Int? = null

    // ── Standard Reports ────────────────────────────────────────────────────

    override suspend fun getDailySalesSummary(date: LocalDate) = stubDailySummary

    override suspend fun getSalesByCategory(from: Instant, to: Instant) = stubCategoryBreakdown

    override suspend fun getCashMovementLog(from: Instant, to: Instant) = stubCashMovements

    override suspend fun getTopProductsByVolume(from: Instant, to: Instant, limit: Int): List<ProductVolumeData> {
        lastTopProductsLimit = limit
        return stubTopProducts
    }

    override suspend fun getProfitLoss(from: Instant, to: Instant) = stubProfitLoss

    override suspend fun getProductPerformance(from: Instant, to: Instant) = stubProductPerformance

    override suspend fun getCouponUsage(from: Instant, to: Instant) = stubCouponUsage

    override suspend fun getPaymentMethodBreakdown(from: Instant, to: Instant) = stubPaymentMethods

    override suspend fun getTaxCollection(from: Instant, to: Instant) = stubTaxCollection

    override suspend fun getDiscountVoidAnalysis(from: Instant, to: Instant) = stubDiscountVoid

    override suspend fun getTopCustomers(from: Instant, to: Instant, limit: Int): List<CustomerSpendData> {
        lastTopCustomersLimit = limit
        return stubTopCustomers
    }

    // ── Enterprise Reports — Staff ──────────────────────────────────────────

    override suspend fun getStaffAttendanceSummary(from: Instant, to: Instant) = stubStaffAttendance

    override suspend fun getStaffSalesSummary(from: Instant, to: Instant) = stubStaffSales

    override suspend fun getPayrollSummary(payPeriodId: String): List<PayrollSummaryData> {
        lastPayrollPeriodId = payPeriodId
        return stubPayrollSummary
    }

    override suspend fun getLeaveBalances(asOf: Instant) = stubLeaveBalances

    override suspend fun getShiftCoverage(from: Instant, to: Instant) = stubShiftCoverage

    // ── Enterprise Reports — Multi-Store ────────────────────────────────────

    override suspend fun getMultiStoreComparison(from: Instant, to: Instant) = stubMultiStoreComparison

    override suspend fun getInterStoreTransfers(from: Instant, to: Instant) = stubInterStoreTransfers

    override suspend fun getWarehouseInventory() = stubWarehouseInventory

    // ── Enterprise Reports — Inventory ──────────────────────────────────────

    override suspend fun getStockAging(noSalesDays: Int): List<StockAgingData> {
        lastStockAgingNoSalesDays = noSalesDays
        return stubStockAging
    }

    override suspend fun getStockReorderAlerts() = stubStockReorderAlerts

    override suspend fun getSupplierPurchases(from: Instant, to: Instant) = stubSupplierPurchases

    override suspend fun getReturnRefundSummary(from: Instant, to: Instant) = stubReturnRefund

    // ── Enterprise Reports — Finance ────────────────────────────────────────

    override suspend fun getAccountingLedger(from: Instant, to: Instant) = stubAccountingLedger

    override suspend fun getHourlySales(date: LocalDate) = stubHourlySales

    override suspend fun getCustomerLoyaltySummary(from: Instant, to: Instant) = stubCustomerLoyalty

    override suspend fun getWalletBalances() = stubWalletBalances

    override suspend fun getCOGS(from: Instant, to: Instant) = stubCOGS

    override suspend fun getGrossMargin(from: Instant, to: Instant) = stubGrossMargin

    override suspend fun getAnnualSalesTrend(year: Int): List<MonthlySalesData> {
        lastAnnualTrendYear = year
        return stubAnnualSalesTrend
    }

    override suspend fun getClockInOutLog(from: Instant, to: Instant, employeeId: String?): List<ClockRecord> {
        lastClockEmployeeId = employeeId
        return stubClockInOut
    }

    override suspend fun getInventoryValuation() = stubInventoryValuation

    override suspend fun getCustomerRetentionMetrics(from: Instant, to: Instant) = stubCustomerRetention

    override suspend fun getPurchaseOrders(from: Instant, to: Instant) = stubPurchaseOrders

    override suspend fun getExpensesByDepartment(from: Instant, to: Instant) = stubExpensesByDept
}
