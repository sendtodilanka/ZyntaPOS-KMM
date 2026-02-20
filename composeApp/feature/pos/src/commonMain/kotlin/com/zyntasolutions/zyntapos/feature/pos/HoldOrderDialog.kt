package com.zyntasolutions.zyntapos.feature.pos

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

// ─────────────────────────────────────────────────────────────────────────────
// HoldOrderDialog — Confirmation dialog before holding the current order.
// F8 shortcut → KeyboardShortcutHandler dispatches PosIntent.HoldOrder →
// PosScreen intercepts via HoldOrderDialog before forwarding to ViewModel.
// On success, a snackbar with the hold ID is shown.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Confirmation dialog displayed when the cashier triggers "Hold Order" (F8 or UI button).
 *
 * The dialog must be shown **before** dispatching [PosIntent.HoldOrder] to the ViewModel.
 * On confirmation, [onConfirm] is invoked which should dispatch the intent. The ViewModel
 * emits a [PosEffect] (or a state update) that the caller converts to a snackbar
 * message containing the hold ID.
 *
 * ### Integration in PosScreen
 * ```kotlin
 * var showHoldDialog by remember { mutableStateOf(false) }
 *
 * // F8 / Hold button sets showHoldDialog = true
 * if (showHoldDialog) {
 *     HoldOrderConfirmDialog(
 *         itemCount = state.cartItems.size,
 *         onConfirm = {
 *             showHoldDialog = false
 *             viewModel.dispatch(PosIntent.HoldOrder)
 *         },
 *         onDismiss = { showHoldDialog = false },
 *     )
 * }
 * ```
 *
 * @param itemCount  Number of items in the current cart — shown in dialog body.
 * @param onConfirm  Invoked when the cashier taps "Hold Order" to proceed.
 * @param onDismiss  Invoked when the cashier taps "Cancel" or dismisses the dialog.
 */
@Composable
fun HoldOrderConfirmDialog(
    itemCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Pause,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = { Text("Hold Order?") },
        text = {
            Text(
                "The current order ($itemCount item${if (itemCount != 1) "s" else ""}) will be " +
                    "parked. You can retrieve it from the Held Orders panel (F9) at any time.",
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text("Hold Order") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/**
 * Builds the snackbar message to show after a successful hold operation.
 *
 * ```kotlin
 * // In PosScreen LaunchedEffect collecting PosEffect:
 * is PosEffect.OrderHeld -> {
 *     snackbarHostState.showSnackbar(holdOrderSnackbarMessage(effect.holdId))
 * }
 * ```
 *
 * @param holdId The Order ID returned by [HoldOrderUseCase] — shown to the cashier
 *               so they can reference it when retrieving the order.
 */
fun holdOrderSnackbarMessage(holdId: String): String =
    "Order held ✓  Hold ID: ${holdId.takeLast(8).uppercase()}"
