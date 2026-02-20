package com.zyntasolutions.zyntapos.feature.pos

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.zyntasolutions.zyntapos.core.utils.CurrencyFormatter
import com.zyntasolutions.zyntapos.domain.model.DiscountType
import com.zyntasolutions.zyntapos.domain.model.Permission
import com.zyntasolutions.zyntapos.domain.usecase.auth.CheckPermissionUseCase
import com.zyntasolutions.zyntapos.feature.auth.guard.RoleGuard

// ─────────────────────────────────────────────────────────────────────────────
// OrderDiscountDialog — Applies a FLAT or PERCENT discount at the order level.
// Same core UI as ItemDiscountDialog — reuses DiscountDialogContent.
// RoleGuard(APPLY_DISCOUNT) enforced.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Order-level discount dialog applying a FLAT or PERCENT discount to the cart total.
 *
 * Delegates all input rendering to the shared [DiscountDialogContent] composable.
 * The [RoleGuard] wrapping enforces [Permission.APPLY_DISCOUNT] exactly as in
 * [ItemDiscountDialog], ensuring a consistent RBAC boundary.
 *
 * @param orderSubtotal         Current cart subtotal before discounts (used for percent cap).
 * @param currentDiscount       Currently applied order discount value.
 * @param currentDiscountType   Currently applied discount type.
 * @param onApply               Invoked with `(discount, type)` on confirmation.
 * @param onDismiss             Invoked on cancel / dismiss.
 * @param userId                Active session user ID for RBAC evaluation.
 * @param checkPermissionUseCase Koin-injected RBAC evaluator.
 * @param maxDiscountPercent    Max allowed discount as percent of [orderSubtotal] (0–100).
 * @param formatter             Currency formatter for labels.
 */
@Composable
fun OrderDiscountDialog(
    orderSubtotal: Double,
    currentDiscount: Double = 0.0,
    currentDiscountType: DiscountType = DiscountType.PERCENT,
    onApply: (discount: Double, type: DiscountType) -> Unit,
    onDismiss: () -> Unit,
    userId: String,
    checkPermissionUseCase: CheckPermissionUseCase,
    maxDiscountPercent: Double = 100.0,
    formatter: CurrencyFormatter = CurrencyFormatter(),
) {
    RoleGuard(
        userId = userId,
        permission = Permission.APPLY_DISCOUNT,
        checkPermissionUseCase = checkPermissionUseCase,
        unauthorizedContent = {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Access Denied") },
                text = { Text("You do not have permission to apply order discounts.") },
                confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
            )
        },
    ) {
        DiscountDialogContent(
            title = "Order Discount",
            subtitle = "Applied to the entire order total",
            lineTotal = orderSubtotal,
            currentDiscount = currentDiscount,
            currentDiscountType = currentDiscountType,
            maxDiscountPercent = maxDiscountPercent,
            formatter = formatter,
            onApply = onApply,
            onDismiss = onDismiss,
        )
    }
}
