package com.zyntasolutions.zyntapos.feature.pos

/**
 * One-shot side-effect events emitted by `PosViewModel` for actions that cannot
 * be modelled as persistent [PosState] updates.
 *
 * Collected in `PosScreen` via `LaunchedEffect(Unit) { viewModel.effects.collect { … } }`.
 * Each subtype is consumed once and discarded — they are never re-delivered on
 * recomposition.
 *
 * ### Consumption Pattern
 * ```kotlin
 * LaunchedEffect(Unit) {
 *     viewModel.effects.collect { effect ->
 *         when (effect) {
 *             is PosEffect.NavigateToPayment  -> navController.navigate(ZyntaRoute.Payment(effect.orderId))
 *             is PosEffect.ShowReceiptScreen  -> navController.navigate(ZyntaRoute.Receipt(effect.orderId))
 *             is PosEffect.ShowError          -> snackbarHostState.showSnackbar(effect.msg)
 *             is PosEffect.PrintReceipt       -> /* delegate to HAL printer */
 *             is PosEffect.BarcodeNotFound    -> snackbarHostState.showSnackbar("Barcode not found: ${effect.barcode}")
 *             is PosEffect.OpenCashDrawer     -> /* delegate to HAL cash drawer */
 *         }
 *     }
 * }
 * ```
 */
sealed interface PosEffect {

    /**
     * Instructs the UI to open the payment screen overlay so the cashier can
     * select a payment method and enter the tendered amount.
     *
     * Emitted in response to [PosIntent.RequestPayment] after the ViewModel
     * confirms the cart is non-empty.
     */
    data object OpenPaymentSheet : PosEffect

    /**
     * Instructs the UI to open the customer search/picker dialog.
     *
     * Emitted by the ViewModel after pre-loading [PosState.customerPickerResults]
     * in response to [PosIntent.RequestCustomerSelect].
     */
    data object OpenCustomerPicker : PosEffect

    /**
     * Navigate to the payment confirmation screen after an order has been
     * successfully created but not yet tendered.
     *
     * Emitted when [PosIntent.ProcessPayment] succeeds and the configured
     * payment flow requires a dedicated payment step (e.g., card terminal handoff).
     *
     * @param orderId The newly created [com.zyntasolutions.zyntapos.domain.model.Order.id].
     */
    data class NavigateToPayment(val orderId: String) : PosEffect

    /**
     * Navigate directly to the receipt screen after a completed cash or
     * mobile payment where no separate payment confirmation step is needed.
     *
     * @param orderId The completed [com.zyntasolutions.zyntapos.domain.model.Order.id].
     */
    data class ShowReceiptScreen(val orderId: String) : PosEffect

    /**
     * Show a transient error message to the cashier (typically via a `Snackbar`).
     * Does not alter [PosState.error] — use [PosState.error] for persistent inline
     * validation errors; use this effect for ephemeral, dismissible messages.
     *
     * @param msg Localised, user-visible error text.
     */
    data class ShowError(val msg: String) : PosEffect

    /**
     * Signal the HAL printer layer to print a receipt for the given order.
     * Emitted alongside [ShowReceiptScreen] when auto-print is enabled in settings.
     *
     * @param orderId Target [com.zyntasolutions.zyntapos.domain.model.Order.id].
     */
    data class PrintReceipt(val orderId: String) : PosEffect

    /**
     * Emitted when a barcode scan returns no matching product.
     * The UI should display a brief dismissible message with the raw barcode value
     * so the cashier can locate the product manually.
     *
     * @param barcode The unrecognised barcode string that was scanned.
     */
    data class BarcodeNotFound(val barcode: String) : PosEffect

    /**
     * Signal the HAL cash-drawer layer to open the cash drawer.
     * Typically emitted after a successful cash payment alongside [PrintReceipt].
     *
     * @param registerId The active register session's register ID, used by the HAL
     *   to route the open pulse to the correct drawer interface.
     */
    data class OpenCashDrawer(val registerId: String) : PosEffect

    /** Show the email dialog so the cashier can enter the recipient address. */
    data object ShowEmailDialog : PosEffect

    /** The receipt email was sent successfully — show a confirmation snackbar. */
    data object ReceiptEmailSent : PosEffect

    /** The A4 tax invoice was sent to the system print dialog. */
    data object A4InvoicePrinted : PosEffect

    /**
     * Navigate to the refund/return flow for [orderId].
     * Emitted when a receipt barcode is scanned in POS mode.
     */
    data class NavigateToRefund(val orderId: String) : PosEffect

    /**
     * Prompt the cashier for a Google Play Store in-app review.
     * Emitted by [PosViewModel] after every 5th successful sale.
     *
     * Handled in [PosScreen] by [PosAppReviewEffect] — Android shows the native
     * review overlay; JVM Desktop silently consumes it (no-op).
     */
    data object RequestAppReview : PosEffect
}
