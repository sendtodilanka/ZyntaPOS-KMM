package com.zyntasolutions.zyntapos.domain.model.report

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

// ── Standard Report Data Classes ─────────────────────────────────────────────

data class DailySalesSummaryData(
    val date: LocalDate,
    val totalRevenue: Double,
    val totalOrders: Int,
    val averageOrderValue: Double,
    val totalItemsSold: Int,
    val openingCash: Double,
    val closingCash: Double,
    val cashRevenue: Double,
    val cardRevenue: Double,
)

data class CategorySalesData(
    val categoryId: String,
    val categoryName: String,
    val revenue: Double,
    val orderCount: Int,
    val itemCount: Int,
    val revenueSharePct: Double,
)

data class CashMovementRecord(
    val id: String,
    val type: String,        // "CASH_IN" | "CASH_OUT"
    val amount: Double,
    val reason: String,
    val recordedBy: String,
    val recordedAt: Instant,
)

data class ProductVolumeData(
    val rank: Int,
    val productId: String,
    val productName: String,
    val unitsSold: Int,
    val revenue: Double,
)

// ── Premium Report Data Classes ───────────────────────────────────────────────

data class ProfitLossData(
    val from: Instant,
    val to: Instant,
    val totalRevenue: Double,
    val totalCOGS: Double,
    val grossProfit: Double,
    val totalExpenses: Double,
    val netProfit: Double,
    val grossMarginPct: Double,
)

data class ProductPerformanceData(
    val productId: String,
    val productName: String,
    val unitsSold: Int,
    val revenue: Double,
    val cogs: Double,
    val grossMargin: Double,
    val grossMarginPct: Double,
    val returnCount: Int,
)

data class CouponUsageData(
    val totalRedemptions: Int,
    val totalDiscountGiven: Double,
    val byCode: List<CouponCodeData>,
)

data class CouponCodeData(
    val code: String,
    val redemptions: Int,
    val totalDiscount: Double,
)

data class TaxCollectionData(
    val taxGroupId: String,
    val taxGroupName: String,
    val taxRate: Double,
    val taxableAmount: Double,
    val taxCollected: Double,
)

data class DiscountVoidData(
    val totalDiscountAmount: Double,
    val totalVoidCount: Int,
    val totalVoidAmount: Double,
    val byCashier: List<CashierDiscountVoidData>,
)

data class CashierDiscountVoidData(
    val cashierId: String,
    val cashierName: String,
    val discountAmount: Double,
    val voidCount: Int,
    val voidAmount: Double,
)

data class CustomerSpendData(
    val customerId: String,
    val customerName: String,
    val totalSpend: Double,
    val orderCount: Int,
    val averageOrderValue: Double,
)

// ── Enterprise Report Data Classes — Staff ────────────────────────────────────

data class StaffAttendanceData(
    val employeeId: String,
    val employeeName: String,
    val daysPresent: Int,
    val daysAbsent: Int,
    val daysLate: Int,
    val totalHours: Double,
)

data class StaffSalesData(
    val employeeId: String,
    val employeeName: String,
    val totalSales: Double,
    val orderCount: Int,
    val averageBasket: Double,
    val voidCount: Int,
)

data class PayrollSummaryData(
    val employeeId: String,
    val employeeName: String,
    val payPeriodId: String,
    val baseSalary: Double,
    val overtimePay: Double,
    val deductions: Double,
    val netPay: Double,
)

data class LeaveBalanceData(
    val employeeId: String,
    val employeeName: String,
    val leaveType: String,
    val allottedDays: Int,
    val takenDays: Int,
    val remainingDays: Int,
)

data class ShiftCoverageData(
    val shiftId: String,
    val shiftName: String,
    val scheduledHours: Double,
    val actualHours: Double,
    val coveragePct: Double,
)

data class ClockRecord(
    val employeeId: String,
    val employeeName: String,
    val clockIn: Instant,
    val clockOut: Instant?,
    val totalHours: Double,
)

// ── Enterprise Report Data Classes — Multi-Store ──────────────────────────────

data class StoreSalesData(
    val storeId: String,
    val storeName: String,
    val totalRevenue: Double,
    val orderCount: Int,
    val averageOrderValue: Double,
    /** Revenue growth % compared to previous period. Null when no previous data. */
    val revenueGrowthPercent: Double? = null,
    /** Order count growth % compared to previous period. Null when no previous data. */
    val orderGrowthPercent: Double? = null,
)

data class StockTransferRecord(
    val transferId: String,
    val fromStoreId: String,
    val toStoreId: String,
    val productId: String,
    val productName: String,
    val quantity: Int,
    val transferredAt: Instant,
)

data class WarehouseInventoryData(
    val warehouseId: String,
    val warehouseName: String,
    val rackId: String,
    val rackCode: String,
    val productId: String,
    val productName: String,
    val quantity: Int,
)

// ── Enterprise Report Data Classes — Inventory ────────────────────────────────

data class StockAgingData(
    val productId: String,
    val productName: String,
    val daysSinceLastSale: Int,
    val currentStock: Int,
    val stockValue: Double,
)

data class StockReorderData(
    val productId: String,
    val productName: String,
    val currentStock: Int,
    val reorderPoint: Int,
    val suggestedReorderQty: Int,
)

data class SupplierPurchaseData(
    val supplierId: String,
    val supplierName: String,
    val totalPurchaseAmount: Double,
    val orderCount: Int,
)

data class ReturnRefundData(
    val totalReturns: Int,
    val totalRefundAmount: Double,
    val byProduct: List<ProductReturnData>,
)

data class ProductReturnData(
    val productId: String,
    val productName: String,
    val returnCount: Int,
    val refundAmount: Double,
)

// ── Enterprise Report Data Classes — Finance ──────────────────────────────────

data class EInvoiceStatusData(
    val invoiceId: String,
    val orderId: String,
    val status: String,
    val submittedAt: Instant?,
    val totalAmount: Double,
)

data class AccountingLedgerRecord(
    val entryId: String,
    val entryType: String,       // "DEBIT" | "CREDIT"
    val accountName: String,
    val amount: Double,
    val description: String,
    val recordedAt: Instant,
)

data class HourlySalesData(
    val hour: Int,               // 0–23
    val revenue: Double,
    val orderCount: Int,
)

data class CustomerLoyaltyData(
    val customerId: String,
    val customerName: String,
    val pointsEarned: Int,
    val pointsRedeemed: Int,
    val pointsBalance: Int,
)

data class WalletBalanceData(
    val customerId: String,
    val customerName: String,
    val balance: Double,
    val lastTransactionAt: Instant?,
)

data class COGSData(
    val productId: String,
    val productName: String,
    val unitsSold: Int,
    val costPerUnit: Double,
    val totalCOGS: Double,
    val revenue: Double,
)

data class GrossMarginData(
    val productId: String,
    val productName: String,
    val revenue: Double,
    val cogs: Double,
    val grossMargin: Double,
    val marginPct: Double,
)

data class MonthlySalesData(
    val year: Int,
    val month: Int,
    val revenue: Double,
    val orderCount: Int,
    val momGrowthPct: Double,    // month-over-month growth percentage
)

data class InventoryValuationData(
    val productId: String,
    val productName: String,
    val quantityOnHand: Int,
    val unitCost: Double,
    val totalValue: Double,
)

data class CustomerRetentionData(
    val totalCustomers: Int,
    val newCustomers: Int,
    val returningCustomers: Int,
    val retentionRate: Double,
    val churnRate: Double,
)

data class PurchaseOrderData(
    val orderId: String,
    val supplierId: String,
    val supplierName: String,
    val totalAmount: Double,
    val status: String,
    val createdAt: Instant,
)

data class DeptExpenseData(
    val department: String,
    val totalAmount: Double,
    val expenseCount: Int,
)
