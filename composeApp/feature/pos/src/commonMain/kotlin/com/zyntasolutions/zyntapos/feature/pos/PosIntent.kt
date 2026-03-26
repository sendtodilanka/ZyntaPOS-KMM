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

    /**
     * Loads the store's configured currency code from [SettingsRepository] and
     * updates [PosState.storeCurrency]. Also sets [CurrencyFormatter.defaultCurrency]
     * so all price displays use the correct currency symbol.
     */
    data object LoadStoreCurrency : PosIntent

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

    /**
     * Opens the customer search/picker dialog.
     *
     * The ViewModel pre-loads all customers into [PosState.customerPickerResults]
     * and emits [PosEffect.OpenCustomerPicker] so the UI can display the dialog.
     */
    data object RequestCustomerSelect : PosIntent

    /**
     * Filters the customer picker list by the cashier's live search text.
     * Results are pushed into [PosState.customerPickerResults].
     *
     * @param query Search text (name or phone fragment). Empty string resets to full list.
     */
    data class SearchCustomers(val query: String) : PosIntent

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
     * Signals that the cashier wants to proceed to checkout.
     *
     * The ViewModel validates preconditions (non-empty cart) and responds with
     * [PosEffect.OpenPaymentSheet] so the UI can display [PaymentScreen].
     * This intent never processes payment directly — it only opens the payment UI.
     */
    data object RequestPayment : PosIntent

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

    // ─── Wallet ────────────────────────────────────────────────────────────────

    /**
     * Loads or refreshes the wallet balance and loyalty points for the currently
     * [selectedCustomer]. Dispatched automatically when [SelectCustomer] is processed.
     */
    data object LoadCustomerWallet : PosIntent

    /**
     * Sets the portion of the order total the cashier wants to pay via the customer's
     * store-credit wallet.
     *
     * @param amount Wallet debit amount (0.0 = no wallet payment). Must not exceed [PosState.walletBalance].
     */
    data class SetWalletPaymentAmount(val amount: Double) : PosIntent

    /**
     * Opens the wallet payment choice dialog. Loads the customer's current wallet
     * balance from [CustomerWalletRepository] and sets [PosState.showWalletPaymentDialog] to `true`.
     * Requires [PosState.selectedCustomer] to be non-null; no-ops otherwise.
     */
    data object ShowWalletPaymentDialog : PosIntent

    /**
     * Dismisses the wallet payment choice dialog and resets [PosState.walletPaymentAmount] to 0.
     */
    data object DismissWalletPaymentDialog : PosIntent

    /**
     * Updates [PosState.walletPaymentAmount] from the wallet payment dialog input.
     * The amount is capped at the customer's available [PosState.walletBalance].
     *
     * @param amount Desired wallet payment amount entered by the cashier.
     */
    data class WalletPaymentAmountChanged(val amount: Double) : PosIntent

    /**
     * Confirms the wallet payment amount entered in the dialog and applies it to the
     * order's payment splits. Dismisses the dialog on completion.
     */
    data object ConfirmWalletPayment : PosIntent

    /**
     * Sets the number of loyalty points to redeem for this transaction.
     * The ViewModel converts points to a monetary discount via [CalculateLoyaltyDiscountUseCase]
     * and updates [PosState.loyaltyDiscount].
     *
     * @param points Points to redeem (0 = no redemption). Must not exceed [PosState.loyaltyPointsBalance].
     */
    data class SetLoyaltyPointsRedemption(val points: Int) : PosIntent

    // ─── Coupon ────────────────────────────────────────────────────────────────

    /**
     * Updates the coupon code text field. Clears any previous validation error.
     *
     * @param code Current text in the coupon code input.
     */
    data class EnterCouponCode(val code: String) : PosIntent

    /**
     * Triggers async validation of [PosState.couponCode] via [ValidateCouponUseCase].
     * On success, populates [PosState.appliedCoupon] and [PosState.couponDiscount].
     * On failure, sets [PosState.couponError].
     */
    data object ValidateCoupon : PosIntent

    /**
     * Removes the applied coupon, resets [PosState.couponDiscount] to 0, and clears
     * [PosState.couponCode] and [PosState.couponError].
     */
    data object ClearCoupon : PosIntent

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

    // ─── Reprint / A4 Invoice ──────────────────────────────────────────────────

    /** Reprints a thermal receipt for a past order (e.g. from Order History). */
    data class ReprintReceipt(val orderId: String) : PosIntent

    /** Opens the email dialog pre-filled for [orderId]. */
    data class OpenEmailDialog(val orderId: String) : PosIntent

    /** Dismisses the email dialog without sending. */
    data object DismissEmailDialog : PosIntent

    /** Sends the receipt for [orderId] to [emailAddress] as a PDF attachment. */
    data class EmailReceipt(val orderId: String, val emailAddress: String) : PosIntent

    /** Prints an A4 tax invoice PDF for [orderId] via the system print dialog. */
    data class PrintA4Invoice(val orderId: String) : PosIntent

    // ─── Context-aware barcode scans ───────────────────────────────────────────

    /** Barcode detected as a receipt barcode — navigate to the refund/return flow. */
    data class ScanReceiptBarcode(val barcode: String) : PosIntent

    /** Barcode detected as a loyalty card — auto-attach the matching customer. */
    data class ScanLoyaltyCard(val barcode: String) : PosIntent

    /** Barcode detected as a coupon code — auto-apply the coupon. */
    data class ScanCoupon(val barcode: String) : PosIntent

    /** Barcode detected as a gift card — look up the balance and apply to payment. */
    data class ScanGiftCard(val barcode: String) : PosIntent

    /** G3: Dismiss the coupon scan preview overlay after it has been shown. */
    data object DismissCouponScanPreview : PosIntent

    // ─── Gift Card (G3-2) ─────────────────────────────────────────────────────

    /** Show the gift card lookup dialog. */
    data object ShowGiftCardDialog : PosIntent
    /** Dismiss the gift card dialog. */
    data object DismissGiftCardDialog : PosIntent
    /** Update the gift card code text field. */
    data class GiftCardCodeChanged(val code: String) : PosIntent
    /** Lookup the gift card balance by code. */
    data object LookupGiftCard : PosIntent
    /** Set the amount to apply from gift card to transaction. */
    data class GiftCardPaymentAmountChanged(val amount: Double) : PosIntent
    /** Apply the gift card payment and close the dialog. */
    data object ConfirmGiftCardPayment : PosIntent

    // ─── Return / Lookup ───────────────────────────────────────────────────────

    /** Opens the manual order lookup dialog for return processing. */
    data object ShowReturnLookupDialog : PosIntent

    /** Closes the return lookup dialog without navigating. */
    data object DismissReturnLookupDialog : PosIntent

    /**
     * Updates the live text in the return lookup query field.
     *
     * @param query Current text in the order ID / receipt number field.
     */
    data class SetReturnLookupQuery(val query: String) : PosIntent

    /**
     * Triggers a lookup of [PosState.returnLookupQuery] via [LookupOrderForReturnUseCase].
     * On success, emits [PosEffect.NavigateToRefund]. On failure, sets [PosState.returnLookupError].
     */
    data object LookupOrderForReturn : PosIntent

    // ─── Card Terminal (G3-3) ────────────────────────────────────────────────

    /**
     * Polls the current card terminal connection status via the HAL layer.
     * For now (pre-Phase 2 HAL integration) this sets [PosState.cardTerminalConnected] to `false`.
     */
    data object CheckCardTerminalStatus : PosIntent

    /**
     * Dispatched by the HAL card-terminal listener when the connection state changes.
     *
     * @param connected `true` when the terminal is connected and ready.
     * @param name Display name of the terminal (e.g. "Verifone P400"). Empty when disconnected.
     */
    data class CardTerminalStatusChanged(val connected: Boolean, val name: String) : PosIntent

    // ─── Cross-store Return (G3-1) ─────────────────────────────────────────────

    /** Toggles cross-store return mode on/off. When activated, the UI shows the cross-store order lookup panel. */
    data object ToggleCrossStoreReturnMode : PosIntent

    /**
     * Updates the order ID text field in the cross-store return lookup panel.
     * Clears any previous lookup error.
     *
     * @param orderId Current text in the cross-store order ID field.
     */
    data class CrossStoreOrderIdChanged(val orderId: String) : PosIntent

    /**
     * Triggers a lookup of [PosState.crossStoreOrderId] via [LookupOrderForReturnUseCase].
     * On success, populates [PosState.crossStoreOrder]. On failure, sets [PosState.crossStoreOrderLookupError].
     */
    data object LookupCrossStoreOrder : PosIntent

    /** Resets all cross-store return state fields and exits cross-store return mode. */
    data object CancelCrossStoreReturn : PosIntent
}
