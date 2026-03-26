package com.zyntasolutions.zyntapos.domain.model

/**
 * A transient cart line — **never persisted directly**.
 *
 * [CartItem] lives only in POS ViewModel memory while the cashier is
 * building an order. When an order is confirmed, [CartItem]s are converted
 * to [OrderItem]s by `ProcessPaymentUseCase`.
 *
 * @property productId FK to [Product]. Used for stock validation and barcode lookup.
 * @property productName Snapshot of the product name at the time it was added to the cart.
 * @property unitPrice The sell price per unit at the time the item was added.
 * @property quantity Number of units. Must be ≥ 1.
 * @property discount Discount value applied to this line (see [discountType]).
 * @property discountType Whether [discount] is a [DiscountType.FIXED] amount or [DiscountType.PERCENT].
 * @property taxRate Effective tax percentage for this item (sourced from its [TaxGroup],
 *                  with regional override applied via `GetEffectiveTaxRateUseCase`).
 * @property isTaxInclusive If true, [unitPrice] already includes tax (inclusive/VAT style).
 *                          If false, tax is added on top at checkout. Sourced from [TaxGroup.isInclusive].
 * @property lineTotal Computed: `(unitPrice × quantity) - discountAmount ± taxAmount`.
 *                     Calculated by `CalculateOrderTotalsUseCase` — **do not set manually**.
 */
data class CartItem(
    val productId: String,
    val productName: String,
    val unitPrice: Double,
    val quantity: Double,
    val discount: Double = 0.0,
    val discountType: DiscountType = DiscountType.FIXED,
    val taxRate: Double = 0.0,
    val isTaxInclusive: Boolean = false,
    val lineTotal: Double = 0.0,
    /** FK to [Category]. Used for category-targeted promotion evaluation. */
    val categoryId: String = "",
    /**
     * Compound tax components for stacked taxes (C2.3).
     * When non-empty, these replace the single [taxRate] with a multi-component
     * tax chain (e.g., VAT 15% + Service Charge 10% + Local Surcharge 2%).
     * Empty list = single tax rate mode (backward compatible).
     */
    val compoundTaxComponents: List<CompoundTaxComponent> = emptyList(),
) {
    init {
        require(quantity >= 1.0) { "Cart item quantity must be at least 1, got $quantity" }
    }
}
