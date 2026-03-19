package com.zyntasolutions.zyntapos.feature.inventory.masterproduct

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.repository.MasterProductRepository
import com.zyntasolutions.zyntapos.domain.repository.StoreProductOverrideRepository
import com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel

/**
 * ViewModel for the master product override screen.
 * Shows read-only master product details with editable local price/stock overrides.
 */
class MasterProductOverrideViewModel(
    private val storeId: String,
    private val masterProductRepository: MasterProductRepository,
    private val storeProductOverrideRepository: StoreProductOverrideRepository,
) : BaseViewModel<MasterProductOverrideState, MasterProductOverrideIntent, MasterProductOverrideEffect>(
    initialState = MasterProductOverrideState()
) {

    override suspend fun handleIntent(intent: MasterProductOverrideIntent) {
        when (intent) {
            is MasterProductOverrideIntent.Load -> load(intent.masterProductId)
            is MasterProductOverrideIntent.UpdateLocalPrice -> updateState { copy(localPriceInput = intent.price) }
            is MasterProductOverrideIntent.UpdateLocalStock -> updateState { copy(localStockInput = intent.qty) }
            is MasterProductOverrideIntent.Save -> save()
            is MasterProductOverrideIntent.NavigateBack -> sendEffect(MasterProductOverrideEffect.NavigateBack)
        }
    }

    private suspend fun load(masterProductId: String) {
        updateState { copy(isLoading = true, error = null) }

        when (val masterResult = masterProductRepository.getById(masterProductId)) {
            is Result.Success -> {
                val master = masterResult.data
                val overrideResult = storeProductOverrideRepository.getOverride(masterProductId, storeId)
                val override = (overrideResult as? Result.Success)?.data

                val effectivePrice = override?.localPrice ?: master.basePrice

                updateState {
                    copy(
                        masterProduct = master,
                        currentOverride = override,
                        effectivePrice = effectivePrice,
                        localPriceInput = override?.localPrice?.toString() ?: "",
                        localStockInput = override?.localStockQty?.toString() ?: "0.0",
                        isLoading = false,
                    )
                }
            }
            is Result.Error -> {
                updateState { copy(isLoading = false, error = "Failed to load product") }
                sendEffect(MasterProductOverrideEffect.ShowError("Failed to load master product"))
            }
            is Result.Loading -> { /* no-op — loading state already set above */ }
        }
    }

    private suspend fun save() {
        val masterProductId = currentState.masterProduct?.id ?: return
        updateState { copy(isSaving = true) }

        val price = currentState.localPriceInput.toDoubleOrNull()
        val priceResult = storeProductOverrideRepository.updateLocalPrice(masterProductId, storeId, price)

        val qty = currentState.localStockInput.toDoubleOrNull() ?: 0.0
        val stockResult = storeProductOverrideRepository.updateLocalStock(masterProductId, storeId, qty)

        if (priceResult is Result.Success && stockResult is Result.Success) {
            updateState { copy(isSaving = false, effectivePrice = price ?: currentState.masterProduct!!.basePrice) }
            sendEffect(MasterProductOverrideEffect.SaveSuccess)
        } else {
            updateState { copy(isSaving = false) }
            sendEffect(MasterProductOverrideEffect.ShowError("Failed to save overrides"))
        }
    }
}
