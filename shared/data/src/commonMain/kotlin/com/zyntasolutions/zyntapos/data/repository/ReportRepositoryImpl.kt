package com.zyntasolutions.zyntapos.data.repository

import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.report.AccountingLedgerRecord
import com.zyntasolutions.zyntapos.domain.model.report.COGSData
import com.zyntasolutions.zyntapos.domain.model.report.CashMovementRecord
import com.zyntasolutions.zyntapos.domain.model.report.CategorySalesData
import com.zyntasolutions.zyntapos.domain.model.report.ClockRecord
import com.zyntasolutions.zyntapos.domain.model.report.CouponCodeData
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
import com.zyntasolutions.zyntapos.domain.model.report.ProductReturnData
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
import com.zyntasolutions.zyntapos.domain.model.report.CashierDiscountVoidData
import com.zyntasolutions.zyntapos.domain.repository.ReportRepository
import com.zyntasolutions.zyntapos.core.utils.AppTimezone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/**
 * Concrete implementation of [ReportRepository] backed by SQLDelight-generated queries
 * in `reports.sq`.
 *
 * All methods dispatch on [Dispatchers.IO] and return pure Kotlin data objects
 * (no [kotlinx.coroutines.flow.Flow] — reports are always one-shot snapshots).
 */
class ReportRepositoryImpl(
    private val db: ZyntaDatabase,
) : ReportRepository {

    private val q get() = db.reportsQueries
    private val tz: TimeZone get() = AppTimezone.current

    // ── Standard Reports ──────────────────────────────────────────────────────

    override suspend fun getDailySalesSummary(date: LocalDate): DailySalesSummaryData =
        withContext(Dispatchers.IO) {
            val dateStr = date.toString()  // "YYYY-MM-DD"
            val row = q.dailySalesSummary(dateStr).executeAsOne()

            // Opening / closing cash from register sessions that cover this date
            val _dayStart = date.atStartOfDayIn(tz).toEpochMilliseconds()
            val _dayEnd   = LocalDate(date.year, date.monthNumber, date.dayOfMonth)
                .atStartOfDayIn(tz).toEpochMilliseconds() + 86_400_000L

            val openingCash = db.registersQueries
                .getActiveSession()
                .executeAsOneOrNull()
                ?.opening_balance ?: 0.0

            val closingCash = db.registersQueries
                .getActiveSession()
                .executeAsOneOrNull()
                ?.closing_balance ?: 0.0

            DailySalesSummaryData(
                date               = date,
                totalRevenue       = row.total_revenue ?: 0.0,
                totalOrders        = (row.total_orders ?: 0L).toInt(),
                averageOrderValue  = row.average_order_value ?: 0.0,
                totalItemsSold     = (row.total_items_sold ?: 0.0).toInt(),
                openingCash        = openingCash,
                closingCash        = closingCash,
                cashRevenue        = row.cash_revenue ?: 0.0,
                cardRevenue        = row.card_revenue ?: 0.0,
            )
        }

    override suspend fun getSalesByCategory(from: Instant, to: Instant): List<CategorySalesData> =
        withContext(Dispatchers.IO) {
            val fromMs  = from.toEpochMilliseconds()
            val toMs    = to.toEpochMilliseconds()
            val rows    = q.salesByCategory(fromMs, toMs).executeAsList()
            val total   = q.totalRevenueForRange(fromMs, toMs).executeAsOne()

            rows.map { row ->
                val revenue = row.revenue ?: 0.0
                CategorySalesData(
                    categoryId       = row.category_id,
                    categoryName     = row.category_name,
                    revenue          = revenue,
                    orderCount       = (row.order_count ?: 0L).toInt(),
                    itemCount        = (row.item_count ?: 0.0).toInt(),
                    revenueSharePct  = if (total > 0.0) (revenue / total) * 100.0 else 0.0,
                )
            }
        }

    override suspend fun getCashMovementLog(from: Instant, to: Instant): List<CashMovementRecord> =
        withContext(Dispatchers.IO) {
            q.cashMovements(
                from.toEpochMilliseconds(),
                to.toEpochMilliseconds(),
            ).executeAsList().map { row ->
                CashMovementRecord(
                    id          = row.id,
                    type        = row.type,
                    amount      = row.amount,
                    reason      = row.reason,
                    recordedBy  = row.recorded_by,
                    recordedAt  = Instant.fromEpochMilliseconds(row.recorded_at),
                )
            }
        }

    override suspend fun getTopProductsByVolume(
        from: Instant,
        to: Instant,
        limit: Int,
    ): List<ProductVolumeData> =
        withContext(Dispatchers.IO) {
            q.topProductsByVolume(
                from.toEpochMilliseconds(),
                to.toEpochMilliseconds(),
                limit.toLong(),
            ).executeAsList().mapIndexed { index, row ->
                ProductVolumeData(
                    rank        = index + 1,
                    productId   = row.product_id,
                    productName = row.product_name,
                    unitsSold   = (row.units_sold ?: 0.0).toInt(),
                    revenue     = row.revenue ?: 0.0,
                )
            }
        }

    // ── Premium Reports ───────────────────────────────────────────────────────

    override suspend fun getProfitLoss(from: Instant, to: Instant): ProfitLossData =
        withContext(Dispatchers.IO) {
            val fromMs = from.toEpochMilliseconds()
            val toMs   = to.toEpochMilliseconds()

            val revenue  = q.profitLossRevenue(fromMs, toMs).executeAsOne()
            val cogs     = q.profitLossCOGS(fromMs, toMs).executeAsOne()
            val expenses = q.profitLossExpenses(fromMs, toMs).executeAsOne()

            val grossProfit    = revenue - cogs
            val netProfit      = grossProfit - expenses
            val grossMarginPct = if (revenue > 0.0) (grossProfit / revenue) * 100.0 else 0.0

            ProfitLossData(
                from           = from,
                to             = to,
                totalRevenue   = revenue,
                totalCOGS      = cogs,
                grossProfit    = grossProfit,
                totalExpenses  = expenses,
                netProfit      = netProfit,
                grossMarginPct = grossMarginPct,
            )
        }

    override suspend fun getProductPerformance(
        from: Instant,
        to: Instant,
    ): List<ProductPerformanceData> =
        withContext(Dispatchers.IO) {
            val fromMs = from.toEpochMilliseconds()
            val toMs   = to.toEpochMilliseconds()
            q.productPerformance(fromMs, toMs, fromMs, toMs).executeAsList().map { row ->
                val revenue      = row.revenue ?: 0.0
                val cogs         = row.cogs ?: 0.0
                val grossMargin  = revenue - cogs
                val marginPct    = if (revenue > 0.0) (grossMargin / revenue) * 100.0 else 0.0
                ProductPerformanceData(
                    productId       = row.product_id,
                    productName     = row.product_name,
                    unitsSold       = (row.units_sold ?: 0.0).toInt(),
                    revenue         = revenue,
                    cogs            = cogs,
                    grossMargin     = grossMargin,
                    grossMarginPct  = marginPct,
                    returnCount     = (row.return_count ?: 0L).toInt(),
                )
            }
        }

    override suspend fun getCouponUsage(from: Instant, to: Instant): CouponUsageData =
        withContext(Dispatchers.IO) {
            val rows = q.couponUsageSummary(
                from.toEpochMilliseconds(),
                to.toEpochMilliseconds(),
            ).executeAsList()

            val totalRedemptions   = rows.sumOf { (it.redemptions ?: 0L).toInt() }
            val totalDiscountGiven = rows.sumOf { it.total_discount ?: 0.0 }

            CouponUsageData(
                totalRedemptions   = totalRedemptions,
                totalDiscountGiven = totalDiscountGiven,
                byCode             = rows.map { row ->
                    CouponCodeData(
                        code           = row.code,
                        redemptions    = (row.redemptions ?: 0L).toInt(),
                        totalDiscount  = row.total_discount ?: 0.0,
                    )
                },
            )
        }

    override suspend fun getPaymentMethodBreakdown(
        from: Instant,
        to: Instant,
    ): Map<String, Double> =
        withContext(Dispatchers.IO) {
            q.paymentMethodBreakdown(
                from.toEpochMilliseconds(),
                to.toEpochMilliseconds(),
            ).executeAsList()
                .associate { row -> row.payment_method to (row.revenue ?: 0.0) }
        }

    override suspend fun getTaxCollection(
        from: Instant,
        to: Instant,
    ): List<TaxCollectionData> =
        withContext(Dispatchers.IO) {
            q.taxCollection(
                from.toEpochMilliseconds(),
                to.toEpochMilliseconds(),
            ).executeAsList().map { row ->
                TaxCollectionData(
                    taxGroupId     = row.tax_group_id,
                    taxGroupName   = row.tax_group_name,
                    taxRate        = row.tax_rate,
                    taxableAmount  = row.taxable_amount ?: 0.0,
                    taxCollected   = row.tax_collected ?: 0.0,
                )
            }
        }

    override suspend fun getDiscountVoidAnalysis(
        from: Instant,
        to: Instant,
    ): DiscountVoidData =
        withContext(Dispatchers.IO) {
            val fromMs = from.toEpochMilliseconds()
            val toMs   = to.toEpochMilliseconds()
            val totals = q.discountAnalysisTotals(fromMs, toMs).executeAsOne()
            val byCashier = q.discountAnalysisByCashier(fromMs, toMs).executeAsList()

            // Resolve cashier names from the users table
            val userNames: Map<String, String> = byCashier.associate { row ->
                val name = db.usersQueries.getUserById(row.cashier_id)
                    .executeAsOneOrNull()?.name ?: row.cashier_id
                row.cashier_id to name
            }

            DiscountVoidData(
                totalDiscountAmount = totals.total_discount_amount ?: 0.0,
                totalVoidCount      = (totals.total_void_count ?: 0L).toInt(),
                totalVoidAmount     = totals.total_void_amount ?: 0.0,
                byCashier           = byCashier.map { row ->
                    CashierDiscountVoidData(
                        cashierId      = row.cashier_id,
                        cashierName    = userNames[row.cashier_id] ?: row.cashier_id,
                        discountAmount = row.discount_amount ?: 0.0,
                        voidCount      = (row.void_count ?: 0L).toInt(),
                        voidAmount     = row.void_amount ?: 0.0,
                    )
                },
            )
        }

    override suspend fun getTopCustomers(
        from: Instant,
        to: Instant,
        limit: Int,
    ): List<CustomerSpendData> =
        withContext(Dispatchers.IO) {
            q.topCustomersBySpend(
                from.toEpochMilliseconds(),
                to.toEpochMilliseconds(),
                limit.toLong(),
            ).executeAsList().map { row ->
                CustomerSpendData(
                    customerId        = row.customer_id,
                    customerName      = row.customer_name,
                    totalSpend        = row.total_spend ?: 0.0,
                    orderCount        = (row.order_count ?: 0L).toInt(),
                    averageOrderValue = row.average_order_value ?: 0.0,
                )
            }
        }

    // ── Enterprise Reports — Staff ────────────────────────────────────────────

    override suspend fun getStaffAttendanceSummary(
        from: Instant,
        to: Instant,
    ): List<StaffAttendanceData> =
        withContext(Dispatchers.IO) {
            // attendance_records uses TEXT ISO timestamps; pass as "YYYY-MM-DDTHH:mm:ss" prefix
            val fromStr = from.toLocalDateTime(tz).date.toString()
            val toStr   = to.toLocalDateTime(tz).date.toString() + "T23:59:59"
            q.staffAttendanceSummary(fromStr, toStr).executeAsList().map { row ->
                StaffAttendanceData(
                    employeeId   = row.employee_id,
                    employeeName = row.employee_name,
                    daysPresent  = (row.days_present ?: 0L).toInt(),
                    daysAbsent   = (row.days_absent ?: 0L).toInt(),
                    daysLate     = (row.days_late ?: 0L).toInt(),
                    totalHours   = row.total_hours ?: 0.0,
                )
            }
        }

    override suspend fun getStaffSalesSummary(
        from: Instant,
        to: Instant,
    ): List<StaffSalesData> =
        withContext(Dispatchers.IO) {
            q.staffSalesSummary(
                from.toEpochMilliseconds(),
                to.toEpochMilliseconds(),
            ).executeAsList().map { row ->
                StaffSalesData(
                    employeeId   = row.employee_id,
                    employeeName = row.employee_name,
                    totalSales   = row.total_sales ?: 0.0,
                    orderCount   = (row.order_count ?: 0L).toInt(),
                    averageBasket= row.average_basket ?: 0.0,
                    voidCount    = (row.void_count ?: 0L).toInt(),
                )
            }
        }

    override suspend fun getPayrollSummary(payPeriodId: String): List<PayrollSummaryData> =
        withContext(Dispatchers.IO) {
            q.payrollSummary(payPeriodId).executeAsList().map { row ->
                PayrollSummaryData(
                    employeeId   = row.employee_id,
                    employeeName = row.employee_name,
                    payPeriodId  = row.pay_period_id,
                    baseSalary   = row.base_salary,
                    overtimePay  = row.overtime_pay,
                    deductions   = row.deductions,
                    netPay       = row.net_pay,
                )
            }
        }

    override suspend fun getLeaveBalances(asOf: Instant): List<LeaveBalanceData> =
        withContext(Dispatchers.IO) {
            val asOfStr = asOf.toLocalDateTime(tz).date.toString()
            // Pass asOfStr twice: once for year extraction in the subquery, once for end_date comparison.
            // allotted_days resolved from leave_allotments table (Phase 3 Sprint 13+).
            q.leaveBalances(asOfStr, asOfStr).executeAsList().map { row ->
                val allotted = (row.allotted_days ?: 0L).toInt()
                val taken    = (row.taken_days ?: 0L).toInt()
                LeaveBalanceData(
                    employeeId   = row.employee_id,
                    employeeName = row.employee_name,
                    leaveType    = row.leave_type,
                    allottedDays = allotted,
                    takenDays    = taken,
                    remainingDays= maxOf(0, allotted - taken),
                )
            }
        }

    override suspend fun getShiftCoverage(
        from: Instant,
        to: Instant,
    ): List<ShiftCoverageData> =
        withContext(Dispatchers.IO) {
            val fromStr = from.toLocalDateTime(tz).date.toString()
            val toStr   = to.toLocalDateTime(tz).date.toString()
            q.shiftCoverage(fromStr, toStr).executeAsList().map { row ->
                val scheduled = row.scheduled_hours ?: 0.0
                val actual    = row.actual_hours ?: 0.0
                val coverage  = if (scheduled > 0.0) (actual / scheduled) * 100.0 else 0.0
                ShiftCoverageData(
                    shiftId       = row.shift_id,
                    shiftName     = row.shift_name,
                    scheduledHours= scheduled,
                    actualHours   = actual,
                    coveragePct   = coverage,
                )
            }
        }

    // ── Enterprise Reports — Multi-Store ──────────────────────────────────────

    override suspend fun getMultiStoreComparison(
        from: Instant,
        to: Instant,
    ): List<StoreSalesData> =
        withContext(Dispatchers.IO) {
            q.multiStoreSales(
                from.toEpochMilliseconds(),
                to.toEpochMilliseconds(),
            ).executeAsList().map { row ->
                StoreSalesData(
                    storeId           = row.store_id,
                    storeName         = row.store_name, // Resolved from stores table (Phase 3 Sprint 13+)
                    totalRevenue      = row.total_revenue ?: 0.0,
                    orderCount        = (row.order_count ?: 0L).toInt(),
                    averageOrderValue = row.average_order_value ?: 0.0,
                )
            }
        }

    override suspend fun getInterStoreTransfers(
        from: Instant,
        to: Instant,
    ): List<StockTransferRecord> =
        withContext(Dispatchers.IO) {
            q.interStoreTransfers(
                from.toEpochMilliseconds(),
                to.toEpochMilliseconds(),
            ).executeAsList().mapNotNull { row ->
                val transferredAt = row.transferred_at ?: return@mapNotNull null
                // Warehouse store IDs resolved via warehouses table in-memory
                val srcWarehouse = db.warehousesQueries.getWarehouseById(row.source_warehouse_id)
                    .executeAsOneOrNull()
                val dstWarehouse = db.warehousesQueries.getWarehouseById(row.dest_warehouse_id)
                    .executeAsOneOrNull()
                StockTransferRecord(
                    transferId   = row.transfer_id,
                    fromStoreId  = srcWarehouse?.store_id ?: row.source_warehouse_id,
                    toStoreId    = dstWarehouse?.store_id ?: row.dest_warehouse_id,
                    productId    = row.product_id,
                    productName  = row.product_name,
                    quantity     = row.quantity.toInt(),
                    transferredAt= Instant.fromEpochMilliseconds(transferredAt),
                )
            }
        }

    override suspend fun getWarehouseInventory(): List<WarehouseInventoryData> =
        withContext(Dispatchers.IO) {
            // warehouseInventory now LEFT JOINs rack_products for product-level data.
            // Racks with no assigned products return empty strings and zero quantity.
            q.warehouseInventory().executeAsList().map { row ->
                WarehouseInventoryData(
                    warehouseId   = row.warehouse_id,
                    warehouseName = row.warehouse_name,
                    rackId        = row.rack_id,
                    rackCode      = row.rack_code,
                    productId     = row.product_id,
                    productName   = row.product_name,
                    quantity      = (row.quantity ?: 0L).toInt(),
                )
            }
        }

    // ── Enterprise Reports — Inventory ────────────────────────────────────────

    override suspend fun getStockAging(noSalesDays: Int): List<StockAgingData> =
        withContext(Dispatchers.IO) {
            val thresholdMs = System.currentTimeMillis() - (noSalesDays.toLong() * 86_400_000L)
            q.stockAging(thresholdMs).executeAsList().map { row ->
                val lastSaleMs  = row.last_sale_at ?: 0L
                val daysSince   = if (lastSaleMs > 0L)
                    ((System.currentTimeMillis() - lastSaleMs) / 86_400_000L).toInt()
                else noSalesDays
                StockAgingData(
                    productId         = row.product_id,
                    productName       = row.product_name,
                    daysSinceLastSale = daysSince,
                    currentStock      = row.stock_qty.toInt(),
                    stockValue        = row.stock_qty * row.cost_price,
                )
            }
        }

    override suspend fun getStockReorderAlerts(): List<StockReorderData> =
        withContext(Dispatchers.IO) {
            q.stockReorderAlerts().executeAsList().map { row ->
                val current  = row.current_stock.toInt()
                val reorder  = row.reorder_point.toInt()
                // Suggested qty: reorder up to 2x the reorder point
                val suggested = maxOf(1, reorder * 2 - current)
                StockReorderData(
                    productId          = row.product_id,
                    productName        = row.product_name,
                    currentStock       = current,
                    reorderPoint       = reorder,
                    suggestedReorderQty= suggested,
                )
            }
        }

    override suspend fun getSupplierPurchases(
        from: Instant,
        to: Instant,
    ): List<SupplierPurchaseData> =
        withContext(Dispatchers.IO) {
            // supplierPurchases now aggregates from purchase_orders table (Phase 3 Sprint 13+).
            q.supplierPurchases(
                from.toEpochMilliseconds(),
                to.toEpochMilliseconds(),
            ).executeAsList().map { row ->
                SupplierPurchaseData(
                    supplierId            = row.supplier_id,
                    supplierName          = row.supplier_name,
                    totalPurchaseAmount   = row.total_purchase_amount ?: 0.0,
                    orderCount            = (row.order_count ?: 0L).toInt(),
                )
            }
        }

    override suspend fun getReturnRefundSummary(
        from: Instant,
        to: Instant,
    ): ReturnRefundData =
        withContext(Dispatchers.IO) {
            val fromMs = from.toEpochMilliseconds()
            val toMs   = to.toEpochMilliseconds()
            val totals   = q.returnRefundTotals(fromMs, toMs).executeAsOne()
            val products = q.returnRefundByProduct(fromMs, toMs).executeAsList()
            ReturnRefundData(
                totalReturns      = (totals.total_returns ?: 0L).toInt(),
                totalRefundAmount = totals.total_refund_amount ?: 0.0,
                byProduct         = products.map { row ->
                    ProductReturnData(
                        productId    = row.product_id,
                        productName  = row.product_name,
                        returnCount  = (row.return_count ?: 0L).toInt(),
                        refundAmount = row.refund_amount ?: 0.0,
                    )
                },
            )
        }

    // ── Enterprise Reports — Finance ──────────────────────────────────────────

    override suspend fun getAccountingLedger(
        from: Instant,
        to: Instant,
    ): List<AccountingLedgerRecord> =
        withContext(Dispatchers.IO) {
            q.accountingLedger(
                from.toEpochMilliseconds(),
                to.toEpochMilliseconds(),
            ).executeAsList().map { row ->
                AccountingLedgerRecord(
                    entryId     = row.entry_id,
                    entryType   = row.entry_type,
                    accountName = row.account_name,
                    amount      = row.amount,
                    description = row.description ?: "",
                    recordedAt  = Instant.fromEpochMilliseconds(row.recorded_at),
                )
            }
        }

    override suspend fun getHourlySales(date: LocalDate): List<HourlySalesData> =
        withContext(Dispatchers.IO) {
            val dateStr = date.toString()
            // Fill all 24 hours, defaulting missing hours to zero
            val dbRows = q.hourlySales(dateStr).executeAsList()
                .associate { row -> (row.hour ?: 0L).toInt() to row }
            (0..23).map { hour ->
                val row = dbRows[hour]
                HourlySalesData(
                    hour       = hour,
                    revenue    = row?.revenue ?: 0.0,
                    orderCount = (row?.order_count ?: 0L).toInt(),
                )
            }
        }

    override suspend fun getCustomerLoyaltySummary(
        from: Instant,
        to: Instant,
    ): List<CustomerLoyaltyData> =
        withContext(Dispatchers.IO) {
            q.customerLoyaltySummary(
                from.toEpochMilliseconds(),
                to.toEpochMilliseconds(),
            ).executeAsList().map { row ->
                CustomerLoyaltyData(
                    customerId      = row.customer_id,
                    customerName    = row.customer_name,
                    pointsEarned    = (row.points_earned ?: 0L).toInt(),
                    pointsRedeemed  = (row.points_redeemed ?: 0L).toInt(),
                    pointsBalance   = (row.points_balance ?: 0L).toInt(),
                )
            }
        }

    override suspend fun getWalletBalances(): List<WalletBalanceData> =
        withContext(Dispatchers.IO) {
            q.walletBalances().executeAsList().map { row ->
                WalletBalanceData(
                    customerId          = row.customer_id,
                    customerName        = row.customer_name,
                    balance             = row.balance,
                    lastTransactionAt   = row.last_transaction_at?.let {
                        Instant.fromEpochMilliseconds(it)
                    },
                )
            }
        }

    override suspend fun getCOGS(from: Instant, to: Instant): List<COGSData> =
        withContext(Dispatchers.IO) {
            q.cogsData(
                from.toEpochMilliseconds(),
                to.toEpochMilliseconds(),
            ).executeAsList().map { row ->
                COGSData(
                    productId   = row.product_id,
                    productName = row.product_name,
                    unitsSold   = (row.units_sold ?: 0.0).toInt(),
                    costPerUnit = row.cost_price,
                    totalCOGS   = row.total_cogs ?: 0.0,
                    revenue     = row.revenue ?: 0.0,
                )
            }
        }

    override suspend fun getGrossMargin(from: Instant, to: Instant): List<GrossMarginData> =
        withContext(Dispatchers.IO) {
            q.grossMargin(
                from.toEpochMilliseconds(),
                to.toEpochMilliseconds(),
            ).executeAsList().map { row ->
                val revenue = row.revenue ?: 0.0
                val cogs    = row.cogs ?: 0.0
                val margin  = revenue - cogs
                val pct     = if (revenue > 0.0) (margin / revenue) * 100.0 else 0.0
                GrossMarginData(
                    productId   = row.product_id,
                    productName = row.product_name,
                    revenue     = revenue,
                    cogs        = cogs,
                    grossMargin = margin,
                    marginPct   = pct,
                )
            }
        }

    override suspend fun getAnnualSalesTrend(year: Int): List<MonthlySalesData> =
        withContext(Dispatchers.IO) {
            val yearStr = year.toString()
            val dbRows = q.annualSalesTrend(yearStr).executeAsList()
                .associate { row -> (row.month ?: 0L).toInt() to row }

            var prevRevenue = 0.0
            (1..12).map { month ->
                val row     = dbRows[month]
                val revenue = row?.revenue ?: 0.0
                val growth  = if (prevRevenue > 0.0) ((revenue - prevRevenue) / prevRevenue) * 100.0 else 0.0
                prevRevenue = revenue
                MonthlySalesData(
                    year          = year,
                    month         = month,
                    revenue       = revenue,
                    orderCount    = (row?.order_count ?: 0L).toInt(),
                    momGrowthPct  = growth,
                )
            }
        }

    override suspend fun getClockInOutLog(
        from: Instant,
        to: Instant,
        employeeId: String?,
    ): List<ClockRecord> =
        withContext(Dispatchers.IO) {
            val fromStr = from.toLocalDateTime(tz).let { ldt ->
                "${ldt.date}T${ldt.time}"
            }
            val toStr = to.toLocalDateTime(tz).let { ldt ->
                "${ldt.date}T${ldt.time}"
            }
            if (employeeId != null) {
                q.clockInOutLogByEmployee(fromStr, toStr, employeeId).executeAsList().map { row ->
                    ClockRecord(
                        employeeId   = row.employee_id,
                        employeeName = row.employee_name,
                        clockIn      = parseIsoDateTime(row.clock_in),
                        clockOut     = row.clock_out?.let { parseIsoDateTime(it) },
                        totalHours   = row.total_hours ?: 0.0,
                    )
                }
            } else {
                q.clockInOutLogAll(fromStr, toStr).executeAsList().map { row ->
                    ClockRecord(
                        employeeId   = row.employee_id,
                        employeeName = row.employee_name,
                        clockIn      = parseIsoDateTime(row.clock_in),
                        clockOut     = row.clock_out?.let { parseIsoDateTime(it) },
                        totalHours   = row.total_hours ?: 0.0,
                    )
                }
            }
        }

    override suspend fun getInventoryValuation(): List<InventoryValuationData> =
        withContext(Dispatchers.IO) {
            q.inventoryValuation().executeAsList().map { row ->
                InventoryValuationData(
                    productId      = row.product_id,
                    productName    = row.product_name,
                    quantityOnHand = row.quantity_on_hand.toInt(),
                    unitCost       = row.cost_price,
                    totalValue     = row.total_value ?: 0.0,
                )
            }
        }

    override suspend fun getCustomerRetentionMetrics(
        from: Instant,
        to: Instant,
    ): CustomerRetentionData =
        withContext(Dispatchers.IO) {
            val fromMs = from.toEpochMilliseconds()
            val toMs   = to.toEpochMilliseconds()
            val total      = q.customerRetentionTotal(fromMs, toMs).executeAsOne().toInt()
            val newCust    = q.customerRetentionNew(fromMs, toMs, fromMs).executeAsOne().toInt()
            val returning  = q.customerRetentionReturning(fromMs, toMs, fromMs).executeAsOne().toInt()
            val retention  = if (total > 0) (returning.toDouble() / total) * 100.0 else 0.0
            val churn      = 100.0 - retention
            CustomerRetentionData(
                totalCustomers     = total,
                newCustomers       = newCust,
                returningCustomers = returning,
                retentionRate      = retention,
                churnRate          = churn,
            )
        }

    override suspend fun getPurchaseOrders(
        from: Instant,
        to: Instant,
    ): List<PurchaseOrderData> =
        withContext(Dispatchers.IO) {
            // purchaseOrders query uses the new purchase_orders table (Phase 3 Sprint 13+).
            q.purchaseOrders(
                from.toEpochMilliseconds(),
                to.toEpochMilliseconds(),
            ).executeAsList().map { row ->
                PurchaseOrderData(
                    orderId      = row.order_id,
                    supplierId   = row.supplier_id,
                    supplierName = row.supplier_name,
                    totalAmount  = row.total_amount,
                    status       = row.order_status,
                    createdAt    = Instant.fromEpochMilliseconds(row.order_date),
                )
            }
        }

    override suspend fun getExpensesByDepartment(
        from: Instant,
        to: Instant,
    ): List<DeptExpenseData> =
        withContext(Dispatchers.IO) {
            q.expensesByDepartment(
                from.toEpochMilliseconds(),
                to.toEpochMilliseconds(),
            ).executeAsList().map { row ->
                DeptExpenseData(
                    department    = row.department,
                    totalAmount   = row.total_amount ?: 0.0,
                    expenseCount  = (row.expense_count ?: 0L).toInt(),
                )
            }
        }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Parses an ISO-8601 datetime string in the format "YYYY-MM-DDTHH:mm:ss"
     * stored in the attendance_records table into an [Instant].
     *
     * Falls back to [Instant.DISTANT_PAST] on parse failure so callers never crash.
     */
    private fun parseIsoDateTime(text: String): Instant = runCatching {
        // attendance_records stores clock_in as TEXT without timezone; parse as local date-time
        val normalized = if (text.length == 19) "${text}Z" else text
        Instant.parse(normalized)
    }.getOrDefault(Instant.DISTANT_PAST)
}
