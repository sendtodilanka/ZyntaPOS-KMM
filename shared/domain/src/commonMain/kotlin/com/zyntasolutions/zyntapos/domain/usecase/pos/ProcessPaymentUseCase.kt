package com.zyntasolutions.zyntapos.domain.usecase.pos

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.core.utils.IdGenerator
import com.zyntasolutions.zyntapos.domain.model.CartItem
import com.zyntasolutions.zyntapos.domain.model.DiscountType
import com.zyntasolutions.zyntapos.domain.model.Order
import com.zyntasolutions.zyntapos.domain.model.OrderItem
import com.zyntasolutions.zyntapos.domain.model.OrderStatus
import com.zyntasolutions.zyntapos.domain.model.OrderType
import com.zyntasolutions.zyntapos.domain.model.PaymentMethod
import com.zyntasolutions.zyntapos.domain.model.PaymentSplit
import com.zyntasolutions.zyntapos.domain.model.StockAdjustment
import com.zyntasolutions.zyntapos.domain.model.SyncStatus
import com.zyntasolutions.zyntapos.domain.repository.OrderRepository
import com.zyntasolutions.zyntapos.domain.repository.StockRepository
import com.zyntasolutions.zyntapos.domain.validation.PaymentValidator
import kotlin.time.Clock

/**
 * Finalises a cart into a persisted [Order], consuming stock.
 *
 * ### Business Rules
 * 1. **Tender validation** — for CASH payments, `amountTendered` must be ≥ `orderTotal`.
 * 2. **Split validation** — when [paymentSplits] is non-empty, the sum of split amounts
 *    must equal [orderTotal] (± 0.001 tolerance for floating-point).
 * 3. **Change calculation** — `change = amountTendered − orderTotal`; 0 for non-cash methods.
 * 4. **Atomicity** — stock decrement and order persist are performed sequentially.
 *    If either step fails the error is returned immediately.
 * 5. Order number is assigned by [OrderRepository.create] at the data layer.
 *
 * @param orderRepository     Persists the finalised order.
 * @param stockRepository     Decrements product stock quantities.
 * @param calculateTotalsUseCase Recalculates totals for consistency.
 */
class ProcessPaymentUseCase(
    private val orderRepository: OrderRepository,
    private val stockRepository: StockRepository,
    private val calculateTotalsUseCase: CalculateOrderTotalsUseCase,
) {
    /**
     * @param items             Cart items to finalise.
     * @param paymentMethod     Primary payment method.
     * @param paymentSplits     Non-empty only when [paymentMethod] is [PaymentMethod.SPLIT].
     * @param amountTendered    Cash amount presented by customer (set equal to total for card/mobile).
     * @param customerId        Optional customer FK.
     * @param cashierId         The authenticated cashier's user ID.
     * @param storeId           The store where the transaction is occurring.
     * @param registerSessionId The active register session ID.
     * @param orderDiscount     Order-level discount (0.0 = none).
     * @param orderDiscountType Discount type for the order-level discount.
     * @param taxInclusive      Whether item prices include tax.
     * @param notes             Optional operator notes.
     * @return [Result.Success] with the newly created [Order], or [Result.Error] on failure.
     */
    suspend operator fun invoke(
        items: List<CartItem>,
        paymentMethod: PaymentMethod,
        paymentSplits: List<PaymentSplit> = emptyList(),
        amountTendered: Double,
        customerId: String? = null,
        cashierId: String,
        storeId: String,
        registerSessionId: String,
        orderDiscount: Double = 0.0,
        orderDiscountType: DiscountType = DiscountType.FIXED,
        taxInclusive: Boolean = false,
        notes: String? = null,
    ): Result<Order> {
        if (items.isEmpty()) {
            return Result.Error(
                ValidationException("Cart is empty — cannot process payment.", field = "items", rule = "EMPTY_CART"),
            )
        }

        // 1. Recalculate totals
        val totalsResult = calculateTotalsUseCase(items, orderDiscount, orderDiscountType, taxInclusive)
        if (totalsResult is Result.Error) return totalsResult
        val totals = (totalsResult as Result.Success).data

        // 2. Validate tender / split
        val paymentValidation = if (paymentSplits.isNotEmpty()) {
            PaymentValidator.validateSplitPayment(paymentSplits, totals.total)
        } else {
            PaymentValidator.validateTender(amountTendered, totals.total, paymentMethod)
        }
        if (paymentValidation is Result.Error) return paymentValidation

        val change = if (paymentMethod == PaymentMethod.CASH) {
            amountTendered - totals.total
        } else {
            0.0
        }

        val now = Clock.System.now()

        // 3. Build order items (tax already calculated in calculateTotalsUseCase)
        val orderItems = items.map { cart ->
            val itemDiscountAmt = when (cart.discountType) {
                DiscountType.FIXED -> cart.discount
                DiscountType.PERCENT -> cart.unitPrice * cart.quantity * (cart.discount / 100.0)
                DiscountType.BOGO -> 0.0 // BOGO qty already adjusted upstream
            }
            val baseAmount = cart.unitPrice * cart.quantity - itemDiscountAmt
            val taxAmt = if (taxInclusive && cart.taxRate > 0.0) {
                baseAmount - baseAmount / (1.0 + cart.taxRate / 100.0)
            } else {
                baseAmount * (cart.taxRate / 100.0)
            }
            OrderItem(
                id = IdGenerator.newId(),
                orderId = "", // filled by repository on insert
                productId = cart.productId,
                productName = cart.productName,
                unitPrice = cart.unitPrice,
                quantity = cart.quantity,
                discount = cart.discount,
                discountType = cart.discountType,
                taxRate = cart.taxRate,
                taxAmount = taxAmt,
                lineTotal = baseAmount + taxAmt,
            )
        }

        val order = Order(
            id = IdGenerator.newId(),
            orderNumber = "", // assigned by repository
            type = OrderType.SALE,
            status = OrderStatus.COMPLETED,
            items = orderItems,
            subtotal = totals.subtotal,
            taxAmount = totals.taxAmount,
            discountAmount = totals.discountAmount,
            total = totals.total,
            paymentMethod = paymentMethod,
            paymentSplits = paymentSplits,
            amountTendered = amountTendered,
            changeAmount = change,
            customerId = customerId,
            cashierId = cashierId,
            storeId = storeId,
            registerSessionId = registerSessionId,
            notes = notes,
            createdAt = now,
            updatedAt = now,
            syncStatus = SyncStatus(state = SyncStatus.State.PENDING),
        )

        // 4. Decrement stock for each cart item
        for (item in items) {
            val adjustResult = stockRepository.adjustStock(
                StockAdjustment(
                    id = IdGenerator.newId(),
                    productId = item.productId,
                    type = StockAdjustment.Type.DECREASE,
                    quantity = item.quantity,
                    reason = "Sale",
                    adjustedBy = cashierId,
                    timestamp = now,
                    syncStatus = SyncStatus(state = SyncStatus.State.PENDING),
                ),
            )
            if (adjustResult is Result.Error) return adjustResult
        }

        // 5. Persist order (repository also sets orderNumber and fills orderId on items)
        return orderRepository.create(order)
    }
}
