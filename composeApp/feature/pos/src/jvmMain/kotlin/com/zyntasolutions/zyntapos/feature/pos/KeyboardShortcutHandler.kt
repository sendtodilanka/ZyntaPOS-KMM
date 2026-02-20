package com.zyntasolutions.zyntapos.feature.pos

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/**
 * Desktop-only (jvmMain) keyboard shortcut handler for the POS screen (Sprint 14, task 9.1.6).
 *
 * Processes raw [KeyEvent]s captured by the POS screen's root composable via `Modifier.onKeyEvent`.
 * Returns `true` when an event is consumed (prevents further propagation), `false` otherwise.
 *
 * ### Shortcuts
 * | Key    | Action                                                        |
 * |--------|---------------------------------------------------------------|
 * | F2     | Focus the product search bar ([FocusRequester.requestFocus])  |
 * | F8     | Hold current order ([PosIntent.HoldOrder])                    |
 * | F9     | Show held orders panel (emits [onShowHeldOrders])             |
 * | Delete | Remove selected cart item ([PosIntent.RemoveFromCart])        |
 * | `+`    | Increment quantity of selected cart item by 1                 |
 * | `-`    | Decrement quantity of selected cart item by 1 (min 0 → remove)|
 *
 * ### Integration pattern
 * ```kotlin
 * // In PosScreen (Expanded layout — Desktop):
 * Box(
 *     modifier = Modifier
 *         .fillMaxSize()
 *         .onKeyEvent { event ->
 *             handlePosKeyEvent(
 *                 event            = event,
 *                 searchFocus      = searchFocusRequester,
 *                 selectedProductId = state.selectedCartItemId,
 *                 selectedQty      = state.cartItems.find { it.productId == state.selectedCartItemId }?.quantity ?: 1.0,
 *                 onIntent         = viewModel::dispatch,
 *                 onShowHeldOrders = { showHeldOrdersSheet = true },
 *             )
 *         }
 * ) { … }
 * ```
 *
 * @param event             Raw [KeyEvent] from the Compose key handler.
 * @param searchFocus       [FocusRequester] for the [PosSearchBar]. F2 calls [requestFocus].
 * @param selectedProductId The productId of the currently highlighted cart item, or `null`.
 * @param selectedQty       Current quantity of the selected cart item (used for +/- logic).
 * @param onIntent          Dispatch a [PosIntent] to the ViewModel.
 * @param onShowHeldOrders  Callback to open the held orders bottom sheet (F9).
 * @return `true` if the event was consumed; `false` to pass it through.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun handlePosKeyEvent(
    event: KeyEvent,
    searchFocus: FocusRequester,
    selectedProductId: String?,
    selectedQty: Double,
    onIntent: (PosIntent) -> Unit,
    onShowHeldOrders: () -> Unit,
): Boolean {
    // Only handle KEY_DOWN events to avoid double-firing on KEY_UP
    if (event.type != KeyEventType.KeyDown) return false

    return when (event.key) {
        Key.F2 -> {
            // Focus the search bar so cashier can type or trigger barcode scan
            searchFocus.requestFocus()
            true
        }
        Key.F8 -> {
            // Hold the current order
            onIntent(PosIntent.HoldOrder)
            true
        }
        Key.F9 -> {
            // Open the held orders sheet so cashier can retrieve a held order
            onShowHeldOrders()
            true
        }
        Key.Delete -> {
            // Remove the selected cart item (if any)
            selectedProductId?.let { id ->
                onIntent(PosIntent.RemoveFromCart(id))
            }
            selectedProductId != null
        }
        Key.Plus, Key.NumPadAdd -> {
            // Increment quantity of selected cart item
            selectedProductId?.let { id ->
                onIntent(PosIntent.UpdateQty(id, selectedQty + 1.0))
            }
            selectedProductId != null
        }
        Key.Minus, Key.NumPadSubtract -> {
            // Decrement — dispatching UpdateQty(0) → ViewModel removes the item
            selectedProductId?.let { id ->
                onIntent(PosIntent.UpdateQty(id, selectedQty - 1.0))
            }
            selectedProductId != null
        }
        else -> false
    }
}

/**
 * Composable wrapper around [handlePosKeyEvent] for callers that prefer a composable API.
 *
 * Returns the handler lambda ready to pass to `Modifier.onKeyEvent { … }`.
 * This indirection is provided for future extensibility (e.g., collecting shortcut hints
 * for a help overlay) and to keep [handlePosKeyEvent] fully unit-testable without Compose.
 *
 * @see handlePosKeyEvent for parameter documentation.
 */
@Composable
fun rememberPosKeyEventHandler(
    searchFocus: FocusRequester,
    selectedProductId: String?,
    selectedQty: Double,
    onIntent: (PosIntent) -> Unit,
    onShowHeldOrders: () -> Unit,
): (KeyEvent) -> Boolean = { event ->
    handlePosKeyEvent(
        event = event,
        searchFocus = searchFocus,
        selectedProductId = selectedProductId,
        selectedQty = selectedQty,
        onIntent = onIntent,
        onShowHeldOrders = onShowHeldOrders,
    )
}
