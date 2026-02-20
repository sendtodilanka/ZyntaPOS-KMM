package com.zyntasolutions.zyntapos.feature.pos

import com.zyntasolutions.zyntapos.domain.model.CartItem
import com.zyntasolutions.zyntapos.domain.model.Category
import com.zyntasolutions.zyntapos.domain.model.Customer
import com.zyntasolutions.zyntapos.domain.model.DiscountType
import com.zyntasolutions.zyntapos.domain.model.Order
import com.zyntasolutions.zyntapos.domain.model.OrderTotals
import com.zyntasolutions.zyntapos.domain.model.Product

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
 */
data class PosState(
    val products: List<Product> = emptyList(),
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: String? = null,
    val searchQuery: String = "",
    val isSearchFocused: Boolean = false,
    val cartItems: List<CartItem> = emptyList(),
    val selectedCustomer: Customer? = null,
    val orderDiscount: Double = 0.0,
    val orderDiscountType: DiscountType = DiscountType.FIXED,
    val heldOrders: List<Order> = emptyList(),
    val orderTotals: OrderTotals = OrderTotals.EMPTY,
    val isLoading: Boolean = false,
    val scannerActive: Boolean = false,
    val error: String? = null,
)
