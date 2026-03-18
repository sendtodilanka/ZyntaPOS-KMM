package com.zyntasolutions.zyntapos.feature.inventory.stocktake

import androidx.lifecycle.viewModelScope
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.StocktakeCount
import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
import com.zyntasolutions.zyntapos.domain.repository.StocktakeRepository
import com.zyntasolutions.zyntapos.domain.usecase.inventory.CompleteStocktakeUseCase
import com.zyntasolutions.zyntapos.domain.usecase.inventory.ScanStocktakeItemUseCase
import com.zyntasolutions.zyntapos.domain.usecase.inventory.StartStocktakeUseCase
import com.zyntasolutions.zyntapos.hal.scanner.BarcodeScanner
import com.zyntasolutions.zyntapos.hal.scanner.ScanResult
import com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * ViewModel for the stocktake (physical inventory count) screen.
 *
 * Handles scanner events, delegates to stocktake use cases, and exposes
 * live count state to the UI. Follows the MVI pattern via [BaseViewModel].
 */
class StocktakeViewModel(
    private val startStocktakeUseCase: StartStocktakeUseCase,
    private val scanStocktakeItemUseCase: ScanStocktakeItemUseCase,
    private val completeStocktakeUseCase: CompleteStocktakeUseCase,
    private val stocktakeRepository: StocktakeRepository,
    private val authRepository: AuthRepository,
    private val barcodeScanner: BarcodeScanner,
) : BaseViewModel<StocktakeState, StocktakeIntent, StocktakeEffect>(StocktakeState()) {

    private var currentUserId: String = "unknown"
    private var scannerJob: Job? = null

    init {
        viewModelScope.launch {
            currentUserId = authRepository.getSession().first()?.id ?: "unknown"
        }
    }

    override suspend fun handleIntent(intent: StocktakeIntent) {
        when (intent) {
            is StocktakeIntent.StartSession         -> onStartSession()
            is StocktakeIntent.ScanItem             -> onScanItem(intent.barcode)
            is StocktakeIntent.ManualAdjustCount    -> onManualAdjustCount(intent.productId, intent.qty)
            is StocktakeIntent.RemoveCount          -> onRemoveCount(intent.productId)
            is StocktakeIntent.SetScannerActive     -> onSetScannerActive(intent.active)
            is StocktakeIntent.CompleteStocktake    -> onCompleteStocktake()
            is StocktakeIntent.CancelStocktake      -> onCancelStocktake()
            is StocktakeIntent.DismissError         -> updateState { copy(error = null) }
        }
    }

    private fun onSetScannerActive(active: Boolean) {
        if (active && !currentState.isScanning) {
            viewModelScope.launch {
                val result = barcodeScanner.startListening()
                if (result.isSuccess) {
                    updateState { copy(isScanning = true) }
                    scannerJob = barcodeScanner.scanEvents
                        .onEach { scanResult ->
                            when (scanResult) {
                                is ScanResult.Barcode -> dispatch(StocktakeIntent.ScanItem(scanResult.value))
                                is ScanResult.Error -> sendEffect(StocktakeEffect.ScanNotFound(scanResult.message))
                            }
                        }
                        .launchIn(viewModelScope)
                }
            }
        } else if (!active && currentState.isScanning) {
            scannerJob?.cancel()
            scannerJob = null
            viewModelScope.launch { barcodeScanner.stopListening() }
            updateState { copy(isScanning = false) }
        }
    }

    private suspend fun onStartSession() {
        updateState { copy(isStarting = true, error = null) }
        when (val result = startStocktakeUseCase.execute(currentUserId)) {
            is Result.Success -> updateState {
                copy(session = result.data, isStarting = false, counts = emptyList())
            }
            is Result.Error   -> updateState {
                copy(isStarting = false, error = result.exception.message ?: "Failed to start stocktake")
            }
            is Result.Loading -> Unit
        }
    }

    private suspend fun onScanItem(barcode: String) {
        val sessionId = currentState.session?.id ?: return
        updateState { copy(lastScannedBarcode = barcode) }
        when (val result = scanStocktakeItemUseCase.execute(sessionId, barcode)) {
            is Result.Success -> {
                val count = result.data
                val updatedCounts = mergeCounts(currentState.counts, count)
                updateState { copy(counts = updatedCounts) }
                sendEffect(StocktakeEffect.ScanSuccess(count.productName, count.countedQty ?: 1))
            }
            is Result.Error   -> sendEffect(StocktakeEffect.ScanNotFound(barcode))
            is Result.Loading -> Unit
        }
    }

    private suspend fun onManualAdjustCount(productId: String, qty: Int) {
        val sessionId = currentState.session?.id ?: return
        val existing = currentState.counts.firstOrNull { it.productId == productId } ?: return
        val updateResult = stocktakeRepository.updateCount(sessionId, existing.barcode, qty)
        if (updateResult is Result.Success) {
            val updated = existing.copy(
                countedQty = qty,
                scannedAt = Clock.System.now().toEpochMilliseconds(),
            )
            val updatedCounts = mergeCounts(currentState.counts, updated)
            updateState { copy(counts = updatedCounts) }
        }
    }

    private fun onRemoveCount(productId: String) {
        updateState { copy(counts = counts.filter { it.productId != productId }) }
    }

    private suspend fun onCompleteStocktake() {
        onSetScannerActive(false)
        val sessionId = currentState.session?.id ?: return
        updateState { copy(isCompleting = true) }
        when (val result = completeStocktakeUseCase.execute(sessionId, currentUserId)) {
            is Result.Success -> {
                val varianceCount = result.data.values.count { it != 0 }
                updateState { copy(isCompleting = false, session = null, counts = emptyList()) }
                sendEffect(StocktakeEffect.StocktakeCompleted(varianceCount))
            }
            is Result.Error   -> {
                updateState {
                    copy(isCompleting = false, error = result.exception.message ?: "Failed to complete stocktake")
                }
            }
            is Result.Loading -> Unit
        }
    }

    private suspend fun onCancelStocktake() {
        onSetScannerActive(false)
        updateState { copy(session = null, counts = emptyList(), error = null) }
        sendEffect(StocktakeEffect.SessionCancelled)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun mergeCounts(
        existing: List<StocktakeCount>,
        updated: StocktakeCount,
    ): List<StocktakeCount> {
        val idx = existing.indexOfFirst { it.productId == updated.productId }
        return if (idx >= 0) {
            existing.toMutableList().also { it[idx] = updated }
        } else {
            existing + updated
        }
    }
}
