package com.zyntasolutions.zyntapos.domain.formatter

import com.zyntasolutions.zyntapos.core.utils.CurrencyFormatter
import com.zyntasolutions.zyntapos.core.utils.AppTimezone
import com.zyntasolutions.zyntapos.domain.model.DiscountType
import com.zyntasolutions.zyntapos.domain.model.Order
import com.zyntasolutions.zyntapos.domain.model.OrderItem
import com.zyntasolutions.zyntapos.domain.model.OrderStatus
import com.zyntasolutions.zyntapos.domain.model.OrderType
import com.zyntasolutions.zyntapos.domain.model.PaymentMethod
import com.zyntasolutions.zyntapos.domain.model.PaymentSplit
import com.zyntasolutions.zyntapos.domain.model.SyncStatus
import kotlinx.datetime.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * ZyntaPOS — ReceiptFormatter Unit Tests (commonTest)
 *
 * Validates plain-text receipt generation from [Order] domain models.
 * All tests use UTC to ensure deterministic date/time output.
 *
 * Coverage:
 *  A. basic receipt contains order number and dividers
 *  B. header lines appear centered between top dividers
 *  C. header capped at 5 lines
 *  D. footer lines appear centered at the bottom
 *  E. footer capped at 3 lines
 *  F. tax line shown when taxAmount > 0
 *  G. tax line absent when taxAmount == 0
 *  H. discount line shown with minus sign when discountAmount > 0
 *  I. discount line absent when discountAmount == 0
 *  J. CASH payment shows Cash Tendered and Change lines
 *  K. CARD payment does NOT show Cash Tendered / Change lines
 *  L. split payment shows each leg with its label
 *  M. customerId line shown when customerId is non-null
 *  N. customerId line absent when customerId is null
 *  O. item product name truncated to 20 characters
 *  P. 32-char paper width produces dividers of 32 chars
 *  Q. TOTAL line always present
 *  R. receipt ends with divider (no trailing newline)
 */
class ReceiptFormatterTest {

    private val formatter = ReceiptFormatter(
        currencyFormatter = CurrencyFormatter(defaultCurrency = "LKR"),
        currencyCode = "LKR",
    )

    @BeforeTest
    fun setUp() {
        // Use UTC so date/time output is deterministic across CI environments
        AppTimezone.set("UTC")
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun item(
        name: String = "Product",
        qty: Double = 1.0,
        unitPrice: Double = 100.0,
        lineTotal: Double = 100.0,
        taxRate: Double = 0.0,
        taxAmount: Double = 0.0,
    ) = OrderItem(
        id = "item-1",
        orderId = "ord-1",
        productId = "prod-1",
        productName = name,
        unitPrice = unitPrice,
        quantity = qty,
        taxRate = taxRate,
        taxAmount = taxAmount,
        lineTotal = lineTotal,
    )

    private fun order(
        orderNumber: String = "1001",
        items: List<OrderItem> = listOf(item()),
        subtotal: Double = 100.0,
        taxAmount: Double = 0.0,
        discountAmount: Double = 0.0,
        total: Double = 100.0,
        paymentMethod: PaymentMethod = PaymentMethod.CASH,
        paymentSplits: List<PaymentSplit> = emptyList(),
        amountTendered: Double = 100.0,
        changeAmount: Double = 0.0,
        customerId: String? = null,
    ) = Order(
        id = "ord-1",
        orderNumber = orderNumber,
        type = OrderType.SALE,
        status = OrderStatus.COMPLETED,
        items = items,
        subtotal = subtotal,
        taxAmount = taxAmount,
        discountAmount = discountAmount,
        total = total,
        paymentMethod = paymentMethod,
        paymentSplits = paymentSplits,
        amountTendered = amountTendered,
        changeAmount = changeAmount,
        customerId = customerId,
        cashierId = "cashier-1",
        storeId = "store-1",
        registerSessionId = "session-1",
        currency = "LKR",
        createdAt = Instant.fromEpochMilliseconds(1_700_000_000_000L), // 2023-11-14T22:13:20Z
        updatedAt = Instant.fromEpochMilliseconds(1_700_000_000_000L),
        syncStatus = SyncStatus(state = SyncStatus.State.SYNCED),
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `A - basic receipt contains order number and equals dividers`() {
        val receipt = formatter.format(order(orderNumber = "1001"))
        assertTrue(receipt.contains("Order #: 1001"), "Expected 'Order #: 1001' in receipt")
        assertTrue(receipt.contains("=".repeat(48)), "Expected 48-char divider")
    }

    @Test
    fun `B - header lines appear in receipt`() {
        val receipt = formatter.format(
            order = order(),
            headerLines = listOf("ZyntaPOS Store", "123 Main Street"),
        )
        assertTrue(receipt.contains("ZyntaPOS Store"), "Expected store name in header")
        assertTrue(receipt.contains("123 Main Street"), "Expected address in header")
    }

    @Test
    fun `C - header capped at 5 lines and 6th line is excluded`() {
        val lines = listOf("L1", "L2", "L3", "L4", "L5", "SHOULD_BE_EXCLUDED")
        val receipt = formatter.format(order = order(), headerLines = lines)
        assertFalse(receipt.contains("SHOULD_BE_EXCLUDED"), "6th header line must not appear")
        assertTrue(receipt.contains("L5"), "5th header line must appear")
    }

    @Test
    fun `D - footer lines appear at the bottom of receipt`() {
        val receipt = formatter.format(
            order = order(),
            footerLines = listOf("Thank you!", "Come again"),
        )
        assertTrue(receipt.contains("Thank you!"), "Expected first footer line")
        assertTrue(receipt.contains("Come again"), "Expected second footer line")
    }

    @Test
    fun `E - footer capped at 3 lines and 4th line is excluded`() {
        val lines = listOf("F1", "F2", "F3", "SHOULD_BE_EXCLUDED")
        val receipt = formatter.format(order = order(), footerLines = lines)
        assertFalse(receipt.contains("SHOULD_BE_EXCLUDED"), "4th footer line must not appear")
        assertTrue(receipt.contains("F3"), "3rd footer line must appear")
    }

    @Test
    fun `F - tax line present when taxAmount is positive`() {
        val receipt = formatter.format(order(taxAmount = 15.0, total = 115.0))
        assertTrue(receipt.contains("Tax:"), "Expected Tax: line when taxAmount > 0")
    }

    @Test
    fun `G - tax line absent when taxAmount is zero`() {
        val receipt = formatter.format(order(taxAmount = 0.0))
        assertFalse(receipt.contains("Tax:"), "Tax line must be absent when taxAmount = 0")
    }

    @Test
    fun `H - discount line present with minus sign when discountAmount is positive`() {
        val receipt = formatter.format(order(discountAmount = 10.0, total = 90.0))
        assertTrue(receipt.contains("Discount:"), "Expected Discount: line")
        assertTrue(receipt.contains("-Rs."), "Expected negative discount sign")
    }

    @Test
    fun `I - discount line absent when discountAmount is zero`() {
        val receipt = formatter.format(order(discountAmount = 0.0))
        assertFalse(receipt.contains("Discount:"), "Discount line must be absent when 0")
    }

    @Test
    fun `J - CASH payment shows Cash Tendered and Change lines`() {
        val receipt = formatter.format(
            order(
                paymentMethod = PaymentMethod.CASH,
                amountTendered = 150.0,
                changeAmount = 50.0,
            )
        )
        assertTrue(receipt.contains("Cash Tendered:"), "Expected Cash Tendered line")
        assertTrue(receipt.contains("Change:"), "Expected Change line")
    }

    @Test
    fun `K - CARD payment does not show Cash Tendered or Change lines`() {
        val receipt = formatter.format(
            order(paymentMethod = PaymentMethod.CARD, amountTendered = 100.0, changeAmount = 0.0)
        )
        assertFalse(receipt.contains("Cash Tendered:"), "Cash Tendered must not appear for CARD")
        assertFalse(receipt.contains("Change:"), "Change must not appear for CARD")
    }

    @Test
    fun `L - split payment shows each leg label`() {
        val receipt = formatter.format(
            order(
                paymentMethod = PaymentMethod.SPLIT,
                paymentSplits = listOf(
                    PaymentSplit(PaymentMethod.CASH, 60.0),
                    PaymentSplit(PaymentMethod.CARD, 40.0),
                ),
            )
        )
        assertTrue(receipt.contains("Cash:"), "Expected Cash split label")
        assertTrue(receipt.contains("Card:"), "Expected Card split label")
    }

    @Test
    fun `M - customer ID line shown when customerId is non-null`() {
        val receipt = formatter.format(order(customerId = "cust-99"))
        assertTrue(receipt.contains("Customer ID: cust-99"), "Expected Customer ID line")
    }

    @Test
    fun `N - customer ID line absent when customerId is null`() {
        val receipt = formatter.format(order(customerId = null))
        assertFalse(receipt.contains("Customer ID:"), "Customer ID must not appear for walk-in")
    }

    @Test
    fun `O - item product name truncated to 20 characters`() {
        val longName = "A".repeat(30) // 30 chars — should be truncated to 20
        val receipt = formatter.format(order(items = listOf(item(name = longName))))
        // The 21st+ chars must not appear: the name is taken as first 20 chars = "A" * 20
        assertFalse(receipt.contains("A".repeat(21)), "Name must be truncated to 20 chars")
        assertTrue(receipt.contains("A".repeat(20)), "First 20 chars of name must appear")
    }

    @Test
    fun `P - 32-char paper width produces 32-char dividers`() {
        val receipt = formatter.format(order(), charsPerLine = 32)
        assertTrue(receipt.contains("=".repeat(32)), "Expected 32-char divider")
        assertFalse(receipt.contains("=".repeat(33)), "No 33-char divider should appear")
    }

    @Test
    fun `Q - TOTAL line always present in receipt`() {
        val receipt = formatter.format(order(total = 250.0))
        assertTrue(receipt.contains("TOTAL:"), "Expected TOTAL: line in receipt")
    }

    @Test
    fun `R - receipt ends with divider and no trailing newline`() {
        val receipt = formatter.format(order())
        assertTrue(receipt.endsWith("=".repeat(48)), "Receipt must end with 48-char divider")
        assertFalse(receipt.endsWith("\n"), "Receipt must not have trailing newline")
    }
}
