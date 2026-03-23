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
    // ── Receipt preview state ─────────────────────────────────────────────────
    val receiptPreviewText: String = "",
    val currentReceiptOrder: Order? = null,
    val isPrinting: Boolean = false,
    val printError: String? = null,
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
)
