package com.zyntasolutions.zyntapos.feature.multistore

/**
 * One-shot side effects for the Multi-store / Warehouse feature.
 */
sealed interface WarehouseEffect {
    data class NavigateToDetail(val warehouseId: String?) : WarehouseEffect
    data object NavigateToList : WarehouseEffect
    data object NavigateToTransfers : WarehouseEffect
    data object TransferComplete : WarehouseEffect

    // IST multi-step workflow effects (C1.3)
    data class NavigateToTransferDetail(val transferId: String) : WarehouseEffect
    data object TransferApproved : WarehouseEffect
    data object TransferDispatched : WarehouseEffect
    data object TransferReceived : WarehouseEffect

    // Rack navigation
    data class NavigateToRackDetail(val rackId: String?, val warehouseId: String) : WarehouseEffect
    data object NavigateToRackList : WarehouseEffect

    // Warehouse stock navigation (C1.2)
    data class NavigateToWarehouseStock(val warehouseId: String) : WarehouseEffect
    data class NavigateToStockEntry(val warehouseId: String, val productId: String?) : WarehouseEffect

    // Rack product navigation (C1.2)
    data class NavigateToRackProductList(val rackId: String) : WarehouseEffect
    data class NavigateToRackProductDetail(val rackId: String, val productId: String?) : WarehouseEffect

    // Transit tracking navigation (C1.4)
    data class NavigateToTransitTracker(val transferId: String) : WarehouseEffect
    data object TransitEventAdded : WarehouseEffect

    // Pick list (P3-B1)
    data object PickListGenerated : WarehouseEffect
    data object PickListPrinted : WarehouseEffect

    data class ShowError(val message: String) : WarehouseEffect
    data class ShowSuccess(val message: String) : WarehouseEffect
}
