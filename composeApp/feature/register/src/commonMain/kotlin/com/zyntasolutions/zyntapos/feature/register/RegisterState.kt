package com.zyntasolutions.zyntapos.feature.register

import androidx.compose.runtime.Immutable
import com.zyntasolutions.zyntapos.domain.model.CashMovement
import com.zyntasolutions.zyntapos.domain.model.CashRegister
import com.zyntasolutions.zyntapos.domain.model.RegisterSession

/**
 * Immutable UI state for the Cash Register feature (Sprint 20, Step 11.1).
 *
 * This is the **single source of truth** consumed by [RegisterGuard],
 * [OpenRegisterScreen], [RegisterDashboardScreen], [CashInOutDialog],
 * and [CashMovementHistory]. All fields are read-only snapshots emitted by
 * [RegisterViewModel] via its `StateFlow<RegisterState>`.
 *
 * ### Sub-states
 * - **Guard:** [activeSession] — null means no open register; guard redirects to OpenRegister.
 * - **Open Register:** [availableRegisters], [openRegisterForm]
 * - **Dashboard:** [activeSession], [todayOrderCount], [todayRevenue], [movements]
 * - **Cash In/Out Dialog:** [cashInOutDialog]
 * - **Global:** [isLoading], [error], [successMessage]
 *
 * @property activeSession Currently OPEN [RegisterSession]; null = no open register.
 * @property availableRegisters Registers the current user may open (filtered by store + RBAC).
 * @property openRegisterForm Mutable form state for the Open Register screen.
 * @property todayOrderCount Count of orders placed in the active session today.
 * @property todayRevenue Total revenue for orders in the active session today.
 * @property movements Ordered list of [CashMovement] entries for the active session.
 * @property cashInOutDialog State controlling the Cash In/Out dialog; null = hidden.
 * @property isLoading True while an async operation is in-flight.
 * @property error Transient user-visible error; null = no error.
 * @property successMessage Transient success notification; null = no message.
 */
@Immutable
data class RegisterState(
    // ── Store context ─────────────────────────────────────────────────────
    val activeStoreId: String = "",
    val storeName: String = "",

    // ── Session ───────────────────────────────────────────────────────────
    val activeSession: RegisterSession? = null,

    // ── Open Register screen ──────────────────────────────────────────────
    val availableRegisters: List<CashRegister> = emptyList(),
    val openRegisterForm: OpenRegisterFormState = OpenRegisterFormState(),

    // ── Dashboard stats ───────────────────────────────────────────────────
    val todayOrderCount: Int = 0,
    val todayRevenue: Double = 0.0,

    // ── Cash movements ────────────────────────────────────────────────────
    val movements: List<CashMovement> = emptyList(),

    // ── Cash In/Out dialog ────────────────────────────────────────────────
    val cashInOutDialog: CashInOutDialogState? = null,

    // ── Close Register (Sprint 21) ────────────────────────────────────────
    val closeRegisterForm: CloseRegisterFormState = CloseRegisterFormState(),

    // ── Z-Report (Sprint 21) ──────────────────────────────────────────────
    val zReportSession: RegisterSession? = null,
    val zReportMovements: List<CashMovement> = emptyList(),
    val zReportSalesByPayment: Map<String, Double> = emptyMap(),
    val isPrintingZReport: Boolean = false,
    val isPrintingA4ZReport: Boolean = false,

    // ── Global ────────────────────────────────────────────────────────────
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
)

/**
 * Form state for the Open Register screen.
 *
 * @property selectedRegisterId UUID of the register the cashier has selected; null = none.
 * @property openingBalanceRaw Raw string from [ZyntaNumericPad] (e.g. "150050" → LKR 1,500.50).
 * @property openingNotes Optional notes entered by the cashier.
 * @property validationErrors Field-level validation failures keyed by field name.
 */
data class OpenRegisterFormState(
    val selectedRegisterId: String? = null,
    val openingBalanceRaw: String = "0",
    val openingNotes: String = "",
    val validationErrors: Map<String, String> = emptyMap(),
) {
    /** Parses [openingBalanceRaw] as a right-to-left price: "12345" → 123.45 */
    val openingBalanceDouble: Double
        get() = openingBalanceRaw.toLongOrNull()?.let { it / 100.0 } ?: 0.0
}

/**
 * State for the Cash In / Cash Out dialog.
 *
 * @property type Current selection: [CashMovement.Type.IN] or [CashMovement.Type.OUT].
 * @property amountRaw Raw string from [ZyntaNumericPad].
 * @property reason Mandatory reason text for the audit trail.
 * @property validationErrors Field-level validation failures.
 */
data class CashInOutDialogState(
    val type: CashMovement.Type = CashMovement.Type.IN,
    val amountRaw: String = "0",
    val reason: String = "",
    val validationErrors: Map<String, String> = emptyMap(),
) {
    /** Parses [amountRaw] as a right-to-left price: "5000" → 50.00 */
    val amountDouble: Double
        get() = amountRaw.toLongOrNull()?.let { it / 100.0 } ?: 0.0
}

/**
 * Form state for the Close Register screen (Sprint 21, task 11.1.6).
 *
 * The **expected balance** is auto-calculated from the session data
 * (`openingBalance + ∑cashIn − ∑cashOut + cashSales`) and shown as read-only.
 * The **actual balance** is entered by the operator via [ZyntaNumericPad].
 *
 * @property actualBalanceRaw Raw string from [ZyntaNumericPad] (e.g. "250075" → 2,500.75).
 * @property closingNotes Optional notes entered during the closing procedure.
 * @property expectedBalance System-calculated expected cash (read-only display).
 * @property discrepancy The difference `actualBalance − expectedBalance`. Negative = short.
 * @property discrepancyThreshold Configurable threshold (absolute value) that triggers a warning.
 * @property validationErrors Field-level validation failures keyed by field name.
 * @property showConfirmation Whether the "Confirm Close" dialog is displayed.
 */
data class CloseRegisterFormState(
    val actualBalanceRaw: String = "0",
    val closingNotes: String = "",
    val expectedBalance: Double = 0.0,
    val discrepancy: Double = 0.0,
    val discrepancyThreshold: Double = 10.0,
    val validationErrors: Map<String, String> = emptyMap(),
    val showConfirmation: Boolean = false,
) {
    /** Parses [actualBalanceRaw] as a right-to-left price: "12345" → 123.45 */
    val actualBalanceDouble: Double
        get() = actualBalanceRaw.toLongOrNull()?.let { it / 100.0 } ?: 0.0

    /** True when the absolute discrepancy exceeds [discrepancyThreshold]. */
    val isDiscrepancyWarning: Boolean
        get() = kotlin.math.abs(discrepancy) > discrepancyThreshold
}
