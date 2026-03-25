package com.zyntasolutions.zyntapos.feature.pos.fulfillment

import com.zyntasolutions.zyntapos.core.result.onError
import com.zyntasolutions.zyntapos.core.result.onSuccess
import com.zyntasolutions.zyntapos.domain.model.FulfillmentStatus
import com.zyntasolutions.zyntapos.domain.repository.FulfillmentRepository
import com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * ViewModel for the Click & Collect fulfillment queue screen (C4.4).
 *
 * Observes the live pickup queue via [FulfillmentRepository.getPendingPickups] and
 * handles status transitions (PREPARING → READY_FOR_PICKUP → PICKED_UP / CANCELLED).
 */
class FulfillmentViewModel(
    private val fulfillmentRepository: FulfillmentRepository,
    private val storeId: String,
) : BaseViewModel<FulfillmentState, FulfillmentIntent, Unit>(FulfillmentState()) {

    init {
        observePickups()
    }

    override suspend fun handleIntent(intent: FulfillmentIntent) {
        when (intent) {
            is FulfillmentIntent.LoadQueue    -> observePickups()
            is FulfillmentIntent.MarkPreparing -> updateStatus(intent.orderId, FulfillmentStatus.PREPARING)
            is FulfillmentIntent.MarkReady    -> updateStatus(intent.orderId, FulfillmentStatus.READY_FOR_PICKUP, notifyCustomer = intent.notifyCustomer)
            is FulfillmentIntent.MarkPickedUp -> updateStatus(intent.orderId, FulfillmentStatus.PICKED_UP)
            is FulfillmentIntent.CancelOrder  -> updateStatus(intent.orderId, FulfillmentStatus.CANCELLED)
            is FulfillmentIntent.DismissError -> updateState { it.copy(errorMessage = null) }
        }
    }

    private fun observePickups() {
        updateState { it.copy(isLoading = true) }
        fulfillmentRepository.getPendingPickups(storeId)
            .onEach { pickups ->
                updateState { it.copy(pickups = pickups, isLoading = false) }
            }
            .catch { e ->
                updateState { it.copy(isLoading = false, errorMessage = e.message ?: "Failed to load pickups") }
            }
            .launchIn(viewModelScope)
    }

    private suspend fun updateStatus(
        orderId: String,
        newStatus: FulfillmentStatus,
        notifyCustomer: Boolean = false,
    ) {
        updateState { it.copy(updatingOrderId = orderId) }
        fulfillmentRepository.updateStatus(orderId, newStatus, notifyCustomer)
            .onSuccess {
                updateState { it.copy(updatingOrderId = null) }
            }
            .onError { e ->
                updateState { it.copy(updatingOrderId = null, errorMessage = e.message ?: "Update failed") }
            }
    }
}
