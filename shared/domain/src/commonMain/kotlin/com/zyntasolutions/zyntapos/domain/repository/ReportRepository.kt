package com.zyntasolutions.zyntapos.domain.repository

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
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

/** Cross-table aggregate query source for all report use cases. */
interface ReportRepository {

    // ── Standard Reports ────────────────────────────────────────────────────

    /**
     * Returns an aggregated sales summary for the given calendar [date],
     * including total revenue, order count, average order value, and cash balances.
     */
    suspend fun getDailySalesSummary(date: LocalDate): DailySalesSummaryData

    /**
     * Returns revenue totals broken down by product category for the given date range.
     * Includes each category's share of total revenue as a percentage.
     */
    suspend fun getSalesByCategory(from: Instant, to: Instant): List<CategorySalesData>

    /**
     * Returns the cash-in / cash-out movement log for the given date range,
     * ordered by [CashMovementRecord.recordedAt] ascending.
     */
    suspend fun getCashMovementLog(from: Instant, to: Instant): List<CashMovementRecord>

    /**
     * Returns the top [limit] products ranked by units sold in the given date range.
     * Results are ordered by [ProductVolumeData.rank] ascending (rank 1 = highest volume).
     */
    suspend fun getTopProductsByVolume(from: Instant, to: Instant, limit: Int): List<ProductVolumeData>

    // ── Premium Reports ─────────────────────────────────────────────────────

    /**
     * Returns a profit-and-loss statement for the given date range, including
     * gross profit, total expenses, net profit, and gross margin percentage.
     */
    suspend fun getProfitLoss(from: Instant, to: Instant): ProfitLossData

    /**
     * Returns per-product performance metrics for the given date range,
     * including units sold, revenue, COGS, margin, and return count.
     */
    suspend fun getProductPerformance(from: Instant, to: Instant): List<ProductPerformanceData>

    /**
     * Returns aggregated coupon redemption totals for the given date range,
     * including a breakdown of redemption counts by coupon code.
     */
    suspend fun getCouponUsage(from: Instant, to: Instant): CouponUsageData

    /**
     * Returns a revenue breakdown by payment method (e.g., CASH, CARD, MOBILE)
     * for the given date range. Keys are [PaymentMethod] name strings.
     */
    suspend fun getPaymentMethodBreakdown(from: Instant, to: Instant): Map<String, Double>

    /**
     * Returns tax collection totals grouped by tax group for the given date range,
     * including taxable amount and tax collected per group.
     */
    suspend fun getTaxCollection(from: Instant, to: Instant): List<TaxCollectionData>

    /**
     * Returns discount and void analysis for the given date range,
     * including totals and a per-cashier breakdown.
     */
    suspend fun getDiscountVoidAnalysis(from: Instant, to: Instant): DiscountVoidData

    /**
     * Returns the top [limit] customers ranked by total spend in the given date range.
     * Results are ordered by [CustomerSpendData.totalSpend] descending.
     */
    suspend fun getTopCustomers(from: Instant, to: Instant, limit: Int): List<CustomerSpendData>

    // ── Enterprise Reports — Staff ──────────────────────────────────────────

    /**
     * Returns attendance summary records (days present, absent, late, and total hours)
     * for all employees over the given date range.
     */
    suspend fun getStaffAttendanceSummary(from: Instant, to: Instant): List<StaffAttendanceData>

    /**
     * Returns per-employee sales performance metrics (total sales, order count,
     * average basket, void count) for the given date range.
     */
    suspend fun getStaffSalesSummary(from: Instant, to: Instant): List<StaffSalesData>

    /**
     * Returns payroll summary records (base salary, overtime pay, deductions, net pay)
     * for all employees in the pay period identified by [payPeriodId].
     */
    suspend fun getPayrollSummary(payPeriodId: String): List<PayrollSummaryData>

    /**
     * Returns leave balance records (allotted, taken, remaining days per leave type)
     * for all employees as of the given [asOf] point in time.
     */
    suspend fun getLeaveBalances(asOf: Instant): List<LeaveBalanceData>

    /**
     * Returns shift coverage metrics (scheduled vs. actual hours and coverage percentage)
     * for all shifts that overlap the given date range.
     */
    suspend fun getShiftCoverage(from: Instant, to: Instant): List<ShiftCoverageData>

    // ── Enterprise Reports — Multi-Store ────────────────────────────────────

    /**
     * Returns sales comparison data for all stores over the given date range,
     * including revenue, order count, and average order value per store.
     */
    suspend fun getMultiStoreComparison(from: Instant, to: Instant): List<StoreSalesData>

    /**
     * Returns inter-store stock transfer records for the given date range,
     * ordered by [StockTransferRecord.transferredAt] ascending.
     */
    suspend fun getInterStoreTransfers(from: Instant, to: Instant): List<StockTransferRecord>

    /**
     * Returns the current inventory snapshot for all warehouses,
     * including rack-level product quantities.
     */
    suspend fun getWarehouseInventory(): List<WarehouseInventoryData>

    // ── Enterprise Reports — Inventory ──────────────────────────────────────

    /**
     * Returns products that have not had a sale in at least [noSalesDays] days,
     * along with their current stock quantity and value.
     */
    suspend fun getStockAging(noSalesDays: Int): List<StockAgingData>

    /**
     * Returns products whose current stock has reached or fallen below their reorder point,
     * along with the suggested reorder quantity.
     */
    suspend fun getStockReorderAlerts(): List<StockReorderData>

    /**
     * Returns purchase totals grouped by supplier for the given date range,
     * including total purchase amount and order count per supplier.
     */
    suspend fun getSupplierPurchases(from: Instant, to: Instant): List<SupplierPurchaseData>

    /**
     * Returns a summary of customer returns and refunds for the given date range,
     * including a per-product breakdown of return counts.
     */
    suspend fun getReturnRefundSummary(from: Instant, to: Instant): ReturnRefundData

    // ── Enterprise Reports — Finance ────────────────────────────────────────

    /**
     * Returns e-invoice submission status records for the given date range,
     * ordered by [EInvoiceStatusData.submittedAt] ascending.
     */
    suspend fun getEInvoiceStatus(from: Instant, to: Instant): List<EInvoiceStatusData>

    /**
     * Returns accounting ledger entries for the given date range,
     * ordered by [AccountingLedgerRecord.recordedAt] ascending.
     */
    suspend fun getAccountingLedger(from: Instant, to: Instant): List<AccountingLedgerRecord>

    /**
     * Returns an hourly breakdown of sales (revenue and order count) for the given calendar [date].
     * The result contains one [HourlySalesData] entry per hour in the store's operating day (0–23).
     */
    suspend fun getHourlySales(date: LocalDate): List<HourlySalesData>

    /**
     * Returns loyalty point activity (earned, redeemed, balance) for all customers
     * over the given date range.
     */
    suspend fun getCustomerLoyaltySummary(from: Instant, to: Instant): List<CustomerLoyaltyData>

    /**
     * Returns the current wallet (store-credit) balance for all customers
     * who hold an active wallet, including the last transaction timestamp.
     */
    suspend fun getWalletBalances(): List<WalletBalanceData>

    /**
     * Returns cost-of-goods-sold data per product for the given date range,
     * including units sold, cost per unit, total COGS, and revenue.
     */
    suspend fun getCOGS(from: Instant, to: Instant): List<COGSData>

    /**
     * Returns gross margin data per product for the given date range,
     * including revenue, COGS, gross margin amount, and margin percentage.
     */
    suspend fun getGrossMargin(from: Instant, to: Instant): List<GrossMarginData>

    /**
     * Returns monthly sales totals for the given [year], including revenue,
     * order count, and month-over-month growth percentage.
     */
    suspend fun getAnnualSalesTrend(year: Int): List<MonthlySalesData>

    /**
     * Returns clock-in / clock-out records for the given date range.
     * Pass [employeeId] to filter to a single employee, or `null` to return all staff.
     */
    suspend fun getClockInOutLog(from: Instant, to: Instant, employeeId: String?): List<ClockRecord>

    /**
     * Returns the current inventory valuation snapshot for all active products,
     * including quantity on hand, unit cost, and total stock value.
     */
    suspend fun getInventoryValuation(): List<InventoryValuationData>

    /**
     * Returns customer retention metrics (new vs. returning customers, retention rate,
     * churn rate) for the given date range.
     */
    suspend fun getCustomerRetentionMetrics(from: Instant, to: Instant): CustomerRetentionData

    /**
     * Returns purchase order records for the given date range,
     * ordered by [PurchaseOrderData.createdAt] ascending.
     */
    suspend fun getPurchaseOrders(from: Instant, to: Instant): List<PurchaseOrderData>

    /**
     * Returns expense totals grouped by department for the given date range,
     * including total amount and expense count per department.
     */
    suspend fun getExpensesByDepartment(from: Instant, to: Instant): List<DeptExpenseData>
}
