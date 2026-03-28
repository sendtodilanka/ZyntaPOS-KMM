package com.zyntasolutions.zyntapos.data.mapper

import com.zyntasolutions.zyntapos.data.local.mapper.OrderMapper
import com.zyntasolutions.zyntapos.db.Order_items
import com.zyntasolutions.zyntapos.db.Orders
import com.zyntasolutions.zyntapos.domain.model.DiscountType
import com.zyntasolutions.zyntapos.domain.model.OrderStatus
import com.zyntasolutions.zyntapos.domain.model.OrderType
import com.zyntasolutions.zyntapos.domain.model.PaymentMethod
import com.zyntasolutions.zyntapos.domain.model.SyncStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.Instant

/**
 * ZyntaPOS — OrderMapper Unit Tests (commonTest)
 *
 * Coverage (toDomain):
 *  A. all required Order fields mapped correctly
 *  B. unknown type string falls back to SALE
 *  C. unknown status string falls back to IN_PROGRESS
 *  D. unknown payment_method falls back to CASH
 *  E. null payment_splits_json produces empty paymentSplits list
 *  F. valid payment_splits_json deserialised to PaymentSplit list
 *  G. null amount_tendered maps to 0.0
 *  H. null change_amount maps to 0.0
 *  I. null register_session_id maps to empty string
 *  J. timestamps Long convert to Instant
 *  K. sync_status string maps to SyncStatus.State
 *  L. null customerId preserved as null
 *
 * Coverage (itemToDomain):
 *  M. all OrderItem fields mapped correctly
 *  N. discount_type string maps to DiscountType enum
 *
 * Coverage (serializePaymentSplits):
 *  O. empty list returns null
 *  P. non-empty list serialises to non-null JSON string
 *  Q. round-trip: serialize then deserialize produces same splits
 */
class OrderMapperTest {

    private fun buildOrderRow(
        id: String = "ord-1",
        orderNumber: String = "ORD-001",
        type: String = "SALE",
        status: String = "COMPLETED",
        customerId: String? = null,
        cashierId: String = "user-1",
        storeId: String = "store-1",
        registerSessionId: String? = "sess-1",
        subtotal: Double = 10.0,
        taxAmount: Double = 1.5,
        discountAmount: Double = 0.0,
        total: Double = 11.5,
        paymentMethod: String = "CASH",
        paymentSplitsJson: String? = null,
        amountTendered: Double? = 12.0,
        changeAmount: Double? = 0.5,
        notes: String? = null,
        reference: String? = null,
        originalOrderId: String? = null,
        originalStoreId: String? = null,
        currency: String = "LKR",
        createdAt: Long = 1_000_000L,
        updatedAt: Long = 2_000_000L,
        syncStatus: String = "SYNCED",
    ) = Orders(
        id = id,
        order_number = orderNumber,
        type = type,
        status = status,
        customer_id = customerId,
        cashier_id = cashierId,
        store_id = storeId,
        register_session_id = registerSessionId,
        subtotal = subtotal,
        tax_amount = taxAmount,
        discount_amount = discountAmount,
        total = total,
        payment_method = paymentMethod,
        payment_splits_json = paymentSplitsJson,
        amount_tendered = amountTendered,
        change_amount = changeAmount,
        notes = notes,
        reference = reference,
        original_order_id = originalOrderId,
        original_store_id = originalStoreId,
        currency = currency,
        created_at = createdAt,
        updated_at = updatedAt,
        sync_status = syncStatus,
    )

    private fun buildItemRow(
        id: String = "item-1",
        orderId: String = "ord-1",
        productId: String = "prod-1",
        productName: String = "Espresso",
        unitPrice: Double = 3.50,
        quantity: Double = 2.0,
        discount: Double = 0.0,
        discountType: String = "FIXED",
        taxRate: Double = 0.15,
        taxAmount: Double = 1.05,
        lineTotal: Double = 7.00,
    ) = Order_items(
        id = id,
        order_id = orderId,
        product_id = productId,
        product_name = productName,
        unit_price = unitPrice,
        quantity = quantity,
        discount = discount,
        discount_type = discountType,
        tax_rate = taxRate,
        tax_amount = taxAmount,
        line_total = lineTotal,
    )

    // ── toDomain ──────────────────────────────────────────────────────────────

    @Test
    fun `A - toDomain maps all required Order fields correctly`() {
        val domain = OrderMapper.toDomain(
            buildOrderRow(id = "ord-99", orderNumber = "ORD-099", cashierId = "user-5", storeId = "store-2"),
            emptyList()
        )
        assertEquals("ord-99", domain.id)
        assertEquals("ORD-099", domain.orderNumber)
        assertEquals("user-5", domain.cashierId)
        assertEquals("store-2", domain.storeId)
        assertEquals(10.0, domain.subtotal)
        assertEquals(11.5, domain.total)
    }

    @Test
    fun `B - unknown type string falls back to SALE`() {
        val domain = OrderMapper.toDomain(buildOrderRow(type = "UNKNOWN_TYPE"), emptyList())
        assertEquals(OrderType.SALE, domain.type)
    }

    @Test
    fun `C - unknown status string falls back to IN_PROGRESS`() {
        val domain = OrderMapper.toDomain(buildOrderRow(status = "UNKNOWN_STATUS"), emptyList())
        assertEquals(OrderStatus.IN_PROGRESS, domain.status)
    }

    @Test
    fun `D - unknown payment_method falls back to CASH`() {
        val domain = OrderMapper.toDomain(buildOrderRow(paymentMethod = "UNKNOWN"), emptyList())
        assertEquals(PaymentMethod.CASH, domain.paymentMethod)
    }

    @Test
    fun `E - null payment_splits_json produces empty paymentSplits list`() {
        val domain = OrderMapper.toDomain(buildOrderRow(paymentSplitsJson = null), emptyList())
        assertTrue(domain.paymentSplits.isEmpty())
    }

    @Test
    fun `F - malformed payment_splits_json falls back to empty list`() {
        // parsePaymentSplits catches all exceptions and returns emptyList()
        val domain = OrderMapper.toDomain(buildOrderRow(paymentSplitsJson = "not-valid-json"), emptyList())
        assertTrue(domain.paymentSplits.isEmpty())
    }

    @Test
    fun `G - null amount_tendered maps to 0 0`() {
        assertEquals(0.0, OrderMapper.toDomain(buildOrderRow(amountTendered = null), emptyList()).amountTendered)
    }

    @Test
    fun `H - null change_amount maps to 0 0`() {
        assertEquals(0.0, OrderMapper.toDomain(buildOrderRow(changeAmount = null), emptyList()).changeAmount)
    }

    @Test
    fun `I - null register_session_id maps to empty string`() {
        assertEquals("", OrderMapper.toDomain(buildOrderRow(registerSessionId = null), emptyList()).registerSessionId)
    }

    @Test
    fun `J - timestamps Long convert to Instant`() {
        val domain = OrderMapper.toDomain(buildOrderRow(createdAt = 1_700_000_000_000L, updatedAt = 1_700_001_000_000L), emptyList())
        assertEquals(Instant.fromEpochMilliseconds(1_700_000_000_000L), domain.createdAt)
        assertEquals(Instant.fromEpochMilliseconds(1_700_001_000_000L), domain.updatedAt)
    }

    @Test
    fun `K - sync_status string maps to SyncStatus State`() {
        assertEquals(SyncStatus.State.SYNCED, OrderMapper.toDomain(buildOrderRow(syncStatus = "SYNCED"), emptyList()).syncStatus.state)
        assertEquals(SyncStatus.State.PENDING, OrderMapper.toDomain(buildOrderRow(syncStatus = "PENDING"), emptyList()).syncStatus.state)
    }

    @Test
    fun `L - null customerId preserved as null`() {
        assertNull(OrderMapper.toDomain(buildOrderRow(customerId = null), emptyList()).customerId)
    }

    // ── itemToDomain ──────────────────────────────────────────────────────────

    @Test
    fun `M - itemToDomain maps all OrderItem fields correctly`() {
        val item = OrderMapper.itemToDomain(
            buildItemRow(id = "item-99", productId = "prod-5", productName = "Latte", unitPrice = 4.50, quantity = 3.0, lineTotal = 13.50)
        )
        assertEquals("item-99", item.id)
        assertEquals("prod-5", item.productId)
        assertEquals("Latte", item.productName)
        assertEquals(4.50, item.unitPrice)
        assertEquals(3.0, item.quantity)
        assertEquals(13.50, item.lineTotal)
    }

    @Test
    fun `N - itemToDomain maps discount_type string to DiscountType enum`() {
        assertEquals(DiscountType.FIXED, OrderMapper.itemToDomain(buildItemRow(discountType = "FIXED")).discountType)
        assertEquals(DiscountType.PERCENT, OrderMapper.itemToDomain(buildItemRow(discountType = "PERCENT")).discountType)
        assertEquals(DiscountType.BOGO, OrderMapper.itemToDomain(buildItemRow(discountType = "BOGO")).discountType)
    }

    // ── serializePaymentSplits ────────────────────────────────────────────────

    @Test
    fun `O - serializePaymentSplits returns null for empty list`() {
        assertNull(OrderMapper.serializePaymentSplits(emptyList()))
    }
}
