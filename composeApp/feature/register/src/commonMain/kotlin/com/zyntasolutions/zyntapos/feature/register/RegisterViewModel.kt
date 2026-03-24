package com.zyntasolutions.zyntapos.feature.register

import androidx.lifecycle.viewModelScope
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.CashMovement
import com.zyntasolutions.zyntapos.domain.model.OrderStatus
import com.zyntasolutions.zyntapos.domain.model.RegisterSession
import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
import com.zyntasolutions.zyntapos.domain.repository.OrderRepository
import com.zyntasolutions.zyntapos.domain.repository.RegisterRepository
import com.zyntasolutions.zyntapos.domain.repository.StoreRepository
import com.zyntasolutions.zyntapos.domain.usecase.register.CloseRegisterSessionUseCase
import com.zyntasolutions.zyntapos.domain.usecase.register.OpenRegisterSessionUseCase
import com.zyntasolutions.zyntapos.domain.usecase.register.PrintA4ZReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.register.PrintZReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.OpenCashDrawerUseCase
import com.zyntasolutions.zyntapos.domain.usecase.register.RecordCashMovementUseCase
import com.zyntasolutions.zyntapos.core.analytics.AnalyticsTracker
import com.zyntasolutions.zyntapos.security.audit.SecurityAuditLogger
import com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel
import kotlin.time.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/**
 * ViewModel for the Cash Register lifecycle screens (Sprint 20, task 11.1).
 *
 * ### Responsibilities
 * - Observes [RegisterRepository.getActive] and drives [RegisterGuard] redirects.
 * - Manages the Open Register form lifecycle with numeric-pad input and validation.
 * - Loads available registers for the Open Register screen.
 * - Provides dashboard stats (today's orders and revenue — placeholder until
 *   OrderRepository is wired in Phase 2).
 * - Records Cash In/Out movements via [RecordCashMovementUseCase].
 * - Observes [RegisterRepository.getMovements] for the active session.
 *
 * @param registerRepository     Register session and movement gateway.
 * @param orderRepository        Order history source; used to compute today's dashboard stats.
 * @param openRegisterSessionUseCase Opens a new session with validation.
 * @param closeRegisterSessionUseCase Closes an active session with balance reconciliation.
 * @param recordCashMovementUseCase  Records a cash-in or cash-out movement.
 * @param printZReportUseCase    Prints the Z-report to the connected thermal printer.
 * @param printA4ZReportUseCase  Prints the A4 PDF Z-report via system print dialog.
 * @param authRepository         Provides the active auth session for resolving currentUserId.
 * @param storeRepository        Provides store name lookup for the active store context.
 */
class RegisterViewModel(
    private val registerRepository: RegisterRepository,
    private val orderRepository: OrderRepository,
    private val openRegisterSessionUseCase: OpenRegisterSessionUseCase,
    private val closeRegisterSessionUseCase: CloseRegisterSessionUseCase,
    private val recordCashMovementUseCase: RecordCashMovementUseCase,
    private val printZReportUseCase: PrintZReportUseCase,
    private val printA4ZReportUseCase: PrintA4ZReportUseCase,
    private val authRepository: AuthRepository,
    private val storeRepository: StoreRepository,
    private val openCashDrawerUseCase: OpenCashDrawerUseCase,
    private val auditLogger: SecurityAuditLogger,
    private val analytics: AnalyticsTracker,
) : BaseViewModel<RegisterState, RegisterIntent, RegisterEffect>(RegisterState()) {

    private var currentUserId: String = "unknown"

    init {
        analytics.logScreenView("Register", "RegisterViewModel")
        viewModelScope.launch {
            val session = authRepository.getSession().first()
            currentUserId = session?.id ?: "unknown"
            val storeId = session?.storeId ?: ""
            val name = if (storeId.isNotEmpty()) {
                storeRepository.getStoreName(storeId) ?: ""
            } else ""
            updateState { copy(activeStoreId = storeId, storeName = name) }
        }
    }

    // ── Intent handler ────────────────────────────────────────────────────

    override suspend fun handleIntent(intent: RegisterIntent) {
        when (intent) {
            is RegisterIntent.ObserveActiveSession -> observeActiveSession()
            is RegisterIntent.LoadAvailableRegisters -> loadAvailableRegisters()

            // Open Register form
            is RegisterIntent.SelectRegister -> updateOpenForm { copy(selectedRegisterId = intent.registerId) }
            is RegisterIntent.OpeningBalanceDigit -> updateOpenForm {
                copy(openingBalanceRaw = appendDigit(openingBalanceRaw, intent.digit))
            }
            is RegisterIntent.OpeningBalanceDoubleZero -> updateOpenForm {
                copy(openingBalanceRaw = appendDoubleZero(openingBalanceRaw))
            }
            is RegisterIntent.OpeningBalanceDecimal -> Unit // no-op in right-to-left PRICE mode
            is RegisterIntent.OpeningBalanceBackspace -> updateOpenForm {
                copy(openingBalanceRaw = deleteLastChar(openingBalanceRaw))
            }
            is RegisterIntent.OpeningBalanceClear -> updateOpenForm { copy(openingBalanceRaw = "0") }
            is RegisterIntent.OpeningNotesChanged -> updateOpenForm { copy(openingNotes = intent.notes) }
            is RegisterIntent.ConfirmOpenRegister -> confirmOpenRegister()

            // Dashboard
            is RegisterIntent.LoadDashboardStats -> loadDashboardStats()

            // Cash In/Out dialog
            is RegisterIntent.ShowCashInOutDialog ->
                updateState { copy(cashInOutDialog = CashInOutDialogState(type = intent.initialType)) }
            is RegisterIntent.SetCashInOutType -> updateDialog { copy(type = intent.type) }
            is RegisterIntent.CashInOutAmountDigit -> updateDialog {
                copy(amountRaw = appendDigit(amountRaw, intent.digit))
            }
            is RegisterIntent.CashInOutAmountDoubleZero -> updateDialog {
                copy(amountRaw = appendDoubleZero(amountRaw))
            }
            is RegisterIntent.CashInOutAmountDecimal -> Unit // no-op in PRICE mode
            is RegisterIntent.CashInOutAmountBackspace -> updateDialog {
                copy(amountRaw = deleteLastChar(amountRaw))
            }
            is RegisterIntent.CashInOutAmountClear -> updateDialog { copy(amountRaw = "0") }
            is RegisterIntent.CashInOutReasonChanged -> updateDialog { copy(reason = intent.reason) }
            is RegisterIntent.ConfirmCashInOut -> confirmCashInOut()
            is RegisterIntent.DismissCashInOut -> updateState { copy(cashInOutDialog = null) }

            // Close Register (Sprint 21)
            is RegisterIntent.LoadCloseRegisterData -> loadCloseRegisterData()
            is RegisterIntent.ActualBalanceDigit -> updateCloseForm {
                val newRaw = appendDigit(actualBalanceRaw, intent.digit)
                val actual = newRaw.toLongOrNull()?.let { it / 100.0 } ?: 0.0
                copy(
                    actualBalanceRaw = newRaw,
                    discrepancy = actual - expectedBalance,
                )
            }
            is RegisterIntent.ActualBalanceDoubleZero -> updateCloseForm {
                val newRaw = appendDoubleZero(actualBalanceRaw)
                val actual = newRaw.toLongOrNull()?.let { it / 100.0 } ?: 0.0
                copy(
                    actualBalanceRaw = newRaw,
                    discrepancy = actual - expectedBalance,
                )
            }
            is RegisterIntent.ActualBalanceBackspace -> updateCloseForm {
                val newRaw = deleteLastChar(actualBalanceRaw)
                val actual = newRaw.toLongOrNull()?.let { it / 100.0 } ?: 0.0
                copy(
                    actualBalanceRaw = newRaw,
                    discrepancy = actual - expectedBalance,
                )
            }
            is RegisterIntent.ActualBalanceClear -> updateCloseForm {
                copy(
                    actualBalanceRaw = "0",
                    discrepancy = 0.0 - expectedBalance,
                )
            }
            is RegisterIntent.ClosingNotesChanged -> updateCloseForm {
                copy(closingNotes = intent.notes)
            }
            is RegisterIntent.ShowCloseConfirmation -> {
                val form = currentState.closeRegisterForm
                val errors = validateCloseRegisterForm(form)
                if (errors.isNotEmpty()) {
                    updateCloseForm { copy(validationErrors = errors) }
                } else {
                    updateCloseForm { copy(showConfirmation = true, validationErrors = emptyMap()) }
                }
            }
            is RegisterIntent.ConfirmCloseRegister -> confirmCloseRegister()
            is RegisterIntent.DismissCloseConfirmation -> updateCloseForm {
                copy(showConfirmation = false)
            }

            // Z-Report (Sprint 21)
            is RegisterIntent.LoadZReport -> loadZReport(intent.sessionId)
            is RegisterIntent.PrintZReport -> printZReport(intent.sessionId)
            is RegisterIntent.PrintA4ZReport -> onPrintA4ZReport(intent.sessionId)

            // Cash drawer
            is RegisterIntent.OpenCashDrawer -> onOpenCashDrawer()

            // UI feedback
            is RegisterIntent.DismissError -> updateState { copy(error = null) }
            is RegisterIntent.DismissSuccess -> updateState { copy(successMessage = null) }
        }
    }

    private suspend fun onOpenCashDrawer() {
        when (val result = openCashDrawerUseCase()) {
            is Result.Success -> updateState { copy(successMessage = "Cash drawer opened") }
            is Result.Error -> updateState { copy(error = "Failed to open cash drawer: ${result.exception.message}") }
            is Result.Loading -> Unit // no-op — use case completes synchronously
        }
    }

    // ── Observation ───────────────────────────────────────────────────────

    private fun observeActiveSession() {
        registerRepository.getActive()
            .onEach { session ->
                updateState { copy(activeSession = session) }
                if (session != null) {
                    observeMovements(session.id)
                }
            }
            .launchIn(viewModelScope)
    }

    private fun observeMovements(sessionId: String) {
        registerRepository.getMovements(sessionId)
            .onEach { movements -> updateState { copy(movements = movements) } }
            .launchIn(viewModelScope)
    }

    // ── Open Register ─────────────────────────────────────────────────────

    private fun loadAvailableRegisters() {
        registerRepository.getRegisters()
            .onEach { registers -> updateState { copy(availableRegisters = registers) } }
            .launchIn(viewModelScope)
    }

    private suspend fun confirmOpenRegister() {
        val form = currentState.openRegisterForm
        val errors = validateOpenRegisterForm(form)
        if (errors.isNotEmpty()) {
            updateOpenForm { copy(validationErrors = errors) }
            return
        }

        updateState { copy(isLoading = true) }
        val result = openRegisterSessionUseCase(
            registerId = form.selectedRegisterId!!,
            openingBalance = form.openingBalanceDouble,
            userId = currentUserId,
        )
        updateState { copy(isLoading = false) }

        when (result) {
            is Result.Success -> {
                auditLogger.logRegisterOpen(currentUserId, form.selectedRegisterId!!, form.openingBalanceDouble)
                updateState {
                    copy(
                        activeSession = result.data,
                        openRegisterForm = OpenRegisterFormState(),
                    )
                }
                sendEffect(RegisterEffect.NavigateToDashboard)
                sendEffect(RegisterEffect.ShowSuccess("Register opened — opening balance: ${result.data.openingBalance}"))
            }
            is Result.Error -> sendEffect(RegisterEffect.ShowError(result.exception.message ?: "Failed to open register"))
            is Result.Loading -> Unit
        }
    }

    private fun validateOpenRegisterForm(form: OpenRegisterFormState): Map<String, String> {
        val errors = mutableMapOf<String, String>()
        if (form.selectedRegisterId == null) errors["register"] = "Please select a register."
        if (form.openingBalanceDouble < 0) errors["openingBalance"] = "Opening balance must be ≥ 0."
        return errors
    }

    // ── Dashboard ─────────────────────────────────────────────────────────

    private suspend fun loadDashboardStats() {
        val now = Clock.System.now()
        val tz = TimeZone.currentSystemDefault()
        val todayStart = now.toLocalDateTime(tz).date.atStartOfDayIn(tz)
        orderRepository.getByDateRange(todayStart, now)
            .first()
            .let { orders ->
                updateState {
                    copy(
                        todayOrderCount = orders.size,
                        todayRevenue    = orders.sumOf { it.total },
                    )
                }
            }
    }

    // ── Cash In/Out ───────────────────────────────────────────────────────

    private suspend fun confirmCashInOut() {
        val dialog = currentState.cashInOutDialog ?: return
        val sessionId = currentState.activeSession?.id ?: return

        val errors = validateCashInOut(dialog)
        if (errors.isNotEmpty()) {
            updateDialog { copy(validationErrors = errors) }
            return
        }

        updateState { copy(isLoading = true) }
        val result = recordCashMovementUseCase(
            sessionId = sessionId,
            type = dialog.type,
            amount = dialog.amountDouble,
            reason = dialog.reason.trim(),
            recordedBy = currentUserId,
        )
        updateState { copy(isLoading = false, cashInOutDialog = null) }

        when (result) {
            is Result.Success -> {
                if (dialog.type == CashMovement.Type.IN) {
                    auditLogger.logCashIn(currentUserId, sessionId, dialog.amountDouble, dialog.reason.trim())
                } else {
                    auditLogger.logCashOut(currentUserId, sessionId, dialog.amountDouble, dialog.reason.trim())
                }
                val label = if (dialog.type == CashMovement.Type.IN) "Cash In" else "Cash Out"
                sendEffect(RegisterEffect.ShowSuccess("$label of ${dialog.amountDouble} recorded."))
            }
            is Result.Error -> sendEffect(RegisterEffect.ShowError(result.exception.message ?: "Cash movement failed"))
            is Result.Loading -> Unit
        }
    }

    private fun validateCashInOut(dialog: CashInOutDialogState): Map<String, String> {
        val errors = mutableMapOf<String, String>()
        if (dialog.amountDouble <= 0.0) errors["amount"] = "Amount must be greater than 0."
        if (dialog.reason.isBlank()) errors["reason"] = "Reason is required."
        return errors
    }

    // ── Close Register (Sprint 21) ────────────────────────────────────────

    private suspend fun loadCloseRegisterData() {
        val session = currentState.activeSession ?: return
        // expectedBalance is maintained by the repository layer; it is stored on the session.
        // We read the current session's expected balance directly.
        updateCloseForm {
            copy(
                expectedBalance = session.expectedBalance,
                discrepancy = actualBalanceDouble - session.expectedBalance,
            )
        }
    }

    private suspend fun confirmCloseRegister() {
        val session = currentState.activeSession ?: return
        val form = currentState.closeRegisterForm

        updateCloseForm { copy(showConfirmation = false) }
        updateState { copy(isLoading = true) }

        val result = closeRegisterSessionUseCase(
            sessionId = session.id,
            actualBalance = form.actualBalanceDouble,
            userId = currentUserId,
        )
        updateState { copy(isLoading = false) }

        when (result) {
            is Result.Success -> {
                val closeResult = result.data
                auditLogger.logRegisterClose(currentUserId, closeResult.session.id, closeResult.discrepancy)
                updateState {
                    copy(
                        activeSession = null,
                        closeRegisterForm = CloseRegisterFormState(),
                        zReportSession = closeResult.session,
                    )
                }
                sendEffect(RegisterEffect.NavigateToZReport(closeResult.session.id))
                val discrepancyLabel = if (closeResult.isBalanced) {
                    "Register closed — balanced."
                } else {
                    val direction = if (closeResult.discrepancy > 0) "OVER" else "SHORT"
                    "Register closed — $direction by ${kotlin.math.abs(closeResult.discrepancy)}."
                }
                sendEffect(RegisterEffect.ShowSuccess(discrepancyLabel))
            }
            is Result.Error -> sendEffect(
                RegisterEffect.ShowError(result.exception.message ?: "Failed to close register"),
            )
            is Result.Loading -> Unit
        }
    }

    private fun validateCloseRegisterForm(form: CloseRegisterFormState): Map<String, String> {
        val errors = mutableMapOf<String, String>()
        if (form.actualBalanceDouble < 0.0) {
            errors["actualBalance"] = "Actual balance must be ≥ 0."
        }
        return errors
    }

    // ── Z-Report (Sprint 21) ──────────────────────────────────────────────

    private suspend fun loadZReport(sessionId: String) {
        updateState { copy(isLoading = true) }
        val session = currentState.zReportSession
        if (session != null && session.id == sessionId) {
            val movements = currentState.movements

            // Load sales breakdown by payment method for the session's time window
            val salesByPayment = loadSalesByPaymentMethod(session)

            updateState {
                copy(
                    isLoading = false,
                    zReportMovements = movements,
                    zReportSalesByPayment = salesByPayment,
                )
            }
        } else {
            updateState { copy(isLoading = false) }
        }
    }

    private suspend fun loadSalesByPaymentMethod(session: RegisterSession): Map<String, Double> {
        val from = session.openedAt
        val to = session.closedAt ?: Clock.System.now()

        val orders = orderRepository.getByDateRange(from, to).first()
        val completed = orders.filter {
            it.status == OrderStatus.COMPLETED
        }
        return completed.groupBy { it.paymentMethod.name }
            .mapValues { (_, orderList) -> orderList.sumOf { it.total } }
    }

    private suspend fun printZReport(sessionId: String) {
        val session = currentState.zReportSession ?: return
        if (session.id != sessionId) return

        updateState { copy(isPrintingZReport = true) }
        val result = printZReportUseCase(session)
        updateState { copy(isPrintingZReport = false) }

        when (result) {
            is Result.Success -> sendEffect(RegisterEffect.ShowSuccess("Z-report sent to printer."))
            is Result.Error -> sendEffect(
                RegisterEffect.ShowError("Print failed: ${result.exception.message}"),
            )
            is Result.Loading -> Unit
        }
    }

    private suspend fun onPrintA4ZReport(sessionId: String) {
        val session = currentState.zReportSession ?: return
        if (session.id != sessionId) return

        updateState { copy(isPrintingA4ZReport = true) }
        when (val result = printA4ZReportUseCase.execute(sessionId, currentUserId)) {
            is Result.Success -> {
                updateState { copy(isPrintingA4ZReport = false) }
                sendEffect(RegisterEffect.A4ZReportPrinted)
            }
            is Result.Error -> {
                updateState { copy(isPrintingA4ZReport = false) }
                sendEffect(RegisterEffect.ShowError(result.exception.message ?: "Failed to print A4 Z-report"))
            }
            is Result.Loading -> Unit
        }
    }

    // ── Numeric-pad helpers ───────────────────────────────────────────────

    /**
     * Right-to-left digit append for PRICE mode.
     * "0" seed → "1" → "12" → "123" (displayed as 1.23)
     */
    private fun appendDigit(current: String, digit: String): String {
        val raw = if (current == "0") digit else current + digit
        return raw.trimStart('0').ifEmpty { "0" }
    }

    private fun appendDoubleZero(current: String): String =
        if (current == "0") "0" else (current + "00").trimStart('0').ifEmpty { "0" }

    private fun deleteLastChar(current: String): String =
        if (current.length <= 1) "0" else current.dropLast(1)

    // ── State update shortcuts ────────────────────────────────────────────

    private fun updateOpenForm(transform: OpenRegisterFormState.() -> OpenRegisterFormState) {
        updateState { copy(openRegisterForm = openRegisterForm.transform()) }
    }

    private fun updateDialog(transform: CashInOutDialogState.() -> CashInOutDialogState) {
        updateState {
            copy(cashInOutDialog = cashInOutDialog?.transform())
        }
    }

    private fun updateCloseForm(transform: CloseRegisterFormState.() -> CloseRegisterFormState) {
        updateState { copy(closeRegisterForm = closeRegisterForm.transform()) }
    }
}
