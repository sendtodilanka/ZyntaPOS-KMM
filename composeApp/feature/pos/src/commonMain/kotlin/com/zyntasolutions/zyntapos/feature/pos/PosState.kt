package com.zyntasolutions.zyntapos.feature.pos

import androidx.compose.runtime.Immutable
import com.zyntasolutions.zyntapos.domain.model.CartItem
import com.zyntasolutions.zyntapos.domain.model.Category
import com.zyntasolutions.zyntapos.domain.model.Coupon
import com.zyntasolutions.zyntapos.domain.model.Customer
import com.zyntasolutions.zyntapos.domain.model.DiscountType
import com.zyntasolutions.zyntapos.domain.model.Order
import com.zyntasolutions.zyntapos.domain.model.OrderTotals
import com.zyntasolutions.zyntapos.domain.model.Product

// NOTE: receiptPreviewText is populated by PosViewModel via ReceiptFormatter after payment
// succeeds. currentReceiptOrder is retained so PosViewModel can call PrintReceiptUseCase
// when the cashier taps "Print" in ReceiptScreen.

/**
 * Immutable UI state for the POS (Point-of-Sale) checkout screen.
 *
 * This is the **single source of truth** consumed by `PosScreen`. All fields are
 * read-only snapshots emitted by `PosViewModel` via its `StateFlow<PosState>`.
 *
 * ### State Lifecycle
 * 1. `PosViewModel` starts with [PosState] defaults (empty cart, no selection).
 * 2. `LoadProducts` intent triggers a repository fetch; [isLoading] becomes `true`
 *    until [products] + [categories] are populated.
 * 3. Cashier interactions (scan, tap, search) drive incremental state updates.
 * 4. [orderTotals] is recalculated by `CalculateOrderTotalsUseCase` after every
 *    cart mutation — **never computed inside the composable**.
 *
 * ### Performance Notes
 * - `LazyVerticalGrid` driving the product catalogue depends on [products].
 *   Filter by [selectedCategoryId] / [searchQuery] inside the ViewModel
 *   (not in the Composable) to avoid recomposition on every keystroke.
 * - [cartItems] is bounded in practice (rarely > 50 lines) so a regular
 *   `LazyColumn` is appropriate for the cart panel.
 *
 * @property products Full list of active products matching the current
 *   [selectedCategoryId] and [searchQuery] filters. Pre-filtered by ViewModel.
 * @property categories All active categories available for the chip-row filter.
 * @property selectedCategoryId The currently active category filter. `null` means
 *   "All" is selected (no category filter applied).
 * @property searchQuery The live text entered in the product search bar.
 * @property isSearchFocused Whether the search field currently has keyboard focus.
 *   Used to conditionally show/hide the scanner FAB and expand the search panel.
 * @property cartItems Ordered list of line items currently in the active cart.
 *   Empty list represents a cleared or fresh cart.
 * @property selectedCustomer The customer attached to the current order. `null` for
 *   walk-in (anonymous) sales. Set via [PosIntent.SelectCustomer].
 * @property orderDiscount The discount value applied at the order level (not per-item).
 *   Interpreted according to [orderDiscountType].
 * @property orderDiscountType Whether [orderDiscount] is a [DiscountType.FIXED] amount
 *   or a [DiscountType.PERCENT] of the subtotal.
 * @property heldOrders List of previously held orders (status = HELD) available for
 *   retrieval. Populated on screen load and refreshed after each [PosIntent.HoldOrder].
 *   These are full [Order] domain objects — the ViewModel extracts cart items via
 *   `RetrieveHeldOrderUseCase` when [PosIntent.RetrieveHeld] is dispatched.
 * @property orderTotals Computed financial summary for the current cart: subtotal,
 *   tax, discount and grand total. Initialised as [OrderTotals.EMPTY].
 * @property isLoading `true` while an async operation is in-flight (product load,
 *   barcode lookup, payment processing). Drives the loading indicator overlay.
 * @property scannerActive `true` when the HAL barcode scanner has been activated
 *   (listening for scan events). Used to render the scanner status indicator.
 * @property error A non-null string contains a user-visible error message to be
 *   surfaced via a snackbar or inline banner. `null` = no current error.
 * @property receiptPreviewText Human-readable receipt text for [ReceiptScreen]. Populated by
 *   [PosViewModel] via [com.zyntasolutions.zyntapos.domain.formatter.ReceiptFormatter] immediately
 *   after a successful payment. Empty string when no receipt is pending.
 * @property currentReceiptOrder The [Order] produced by the most recent successful payment.
 *   Retained so [PosViewModel] can call [com.zyntasolutions.zyntapos.domain.usecase.pos.PrintReceiptUseCase]
 *   when the cashier taps "Print" in [ReceiptScreen]. Set to `null` after the receipt flow is dismissed.
 * @property isPrinting `true` while a print job is in-flight; drives the loading indicator on the
 *   Print button in [ReceiptScreen].
 * @property printError Non-null when the last print attempt failed. Cleared after the cashier
 *   acknowledges the retry dialog.
 */
@Immutable
data class PosState(
    val products: List<Product> = emptyList(),
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: String? = null,
    val searchQuery: String = "",
    val isSearchFocused: Boolean = false,
    /** Cashier display name for the POS header. Populated from auth session on init. */
    val cashierName: String = "",
    /** Store display name for the POS header. Populated from StoreRepository on init. */
    val storeName: String = "",
    /** Active store ID for context-aware operations. Populated from auth session on init. */
    val activeStoreId: String = "",
    /** ISO 4217 currency code from store settings (e.g. "LKR", "USD"). Drives all price formatting in the POS UI. */
    val storeCurrency: String = "LKR",
    val cartItems: List<CartItem> = emptyList(),
    val selectedCustomer: Customer? = null,
    // ── Customer picker ───────────────────────────────────────────────────────
    /** Candidate list shown in the customer picker dialog. Populated by [PosIntent.RequestCustomerSelect] / [PosIntent.SearchCustomers]. */
    val customerPickerResults: List<Customer> = emptyList(),
    /** Live search query entered inside the customer picker dialog. */
    val customerSearchQuery: String = "",
    val orderDiscount: Double = 0.0,
    val orderDiscountType: DiscountType = DiscountType.FIXED,
    /** Free-text note attached to the current order. Forwarded to [com.zyntasolutions.zyntapos.domain.usecase.pos.ProcessPaymentUseCase] at checkout. */
    val orderNotes: String = "",
    val heldOrders: List<Order> = emptyList(),
    val orderTotals: OrderTotals = OrderTotals.EMPTY,
    val isLoading: Boolean = false,
    val scannerActive: Boolean = false,
    val error: String? = null,
    // ── Wallet & Loyalty (populated when a customer is selected) ─────────────
    /** `true` when the wallet payment choice dialog is visible. */
    val showWalletPaymentDialog: Boolean = false,
    /** Current store-credit balance for [selectedCustomer]. `null` if no customer or not yet loaded. */
    val walletBalance: Double? = null,
    /** Current loyalty points balance for [selectedCustomer]. `null` if no customer or not yet loaded. */
    val loyaltyPointsBalance: Int? = null,
    /** Monetary amount the cashier has elected to pay via the customer's store-credit wallet. */
    val walletPaymentAmount: Double = 0.0,
    /** Number of loyalty points the cashier has elected to redeem for this transaction. */
    val loyaltyPointsToRedeem: Int = 0,
    /** Pre-computed monetary discount for [loyaltyPointsToRedeem] at the current conversion rate. */
    val loyaltyDiscount: Double = 0.0,
    // ── Coupon ────────────────────────────────────────────────────────────────
    /** Raw code text currently in the coupon entry field. */
    val couponCode: String = "",
    /** `true` while [ValidateCouponUseCase] is running. */
    val couponValidating: Boolean = false,
    /** The validated coupon that has been applied to this transaction. `null` if none. */
    val appliedCoupon: Coupon? = null,
    /** Pre-computed monetary discount for [appliedCoupon] on the current cart total. */
    val couponDiscount: Double = 0.0,
    /** Non-null when coupon validation fails; cleared when a new code is entered or coupon is cleared. */
    val couponError: String? = null,
    // ── Store Promotions (C2.4) ───────────────────────────────────────────────
    /** Pre-computed monetary discount from auto-applied store promotions (flash sales, BOGO, bundles). */
    val autoPromotionDiscount: Double = 0.0,
    // ── Receipt preview state ─────────────────────────────────────────────────
    val receiptPreviewText: String = "",
    val currentReceiptOrder: Order? = null,
    val isPrinting: Boolean = false,
    val printError: String? = null,
    // ── Return / Lookup ───────────────────────────────────────────────────────
    /** `true` when the manual return lookup dialog is visible. */
    val showReturnLookupDialog: Boolean = false,
    /** Live text in the return lookup order ID / receipt number field. */
    val returnLookupQuery: String = "",
    /** Non-null when the last lookup attempt failed; cleared when query changes. */
    val returnLookupError: String? = null,
    /** `true` while [LookupOrderForReturnUseCase] is running. */
    val isReturnLookupLoading: Boolean = false,
    // ── Cross-store Return (G3-1) ──────────────────────────────────────────────
    /** `true` when the cashier has activated cross-store return mode. */
    val crossStoreReturnMode: Boolean = false,
    /** The order ID entered by the cashier for cross-store return lookup. */
    val crossStoreOrderId: String = "",
    /** Non-null when the cross-store order lookup fails; cleared when the order ID changes. */
    val crossStoreOrderLookupError: String? = null,
    /** The order retrieved from another store for return processing. `null` until a successful lookup. */
    val crossStoreOrder: Order? = null,
    // ── Reprint / A4 Invoice / Email ──────────────────────────────────────────
    /** `true` while a reprint job for a past order is in-flight. */
    val isReprintingReceipt: Boolean = false,
    /** `true` while an A4 tax invoice print job is in-flight. */
    val isPrintingA4: Boolean = false,
    /** `true` while a receipt email is being sent. */
    val isEmailingReceipt: Boolean = false,
    /** When `true`, the email receipt dialog is visible. */
    val emailDialogOpen: Boolean = false,
    /** The order ID for which the email dialog was opened. */
    val emailDialogOrderId: String? = null,
    // ── Card Terminal (G3-3) ─────────────────────────────────────────────────
    /** `true` when an EMV card terminal is connected and ready for transactions. */
    val cardTerminalConnected: Boolean = false,
    /** Display name of the connected card terminal (e.g. "Verifone P400"). Empty when disconnected. */
    val cardTerminalName: String = "",
    // ── Gift Card (G3-2) ──────────────────────────────────────────────────────
    /** `true` when the gift card lookup dialog is visible. */
    val showGiftCardDialog: Boolean = false,
    /** Barcode or code entered for gift card lookup. */
    val giftCardCode: String = "",
    /** Balance found for the scanned gift card. `null` if not yet looked up or not found. */
    val giftCardBalance: Double? = null,
    /** Amount to apply from gift card to current transaction. */
    val giftCardPaymentAmount: Double = 0.0,
    /** Error from gift card lookup. */
    val giftCardError: String? = null,
    /** `true` while looking up gift card balance. */
    val isGiftCardLoading: Boolean = false,
    // ── Multi-Currency Display (G3-5/G8-2) ──────────────────────────────────
    /** Secondary/display currency code (e.g. "USD"). Empty when multi-currency is disabled. */
    val secondaryCurrency: String = "",
    /** Exchange rate: 1 unit of [storeCurrency] = [exchangeRate] units of [secondaryCurrency]. */
    val exchangeRate: Double = 0.0,
    /** `true` when multi-currency display is enabled in store settings. */
    val showMultiCurrency: Boolean = false,
)
