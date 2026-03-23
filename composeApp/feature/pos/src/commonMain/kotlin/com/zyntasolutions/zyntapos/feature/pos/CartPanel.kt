package com.zyntasolutions.zyntapos.feature.pos

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaBottomSheet
import com.zyntasolutions.zyntapos.designsystem.util.WindowSize
import com.zyntasolutions.zyntapos.designsystem.util.currentWindowSize
import com.zyntasolutions.zyntapos.domain.model.CartItem
import com.zyntasolutions.zyntapos.domain.model.Customer
import com.zyntasolutions.zyntapos.domain.model.DiscountType
import com.zyntasolutions.zyntapos.domain.model.OrderTotals

// ─────────────────────────────────────────────────────────────────────────────
// CartPanel — Adaptive cart container.
//   EXPANDED: permanent right-side panel (40% width) alongside product grid.
//   COMPACT/MEDIUM: ZyntaBottomSheet (draggable) so product grid is full-screen.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Adaptive cart container for the POS checkout screen.
 *
 * On **EXPANDED** (desktop / large tablet) window sizes, the cart renders as a
 * permanent 40%-width right-side panel displayed alongside the product grid — no
 * overlay, no interaction block.
 *
 * On **COMPACT** and **MEDIUM** window sizes, the cart is surfaced inside a
 * draggable [ZyntaBottomSheet]. The caller controls visibility via [isSheetVisible]
 * / [onDismissSheet] so the sheet can be toggled by a FAB or the cart-tab.
 *
 * State is fully hoisted; this composable is stateless.
 *
 * @param cartItems        Current list of cart line items.
 * @param orderTotals      Computed financial summary (subtotal, tax, discount, total).
 * @param selectedCustomer Customer attached to the order; `null` for walk-in.
 * @param onIntent         Dispatcher for [PosIntent] — routes all user actions upward.
 * @param isSheetVisible   Controls bottom-sheet visibility on compact screens.
 * @param onDismissSheet   Invoked when the bottom-sheet is swiped/dismissed.
 * @param modifier         Optional [Modifier] applied to the expanded panel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartPanel(
    cartItems: List<CartItem>,
    orderTotals: OrderTotals,
    selectedCustomer: Customer?,
    onIntent: (PosIntent) -> Unit,
    isSheetVisible: Boolean,
    onDismissSheet: () -> Unit,
    modifier: Modifier = Modifier,
    loyaltyPointsBalance: Int? = null,
    loyaltyPointsToRedeem: Int = 0,
    loyaltyDiscount: Double = 0.0,
) {
    val windowSizeClass = currentWindowSize()

    if (windowSizeClass == WindowSize.EXPANDED) {
        // ── Expanded: permanent side panel ────────────────────────────────────
        Surface(
            modifier = modifier
                .fillMaxHeight()
                .width(400.dp),
            tonalElevation = 2.dp,
        ) {
            CartContent(
                cartItems = cartItems,
                orderTotals = orderTotals,
                selectedCustomer = selectedCustomer,
                onIntent = onIntent,
                loyaltyPointsBalance = loyaltyPointsBalance,
                loyaltyPointsToRedeem = loyaltyPointsToRedeem,
                loyaltyDiscount = loyaltyDiscount,
                modifier = Modifier.fillMaxSize(),
            )
        }
    } else {
        // ── Compact / Medium: draggable bottom sheet ──────────────────────────
        if (isSheetVisible) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
            ZyntaBottomSheet(
                sheetState = sheetState,
                onDismiss = onDismissSheet,
                dragHandleVisible = true,
            ) {
                CartContent(
                    cartItems = cartItems,
                    orderTotals = orderTotals,
                    selectedCustomer = selectedCustomer,
                    onIntent = onIntent,
                    loyaltyPointsBalance = loyaltyPointsBalance,
                loyaltyPointsToRedeem = loyaltyPointsToRedeem,
                loyaltyDiscount = loyaltyDiscount,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                )
            }
        }
    }
}
