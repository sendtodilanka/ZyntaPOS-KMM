package com.zyntasolutions.zyntapos.data.repository

import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn

/**
 * ZyntaPOS — ReportRepositoryImpl Integration Tests (jvmTest)
 *
 * Validates [ReportRepositoryImpl] against a real in-memory SQLite database.
 * Tests the core aggregate SQL queries without mocks.
 *
 * Strategy: seed minimal rows into the raw tables and assert that the report
 * queries return correctly aggregated / shaped results.  Enterprise-only
 * reports that need heavy seeding (payroll, e-invoices, etc.) are verified
 * to return empty or zero-filled structures when the database is empty.
 *
 * Coverage:
 *  A. getDailySalesSummary returns zeroes for empty database
 *  B. getDailySalesSummary aggregates COMPLETED orders for a given date
 *  C. getDailySalesSummary excludes non-COMPLETED orders
 *  D. getSalesByCategory returns empty list when no data
 *  E. getSalesByCategory aggregates revenue per category
 *  F. getTopProductsByVolume returns products ranked by units sold
 *  G. getPaymentMethodBreakdown groups COMPLETED orders by payment method
 *  H. getTopCustomers returns customers ranked by total spend
 *  I. getHourlySales always returns exactly 24 hourly slots
 *  J. getAnnualSalesTrend always returns exactly 12 monthly entries
 *  K. getInventoryValuation returns product-level cost × stock
 *  L. getWalletBalances returns empty list when no wallets exist
 *  M. getCashMovementLog returns empty list when no cash movements
 *  N. getProfitLoss revenue matches COMPLETED order totals
 *  O. getStaffAttendanceSummary returns rows for all active employees
 */
class ReportRepositoryImplIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: ReportRepositoryImpl

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        repo = ReportRepositoryImpl(db)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private val now get() = Clock.System.now().toEpochMilliseconds()

    /** UTC midnight epoch ms for a calendar date string "YYYY-MM-DD". */
    private fun dateToEpochMs(dateStr: String): Long {
        val parts = dateStr.split("-")
        val date = LocalDate(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
        return date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
    }

    /** Insert a minimal category row. */
    private fun insertCategory(id: String, name: String) {
        db.categoriesQueries.insertCategory(
            id = id, name = name, parent_id = null, image_url = null,
            display_order = 0L, is_active = 1L, created_at = now, updated_at = now,
            sync_status = "PENDING",
        )
    }

    /** Insert a minimal product row with a specific category. */
    private fun insertProduct(
        id: String,
        name: String = "Product $id",
        categoryId: String? = null,
        price: Double = 10.0,
        costPrice: Double = 5.0,
        stockQty: Double = 100.0,
        minStockQty: Double = 5.0,
    ) {
        db.productsQueries.insertProduct(
            id = id, name = name, barcode = null, sku = null,
            category_id = categoryId, unit_id = "pcs",
            price = price, cost_price = costPrice, tax_group_id = null,
            stock_qty = stockQty, min_stock_qty = minStockQty,
            image_url = null, description = null, is_active = 1L,
            created_at = now, updated_at = now, sync_status = "PENDING",
            master_product_id = null,
        )
    }

    /** Insert a minimal customer row. */
    private fun insertCustomer(id: String, name: String) {
        val ts = now
        db.customersQueries.insertCustomer(
            id = id, name = name, phone = null, email = null, address = null,
            group_id = null, loyalty_points = 0L, notes = null, is_active = 1L,
            credit_limit = 0.0, credit_enabled = 0L, gender = null, birthday = null,
            is_walk_in = 0L, store_id = "store-01", created_at = ts, updated_at = ts,
            sync_status = "PENDING",
        )
    }

    /** Insert an order row directly via SQL. */
    private fun insertOrder(
        id: String,
        status: String = "COMPLETED",
        total: Double = 100.0,
        paymentMethod: String = "CASH",
        discountAmount: Double = 0.0,
        customerId: String? = null,
        cashierId: String = "cashier-01",
        createdAtMs: Long = now,
    ) {
        db.ordersQueries.insertOrder(
            id = id,
            order_number = "ORD-$id",
            type = "SALE",
            status = status,
            customer_id = customerId,
            cashier_id = cashierId,
            store_id = "store-01",
            register_session_id = null,
            subtotal = total,
            tax_amount = 0.0,
            discount_amount = discountAmount,
            total = total,
            payment_method = paymentMethod,
            payment_splits_json = null,
            amount_tendered = total,
            change_amount = 0.0,
            notes = null,
            reference = null,
            original_order_id = null,
            original_store_id = null,
            created_at = createdAtMs,
            updated_at = createdAtMs,
            sync_status = "PENDING",
        )
    }

    /** Insert an order_item row. */
    private fun insertOrderItem(
        id: String,
        orderId: String,
        productId: String,
        productName: String = "Item",
        unitPrice: Double = 10.0,
        quantity: Double = 1.0,
        lineTotal: Double = unitPrice * quantity,
    ) {
        db.ordersQueries.insertOrderItem(
            id = id,
            order_id = orderId,
            product_id = productId,
            product_name = productName,
            unit_price = unitPrice,
            quantity = quantity,
            discount = 0.0,
            discount_type = "NONE",
            tax_rate = 0.0,
            tax_amount = 0.0,
            line_total = lineTotal,
        )
    }

    /** Insert an employee row. */
    private fun insertEmployee(id: String, firstName: String, lastName: String) {
        val ts = now
        db.employeesQueries.insertEmployee(
            id = id, user_id = null, store_id = "store-01",
            first_name = firstName, last_name = lastName,
            email = null, phone = null, address = null,
            date_of_birth = null, hire_date = "2024-01-01",
            department = null, position = "Cashier",
            salary = 50000.0, salary_type = "MONTHLY",
            commission_rate = 0.0, emergency_contact = null, documents = null,
            is_active = 1L, created_at = ts, updated_at = ts, deleted_at = null,
            sync_status = "PENDING",
        )
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `A - getDailySalesSummary returns zeroes for empty database`() = runTest {
        val date = LocalDate(2026, 1, 15)
        val result = repo.getDailySalesSummary(date)

        assertEquals(date, result.date)
        assertEquals(0.0, result.totalRevenue)
        assertEquals(0, result.totalOrders)
        assertEquals(0.0, result.averageOrderValue)
        assertEquals(0, result.totalItemsSold)
        assertEquals(0.0, result.cashRevenue)
        assertEquals(0.0, result.cardRevenue)
    }

    @Test
    fun `B - getDailySalesSummary aggregates COMPLETED orders for the date`() = runTest {
        // Use UTC midnight for "2026-01-15" so SQLite date() yields exactly that string
        val targetMs = dateToEpochMs("2026-01-15")

        // Two COMPLETED orders on the target date
        insertOrder("ord-1", status = "COMPLETED", total = 200.0, paymentMethod = "CASH", createdAtMs = targetMs + 1000)
        insertOrder("ord-2", status = "COMPLETED", total = 300.0, paymentMethod = "CARD", createdAtMs = targetMs + 2000)
        // Add items so totalItemsSold > 0
        insertProduct("p-1")
        insertOrderItem("oi-1", "ord-1", "p-1", quantity = 2.0, unitPrice = 100.0, lineTotal = 200.0)
        insertOrderItem("oi-2", "ord-2", "p-1", quantity = 3.0, unitPrice = 100.0, lineTotal = 300.0)

        val result = repo.getDailySalesSummary(LocalDate(2026, 1, 15))

        assertEquals(2, result.totalOrders)
        assertEquals(500.0, result.totalRevenue)
        assertEquals(250.0, result.averageOrderValue)
        assertEquals(5, result.totalItemsSold)
        assertEquals(200.0, result.cashRevenue)
        assertEquals(300.0, result.cardRevenue)
    }

    @Test
    fun `C - getDailySalesSummary excludes PENDING and VOIDED orders`() = runTest {
        val targetMs = dateToEpochMs("2026-02-10")

        insertOrder("ord-pending", status = "PENDING",   total = 999.0, createdAtMs = targetMs + 500)
        insertOrder("ord-voided",  status = "VOIDED",    total = 888.0, createdAtMs = targetMs + 600)
        insertOrder("ord-ok",      status = "COMPLETED", total = 100.0, createdAtMs = targetMs + 700)

        val result = repo.getDailySalesSummary(LocalDate(2026, 2, 10))

        assertEquals(1, result.totalOrders)
        assertEquals(100.0, result.totalRevenue)
    }

    @Test
    fun `D - getSalesByCategory returns empty list when no orders`() = runTest {
        val from = Instant.fromEpochMilliseconds(0)
        val to   = Instant.fromEpochMilliseconds(now + 1_000_000)

        val result = repo.getSalesByCategory(from, to)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `E - getSalesByCategory aggregates revenue per category`() = runTest {
        insertCategory("cat-1", "Beverages")
        insertCategory("cat-2", "Snacks")
        insertProduct("p-bev1", categoryId = "cat-1", price = 50.0)
        insertProduct("p-snk1", categoryId = "cat-2", price = 30.0)

        val orderMs = now - 1000
        insertOrder("ord-A", status = "COMPLETED", total = 100.0, createdAtMs = orderMs)
        insertOrder("ord-B", status = "COMPLETED", total = 60.0,  createdAtMs = orderMs)
        insertOrderItem("oi-A1", "ord-A", "p-bev1", quantity = 2.0, lineTotal = 100.0)
        insertOrderItem("oi-B1", "ord-B", "p-snk1", quantity = 2.0, lineTotal = 60.0)

        val from = Instant.fromEpochMilliseconds(orderMs - 5000)
        val to   = Instant.fromEpochMilliseconds(orderMs + 5000)
        val results = repo.getSalesByCategory(from, to)

        assertEquals(2, results.size)
        val bev = results.first { it.categoryName == "Beverages" }
        val snk = results.first { it.categoryName == "Snacks" }
        assertEquals(100.0, bev.revenue)
        assertEquals(60.0,  snk.revenue)
        // Revenue share sums to 100%
        val totalShare = bev.revenueSharePct + snk.revenueSharePct
        assertTrue(totalShare > 99.9 && totalShare <= 100.0)
    }

    @Test
    fun `F - getTopProductsByVolume returns products ranked by units sold`() = runTest {
        insertProduct("p-high")
        insertProduct("p-low")

        val orderMs = now - 1000
        insertOrder("ord-1", status = "COMPLETED", total = 50.0, createdAtMs = orderMs)
        insertOrderItem("oi-1a", "ord-1", "p-high", quantity = 10.0, lineTotal = 50.0)
        insertOrderItem("oi-1b", "ord-1", "p-low",  quantity = 2.0,  lineTotal = 10.0)

        val from = Instant.fromEpochMilliseconds(orderMs - 5000)
        val to   = Instant.fromEpochMilliseconds(orderMs + 5000)
        val results = repo.getTopProductsByVolume(from, to, limit = 5)

        assertEquals(2, results.size)
        // First result must be the higher-volume product
        assertEquals("p-high", results.first().productId)
        assertEquals(10, results.first().unitsSold)
        assertEquals(2, results[1].unitsSold)
    }

    @Test
    fun `G - getPaymentMethodBreakdown groups COMPLETED orders by payment method`() = runTest {
        val orderMs = now - 500

        insertOrder("ord-c1", status = "COMPLETED", total = 100.0, paymentMethod = "CASH",  createdAtMs = orderMs)
        insertOrder("ord-c2", status = "COMPLETED", total = 200.0, paymentMethod = "CASH",  createdAtMs = orderMs)
        insertOrder("ord-d1", status = "COMPLETED", total = 150.0, paymentMethod = "CARD",  createdAtMs = orderMs)
        // VOIDED order must not appear
        insertOrder("ord-v1", status = "VOIDED",    total = 999.0, paymentMethod = "CASH",  createdAtMs = orderMs)

        val from = Instant.fromEpochMilliseconds(orderMs - 2000)
        val to   = Instant.fromEpochMilliseconds(orderMs + 2000)
        val results = repo.getPaymentMethodBreakdown(from, to)

        // getPaymentMethodBreakdown returns Map<String, Double>
        assertEquals(300.0, results["CASH"])
        assertEquals(150.0, results["CARD"])
    }

    @Test
    fun `H - getTopCustomers returns customers ranked by total spend descending`() = runTest {
        insertCustomer("cust-A", "Alice")
        insertCustomer("cust-B", "Bob")

        val orderMs = now - 1000
        insertOrder("ord-a1", status = "COMPLETED", total = 500.0, customerId = "cust-A", createdAtMs = orderMs)
        insertOrder("ord-a2", status = "COMPLETED", total = 300.0, customerId = "cust-A", createdAtMs = orderMs)
        insertOrder("ord-b1", status = "COMPLETED", total = 100.0, customerId = "cust-B", createdAtMs = orderMs)

        val from = Instant.fromEpochMilliseconds(orderMs - 2000)
        val to   = Instant.fromEpochMilliseconds(orderMs + 2000)
        val results = repo.getTopCustomers(from, to, limit = 10)

        assertEquals(2, results.size)
        // Alice should be first (800.0 > 100.0)
        assertEquals("cust-A", results.first().customerId)
        assertEquals(800.0, results.first().totalSpend)
        assertEquals(2, results.first().orderCount)
        assertEquals(400.0, results.first().averageOrderValue)

        assertEquals("cust-B", results[1].customerId)
        assertEquals(100.0, results[1].totalSpend)
    }

    @Test
    fun `I - getHourlySales always returns exactly 24 hourly slots`() = runTest {
        val date = LocalDate(2026, 3, 1)
        val results = repo.getHourlySales(date)

        assertEquals(24, results.size)
        // Hours are in ascending order 0..23
        assertEquals(0, results.first().hour)
        assertEquals(23, results.last().hour)
        // All zero when no data
        assertTrue(results.all { it.revenue == 0.0 })
        assertTrue(results.all { it.orderCount == 0 })
    }

    @Test
    fun `J - getAnnualSalesTrend always returns exactly 12 monthly entries`() = runTest {
        val results = repo.getAnnualSalesTrend(2026)

        assertEquals(12, results.size)
        assertEquals(1,  results.first().month)
        assertEquals(12, results.last().month)
        // All revenue zero when no data
        assertTrue(results.all { it.revenue == 0.0 })
        assertTrue(results.all { it.orderCount == 0 })
    }

    @Test
    fun `K - getInventoryValuation returns product cost times stock`() = runTest {
        insertProduct("prod-x", name = "Widget", costPrice = 8.0, stockQty = 50.0)
        insertProduct("prod-y", name = "Gadget", costPrice = 20.0, stockQty = 10.0)

        val results = repo.getInventoryValuation()

        assertEquals(2, results.size)
        val widget = results.first { it.productId == "prod-x" }
        val gadget = results.first { it.productId == "prod-y" }
        assertEquals(50, widget.quantityOnHand)
        assertEquals(8.0, widget.unitCost)
        assertEquals(400.0, widget.totalValue)
        assertEquals(10, gadget.quantityOnHand)
        assertEquals(200.0, gadget.totalValue)
    }

    @Test
    fun `L - getWalletBalances returns empty list when no customer wallets`() = runTest {
        val results = repo.getWalletBalances()
        assertTrue(results.isEmpty())
    }

    @Test
    fun `M - getCashMovementLog returns empty list when no cash movements`() = runTest {
        val from = Instant.fromEpochMilliseconds(0)
        val to   = Instant.fromEpochMilliseconds(now + 1_000_000)

        val results = repo.getCashMovementLog(from, to)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `N - getProfitLoss revenue matches COMPLETED order totals`() = runTest {
        val orderMs = now - 1000
        insertProduct("p-cost", costPrice = 30.0)

        insertOrder("ord-1", status = "COMPLETED", total = 100.0, createdAtMs = orderMs)
        insertOrderItem("oi-1", "ord-1", "p-cost", quantity = 1.0, lineTotal = 100.0)
        insertOrder("ord-2", status = "COMPLETED", total = 200.0, createdAtMs = orderMs)
        insertOrderItem("oi-2", "ord-2", "p-cost", quantity = 2.0, lineTotal = 200.0)
        // PENDING order should not count
        insertOrder("ord-p", status = "PENDING", total = 999.0, createdAtMs = orderMs)

        val from = Instant.fromEpochMilliseconds(orderMs - 2000)
        val to   = Instant.fromEpochMilliseconds(orderMs + 2000)
        val result = repo.getProfitLoss(from, to)

        assertEquals(300.0, result.totalRevenue)
        // COGS = (1 + 2) * 30.0 = 90.0
        assertEquals(90.0, result.totalCOGS)
        assertEquals(210.0, result.grossProfit)
        // No approved expenses seeded → totalExpenses = 0
        assertEquals(0.0, result.totalExpenses)
        assertEquals(210.0, result.netProfit)
    }

    @Test
    fun `O - getStaffAttendanceSummary returns row per active employee with zero counts when no records`() = runTest {
        insertEmployee("emp-01", "Alice", "Smith")
        insertEmployee("emp-02", "Bob",   "Jones")

        val from = Instant.fromEpochMilliseconds(0)
        val to   = Instant.fromEpochMilliseconds(now + 1_000_000)
        val results = repo.getStaffAttendanceSummary(from, to)

        // Two active employees, no attendance records → present/absent/late = 0
        assertEquals(2, results.size)
        assertTrue(results.all { it.daysPresent == 0 })
        assertTrue(results.all { it.daysAbsent == 0 })
        assertTrue(results.all { it.daysLate == 0 })
        assertTrue(results.all { it.totalHours == 0.0 })
    }
}
