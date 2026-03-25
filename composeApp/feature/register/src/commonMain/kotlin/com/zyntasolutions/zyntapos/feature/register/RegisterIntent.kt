package com.zyntasolutions.zyntapos.feature.register

import com.zyntasolutions.zyntapos.domain.model.CashMovement

/**
 * All user interactions and system events that can mutate [RegisterState].
 *
 * [RegisterIntent] is the single entry point into [RegisterViewModel].
 * The Composable layer dispatches intents and observes resulting state.
 *
 * ### Intent Categories
 * - **Guard / init:** [ObserveActiveSession]
 * - **Open Register:** [SelectRegister], [OpeningBalanceDigit], [OpeningBalanceDoubleZero],
 *   [OpeningBalanceDecimal], [OpeningBalanceBackspace], [OpeningBalanceClear],
 *   [OpeningNotesChanged], [ConfirmOpenRegister]
 * - **Dashboard:** [LoadDashboardStats]
 * - **Cash In/Out:** [ShowCashInOutDialog], [SetCashInOutType], [CashInOutAmountDigit],
 *   [CashInOutAmountDoubleZero], [CashInOutAmountDecimal], [CashInOutAmountBackspace],
 *   [CashInOutAmountClear], [CashInOutReasonChanged], [ConfirmCashInOut], [DismissCashInOut]
 * - **Close Register (Sprint 21):** [LoadCloseRegisterData], [ActualBalanceDigit],
 *   [ActualBalanceDoubleZero], [ActualBalanceBackspace], [ActualBalanceClear],
 *   [ClosingNotesChanged], [ShowCloseConfirmation], [ConfirmCloseRegister], [DismissCloseConfirmation]
 * - **Z-Report (Sprint 21):** [LoadZReport], [PrintZReport]
 * - **UI:** [DismissError], [DismissSuccess]
 */
sealed interface RegisterIntent {

    // ─── Guard / Init ──────────────────────────────────────────────────────

    /** Starts observing the active session Flow. Called by [RegisterGuard] on composition. */
    data object ObserveActiveSession : RegisterIntent

    /** Loads the list of registers available for the current user's store. */
    data object LoadAvailableRegisters : RegisterIntent

    // ─── Open Register screen ──────────────────────────────────────────────

    /** Selects a register by ID on the Open Register screen. */
    data class SelectRegister(val registerId: String) : RegisterIntent

    /** Appends a digit to the opening balance numeric pad. */
    data class OpeningBalanceDigit(val digit: String) : RegisterIntent

    /** Appends "00" to the opening balance numeric pad. */
    data object OpeningBalanceDoubleZero : RegisterIntent

    /** Appends "." to the opening balance numeric pad (no-op for right-to-left PRICE mode). */
    data object OpeningBalanceDecimal : RegisterIntent

    /** Removes the last character from the opening balance raw string. */
    data object OpeningBalanceBackspace : RegisterIntent

    /** Resets the opening balance to "0". */
    data object OpeningBalanceClear : RegisterIntent

    /** Updates the opening notes text field. */
    data class OpeningNotesChanged(val notes: String) : RegisterIntent

    /** Validates and submits the open-register form. */
    data object ConfirmOpenRegister : RegisterIntent

    // ─── Dashboard ────────────────────────────────────────────────────────

    /** Triggers a refresh of today's order count and revenue. */
    data object LoadDashboardStats : RegisterIntent

    // ─── Cash In/Out dialog ───────────────────────────────────────────────

    /** Opens the Cash In/Out dialog (pre-selects [initialType]). */
    data class ShowCashInOutDialog(
        val initialType: CashMovement.Type = CashMovement.Type.IN,
    ) : RegisterIntent

    /** Switches the IN/OUT toggle inside the dialog. */
    data class SetCashInOutType(val type: CashMovement.Type) : RegisterIntent

    /** Appends a digit to the Cash In/Out amount. */
    data class CashInOutAmountDigit(val digit: String) : RegisterIntent

    /** Appends "00" to the Cash In/Out amount. */
    data object CashInOutAmountDoubleZero : RegisterIntent

    /** Decimal key (no-op in PRICE mode, included for completeness). */
    data object CashInOutAmountDecimal : RegisterIntent

    /** Removes the last character from the Cash In/Out amount. */
    data object CashInOutAmountBackspace : RegisterIntent

    /** Resets the Cash In/Out amount to "0". */
    data object CashInOutAmountClear : RegisterIntent

    /** Updates the reason text field inside the dialog. */
    data class CashInOutReasonChanged(val reason: String) : RegisterIntent

    /** Validates and submits the Cash In/Out movement. */
    data object ConfirmCashInOut : RegisterIntent

    /** Closes the Cash In/Out dialog without saving. */
    data object DismissCashInOut : RegisterIntent

    // ─── Close Register (Sprint 21, task 11.1.6) ──────────────────────────

    /** Loads the expected balance and initialises close-register form data. */
    data object LoadCloseRegisterData : RegisterIntent

    /** Appends a digit to the actual balance numeric pad. */
    data class ActualBalanceDigit(val digit: String) : RegisterIntent

    /** Appends "00" to the actual balance numeric pad. */
    data object ActualBalanceDoubleZero : RegisterIntent

    /** Removes the last character from the actual balance raw string. */
    data object ActualBalanceBackspace : RegisterIntent

    /** Resets the actual balance to "0". */
    data object ActualBalanceClear : RegisterIntent

    /** Updates the closing notes text field. */
    data class ClosingNotesChanged(val notes: String) : RegisterIntent

    /** Displays the confirmation dialog before closing the register. */
    data object ShowCloseConfirmation : RegisterIntent

    /** Validates and submits the close-register operation via [CloseRegisterSessionUseCase]. */
    data object ConfirmCloseRegister : RegisterIntent

    /** Hides the close-register confirmation dialog. */
    data object DismissCloseConfirmation : RegisterIntent

    /** Manager enters PIN to approve a large discrepancy close. */
    data class ManagerApprovalPinChanged(val pin: String) : RegisterIntent

    /** Manager submits PIN to approve the discrepancy and proceed with close. */
    data object SubmitManagerApproval : RegisterIntent

    /** Cancel the manager approval flow and return to the close register form. */
    data object CancelManagerApproval : RegisterIntent

    // ─── Z-Report (Sprint 21, task 11.1.8) ────────────────────────────────

    /** Loads the Z-report data for the most recently closed session. */
    data class LoadZReport(val sessionId: String) : RegisterIntent

    /** Triggers thermal printing of the Z-report via [PrintZReportUseCase]. */
    data class PrintZReport(val sessionId: String) : RegisterIntent

    /** Generates and delivers an A4 PDF Z-report for the given session. */
    data class PrintA4ZReport(val sessionId: String) : RegisterIntent

    // ─── Cash Drawer ─────────────────────────────────────────────────────

    /** Manually opens the cash drawer via the printer HAL (ESC p command). */
    data object OpenCashDrawer : RegisterIntent

    // ─── UI Feedback ──────────────────────────────────────────────────────

    /** Dismisses the current error message. */
    data object DismissError : RegisterIntent

    /** Dismisses the current success message. */
    data object DismissSuccess : RegisterIntent
}
