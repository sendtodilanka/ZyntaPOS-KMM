package com.zyntasolutions.zyntapos.feature.accounting

import androidx.lifecycle.viewModelScope
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
import com.zyntasolutions.zyntapos.domain.usecase.einvoice.CancelEInvoiceUseCase
import com.zyntasolutions.zyntapos.domain.usecase.einvoice.GetEInvoicesUseCase
import com.zyntasolutions.zyntapos.domain.usecase.einvoice.SubmitEInvoiceToIrdUseCase
import com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * ViewModel for the Accounting / E-Invoice feature (Sprint 18-24).
 *
 * Reactive behaviour:
 * - On init, observes all e-invoices for the current store via [GetEInvoicesUseCase].
 * - IRD submission and cancellation are mutating operations dispatched via intents.
 *
 * @param getEInvoicesUseCase     Reactive Flow of all invoices for the store.
 * @param submitEInvoiceToIrdUseCase Submits a DRAFT invoice to the IRD API.
 * @param cancelEInvoiceUseCase   Cancels a DRAFT or SUBMITTED invoice.
 * @param currentStoreId          Resolved from the active auth session at DI time.
 */
class EInvoiceViewModel(
    private val getEInvoicesUseCase: GetEInvoicesUseCase,
    private val submitEInvoiceToIrdUseCase: SubmitEInvoiceToIrdUseCase,
    private val cancelEInvoiceUseCase: CancelEInvoiceUseCase,
    private val authRepository: AuthRepository,
) : BaseViewModel<EInvoiceState, EInvoiceIntent, EInvoiceEffect>(EInvoiceState()) {

    private var currentStoreId: String = "default"

    init {
        // Resolve the store ID from the active session without blocking the main thread,
        // then begin observing invoices once the scope is known.
        viewModelScope.launch {
            currentStoreId = authRepository.getSession().first()?.storeId ?: "default"
            observeInvoices()
        }
    }

    private fun observeInvoices() {
        updateState { copy(isLoading = true) }
        getEInvoicesUseCase(currentStoreId)
            .onEach { invoices -> updateState { copy(invoices = invoices, isLoading = false) } }
            .launchIn(viewModelScope)
    }

    override suspend fun handleIntent(intent: EInvoiceIntent) {
        when (intent) {
            is EInvoiceIntent.FilterByStatus -> updateState { copy(statusFilter = intent.status) }

            is EInvoiceIntent.LoadInvoice -> onLoadInvoice(intent.invoiceId)

            is EInvoiceIntent.SubmitToIrd -> onSubmitToIrd(intent.invoiceId)

            is EInvoiceIntent.RequestCancel -> updateState { copy(showCancelConfirm = intent.invoice) }
            is EInvoiceIntent.ConfirmCancel -> onConfirmCancel()
            is EInvoiceIntent.DismissCancel -> updateState { copy(showCancelConfirm = null) }

            is EInvoiceIntent.DismissError -> updateState { copy(error = null) }
            is EInvoiceIntent.DismissSuccess -> updateState { copy(successMessage = null) }
        }
    }

    // ── Handlers ──────────────────────────────────────────────────────────

    private fun onLoadInvoice(invoiceId: String) {
        // Look up from the already-loaded list to avoid extra DB round trip
        val invoice = currentState.invoices.find { it.id == invoiceId }
        updateState { copy(selectedInvoice = invoice) }
    }

    private suspend fun onSubmitToIrd(invoiceId: String) {
        val invoice = currentState.invoices.find { it.id == invoiceId }
            ?: run {
                updateState { copy(error = "Invoice not found") }
                return
            }
        if (invoice.status.name != "DRAFT" && invoice.status.name != "REJECTED") {
            updateState { copy(error = "Only DRAFT or REJECTED invoices can be submitted to IRD.") }
            return
        }
        updateState { copy(isSubmitting = true, error = null) }
        val submittedAt = Clock.System.now().toEpochMilliseconds()
        when (val result = submitEInvoiceToIrdUseCase(invoiceId, submittedAt)) {
            is Result.Success -> {
                val irdResult = result.data
                val msg = if (irdResult.success) {
                    "Invoice submitted to IRD. Ref: ${irdResult.referenceNumber ?: "—"}"
                } else {
                    "IRD submission failed: ${irdResult.errorMessage ?: "Unknown error"}"
                }
                updateState { copy(isSubmitting = false, successMessage = msg.takeIf { irdResult.success }) }
                if (!irdResult.success) {
                    updateState { copy(error = msg) }
                }
                sendEffect(EInvoiceEffect.ShowSnackbar(msg))
            }
            is Result.Error -> {
                updateState {
                    copy(isSubmitting = false, error = result.exception.message ?: "Submission failed")
                }
                sendEffect(EInvoiceEffect.ShowSnackbar(result.exception.message ?: "Submission failed"))
            }
            is Result.Loading -> {}
        }
    }

    private suspend fun onConfirmCancel() {
        val invoice = currentState.showCancelConfirm ?: return
        updateState { copy(showCancelConfirm = null, isLoading = true) }
        val now = Clock.System.now().toEpochMilliseconds()
        when (val result = cancelEInvoiceUseCase(invoice.id, now)) {
            is Result.Success -> {
                updateState { copy(isLoading = false, successMessage = "Invoice #${invoice.invoiceNumber} cancelled") }
                sendEffect(EInvoiceEffect.ShowSnackbar("Invoice cancelled"))
            }
            is Result.Error -> {
                updateState { copy(isLoading = false, error = result.exception.message ?: "Cancel failed") }
                sendEffect(EInvoiceEffect.ShowSnackbar(result.exception.message ?: "Cancel failed"))
            }
            is Result.Loading -> {}
        }
    }
}
