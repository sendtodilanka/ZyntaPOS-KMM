package com.zyntasolutions.zyntapos.feature.pos

import com.zyntasolutions.zyntapos.domain.model.Customer
import com.zyntasolutions.zyntapos.domain.model.DiscountType
import com.zyntasolutions.zyntapos.domain.model.PaymentMethod
import com.zyntasolutions.zyntapos.domain.model.PaymentSplit
import com.zyntasolutions.zyntapos.domain.model.Product

/**
 * All user interactions and system events that can mutate [PosState].
 *
 * `PosIntent` is the **single entry point** into `PosViewModel`. The Composable
 * layer never writes to state directly — it dispatches an intent and observes the
 * resulting [PosState] emission.
 *
 * ### Intent Categories
 * - **Data loading:** [LoadProducts]
 * - **Catalogue navigation:** [SelectCategory], [SearchQueryChanged], [SearchFocusChanged]
 * - **Cart mutations:** [AddToCart], [RemoveFromCart], [UpdateQty], [ApplyItemDiscount],
 *   [ApplyOrderDiscount], [ClearCart], [SetNotes]
 * - **Customer:** [SelectCustomer], [ClearCustomer]
 * - **Hardware:** [ScanBarcode], [SetScannerActive]
 * - **Hold / Retrieve:** [HoldOrder], [RetrieveHeld]
 * - **Payment:** [ProcessPayment]
 */
sealed interface PosIntent {

    // ─── Data Loading ──────────────────────────────────────────────────────────

    /**
     * Triggers an initial or refresh load of products and categories from the
     * local database. Should be dispatched from `LaunchedEffect(Unit)` in `PosScreen`.
     */
    data object LoadProducts : PosIntent

    // ─── Catalogue Navigation ──────────────────────────────────────────────────

    /**
     * Applies a category filter to the product grid.
     *
     * @param id The [com.zyntasolutions.zyntapos.domain.model.Category.id] to filter by,
     *   or `null` to show all products (deselect filter).
     */
    data class SelectCategory(val id: String?) : PosIntent

    /**
     * Updates the live text in the product search bar.
     * The ViewModel debounces this before querying the repository.
     *
     * @param query Current text in the search field (may be empty to reset).
     */
    data class SearchQueryChanged(val query: String) : PosIntent

    /**
     * Notifies the ViewModel that the search field focus state has changed.
     * Drives [PosState.isSearchFocused] to control scanner FAB visibility.
     *
     * @param focused `true` when the search field gains focus.
     */
    data class SearchFocusChanged(val focused: Boolean) : PosIntent

    // ─── Cart Mutations ────────────────────────────────────────────────────────

    /**
     * Adds [product] to the cart or increments its quantity by 1 if already present.
     * Tax rate is resolved from the product's assigned tax group by the ViewModel.
     *
     * @param product The [Product] tapped or scanned from the catalogue grid.
     */
    data class AddToCart(val product: Product) : PosIntent

    /**
     * Removes the cart line identified by [productId] entirely, regardless of quantity.
     *
     * @param productId Matches [com.zyntasolutions.zyntapos.domain.model.CartItem.productId].
     */
    data class RemoveFromCart(val productId: String) : PosIntent

    /**
     * Sets the quantity of a specific cart line to [qty].
     * Dispatching with `qty = 0` is equivalent to [RemoveFromCart].
     *
     * @param productId Target cart line identifier.
     * @param qty New quantity value (must be ≥ 0; use 0 to remove the line).
     */
    data class UpdateQty(val productId: String, val qty: Double) : PosIntent

    /**
     * Applies or updates a line-level discount on a specific cart item.
     *
     * @param productId Target cart line identifier.
     * @param discount Discount value (monetary amount or percentage — see [type]).
     * @param type Interpretation mode: [DiscountType.FIXED] or [DiscountType.PERCENT].
     */
    data class ApplyItemDiscount(
        val productId: String,
        val discount: Double,
        val type: DiscountType,
    ) : PosIntent

    /**
     * Applies or updates the order-level discount applied after all line totals.
     *
     * @param discount Discount value.
     * @param type [DiscountType.FIXED] deducts a flat amount; [DiscountType.PERCENT]
     *   applies a percentage of the cart subtotal.
     */
    data class ApplyOrderDiscount(val discount: Double, val type: DiscountType) : PosIntent

    /**
     * Clears all cart lines, resets [PosState.orderDiscount],
     * [PosState.selectedCustomer], and [PosState.orderTotals] to their defaults.
     * Does **not** affect [PosState.heldOrders].
     */
    data object ClearCart : PosIntent

    /**
     * Attaches a free-text note to the current order.
     * Stored in [com.zyntasolutions.zyntapos.domain.model.Order.notes] at payment time.
     *
     * @param notes Operator-entered note text (empty string clears existing notes).
     */
    data class SetNotes(val notes: String) : PosIntent

    // ─── Customer ──────────────────────────────────────────────────────────────

    /**
     * Associates a [Customer] with the current order for loyalty tracking and receipts.
     *
     * @param customer The selected [Customer] domain object.
     */
    data class SelectCustomer(val customer: Customer) : PosIntent

    /** Removes the customer association from the current order. */
    data object ClearCustomer : PosIntent

    // ─── Hardware / Scanner ────────────────────────────────────────────────────

    /**
     * Dispatched by the HAL scanner callback or the manual barcode entry field
     * when a barcode string is available. The ViewModel resolves the product via
     * `SearchProductsUseCase(barcode)` and dispatches [AddToCart] on success,
     * or emits [PosEffect.BarcodeNotFound] on failure.
     *
     * @param barcode The raw barcode string (EAN-13, QR, Code128, etc.).
     */
    data class ScanBarcode(val barcode: String) : PosIntent

    /**
     * Activates or deactivates the HAL barcode scanner listener.
     * Drives [PosState.scannerActive] and the corresponding HAL `startScanning` /
     * `stopScanning` calls inside the ViewModel.
     *
     * @param active `true` to start listening; `false` to stop.
     */
    data class SetScannerActive(val active: Boolean) : PosIntent

    // ─── Hold / Retrieve ───────────────────────────────────────────────────────

    /**
     * Serialises the current cart to a held order via `HoldOrderUseCase` and
     * clears the active cart. The hold appears in [PosState.heldOrders].
     */
    data object HoldOrder : PosIntent

    /**
     * Restores a previously held order into the active cart via `RetrieveHeldOrderUseCase`.
     * Any currently active cart items are replaced (or optionally merged — ViewModel policy).
     *
     * @param holdId The [com.zyntasolutions.zyntapos.domain.model.Order.id] of the held order
     *   (returned by `HoldOrderUseCase`).
     */
    data class RetrieveHeld(val holdId: String) : PosIntent

    // ─── Payment ───────────────────────────────────────────────────────────────

    /**
     * Initiates payment processing via `ProcessPaymentUseCase`.
     *
     * On success the ViewModel emits [PosEffect.NavigateToPayment] or
     * [PosEffect.ShowReceiptScreen] depending on the payment method.
     *
     * @param method Primary [PaymentMethod]. Set to [PaymentMethod.SPLIT] when
     *   [splits] is non-empty.
     * @param splits Individual payment legs for split-payment scenarios.
     *   Must be empty when [method] is not [PaymentMethod.SPLIT].
     * @param tendered Amount tendered by the customer (cash/total). Used to
     *   calculate change. Pass `0.0` for card / mobile payments.
     */
    data class ProcessPayment(
        val method: PaymentMethod,
        val splits: List<PaymentSplit> = emptyList(),
        val tendered: Double = 0.0,
    ) : PosIntent

    // ─── Receipt ───────────────────────────────────────────────────────────────

    /**
     * Requests thermal printing of the receipt for the order currently stored in
     * [PosState.currentReceiptOrder]. Dispatched from [ReceiptScreen] when the
     * cashier taps the "Print" button.
     *
     * The ViewModel calls [com.zyntasolutions.zyntapos.domain.usecase.pos.PrintReceiptUseCase]
     * with `PosState.currentReceiptOrder` and updates [PosState.isPrinting] /
     * [PosState.printError] accordingly.
     */
    data object PrintCurrentReceipt : PosIntent

    /**
     * Clears [PosState.printError] after the cashier dismisses the retry dialog
     * in [ReceiptScreen] without retrying.
     */
    data object DismissPrintError : PosIntent
}
